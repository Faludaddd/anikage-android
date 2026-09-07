package com.anikage.app.ui.about

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Balance
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Copyright
import androidx.compose.material.icons.filled.Gavel
import androidx.compose.material.icons.filled.Mail
import androidx.compose.material.icons.filled.PrivacyTip
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.anikage.app.core.theme.LocalAnikageTheme
import com.anikage.app.core.theme.WebTextStyles

/**
 * DMCA & Legal Information — 1:1 port of the site's /dmca page content:
 * Service Model, Copyright Policy, Data Protection, User Responsibilities,
 * Terms & Conditions, and Legal Contact sections in the site's card layout.
 */
@Composable
fun DmcaScreen(
    onBackClick: () -> Unit = {},
) {
    val theme = LocalAnikageTheme.current
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(theme.surface)
            .statusBarsPadding()
            .verticalScroll(rememberScrollState()),
    ) {
        // Header (site: /legal page header — title + subtitle + back)
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
        ) {
            Icon(
                Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Back",
                tint = theme.fg,
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color(0x08FFFFFF))
                    .padding(8.dp)
                    .size(18.dp),
            )
        }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 48.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = "DMCA + Legal Information",
                style = WebTextStyles.lg,
                color = theme.fg,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = "Important information about our service, content policies, and user responsibilities.",
                style = WebTextStyles.sm,
                color = theme.fgMuted,
            )
            Spacer(Modifier.height(8.dp))

            DmcaSection(
                icon = Icons.Filled.Settings,
                title = "Service Model",
                subtitle = "How We Operate",
                paragraphs = listOf(
                    "Anikage functions as a search engine and content aggregator that indexes publicly available media from across the internet.",
                    "We don't host, store, or control any media files - everything is sourced from external third-party websites that are already publicly accessible.",
                    "Our automated systems simply provide links to content that's already available online, without bypassing any security measures.",
                ),
            )
            DmcaSection(
                icon = Icons.Filled.Copyright,
                title = "Copyright Policy",
                subtitle = "Content & Copyright",
                paragraphs = listOf(
                    "Since we don't host any content ourselves, all takedown requests must go directly to the websites that actually host the files.",
                    "We respect intellectual property rights and will cooperate with valid legal requests within our technical capabilities.",
                    "For content removal, please contact the original hosting platform - we cannot remove what we don't control.",
                    "If you are a copyright holder and want to report a violation, we are more than happy to point you to where we found the content.",
                ),
            )
            DmcaSection(
                icon = Icons.Filled.PrivacyTip,
                title = "Data Protection",
                subtitle = "Privacy & Data",
                paragraphs = listOf(
                    "User privacy is important to us. We don't collect, store, or track any personal information about our users.",
                    "Optionally, users can store their bookmarks and watch history in our encrypted backend. But we don't store any personal information or identifying data.",
                    "Anikage is entirely self hostable, and can be run on any server. Even by yourself.",
                ),
            )
            DmcaSection(
                icon = Icons.Filled.Gavel,
                title = "User Responsibilities",
                subtitle = "User Guidelines",
                paragraphs = listOf(
                    "Users are responsible for ensuring their access complies with local laws and regulations in their jurisdiction.",
                    "We strongly recommend using VPN services for enhanced privacy and security while browsing. Downloading is not advised.",
                    "Please respect intellectual property rights and be mindful of copyright laws in your area.",
                ),
            )
            DmcaSection(
                icon = Icons.Filled.Balance,
                title = "Terms & Conditions",
                subtitle = "Service Terms",
                paragraphs = listOf(
                    "Anikage is licensed under the MIT license.",
                    "By using our platform, you acknowledge these terms and agree that we're not responsible for third-party content.",
                    "We operate in good faith compliance with applicable laws and regulations. We are not liable for any damages or losses incurred while using our service.",
                ),
            )
            DmcaSection(
                icon = Icons.Filled.Mail,
                title = "Legal Contact",
                subtitle = "Legal Inquiries",
                paragraphs = listOf(
                    "For legal matters related to specific content, please contact the hosting websites directly as they have control over their files.",
                    "Anikage operates within legal boundaries and cooperates with legitimate requests when technically feasible.",
                ),
                contact = true,
            )

            // Site's footer disclaimer (same wording as the site footer).
            Text(
                text = "Please note: Anikage does not host any files itself but instead only display's content from 3rd party providers. Legal issues should be taken up with them.",
                style = WebTextStyles.xs,
                color = theme.fgMuted,
                lineHeight = 18.sp,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    }
}

@Composable
private fun DmcaSection(
    icon: ImageVector,
    title: String,
    subtitle: String,
    paragraphs: List<String>,
    contact: Boolean = false,
) {
    val theme = LocalAnikageTheme.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Color(0x08FFFFFF))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Icon(icon, contentDescription = null, tint = theme.action, modifier = Modifier.size(18.dp))
            Column {
                Text(
                    text = title,
                    style = WebTextStyles.base,
                    color = theme.fg,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = subtitle,
                    style = WebTextStyles.xs,
                    color = theme.fgMuted,
                )
            }
        }
        paragraphs.forEach { p ->
            Text(
                text = p,
                style = WebTextStyles.sm,
                color = Color(0xFFA1A1AA),
                lineHeight = 20.sp,
            )
        }
        if (contact) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(top = 4.dp),
            ) {
                Text(
                    text = "Contact:",
                    style = WebTextStyles.sm,
                    color = theme.fg,
                    fontWeight = FontWeight.Medium,
                )
                Text(
                    text = "legal@anikage.cc",
                    style = WebTextStyles.sm,
                    color = theme.action,
                )
            }
        }
    }
}
