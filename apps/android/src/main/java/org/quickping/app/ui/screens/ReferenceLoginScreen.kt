package org.quickping.app.ui.screens

import android.content.ClipboardManager
import android.content.Context
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.google.android.gms.mlkit.codescanner.GmsBarcodeScannerOptions
import com.google.android.gms.mlkit.codescanner.GmsBarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import java.net.URI
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import kotlinx.coroutines.delay
import org.json.JSONObject
import org.quickping.app.R
import org.quickping.app.core.design.Peyda
import org.quickping.app.core.design.QuickPingColors
import org.quickping.app.core.design.quickText
import org.quickping.app.model.AppLanguage

private const val WELCOME_CHARACTER_DELAY_MS = 72L
private const val REFERENCE_VIEWPORT_HEIGHT_DP = 843f

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

    val scannerOptions = remember {
        GmsBarcodeScannerOptions.Builder()
            .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
            .enableAutoZoom()
            .build()
    }
    val scanner = remember(context, scannerOptions) { GmsBarcodeScanning.getClient(context, scannerOptions) }
    val qrNotFoundText = quickText("کد مجوز در QR پیدا نشد", "License code was not found in the QR")
    val qrFailedText = quickText("اسکن QR انجام نشد", "QR scan failed")
    val licenseInvalidText = quickText("کد مجوز معتبر نیست", "License code is not valid")
    val clipboardEmptyText = quickText("متن مجوز در حافظهٔ کپی پیدا نشد", "No license was found in the clipboard")
    val qrPromptText = quickText("کیوآرکد مجوز را داخل کادر قرار دهید", "Place the license QR code inside the frame")

    fun submitLicense(raw: String, fromQr: Boolean = false) {
        val parsed = referenceExtractLicense(raw)
        if (parsed.isBlank()) {
            scanError = if (fromQr) qrNotFoundText else licenseInvalidText
            return
        }
        license = parsed
        scanError = null
        focusManager.clearFocus()
        keyboardController?.hide()
        onPasswordLogin(parsed, "")
    }

    val fallbackScannerLauncher = rememberLauncherForActivityResult(ScanContract()) { result ->
        val contents = result.contents
        if (contents.isNullOrBlank()) scanError = null else submitLicense(contents, fromQr = true)
    }

    fun launchFallbackScanner() {
        val options = ScanOptions()
            .setDesiredBarcodeFormats(ScanOptions.QR_CODE)
            .setPrompt(qrPromptText)
            .setBeepEnabled(false)
            .setOrientationLocked(true)
        runCatching { fallbackScannerLauncher.launch(options) }
            .onFailure { scanError = qrFailedText }
    }

    fun launchQrScanner() {
        scanError = null
        scanner.startScan()
            .addOnSuccessListener { barcode -> submitLicense(barcode.rawValue.orEmpty(), fromQr = true) }
            .addOnCanceledListener { scanError = null }
            .addOnFailureListener { launchFallbackScanner() }
    }

    fun pasteLicense() {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        val text = clipboard?.primaryClip?.getItemAt(0)?.coerceToText(context)?.toString().orEmpty()
        val parsed = referenceExtractLicense(text)
        if (parsed.isBlank()) {
            scanError = clipboardEmptyText
        } else {
            license = parsed
            scanError = null
        }
    }

    BoxWithConstraints(Modifier.fillMaxSize().background(Color(0xFF05070B))) {
        val layoutScale = (maxHeight.value / REFERENCE_VIEWPORT_HEIGHT_DP).coerceIn(0.88f, 1.08f)

        Image(
            painter = painterResource(R.drawable.bg_login),
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.FillBounds,
        )
        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .height(520.dp)
                .background(
                    Brush.verticalGradient(
                        colorStops = arrayOf(
                            0f to Color(0xFF0A244F).copy(alpha = 0.72f),
                            0.42f to Color(0xFF081A35).copy(alpha = 0.48f),
                            0.75f to Color(0xFF07101D).copy(alpha = 0.22f),
                            1f to Color.Transparent,
                        ),
                    ),
                ),
        )

        if (!keyboardVisible) {
            Image(
                painter = painterResource(R.drawable.header_login),
                contentDescription = null,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .offset(y = (160.dp * layoutScale))
                    .fillMaxWidth()
                    .height(417.dp),
                contentScale = ContentScale.FillWidth,
            )

            ReferenceLanguageSelector(
                language = language,
                compact = false,
                onClick = { showLanguageDialog = true },
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .statusBarsPadding()
                    .padding(top = 10.dp, end = 18.dp),
            )

            Image(
                painter = painterResource(R.drawable.nimhub_logo),
                contentDescription = "NimHUB Vpn",
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .offset(y = (260.dp * layoutScale))
                    .size(width = 156.dp, height = 105.dp),
                contentScale = ContentScale.Fit,
            )

            ReferenceWelcomeText(
                text = typedWelcome,
                cursorVisible = cursorVisible,
                compact = false,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .offset(y = (375.dp * layoutScale)),
            )

            Column(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .offset(y = (568.dp * layoutScale))
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                ReferenceLicenseBar(
                    value = license,
                    enabled = !busy,
                    compact = false,
                    onValueChange = {
                        license = it
                        scanError = null
                    },
                    onScan = ::launchQrScanner,
                    onPaste = ::pasteLicense,
                    onSubmit = { submitLicense(license) },
                )
                val visibleError = scanError ?: error
                if (!visibleError.isNullOrBlank()) {
                    Spacer(Modifier.height(4.dp))
                    ReferenceLoginError(visibleError)
                }
            }

            ReferenceOrDivider(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .offset(y = (635.dp * layoutScale))
                    .padding(horizontal = 18.dp),
            )

            ReferenceSocialButtons(
                compact = false,
                onGoogleRequested = onGoogleRequested,
                onEmailRequested = {
                    email = ""
                    password = ""
                    showEmailDialog = true
                },
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .offset(y = (655.dp * layoutScale)),
            )

            ReferenceHelpRow(
                onHelpRequested = onHelpRequested,
                compact = false,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .offset(y = (745.dp * layoutScale)),
            )

            ReferenceLoginTerms(
                compact = false,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .offset(y = (780.dp * layoutScale))
                    .padding(horizontal = 26.dp),
            )
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .imePadding()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 18.dp, vertical = 6.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().height(40.dp),
                    horizontalArrangement = Arrangement.Absolute.Right,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    ReferenceLanguageSelector(
                        language = language,
                        compact = true,
                        onClick = { showLanguageDialog = true },
                    )
                }
                Spacer(Modifier.height(4.dp))
                Image(
                    painter = painterResource(R.drawable.nimhub_logo),
                    contentDescription = "NimHUB Vpn",
                    modifier = Modifier.size(width = 104.dp, height = 70.dp),
                    contentScale = ContentScale.Fit,
                )
                Spacer(Modifier.height(3.dp))
                ReferenceWelcomeText(typedWelcome, cursorVisible, true)
                Spacer(Modifier.height(14.dp))
                ReferenceLicenseBar(
                    value = license,
                    enabled = !busy,
                    compact = true,
                    onValueChange = {
                        license = it
                        scanError = null
                    },
                    onScan = ::launchQrScanner,
                    onPaste = ::pasteLicense,
                    onSubmit = { submitLicense(license) },
                )
                val visibleError = scanError ?: error
                if (!visibleError.isNullOrBlank()) {
                    Spacer(Modifier.height(4.dp))
                    ReferenceLoginError(visibleError)
                }
                Spacer(Modifier.height(5.dp))
                ReferenceOrDivider()
                Spacer(Modifier.height(5.dp))
                ReferenceSocialButtons(
                    compact = true,
                    onGoogleRequested = onGoogleRequested,
                    onEmailRequested = {
                        email = ""
                        password = ""
                        showEmailDialog = true
                    },
                )
                Spacer(Modifier.height(6.dp))
                ReferenceHelpRow(onHelpRequested, compact = true)
                Spacer(Modifier.height(6.dp))
                ReferenceLoginTerms(compact = true)
                Spacer(Modifier.height(8.dp))
            }
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
    }
}

