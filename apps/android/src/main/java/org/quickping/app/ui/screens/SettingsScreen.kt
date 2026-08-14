package org.quickping.app.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings
import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import java.net.Inet4Address
import java.net.NetworkInterface
import org.quickping.app.BuildConfig
import org.quickping.app.R
import org.quickping.app.core.design.QuickPingColors
import org.quickping.app.core.design.quickText
import org.quickping.app.model.AppLanguage
import org.quickping.app.model.AppSettings
import org.quickping.app.model.DnsProvider
import org.quickping.app.ui.components.QuickPingScreen
import org.quickping.app.ui.components.QuickPingTopBar
import org.quickping.app.ui.components.QuickSwitch
import org.quickping.app.vpn.QuickPingVpnService

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    settings: AppSettings,
    onUpdateSettings: ((AppSettings) -> AppSettings) -> Unit,
    onResetSettings: () -> Unit,
    onBack: () -> Unit,
    onSplitTunneling: () -> Unit,
    onGuardian: () -> Unit,
    onAccount: () -> Unit,
    onNotifications: () -> Unit,
    onVersion: () -> Unit,
) {
    var showResetDialog by remember { mutableStateOf(false) }
    var showPortDialog by remember { mutableStateOf(false) }
    var showDnsDialog by remember { mutableStateOf(false) }
    var showLanguageDialog by remember { mutableStateOf(false) }
    var showHotspotSheet by remember { mutableStateOf(false) }
    var showBatteryWarning by remember { mutableStateOf(false) }
    var showReportWarning by remember { mutableStateOf(false) }
    val context = LocalContext.current

    QuickPingScreen {
        QuickPingTopBar(
            title = quickText("تنظیمات", "Settings"),
            onBack = onBack,
            action = {
                Icon(
                    painter = painterResource(R.drawable.ic_settings_reset),
                    contentDescription = quickText("بازنشانی تنظیمات", "Reset settings"),
                    tint = QuickPingColors.TextSecondary,
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(QuickPingColors.Surface)
                        .clickable { showResetDialog = true }
                        .padding(9.dp),
                )
            },
        )
        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(start = 14.dp, end = 14.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                SettingsGroup {
                    SettingsTile(
                        title = quickText("تقسیم تونل", "Split tunneling"),
                        icon = R.drawable.ic_split_tunneling,
                        onClick = onSplitTunneling,
                    )
                    SettingsTile(
                        title = quickText("گاردین", "Guardian"),
                        icon = R.drawable.ic_guard,
                        onClick = onGuardian,
                    )
                }
            }
            item {
                SettingsGroup {
                    SettingsTile(
                        title = quickText("حالت پروکسی", "Proxy mode"),
                        icon = R.drawable.ic_proxy,
                        trailing = {
                            QuickSwitch(
                                checked = settings.proxyModeEnabled,
                                onCheckedChange = { enabled ->
                                    onUpdateSettings { it.copy(proxyModeEnabled = enabled) }
                                },
                            )
                        },
                    )
                    SettingsTile(
                        title = quickText("اشتراک‌گذاری با نقطه اتصال", "Share over hotspot"),
                        icon = R.drawable.ic_hotspot,
                        onClick = { showHotspotSheet = true },
                    )
                    SettingsTile(
                        title = quickText("پورت SOCKS5 و HTTP", "HTTP and SOCKS5 port"),
                        icon = R.drawable.ic_port,
                        onClick = { showPortDialog = true },
                        trailing = { ValuePill(settings.proxyPort.toString()) },
                    )
                    SettingsTile(
                        title = "DNS",
                        icon = R.drawable.ic_dns,
                        onClick = { showDnsDialog = true },
                        trailing = { ValuePill(dnsLabel(settings.dnsProvider), caret = true) },
                    )
                    SettingsTile(
                        title = quickText("پینگ گرفتن خودکار سرورها", "Automatically ping servers"),
                        icon = R.drawable.ic_ping_auto,
                        trailing = {
                            QuickSwitch(
                                checked = settings.autoPing,
                                onCheckedChange = { enabled -> onUpdateSettings { it.copy(autoPing = enabled) } },
                            )
                        },
                    )
                }
            }
            item {
                SettingsGroup {
                    SettingsTile(
                        title = quickText("غیرفعال کردن بهینه‌سازی باتری", "Disable battery optimization"),
                        icon = R.drawable.ic_battery_optimize,
                        onClick = { showBatteryWarning = true },
                    )
                    SettingsTile(
                        title = quickText("زبان", "Language"),
                        icon = R.drawable.ic_language,
                        onClick = { showLanguageDialog = true },
                        trailing = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    settings.language.label,
                                    color = QuickPingColors.TextSecondary,
                                    style = MaterialTheme.typography.bodySmall,
                                )
                                Spacer(Modifier.width(9.dp))
                                EndChevron()
                            }
                        },
                    )
                    SettingsTile(
                        title = quickText("گزارش مشکل", "Report a problem"),
                        icon = R.drawable.ic_exclamation_circle,
                        onClick = { showReportWarning = true },
                    )
                    SettingsTile(
                        title = quickText("نسخه", "Version"),
                        icon = R.drawable.ic_version,
                        onClick = onVersion,
                        trailing = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    BuildConfig.VERSION_NAME.substringBefore('-'),
                                    color = QuickPingColors.TextSecondary,
                                    style = MaterialTheme.typography.bodySmall,
                                )
                                Spacer(Modifier.width(9.dp))
                                EndChevron()
                            }
                        },
                    )
                }
            }
        }
    }

    if (showResetDialog) {
        ReferenceDialog(
            title = quickText("بازنشانی تنظیمات", "Reset settings"),
            body = quickText(
                "همهٔ تنظیمات اتصال، گاردین و تقسیم تونل به حالت اولیه بازمی‌گردند. حساب شما حذف نمی‌شود.",
                "Connection, Guardian and split-tunnel preferences will return to their defaults. Your account will not be removed.",
            ),
            confirm = quickText("بازنشانی", "Reset"),
            onConfirm = { onResetSettings(); showResetDialog = false },
            onDismiss = { showResetDialog = false },
        )
    }
    if (showPortDialog) {
        PortDialog(
            currentPort = settings.proxyPort,
            onSave = { port ->
                onUpdateSettings { it.copy(proxyPort = port, localProxyEnabled = true) }
                showPortDialog = false
            },
            onDismiss = { showPortDialog = false },
        )
    }
    if (showDnsDialog) {
        ChoiceDialog(
            title = "DNS",
            options = DnsProvider.entries.map { it to dnsLabel(it) },
            selected = settings.dnsProvider,
            onSelect = { provider ->
                onUpdateSettings { it.copy(dnsProvider = provider) }
                showDnsDialog = false
            },
            onDismiss = { showDnsDialog = false },
        )
    }
    if (showLanguageDialog) {
        ChoiceDialog(
            title = quickText("زبان", "Language"),
            options = AppLanguage.entries.map { it to it.label },
            selected = settings.language,
            onSelect = { language ->
                onUpdateSettings { it.copy(language = language) }
                showLanguageDialog = false
            },
            onDismiss = { showLanguageDialog = false },
        )
    }
    if (showHotspotSheet) {
        HotspotSheet(
            enabled = settings.shareHotspot,
            port = settings.proxyPort,
            onEnabledChange = { enabled ->
                onUpdateSettings { it.copy(shareHotspot = enabled, localProxyEnabled = true) }
            },
            onDismiss = { showHotspotSheet = false },
        )
    }
    if (showBatteryWarning) {
        ReferenceDialog(
            title = quickText("بهینه‌سازی باتری", "Battery optimization"),
            body = quickText(
                "برای جلوگیری از قطع‌شدن VPN در پس‌زمینه، nimHUB را در صفحهٔ بعد از محدودیت باتری خارج کنید.",
                "Allow nimHUB to run without battery restrictions so the VPN remains connected in the background.",
            ),
            confirm = quickText("ادامه", "Continue"),
            onConfirm = { showBatteryWarning = false; openBatterySettings(context) },
            onDismiss = { showBatteryWarning = false },
        )
    }
    if (showReportWarning) {
        ReferenceDialog(
            title = quickText("ابتدا مشکل را امتحان کنید", "Try reproducing the issue first"),
            body = quickText(
                "قبل از ادامه، مطمئن شوید مشکل دوباره رخ می‌دهد. گزارش فنیِ بدون اطلاعات ورود برای پشتیبانی آماده خواهد شد.",
                "Before continuing, reproduce the issue once. A technical report without login secrets will be prepared for support.",
            ),
            confirm = quickText("ادامه", "Continue"),
            onConfirm = { showReportWarning = false; openProblemReport(context, settings) },
            onDismiss = { showReportWarning = false },
        )
    }
}

