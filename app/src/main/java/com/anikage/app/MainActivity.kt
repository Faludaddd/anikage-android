package com.anikage.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.anikage.app.core.log.AppLogger
import com.anikage.app.core.log.LogCategory
import com.anikage.app.core.nav.AnikageApp
import com.anikage.app.core.theme.AnikageTheme
import com.anikage.app.core.theme.ThemeState
import com.anikage.app.core.theme.WebTypeScale

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        // Splash screen: shows briefly while Compose inflates.
        installSplashScreen()
        super.onCreate(savedInstanceState)
        // Restore the persisted website theme (like the site's localStorage).
        ThemeState.init(this)
        // Restore the Anikage account session (real auth.anikage.cc login)
        // and schedule the subscription new-episode checks.
        com.anikage.app.core.auth.AuthManager.init(this)
        com.anikage.app.core.data.SubscriptionWorker.ensure(this)
        // Subscription notification deep link: open the anime's watch screen.
        handleDeepLink(intent)
        AppLogger.d(LogCategory.UI, "MainActivity.onCreate")

        setContent {
            // Observes ThemeState.key — the theme switcher in Settings
            // recomposes the entire app with the new website theme tokens.
            AnikageTheme(themeKey = ThemeState.key) {
                // Site's @media (width>=768px) type scale: tablet/TV gets the
                // enlarged fixed rem values, phones get the fluid clamp base.
                TypeScaleGate()
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

    override fun onResume() {
        super.onResume()
        // Session heartbeat keeps duration/status metadata fresh on disk.
        AppLogger.heartbeat()
        AppLogger.d(LogCategory.UI, "MainActivity.onResume")
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        // Notification taps while the activity is alive (singleTop).
        handleDeepLink(intent)
    }

    /** Subscription-notification deep link: watch {animeId} at {episode}. */
    private fun handleDeepLink(intent: android.content.Intent?) {
        val animeId = intent?.getIntExtra("anikage.open.animeId", -1) ?: -1
        if (animeId > 0 && intent != null) {
            val episode = intent.getIntExtra("anikage.open.episode", 0)
            com.anikage.app.core.nav.PendingNavigation.request(
                com.anikage.app.core.nav.PendingNavigation.WatchTarget(animeId, episode),
            )
        }
    }

    override fun onDestroy() {
        AppLogger.d(LogCategory.UI, "MainActivity.onDestroy (finishing=$isFinishing)")
        // Only a real user-initiated finish counts as a clean COMPLETED
        // session; a system kill leaves the session open -> next launch
        // detects it as CRASHED (did not shut down normally).
        if (isFinishing) AppLogger.markSessionCompleted()
        super.onDestroy()
    }
}

/** Flips [WebTypeScale] at the site's 768px CSS breakpoint. */
@Composable
private fun TypeScaleGate() {
    WebTypeScale.wide = LocalConfiguration.current.screenWidthDp >= 768
}
