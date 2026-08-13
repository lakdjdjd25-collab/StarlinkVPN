package org.quickping.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Modifier
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import org.quickping.app.state.QuickPingUiState

@Composable
internal fun ReferenceServerList(
    modifier: Modifier,
    state: QuickPingUiState,
    bestLocationSelected: Boolean,
    onSelectBestLocation: () -> Unit,
    onSelectServer: (String) -> Unit,
) {
    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(
            start = 14.dp,
            end = 14.dp,
            bottom = 52.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (state.servers.isNotEmpty()) {
            item(key = "recent") {
                ReferenceRecentServers(state, onSelectServer)
            }
            item(key = "divider") {
                ReferenceDashedDivider(
                    modifier = Modifier.padding(
                        start = 29.dp,
                        end = 29.dp,
                        top = 15.dp,
                        bottom = 1.dp,
                    ),
                )
            }
        }
        item(key = "filters") {
            ReferenceFilterRow()
        }
        if (state.servers.isNotEmpty()) {
            item(key = "best-location") {
                ReferenceBestLocationRow(
                    selected = bestLocationSelected,
                    onClick = onSelectBestLocation,
                )
            }
            items(state.servers, key = { it.id }) { server ->
                ReferenceServerRow(
                    server = server,
                    title = referenceServerTitle(server, state.servers),
                    selected = !bestLocationSelected && server.id == state.selectedServerId,
                    onClick = { onSelectServer(server.id) },
                )
            }
        }
    }
}
