package org.quickping.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.quickping.app.core.design.Peyda
import org.quickping.app.core.design.QuickPingColors
import org.quickping.app.core.design.quickText
import org.quickping.app.model.Server
import org.quickping.app.state.QuickPingUiState

private enum class ReferenceFilterMode { Recommended, Fastest, Free, Unlimited }

@Composable
internal fun ReferenceServerList(
    modifier: Modifier,
    state: QuickPingUiState,
    bestLocationSelected: Boolean,
    onSelectBestLocation: () -> Unit,
    onSelectServer: (String) -> Unit,
) {
    var searchOpen by rememberSaveable { mutableStateOf(false) }
    var query by rememberSaveable { mutableStateOf("") }
    var gamingOnly by rememberSaveable { mutableStateOf(false) }
    var showFilters by rememberSaveable { mutableStateOf(false) }
    var filterModeName by rememberSaveable { mutableStateOf(ReferenceFilterMode.Recommended.name) }
    val filterMode = runCatching { ReferenceFilterMode.valueOf(filterModeName) }
        .getOrDefault(ReferenceFilterMode.Recommended)
    val normalizedQuery = query.trim().lowercase()
    val visibleServers = state.servers.asSequence()
        .filter { !gamingOnly || it.isReferenceGamingServer() }
        .filter { normalizedQuery.isBlank() || referenceCountrySearchText(it).contains(normalizedQuery) }
        .filter {
            when (filterMode) {
                ReferenceFilterMode.Free -> it.freeAllowed
                ReferenceFilterMode.Unlimited -> it.unmetered || it.isUnlimitedCategory
                else -> true
            }
        }
        .toList()
        .let { servers ->
            if (filterMode == ReferenceFilterMode.Fastest) {
                servers.sortedWith(compareBy<Server> { !it.selectable }.thenBy { it.pingMs == null }.thenBy { it.pingMs ?: Int.MAX_VALUE })
            } else {
                servers
            }
        }

    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(start = 14.dp, end = 14.dp, bottom = 52.dp),
        verticalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        if (state.servers.isNotEmpty()) {
            item(key = "recent") { ReferenceRecentServers(state, onSelectServer) }
            item(key = "divider") {
                ReferenceDashedDivider(
                    modifier = Modifier.padding(start = 29.dp, end = 29.dp, top = 12.dp, bottom = 1.dp),
                )
            }
        }
        item(key = "filters") {
            ReferenceFilterRow(
                searchOpen = searchOpen,
                query = query,
                onQueryChange = { query = it },
                onSearchClick = {
                    if (searchOpen) {
                        query = ""
                        searchOpen = false
                    } else {
                        searchOpen = true
                    }
                },
                onFilterClick = { showFilters = true },
                filterActive = filterMode != ReferenceFilterMode.Recommended,
                gamingOnly = gamingOnly,
                onGamingOnlyChange = { gamingOnly = it },
            )
        }
        val cleanDefaultView = normalizedQuery.isBlank() && !gamingOnly && filterMode == ReferenceFilterMode.Recommended
        if (state.servers.isNotEmpty() && cleanDefaultView && state.servers.any { it.selectable }) {
            item(key = "best-location") {
                ReferenceBestLocationRow(selected = bestLocationSelected, onClick = onSelectBestLocation)
            }
        }
        items(visibleServers, key = { it.id }) { server ->
            ReferenceServerRow(
                server = server,
                title = referenceServerTitle(server, state.servers),
                selected = !bestLocationSelected && server.id == state.selectedServerId,
                onClick = { onSelectServer(server.id) },
            )
        }
        if (state.servers.isNotEmpty() && visibleServers.isEmpty()) {
            item(key = "empty-filter") {
                Text(
                    text = quickText("سروری با این فیلتر پیدا نشد", "No server matches these filters"),
                    modifier = Modifier.fillMaxWidth().padding(vertical = 28.dp),
                    color = QuickPingColors.TextMuted,
                    fontFamily = Peyda,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }

    if (showFilters) {
        ReferenceFilterDialog(
            selected = filterMode,
            onSelect = {
                filterModeName = it.name
                showFilters = false
            },
            onDismiss = { showFilters = false },
        )
    }
}

@Composable
private fun ReferenceFilterDialog(
    selected: ReferenceFilterMode,
    onSelect: (ReferenceFilterMode) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF111318),
        shape = RoundedCornerShape(24.dp),
        title = {
            Text(
                text = quickText("فیلتر سرورها", "Server filters"),
                modifier = Modifier.fillMaxWidth(),
                color = QuickPingColors.TextPrimary,
                fontFamily = Peyda,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                ReferenceFilterMode.entries.forEach { mode ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .background(Color(0xFF0B0D11), RoundedCornerShape(14.dp))
                            .clickable { onSelect(mode) }
                            .padding(horizontal = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = mode.referenceLabel(),
                            modifier = Modifier.weight(1f),
                            color = QuickPingColors.TextPrimary,
                            fontFamily = Peyda,
                            fontSize = 14.sp,
                            textAlign = TextAlign.End,
                        )
                        RadioButton(
                            selected = selected == mode,
                            onClick = null,
                            colors = RadioButtonDefaults.colors(
                                selectedColor = QuickPingColors.Primary,
                                unselectedColor = QuickPingColors.TextMuted,
                            ),
                        )
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(quickText("بستن", "Close"), color = QuickPingColors.TextSecondary, fontFamily = Peyda)
            }
        },
    )
}

@Composable
private fun ReferenceFilterMode.referenceLabel(): String = when (this) {
    ReferenceFilterMode.Recommended -> quickText("پیشنهادی", "Recommended")
    ReferenceFilterMode.Fastest -> quickText("کمترین پینگ", "Lowest ping")
    ReferenceFilterMode.Free -> quickText("مناسب سرویس رایگان", "Free available")
    ReferenceFilterMode.Unlimited -> quickText("Unlimited ∞", "Unlimited ∞")
}

private fun Server.isReferenceGamingServer(): Boolean {
    if (isGaming) return true
    val searchable = "$title $remarks".lowercase()
    return listOf("gaming", "game", "گیم", "بازی").any(searchable::contains)
}
