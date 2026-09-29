// SPDX-License-Identifier: GPL-2.0
// HydraSU pathhide v2 — hide configured paths from non-privileged processes.
// Ported from FolkPatch folkpatch_pathhide (GPL-2.0-or-later) to HydraSU's
// syscall-table hook architecture. Handlers must be plain syscall_fn_t
// (single pt_regs arg); originals are saved per-hook.
#include "linux/file.h"
#include "linux/fcntl.h"
#include "linux/namei.h"
#include <linux/compiler_types.h>
#include <linux/preempt.h>
#include <linux/printk.h>
#include <linux/mm.h>
#include <linux/pgtable.h>
#include <linux/uaccess.h>
#include <asm/current.h>
#include <linux/cred.h>
#include <linux/fs.h>
#include <linux/types.h>
#include <linux/version.h>
#include <linux/sched/task_stack.h>
#include <linux/ptrace.h>
#include <linux/slab.h>
#include <linux/vmalloc.h>
#include <linux/errno.h>
#include <linux/string.h>
#include <linux/dirent.h>

#include "arch.h"
#include "policy/allowlist.h"
#include "klog.h"
#include "hook/syscall_hook.h"
#include "ksu.h"
#include "util.h"

#define PATHHIDE_MAX_PATHS 32
#define PATHHIDE_MAX_PATH_LEN 256
#define PATHHIDE_DIRENT_LEN 4096
#define DENT64_RECLEN_OFF 16
#define DENT64_NAME_OFF 19

static char hide_paths[PATHHIDE_MAX_PATHS][PATHHIDE_MAX_PATH_LEN];
static int hide_path_count;
static bool pathhide_ready;
static DEFINE_MUTEX(pathhide_lock);

static syscall_fn_t orig_openat;
static syscall_fn_t orig_faccessat;
static syscall_fn_t orig_newfstatat;
static syscall_fn_t orig_getdents64;

static bool pathhide_should_filter(void)
{
	return !ksu_is_allow_uid_for_current(current_uid().val);
}

static bool path_is_blocked(const char *p)
{
	int i;
	for (i = 0; i < hide_path_count; i++) {
		if (strncmp(p, hide_paths[i], strlen(hide_paths[i])) == 0)
			return true;
	}
	return false;
}

static long hydra_openat(const struct pt_regs *regs)
{
	char resolved[PATHHIDE_MAX_PATH_LEN];
	const char __user *uname = (const char __user *)regs->__PT_PARM2_REG;

	if (!pathhide_should_filter()) goto orig;
	if (strncpy_from_user_nofault(resolved, uname, sizeof(resolved)) <= 0)
		goto orig;
	if (path_is_blocked(resolved)) return -ENOENT;
orig:
	return orig_openat(regs);
}

static long hydra_faccessat(const struct pt_regs *regs)
{
	char resolved[PATHHIDE_MAX_PATH_LEN];
	const char __user *uname = (const char __user *)regs->__PT_PARM2_REG;

	if (!pathhide_should_filter()) goto orig;
	if (strncpy_from_user_nofault(resolved, uname, sizeof(resolved)) <= 0)
		goto orig;
	if (path_is_blocked(resolved)) return -ENOENT;
orig:
	return orig_faccessat(regs);
}

static long hydra_newfstatat(const struct pt_regs *regs)
{
	char resolved[PATHHIDE_MAX_PATH_LEN];
	const char __user *uname = (const char __user *)regs->__PT_PARM2_REG;

	if (!pathhide_should_filter()) goto orig;
	if (strncpy_from_user_nofault(resolved, uname, sizeof(resolved)) <= 0)
		goto orig;
	if (path_is_blocked(resolved)) return -ENOENT;
orig:
	return orig_newfstatat(regs);
}