@Composable
private fun ReferenceLanguageSelector(
    language: AppLanguage,
    compact: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .background(Color(0xFF11151D).copy(alpha = .94f), RoundedCornerShape(16.dp))
            .border(1.dp, Color(0xFF252B35), RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(
                horizontal = if (compact) 11.dp else 12.dp,
                vertical = if (compact) 7.dp else 8.dp,
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            language.label,
            color = Color(0xFFBDC1CA),
            fontFamily = Peyda,
            fontSize = if (compact) 11.5.sp else 12.sp,
            fontWeight = FontWeight.Medium,
        )
        Spacer(Modifier.width(5.dp))
        Icon(
            painter = painterResource(R.drawable.ic_chevron_down),
            contentDescription = null,
            tint = Color.Unspecified,
            modifier = Modifier.size(12.dp),
        )
    }
}

@Composable
private fun ReferenceWelcomeText(
    text: String,
    cursorVisible: Boolean,
    compact: Boolean,
    modifier: Modifier = Modifier,
) {
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "│",
                color = Color(0xFF4A82FF).copy(alpha = if (cursorVisible) 1f else 0f),
                fontFamily = Peyda,
                fontSize = if (compact) 16.sp else 18.sp,
                fontWeight = FontWeight.Normal,
                maxLines = 1,
            )
            Spacer(Modifier.width(4.dp))
            Text(
                text = text,
                color = Color(0xFFD1D4DB),
                fontFamily = Peyda,
                fontSize = if (compact) 16.sp else 18.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun ReferenceLoginError(message: String) {
    Text(
        referenceLocalizedLoginError(message),
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF2A1116).copy(alpha = .72f), RoundedCornerShape(10.dp))
            .padding(horizontal = 10.dp, vertical = 6.dp),
        color = Color(0xFFFF9AA8),
        fontFamily = Peyda,
        fontSize = 11.5.sp,
        lineHeight = 15.sp,
        textAlign = TextAlign.Center,
        maxLines = 3,
    )
}

