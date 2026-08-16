package org.quickping.app.ui.screens

import android.net.Uri
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.google.android.gms.mlkit.codescanner.GmsBarcodeScanning
import com.google.android.gms.mlkit.codescanner.GmsBarcodeScannerOptions
import com.google.mlkit.vision.barcode.common.Barcode
import org.quickping.app.R
import org.quickping.app.core.design.Peyda
import org.quickping.app.core.design.QuickPingColors
import org.quickping.app.core.design.quickText
import org.quickping.app.model.AppLanguage

private val supportedLanguages = AppLanguage.entries
private const val LOGIN_MOTION_MS = 280

@Composable
fun LoginScreen(
    language: AppLanguage,
    busy: Boolean,
    challengeId: String?,
    debugCode: String?,
    error: String?,
    onLanguageChange: (AppLanguage) -> Unit,
    onRequestEmailCode: (String) -> Unit,
    onPasswordLogin: (String, String) -> Unit,
    onVerifyCode: (String) -> Unit,
    onCancelChallenge: () -> Unit,
    onGoogleRequested: () -> Unit,
    onHelpRequested: () -> Unit,
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val keyboardVisible = WindowInsets.ime.getBottom(density) > 0

    var showLanguages by remember { mutableStateOf(false) }
    var showPasswordLogin by remember { mutableStateOf(false) }
    var license by remember { mutableStateOf("") }
    var passwordEmail by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var verificationCode by remember { mutableStateOf("") }
    var scanError by remember { mutableStateOf<String?>(null) }

    val logoWidth by animateDpAsState(
        if (keyboardVisible) 104.dp else 156.dp,
        tween(LOGIN_MOTION_MS),
        label = "loginLogoWidth",
    )
    val logoHeight by animateDpAsState(
        if (keyboardVisible) 70.dp else 105.dp,
        tween(LOGIN_MOTION_MS),
        label = "loginLogoHeight",
    )
    val heroTop by animateDpAsState(
        if (keyboardVisible) 2.dp else 50.dp,
        tween(LOGIN_MOTION_MS),
        label = "loginHeroTop",
    )
    val heroGap by animateDpAsState(
        if (keyboardVisible) 3.dp else 10.dp,
        tween(LOGIN_MOTION_MS),
        label = "loginHeroGap",
    )
    val lowerGap by animateDpAsState(
        if (keyboardVisible) 5.dp else 11.dp,
        tween(LOGIN_MOTION_MS),
        label = "loginLowerGap",
    )
    val cursorTransition = rememberInfiniteTransition(label = "welcomeCursor")
    val cursorAlpha by cursorTransition.animateFloat(
        initialValue = 1f,
        targetValue = 0.12f,
        animationSpec = infiniteRepeatable(
            animation = tween(560),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "welcomeCursorAlpha",
    )

    val scannerOptions = remember {
        GmsBarcodeScannerOptions.Builder()
            .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
            .enableAutoZoom()
            .build()
    }
    val scanner = remember(context, scannerOptions) { GmsBarcodeScanning.getClient(context, scannerOptions) }

    LaunchedEffect(debugCode) {
        if (!debugCode.isNullOrBlank()) verificationCode = debugCode
    }

    Box(Modifier.fillMaxSize().background(QuickPingColors.Background)) {
        Image(
            painter = painterResource(R.drawable.bg_login),
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.FillBounds,
        )
        Image(
            painter = painterResource(R.drawable.header_login),
            contentDescription = null,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .height(if (keyboardVisible) 270.dp else 390.dp),
            contentScale = ContentScale.FillWidth,
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
                modifier = Modifier.fillMaxWidth().height(48.dp),
                horizontalArrangement = Arrangement.Absolute.Right,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    modifier = Modifier
                        .background(Color(0xFF111318).copy(alpha = .92f), RoundedCornerShape(16.dp))
                        .border(1.dp, Color(0xFF22262D), RoundedCornerShape(16.dp))
                        .clickable { showLanguages = true }
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = language.label,
                        color = Color(0xFFB8BBC4),
                        fontFamily = Peyda,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                    )
                    Spacer(Modifier.width(5.dp))
                    Text("⌄", color = Color(0xFF8A8E98), fontSize = 13.sp)
                }
            }

            Spacer(Modifier.height(heroTop))
            Image(
                painter = painterResource(R.drawable.nimhub_logo),
                contentDescription = "NimHUB Vpn",
                modifier = Modifier.size(logoWidth, logoHeight),
                contentScale = ContentScale.Fit,
            )
            Spacer(Modifier.height(heroGap))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .width(2.dp)
                        .height(18.dp)
                        .alpha(cursorAlpha)
                        .background(Color(0xFF347BFF), CircleShape),
                )
                Spacer(Modifier.width(7.dp))
                Text(
                    text = quickText("خوش آمدید", "Welcome"),
                    color = Color(0xFFCDD0D7),
                    fontFamily = Peyda,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }

            Spacer(Modifier.weight(1f))
            LicenseEntryBar(
                value = license,
                enabled = !busy,
                onValueChange = {
                    license = it
                    scanError = null
                },
                onBack = {
                    if (license.isNotBlank()) license = ""
                    focusManager.clearFocus()
                    keyboardController?.hide()
                },
                onScan = {
                    scanner.startScan()
                        .addOnSuccessListener { barcode ->
                            val parsed = extractLicense(barcode.rawValue.orEmpty())
                            if (parsed.isNotBlank()) {
                                license = parsed
                                scanError = null
                            } else {
                                scanError = "کد مجوز در QR پیدا نشد"
                            }
                        }
                        .addOnCanceledListener { scanError = null }
                        .addOnFailureListener { scanError = "اسکن QR انجام نشد" }
                },
                onSubmit = {
                    val value = license.trim()
                    if (value.isNotBlank()) {
                        focusManager.clearFocus()
                        keyboardController?.hide()
                        onPasswordLogin(value, value)
                    }
                },
            )

            val visibleError = scanError ?: error?.takeIf { challengeId == null }
            if (!visibleError.isNullOrBlank()) {
                Spacer(Modifier.height(5.dp))
                Text(
                    visibleError,
                    color = QuickPingColors.Danger,
                    fontFamily = Peyda,
                    fontSize = 11.sp,
                    textAlign = TextAlign.Center,
                )
            }

            Spacer(Modifier.height(lowerGap))
            OrDivider()
            Spacer(Modifier.height(lowerGap))
            Row(
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box {
                    LoginSquareButton(
                        icon = R.drawable.logo_google,
                        contentDescription = quickText("ورود با گوگل", "Sign in with Google"),
                        onClick = onGoogleRequested,
                        iconTint = Color.Unspecified,
                    )
                    Row(
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .background(Color(0xFFE9EAED), RoundedCornerShape(8.dp))
                            .padding(horizontal = 5.dp, vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            "10GB",
                            color = Color(0xFF282B31),
                            fontSize = 9.sp,
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
                LoginSquareButton(
                    icon = R.drawable.ic_mail,
                    contentDescription = quickText("ورود با ایمیل", "Sign in with email"),
                    onClick = {
                        passwordEmail = ""
                        password = ""
                        showPasswordLogin = true
                    },
                )
            }

            Spacer(Modifier.height(if (keyboardVisible) 5.dp else 10.dp))
            Row(
                modifier = Modifier.clickable(onClick = onHelpRequested),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    painterResource(R.drawable.ic_help_hexagon),
                    contentDescription = null,
                    tint = Color(0xFF999DA7),
                    modifier = Modifier.size(15.dp),
                )
                Spacer(Modifier.width(5.dp))
                Text(
                    quickText("به کمک نیاز دارید؟", "Need help?"),
                    color = Color(0xFF999DA7),
                    fontFamily = Peyda,
                    fontSize = 11.sp,
                )
            }

            Spacer(Modifier.height(if (keyboardVisible) 5.dp else 12.dp))
            LoginTermsText()
            Spacer(Modifier.height(if (keyboardVisible) 4.dp else 12.dp))
        }
    }

    if (showLanguages) {
        LanguagePicker(
            current = language,
            onSelect = {
                onLanguageChange(it)
                showLanguages = false
            },
            onDismiss = { showLanguages = false },
        )
    }

    if (showPasswordLogin) {
        PasswordLoginDialog(
            email = passwordEmail,
            password = password,
            onEmailChange = { passwordEmail = it.trim() },
            onPasswordChange = { password = it },
            onDismiss = { showPasswordLogin = false },
            onSubmit = {
                val email = passwordEmail.trim()
                showPasswordLogin = false
                onPasswordLogin(email, password)
            },
        )
    }

    if (busy) {
        BusyLoginDialog(onCancelChallenge)
    } else if (challengeId != null) {
        VerificationDialog(
            value = verificationCode,
            error = error,
            onValueChange = { verificationCode = it.filter(Char::isDigit).take(6) },
            onVerify = { onVerifyCode(verificationCode) },
            onDismiss = onCancelChallenge,
        )
    }
}

