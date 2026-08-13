package org.quickping.app.ui.screens

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt
import org.quickping.app.R
import org.quickping.app.core.design.QuickPingColors
import org.quickping.app.core.design.quickText
import org.quickping.app.model.Service
import org.quickping.app.model.UserInfo
import org.quickping.app.ui.components.PrimaryButton
import org.quickping.app.ui.components.QuickPingScreen
import org.quickping.app.ui.components.QuickPingTopBar
import org.quickping.app.ui.components.SettingRow
import org.quickping.app.ui.components.StatusPill

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountScreen(
    user: UserInfo,
    service: Service,
    busy: Boolean,
    error: String?,
    passwordChallengeId: String?,
    passwordDebugCode: String?,
    onRequestPasswordCode: () -> Unit,
    onConfirmPasswordChange: (String, String) -> Unit,
    onDeleteAccount: (String) -> Unit,
    onClearAction: () -> Unit,
    onBack: () -> Unit,
    onSignOut: () -> Unit,
    onServices: () -> Unit,
) {
    var showPasswordSheet by remember { mutableStateOf(false) }
    var showDeleteSheet by remember { mutableStateOf(false) }
    var passwordCode by remember { mutableStateOf("") }
    var newPassword by remember { mutableStateOf("") }
    var deletionPassword by remember { mutableStateOf("") }

    QuickPingScreen {
        QuickPingTopBar(
            title = quickText("حساب کاربری", "Account"),
            onBack = onBack,
            action = {
                Icon(
                    painterResource(R.drawable.ic_logout),
                    contentDescription = quickText("خروج", "Sign out"),
                    tint = QuickPingColors.TextSecondary,
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(QuickPingColors.Surface)
                        .clickable(onClick = onSignOut)
                        .padding(9.dp),
                )
            },
        )
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 14.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            ProfileCard(
                user = user,
                onChangePassword = {
                    onClearAction()
                    passwordCode = ""
                    newPassword = ""
                    showPasswordSheet = true
                },
                onDelete = {
                    onClearAction()
                    deletionPassword = ""
                    showDeleteSheet = true
                },
            )
            ReferenceServiceCard(service = service, onServices = onServices)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(24.dp))
                    .background(Color(0xFF09292D))
                    .padding(horizontal = 18.dp, vertical = 16.dp),
            ) {
                Text(
                    quickText(
                        "استفاده از سرورهای نامحدود تنها یک‌دهم حجم واقعی دادهٔ شما را مصرف می‌کند.",
                        "Unlimited servers count only one tenth of your actual data usage.",
                    ),
                    modifier = Modifier.fillMaxWidth(),
                    color = Color(0xFF59BFD8),
                    style = MaterialTheme.typography.bodySmall,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(7.dp))
                Text(
                    quickText(
                        "حساب و سرویس‌ها از طریق ارتباط رمزگذاری‌شده همگام می‌شوند.",
                        "Your account and services are synchronized over an encrypted connection.",
                    ),
                    modifier = Modifier.fillMaxWidth(),
                    color = QuickPingColors.TextSecondary,
                    style = MaterialTheme.typography.labelSmall,
                    textAlign = TextAlign.Center,
                )
            }
            Spacer(Modifier.height(22.dp))
        }
    }

    if (showPasswordSheet) {
        ModalBottomSheet(
            onDismissRequest = {
                if (!busy) {
                    showPasswordSheet = false
                    onClearAction()
                }
            },
            dragHandle = null,
            containerColor = QuickPingColors.SurfaceHigh,
            contentColor = QuickPingColors.TextPrimary,
            shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        ) {
            AccountSheetHeader(
                title = quickText("تغییر گذرواژه", "Change password"),
                icon = R.drawable.ic_change_password,
                iconColor = Color(0xFF4D84CA),
                onClose = {
                    if (!busy) {
                        showPasswordSheet = false
                        onClearAction()
                    }
                },
            )
            Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
                OutlinedTextField(
                    value = user.email,
                    onValueChange = {},
                    modifier = Modifier.fillMaxWidth(),
                    readOnly = true,
                    singleLine = true,
                    leadingIcon = { Icon(painterResource(R.drawable.ic_mail), null) },
                    shape = RoundedCornerShape(16.dp),
                    colors = accountFieldColors(),
                )
                Spacer(Modifier.height(12.dp))
                if (passwordChallengeId == null) {
                    Text(
                        quickText(
                            "ما یک کد تأیید به ایمیلی که وارد کرده‌اید خواهیم فرستاد.",
                            "We will send a verification code to your account email.",
                        ),
                        modifier = Modifier.fillMaxWidth(),
                        color = QuickPingColors.TextSecondary,
                        style = MaterialTheme.typography.bodySmall,
                        textAlign = TextAlign.Center,
                    )
                } else {
                    OutlinedTextField(
                        value = passwordCode,
                        onValueChange = { passwordCode = it.filter(Char::isDigit).take(6) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        label = { Text(quickText("کد اعتبارسنجی", "Verification code")) },
                        leadingIcon = { Icon(painterResource(R.drawable.ic_otp), null) },
                        shape = RoundedCornerShape(16.dp),
                        colors = accountFieldColors(),
                    )
                    Spacer(Modifier.height(10.dp))
                    OutlinedTextField(
                        value = newPassword,
                        onValueChange = { newPassword = it.take(72) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        label = { Text(quickText("گذرواژهٔ جدید", "New password")) },
                        leadingIcon = { Icon(painterResource(R.drawable.ic_key), null) },
                        visualTransformation = PasswordVisualTransformation(),
                        shape = RoundedCornerShape(16.dp),
                        colors = accountFieldColors(),
                    )
                }
                passwordDebugCode?.let { code ->
                    Spacer(Modifier.height(8.dp))
                    Text("Debug code: $code", color = QuickPingColors.Warning, style = MaterialTheme.typography.labelSmall)
                }
                error?.let { message ->
                    Spacer(Modifier.height(8.dp))
                    Text(message, color = QuickPingColors.Danger, style = MaterialTheme.typography.bodySmall)
                }
                Spacer(Modifier.height(28.dp))
                PrimaryButton(
                    text = when {
                        busy -> quickText("لطفاً صبر کنید…", "Please wait…")
                        passwordChallengeId == null -> quickText("فرستادن کد", "Send code")
                        else -> quickText("تأیید", "Confirm")
                    },
                    onClick = {
                        if (passwordChallengeId == null) onRequestPasswordCode()
                        else onConfirmPasswordChange(passwordCode, newPassword)
                    },
                    enabled = !busy && (passwordChallengeId == null || passwordCode.length == 6 && newPassword.length >= 8),
                )
                Spacer(Modifier.height(10.dp))
                SheetCancelButton {
                    showPasswordSheet = false
                    onClearAction()
                }
                Spacer(Modifier.height(30.dp))
            }
        }
    }

    if (showDeleteSheet) {
        ModalBottomSheet(
            onDismissRequest = {
                if (!busy) {
                    showDeleteSheet = false
                    onClearAction()
                }
            },
            dragHandle = null,
            containerColor = QuickPingColors.SurfaceHigh,
            contentColor = QuickPingColors.TextPrimary,
            shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        ) {
            AccountSheetHeader(
                title = quickText("حذف حساب کاربری", "Delete account"),
                icon = R.drawable.ic_trash,
                iconColor = QuickPingColors.Danger,
                onClose = {
                    if (!busy) {
                        showDeleteSheet = false
                        onClearAction()
                    }
                },
            )
            Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
                Text(
                    quickText(
                        "تمام داده‌های شما و همچنین سرویس‌های رایگان و خریداری‌شده حذف خواهند شد. این کار برگشت‌ناپذیر است.",
                        "All account data and all free or purchased services will be permanently deleted. This cannot be undone.",
                    ),
                    modifier = Modifier.fillMaxWidth(),
                    color = QuickPingColors.TextSecondary,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(18.dp))
                OutlinedTextField(
                    value = deletionPassword,
                    onValueChange = { deletionPassword = it.take(256) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text(quickText("گذرواژهٔ خود را وارد کنید", "Enter your password")) },
                    leadingIcon = { Icon(painterResource(R.drawable.ic_key), null) },
                    visualTransformation = PasswordVisualTransformation(),
                    shape = RoundedCornerShape(16.dp),
                    colors = accountFieldColors(),
                )
                error?.let { message ->
                    Spacer(Modifier.height(8.dp))
                    Text(message, color = QuickPingColors.Danger, style = MaterialTheme.typography.bodySmall)
                }
                Spacer(Modifier.height(28.dp))
                PrimaryButton(
                    text = if (busy) quickText("در حال حذف…", "Deleting…") else quickText("حذف حساب کاربری", "Delete account"),
                    onClick = { onDeleteAccount(deletionPassword) },
                    danger = true,
                    enabled = deletionPassword.isNotBlank() && !busy,
                )
                Spacer(Modifier.height(10.dp))
                SheetCancelButton {
                    showDeleteSheet = false
                    onClearAction()
                }
                Spacer(Modifier.height(30.dp))
            }
        }
    }
}

