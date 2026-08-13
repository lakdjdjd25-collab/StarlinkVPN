package org.quickping.app.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import org.quickping.app.R
import org.quickping.app.BuildConfig
import org.quickping.app.core.design.QuickPingColors
import org.quickping.app.core.design.Unbounded
import org.quickping.app.core.design.quickText
import org.quickping.app.model.AppRelease
import org.quickping.app.model.NotificationItem
import org.quickping.app.model.Service
import org.quickping.app.ui.components.GlassCard
import org.quickping.app.ui.components.DashedDivider
import org.quickping.app.ui.components.PrimaryButton
import org.quickping.app.ui.components.QuickPingScreen
import org.quickping.app.ui.components.QuickPingTopBar

@Composable
fun NotificationsScreen(notifications: List<NotificationItem>, onBack: () -> Unit) {
    QuickPingScreen {
        QuickPingTopBar(title = quickText("اعلان‌ها", "Notifications"), onBack = onBack)
        if (notifications.isEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Image(
                    painterResource(R.drawable.header_no_notification),
                    contentDescription = null,
                    modifier = Modifier.size(140.dp),
                )
                Spacer(Modifier.height(18.dp))
                Text(
                    quickText("اعلانی وجود ندارد", "No notifications"),
                    color = QuickPingColors.TextPrimary,
                    style = MaterialTheme.typography.titleMedium,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    quickText(
                        "وقتی خبر یا تغییر مهمی وجود داشته باشد، اینجا نمایش داده می‌شود.",
                        "Important news and changes will appear here.",
                    ),
                    color = QuickPingColors.TextMuted,
                    style = MaterialTheme.typography.bodySmall,
                    textAlign = TextAlign.Center,
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(notifications.size) { index ->
                    val item = notifications[index]
                    GlassCard(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(14.dp)) {
                            Text(item.title, color = QuickPingColors.TextPrimary)
                            Spacer(Modifier.height(5.dp))
                            Text(
                                item.body,
                                color = QuickPingColors.TextSecondary,
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun VersionScreen(release: AppRelease?, onBack: () -> Unit) {
    val context = LocalContext.current
    val updateAvailable = release != null && release.versionCode > BuildConfig.VERSION_CODE
    QuickPingScreen {
        QuickPingTopBar(title = quickText("نسخه", "Version"), onBack = onBack)
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Image(
                painterResource(R.drawable.update_last_version_ovals),
                contentDescription = null,
                modifier = Modifier.size(300.dp),
            )
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    if (updateAvailable) release!!.versionName else BuildConfig.VERSION_NAME.substringBefore('-'),
                    color = QuickPingColors.Background,
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontFamily = Unbounded,
                        fontWeight = FontWeight.Normal,
                    ),
                    modifier = Modifier
                        .background(QuickPingColors.TextPrimary, CircleShape)
                        .padding(horizontal = 24.dp, vertical = 8.dp),
                )
                Spacer(Modifier.height(56.dp))
                Text(
                    if (updateAvailable) {
                        quickText("نسخهٔ جدید QuickPing آماده است", "A new QuickPing version is available")
                    } else {
                        quickText("تبریک! QuickPing شما به‌روز است", "QuickPing is up to date")
                    },
                    color = QuickPingColors.TextPrimary,
                    style = MaterialTheme.typography.titleMedium,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    if (updateAvailable) {
                        release!!.changelog.ifBlank { quickText("بهبود پایداری و امنیت اتصال", "Connection stability and security improvements") }
                    } else {
                        quickText("آماده‌اید برای اتصال سریع و پایدار!", "Ready for a fast and stable connection!")
                    },
                    modifier = Modifier.padding(horizontal = 30.dp),
                    color = QuickPingColors.TextMuted,
                    style = MaterialTheme.typography.bodySmall,
                    textAlign = TextAlign.Center,
                )
                if (updateAvailable && release!!.downloadUrl.startsWith("https://")) {
                    Spacer(Modifier.height(20.dp))
                    PrimaryButton(
                        text = quickText("دریافت نسخهٔ جدید", "Download update"),
                        onClick = {
                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(release.downloadUrl)))
                        },
                        modifier = Modifier.padding(horizontal = 46.dp),
                    )
                }
            }
        }
    }
}

@Composable
fun ServicesScreen(service: Service, onBack: () -> Unit) {
    QuickPingScreen {
        QuickPingTopBar(title = quickText("سرویس‌ها", "Services"), onBack = onBack)
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 12.dp),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, QuickPingColors.TextSecondary, RoundedCornerShape(22.dp)),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().height(48.dp).padding(horizontal = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier.size(20.dp).border(2.dp, QuickPingColors.TextSecondary, CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        Box(Modifier.size(12.dp).background(QuickPingColors.Primary, CircleShape))
                    }
                    Spacer(Modifier.width(8.dp))
                    Text(service.license, color = QuickPingColors.TextPrimary, style = MaterialTheme.typography.labelLarge)
                    Spacer(Modifier.weight(1f))
                    Text(
                        if (service.isFree) quickText("رایگان", "Free") else service.plan,
                        color = QuickPingColors.Success,
                        style = MaterialTheme.typography.labelMedium,
                    )
                }
                DashedDivider()
                Row(
                    modifier = Modifier.fillMaxWidth().padding(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    ServiceMetric(
                        Modifier.weight(1f),
                        "${bytesToGb(service.usedBytes)}GB از ${bytesToGb(service.totalBytes)}GB",
                        quickText("دادهٔ استفاده‌شده", "Data used"),
                    )
                    ServiceMetric(
                        Modifier.weight(1f),
                        "${service.daysLeft} ${quickText("روز", "days")}",
                        quickText("اعتبار", "Validity"),
                    )
                }
            }
        }
    }
}

@Composable
private fun ServiceMetric(modifier: Modifier, value: String, label: String) {
    Column(
        modifier = modifier
            .height(48.dp)
            .background(QuickPingColors.BackgroundRaised, androidx.compose.foundation.shape.RoundedCornerShape(12.dp)),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(value, color = QuickPingColors.TextPrimary, style = MaterialTheme.typography.labelMedium)
        Text(label, color = QuickPingColors.TextMuted, style = MaterialTheme.typography.labelSmall)
    }
}

private fun bytesToGb(bytes: Long): String = "%.1f".format(bytes / 1024.0 / 1024.0 / 1024.0)