@Composable
private fun LicenseEntryBar(
    value: String,
    enabled: Boolean,
    onValueChange: (String) -> Unit,
    onBack: () -> Unit,
    onScan: () -> Unit,
    onSubmit: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(54.dp)
            .background(Color(0xFF17191E), RoundedCornerShape(18.dp))
            .border(1.dp, Color(0xFF23262D), RoundedCornerShape(18.dp))
            .padding(horizontal = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        LoginInnerButton(R.drawable.ic_arrow_back, onBack)
        Spacer(Modifier.width(6.dp))
        LoginInnerButton(R.drawable.ic_scan, onScan)
        Box(
            modifier = Modifier.weight(1f).padding(horizontal = 10.dp),
            contentAlignment = Alignment.CenterEnd,
        ) {
            if (value.isEmpty()) {
                Text(
                    quickText("مجوز خود را وارد کنید", "Enter your license"),
                    modifier = Modifier.fillMaxWidth(),
                    color = Color(0xFF727680),
                    fontFamily = Peyda,
                    fontSize = 13.sp,
                    textAlign = TextAlign.End,
                )
            }
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                modifier = Modifier.fillMaxWidth(),
                enabled = enabled,
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
                textStyle = TextStyle(
                    color = QuickPingColors.TextPrimary,
                    fontFamily = Peyda,
                    fontSize = 13.sp,
                    textAlign = TextAlign.End,
                ),
                cursorBrush = SolidColor(Color(0xFF4A82FF)),
            )
        }
        LoginInnerButton(
            icon = R.drawable.ic_ticket,
            onClick = onSubmit,
            enabled = enabled && value.isNotBlank(),
        )
    }
}

