package org.quickping.app.ui.screens

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import org.quickping.app.R
import org.quickping.app.core.design.QuickPingColors
import org.quickping.app.model.GuardianCategory
import org.quickping.app.ui.components.GlassCard
import org.quickping.app.ui.components.QuickPingScreen
import org.quickping.app.ui.components.QuickPingTopBar
import org.quickping.app.ui.components.QuickSwitch
import org.quickping.app.ui.components.SettingRow

@Composable
fun GuardianScreen(
    enabled: Boolean,
    categories: List<GuardianCategory>,
    onEnabledChange: (Boolean) -> Unit,
    onToggleCategory: (String) -> Unit,
    onBack: () -> Unit,
) {
    QuickPingScreen {
        QuickPingTopBar(title = "گاردین", onBack = onBack)
        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                start = 12.dp,
                end = 12.dp,
                bottom = 28.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 18.dp, vertical = 18.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Box(
                        modifier = Modifier
                            .size(70.dp)
                            .background(QuickPingColors.Surface, CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            painterResource(R.drawable.ic_shield),
                            contentDescription = null,
                            tint = QuickPingColors.TextPrimary,
                            modifier = Modifier.size(38.dp),
                        )
                    }
                    Spacer(Modifier.height(12.dp))
                    Text(
                        "گاردین موارد ناخواسته مانند تبلیغات و محتوای مضر را مسدود خواهد کرد.",
                        color = QuickPingColors.TextSecondary,
                        style = MaterialTheme.typography.bodySmall,
                        textAlign = TextAlign.Center,
                    )
                }
            }
            item {
                GlassCard(Modifier.fillMaxWidth()) {
                    SettingRow(
                        title = "فعال‌سازی گاردین",
                        subtitle = if (enabled) "محافظت فعال است" else "محافظت غیرفعال است",
                        icon = R.drawable.ic_guard,
                        trailing = { QuickSwitch(enabled, onEnabledChange) },
                    )
                }
            }
            items(categories, key = { it.id }) { category ->
                GlassCard(Modifier.fillMaxWidth()) {
                    SettingRow(
                        title = category.title,
                        subtitle = category.description,
                        icon = guardianIcon(category.iconName),
                        trailing = {
                            QuickSwitch(
                                checked = category.enabled,
                                enabled = enabled,
                                onCheckedChange = { onToggleCategory(category.id) },
                            )
                        },
                    )
                }
            }
        }
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
