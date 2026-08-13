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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import org.quickping.app.R
import org.quickping.app.core.design.QuickPingColors
import org.quickping.app.core.design.quickText
import org.quickping.app.model.AppSettings
import org.quickping.app.model.InstalledApp
import org.quickping.app.model.SplitTunnelMode
import org.quickping.app.ui.components.GlassCard
import org.quickping.app.ui.components.DashedDivider
import org.quickping.app.ui.components.QuickPingScreen
import org.quickping.app.ui.components.QuickPingTopBar
import org.quickping.app.ui.components.QuickSwitch
import org.quickping.app.ui.components.SettingRow
import org.quickping.app.vpn.normalizeSplitAddress

@Composable
fun SplitTunnelingScreen(
    settings: AppSettings,
    installedApps: List<InstalledApp>,
    loadingApps: Boolean,
    onUpdateSettings: ((AppSettings) -> AppSettings) -> Unit,
    onBack: () -> Unit,
) {
    var showAppsDialog by remember { mutableStateOf(false) }
    var showAddressesDialog by remember { mutableStateOf(false) }
    var showModeDialog by remember { mutableStateOf(false) }
    QuickPingScreen {
        QuickPingTopBar(
            title = quickText("تقسیم تونل", "Split tunneling"),
            onBack = onBack,
            action = {
                Icon(
                    painter = painterResource(R.drawable.ic_settings_reset),
                    contentDescription = quickText("بازنشانی تقسیم تونل", "Reset split tunneling"),
                    tint = QuickPingColors.TextSecondary,
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(QuickPingColors.Surface)
                        .clickable {
                            onUpdateSettings {
                                it.copy(
                                    splitTunnelingEnabled = false,
                                    splitTunnelMode = SplitTunnelMode.Exclude,
                                    splitTunnelPackages = emptySet(),
                                    splitTunnelAddresses = emptyList(),
                                    rememberSplitTunnelSettings = true,
                                    blockIrDomains = true,
                                )
                            }
                        }
                        .padding(9.dp),
                )
            },
        )
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 12.dp, vertical = 6.dp),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 18.dp, vertical = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(
                    modifier = Modifier
                        .size(58.dp)
                        .border(1.dp, QuickPingColors.Border, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        painterResource(R.drawable.ic_split_tunneling),
                        contentDescription = null,
                        tint = QuickPingColors.TextPrimary,
                        modifier = Modifier.size(32.dp),
                    )
                }
                Spacer(Modifier.height(10.dp))
                Text(
                    quickText(
                        "تقسیم تونل به شما اجازه می‌دهد انتخاب کنید کدام برنامه‌ها از VPN استفاده کنند و کدام به‌صورت مستقیم به اینترنت متصل شوند.",
                        "Split tunneling lets you choose which apps use the VPN and which connect directly to the internet.",
                    ),
                    color = QuickPingColors.TextSecondary,
                    style = MaterialTheme.typography.bodySmall,
                    textAlign = TextAlign.Center,
                )
            }
            GlassCard(Modifier.fillMaxWidth()) {
                SettingRow(
                    title = quickText("فعال‌سازی تقسیم تونل", "Enable split tunneling"),
                    icon = R.drawable.ic_split_tunneling,
                    modifier = Modifier.clip(RoundedCornerShape(15.dp)).background(QuickPingColors.SurfaceHigh),
                    trailing = {
                        QuickSwitch(
                            checked = settings.splitTunnelingEnabled,
                            onCheckedChange = { enabled -> onUpdateSettings { it.copy(splitTunnelingEnabled = enabled) } },
                        )
                    },
                )
                Row(
                    modifier = Modifier.fillMaxWidth().height(58.dp).clickable { showModeDialog = true }.padding(horizontal = 18.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(quickText("حالت", "Mode"), color = QuickPingColors.TextSecondary)
                    Spacer(Modifier.weight(1f))
                    Text(
                        quickText(
                            if (settings.splitTunnelMode == SplitTunnelMode.Exclude) "انحصاری" else "شامل",
                            if (settings.splitTunnelMode == SplitTunnelMode.Exclude) "Exclude selected" else "Only selected",
                        ),
                        modifier = Modifier
                            .border(1.dp, QuickPingColors.Border, RoundedCornerShape(18.dp))
                            .padding(horizontal = 15.dp, vertical = 8.dp),
                        color = QuickPingColors.TextSecondary,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                DashedDivider()
                Row(
                    modifier = Modifier.fillMaxWidth().padding(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    SplitSelectionCard(
                        modifier = Modifier.weight(1f),
                        title = quickText("برنامه‌ها", "Apps"),
                        count = quickText("${settings.splitTunnelPackages.size} علامت‌گذاری شده", "${settings.splitTunnelPackages.size} selected"),
                        icon = R.drawable.ic_apps,
                        onClick = { showAppsDialog = true },
                    )
                    SplitSelectionCard(
                        modifier = Modifier.weight(1f),
                        title = quickText("نشانی‌ها", "Addresses"),
                        count = quickText("${settings.splitTunnelAddresses.size} علامت‌گذاری شده", "${settings.splitTunnelAddresses.size} selected"),
                        icon = R.drawable.ic_addresses,
                        onClick = { showAddressesDialog = true },
                    )
                }
            }
            Spacer(Modifier.height(10.dp))
            GlassCard(Modifier.fillMaxWidth()) {
                SettingRow(
                    title = quickText("مسدود کردن اتصالات .ir", "Block .ir connections"),
                    icon = R.drawable.ic_lock,
                    modifier = Modifier.clip(RoundedCornerShape(15.dp)).background(QuickPingColors.SurfaceHigh),
                    trailing = {
                        QuickSwitch(
                            checked = settings.blockIrDomains,
                            onCheckedChange = { enabled -> onUpdateSettings { it.copy(blockIrDomains = enabled) } },
                        )
                    },
                )
                Row(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(quickText("وضعیت:", "Status:"), color = QuickPingColors.TextPrimary, style = MaterialTheme.typography.labelMedium)
                    Spacer(Modifier.weight(1f))
                    Text(
                        if (settings.blockIrDomains) quickText("🔒 مسدود است", "🔒 Blocked") else quickText("🔓 مجاز است", "🔓 Allowed"),
                        color = if (settings.blockIrDomains) Color(0xFF59BFD8) else QuickPingColors.TextMuted,
                        style = MaterialTheme.typography.labelMedium,
                    )
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }

    if (showModeDialog) {
        AlertDialog(
            onDismissRequest = { showModeDialog = false },
            containerColor = QuickPingColors.SurfaceHigh,
            shape = RoundedCornerShape(22.dp),
            title = { Text(quickText("حالت تقسیم تونل", "Split-tunnel mode")) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    ModeChoice(
                        title = quickText("انحصاری", "Exclude selected"),
                        subtitle = quickText("موارد انتخاب‌شده مستقیم متصل می‌شوند", "Selected items connect directly"),
                        selected = settings.splitTunnelMode == SplitTunnelMode.Exclude,
                    ) {
                        onUpdateSettings { it.copy(splitTunnelMode = SplitTunnelMode.Exclude) }
                        showModeDialog = false
                    }
                    ModeChoice(
                        title = quickText("شامل", "Only selected"),
                        subtitle = quickText("فقط موارد انتخاب‌شده از VPN عبور می‌کنند", "Only selected items use the VPN"),
                        selected = settings.splitTunnelMode == SplitTunnelMode.Include,
                    ) {
                        onUpdateSettings { it.copy(splitTunnelMode = SplitTunnelMode.Include) }
                        showModeDialog = false
                    }
                }
            },
            confirmButton = {},
        )
    }

    if (showAppsDialog) {
        SplitAppsDialog(
            apps = installedApps,
            selectedPackages = settings.splitTunnelPackages,
            loading = loadingApps,
            onToggle = { packageName ->
                onUpdateSettings { current ->
                    val selected = current.splitTunnelPackages.toMutableSet()
                    if (!selected.add(packageName)) selected.remove(packageName)
                    current.copy(splitTunnelPackages = selected)
                }
            },
            onDismiss = { showAppsDialog = false },
        )
    }
    if (showAddressesDialog) {
        SplitAddressesDialog(
            addresses = settings.splitTunnelAddresses,
            onAdd = { address ->
                onUpdateSettings { current ->
                    current.copy(splitTunnelAddresses = (current.splitTunnelAddresses + address).distinct())
                }
            },
            onRemove = { address ->
                onUpdateSettings { current ->
                    current.copy(splitTunnelAddresses = current.splitTunnelAddresses - address)
                }
            },
            onDismiss = { showAddressesDialog = false },
        )
    }
}

@Composable
private fun SplitSelectionCard(
    modifier: Modifier,
    title: String,
    count: String,
    icon: Int,
    onClick: () -> Unit,
) {
    Column(
        modifier = modifier
            .height(92.dp)
            .background(QuickPingColors.SurfaceHigh, RoundedCornerShape(17.dp))
            .border(1.dp, QuickPingColors.BorderSoft, RoundedCornerShape(17.dp))
            .clickable(onClick = onClick)
            .padding(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(painterResource(icon), null, tint = QuickPingColors.TextSecondary, modifier = Modifier.size(24.dp))
        Spacer(Modifier.height(8.dp))
        Text(title, color = QuickPingColors.TextPrimary, style = MaterialTheme.typography.labelLarge)
        Text(count, color = QuickPingColors.TextMuted, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun ModeChoice(title: String, subtitle: String, selected: Boolean, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, if (selected) QuickPingColors.Primary else QuickPingColors.Border, RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(14.dp),
    ) {
        Text(title, color = QuickPingColors.TextPrimary)
        Text(subtitle, color = QuickPingColors.TextMuted, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun SplitAppsDialog(
    apps: List<InstalledApp>,
    selectedPackages: Set<String>,
    loading: Boolean,
    onToggle: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var query by remember { mutableStateOf("") }
    val filtered = remember(apps, query) {
        if (query.isBlank()) apps else apps.filter {
            it.label.contains(query, ignoreCase = true) || it.packageName.contains(query, ignoreCase = true)
        }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = QuickPingColors.SurfaceHigh,
        shape = RoundedCornerShape(22.dp),
        title = { Text(quickText("انتخاب برنامه‌ها", "Select apps"), color = QuickPingColors.TextPrimary) },
        text = {
            Column {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    placeholder = { Text(quickText("جستجوی برنامه", "Search apps")) },
                    shape = RoundedCornerShape(12.dp),
                    colors = quickPingFieldColors(),
                )
                Spacer(Modifier.height(8.dp))
                if (loading) {
                    Text(
                        quickText("در حال خواندن فهرست برنامه‌های دستگاه…", "Loading apps on this device…"),
                        color = QuickPingColors.TextSecondary,
                        modifier = Modifier.padding(vertical = 18.dp),
                    )
                } else {
                    LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 380.dp)) {
                        items(filtered, key = InstalledApp::packageName) { app ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onToggle(app.packageName) }
                                    .padding(vertical = 7.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Checkbox(
                                    checked = app.packageName in selectedPackages,
                                    onCheckedChange = null,
                                    colors = CheckboxDefaults.colors(checkedColor = QuickPingColors.Primary),
                                )
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        app.label,
                                        color = QuickPingColors.TextPrimary,
                                        textAlign = TextAlign.End,
                                        modifier = Modifier.fillMaxWidth(),
                                    )
                                    Text(
                                        app.packageName,
                                        color = QuickPingColors.TextMuted,
                                        style = MaterialTheme.typography.labelSmall,
                                        textAlign = TextAlign.End,
                                        modifier = Modifier.fillMaxWidth(),
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(quickText("انجام شد", "Done")) } },
    )
}

@Composable
private fun SplitAddressesDialog(
    addresses: List<String>,
    onAdd: (String) -> Unit,
    onRemove: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var input by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    val invalidAddressMessage = quickText(
        "دامنه، IP یا CIDR معتبر وارد کنید",
        "Enter a valid domain, IP address or CIDR",
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = QuickPingColors.SurfaceHigh,
        shape = RoundedCornerShape(22.dp),
        title = { Text(quickText("نشانی‌های تقسیم تونل", "Split-tunnel addresses"), color = QuickPingColors.TextPrimary) },
        text = {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextButton(
                        onClick = {
                            val normalized = normalizeSplitAddress(input)
                            if (normalized == null) {
                                error = invalidAddressMessage
                            } else {
                                onAdd(normalized)
                                input = ""
                                error = null
                            }
                        },
                    ) { Text(quickText("افزودن", "Add")) }
                    Spacer(Modifier.width(6.dp))
                    OutlinedTextField(
                        value = input,
                        onValueChange = { input = it; error = null },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        placeholder = { Text("example.com یا 10.0.0.0/8") },
                        isError = error != null,
                        shape = RoundedCornerShape(12.dp),
                        colors = quickPingFieldColors(),
                    )
                }
                error?.let {
                    Text(it, color = QuickPingColors.Danger, style = MaterialTheme.typography.labelSmall)
                }
                Spacer(Modifier.height(8.dp))
                LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 280.dp)) {
                    items(addresses, key = { it }) { address ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            TextButton(onClick = { onRemove(address) }) {
                                Text(quickText("حذف", "Remove"), color = QuickPingColors.Danger)
                            }
                            Text(
                                address,
                                modifier = Modifier.weight(1f),
                                color = QuickPingColors.TextPrimary,
                                textAlign = TextAlign.End,
                            )
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(quickText("انجام شد", "Done")) } },
    )
}

@Composable
private fun quickPingFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = QuickPingColors.Primary,
    unfocusedBorderColor = QuickPingColors.Border,
    focusedTextColor = QuickPingColors.TextPrimary,
    unfocusedTextColor = QuickPingColors.TextPrimary,
    focusedPlaceholderColor = QuickPingColors.TextMuted,
    unfocusedPlaceholderColor = QuickPingColors.TextMuted,
)

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
