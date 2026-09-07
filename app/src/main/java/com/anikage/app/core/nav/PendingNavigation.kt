package com.anikage.app.core.nav

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * App-entry navigation requests (subscription notifications, etc.). The
 * nav graph consumes [consume] exactly once when it's ready to navigate.
 */
object PendingNavigation {

    data class WatchTarget(val animeId: Int, val episode: Int)

    /** Non-null while a navigation request is waiting to be handled. */
    var openWatch: WatchTarget? by mutableStateOf(null)
        private set

    fun request(target: WatchTarget) { openWatch = target }

    /** Called by the nav graph after issuing the navigation. */
    fun consume() { openWatch = null }
}
