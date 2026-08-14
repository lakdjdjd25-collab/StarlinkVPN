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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import java.util.Locale
import org.quickping.app.core.design.QuickPingColors
import org.quickping.app.core.design.quickText
import org.quickping.app.model.Service
import org.quickping.app.ui.components.DashedDivider
import org.quickping.app.ui.components.QuickPingScreen
import org.quickping.app.ui.components.QuickPingTopBar

@Composable
fun ServicesCatalogScreen(
    services: List<Service>,
    currentServiceId: String,
    onSelectService: (String) -> Unit,
    onBack: () -> Unit,
) {
    QuickPingScreen {
        QuickPingTopBar(title = quickText("سرویس‌ها", "Services"), onBack = onBack)
        if (services.isEmpty()) {
            Box(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    quickText("سرویسی برای این حساب وجود ندارد", "No services are available for this account"),
                    color = QuickPingColors.TextMuted,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(services, key = Service::id) { service ->
                    ServiceCatalogCard(
                        service = service,
                        selected = service.id == currentServiceId,
                        onClick = { onSelectService(service.id) },
                    )
                }
            }
        }
    }
}

@Composable
private fun ServiceCatalogCard(
    service: Service,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val stroke = if (selected) QuickPingColors.Primary else QuickPingColors.TextSecondary.copy(alpha = 0.55f)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(QuickPingColors.Surface, RoundedCornerShape(22.dp))
            .border(1.dp, stroke, RoundedCornerShape(22.dp))
            .clickable(onClick = onClick),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 13.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier.size(20.dp).border(2.dp, stroke, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                if (selected) {
                    Box(Modifier.size(12.dp).background(QuickPingColors.Primary, CircleShape))
                }
            }
            Spacer(Modifier.width(9.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    service.name.ifBlank { service.license },
                    color = QuickPingColors.TextPrimary,
                    style = MaterialTheme.typography.labelLarge,
                )
                if (service.license.isNotBlank()) {
                    Text(
                        service.license,
                        color = QuickPingColors.TextMuted,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
            Text(
                if (service.isFree) quickText("رایگان", "Free") else service.plan,
                color = if (selected) QuickPingColors.Success else QuickPingColors.TextSecondary,
                style = MaterialTheme.typography.labelMedium,
            )
        }

        DashedDivider()

        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            ServiceCatalogMetric(
                modifier = Modifier.weight(1f),
                value = "${formatGb(service.usedBytes)}GB / ${formatGb(service.totalBytes)}GB",
                label = quickText("دادهٔ استفاده‌شده", "Data used"),
            )
            ServiceCatalogMetric(
                modifier = Modifier.weight(1f),
                value = "${service.daysLeft} ${quickText("روز", "days")}",
                label = quickText("اعتبار", "Validity"),
            )
        }
    }
}

@Composable
private fun ServiceCatalogMetric(
    modifier: Modifier,
    value: String,
    label: String,
) {
    Column(
        modifier = modifier
            .background(Color(0xFF101319), RoundedCornerShape(15.dp))
            .padding(horizontal = 10.dp, vertical = 10.dp),
    ) {
        Text(
            value,
            color = QuickPingColors.TextPrimary,
            style = MaterialTheme.typography.labelMedium,
        )
        Text(
            label,
            color = QuickPingColors.TextMuted,
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

private fun formatGb(bytes: Long): String {
    val gb = bytes.coerceAtLeast(0L).toDouble() / (1024.0 * 1024.0 * 1024.0)
    return if (gb >= 10.0 || gb == 0.0) {
        String.format(Locale.US, "%.0f", gb)
    } else {
        String.format(Locale.US, "%.1f", gb)
    }
}
