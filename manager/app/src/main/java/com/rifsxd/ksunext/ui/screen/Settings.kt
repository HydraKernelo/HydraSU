package com.rifsxd.ksunext.ui.screen

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.system.OsConstants
import androidx.compose.material.icons.filled.RadioButtonChecked
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Article
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import com.rifsxd.ksunext.ui.LocalNavBarEnabled
import com.rifsxd.ksunext.ui.LocalScrollState
import com.rifsxd.ksunext.ui.rememberScrollConnection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.core.content.edit
import androidx.compose.ui.graphics.Color
import androidx.compose.material3.ListItemDefaults
import com.maxkeppeker.sheets.core.models.base.Header
import com.maxkeppeker.sheets.core.models.base.IconSource
import com.maxkeppeker.sheets.core.models.base.rememberUseCaseState
import com.maxkeppeler.sheets.list.ListDialog
import com.maxkeppeler.sheets.list.models.ListOption
import com.maxkeppeler.sheets.list.models.ListSelection
import com.ramcosta.composedestinations.annotation.Destination
import com.ramcosta.composedestinations.annotation.RootGraph
import com.ramcosta.composedestinations.generated.destinations.*
import com.ramcosta.composedestinations.navigation.DestinationsNavigator
import com.ramcosta.composedestinations.navigation.EmptyDestinationsNavigator
import com.rifsxd.ksunext.BuildConfig
import com.rifsxd.ksunext.Natives
import com.rifsxd.ksunext.R
import com.rifsxd.ksunext.ui.component.*
import com.rifsxd.ksunext.ui.util.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.topjohnwu.superuser.Shell
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * @author weishu
 * @date 2023/1/1.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Destination<RootGraph>
@Composable
fun SettingScreen(navigator: DestinationsNavigator) {
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior(rememberTopAppBarState())
    val snackBarHost = LocalSnackbarHost.current

    val isManager = Natives.isManager
    val ksuVersion = if (isManager) Natives.version else null

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    val scrollState = LocalScrollState.current
    val navBarEnabled = LocalNavBarEnabled.current
    val isNavBarHidden = (scrollState?.isScrollingDown?.value ?: false) || (navBarEnabled?.value == false)
    val navBarPadding =
        WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + if (isNavBarHidden) 0.dp else 112.dp

    val bottomBarScrollState = LocalScrollState.current
    val bottomBarScrollConnection = if (bottomBarScrollState != null) {
        rememberScrollConnection(
            isScrollingDown = bottomBarScrollState.isScrollingDown,
            scrollOffset = bottomBarScrollState.scrollOffset,
            previousScrollOffset = bottomBarScrollState.previousScrollOffset,
            threshold = 30f
        )
    } else null

    val aboutDialog = rememberCustomDialog {
        AboutDialog(it)
    }
    val loadingDialog = rememberLoadingDialog()

    var isUnrooted by remember { mutableStateOf(!rootAvailable()) }

    LaunchedEffect(Unit) {
        isUnrooted = !rootAvailable()
    }

    val exportBugreportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/gzip")
    ) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch(Dispatchers.IO) {
            loadingDialog.show()
            context.contentResolver.openOutputStream(uri)?.use { output ->
                val bugReport = if (isUnrooted) getBugreportFileUnrooted(context) else getBugreportFile(context)
                    bugReport.inputStream().use {
                    it.copyTo(output)
                }
            }
            loadingDialog.hide()
            snackBarHost.showSnackbar(context.getString(R.string.log_saved))
        }
    }

    var suCompatStatus by rememberSaveable { mutableStateOf("") }
    var kernelUmountStatus by rememberSaveable { mutableStateOf("") }
    var adbRootStatus by rememberSaveable { mutableStateOf("") }
    var selinuxHideStatus by rememberSaveable { mutableStateOf("") }
    var sulogStatus by rememberSaveable { mutableStateOf("") }
    var isSulogEnabled by rememberSaveable { mutableStateOf(false) }
    var avcSpoofStatus by rememberSaveable { mutableStateOf("") }

    LaunchedEffect(Unit) {
        suCompatStatus = getFeatureStatus("su_compat")
        kernelUmountStatus = getFeatureStatus("kernel_umount")
        sulogStatus = getFeatureStatus("sulog")
        isSulogEnabled = getFeaturePersistValue("sulog") == 1L
        adbRootStatus = getFeatureStatus("adb_root")

        selinuxHideStatus = getFeatureStatus("selinux_hide")
        avcSpoofStatus = getFeatureStatus("avc_spoof")
    }

    Scaffold(
        topBar = {
            TopBar(scrollBehavior = scrollBehavior)
        },
        snackbarHost = { SnackbarHost(snackBarHost, modifier = Modifier.padding(bottom = navBarPadding)) },
        contentWindowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal)
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .let { modifier ->
                    if (bottomBarScrollConnection != null) {
                        modifier
                            .nestedScroll(bottomBarScrollConnection)
                            .nestedScroll(scrollBehavior.nestedScrollConnection)
                    } else {
                        modifier.nestedScroll(scrollBehavior.nestedScrollConnection)
                    }
                }
                .verticalScroll(rememberScrollState())
                .padding(top = 16.dp)
                .padding(bottom = navBarPadding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            if (ksuVersion != null) {
                KernelFeaturesCard(
                    suCompatStatus = suCompatStatus,
                    kernelUmountStatus = kernelUmountStatus,
                    sulogStatusParam = sulogStatus,
                    isSulogEnabled = isSulogEnabled,
                    onSulogEnabledChange = { isSulogEnabled = it },
                    adbRootStatus = adbRootStatus,
                    selinuxHideStatus = selinuxHideStatus,
                    avcSpoofStatus = avcSpoofStatus,
                    scope = scope
                )
                SecurityCard(
                    navigator = navigator,
                    loadingDialog = loadingDialog
                )
            }

            AppSettingsCard(
                navigator = navigator,
                prefs = prefs,
                aboutDialog = aboutDialog,
                exportBugreportLauncher = exportBugreportLauncher,
                loadingDialog = loadingDialog,
                scope = scope,
                context = context,
                isUnrooted = isUnrooted
            )

            Spacer(Modifier)
        }
    }
}

