package com.anikage.app.core.media

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * Cross-layer player state — the ONLY channel the watch screen uses to tell
 * the app shell (top bar, bottom nav, banners) that a fullscreen video is
 * playing, so NOTHING but the video shows (user directive #2: "When I enter
 * fullscreen video playback, I can still see the top bar containing the app
 * tabs. That should NEVER be visible in fullscreen.").
 */
object PlayerFullscreen {
    /** True while the video player covers the whole screen. */
    var isActive by mutableStateOf(false)
        private set

    fun enter() { isActive = true }
    fun exit() { isActive = false }
}
