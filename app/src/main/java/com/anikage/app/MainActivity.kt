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

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        // Splash screen: shows briefly while Compose inflates.
        installSplashScreen()
        super.onCreate(savedInstanceState)
        AppLogger.d(LogCategory.UI, "MainActivity.onCreate")

        setContent {
            AnikageTheme(darkTheme = Config.Theme.DEFAULT_DARK_MODE) {
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
