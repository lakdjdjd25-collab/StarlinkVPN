package org.quickping.app.ui.screens

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import org.quickping.app.R
import org.quickping.app.core.design.QuickPingColors
import org.quickping.app.core.design.quickText
import org.quickping.app.model.GuardianCategory
import org.quickping.app.ui.components.DashedDivider
import org.quickping.app.ui.components.QuickPingScreen
import org.quickping.app.ui.components.QuickPingTopBar
import org.quickping.app.ui.components.QuickSwitch

@Composable
fun GuardianScreen(
    enabled: Boolean,
    categories: List<GuardianCategory>,
    onEnabledChange: (Boolean) -> Unit,
    onToggleCategory: (String) -> Unit,
    onBack: () -> Unit,
) {
    LaunchedEffect(enabled) {
        // The reference app exposes category switches directly rather than a
        // separate master switch. Preserve old installs by enabling the engine.
        if (!enabled) onEnabledChange(true)
    }
    QuickPingScreen {
        QuickPingTopBar(
            title = quickText("گاردین", "Guardian"),
            onBack = onBack,
            action = {
                Icon(
                    painter = painterResource(R.drawable.ic_settings_reset),
                    contentDescription = quickText("بازنشانی", "Reset"),
                    tint = QuickPingColors.TextSecondary,
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(QuickPingColors.Surface)
                        .clickable {
                            val defaults = setOf("malware", "phishing")
                            categories.filter { category -> category.enabled != (category.id in defaults) }
                                .forEach { category -> onToggleCategory(category.id) }
                        }
                        .padding(9.dp),
                )
            },
        )
        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(start = 14.dp, end = 14.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            item {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Box(
                        modifier = Modifier.size(58.dp).border(1.dp, QuickPingColors.Border, CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            painterResource(R.drawable.ic_shield),
                            contentDescription = null,
                            tint = QuickPingColors.TextPrimary,
                            modifier = Modifier.size(32.dp),
                        )
                    }
                    Spacer(Modifier.height(12.dp))
                    Text(
                        quickText(
                            "گاردین موارد ناخواسته مانند تبلیغات و محتوای مضر را مسدود خواهد کرد.",
                            "Guardian blocks unwanted content such as ads, trackers and harmful websites.",
                        ),
                        color = QuickPingColors.TextSecondary,
                        style = MaterialTheme.typography.bodySmall,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(12.dp))
                    DashedDivider()
                }
            }
            items(categories, key = GuardianCategory::id) { category ->
                GuardianTile(
                    category = category,
                    onToggle = { onToggleCategory(category.id) },
                )
            }
        }
    }
}

@Composable
private fun GuardianTile(category: GuardianCategory, onToggle: () -> Unit) {
    val title = when (category.id) {
        "malware" -> quickText("بدافزارها", "Malware")
        "ads" -> quickText("تبلیغات و ردیاب‌ها", "Ads and trackers")
        "youtube" -> quickText("تبلیغات یوتیوب", "YouTube ads")
        "phishing" -> quickText("محتوای فریبنده", "Deceptive content")
        "porn" -> quickText("محتوای بزرگسال", "Adult content")
        "government" -> quickText("دولتی", "Government websites")
        "payment" -> quickText("درگاه‌های پرداخت", "Payment gateways")
        "socials" -> quickText("شبکه‌های اجتماعی", "Social networks")
        "crypto" -> quickText("کریپتو", "Cryptocurrency")
        "fake-news" -> quickText("اخبار جعلی", "Fake news")
        else -> category.title
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(60.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(QuickPingColors.Surface.copy(alpha = 0.94f))
            .border(1.dp, QuickPingColors.BorderSoft, RoundedCornerShape(16.dp))
            .clickable(onClick = onToggle)
            .padding(horizontal = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(QuickPingColors.SurfaceHigh),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painterResource(guardianIcon(category.iconName)),
                contentDescription = null,
                tint = QuickPingColors.TextSecondary,
                modifier = Modifier.size(22.dp),
            )
        }
        Spacer(Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, color = QuickPingColors.TextPrimary, style = MaterialTheme.typography.bodyLarge)
            Spacer(Modifier.height(3.dp))
            Text(
                if (category.enabled) quickText("🔒 مسدود است", "🔒 Blocked") else quickText("🔓 مجاز است", "🔓 Allowed"),
                color = if (category.enabled) Color(0xFF59BFD8) else QuickPingColors.TextMuted,
                style = MaterialTheme.typography.labelSmall,
            )
        }
        QuickSwitch(checked = category.enabled, onCheckedChange = { onToggle() })
    }
}

@DrawableRes
private fun guardianIcon(name: String): Int = when (name) {
    "malware" -> R.drawable.ic_malware
    "ads" -> R.drawable.ic_ads
    "youtube" -> R.drawable.ic_youtube
    "phishing" -> R.drawable.ic_alert
    "porn" -> R.drawable.ic_porn
    "government" -> R.drawable.ic_government
    "payment" -> R.drawable.ic_payment
    "socials" -> R.drawable.ic_socials
    "crypto" -> R.drawable.ic_crypto
    else -> R.drawable.ic_fake_news
}