static int pathhide_filter_dirents(char *data, int len, const char *dir)
{
	int pos = 0, out = 0;
	char full[PATHHIDE_MAX_PATH_LEN];

	while (pos < len) {
		uint16_t record_len;
		char *name;
		int full_len;

		if (pos + DENT64_NAME_OFF >= len) return -EIO;
		record_len = *(uint16_t *)(data + pos + DENT64_RECLEN_OFF);
		if (record_len < DENT64_NAME_OFF + 1 || pos + record_len > len)
			return -EIO;
		name = data + pos + DENT64_NAME_OFF;
		if (!memchr(name, '\0', record_len - DENT64_NAME_OFF))
			return -EIO;
		full_len = snprintf(full, sizeof(full), "%s/%s", dir, name);
		if (full_len <= 0 || full_len >= sizeof(full) ||
		    !path_is_blocked(full)) {
			if (out != pos) memmove(data + out, data + pos, record_len);
			out += record_len;
		}
		pos += record_len;
	}
	return out;
}

static long hydra_getdents64(const struct pt_regs *regs)
{
	char dir[PATHHIDE_MAX_PATH_LEN];
	char *snapshot;
	void __user *user_data;
	int len, filtered, dfd;
	struct file *f;
	char *base;
	char fdpath[PATHHIDE_MAX_PATH_LEN];

	if (!pathhide_should_filter()) goto orig;

	len = (int)regs->__PT_PARM3_REG;
	if (len <= 0 || len > PATHHIDE_DIRENT_LEN) goto orig;

	dfd = (int)regs->__PT_PARM1_REG;
	if (dfd == AT_FDCWD) goto orig;
	f = fget_raw(dfd);
	if (!f) goto orig;
	base = d_path(&f->f_path, fdpath, sizeof(fdpath));
	fput(f);
	if (IS_ERR(base)) goto orig;
	memmove(fdpath, base, strlen(base) + 1);

	user_data = (void __user *)regs->__PT_PARM2_REG;
	snapshot = vmalloc(len);
	if (!snapshot) goto orig;
	if (copy_from_user(snapshot, user_data, len)) {
		vfree(snapshot);
		goto orig;
	}
	filtered = pathhide_filter_dirents(snapshot, len, fdpath);
	if (filtered >= 0 && filtered < len)
		copy_to_user(user_data, snapshot, filtered);
	vfree(snapshot);
	return len;
orig:
	return orig_getdents64(regs);
}

// ---- config load (called from the deferred stealth work) ----
void ksu_pathhide_load_config(void)
{
	char buf[1024] = {0};
	struct file *fp;
	loff_t pos = 0;
	ssize_t n;
	char *line;

	mutex_lock(&pathhide_lock);
	fp = filp_open("/data/adb/hydra/pathhide", O_RDONLY, 0);
	if (IS_ERR(fp)) {
		mutex_unlock(&pathhide_lock);
		return; // no config -> keep defaults
	}
	n = kernel_read(fp, buf, sizeof(buf) - 1, &pos);
	fput(fp);
	if (n <= 0) {
		mutex_unlock(&pathhide_lock);
		return;
	}
	buf[n] = '\0';
	hide_path_count = 0;
	line = buf;
	while (hide_path_count < PATHHIDE_MAX_PATHS && line) {
		char *e = strchr(line, '\n');
		if (e) *e = '\0';
		if (line[0] == '/' && strlen(line) < PATHHIDE_MAX_PATH_LEN) {
			strcpy(hide_paths[hide_path_count], line);
			hide_path_count++;
		}
		line = e ? e + 1 : NULL;
	}
	mutex_unlock(&pathhide_lock);
}

// ---- registration ----
void __init ksu_pathhide_init(void)
{
	strcpy(hide_paths[hide_path_count++], "/data/adb");
	strcpy(hide_paths[hide_path_count++], "/data/adb/ksu");

	ksu_syscall_table_hook(__NR_openat, hydra_openat, &orig_openat);
	ksu_syscall_table_hook(__NR_faccessat, hydra_faccessat, &orig_faccessat);
	ksu_syscall_table_hook(__NR3264_fstatat, hydra_newfstatat, &orig_newfstatat);
	ksu_syscall_table_hook(__NR_getdents64, hydra_getdents64, &orig_getdents64);
	pathhide_ready = true;
}

void __exit ksu_pathhide_exit(void)
{
	if (!pathhide_ready) return;
	ksu_syscall_table_unhook(__NR_openat);
	ksu_syscall_table_unhook(__NR_faccessat);
	ksu_syscall_table_unhook(__NR_newfstatat);
	ksu_syscall_table_unhook(__NR_getdents64);
	pathhide_ready = false;
}
