package dev.sshmobile

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.sshmobile.data.repository.AppSettings
import dev.sshmobile.data.repository.ThemeMode
import dev.sshmobile.navigation.AppNavigation
import dev.sshmobile.ui.LocalAppContainer
import dev.sshmobile.ui.theme.SshMobileTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val container = (application as SshMobileApplication).container

        setContent {
            val settingsFlow = remember(container) { container.settingsRepository.settings }
            val settings by settingsFlow.collectAsStateWithLifecycle(initialValue = AppSettings())
            val view = LocalView.current

            LaunchedEffect(settings.blockScreenshots) {
                if (settings.blockScreenshots) {
                    window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
                } else {
                    window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
                }
            }

            LaunchedEffect(settings.themeMode, view) {
                val light = settings.themeMode == ThemeMode.LIGHT
                val controller = WindowCompat.getInsetsController(window, view)
                controller.isAppearanceLightStatusBars = light
                controller.isAppearanceLightNavigationBars = light
            }

            SshMobileTheme(themeMode = settings.themeMode) {
                CompositionLocalProvider(LocalAppContainer provides container) {
                    AppNavigation()
                }
            }
        }
    }
}
