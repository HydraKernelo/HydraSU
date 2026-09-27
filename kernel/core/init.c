#include <linux/export.h>
#include <linux/fs.h>
#include <linux/kobject.h>
#include <linux/module.h>
#include <linux/utsname.h>
#include <linux/rcupdate.h>
#include <linux/sched.h>
#include <asm/page.h>
#include <linux/workqueue.h>

#include "policy/allowlist.h"
#include "policy/app_profile.h"
#include "policy/feature.h"
#include "klog.h" // IWYU pragma: keep
#include "manager/manager_observer.h"
#include "manager/throne_tracker.h"
#include "hook/syscall_hook_manager.h"
#include "hook/lsm_hook.h"
#include "runtime/ksud.h"
#include "runtime/ksud_boot.h"
#include "supercall/supercall.h"
#include "ksu.h"
#include "infra/file_wrapper.h"
#include "selinux/selinux.h"
#include "hook/syscall_hook.h"
#include "feature/adb_root.h"
#include "feature/selinux_hide.h"
#include "feature/sulog.h"
#include "infra/symbol_resolver.h"

#if defined(__x86_64__) && !defined(CONFIG_KSU_X86_PATCH_SYSCALL_DISPATCHER)
#include <asm/cpufeature.h>
#include <linux/version.h>
#ifndef X86_FEATURE_INDIRECT_SAFE
#error "FATAL: Your kernel is missing the indirect syscall bypass patches!"
#endif
#endif

// workaround for A12-5.10 kernel
// Some third-party kernel (e.g. linegaeOS) uses wrong toolchain, which supports
// CC_HAVE_STACKPROTECTOR_SYSREG while gki's toolchain doesn't.
// Therefore, ksu lkm, which uses gki toolchain, requires this __stack_chk_guard,
// while those third-party kernel can't provide.
// Thus, we manually provide it instead of using kernel's
#if defined(CONFIG_STACKPROTECTOR) &&                                          \
    (defined(CONFIG_ARM64) && defined(MODULE) &&                               \
     !defined(CONFIG_STACKPROTECTOR_PER_TASK))
#include <linux/stackprotector.h>
#include <linux/random.h>
unsigned long __stack_chk_guard __ro_after_init
    __attribute__((visibility("hidden")));

__attribute__((no_stack_protector)) void __init ksu_setup_stack_chk_guard()
{
    unsigned long canary;

    /* Try to get a semi random initial value. */
    get_random_bytes(&canary, sizeof(canary));
    canary ^= LINUX_VERSION_CODE;
    canary &= CANARY_MASK;
    __stack_chk_guard = canary;
}

__attribute__((naked)) int __init kernelsu_init_early(void)
{
    asm("mov x19, x30;\n"
        "bl ksu_setup_stack_chk_guard;\n"
        "mov x30, x19;\n"
        "b kernelsu_init;\n");
}
#define NEED_OWN_STACKPROTECTOR 1
#else
#define NEED_OWN_STACKPROTECTOR 0
#endif

struct cred *ksu_cred;
bool ksu_late_loaded;

#ifdef CONFIG_KSU_DEBUG
bool allow_shell = true;
#else
bool allow_shell = false;
#endif
module_param(allow_shell, bool, 0);

bool ksu_no_custom_rc = false;
module_param_named(norc, ksu_no_custom_rc, bool, 0);

#ifdef MODULE
bool ksu_bundled = false;
module_param_named(bundled, ksu_bundled, bool, 0);
#endif

/* HydraSU stealth: read a small config file from /data/adb/hydra/ */
static int ksu_read_cfg(const char *path, char *buf, size_t size)
{
	struct file *fp;
	loff_t pos = 0;
	ssize_t n;

	if (size < 1)
		return -1;
	fp = filp_open(path, O_RDONLY, 0);
	if (IS_ERR(fp))
		return -1;
	n = kernel_read(fp, buf, size - 1, &pos);
	fput(fp);
	if (n <= 0)
		return -1;
	buf[n] = '\0';
	return (int)n;
}

typedef int (*ksu_set_mem_t)(unsigned long, int);

/* HydraSU stealth: sanitize /proc/version banner in place.
 * The banner lives in .rodata - unlock via resolved set_memory_rw/ro,
 * trim custom-kernel markers from the release segment, re-lock.
 * Official GKI banners (containing "android") are left untouched. */
