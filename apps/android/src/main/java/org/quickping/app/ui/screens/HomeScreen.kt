package org.quickping.app.ui.screens

import androidx.compose.runtime.Composable
import org.quickping.app.state.QuickPingUiState

@Composable
fun HomeScreen(
    state: QuickPingUiState,
    onToggleConnection: () -> Unit,
    onSelectServer: (String) -> Unit,
    onSettings: () -> Unit,
    onAccount: () -> Unit,
    onNotifications: () -> Unit,
    onUpgrade: () -> Unit,
) {
    VipAccessRevocationGuard(state = state, onDisconnect = onToggleConnection)
    val stableServers = rememberStableServerPings(
        servers = state.servers,
        enabled = state.settings.autoPing,
    )
    ReferenceHomeScreen(
        state = state.copy(servers = stableServers),
        onToggleConnection = onToggleConnection,
        onSelectServer = onSelectServer,
        onSettings = onSettings,
        onAccount = onAccount,
        onNotifications = onNotifications,
        onUpgrade = onUpgrade,
    )
}
