package org.quickping.app.ui.screens

import androidx.compose.foundation.background
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import org.quickping.app.R
import org.quickping.app.core.design.QuickPingColors
import org.quickping.app.model.Service
import org.quickping.app.model.UserInfo
import org.quickping.app.ui.components.GlassCard
import org.quickping.app.ui.components.PrimaryButton
import org.quickping.app.ui.components.QuickPingScreen
import org.quickping.app.ui.components.QuickPingTopBar
import org.quickping.app.ui.components.SettingRow
import org.quickping.app.ui.components.StatusPill

@Composable
fun AccountScreen(
    user: UserInfo,
    service: Service,
    onBack: () -> Unit,
    onServices: () -> Unit,
) {
    var showEmailDialog by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var newEmail by remember { mutableStateOf(user.email) }

    QuickPingScreen {
        QuickPingTopBar(title = "حساب کاربری", onBack = onBack)
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 12.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            GlassCard(Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier
                            .size(52.dp)
                            .background(QuickPingColors.Primary.copy(alpha = 0.25f), CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            painterResource(R.drawable.ic_avatar),
                            contentDescription = null,
                            tint = QuickPingColors.PrimaryLight,
                            modifier = Modifier.size(31.dp),
                        )
                    }
                    Spacer(Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            user.email,
                            color = QuickPingColors.TextPrimary,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                        )
                        Spacer(Modifier.height(5.dp))
                        StatusPill(
                            text = if (user.emailVerified) "تأییدشده" else "تأییدنشده",
                            color = if (user.emailVerified) QuickPingColors.Success else QuickPingColors.Warning,
                        )
                    }
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 10.dp, end = 10.dp, bottom = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    AccountAction(
                        modifier = Modifier.weight(1f),
                        text = "تغییر ایمیل",
                        icon = R.drawable.ic_edit,
                        onClick = { showEmailDialog = true },
                    )
                    AccountAction(
                        modifier = Modifier.weight(1f),
                        text = "حذف حساب",
                        icon = R.drawable.ic_trash,
                        danger = true,
                        onClick = { showDeleteDialog = true },
                    )
                }
            }
            ServiceSummaryCard(service)
            GlassCard(Modifier.fillMaxWidth()) {
                SettingRow(
                    title = "سرویس‌های من",
                    subtitle = "مدیریت سرویس‌های فعال",
                    icon = R.drawable.ic_package,
                    onClick = onServices,
                )
                SettingRow(
                    title = "مدیریت و تمدید",
                    subtitle = "افزایش حجم، کاربر یا زمان",
                    icon = R.drawable.ic_renew,
                )
            }
            Text(
                "اطلاعات حساب و سرویس‌ها با ارتباط رمزگذاری‌شده همگام می‌شوند.",
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                color = QuickPingColors.TextMuted,
                style = MaterialTheme.typography.labelSmall,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(20.dp))
        }
    }

    if (showEmailDialog) {
        AlertDialog(
            onDismissRequest = { showEmailDialog = false },
            containerColor = QuickPingColors.SurfaceHigh,
            shape = RoundedCornerShape(22.dp),
            icon = {
                Icon(
                    painterResource(R.drawable.ic_language),
                    contentDescription = null,
                    tint = QuickPingColors.PrimaryLight,
                    modifier = Modifier.size(30.dp),
                )
            },
            title = { Text("تغییر نشانی ایمیل", color = QuickPingColors.TextPrimary) },
            text = {
                Column {
                    Text(
                        "کد تأیید به ایمیل جدید ارسال خواهد شد.",
                        color = QuickPingColors.TextSecondary,
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = newEmail,
                        onValueChange = { newEmail = it },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = QuickPingColors.Primary,
                            unfocusedBorderColor = QuickPingColors.Border,
                            focusedTextColor = QuickPingColors.TextPrimary,
                            unfocusedTextColor = QuickPingColors.TextPrimary,
                        ),
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { showEmailDialog = false }) { Text("تأیید") }
            },
            dismissButton = {
                TextButton(onClick = { showEmailDialog = false }) { Text("انصراف") }
            },
        )
    }
    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            containerColor = QuickPingColors.SurfaceHigh,
            shape = RoundedCornerShape(22.dp),
            icon = {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .background(QuickPingColors.Danger.copy(alpha = 0.2f), CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        painterResource(R.drawable.ic_trash),
                        contentDescription = null,
                        tint = QuickPingColors.Danger,
                        modifier = Modifier.size(25.dp),
                    )
                }
            },
            title = { Text("حذف حساب کاربری", color = QuickPingColors.TextPrimary) },
            text = {
                Text(
                    "پس از حذف حساب، دسترسی به سرویس‌ها و اطلاعات حساب از بین می‌رود. این کار قابل بازگشت نیست.",
                    color = QuickPingColors.TextSecondary,
                    textAlign = TextAlign.Center,
                )
            },
            confirmButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text("حذف حساب کاربری", color = QuickPingColors.Danger)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) { Text("انصراف") }
            },
        )
    }
}

@Composable
private fun AccountAction(
    modifier: Modifier,
    text: String,
    icon: Int,
    danger: Boolean = false,
    onClick: () -> Unit,
) {
    androidx.compose.material3.OutlinedButton(
        onClick = onClick,
        modifier = modifier.height(44.dp),
        shape = RoundedCornerShape(12.dp),
        colors = androidx.compose.material3.ButtonDefaults.outlinedButtonColors(
            contentColor = if (danger) QuickPingColors.Danger else QuickPingColors.TextPrimary,
        ),
        border = androidx.compose.foundation.BorderStroke(1.dp, QuickPingColors.Border),
    ) {
        Icon(painterResource(icon), contentDescription = null, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(6.dp))
        Text(text, style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
private fun ServiceSummaryCard(service: Service) {
    GlassCard(Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        service.name,
                        color = QuickPingColors.TextPrimary,
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        service.license,
                        color = QuickPingColors.TextMuted,
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
                StatusPill(service.plan, QuickPingColors.PrimaryLight)
            }
            Spacer(Modifier.height(14.dp))
            LinearProgressIndicator(
                progress = { service.usedFraction },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(18.dp),
                color = QuickPingColors.PrimaryLight,
                trackColor = QuickPingColors.SurfacePressed,
            )
            Spacer(Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Metric("${bytesToGb(service.usedBytes)}GB", "مصرف‌شده")
                Metric("${bytesToGb(service.remainingBytes)}GB", "باقی‌مانده")
                Metric("${service.daysLeft} روز", "اعتبار")
                Metric("${service.usersCount} کاربر", "ظرفیت")
            }
        }
    }
}

@Composable
private fun Metric(value: String, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, color = QuickPingColors.TextPrimary, style = MaterialTheme.typography.labelMedium)
        Text(label, color = QuickPingColors.TextMuted, style = MaterialTheme.typography.labelSmall)
    }
}

private fun bytesToGb(bytes: Long): String = "%.1f".format(bytes / 1024.0 / 1024.0 / 1024.0)
