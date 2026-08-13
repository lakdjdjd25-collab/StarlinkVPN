package org.quickping.app.ui.screens

import android.net.Uri
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import com.google.android.gms.mlkit.codescanner.GmsBarcodeScanning
import com.google.android.gms.mlkit.codescanner.GmsBarcodeScannerOptions
import com.google.mlkit.vision.barcode.common.Barcode
import kotlinx.coroutines.delay
import org.quickping.app.R
import org.quickping.app.core.design.Peyda
import org.quickping.app.core.design.QuickPingColors
import org.quickping.app.core.design.quickText
import org.quickping.app.model.AppLanguage

private const val REFERENCE_LOGIN_MOTION_MS = 300
private const val WELCOME_CHARACTER_DELAY_MS = 72L

@Composable
fun ReferenceLoginScreen(
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

    var license by remember { mutableStateOf("") }
    var scanError by remember { mutableStateOf<String?>(null) }
    var showLanguageDialog by remember { mutableStateOf(false) }
    var showEmailDialog by remember { mutableStateOf(false) }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var verificationCode by remember { mutableStateOf("") }

    val welcomeTarget = quickText("خوش اومدید", "Welcome")
    var typedWelcome by remember(welcomeTarget) { mutableStateOf("") }
    var cursorVisible by remember(welcomeTarget) { mutableStateOf(true) }
    LaunchedEffect(welcomeTarget) {
        typedWelcome = ""
        cursorVisible = true
        delay(180)
        welcomeTarget.forEachIndexed { index, _ ->
            typedWelcome = welcomeTarget.take(index + 1)
            delay(WELCOME_CHARACTER_DELAY_MS)
        }
        while (true) {
            delay(470)
            cursorVisible = !cursorVisible
        }
    }

    LaunchedEffect(debugCode) {
        if (!debugCode.isNullOrBlank()) verificationCode = debugCode
    }

    val logoWidth by animateDpAsState(
        targetValue = if (keyboardVisible) 88.dp else 156.dp,
        animationSpec = tween(REFERENCE_LOGIN_MOTION_MS),
        label = "referenceLoginLogoWidth",
    )
    val logoHeight by animateDpAsState(
        targetValue = if (keyboardVisible) 59.dp else 105.dp,
        animationSpec = tween(REFERENCE_LOGIN_MOTION_MS),
        label = "referenceLoginLogoHeight",
    )
    val heroTop by animateDpAsState(
        targetValue = if (keyboardVisible) 0.dp else 50.dp,
        animationSpec = tween(REFERENCE_LOGIN_MOTION_MS),
        label = "referenceLoginHeroTop",
    )
    val headerHeight by animateDpAsState(
        targetValue = if (keyboardVisible) 220.dp else 390.dp,
        animationSpec = tween(REFERENCE_LOGIN_MOTION_MS),
        label = "referenceLoginHeaderHeight",
    )

    val scannerOptions = remember {
        GmsBarcodeScannerOptions.Builder()
            .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
            .enableAutoZoom()
            .build()
    }
    val scanner = remember(context, scannerOptions) { GmsBarcodeScanning.getClient(context, scannerOptions) }

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
            modifier = Modifier.align(Alignment.TopCenter).fillMaxWidth().height(headerHeight),
            contentScale = ContentScale.FillWidth,
        )

        val bottomInsetModifier = if (keyboardVisible) Modifier.imePadding() else Modifier.navigationBarsPadding()
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .then(bottomInsetModifier)
                .padding(horizontal = 18.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().height(if (keyboardVisible) 38.dp else 48.dp),
                horizontalArrangement = Arrangement.Absolute.Right,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    modifier = Modifier
                        .background(Color(0xFF111318).copy(alpha = .92f), RoundedCornerShape(16.dp))
                        .border(1.dp, Color(0xFF22262D), RoundedCornerShape(16.dp))
                        .clickable { showLanguageDialog = true }
                        .padding(
                            horizontal = if (keyboardVisible) 10.dp else 12.dp,
                            vertical = if (keyboardVisible) 6.dp else 8.dp,
                        ),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        language.label,
                        color = Color(0xFFB8BBC4),
                        fontFamily = Peyda,
                        fontSize = if (keyboardVisible) 11.sp else 12.sp,
                        fontWeight = FontWeight.Medium,
                    )
                    Spacer(Modifier.width(5.dp))
                    Text("⌄", color = Color(0xFF8A8E98), fontSize = 13.sp)
                }
            }

            Spacer(Modifier.height(heroTop))
            Image(
                painter = painterResource(R.drawable.ic_logo_welcome),
                contentDescription = "QuickPing",
                modifier = Modifier.size(logoWidth, logoHeight),
                contentScale = ContentScale.Fit,
            )
            Spacer(Modifier.height(if (keyboardVisible) 2.dp else 10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .width(2.dp)
                        .height(if (keyboardVisible) 16.dp else 18.dp)
                        .background(Color(0xFF347BFF), CircleShape),
                )
                Spacer(Modifier.width(7.dp))
                Text(
                    text = buildAnnotatedString {
                        append(typedWelcome)
                        withStyle(SpanStyle(color = Color(0xFF4A82FF), fontWeight = FontWeight.Normal)) {
                            append(if (cursorVisible) "│" else " ")
                        }
                    },
                    color = Color(0xFFCDD0D7),
                    fontFamily = Peyda,
                    fontSize = if (keyboardVisible) 16.sp else 18.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                )
            }

            Spacer(Modifier.weight(1f))
            ReferenceLicenseBar(
                value = license,
                enabled = !busy,
                onValueChange = {
                    license = it
                    scanError = null
                },
                onBack = {
                    license = ""
                    focusManager.clearFocus()
                    keyboardController?.hide()
                },
                onScan = {
                    scanner.startScan()
                        .addOnSuccessListener { barcode ->
                            val parsed = referenceExtractLicense(barcode.rawValue.orEmpty())
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
                Spacer(Modifier.height(4.dp))
                Text(
                    visibleError,
                    color = QuickPingColors.Danger,
                    fontFamily = Peyda,
                    fontSize = 10.sp,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                )
            }

            Spacer(Modifier.height(if (keyboardVisible) 4.dp else 11.dp))
            ReferenceOrDivider()
            Spacer(Modifier.height(if (keyboardVisible) 4.dp else 11.dp))

            Row(
                horizontalArrangement = Arrangement.spacedBy(if (keyboardVisible) 12.dp else 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box {
                    ReferenceLoginSquareButton(
                        icon = R.drawable.logo_google,
                        contentDescription = quickText("ورود با گوگل", "Sign in with Google"),
                        onClick = onGoogleRequested,
                        iconTint = Color.Unspecified,
                        compact = keyboardVisible,
                    )
                    Row(
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .background(Color(0xFFE9EAED), RoundedCornerShape(8.dp))
                            .padding(horizontal = 5.dp, vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("10GB", color = Color(0xFF282B31), fontSize = 9.sp, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.width(2.dp))
                        Icon(
                            painterResource(R.drawable.ic_gift),
                            contentDescription = null,
                            tint = Color(0xFF282B31),
                            modifier = Modifier.size(11.dp),
                        )
                    }
                }
                ReferenceLoginSquareButton(
                    icon = R.drawable.ic_mail,
                    contentDescription = quickText("ورود با ایمیل", "Sign in with email"),
                    onClick = {
                        email = ""
                        password = ""
                        showEmailDialog = true
                    },
                    compact = keyboardVisible,
                )
            }

            Spacer(Modifier.height(if (keyboardVisible) 4.dp else 10.dp))
            Row(
                modifier = Modifier.clickable(onClick = onHelpRequested),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    painterResource(R.drawable.ic_help_hexagon),
                    contentDescription = null,
                    tint = Color(0xFF999DA7),
                    modifier = Modifier.size(if (keyboardVisible) 13.dp else 15.dp),
                )
                Spacer(Modifier.width(5.dp))
                Text(
                    quickText("به کمک نیاز دارید؟", "Need help?"),
                    color = Color(0xFF999DA7),
                    fontFamily = Peyda,
                    fontSize = if (keyboardVisible) 10.sp else 11.sp,
                )
            }

            Spacer(Modifier.height(if (keyboardVisible) 4.dp else 12.dp))
            ReferenceLoginTerms(compact = keyboardVisible)
            Spacer(Modifier.height(if (keyboardVisible) 3.dp else 12.dp))
        }
    }

    if (showLanguageDialog) {
        AlertDialog(
            onDismissRequest = { showLanguageDialog = false },
            containerColor = QuickPingColors.SurfaceHigh,
            title = {
                Text(
                    quickText("انتخاب زبان برنامه", "Choose app language"),
                    color = QuickPingColors.TextPrimary,
                    fontFamily = Peyda,
                )
            },
            text = {
                Column {
                    AppLanguage.entries.forEach { item ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    onLanguageChange(item)
                                    showLanguageDialog = false
                                }
                                .padding(vertical = 9.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(item.label, modifier = Modifier.weight(1f), color = QuickPingColors.TextPrimary, fontFamily = Peyda)
                            RadioButton(selected = item == language, onClick = null)
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showLanguageDialog = false }) {
                    Text(quickText("بستن", "Close"), color = QuickPingColors.TextSecondary)
                }
            },
        )
    }

    if (showEmailDialog) {
        AlertDialog(
            onDismissRequest = { showEmailDialog = false },
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
                        onValueChange = { email = it.trim() },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text(quickText("ایمیل", "Email")) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                    )
                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text(quickText("رمز عبور", "Password")) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        visualTransformation = PasswordVisualTransformation(),
                    )
                    TextButton(
                        onClick = {
                            if (email.contains("@")) {
                                showEmailDialog = false
                                onRequestEmailCode(email)
                            }
                        },
                        enabled = email.contains("@") && !busy,
                    ) {
                        Text(quickText("ورود با کد ایمیل", "Sign in with email code"), color = QuickPingColors.TextSecondary)
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showEmailDialog = false
                        onPasswordLogin(email, password)
                    },
                    enabled = email.contains("@") && password.isNotEmpty(),
                ) {
                    Text(quickText("ورود", "Sign in"), color = QuickPingColors.PrimaryLight)
                }
            },
            dismissButton = {
                TextButton(onClick = { showEmailDialog = false }) {
                    Text(quickText("انصراف", "Cancel"), color = QuickPingColors.TextSecondary)
                }
            },
        )
    }

    if (busy) {
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
                TextButton(onClick = onCancelChallenge) {
                    Text(quickText("انصراف", "Cancel"), color = QuickPingColors.TextSecondary)
                }
            }
        }
    } else if (challengeId != null) {
        AlertDialog(
            onDismissRequest = onCancelChallenge,
            containerColor = QuickPingColors.SurfaceHigh,
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
                    OutlinedTextField(
                        value = verificationCode,
                        onValueChange = { verificationCode = it.filter(Char::isDigit).take(6) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    )
                    if (!error.isNullOrBlank()) {
                        Spacer(Modifier.height(8.dp))
                        Text(error, color = QuickPingColors.Danger, fontSize = 11.sp, textAlign = TextAlign.Center)
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { onVerifyCode(verificationCode) }, enabled = verificationCode.length == 6) {
                    Text(quickText("تأیید و ورود", "Verify and sign in"), color = QuickPingColors.PrimaryLight)
                }
            },
            dismissButton = {
                TextButton(onClick = onCancelChallenge) {
                    Text(quickText("انصراف", "Cancel"), color = QuickPingColors.TextSecondary)
                }
            },
        )
    }
}