@Composable
private fun ProfileCard(
    user: UserInfo,
    onChangePassword: () -> Unit,
    onDelete: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(23.dp))
            .background(QuickPingColors.Surface.copy(alpha = 0.92f)),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 18.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier.size(70.dp).clip(RoundedCornerShape(20.dp)).background(Color(0xFF284D85)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(painterResource(R.drawable.ic_avatar), null, tint = Color(0xFF94BCFF), modifier = Modifier.size(46.dp))
            }
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    user.email,
                    color = QuickPingColors.TextPrimary,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(7.dp))
                StatusPill(
                    text = if (user.emailVerified) quickText("تأیید شده", "Verified") else quickText("تأیید نشده", "Unverified"),
                    color = if (user.emailVerified) QuickPingColors.Success else QuickPingColors.Warning,
                )
            }
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(QuickPingColors.Border))
        Row(Modifier.fillMaxWidth().padding(14.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            AccountAction(Modifier.weight(1f), quickText("حذف حساب کاربری", "Delete account"), R.drawable.ic_trash, true, onDelete)
            AccountAction(Modifier.weight(1f), quickText("تغییر گذرواژه", "Change password"), R.drawable.ic_key, false, onChangePassword)
        }
    }
}

@Composable
private fun ReferenceServiceCard(service: Service, onServices: () -> Unit) {
    val remainingPercent = ((1f - service.usedFraction.coerceIn(0f, 1f)) * 100).roundToInt()
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(23.dp))
            .background(QuickPingColors.Surface.copy(alpha = 0.92f)),
    ) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 17.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                StatusPill(service.plan, QuickPingColors.TextSecondary)
                Spacer(Modifier.weight(1f))
                Column(horizontalAlignment = Alignment.End) {
                    Text(quickText("سرویس فعلی", "Current service"), color = QuickPingColors.TextPrimary, style = MaterialTheme.typography.titleMedium)
                    Text(service.license, color = QuickPingColors.TextMuted, style = MaterialTheme.typography.bodySmall)
                }
            }
            Spacer(Modifier.height(8.dp))
            Text("$remainingPercent٪", modifier = Modifier.fillMaxWidth(), color = QuickPingColors.TextPrimary, style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold, textAlign = TextAlign.End)
            Spacer(Modifier.height(14.dp))
            SegmentedQuota(progress = 1f - service.usedFraction.coerceIn(0f, 1f))
            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                MetricBox(Modifier.weight(1f), "${bytesToGb(service.usedBytes)}GB از ${bytesToGb(service.totalBytes)}GB", quickText("دادهٔ استفاده‌شده", "Data used"))
                MetricBox(Modifier.weight(1f), "${bytesToGb(service.remainingBytes)}GB از ${bytesToGb(service.totalBytes)}GB", quickText("دادهٔ باقی‌مانده", "Data remaining"))
            }
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                MetricBox(Modifier.weight(1f), "${service.daysLeft} ${quickText("روز", "days")}", quickText("اعتبار", "Validity"))
                MetricBox(Modifier.weight(1f), "${service.usersCount} ${quickText("کاربر", "users")}", quickText("کاربران مجاز", "Allowed users"))
            }
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(QuickPingColors.Border))
        SettingRow(
            title = quickText("نمایش همه سرویس‌ها", "Show all services"),
            icon = R.drawable.ic_ticket,
            onClick = onServices,
        )
        SettingRow(
            title = quickText("گزینه‌های بیشتر", "More options"),
            icon = R.drawable.ic_open,
        )
    }
}

