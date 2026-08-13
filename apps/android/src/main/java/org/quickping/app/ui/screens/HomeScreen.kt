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
) {
    ReferenceHomeScreen(
        state = state,
        onToggleConnection = onToggleConnection,
        onSelectServer = onSelectServer,
        onSettings = onSettings,
        onAccount = onAccount,
        onNotifications = onNotifications,
    )
}