@Composable
private fun LoginInnerButton(
    icon: Int,
    onClick: () -> Unit,
    enabled: Boolean = true,
) {
    Box(
        modifier = Modifier
            .size(40.dp)
            .background(Color(0xFF202329), RoundedCornerShape(12.dp))
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painterResource(icon),
            contentDescription = null,
            tint = if (enabled) Color(0xFFB9BDC6) else Color(0xFF666B75),
            modifier = Modifier.size(20.dp),
        )
    }
}

@Composable
private fun OrDivider() {
    Row(Modifier.fillMaxWidth().height(18.dp), verticalAlignment = Alignment.CenterVertically) {
        Canvas(Modifier.weight(1f).height(1.dp)) {
            drawLine(Color(0xFF292C33), androidx.compose.ui.geometry.Offset.Zero, androidx.compose.ui.geometry.Offset(size.width, 0f))
        }
        Text(
            quickText("یا", "or"),
            modifier = Modifier.padding(horizontal = 11.dp),
            color = Color(0xFF7B7F89),
            fontFamily = Peyda,
            fontSize = 11.sp,
        )
        Canvas(Modifier.weight(1f).height(1.dp)) {
            drawLine(Color(0xFF292C33), androidx.compose.ui.geometry.Offset.Zero, androidx.compose.ui.geometry.Offset(size.width, 0f))
        }
    }
}

@Composable
private fun LoginSquareButton(icon: Int, contentDescription: String, onClick: () -> Unit, iconTint: Color = QuickPingColors.TextPrimary) {
    Box(
        modifier = Modifier
            .size(72.dp)
            .background(Color(0xFF080A0D).copy(alpha = .9f), RoundedCornerShape(22.dp))
            .border(1.dp, Color(0xFF252830), RoundedCornerShape(22.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(painterResource(icon), contentDescription, tint = iconTint, modifier = Modifier.size(27.dp))
    }
}

@Composable
private fun LoginTermsText() {
    Text(
        text = buildAnnotatedString {
            append(quickText("با ادامه دادن، شما با ", "By continuing, you agree to the "))
            withStyle(SpanStyle(fontWeight = FontWeight.SemiBold, color = Color(0xFF969AA4))) {
                append(quickText("شرایط سرویس‌ها", "Terms of Service"))
            }
            append(quickText(" و ", " and "))
            withStyle(SpanStyle(fontWeight = FontWeight.SemiBold, color = Color(0xFF969AA4))) {
                append(quickText("سیاست حفظ حریم خصوصی", "Privacy Policy"))
            }
            append(quickText(" موافقت می‌کنید", ""))
        },
        modifier = Modifier.padding(horizontal = 10.dp),
        color = Color(0xFF70747E),
        fontFamily = Peyda,
        fontSize = 10.sp,
        lineHeight = 15.sp,
        textAlign = TextAlign.Center,
    )
}

@Composable
private fun LanguagePicker(current: AppLanguage, onSelect: (AppLanguage) -> Unit, onDismiss: () -> Unit) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        Box(
            Modifier.fillMaxSize().background(QuickPingColors.Scrim).clickable(onClick = onDismiss),
            contentAlignment = Alignment.BottomCenter,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(QuickPingColors.SurfaceHigh, RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp))
                    .clickable { }
                    .navigationBarsPadding()
                    .padding(12.dp),
            ) {
                Text(
                    quickText("انتخاب زبان برنامه", "Choose app language"),
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                    color = QuickPingColors.TextPrimary,
                    fontFamily = Peyda,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center,
                )
                supportedLanguages.forEach { item ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(46.dp)
                            .background(Color(0xFF101318), RoundedCornerShape(10.dp))
                            .clickable { onSelect(item) }
                            .padding(horizontal = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(item.label, modifier = Modifier.weight(1f), color = QuickPingColors.TextPrimary, fontFamily = Peyda, textAlign = TextAlign.End)
                        RadioButton(
                            selected = current == item,
                            onClick = null,
                            colors = RadioButtonDefaults.colors(selectedColor = QuickPingColors.Primary, unselectedColor = QuickPingColors.TextMuted),
                        )
                    }
                    Spacer(Modifier.height(5.dp))
                }
            }
        }
    }
}