@Composable
private fun SegmentedQuota(progress: Float) {
    val filled = (progress.coerceIn(0f, 1f) * 18).roundToInt()
    Row(Modifier.fillMaxWidth().height(60.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        repeat(18) { index ->
            Box(
                Modifier
                    .weight(1f)
                    .height(60.dp)
                    .background(
                        if (index < filled) Color(0xFFC4CCDD) else Color(0xFF555D69),
                        RoundedCornerShape(if (index == 0 || index == 17) 14.dp else 2.dp),
                    ),
            )
        }
    }
}

@Composable
private fun MetricBox(modifier: Modifier, value: String, label: String) {
    Column(
        modifier = modifier.height(68.dp).border(1.dp, QuickPingColors.Border, RoundedCornerShape(18.dp)).padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(value, color = QuickPingColors.TextPrimary, style = MaterialTheme.typography.labelMedium, textAlign = TextAlign.Center)
        Text(label, color = QuickPingColors.TextMuted, style = MaterialTheme.typography.labelSmall, textAlign = TextAlign.Center)
    }
}

@Composable
private fun AccountAction(
    modifier: Modifier,
    text: String,
    icon: Int,
    danger: Boolean,
    onClick: () -> Unit,
) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier.height(54.dp),
        shape = RoundedCornerShape(16.dp),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = if (danger) QuickPingColors.Danger else QuickPingColors.TextPrimary),
        border = BorderStroke(1.dp, QuickPingColors.Border),
    ) {
        Icon(painterResource(icon), null, modifier = Modifier.size(17.dp))
        Spacer(Modifier.width(7.dp))
        Text(text, style = MaterialTheme.typography.labelSmall, maxLines = 1)
    }
}

