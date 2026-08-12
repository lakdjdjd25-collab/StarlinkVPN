package org.quickping.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import org.quickping.app.R
import org.quickping.app.core.design.QuickPingColors
import org.quickping.app.ui.components.GlassCard
import org.quickping.app.ui.components.QuickPingScreen
import org.quickping.app.ui.components.QuickPingTopBar
import org.quickping.app.ui.components.QuickSwitch
import org.quickping.app.ui.components.SettingRow

@Composable
fun SplitTunnelingScreen(
    enabled: Boolean,
    onEnabledChange: (Boolean) -> Unit,
    onBack: () -> Unit,
) {
    var selectedMode by remember { mutableStateOf("exclude") }
    QuickPingScreen {
        QuickPingTopBar(title = "تقسیم تونل", onBack = onBack)
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 12.dp, vertical = 6.dp),
        ) {
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
                        painterResource(R.drawable.ic_split_tunneling),
                        contentDescription = null,
                        tint = QuickPingColors.TextPrimary,
                        modifier = Modifier.size(39.dp),
                    )
                }
                Spacer(Modifier.height(12.dp))
                Text(
                    "تقسیم تونل به شما اجازه می‌دهد انتخاب کنید کدام برنامه‌ها از VPN استفاده کنند و کدام به‌صورت مستقیم به اینترنت متصل شوند.",
                    color = QuickPingColors.TextSecondary,
                    style = MaterialTheme.typography.bodySmall,
                    textAlign = TextAlign.Center,
                )
            }
            GlassCard(Modifier.fillMaxWidth()) {
                SettingRow(
                    title = "فعال‌سازی تقسیم تونل",
                    icon = R.drawable.ic_split_tunneling,
                    trailing = { QuickSwitch(enabled, onEnabledChange) },
                )
            }
            Spacer(Modifier.height(10.dp))
            Text(
                "حالت",
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 8.dp),
                color = QuickPingColors.TextMuted,
                style = MaterialTheme.typography.labelSmall,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                ModeCard(
                    modifier = Modifier.weight(1f),
                    title = "فقط انتخاب‌شده‌ها",
                    subtitle = "تنها موارد انتخابی از VPN عبور کنند",
                    selected = selectedMode == "include",
                    onClick = { selectedMode = "include" },
                )
                ModeCard(
                    modifier = Modifier.weight(1f),
                    title = "به‌جز انتخاب‌شده‌ها",
                    subtitle = "موارد انتخابی مستقیم متصل شوند",
                    selected = selectedMode == "exclude",
                    onClick = { selectedMode = "exclude" },
                )
            }
            Spacer(Modifier.height(10.dp))
            GlassCard(Modifier.fillMaxWidth()) {
                SettingRow(
                    title = "برنامه‌ها",
                    subtitle = "۰ برنامه انتخاب شده",
                    icon = R.drawable.ic_apps,
                )
                SettingRow(
                    title = "نشانی‌ها",
                    subtitle = "۰ نشانی انتخاب شده",
                    icon = R.drawable.ic_addresses,
                )
            }
            Spacer(Modifier.height(10.dp))
            GlassCard(Modifier.fillMaxWidth()) {
                SettingRow(
                    title = "حفظ تنظیمات هنگام تغییر سرویس",
                    icon = R.drawable.ic_lock,
                    trailing = { QuickSwitch(true, {}) },
                )
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun ModeCard(
    modifier: Modifier,
    title: String,
    subtitle: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Column(
        modifier = modifier
            .height(102.dp)
            .background(QuickPingColors.Surface, RoundedCornerShape(14.dp))
            .border(
                1.dp,
                if (selected) QuickPingColors.Primary else QuickPingColors.BorderSoft,
                RoundedCornerShape(14.dp),
            )
            .clickable(onClick = onClick)
            .padding(12.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            title,
            color = QuickPingColors.TextPrimary,
            style = MaterialTheme.typography.labelMedium,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(5.dp))
        Text(
            subtitle,
            color = QuickPingColors.TextMuted,
            style = MaterialTheme.typography.labelSmall,
            textAlign = TextAlign.Center,
        )
    }
}