static void ksu_stealth_sanitize_banner(void)
{
	unsigned long addr = find_kernel_symbol_exact("linux_banner");
	ksu_set_mem_t set_rw = (ksu_set_mem_t)find_kernel_symbol_exact("set_memory_rw");
	ksu_set_mem_t set_ro = (ksu_set_mem_t)find_kernel_symbol_exact("set_memory_ro");
	char *banner, *rel, *dash, *paren;

	if (!addr || !set_rw || !set_ro)
		return;
	banner = (char *)addr;
	if (strncmp(banner, "Linux version ", 14))
		return;
	rel = banner + 14;
	dash = strchr(rel, '-');
	paren = strchr(rel, '(');
	if (!dash || !paren || dash >= paren || strstr(rel, "android"))
		return;

	set_rw((unsigned long)banner & PAGE_MASK, 1);
	memmove(rel + (size_t)(dash - rel), paren - 1, strlen(paren - 1) + 1);
	set_ro((unsigned long)banner & PAGE_MASK, 1);
}

int __init kernelsu_init(void)
{
#if defined(__x86_64__) && !defined(CONFIG_KSU_X86_PATCH_SYSCALL_DISPATCHER)
    // If the kernel has the hardening patch, X86_FEATURE_INDIRECT_SAFE must be set
    if (!boot_cpu_has(X86_FEATURE_INDIRECT_SAFE)) {
        pr_alert("*************************************************************");
        pr_alert("**     NOTICE NOTICE NOTICE NOTICE NOTICE NOTICE NOTICE    **");
        pr_alert("**                                                         **");
        pr_alert("**        X86_FEATURE_INDIRECT_SAFE is not enabled!        **");
        pr_alert("**      KernelSU will abort initialization to prevent      **");
        pr_alert("**                     kernel panic.                       **");
        pr_alert("**                                                         **");
        pr_alert("**     NOTICE NOTICE NOTICE NOTICE NOTICE NOTICE NOTICE    **");
        pr_alert("*************************************************************");
        return -ENOSYS;
    }
#endif

#ifdef MODULE
	ksu_late_loaded = (current->pid != 1);
#else
	ksu_late_loaded = false;
#endif

#ifdef CONFIG_KSU_DEBUG
	pr_alert("*************************************************************");
	pr_alert("**     NOTICE NOTICE NOTICE NOTICE NOTICE NOTICE NOTICE    **");
	pr_alert("**                                                         **");
	pr_alert("**         You are running KernelSU in DEBUG mode          **");
	pr_alert("**                                                         **");
	pr_alert("**     NOTICE NOTICE NOTICE NOTICE NOTICE NOTICE NOTICE    **");
	pr_alert("*************************************************************");
#endif
	if (allow_shell) {
		pr_alert("shell is allowed at init!");
	}

	ksu_cred = prepare_creds();
	if (!ksu_cred) {
		pr_err("prepare cred failed!\n");
		return -ENOSYS;
	}

	ksu_init_symbol_resolver();
	ksu_syscall_hook_init();

	ksu_feature_init();
	ksu_sulog_init();
	ksu_adb_root_init();
	ksu_lsm_hook_init();
	ksu_selinux_hide_init();

	ksu_supercalls_init();
	ksu_app_profile_init();

	if (ksu_late_loaded) {
		pr_info("late load mode, skipping kprobe hooks\n");

		apply_kernelsu_rules();
		cache_sid();
		setup_ksu_cred();

		// Grant current process (ksud late-load) root
		// with KSU SELinux domain before enforcing SELinux, so it
		// can continue to access /data/app etc. after enforcement.
		escape_to_root_for_init();

		ksu_allowlist_init();
		ksu_load_allow_list();

		ksu_syscall_hook_manager_init();

		ksu_throne_tracker_init();
		ksu_observer_init();
		ksu_file_wrapper_init();

		ksu_boot_completed = true;
		track_throne(false);

		if (!getenforce()) {
			pr_info("Permissive SELinux, enforcing\n");
			setenforce(true);
		}

	} else {
		ksu_syscall_hook_manager_init();

		ksu_allowlist_init();

		ksu_throne_tracker_init();

		ksu_ksud_init();

		ksu_file_wrapper_init();
	}

#ifdef MODULE
#ifndef CONFIG_KSU_DEBUG
	kobject_del(&THIS_MODULE->mkobj.kobj);
	/* HydraSU stealth: also remove from the modules list so that
	 * /proc/modules and lsmod show nothing (Diamorphine technique).
	 * After this the module cannot be rmmod'ed - that is intended. */
	list_del_init(&THIS_MODULE->list);

	/* HydraSU stealth: optional uname rename + banner trim, configured by
	 * the manager via root-owned files under /data/adb/hydra/:
	 *   uname_hide = "1"   enable (absent/0 = off, the default)
	 *   uname_name = custom  (empty = auto-generate an official-style
	 *     "<base>-androidXX-0-g<hash>" release string)
	 * The config survives manager reinstalls and kernel re-flashes.
	 * The module reads it at init - a toggle applies after reboot. */
	{
		char flag[8] = {0};
		char custom[__NEW_UTS_LEN + 1] = {0};
		char newrel[__NEW_UTS_LEN + 1] = {0};
		char suffix[48];
		char hexc[] = "0123456789abcdef";
		char *dash;
		size_t n;
		int i, sl;

		if (ksu_read_cfg("/data/adb/hydra/uname_hide", flag, sizeof(flag) - 1) > 0 &&
		    flag[0] == '1') {
			ksu_stealth_sanitize_banner();

			if (ksu_read_cfg("/data/adb/hydra/uname_name", custom, __NEW_UTS_LEN) > 0) {
				char *e = custom + strlen(custom);
				while (e > custom && (e[-1] == '\n' || e[-1] == '\r' || e[-1] == ' '))
					*--e = '\0';
			}
			if (custom[0]) {
				strscpy(newrel, custom, sizeof(newrel));
			} else {
				dash = strchr(init_uts_ns.name.release, '-');
				n = dash ? (size_t)(dash - init_uts_ns.name.release)
					 : strlen(init_uts_ns.name.release);
				if (n > __NEW_UTS_LEN - 40)
					n = __NEW_UTS_LEN - 40;
				memcpy(newrel, init_uts_ns.name.release, n);
#if LINUX_VERSION_CODE < KERNEL_VERSION(5, 15, 0)
				scnprintf(suffix, sizeof(suffix), "-android12-0-g");
#elif LINUX_VERSION_CODE < KERNEL_VERSION(6, 1, 0)
				scnprintf(suffix, sizeof(suffix), "-android13-0-g");
#elif LINUX_VERSION_CODE < KERNEL_VERSION(6, 6, 0)
				scnprintf(suffix, sizeof(suffix), "-android14-0-g");
#elif LINUX_VERSION_CODE < KERNEL_VERSION(6, 12, 0)
				scnprintf(suffix, sizeof(suffix), "-android15-0-g");
#elif LINUX_VERSION_CODE < KERNEL_VERSION(6, 18, 0)
				scnprintf(suffix, sizeof(suffix), "-android16-0-g");
#else
				scnprintf(suffix, sizeof(suffix), "-android17-0-g");
#endif
				sl = strlen(suffix);
				for (i = 0; i < 12; i++) {
					u8 rb;
					get_random_bytes(&rb, 1);
					suffix[sl + i] = hexc[rb % 16];
					suffix[sl + i + 1] = '\0';
				}
				strncat(newrel, suffix, sizeof(newrel) - strlen(newrel) - 1);
			}
			strscpy(init_uts_ns.name.release, newrel, sizeof(init_uts_ns.name.release));
		}
	}
#endif
#endif
	return 0;
}