@Composable
private fun ReferenceSocialButtons(
    compact: Boolean,
    onGoogleRequested: () -> Unit,
    onEmailRequested: () -> Unit,
    modifier: Modifier = Modifier,
) {
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        Row(
            modifier = modifier,
            horizontalArrangement = Arrangement.spacedBy(if (compact) 12.dp else 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box {
                ReferenceLoginSquareButton(
                    icon = R.drawable.logo_google,
                    contentDescription = quickText("ورود با گوگل", "Sign in with Google"),
                    onClick = onGoogleRequested,
                    iconTint = Color.Unspecified,
                    compact = compact,
                )
                Row(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .offset(y = (-7).dp)
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
                onClick = onEmailRequested,
                compact = compact,
            )
        }
    }
}

@Composable
private fun ReferenceHelpRow(
    onHelpRequested: () -> Unit,
    compact: Boolean,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.clickable(onClick = onHelpRequested),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            painterResource(R.drawable.ic_help_hexagon),
            contentDescription = null,
            tint = Color(0xFFA6AAB4),
            modifier = Modifier.size(if (compact) 14.dp else 15.dp),
        )
        Spacer(Modifier.width(5.dp))
        Text(
            quickText("به کمک نیاز دارید؟", "Need help?"),
            color = Color(0xFFA6AAB4),
            fontFamily = Peyda,
            fontSize = if (compact) 11.sp else 12.sp,
        )
    }
}

@Composable
private fun ReferenceLicenseBar(
    value: String,
    enabled: Boolean,
    compact: Boolean,
    onValueChange: (String) -> Unit,
    onScan: () -> Unit,
    onPaste: () -> Unit,
    onSubmit: () -> Unit,
) {
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(if (compact) 52.dp else 54.dp)
                .background(Color(0xFF17191E), RoundedCornerShape(18.dp))
                .border(1.dp, Color(0xFF272C35), RoundedCornerShape(18.dp))
                .padding(horizontal = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ReferenceLoginInnerButton(R.drawable.ic_arrow_back, onSubmit, enabled && value.isNotBlank())
            Spacer(Modifier.width(6.dp))
            ReferenceLoginInnerButton(R.drawable.ic_scan, onScan)
            Box(Modifier.weight(1f).padding(horizontal = 10.dp), contentAlignment = Alignment.CenterEnd) {
                if (value.isEmpty()) {
                    Text(
                        quickText("مجوز خود را وارد کنید", "Enter your license"),
                        modifier = Modifier.fillMaxWidth(),
                        color = Color(0xFF777C87),
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
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { if (enabled && value.isNotBlank()) onSubmit() }),
                    textStyle = TextStyle(
                        color = QuickPingColors.TextPrimary,
                        fontFamily = Peyda,
                        fontSize = 13.sp,
                        textAlign = TextAlign.End,
                    ),
                    cursorBrush = SolidColor(Color(0xFF4A82FF)),
                )
            }
            ReferenceLoginInnerButton(R.drawable.ic_ticket, onClick = onPaste, enabled = enabled)
        }
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
private fun ReferenceOrDivider(modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth().height(18.dp), verticalAlignment = Alignment.CenterVertically) {
        Canvas(Modifier.weight(1f).height(1.dp)) {
            drawLine(Color(0xFF292C33), androidx.compose.ui.geometry.Offset.Zero, androidx.compose.ui.geometry.Offset(size.width, 0f))
        }
        Text(quickText("یا", "or"), modifier = Modifier.padding(horizontal = 11.dp), color = Color(0xFF858A94), fontFamily = Peyda, fontSize = 11.sp)
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
            .background(Color(0xFF080A0D).copy(alpha = .94f), RoundedCornerShape(corner))
            .border(1.dp, Color(0xFF2A2F39), RoundedCornerShape(corner))
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
private fun ReferenceLoginTerms(
    compact: Boolean,
    modifier: Modifier = Modifier,
) {
    Text(
        text = buildAnnotatedString {
            append(quickText("با ادامه دادن، شما با ", "By continuing, you agree to the "))
            withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = Color(0xFFAEB2BC))) {
                append(quickText("شرایط سرویس‌ها", "Terms of Service"))
            }
            append(quickText(" و ", " and "))
            withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = Color(0xFFAEB2BC))) {
                append(quickText("سیاست حفظ حریم خصوصی", "Privacy Policy"))
            }
            append(quickText(" موافقت می‌کنید", ""))
        },
        modifier = modifier.fillMaxWidth().padding(horizontal = if (compact) 4.dp else 8.dp),
        color = Color(0xFF858A95),
        fontFamily = Peyda,
        fontSize = if (compact) 11.sp else 12.5.sp,
        lineHeight = if (compact) 15.5.sp else 18.sp,
        fontWeight = FontWeight.Medium,
        textAlign = TextAlign.Center,
    )
}

