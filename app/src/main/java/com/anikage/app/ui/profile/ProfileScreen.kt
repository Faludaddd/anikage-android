package com.anikage.app.ui.profile

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
import androidx.compose.material.icons.filled.Person
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
 * PROFILE — 1:1 port of anikage.cc/profile (site /(account)/profile),
 * logged-out state.
 *
 * Site layout (from the compiled route node):
 *   container-custom … pt-28
 *   ├─ header: back button + title
 *   └─ centered card (min-h-[360px], md:rounded-[28px] border-fg/8 bg-surface-card):
 *        ├─ icon container h-16 w-16 rounded-2xl border-fg/10 bg-fg/2.5 (user glyph)
 *        ├─ max-w-md: title (text-xl font-semibold fg)
 *            "Sync your account to manage your lists from Anikage without
 *             bouncing between tabs." (mt-2 text-sm fg/50)
 *        └─ button: rounded-full border-accent-400/30 bg-accent-500/10
 *            px-7 py-3 text-sm font-semibold text-indigo-200
 *            "Authorize Connection"
 */
@Composable
fun ProfileScreen(
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
        // ── Header ───────────────────────────────────────────────────────
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
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
            Text(
                text = "Profile",
                style = WebTextStyles.titleSection,
                fontSize = 20.sp,
                color = theme.fg,
            )
        }

        // ── Logged-out card (site's connect-account state) ───────────────
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(20.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 48.dp)
                .clip(RoundedCornerShape(28.dp))
                .background(theme.surfaceCard)
                .border(1.dp, Color(0x0FFFFFFF), RoundedCornerShape(28.dp))
                .padding(horizontal = 40.dp, vertical = 80.dp),
        ) {
            // Icon — site: h-16 w-16 rounded-2xl border-fg/10 bg-fg/2.5.
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color(0x0AFFFFFF))
                    .border(1.dp, Color(0x1AFFFFFF), RoundedCornerShape(16.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Default.Person,
                    contentDescription = null,
                    tint = theme.fgMuted,
                    modifier = Modifier.size(30.dp),
                )
            }

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "You're browsing as a guest",
                    style = WebTextStyles.lg,
                    fontSize = 20.sp,
                    color = theme.fg,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = (-0.4).sp,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "Sync your account to manage your lists from Anikage without bouncing between tabs.",
                    style = WebTextStyles.sm,
                    color = theme.fgMuted,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(0.85f),
                )
            }

            // CTA — site: rounded-full border-accent-400/30 bg-accent-500/10
            // text-sm font-semibold text-indigo-200, icon + label.
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(theme.accent.copy(alpha = 0.10f))
                    .border(1.dp, theme.accent.copy(alpha = 0.30f), RoundedCornerShape(50))
                    .clickable { onOpenSettings() }
                    .padding(horizontal = 28.dp, vertical = 12.dp),
            ) {
                Icon(
                    Icons.Default.Person,
                    contentDescription = null,
                    tint = Color(0xFFC7D2FE),     // indigo-200
                    modifier = Modifier.size(16.dp),
                )
                Text(
                    text = "Authorize Connection",
                    style = WebTextStyles.sm,
                    color = Color(0xFFC7D2FE),
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}