@Composable
private fun KernelFeaturesCard(
    suCompatStatus: String,
    kernelUmountStatus: String,
    sulogStatusParam: String,
    isSulogEnabled: Boolean,
    onSulogEnabledChange: (Boolean) -> Unit,
    adbRootStatus: String,
    selinuxHideStatus: String,
    avcSpoofStatus: String,
    scope: kotlinx.coroutines.CoroutineScope
) {
    val context = LocalContext.current
    val suCompatSupported = suCompatStatus == "supported"
    val kernelUmountSupported = kernelUmountStatus == "supported"
    val sulogSupported = sulogStatusParam == "supported"
    val adbRootSupported = adbRootStatus == "supported"
    val selinuxHideSupported = selinuxHideStatus == "supported"
    val avcSpoofSupported = avcSpoofStatus == "supported"

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            val parasiteCtx = androidx.compose.ui.platform.LocalContext.current
            var parasiteEnabled by rememberSaveable {
                mutableStateOf(
                    parasiteCtx.getSharedPreferences("parasite", Context.MODE_PRIVATE)
                        .getBoolean("enabled", true)
                )
            }
            SwitchItem(
                icon = Icons.Filled.Language,
                title = "寄生工作台 (PWA 控制台)",
                summary = "浏览器访问 http://127.0.0.1:38214 · 关闭将停止本地服务",
                checked = parasiteEnabled,
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)),
                colors = ListItemDefaults.colors(containerColor = Color.Transparent)
            ) {
                parasiteCtx.getSharedPreferences("parasite", Context.MODE_PRIVATE)
                    .edit().putBoolean("enabled", it).apply()
                parasiteEnabled = it
                if (it) {
                    val si = Intent(parasiteCtx, com.rifsxd.ksunext.ParasiteService::class.java)
                    if (android.os.Build.VERSION.SDK_INT >= 26) {
                        parasiteCtx.startForegroundService(si)
                    } else {
                        parasiteCtx.startService(si)
                    }
                    Toast.makeText(parasiteCtx, "寄生工作台已启动", Toast.LENGTH_SHORT).show()
                } else {
                    parasiteCtx.stopService(Intent(parasiteCtx, com.rifsxd.ksunext.ParasiteService::class.java))
                    Toast.makeText(parasiteCtx, "寄生工作台已停止", Toast.LENGTH_SHORT).show()
                }
            }

            // ---- 隐匿模式 ----
            val stealthCtx = androidx.compose.ui.platform.LocalContext.current
            val stealthPrefs = remember { stealthCtx.getSharedPreferences("stealth", Context.MODE_PRIVATE) }
            var disguiseOn by rememberSaveable { mutableStateOf(stealthPrefs.getBoolean("disguise", false)) }
            var iconHidden by rememberSaveable { mutableStateOf(stealthPrefs.getBoolean("noicon", false)) }

            fun setAlias(name: String, enabled: Boolean) {
                val cn = android.content.ComponentName(stealthCtx.packageName, name)
                stealthCtx.packageManager.setComponentEnabledSetting(
                    cn,
                    if (enabled) android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_ENABLED
                    else android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                    android.content.pm.PackageManager.DONT_KILL_APP
                )
            }
            fun applyStealth(disguise: Boolean, noIcon: Boolean) {
                setAlias("com.rifsxd.ksunext.ui.LauncherDefault", !noIcon && !disguise)
                setAlias("com.rifsxd.ksunext.ui.LauncherStealth", !noIcon && disguise)
            }

            SwitchItem(
                icon = Icons.Filled.Build,
                title = "隐匿伪装 · 系统更新外观",
                summary = "桌面图标与名称变为「系统更新」，关闭即恢复 HydraSU",
                checked = disguiseOn,
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)),
                colors = ListItemDefaults.colors(containerColor = Color.Transparent)
            ) {
                stealthPrefs.edit().putBoolean("disguise", it).apply()
                disguiseOn = it
                applyStealth(it, iconHidden)
                Toast.makeText(stealthCtx, if (it) "已伪装为「系统更新」" else "已恢复 HydraSU 外观", Toast.LENGTH_SHORT).show()
            }

            SwitchItem(
                icon = Icons.Filled.VisibilityOff,
                title = "隐藏桌面图标",
                summary = "桌面无入口 · 通过浏览器寄生控制台管理",
                checked = iconHidden,
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)),
                colors = ListItemDefaults.colors(containerColor = Color.Transparent)
            ) {
                stealthPrefs.edit().putBoolean("noicon", it).apply()
                iconHidden = it
                applyStealth(disguiseOn, it)
                Toast.makeText(stealthCtx, if (it) "桌面图标已隐藏" else "桌面图标已恢复", Toast.LENGTH_SHORT).show()
            }

            // ---- 隐藏内核名字 ----
            val cfgDir = "/data/adb/hydra"
            fun readCfg(name: String): String =
                Shell.cmd("cat $cfgDir/$name 2>/dev/null").exec().out.joinToString("").trim()

            fun writeCfg(name: String, value: String): Boolean {
                Shell.cmd("mkdir -p $cfgDir").exec()
                Shell.cmd("echo '$value' > $cfgDir/$name").exec()
                val back = Shell.cmd("cat $cfgDir/$name 2>/dev/null").exec().out.joinToString("").trim()
                return back == value.trim()
            }

            var unameHide by rememberSaveable { mutableStateOf(false) }
            var unameName by rememberSaveable { mutableStateOf("") }
            var unameLoaded by rememberSaveable { mutableStateOf(false) }
            LaunchedEffect(Unit) {
                withContext(Dispatchers.IO) {
                    val h = readCfg("uname_hide")
                    val n = readCfg("uname_name")
                    unameHide = h == "1"
                    unameName = n
                    unameLoaded = true
                }
            }
            SwitchItem(
                icon = Icons.Filled.Memory,
                title = "隐藏内核名字",
                summary = "开启后重启生效 · 未自定义时按内核版本自动生成标准命名 · 默认关闭",
                checked = unameHide,
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)),
                colors = ListItemDefaults.colors(containerColor = Color.Transparent)
            ) {
                val okw = writeCfg("uname_hide", if (it) "1" else "0")
                unameHide = it
                Toast.makeText(stealthCtx,
                    if (okw) "已保存 · 重启后生效" else "写入 /data/adb/hydra 失败（root shell 异常）",
                    Toast.LENGTH_LONG).show()
                // 首次开启：提示并自动安装 SELinux 修复模块（放行内核读取配置）
                if (it && okw) {
                    val sp = stealthCtx.getSharedPreferences("hydra", Context.MODE_PRIVATE)
                    if (!sp.getBoolean("selfix_done", false)) {
                        sp.edit().putBoolean("selfix_done", true).apply()
                        val zipB64 = "UEsDBBQAAAAIAMMUQV2jNFxctgAAANUAAAAMAAAAY3VzdG9taXplLnNoC/b2DAj1i/IMsDXgKs2MLyjKzCtRUNJV8KhMKUoMDlUIdvXJzCutUHDLrFBCUfBkx66nba3PFux4On/+syn7XizsUdBPSSxJ1E9MSdLPAOlWeLF+99P+ac/mNr+cOQFV88v23qcT1j+d0Pd8ykaF7OLSFIUX7auedq14umvK8ykrFIpTC/JzMpMrFV4sb3naMRNV77PpC17OaXjauu3J7mnPJvc+2Tvn/Z4eqIG9m5/2r1fiAgBQSwMEFAAAAAgAwxRBXRNwqdqeAAAAzQAAAAsAAABtb2R1bGUucHJvcDXOOw6CUBCF4Z5VTKkNyAIoiNGYaEesycAMMuF6h9wHj90rGMuTLyf5hYp+JYc+1p6N2LjUnSyJxTcXtw2qJ1SXxwZw/cLEzovaYsrT03+clbjIE4yhV/d73dlZNpoQ+9bJGLZLaYzOMOwCpG8UC0HBMRJkhAEzpCbba6BV28kLDnELgV6IYcbQ9uyOKZTjaIQJmhUGHwkwQKMa0uQDUEsDBBQAAAAIAMMUQV1Lhb3oTgAAAKoAAAANAAAAc2Vwb2xpY3kucnVsZY3LwQmAQAwEwL9VbB8Wc6wm6mG4kxjwIfYuCCIIgp/5Dc3qhlm9qIHSJWEwDdkUkh07Rg1GOFal9xPqogWuFBxtw+988ey/zcqcXvVeJ1BLAwQUAAAACADDFEFdrpBMTWwBAAC1AQAAKQAAAE1FVEEtSU5GL2NvbS9nb29nbGUvYW5kcm9pZC91cGRhdGUtYmluYXJ5TVBdSwJBFH2fX3EzQQh0+nguCFKQPqF66SVWZ3QXd3eWnd2yLwhqtSCyQIugIFHBlzaCHsKsHxPNrj35F1pdieDCvefewzmHOz6G+R63qIYzio65jMZhObkxH0+vpHCWaTjPWF6lWNKJyRSCbYNIFo0HXMncg6/jalDgPZZFuQSL1NSpur6Jl6W8wgvgtevi4Va4572GI+7awmmJSiPQF+6FV3v9OX7vfV771RffrftXpQGh5Pgf7h/fu2wFp373TLgnved3UbmBrM0tpin7NMHlfve8373wnpqiefr91hHOvejU0OrmRmphNjqNttJrqfRScjY6gzRm6xbgILgE03OY0B2s26qKbH1fMSDOIBIdsSMQ0xixVZowTGbEIPbfMICcmjtKdgQkYuGJoO9mcsNe0Ic4TkIrLJEMDtX4dvg2XCwWuL0tU9Wg5v8ocHgIBwiAZuUgzRiEyXKSolISGeyLigVT6AgNh0n0C1BLAwQUAAAACADDFEFdewtuYgoAAAAIAAAAKgAAAE1FVEEtSU5GL2NvbS9nb29nbGUvYW5kcm9pZC91cGRhdGVyLXNjcmlwdFP2dXT3DPbmAgBQSwECFAAUAAAACADDFEFdozRcXLYAAADVAAAADAAAAAAAAAAAAAAAtoEAAAAAY3VzdG9taXplLnNoUEsBAhQAFAAAAAgAwxRBXRNwqdqeAAAAzQAAAAsAAAAAAAAAAAAAALaB4AAAAG1vZHVsZS5wcm9wUEsBAhQAFAAAAAgAwxRBXUuFvehOAAAAqgAAAA0AAAAAAAAAAAAAALaBpwEAAHNlcG9saWN5LnJ1bGVQSwECFAAUAAAACADDFEFdrpBMTWwBAAC1AQAAKQAAAAAAAAAAAAAAtoEgAgAATUVUQS1JTkYvY29tL2dvb2dsZS9hbmRyb2lkL3VwZGF0ZS1iaW5hcnlQSwECFAAUAAAACADDFEFdewtuYgoAAAAIAAAAKgAAAAAAAAAAAAAAtoHTAwAATUVUQS1JTkYvY29tL2dvb2dsZS9hbmRyb2lkL3VwZGF0ZXItc2NyaXB0UEsFBgAAAAAFAAUAXQEAACUEAAAAAA=="
                        android.app.AlertDialog.Builder(stealthCtx)
                            .setTitle("安装 SELinux 修复模块")
                            .setMessage("内核隐藏需要读取配置文件，系统默认拦截内核读取。将自动安装 HydraSU SELinux Fix 模块放行（约 2KB，重启后生效）。")
                            .setPositiveButton("自动安装") { _, _ ->
                                Thread {
                                    try {
                                        val f = java.io.File(stealthCtx.cacheDir, "selfix.zip")
                                        java.io.FileOutputStream(f).write(
                                            android.util.Base64.decode(zipB64, android.util.Base64.DEFAULT))
                                        val r = Shell.cmd(
                                            "/data/adb/ksu/bin/ksud module install '${f.absolutePath}'").exec()
                                        f.delete()
                                        android.os.Handler(stealthCtx.mainLooper).post {
                                            Toast.makeText(stealthCtx,
                                                if (r.code == 0) "SELinux Fix 已安装 · 重启后生效"
                                                else "安装失败（code ${r.code}）",
                                                Toast.LENGTH_LONG).show()
                                        }
                                    } catch (e: Throwable) {
                                        android.os.Handler(stealthCtx.mainLooper).post {
                                            Toast.makeText(stealthCtx, "安装异常: ${e.message}",
                                                Toast.LENGTH_LONG).show()
                                        }
                                    }
                                }.start()
                            }
                            .setNegativeButton("暂不", null)
                            .show()
                    }
                }
            }

            OutlinedTextField(
                value = unameName,
                onValueChange = { unameName = it },
                label = { Text("自定义内核名（留空 = 自动生成）") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Button(onClick = {
                val okw = writeCfg("uname_name", unameName)
                Toast.makeText(stealthCtx,
                    if (okw) "已保存 · 重启后生效" else "写入失败（root shell 异常）",
                    Toast.LENGTH_LONG).show()
            }) { Text("保存内核名") }

            // ---- 寄生控制台 Token ----
            var hydraToken by rememberSaveable { mutableStateOf("") }
            LaunchedEffect(Unit) {
                withContext(Dispatchers.IO) {
                    hydraToken = Shell.cmd("cat /data/adb/hydra/token 2>/dev/null")
                        .exec().out.joinToString("").trim()
                }
            }
            OutlinedTextField(
                value = hydraToken,
                onValueChange = { },
                label = { Text("寄生控制台访问密钥") },
                singleLine = true,
                readOnly = true,
                modifier = Modifier.fillMaxWidth()
            )
            Button(onClick = {
                val full = "http://127.0.0.1:38214/" + hydraToken + "/"
                val cb = stealthCtx.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                cb.setPrimaryClip(android.content.ClipData.newPlainText("hydra_url", full))
                Toast.makeText(stealthCtx, "完整地址已复制，粘贴到浏览器打开", Toast.LENGTH_LONG).show()
            }) { Text("复制完整地址") }
            Button(onClick = {
                try {
                    val full = "http://127.0.0.1:38214/" + hydraToken + "/"
                    stealthCtx.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW,
                        android.net.Uri.parse(full)))
                } catch (e: Exception) {
                    Toast.makeText(stealthCtx, "没有可用浏览器", Toast.LENGTH_SHORT).show()
                }
            }) { Text("在浏览器打开控制台") }


            if (!unameLoaded) {
                Text(
                    text = "配置读取中…",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }

            var umountChecked by rememberSaveable {
                mutableStateOf(Natives.isDefaultUmountModules())
            }

            SwitchItem(
                icon = Icons.Filled.FolderDelete,
                title = stringResource(R.string.settings_umount_modules_default),
                summary = stringResource(R.string.settings_umount_modules_default_summary),
                checked = umountChecked,
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)),
                colors = ListItemDefaults.colors(containerColor = Color.Transparent)
            ) {
                if (Natives.setDefaultUmountModules(it)) {
                    umountChecked = it
                }
            }

            var isSuEnabled by rememberSaveable {
                mutableStateOf(Natives.isSuEnabled())
            }
            SwitchItem(
                icon = Icons.Filled.RemoveModerator,
                title = stringResource(R.string.settings_enable_su),
                summary = if (suCompatSupported) {
                    stringResource(R.string.settings_enable_su_summary)
                } else {
                    stringResource(id = R.string.feature_status_unsupported_summary)
                },
                checked = isSuEnabled,
                enabled = suCompatSupported,
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)),
                colors = ListItemDefaults.colors(containerColor = Color.Transparent)
            ) { checked ->
                val prefsLocal = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
                if (Natives.setSuEnabled(checked)) {
                    execKsud("feature save", true)
                    prefsLocal.edit { putInt("su_compat_mode", if (checked) 0 else 2) }
                    isSuEnabled = checked
                }
            }

            var isKernelUmountEnabled by rememberSaveable {
                mutableStateOf(Natives.isKernelUmountEnabled())
            }
            SwitchItem(
                icon = Icons.Filled.RemoveCircle,
                title = stringResource(id = R.string.settings_enable_kernel_umount),
                summary = if (kernelUmountSupported) {
                    stringResource(id = R.string.settings_enable_kernel_umount_summary)
                } else {
                    stringResource(id = R.string.feature_status_unsupported_summary)
                },
                checked = isKernelUmountEnabled,
                enabled = kernelUmountSupported,
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)),
                colors = ListItemDefaults.colors(containerColor = Color.Transparent)
            ) { checked ->
                val prefsLocal = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
                if (Natives.setKernelUmountEnabled(checked)) {
                    execKsud("feature save", true)
                    prefsLocal.edit { putInt("kernel_umount_mode", if (checked) 0 else 2) }
                    isKernelUmountEnabled = checked
                }
            }

            val sulogSummary = when (sulogStatusParam) {
                "unsupported" -> stringResource(id = R.string.feature_status_unsupported_summary)
                "managed" -> stringResource(id = R.string.feature_status_managed_summary)
                else -> stringResource(id = R.string.settings_sulog_summary)
            }
            SwitchItem(
                icon = Icons.AutoMirrored.Filled.Article,
                title = stringResource(id = R.string.settings_sulog),
                summary = sulogSummary,
                checked = isSulogEnabled,
                enabled = sulogSupported,
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)),
                colors = ListItemDefaults.colors(containerColor = Color.Transparent)
            ) { checked ->
                if (execKsud("feature set sulog ${if (checked) 1 else 0}", true)) {
                    execKsud("feature save", true)
                    onSulogEnabledChange(checked)
                }
            }

            var isAdbRootEnabled by rememberSaveable {
                mutableStateOf(Natives.isAdbRootEnabled())
            }
            SwitchItem(
                icon = Icons.Filled.Usb,
                title = stringResource(id = R.string.settings_adb_root),
                summary = if (adbRootSupported) {
                    stringResource(id = R.string.settings_adb_root_summary)
                } else {
                    stringResource(id = R.string.feature_status_unsupported_summary)
                },
                checked = isAdbRootEnabled,
                enabled = adbRootSupported,
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)),
                colors = ListItemDefaults.colors(containerColor = Color.Transparent)
            ) { checked ->
                val prefsLocal = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
                if (Natives.setAdbRootEnabled(checked)) {
                    execKsud("feature save", true)
                    prefsLocal.edit { putInt("adb_root_mode", if (checked) 0 else 2) }
                    com.topjohnwu.superuser.ShellUtils.fastCmd("setprop ctl.restart adbd")
                    isAdbRootEnabled = checked
                }
            }

            var isSelinuxHideEnabled by rememberSaveable {
                mutableStateOf(Natives.isSelinuxHideEnabled())
            }
            SwitchItem(
                icon = Icons.Filled.Policy,
                title = stringResource(id = R.string.settings_selinux_hide),
                summary = if (selinuxHideSupported) {
                    stringResource(id = R.string.settings_selinux_hide_summary)
                } else {
                    stringResource(id = R.string.feature_status_unsupported_summary)
                },
                checked = isSelinuxHideEnabled,
                enabled = selinuxHideSupported,
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)),
                colors = ListItemDefaults.colors(containerColor = Color.Transparent)
            ) { checked ->
                scope.launch(Dispatchers.IO) {
                    val prefsLocal = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
                    val status = Natives.setSelinuxHideEnabled(checked)
                    execKsud("feature save", true)
                    prefsLocal.edit { putInt("selinux_hide_mode", if (checked) 0 else 2) }
                    isSelinuxHideEnabled = checked
                    when (status) {
                        0 -> {}
                        -OsConstants.EAGAIN -> {
                            withContext(Dispatchers.Main) {
                                Toast.makeText(
                                    context, R.string.settings_selinux_hide_reboot_required,
                                    Toast.LENGTH_LONG
                                ).show()
                            }
                        }

                        else -> {
                            withContext(Dispatchers.Main) {
                                Toast.makeText(
                                    context, context.getString(R.string.settings_selinux_hide_failed, status),
                                    Toast.LENGTH_LONG
                                ).show()
                            }
                        }
                    }
                }
            }

            var isAvcSpoofEnabled by rememberSaveable {
                mutableStateOf(Natives.isAvcSpoofEnabled())
            }
            SwitchItem(
                icon = Icons.Filled.Shield,
                title = stringResource(id = R.string.settings_enable_avc_spoof),
                summary = if (avcSpoofSupported) {
                    stringResource(id = R.string.settings_enable_avc_spoof_summary)
                } else {
                    stringResource(id = R.string.feature_status_unsupported_summary)
                },
                checked = isAvcSpoofEnabled,
                enabled = avcSpoofSupported,
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)),
                colors = ListItemDefaults.colors(containerColor = Color.Transparent)
            ) { checked ->
                val prefsLocal = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
                if (Natives.setAvcSpoofEnabled(checked)) {
                    execKsud("feature save", true)
                    prefsLocal.edit { putInt("avc_spoof_mode", if (checked) 0 else 2) }
                    isAvcSpoofEnabled = checked
                }
            }
        }
    }
}

