package com.anikage.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.anikage.app.core.log.AppLogger
import com.anikage.app.core.log.LogCategory
import com.anikage.app.core.nav.AnikageApp
import com.anikage.app.core.theme.AnikageTheme
import com.anikage.app.core.theme.ThemeState

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        // Splash screen: shows briefly while Compose inflates.
        installSplashScreen()
        super.onCreate(savedInstanceState)
        // Restore the persisted website theme (like the site's localStorage).
        ThemeState.init(this)
        AppLogger.d(LogCategory.UI, "MainActivity.onCreate")

        setContent {
            // Observes ThemeState.key — the theme switcher in Settings
            // recomposes the entire app with the new website theme tokens.
            AnikageTheme(themeKey = ThemeState.key) {
                Surface(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.background),
                ) {
                    AnikageApp()
                }
            }
        }
    }

    override fun onDestroy() {
        AppLogger.d(LogCategory.UI, "MainActivity.onDestroy")
        super.onDestroy()
    }
}