internal fun referenceExtractLicense(raw: String): String {
    val value = raw.trim()
    if (value.isBlank()) return ""

    if (value.startsWith("{")) {
        runCatching { JSONObject(value) }.getOrNull()?.let { json ->
            listOf("license", "code", "key")
                .firstNotNullOfOrNull { key -> json.optString(key).takeIf(String::isNotBlank) }
                ?.let(::normalizeReferenceLicense)
                ?.takeIf(String::isNotBlank)
                ?.let { return it }
        }
    }

    val prefixed = value.replace(
        Regex("^(NIMHUB|LICENSE|LICENCE)\\s*[:=]\\s*", RegexOption.IGNORE_CASE),
        "",
    )
    normalizeReferenceLicense(prefixed).takeIf(String::isNotBlank)?.let { return it }

    if (!value.contains("://")) return ""
    return runCatching {
        val uri = URI(value)
        val scheme = uri.scheme?.lowercase().orEmpty()
        if (scheme !in setOf("https", "http", "nimhub")) return@runCatching ""

        val host = uri.host?.lowercase().orEmpty()
        if (scheme in setOf("https", "http") && !host.contains("nimhub")) {
            return@runCatching ""
        }

        val query = uri.rawQuery.orEmpty()
            .split('&')
            .mapNotNull { pair ->
                val separator = pair.indexOf('=')
                if (separator <= 0) return@mapNotNull null
                val key = URLDecoder.decode(pair.substring(0, separator), StandardCharsets.UTF_8.name())
                val content = URLDecoder.decode(pair.substring(separator + 1), StandardCharsets.UTF_8.name())
                key.lowercase() to content
            }
            .toMap()
        val candidate = listOf("license", "code", "key")
            .firstNotNullOfOrNull { query[it]?.trim()?.takeIf(String::isNotBlank) }
            ?: uri.path?.substringAfterLast('/')?.trim().orEmpty()
        normalizeReferenceLicense(candidate)
    }.getOrDefault("")
}

private fun normalizeReferenceLicense(value: String): String {
    val candidate = value.trim().uppercase()
    return candidate.takeIf { it.matches(Regex("[A-Z0-9_-]{6,64}")) }.orEmpty()
}

@Composable
private fun referenceLocalizedLoginError(message: String): String = when {
    message.contains("Email or password is incorrect", ignoreCase = true) ->
        quickText("ایمیل یا رمز عبور صحیح نیست", "Email or password is incorrect")
    message.contains("License is invalid", ignoreCase = true) ->
        quickText("مجوز نامعتبر، منقضی یا بدون حجم باقی‌مانده است", "License is invalid, expired, or has no remaining quota")
    message.contains("This license allows up to", ignoreCase = true) ->
        quickText("تعداد دستگاه‌های مجاز این مجوز تکمیل شده است", "The device limit for this license has been reached")
    message.contains("Google sign-in was cancelled", ignoreCase = true) ->
        quickText("ورود با گوگل لغو شد", "Google sign-in was cancelled")
    message.contains("No available Google account", ignoreCase = true) ->
        quickText("حساب گوگل قابل استفاده‌ای روی دستگاه پیدا نشد", "No available Google account was found on this device")
    message.contains("Google sign-in could not be opened", ignoreCase = true) ->
        quickText("ورود با گوگل باز نشد", "Google sign-in could not be opened")
    message.contains("ورود گوگل") || message.contains("Google", ignoreCase = true) ->
        quickText("ورود با گوگل کامل نشد", "Google sign-in could not be completed")
    else -> message
}
