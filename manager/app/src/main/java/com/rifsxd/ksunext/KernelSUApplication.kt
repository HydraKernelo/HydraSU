package com.rifsxd.ksunext

import android.app.Application
import android.system.Os
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import coil.Coil
import coil.ImageLoader
import com.rifsxd.ksunext.ui.util.LocaleHelper
import com.rifsxd.ksunext.ui.util.createRootShellBuilder
import com.rifsxd.ksunext.ui.viewmodel.ModuleViewModel
import com.rifsxd.ksunext.ui.viewmodel.SuperUserViewModel
import com.topjohnwu.superuser.Shell
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import me.zhanghai.android.appiconloader.coil.AppIconFetcher
import me.zhanghai.android.appiconloader.coil.AppIconKeyer
import okhttp3.Cache
import okhttp3.OkHttpClient
import java.io.File
import java.util.*

lateinit var ksuApp: KernelSUApplication

class KernelSUApplication : Application(), ViewModelStoreOwner {

    lateinit var okhttpClient: OkHttpClient
    private val appViewModelStore by lazy { ViewModelStore() }

    override fun onCreate() {
        super.onCreate()
        LocaleHelper.migrateLegacyLocale(this)
        ksuApp = this
        Shell.setDefaultBuilder(createRootShellBuilder(true))
        Shell.enableVerboseLogging = BuildConfig.DEBUG

        val context = this
        val iconSize = resources.getDimensionPixelSize(android.R.dimen.app_icon_size)
        Coil.setImageLoader(
            ImageLoader.Builder(context)
                .components {
                    add(AppIconKeyer())
                    add(AppIconFetcher.Factory(iconSize, false, context))
                }
                .build()
        )

        val webroot = File(dataDir, "webroot")
        if (!webroot.exists()) {
            webroot.mkdir()
        }

        // Provide working env for rust's temp_dir()
        Os.setenv("TMPDIR", cacheDir.absolutePath, true)

        okhttpClient =
            OkHttpClient.Builder().cache(Cache(File(cacheDir, "okhttp"), 10 * 1024 * 1024))
                .addInterceptor { block ->
                    block.proceed(
                        block.request().newBuilder()
                            .header("User-Agent", "KernelSU-Next/${BuildConfig.VERSION_CODE}")
                            .header("Accept-Language", Locale.getDefault().toLanguageTag()).build()
                    )
                }.build()

        // HydraSU parasite: local management plane (127.0.0.1)
        try {
            // 静默自启（模块拉起）：跳过前台通知，服务以后台形态运行
            val silentMarker = java.io.File("/data/adb/hydra/.silent")
            val silent = silentMarker.exists()
            if (silent) silentMarker.delete()
            if (!silent) {
                val si = android.content.Intent(this, ParasiteService::class.java)
                si.putExtra("notify", true)
                if (android.os.Build.VERSION.SDK_INT >= 26) startForegroundService(si) else startService(si)
            }
        } catch (_: Throwable) {
        }
    }

    override val viewModelStore: ViewModelStore
        get() = appViewModelStore


}