@Composable
private fun SecurityCard(
    navigator: DestinationsNavigator,
    loadingDialog: LoadingDialogHandle
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            var isSelinuxPermissive by rememberSaveable {
                mutableStateOf(getSelinuxEnforce() == false)
            }

            SwitchItem(
                icon = Icons.Filled.Security,
                title = stringResource(R.string.set_selinux),
                summary = stringResource(R.string.set_selinux_summary),
                checked = isSelinuxPermissive,
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)),
                colors = ListItemDefaults.colors(containerColor = Color.Transparent)
            ) { checked ->
                val shouldEnforce = !checked
                if (setSelinuxEnforce(shouldEnforce)) {
                    isSelinuxPermissive = !shouldEnforce
                }
            }

            ListItem(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { navigator.navigate(AppProfileTemplateScreenDestination) },
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                leadingContent = { Icon(Icons.Filled.Fence, null) },
                headlineContent = {
                    Text(
                        text = stringResource(R.string.settings_profile_template),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                },
                supportingContent = {
                    Text(stringResource(R.string.settings_profile_template_summary))
                }
            )

            ListItem(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { navigator.navigate(BackupRestoreScreenDestination) },
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                leadingContent = { Icon(Icons.Filled.Backup, null) },
                headlineContent = {
                    Text(
                        text = stringResource(R.string.backup_restore),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            )

            ListItem(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { navigator.navigate(DeveloperScreenDestination) },
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                leadingContent = { Icon(Icons.Filled.DeveloperBoard, null) },
                headlineContent = {
                    Text(
                        text = stringResource(R.string.developer),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            )

            if (Natives.isLkmMode) {
                UninstallItem(
                    navigator = navigator,
                    withLoading = { loadingDialog.withLoading(it) },
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp))
                )
            }
        }
    }
}