@Composable
private fun AccountSheetHeader(title: String, icon: Int, iconColor: Color, onClose: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(top = 20.dp, bottom = 22.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onClose) { Text("×", color = QuickPingColors.TextPrimary, style = MaterialTheme.typography.titleLarge) }
            Spacer(Modifier.weight(1f))
        }
        Box(Modifier.size(64.dp).clip(CircleShape).background(iconColor), contentAlignment = Alignment.Center) {
            Icon(painterResource(icon), null, tint = Color.White, modifier = Modifier.size(31.dp))
        }
        Spacer(Modifier.height(9.dp))
        Text(title, color = QuickPingColors.TextPrimary, style = MaterialTheme.typography.titleLarge)
    }
}

@Composable
private fun SheetCancelButton(onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().height(50.dp),
        shape = RoundedCornerShape(14.dp),
        border = null,
        colors = ButtonDefaults.outlinedButtonColors(containerColor = QuickPingColors.Surface, contentColor = QuickPingColors.TextPrimary),
    ) { Text(quickText("انصراف", "Cancel")) }
}

@Composable
private fun accountFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = QuickPingColors.Primary,
    unfocusedBorderColor = QuickPingColors.Border,
    focusedTextColor = QuickPingColors.TextPrimary,
    unfocusedTextColor = QuickPingColors.TextPrimary,
    focusedLabelColor = QuickPingColors.TextSecondary,
    unfocusedLabelColor = QuickPingColors.TextMuted,
    focusedLeadingIconColor = QuickPingColors.TextSecondary,
    unfocusedLeadingIconColor = QuickPingColors.TextSecondary,
)

private fun bytesToGb(bytes: Long): String = "%.1f".format(bytes / 1024.0 / 1024.0 / 1024.0)