@Composable
private fun PasswordLoginDialog(
    email: String,
    password: String,
    onEmailChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onDismiss: () -> Unit,
    onSubmit: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = QuickPingColors.SurfaceHigh,
        shape = RoundedCornerShape(22.dp),
        title = {
            Text(
                quickText("ورود با ایمیل", "Sign in with email"),
                modifier = Modifier.fillMaxWidth(),
                color = QuickPingColors.TextPrimary,
                fontFamily = Peyda,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = email,
                    onValueChange = onEmailChange,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(quickText("ایمیل", "Email")) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                    shape = RoundedCornerShape(13.dp),
                    colors = loginFieldColors(),
                )
                OutlinedTextField(
                    value = password,
                    onValueChange = onPasswordChange,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(quickText("رمز عبور", "Password")) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    visualTransformation = PasswordVisualTransformation(),
                    shape = RoundedCornerShape(13.dp),
                    colors = loginFieldColors(),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onSubmit, enabled = email.contains("@") && password.isNotEmpty()) {
                Text(quickText("ورود", "Sign in"), color = QuickPingColors.PrimaryLight)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(quickText("انصراف", "Cancel"), color = QuickPingColors.TextSecondary) }
        },
    )
}

@Composable
private fun VerificationDialog(
    value: String,
    error: String?,
    onValueChange: (String) -> Unit,
    onVerify: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = QuickPingColors.SurfaceHigh,
        shape = RoundedCornerShape(22.dp),
        title = {
            Text(
                quickText("کد تأیید ایمیل", "Email verification code"),
                modifier = Modifier.fillMaxWidth(),
                color = QuickPingColors.TextPrimary,
                textAlign = TextAlign.Center,
            )
        },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    quickText("کد شش‌رقمی ارسال‌شده به ایمیل را وارد کنید.", "Enter the six-digit code sent to your email."),
                    color = QuickPingColors.TextSecondary,
                    fontFamily = Peyda,
                    fontSize = 12.sp,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(14.dp))
                OutlinedTextField(
                    value = value,
                    onValueChange = onValueChange,
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    shape = RoundedCornerShape(13.dp),
                    colors = loginFieldColors(),
                )
                if (!error.isNullOrBlank()) {
                    Spacer(Modifier.height(8.dp))
                    Text(error, color = QuickPingColors.Danger, fontSize = 11.sp, textAlign = TextAlign.Center)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onVerify, enabled = value.length == 6) {
                Text(quickText("تأیید و ورود", "Verify and sign in"), color = QuickPingColors.PrimaryLight)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(quickText("انصراف", "Cancel"), color = QuickPingColors.TextSecondary) }
        },
    )
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
            CircularProgressIndicator(modifier = Modifier.size(28.dp), color = QuickPingColors.TextPrimary, strokeWidth = 2.dp)
            Spacer(Modifier.height(14.dp))
            Text(
                quickText("در حال دریافت سرویس‌های شما...", "Loading your services…"),
                color = QuickPingColors.TextPrimary,
                fontFamily = Peyda,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(18.dp))
            TextButton(onClick = onCancel) { Text(quickText("انصراف", "Cancel"), color = QuickPingColors.TextSecondary) }
        }
    }
}

@Composable
private fun loginFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = QuickPingColors.Primary,
    unfocusedBorderColor = QuickPingColors.Border,
    focusedTextColor = QuickPingColors.TextPrimary,
    unfocusedTextColor = QuickPingColors.TextPrimary,
    focusedLabelColor = QuickPingColors.TextSecondary,
    unfocusedLabelColor = QuickPingColors.TextMuted,
)

private fun extractLicense(raw: String): String {
    val value = raw.trim()
    if (value.isBlank()) return ""
    if (!value.contains("://")) return value
    return runCatching {
        val uri = Uri.parse(value)
        listOf("license", "code", "key")
            .firstNotNullOfOrNull { uri.getQueryParameter(it)?.trim()?.takeIf(String::isNotBlank) }
            ?: uri.lastPathSegment?.trim()?.takeIf(String::isNotBlank)
            ?: value
    }.getOrDefault(value)
}