@Composable
private fun ReferenceLicenseBar(
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
        ReferenceLoginInnerButton(R.drawable.ic_arrow_back, onBack)
        Spacer(Modifier.width(6.dp))
        ReferenceLoginInnerButton(R.drawable.ic_scan, onScan)
        Box(Modifier.weight(1f).padding(horizontal = 10.dp), contentAlignment = Alignment.CenterEnd) {
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
        ReferenceLoginInnerButton(R.drawable.ic_ticket, onSubmit, enabled && value.isNotBlank())
    }
}

@Composable
private fun ReferenceLoginInnerButton(icon: Int, onClick: () -> Unit, enabled: Boolean = true) {
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
private fun ReferenceOrDivider() {
    Row(Modifier.fillMaxWidth().height(18.dp), verticalAlignment = Alignment.CenterVertically) {
        Canvas(Modifier.weight(1f).height(1.dp)) {
            drawLine(Color(0xFF292C33), androidx.compose.ui.geometry.Offset.Zero, androidx.compose.ui.geometry.Offset(size.width, 0f))
        }
        Text(quickText("یا", "or"), modifier = Modifier.padding(horizontal = 11.dp), color = Color(0xFF7B7F89), fontFamily = Peyda, fontSize = 11.sp)
        Canvas(Modifier.weight(1f).height(1.dp)) {
            drawLine(Color(0xFF292C33), androidx.compose.ui.geometry.Offset.Zero, androidx.compose.ui.geometry.Offset(size.width, 0f))
        }
    }
}

@Composable
private fun ReferenceLoginSquareButton(
    icon: Int,
    contentDescription: String,
    onClick: () -> Unit,
    iconTint: Color = QuickPingColors.TextPrimary,
    compact: Boolean = false,
) {
    val size = if (compact) 60.dp else 72.dp
    val corner = if (compact) 19.dp else 22.dp
    Box(
        modifier = Modifier
            .size(size)
            .background(Color(0xFF080A0D).copy(alpha = .9f), RoundedCornerShape(corner))
            .border(1.dp, Color(0xFF252830), RoundedCornerShape(corner))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painterResource(icon),
            contentDescription,
            tint = iconTint,
            modifier = Modifier.size(if (compact) 24.dp else 27.dp),
        )
    }
}

@Composable
private fun ReferenceLoginTerms(compact: Boolean) {
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
        fontSize = if (compact) 9.sp else 10.sp,
        lineHeight = if (compact) 13.sp else 15.sp,
        textAlign = TextAlign.Center,
    )
}

private fun referenceExtractLicense(raw: String): String {
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
