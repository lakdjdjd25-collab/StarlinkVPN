package org.quickping.app.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import org.quickping.app.R
import org.quickping.app.core.design.QuickPingColors
import org.quickping.app.ui.components.StatusPill

private val supportedLanguages = listOf(
    "English",
    "Nederlands",
    "فارسی",
    "العربية",
    "Türkçe",
    "Русский",
    "हिन्दी",
    "汉语",
    "اُردُو",
)

@Composable
private fun LoginEmailBar(
    email: String,
    onEmailChange: (String) -> Unit,
    enabled: Boolean,
    onSubmit: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
            .background(QuickPingColors.Surface.copy(alpha = 0.95f), RoundedCornerShape(17.dp))
            .border(1.dp, QuickPingColors.BorderSoft, RoundedCornerShape(17.dp))
            .padding(horizontal = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            painterResource(R.drawable.ic_mail),
            contentDescription = null,
            tint = QuickPingColors.TextSecondary,
            modifier = Modifier
                .padding(horizontal = 9.dp)
                .size(20.dp),
        )
        Box(
            modifier = Modifier.weight(1f),
            contentAlignment = Alignment.CenterStart,
        ) {
            if (email.isEmpty()) {
                Text(
                    "ایمیل خود را وارد کنید",
                    color = QuickPingColors.TextMuted,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            BasicTextField(
                value = email,
                onValueChange = onEmailChange,
                modifier = Modifier.fillMaxWidth(),
                enabled = enabled,
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                textStyle = MaterialTheme.typography.bodyMedium.merge(
                    TextStyle(color = QuickPingColors.TextPrimary, textAlign = TextAlign.Start),
                ),
                cursorBrush = SolidColor(QuickPingColors.Primary),
            )
        }
        Box(
            modifier = Modifier
                .size(38.dp)
                .background(QuickPingColors.SurfacePressed, RoundedCornerShape(11.dp))
                .clickable(enabled = enabled && email.contains("@"), onClick = onSubmit),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painterResource(R.drawable.ic_arrow_back),
                contentDescription = "ادامه",
                tint = if (email.contains("@")) QuickPingColors.TextPrimary else QuickPingColors.TextMuted,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

@Composable
private fun OrDivider() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Canvas(modifier = Modifier.weight(1f).height(1.dp)) {
            drawLine(QuickPingColors.Border, start = androidx.compose.ui.geometry.Offset.Zero, end = androidx.compose.ui.geometry.Offset(size.width, 0f))
        }
        Text(
            "یا",
            modifier = Modifier.padding(horizontal = 9.dp),
            color = QuickPingColors.TextMuted,
            style = MaterialTheme.typography.labelSmall,
        )
        Canvas(modifier = Modifier.weight(1f).height(1.dp)) {
            drawLine(QuickPingColors.Border, start = androidx.compose.ui.geometry.Offset.Zero, end = androidx.compose.ui.geometry.Offset(size.width, 0f))
        }
    }
}

@Composable
private fun LoginSquareButton(
    icon: Int,
    contentDescription: String,
    onClick: () -> Unit,
    iconTint: Color = QuickPingColors.TextPrimary,
) {
    Box(
        modifier = Modifier
            .size(66.dp)
            .background(QuickPingColors.Background.copy(alpha = 0.75f), RoundedCornerShape(17.dp))
            .border(1.dp, QuickPingColors.Border, RoundedCornerShape(17.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painterResource(icon),
            contentDescription = contentDescription,
            tint = iconTint,
            modifier = Modifier.size(25.dp),
        )
    }
}

@Composable
private fun BusyLoginDialog(onCancel: () -> Unit) {
    Dialog(onDismissRequest = {}) {
        Column(
            modifier = Modifier
                .width(278.dp)
                .background(QuickPingColors.SurfaceHigh, RoundedCornerShape(24.dp))
                .border(1.dp, QuickPingColors.TextMuted, RoundedCornerShape(24.dp))
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                modifier = Modifier
                    .size(54.dp)
                    .background(Color(0xFF5D646D), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(24.dp),
                    color = QuickPingColors.TextPrimary,
                    strokeWidth = 2.dp,
                )
            }
            Spacer(Modifier.height(14.dp))
            Text(
                "در حال دریافت سرویس‌های شما...",
                color = QuickPingColors.TextPrimary,
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.titleMedium,
            )
            Spacer(Modifier.height(20.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(42.dp)
                    .background(QuickPingColors.SurfacePressed, RoundedCornerShape(13.dp))
                    .clickable(onClick = onCancel),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    "انصراف",
                    color = QuickPingColors.TextSecondary,
                    style = MaterialTheme.typography.labelMedium,
                )
            }
        }
    }
}

@Composable
fun LoginScreen(
    busy: Boolean,
    challengeId: String?,
    debugCode: String?,
    error: String?,
    onRequestEmail: (String) -> Unit,
    onVerifyCode: (String) -> Unit,
    onCancelChallenge: () -> Unit,
    onGoogleRequested: () -> Unit,
) {
    var language by remember { mutableStateOf("فارسی") }
    var showLanguages by remember { mutableStateOf(false) }
    var email by remember { mutableStateOf("") }
    var verificationCode by remember { mutableStateOf("") }

    LaunchedEffect(debugCode) {
        if (!debugCode.isNullOrBlank()) verificationCode = debugCode
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(QuickPingColors.Background),
    ) {
        Image(
            painter = painterResource(R.drawable.bg_login),
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.FillBounds,
        )
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .imePadding()
                .padding(horizontal = 18.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                horizontalArrangement = Arrangement.Absolute.Right,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                StatusPill(
                    text = language,
                    color = QuickPingColors.TextSecondary,
                    modifier = Modifier.clickable { showLanguages = true },
                )
            }
            Spacer(Modifier.height(58.dp))
            Image(
                painter = painterResource(R.drawable.ic_logo_welcome),
                contentDescription = "QuickPing",
                modifier = Modifier.size(172.dp),
            )
            Text(
                text = "خوش‌آمدید!",
                color = QuickPingColors.TextPrimary,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.weight(1f))
            LoginEmailBar(
                email = email,
                onEmailChange = { email = it.trim() },
                enabled = !busy,
                onSubmit = { if (email.contains("@")) onRequestEmail(email) },
            )
            if (error != null && challengeId == null) {
                Spacer(Modifier.height(6.dp))
                Text(
                    text = error,
                    color = QuickPingColors.Danger,
                    style = MaterialTheme.typography.bodySmall,
                    textAlign = TextAlign.Center,
                )
            }
            Spacer(Modifier.height(10.dp))
            OrDivider()
            Spacer(Modifier.height(10.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                LoginSquareButton(
                    icon = R.drawable.ic_password_biometric,
                    contentDescription = "ورود با گذرواژه ذخیره‌شده",
                    onClick = onGoogleRequested,
                )
                Box {
                    LoginSquareButton(
                        icon = R.drawable.logo_google,
                        contentDescription = "ورود با گوگل",
                        onClick = onGoogleRequested,
                        iconTint = Color.Unspecified,
                    )
                    Row(
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .padding(top = 1.dp)
                            .background(Color(0xFFE8E9EC), RoundedCornerShape(8.dp))
                            .padding(horizontal = 5.dp, vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            "10GB",
                            color = Color(0xFF282B31),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                        )
                        Spacer(Modifier.width(2.dp))
                        Icon(
                            painterResource(R.drawable.ic_gift),
                            contentDescription = null,
                            tint = Color(0xFF282B31),
                            modifier = Modifier.size(11.dp),
                        )
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
            Row(
                modifier = Modifier.clickable(onClick = onGoogleRequested),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    painterResource(R.drawable.ic_help_hexagon),
                    contentDescription = null,
                    tint = QuickPingColors.TextSecondary,
                    modifier = Modifier.size(15.dp),
                )
                Spacer(Modifier.width(5.dp))
                Text(
                    "به کمک نیاز دارید؟",
                    color = QuickPingColors.TextSecondary,
                    style = MaterialTheme.typography.labelSmall,
                )
            }
            Spacer(Modifier.height(12.dp))
            Text(
                text = "با ادامه دادن، شما با شرایط سرویس‌ها و سیاست حفظ حریم خصوصی موافقت می‌کنید",
                modifier = Modifier.padding(horizontal = 12.dp),
                color = QuickPingColors.TextMuted,
                style = MaterialTheme.typography.bodySmall,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(12.dp))
        }
    }

    if (showLanguages) {
        Dialog(
            onDismissRequest = { showLanguages = false },
            properties = DialogProperties(
                usePlatformDefaultWidth = false,
                decorFitsSystemWindows = false,
            ),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(QuickPingColors.Scrim)
                    .clickable { showLanguages = false },
                contentAlignment = Alignment.BottomCenter,
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            QuickPingColors.SurfaceHigh,
                            RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp),
                        )
                        .clickable { }
                        .navigationBarsPadding()
                        .padding(horizontal = 12.dp, vertical = 12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Absolute.Left,
                    ) {
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .background(QuickPingColors.BackgroundRaised, CircleShape)
                                .clickable { showLanguages = false },
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                painterResource(R.drawable.ic_clear),
                                contentDescription = "بستن",
                                tint = QuickPingColors.TextSecondary,
                                modifier = Modifier.size(15.dp),
                            )
                        }
                    }
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .background(QuickPingColors.Primary.copy(alpha = 0.25f), CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            painterResource(R.drawable.ic_language),
                            contentDescription = null,
                            tint = QuickPingColors.PrimaryLight,
                            modifier = Modifier.size(23.dp),
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "انتخاب زبان برنامه",
                        color = QuickPingColors.TextPrimary,
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Spacer(Modifier.height(12.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        supportedLanguages.forEach { item ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(44.dp)
                                    .background(QuickPingColors.Surface, RoundedCornerShape(9.dp))
                                    .border(
                                        1.dp,
                                        if (language == item) QuickPingColors.TextMuted else QuickPingColors.BorderSoft,
                                        RoundedCornerShape(9.dp),
                                    )
                                    .clickable {
                                        language = item
                                        showLanguages = false
                                    }
                                    .padding(horizontal = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    item,
                                    modifier = Modifier.weight(1f),
                                    color = QuickPingColors.TextPrimary,
                                    style = MaterialTheme.typography.bodySmall,
                                    textAlign = TextAlign.End,
                                )
                                RadioButton(
                                    selected = language == item,
                                    onClick = null,
                                    colors = RadioButtonDefaults.colors(
                                        selectedColor = QuickPingColors.Primary,
                                        unselectedColor = QuickPingColors.TextMuted,
                                    ),
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (busy) {
        BusyLoginDialog(onCancel = onCancelChallenge)
    } else if (challengeId != null) {
        AlertDialog(
            onDismissRequest = onCancelChallenge,
            containerColor = QuickPingColors.SurfaceHigh,
            shape = RoundedCornerShape(22.dp),
            title = {
                Text(
                    "کد تأیید ایمیل",
                    modifier = Modifier.fillMaxWidth(),
                    color = QuickPingColors.TextPrimary,
                    textAlign = TextAlign.Center,
                )
            },
            text = {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        "کد شش‌رقمی ارسال‌شده به ایمیل را وارد کنید.",
                        color = QuickPingColors.TextSecondary,
                        style = MaterialTheme.typography.bodySmall,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(14.dp))
                    OutlinedTextField(
                        value = verificationCode,
                        onValueChange = { value ->
                            verificationCode = value.filter(Char::isDigit).take(6)
                        },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        shape = RoundedCornerShape(13.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = QuickPingColors.Primary,
                            unfocusedBorderColor = QuickPingColors.Border,
                            focusedTextColor = QuickPingColors.TextPrimary,
                            unfocusedTextColor = QuickPingColors.TextPrimary,
                        ),
                    )
                    if (error != null) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            error,
                            color = QuickPingColors.Danger,
                            style = MaterialTheme.typography.bodySmall,
                            textAlign = TextAlign.Center,
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = { onVerifyCode(verificationCode) },
                    enabled = verificationCode.length == 6,
                ) {
                    Text("تأیید و ورود", color = QuickPingColors.PrimaryLight)
                }
            },
            dismissButton = {
                TextButton(onClick = onCancelChallenge) {
                    Text("انصراف", color = QuickPingColors.TextSecondary)
                }
            },
        )
    }
}
