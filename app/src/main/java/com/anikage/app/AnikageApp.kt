package com.anikage.app

import android.app.Application
import com.anikage.app.core.log.AppLogger
import com.anikage.app.core.log.LogCategory

/**
 * Application class. Initialises the in-app logger (and its crash
 * capture) first thing so every subsystem can log through it.
 *
 * All customization comes from [Config].
 */
class AnikageApp : Application() {
    override fun onCreate() {
        super.onCreate()
        AppLogger.init(this)
        // Restore persisted user settings (site's localStorage equivalents).
        com.anikage.app.core.settings.SettingsState.init(this)
        AppLogger.i(
            LogCategory.APP,
            "Anikage starting — v${Config.APP_VERSION} (code ${Config.APP_VERSION_CODE}), " +
                "device ${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}, " +
                "Android ${android.os.Build.VERSION.RELEASE} (API ${android.os.Build.VERSION.SDK_INT})",
        )
    }
}