@Composable
private fun AppSettingsCard(
    navigator: DestinationsNavigator,
    prefs: android.content.SharedPreferences,
    aboutDialog: DialogHandle,
    exportBugreportLauncher: androidx.activity.result.ActivityResultLauncher<String>,
    loadingDialog: LoadingDialogHandle,
    scope: kotlinx.coroutines.CoroutineScope,
    context: android.content.Context,
    isUnrooted: Boolean
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            var checkUpdate by rememberSaveable {
                mutableStateOf(prefs.getBoolean("check_update", true))
            }

            SwitchItem(
                icon = Icons.Filled.Update,
                title = stringResource(R.string.settings_check_update),
                summary = stringResource(R.string.settings_check_update_summary),
                checked = checkUpdate,
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)),
                colors = ListItemDefaults.colors(containerColor = Color.Transparent)
            ) {
                prefs.edit { putBoolean("check_update", it) }
                checkUpdate = it
            }

            var requireBiometric by rememberSaveable {
                mutableStateOf(prefs.getBoolean("enable_biometric_lock", false))
            }
            var appLockTimeout by rememberSaveable {
                mutableStateOf(prefs.getLong("app_lock_timeout", 60000L))
            }
            var showTimeoutMenu by remember { mutableStateOf(false) }

            val timeoutOptions = remember {
                listOf(
                    0L to R.string.settings_app_lock_timeout_immediate,
                    60000L to R.string.settings_app_lock_timeout_1m,
                    300000L to R.string.settings_app_lock_timeout_5m
                )
            }

            val timeoutLabelRes = timeoutOptions.find { it.first == appLockTimeout }?.second
                ?: R.string.settings_app_lock_timeout_1m
            val timeoutLabel = stringResource(timeoutLabelRes)

            ListItem(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .toggleable(
                        value = requireBiometric,
                        role = Role.Switch,
                        onValueChange = { newValue ->
                            requireBiometric = newValue
                            prefs.edit { putBoolean("enable_biometric_lock", newValue) }
                        }
                    ),
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    leadingContent = { Icon(Icons.Filled.Lock, null) },
                    headlineContent = { Text(stringResource(R.string.settings_app_lock)) },
                    supportingContent = {
                        Column(modifier = Modifier.animateContentSize()) {
                            Text(stringResource(R.string.settings_app_lock_summary))
                            if (requireBiometric) {
                                Box {
                                    Text(
                                        text = "${stringResource(R.string.settings_app_lock_timeout)}: $timeoutLabel",
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier
                                            .padding(top = 4.dp)
                                            .clickable { showTimeoutMenu = true }
                                            .padding(vertical = 4.dp)
                                    )
                                    DropdownMenu(
                                        expanded = showTimeoutMenu,
                                        onDismissRequest = { showTimeoutMenu = false }
                                    ) {
                                        timeoutOptions.forEach { (time, stringRes) ->
                                            DropdownMenuItem(
                                                text = { Text(stringResource(stringRes)) },
                                                trailingIcon = {
                                                    Icon(
                                                        imageVector = if (appLockTimeout == time) Icons.Filled.RadioButtonChecked else Icons.Filled.RadioButtonUnchecked,
                                                        contentDescription = null,
                                                        tint = if (appLockTimeout == time) androidx.compose.material3.MaterialTheme.colorScheme.primary else androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant
                                                    )
                                                },
                                                onClick = {
                                                    appLockTimeout = time
                                                    prefs.edit { putLong("app_lock_timeout", time) }
                                                    showTimeoutMenu = false
                                                }
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    },
                    trailingContent = {
                        Switch(
                            checked = requireBiometric,
                            onCheckedChange = null // Handled by toggleable
                        )
                    }
                )


            ListItem(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { navigator.navigate(CustomizationScreenDestination) },
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                leadingContent = { Icon(Icons.Filled.Palette, null) },
                headlineContent = {
                    Text(
                        text = stringResource(R.string.customization),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            )

            var showBottomsheet by remember { mutableStateOf(false) }

            ListItem(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { showBottomsheet = true },
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                leadingContent = { Icon(Icons.Filled.BugReport, null) },
                headlineContent = {
                        Text(
                            text = stringResource(
                                if (isUnrooted) R.string.export_log_unrooted else R.string.export_log
                            ),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                }
            )

            if (showBottomsheet) {
                ExportLogBottomSheet(
                    onDismiss = { showBottomsheet = false },
                    onSave = {
                        val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH_mm")
                        val current = LocalDateTime.now().format(formatter)
                        exportBugreportLauncher.launch("KernelSU_Next_bugreport_${current}.tar.gz")
                        showBottomsheet = false
                    },
                    onShare = {
                        scope.launch {
                            val bugreport = loadingDialog.withLoading {
                                withContext(Dispatchers.IO) {
                                    if (isUnrooted) {
                                        getBugreportFileUnrooted(context)
                                    } else {
                                        getBugreportFile(context)
                                    }
                                }
                            }
                            val uri: Uri = FileProvider.getUriForFile(
                                context,
                                "${BuildConfig.APPLICATION_ID}.fileprovider",
                                bugreport
                            )
                            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                putExtra(Intent.EXTRA_STREAM, uri)
                                setDataAndType(uri, "application/gzip")
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            }
                            context.startActivity(
                                Intent.createChooser(shareIntent, context.getString(R.string.send_log))
                            )
                        }
                        showBottomsheet = false
                    }
                )
            }

            ListItem(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { aboutDialog.show() },
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                leadingContent = { Icon(Icons.Filled.ContactPage, null) },
                headlineContent = {
                    Text(
                        text = stringResource(R.string.about),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ExportLogBottomSheet(
    onDismiss: () -> Unit,
    onSave: () -> Unit,
    onShare: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        content = {
            Row(
                modifier = Modifier
                    .padding(10.dp)
                    .align(Alignment.CenterHorizontally)
            ) {
                Box {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        IconButton(
                            onClick = onSave,
                            modifier = Modifier.size(56.dp).clip(CircleShape)
                        ) {
                            Icon(Icons.Filled.Save, contentDescription = null)
                        }
                        Text(
                            text = stringResource(id = R.string.save_log),
                            modifier = Modifier.padding(top = 16.dp),
                            textAlign = TextAlign.Center.also {
                                LineHeightStyle(
                                    alignment = LineHeightStyle.Alignment.Center,
                                    trim = LineHeightStyle.Trim.None
                                )
                            }
                        )
                    }
                }
                Box {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        IconButton(
                            onClick = onShare,
                            modifier = Modifier.size(56.dp).clip(CircleShape)
                        ) {
                            Icon(Icons.Filled.Share, contentDescription = null)
                        }
                        Text(
                            text = stringResource(id = R.string.send_log),
                            modifier = Modifier.padding(top = 16.dp),
                            textAlign = TextAlign.Center.also {
                                LineHeightStyle(
                                    alignment = LineHeightStyle.Alignment.Center,
                                    trim = LineHeightStyle.Trim.None
                                )
                            }
                        )
                    }
                }
            }
        }
    )
}

@Composable
fun UninstallItem(
    navigator: DestinationsNavigator,
    withLoading: suspend (suspend () -> Unit) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val uninstallConfirmDialog = rememberConfirmDialog()
    val showTodo = {
        Toast.makeText(context, "TODO", Toast.LENGTH_SHORT).show()
    }
    val uninstallDialog = rememberUninstallDialog { uninstallType ->
        scope.launch {
            val result = uninstallConfirmDialog.awaitConfirm(
                title = context.getString(uninstallType.title),
                content = context.getString(uninstallType.message)
            )
            if (result == ConfirmResult.Confirmed) {
                withLoading {
                    when (uninstallType) {
                        UninstallType.TEMPORARY -> showTodo()
                        UninstallType.PERMANENT -> navigator.navigate(
                            FlashScreenDestination(FlashIt.FlashUninstall)
                        )

                        UninstallType.RESTORE_STOCK_IMAGE -> navigator.navigate(
                            FlashScreenDestination(FlashIt.FlashRestore)
                        )

                        UninstallType.NONE -> Unit
                    }
                }
            }
        }
    }
    val uninstall = stringResource(id = R.string.settings_uninstall)
    ListItem(
        modifier = modifier.clickable { uninstallDialog.show() },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        leadingContent = { Icon(Icons.Filled.Delete, uninstall) },
        headlineContent = {
            Text(
                text = uninstall,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
        }
    )
}

enum class UninstallType(val title: Int, val message: Int, val icon: ImageVector) {
    TEMPORARY(
        R.string.settings_uninstall_temporary,
        R.string.settings_uninstall_temporary_message,
        Icons.Filled.Delete
    ),
    PERMANENT(
        R.string.settings_uninstall_permanent,
        R.string.settings_uninstall_permanent_message,
        Icons.Filled.DeleteForever
    ),
    RESTORE_STOCK_IMAGE(
        R.string.settings_restore_stock_image,
        R.string.settings_restore_stock_image_message,
        Icons.AutoMirrored.Filled.Undo
    ),
    NONE(0, 0, Icons.Filled.Delete)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun rememberUninstallDialog(onSelected: (UninstallType) -> Unit): DialogHandle {
    return rememberCustomDialog { dismiss ->
        val options = listOf(
            // UninstallType.TEMPORARY,
            UninstallType.PERMANENT,
            UninstallType.RESTORE_STOCK_IMAGE
        )
        val listOptions = options.map {
            ListOption(
                titleText = stringResource(it.title),
                subtitleText = if (it.message != 0) stringResource(it.message) else null,
                icon = IconSource(it.icon)
            )
        }

        var selection = UninstallType.NONE
        ListDialog(
            state = rememberUseCaseState(visible = true, onFinishedRequest = {
                if (selection != UninstallType.NONE) {
                    onSelected(selection)
                }
            }, onCloseRequest = {
                dismiss()
            }),
            header = Header.Default(title = stringResource(R.string.settings_uninstall)),
            selection = ListSelection.Single(
                showRadioButtons = false,
                options = listOptions,
            ) { index, _ ->
                selection = options[index]
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TopBar(
    scrollBehavior: TopAppBarScrollBehavior? = null,
) {
    TopAppBar(
        title = {
            Text(
                text = stringResource(R.string.settings),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Black,
            )
        },
        windowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal),
        scrollBehavior = scrollBehavior
    )
}

@Preview
@Composable
private fun SettingsPreview() {
    SettingScreen(EmptyDestinationsNavigator)
}
