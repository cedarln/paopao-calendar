package com.paopao.calendar

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.paopao.calendar.data.settings.AppSettings
import com.paopao.calendar.ui.PaopaoApp
import com.paopao.calendar.ui.navigation.Routes
import com.paopao.calendar.ui.theme.PaopaoTheme
import kotlinx.coroutines.flow.MutableStateFlow

class MainActivity : ComponentActivity() {

    /** 桌面插件与提醒通知的落点；Activity 复用（singleTask）时由 onNewIntent 更新。 */
    private val pendingDeepLink = MutableStateFlow<String?>(null)

    private val askNotifications =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { /* 拒绝就不再多问 */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        requestNotificationPermissionIfNeeded()
        pendingDeepLink.value = intent.deepLinkRoute()
        val container = appContainer
        setContent {
            val settings by container.settingsStore.settings
                .collectAsStateWithLifecycle(initialValue = AppSettings())
            PaopaoTheme(themeMode = settings.themeMode) {
                PaopaoApp(container = container, pendingDeepLink = pendingDeepLink)
            }
        }
    }

    /**
     * Android 13+ 的通知是运行时权限，不申请就永远收不到提醒。
     * 只在还没授权时问一次：系统自己会在两次拒绝后停止弹窗。
     */
    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        val granted = ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
        if (!granted) askNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        pendingDeepLink.value = intent.deepLinkRoute()
    }

    companion object {
        /** 桌面插件点击时带上：某天直接落到日历那一天，某模块落到对应顶级页。 */
        const val EXTRA_EPOCH_DAY = "com.paopao.calendar.extra.EPOCH_DAY"
        const val EXTRA_ROUTE = "com.paopao.calendar.extra.ROUTE"

        fun intentToDay(context: android.content.Context, epochDay: Long): Intent =
            context.newMainActivityIntent().apply {
                putExtra(EXTRA_EPOCH_DAY, epochDay)
            }

        fun intentToRoute(context: android.content.Context, route: String): Intent =
            context.newMainActivityIntent().apply {
                putExtra(EXTRA_ROUTE, route)
            }

        private fun android.content.Context.newMainActivityIntent(): Intent =
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
    }
}

/**
 * 把插件带来的 extra 翻成一条导航路由。
 *
 * 转换放在 Activity 一侧而不是插件一侧：路由表属于 UI 层，插件只负责说
 * 「我要去这个模块 / 这一天」。
 */
private fun Intent.deepLinkRoute(): String? {
    val route = getStringExtra(MainActivity.EXTRA_ROUTE)
    val epochDay = getLongExtra(MainActivity.EXTRA_EPOCH_DAY, -1L)
    return when {
        epochDay >= 0L -> Routes.calendarAt(epochDay)
        route != null -> route
        else -> null
    }
}