void __exit kernelsu_exit(void)
{
	// Phase 1: Stop all hooks first to prevent new callbacks
	ksu_syscall_hook_manager_exit();

	ksu_supercalls_exit();

	if (!ksu_late_loaded)
		ksu_ksud_exit();

	// Wait for any in-flight RCU readers (e.g. handler traversing allow_list)
	synchronize_rcu();

	// Phase 2: Now safe to release data structures
	ksu_observer_exit();

	ksu_throne_tracker_exit();

	ksu_allowlist_exit();

	ksu_selinux_hide_exit();

	ksu_lsm_hook_exit();

	ksu_adb_root_exit();

	ksu_sulog_exit();

	ksu_feature_exit();

	put_cred(ksu_cred);
}

#if NEED_OWN_STACKPROTECTOR
module_init(kernelsu_init_early);
#else
module_init(kernelsu_init);
#endif
module_exit(kernelsu_exit);

MODULE_LICENSE("GPL");
MODULE_AUTHOR("weishu");
MODULE_DESCRIPTION("Android KernelSU");
#if LINUX_VERSION_CODE >= KERNEL_VERSION(6, 13, 0)
MODULE_IMPORT_NS("VFS_internal_I_am_really_a_filesystem_and_am_NOT_a_driver");
#elif LINUX_VERSION_CODE >= KERNEL_VERSION(5, 0, 0)
MODULE_IMPORT_NS(VFS_internal_I_am_really_a_filesystem_and_am_NOT_a_driver);
#endif
