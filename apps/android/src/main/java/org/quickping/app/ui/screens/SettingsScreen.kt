package org.quickping.app.ui.screens

import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import org.quickping.app.model.AppSettings
import org.quickping.app.ui.components.GlassCard
import org.quickping.app.ui.components.QuickPingScreen
import org.quickping.app.ui.components.QuickPingTopBar
import org.quickping.app.ui.components.QuickSwitch
import org.quickping.app.ui.components.SectionLabel
import org.quickping.app.ui.components.SettingRow

@Composable
fun SettingsScreen(
    settings: AppSettings,
    onUpdateSettings: ((AppSettings) -> AppSettings) -> Unit,
    onBack: () -> Unit,
    onSplitTunneling: () -> Unit,
    onGuardian: () -> Unit,
    onAccount: () -> Unit,
    onNotifications: () -> Unit,
    onVersion: () -> Unit,
) {
    var showVpnDialog by remember { mutableStateOf(false) }
    var showProxyDialog by remember { mutableStateOf(false) }
    var showDnsDialog by remember { mutableStateOf(false) }
    var showBackgroundWarning by remember { mutableStateOf(false) }

    QuickPingScreen {
        QuickPingTopBar(title = "تنظیمات", onBack = onBack)
        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                start = 12.dp,
                end = 12.dp,
                bottom = 24.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                GlassCard(Modifier.fillMaxWidth()) {
                    SettingRow(
                        title = "تقسیم تونل",
                        icon = R.drawable.ic_split_tunneling,
                        onClick = onSplitTunneling,
                    )
                    SettingRow(
                        title = "گاردین",
                        icon = R.drawable.ic_guard,
                        onClick = onGuardian,
                    )
                }
            }
            item { SectionLabel("اتصال") }
            item {
                GlassCard(Modifier.fillMaxWidth()) {
                    SettingRow(
                        title = "حالت پروکسی",
                        subtitle = "اتصال بدون ایجاد رابط VPN",
                        icon = R.drawable.ic_proxy,
                        trailing = {
                            QuickSwitch(
                                checked = settings.proxyEnabled,
                                onCheckedChange = { enabled ->
                                    onUpdateSettings { current -> current.copy(proxyEnabled = enabled) }
                                },
                            )
                        },
                    )
                    SettingRow(
                        title = "اشتراک‌گذاری با نقطه اتصال",
                        icon = R.drawable.ic_hotspot,
                        trailing = {
                            QuickSwitch(
                                checked = settings.shareHotspot,
                                onCheckedChange = { enabled ->
                                    onUpdateSettings { current -> current.copy(shareHotspot = enabled) }
                                },
                            )
                        },
                    )
                    SettingRow(
                        title = "پروکسی HTTP و SOCKS5",
                        subtitle = "127.0.0.1:${settings.proxyPort}",
                        icon = R.drawable.ic_port,
                        onClick = { showProxyDialog = true },
                    )
                    SettingRow(
                        title = "DNS",
                        subtitle = settings.dnsProvider,
                        icon = R.drawable.ic_dns,
                        onClick = { showDnsDialog = true },
                    )
                    SettingRow(
                        title = "پینگ‌گرفتن خودکار سرورها",
                        icon = R.drawable.ic_ping_auto,
                        trailing = {
                            QuickSwitch(
                                checked = settings.autoPing,
                                onCheckedChange = { enabled ->
                                    onUpdateSettings { current -> current.copy(autoPing = enabled) }
                                },
                            )
                        },
                    )
                    SettingRow(
                        title = "تنظیمات پیشرفته اتصال",
                        icon = R.drawable.ic_settings_pending,
                        onClick = { showVpnDialog = true },
                    )
                    SettingRow(
                        title = "فعال‌ماندن اتصال در پس‌زمینه",
                        icon = R.drawable.ic_battery_optimize,
                        onClick = { showBackgroundWarning = true },
                    )
                }
            }
            item { SectionLabel("برنامه") }
            item {
                GlassCard(Modifier.fillMaxWidth()) {
                    SettingRow(
                        title = "حساب کاربری",
                        icon = R.drawable.ic_user,
                        onClick = onAccount,
                    )
                    SettingRow(
                        title = "اعلان‌ها",
                        icon = R.drawable.ic_bell,
                        onClick = onNotifications,
                    )
                    SettingRow(
                        title = "زبان",
                        subtitle = settings.language,
                        icon = R.drawable.ic_language,
                    )
                    SettingRow(
                        title = "گرافیک و ظاهر",
                        subtitle = "تیره",
                        icon = R.drawable.ic_wand_stars,
                    )
                    SettingRow(
                        title = "نسخه",
                        subtitle = "2.6.0",
                        icon = R.drawable.ic_version,
                        onClick = onVersion,
                    )
                }
            }
        }
    }

    if (showProxyDialog) {
        CompactChoiceDialog(
            title = "پروکسی HTTP و SOCKS5",
            options = listOf("خاموش", "فعال روی پورت ۱۰۸۱۰"),
            selected = if (settings.proxyEnabled) "فعال روی پورت ۱۰۸۱۰" else "خاموش",
            onSelect = { value ->
                onUpdateSettings { it.copy(proxyEnabled = value != "خاموش") }
                showProxyDialog = false
            },
            onDismiss = { showProxyDialog = false },
        )
    }
    if (showDnsDialog) {
        CompactChoiceDialog(
            title = "DNS",
            options = listOf("پیش‌فرض", "گوگل", "کلودفلر"),
            selected = settings.dnsProvider,
            onSelect = { value ->
                onUpdateSettings { it.copy(dnsProvider = value) }
                showDnsDialog = false
            },
            onDismiss = { showDnsDialog = false },
        )
    }
    if (showVpnDialog) {
        AlertDialog(
            onDismissRequest = { showVpnDialog = false },
            containerColor = QuickPingColors.SurfaceHigh,
            shape = RoundedCornerShape(22.dp),
            title = { Text("تنظیمات پیشرفته اتصال", color = QuickPingColors.TextPrimary) },
            text = {
                Column {
                    Text(
                        "این بخش شامل تنظیم رابط VPN، مسیرهای محلی و رفتار اتصال مجدد است.",
                        color = QuickPingColors.TextSecondary,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Spacer(Modifier.height(12.dp))
                    SettingRow(
                        title = "اتصال خودکار",
                        icon = R.drawable.ic_reload,
                        trailing = {
                            QuickSwitch(
                                checked = settings.autoConnect,
                                onCheckedChange = { enabled ->
                                    onUpdateSettings { current -> current.copy(autoConnect = enabled) }
                                },
                            )
                        },
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { showVpnDialog = false }) { Text("انجام شد") }
            },
        )
    }
    if (showBackgroundWarning) {
        AlertDialog(
            onDismissRequest = { showBackgroundWarning = false },
            containerColor = QuickPingColors.SurfaceHigh,
            shape = RoundedCornerShape(22.dp),
            icon = {
                Box(
                    modifier = Modifier
                        .size(46.dp)
                        .background(QuickPingColors.Warning.copy(alpha = 0.16f), CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        painterResource(R.drawable.ic_battery_optimize),
                        contentDescription = null,
                        tint = QuickPingColors.Warning,
                        modifier = Modifier.size(24.dp),
                    )
                }
            },
            title = {
                Text(
                    "بهینه‌سازی باتری فعال است",
                    color = QuickPingColors.TextPrimary,
                    textAlign = TextAlign.Center,
                )
            },
            text = {
                Text(
                    "برای پایداری اتصال در پس‌زمینه، QuickPing را از محدودیت باتری خارج کنید.",
                    color = QuickPingColors.TextSecondary,
                    textAlign = TextAlign.Center,
                )
            },
            confirmButton = {
                TextButton(onClick = { showBackgroundWarning = false }) { Text("ادامه") }
            },
            dismissButton = {
                TextButton(onClick = { showBackgroundWarning = false }) { Text("بعداً") }
            },
        )
    }
}

@Composable
private fun CompactChoiceDialog(
    title: String,
    options: List<String>,
    selected: String,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = QuickPingColors.SurfaceHigh,
        shape = RoundedCornerShape(22.dp),
        title = { Text(title, color = QuickPingColors.TextPrimary) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                options.forEach { option ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(44.dp)
                            .background(QuickPingColors.Surface, RoundedCornerShape(10.dp))
                            .clickable { onSelect(option) }
                            .padding(horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(
                            selected = option == selected,
                            onClick = null,
                            colors = RadioButtonDefaults.colors(
                                selectedColor = QuickPingColors.Primary,
                                unselectedColor = QuickPingColors.TextMuted,
                            ),
                        )
                        Text(
                            option,
                            modifier = Modifier.weight(1f),
                            color = QuickPingColors.TextPrimary,
                            textAlign = TextAlign.End,
                        )
                    }
                }
            }
        },
        confirmButton = {},
    )
}