@Composable
private fun SettingsGroup(content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(23.dp))
            .background(QuickPingColors.Surface.copy(alpha = 0.82f))
            .padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        content = content,
    )
}

@Composable
private fun SettingsTile(
    title: String,
    @DrawableRes icon: Int,
    onClick: (() -> Unit)? = null,
    trailing: @Composable RowScope.() -> Unit = { EndChevron() },
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp)
            .clip(RoundedCornerShape(15.dp))
            .background(QuickPingColors.SurfaceHigh)
            .then(if (onClick == null) Modifier else Modifier.clickable(onClick = onClick))
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(painterResource(icon), null, tint = QuickPingColors.TextSecondary, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(10.dp))
        Text(
            title,
            modifier = Modifier.weight(1f),
            color = QuickPingColors.TextPrimary,
            style = MaterialTheme.typography.bodyLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.width(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically, content = trailing)
    }
}

@Composable
private fun EndChevron() {
    Icon(
        painterResource(R.drawable.ic_chevron_end),
        contentDescription = null,
        tint = QuickPingColors.TextSecondary,
        modifier = Modifier.size(18.dp),
    )
}

@Composable
private fun ValuePill(value: String, caret: Boolean = false) {
    Row(
        modifier = Modifier
            .height(36.dp)
            .border(1.dp, QuickPingColors.Border, RoundedCornerShape(18.dp))
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (caret) {
            Icon(painterResource(R.drawable.ic_caret_down), null, tint = QuickPingColors.TextSecondary, modifier = Modifier.size(14.dp))
            Spacer(Modifier.width(7.dp))
        }
        Text(value, color = QuickPingColors.TextSecondary, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun PortDialog(currentPort: Int, onSave: (Int) -> Unit, onDismiss: () -> Unit) {
    var value by remember(currentPort) { mutableStateOf(currentPort.toString()) }
    val parsed = value.toIntOrNull()
    val valid = parsed != null && parsed in 1024..65535
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = QuickPingColors.SurfaceHigh,
        shape = RoundedCornerShape(24.dp),
        title = { Text(quickText("پورت SOCKS5 و HTTP", "HTTP and SOCKS5 port")) },
        text = {
            OutlinedTextField(
                value = value,
                onValueChange = { input -> value = input.filter(Char::isDigit).take(5) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                isError = value.isNotEmpty() && !valid,
                supportingText = if (!valid) ({ Text(quickText("عددی بین ۱۰۲۴ تا ۶۵۵۳۵ وارد کنید", "Enter a number from 1024 to 65535")) }) else null,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                shape = RoundedCornerShape(14.dp),
                colors = dialogFieldColors(),
            )
        },
        confirmButton = { TextButton(onClick = { onSave(parsed!!) }, enabled = valid) { Text(quickText("تنظیم پورت", "Save port")) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(quickText("انصراف", "Cancel")) } },
    )
}

@Composable
private fun <T> ChoiceDialog(
    title: String,
    options: List<Pair<T, String>>,
    selected: T,
    onSelect: (T) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = QuickPingColors.SurfaceHigh,
        shape = RoundedCornerShape(24.dp),
        title = { Text(title, color = QuickPingColors.TextPrimary) },
        text = {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                items(options) { (value, label) ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(46.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(QuickPingColors.Surface)
                            .clickable { onSelect(value) }
                            .padding(horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(
                            selected = value == selected,
                            onClick = null,
                            colors = RadioButtonDefaults.colors(
                                selectedColor = QuickPingColors.Primary,
                                unselectedColor = QuickPingColors.TextMuted,
                            ),
                        )
                        Text(label, modifier = Modifier.weight(1f), textAlign = TextAlign.End)
                    }
                }
            }
        },
        confirmButton = {},
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HotspotSheet(
    enabled: Boolean,
    port: Int,
    onEnabledChange: (Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val host = remember(enabled) { localIpv4Address() }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = QuickPingColors.SurfaceHigh,
        contentColor = QuickPingColors.TextPrimary,
        dragHandle = null,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 34.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(painterResource(R.drawable.ic_hotspot), null, tint = QuickPingColors.TextSecondary, modifier = Modifier.size(27.dp))
                Spacer(Modifier.width(10.dp))
                Text(quickText("اشتراک‌گذاری VPN با نقطه اتصال", "Share VPN over hotspot"), Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                TextButton(onClick = onDismiss) { Text("×", style = MaterialTheme.typography.titleLarge) }
            }
            Spacer(Modifier.height(18.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                QuickSwitch(checked = enabled, onCheckedChange = onEnabledChange)
                Spacer(Modifier.width(12.dp))
                Text(quickText("فعال‌سازی اشتراک‌گذاری با نقطه اتصال", "Enable hotspot sharing"), Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
            }
            Spacer(Modifier.height(8.dp))
            Text(
                quickText(
                    "دستگاه متصل به هات‌اسپات را با میزبان و پورت زیر روی پروکسی HTTP یا SOCKS5 تنظیم کنید.",
                    "Configure the connected device's HTTP or SOCKS5 proxy with the host and port below.",
                ),
                color = QuickPingColors.TextSecondary,
                style = MaterialTheme.typography.bodySmall,
            )
            Spacer(Modifier.height(18.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                CopyValueCard(Modifier.weight(1f), host, quickText("هاست", "Host"), R.drawable.ic_server) { copyText(context, "nimHUB host", host) }
                CopyValueCard(Modifier.weight(1f), port.toString(), quickText("پورت", "Port"), R.drawable.ic_port) { copyText(context, "nimHUB port", port.toString()) }
            }
            Spacer(Modifier.height(12.dp))
            Text(quickText("برای کپی‌کردن کلیک کنید", "Tap a value to copy"), Modifier.fillMaxWidth(), color = QuickPingColors.TextMuted, style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center)
        }
    }
}

@Composable
private fun CopyValueCard(
    modifier: Modifier,
    value: String,
    label: String,
    @DrawableRes icon: Int,
    onCopy: () -> Unit,
) {
    Column(
        modifier = modifier.height(92.dp).border(1.dp, QuickPingColors.Border, RoundedCornerShape(18.dp)).clickable(onClick = onCopy).padding(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(value, color = QuickPingColors.TextPrimary, style = MaterialTheme.typography.labelLarge)
        Spacer(Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label, color = QuickPingColors.TextSecondary, style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.width(5.dp))
            Icon(painterResource(icon), null, tint = QuickPingColors.TextSecondary, modifier = Modifier.size(16.dp))
        }
    }
}

@Composable
private fun ReferenceDialog(
    title: String,
    body: String,
    confirm: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = QuickPingColors.SurfaceHigh,
        shape = RoundedCornerShape(24.dp),
        title = { Text(title, color = QuickPingColors.TextPrimary, textAlign = TextAlign.Center) },
        text = { Text(body, color = QuickPingColors.TextSecondary, textAlign = TextAlign.Center) },
        confirmButton = { TextButton(onClick = onConfirm) { Text(confirm) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(quickText("انصراف", "Cancel")) } },
    )
}

@Composable
private fun dialogFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = QuickPingColors.Primary,
    unfocusedBorderColor = QuickPingColors.Border,
    focusedTextColor = QuickPingColors.TextPrimary,
    unfocusedTextColor = QuickPingColors.TextPrimary,
    errorBorderColor = QuickPingColors.Danger,
)

private fun openBatterySettings(context: Context) {
    val power = context.getSystemService(PowerManager::class.java)
    if (power?.isIgnoringBatteryOptimizations(context.packageName) == true) {
        context.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
        return
    }
    runCatching {
        context.startActivity(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:${context.packageName}")))
    }.onFailure { context.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)) }
}

private fun openProblemReport(context: Context, appSettings: AppSettings) {
    val serviceStatus = QuickPingVpnService.status.value
    val failure = serviceStatus.failure
    val report = buildString {
        appendLine("nimHUB ${BuildConfig.VERSION_NAME}")
        appendLine("Android ${android.os.Build.VERSION.RELEASE} (API ${android.os.Build.VERSION.SDK_INT})")
        appendLine("Device: ${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}")
        appendLine("VPN state: ${serviceStatus.state.name}")
        failure?.let {
            appendLine("Failure code: ${it.code}")
            appendLine("Failure detail: ${it.safeDetail}")
        }
        appendLine("Mode: ${if (appSettings.proxyModeEnabled) "proxy" else "tun"}")
        appendLine("DNS: ${appSettings.dnsProvider.storageValue}")
        appendLine("Split tunnel: ${appSettings.splitTunnelingEnabled} (${appSettings.splitTunnelMode.name.lowercase()})")
        appendLine("Guardian: ${appSettings.guardianEnabled}")
        appendLine("Hotspot sharing: ${appSettings.shareHotspot}")
        appendLine()
        append("Problem description: ")
    }
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_SUBJECT, "nimHUB problem report")
        putExtra(Intent.EXTRA_TEXT, report)
    }
    context.startActivity(Intent.createChooser(intent, "nimHUB"))
}

private fun localIpv4Address(): String = runCatching {
    NetworkInterface.getNetworkInterfaces().toList().asSequence()
        .flatMap { it.inetAddresses.toList().asSequence() }
        .filterIsInstance<Inet4Address>()
        .firstOrNull { !it.isLoopbackAddress && it.isSiteLocalAddress }
        ?.hostAddress
}.getOrNull().orEmpty().ifBlank { "0.0.0.0" }

private fun copyText(context: Context, label: String, value: String) {
    context.getSystemService(ClipboardManager::class.java)?.setPrimaryClip(ClipData.newPlainText(label, value))
}

@Composable
private fun dnsLabel(provider: DnsProvider): String = when (provider) {
    DnsProvider.Default -> quickText("پیش‌فرض", "Default")
    DnsProvider.Google -> quickText("گوگل", "Google")
    DnsProvider.Cloudflare -> quickText("کلودفلر", "Cloudflare")
}
