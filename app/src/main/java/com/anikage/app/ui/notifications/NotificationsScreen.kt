package com.anikage.app.ui.notifications

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.anikage.app.core.theme.LocalAnikageTheme
import com.anikage.app.core.theme.WebTextStyles

/**
 * NOTIFICATIONS — 1:1 port of anikage.cc/notifications (site /(account)/notifications).
 *
 * Site layout (from the compiled route node):
 *   container-custom min-h-dvh pt-28 pb-10 text-white
 *   ├─ header: [back btn h-10 w-10 rounded-xl border-white/6 bg-white/3]
 *              h1.title-section "Notifications" + count subtitle
 *              [actions: mark-all-read + settings]
 *   ├─ empty state: "All caught up!" (text-base medium zinc-400)
 *      "Replies, warnings, and episode alerts will appear here."
 *      (text-sm zinc-600, max-w-[260px])
 *   └─ list: sticky day headers (uppercase text-xs zinc-500) + items
 *      flex-col gap-1.5, unread dot blue-500 glow, meta row text-xs zinc-500
 *
 * The app is not authenticated yet, so the empty state is exactly what the
 * site shows signed-out users — same structure, no fake data.
 */
@Composable
fun NotificationsScreen(
    onBackClick: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    val theme = LocalAnikageTheme.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(theme.surface)
            .statusBarsPadding(),
    ) {
        // ── Header (site: mb-6 flex items-center justify-between) ────────
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                // Back — site: h-10 w-10 rounded-xl border-white/6 bg-white/3.
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0x08FFFFFF))
                        .border(1.dp, Color(0x0FFFFFFF), RoundedCornerShape(12.dp))
                        .clickable(onClick = onBackClick),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = theme.fg,
                        modifier = Modifier.size(18.dp),
                    )
                }
                Column {
                    Text(
                        text = "Notifications",
                        style = WebTextStyles.titleSection,
                        fontSize = 20.sp,
                        color = theme.fg,
                    )
                    Text(
                        text = "0 unread",
                        style = WebTextStyles.xs,
                        color = theme.fgMuted,
                    )
                }
            }
            // Actions — site: mark-all-read + settings gear.
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                val context = androidx.compose.ui.platform.LocalContext.current
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color(0x08FFFFFF))
                        .border(1.dp, Color(0x0FFFFFFF), RoundedCornerShape(10.dp))
                        .clickable {
                            // Signed-out state: nothing to mark read (site
                            // shows the same empty state without auth).
                            android.widget.Toast.makeText(
                                context, "All caught up", android.widget.Toast.LENGTH_SHORT,
                            ).show()
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Default.Check,
                        contentDescription = "Mark all as read",
                        tint = theme.fgMuted,
                        modifier = Modifier.size(16.dp),
                    )
                }
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color(0x08FFFFFF))
                        .border(1.dp, Color(0x0FFFFFFF), RoundedCornerShape(10.dp))
                        .clickable(onClick = onOpenSettings),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Default.Settings,
                        contentDescription = "Notification settings",
                        tint = theme.fgMuted,
                        modifier = Modifier.size(16.dp),
                    )
                }
            }
        }

        // ── Empty state (site: "All caught up!") ─────────────────────────
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 128.dp, start = 32.dp, end = 32.dp),
        ) {
            // Site: rounded-2xl bg-white/3 p-6 ring-1 ring-white/6 (bell in a card).
            Box(
                modifier = Modifier
                    .size(96.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(Color(0x08FFFFFF))
                    .border(1.dp, Color(0x0FFFFFFF), RoundedCornerShape(20.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Default.Notifications,
                    contentDescription = null,
                    tint = theme.fgMuted,
                    modifier = Modifier.size(40.dp),
                )
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "All caught up!",
                    style = WebTextStyles.base,
                    color = Color(0xFFA1A1AA),
                    fontWeight = FontWeight.Medium,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "Replies, warnings, and episode alerts will appear here.",
                    style = WebTextStyles.sm,
                    color = Color(0xFF52525B),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(0.75f),
                )
            }
        }
    }
}
