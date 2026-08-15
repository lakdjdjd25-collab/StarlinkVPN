from pathlib import Path

p = Path("apps/android/src/main/java/org/quickping/app/ui/screens/ReferenceLoginScreen.kt")
s = p.read_text()


def replace_once(old: str, new: str, label: str) -> None:
    global s
    count = s.count(old)
    if count != 1:
        raise SystemExit(f"{label}: expected one match, found {count}")
    s = s.replace(old, new, 1)


replace_once(
    "import androidx.compose.foundation.text.BasicTextField\nimport androidx.compose.foundation.text.KeyboardOptions",
    "import androidx.compose.foundation.text.BasicTextField\nimport androidx.compose.foundation.text.KeyboardActions\nimport androidx.compose.foundation.text.KeyboardOptions",
    "keyboard actions import",
)
replace_once(
    "import androidx.compose.ui.text.input.KeyboardType\nimport androidx.compose.ui.text.input.PasswordVisualTransformation",
    "import androidx.compose.ui.text.input.ImeAction\nimport androidx.compose.ui.text.input.KeyboardType\nimport androidx.compose.ui.text.input.PasswordVisualTransformation",
    "ime action import",
)
for line in (
    "import com.google.android.gms.common.moduleinstall.InstallStatusListener\n",
    "import com.google.android.gms.common.moduleinstall.ModuleInstall\n",
    "import com.google.android.gms.common.moduleinstall.ModuleInstallRequest\n",
    "import com.google.android.gms.common.moduleinstall.ModuleInstallStatusUpdate\n",
    "import com.google.android.gms.common.moduleinstall.ModuleInstallStatusUpdate.InstallState.STATE_CANCELED\n",
    "import com.google.android.gms.common.moduleinstall.ModuleInstallStatusUpdate.InstallState.STATE_COMPLETED\n",
    "import com.google.android.gms.common.moduleinstall.ModuleInstallStatusUpdate.InstallState.STATE_FAILED\n",
):
    if line not in s:
        raise SystemExit(f"missing module-install import: {line.strip()}")
    s = s.replace(line, "", 1)

replace_once(
    "    var scannerPreparing by remember { mutableStateOf(false) }\n",
    "",
    "scanner preparing state",
)

start_marker = "    val scanner = remember(context, scannerOptions) { GmsBarcodeScanning.getClient(context, scannerOptions) }"
start = s.index(start_marker)
end = s.index("\n    Box(Modifier.fillMaxSize().background(Color(0xFF05070B)))", start)
scanner_block = '''    val scanner = remember(context, scannerOptions) { GmsBarcodeScanning.getClient(context, scannerOptions) }
    val qrNotFoundText = quickText("کد مجوز در QR پیدا نشد", "License code was not found in the QR")
    val qrFailedText = quickText("اسکن QR انجام نشد", "QR scan failed")
    val licenseInvalidText = quickText("کد مجوز معتبر نیست", "License code is not valid")

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

    fun launchQrScanner() {
        scanError = null
        scanner.startScan()
            .addOnSuccessListener { barcode -> submitLicense(barcode.rawValue.orEmpty(), fromQr = true) }
            .addOnCanceledListener { scanError = null }
            .addOnFailureListener { scanError = qrFailedText }
    }
'''
s = s[:start] + scanner_block + s[end:]

replace_once(
    '''                onBack = {
                    license = ""
                    focusManager.clearFocus()
                    keyboardController?.hide()
                },
                onScan = { prepareAndLaunchQrScanner() },
                onSubmit = {
                    val value = license.trim()
                    if (value.isNotBlank()) {
                        focusManager.clearFocus()
                        keyboardController?.hide()
                        onPasswordLogin(value, "")
                    }
                },''',
    '''                onScan = { launchQrScanner() },
                onSubmit = { submitLicense(license) },''',
    "license bar caller",
)
replace_once(
    "    onValueChange: (String) -> Unit,\n    onBack: () -> Unit,\n    onScan: () -> Unit,",
    "    onValueChange: (String) -> Unit,\n    onScan: () -> Unit,",
    "license bar signature",
)
replace_once(
    '''            ReferenceLoginInnerButton(R.drawable.ic_arrow_back, onBack)
            Spacer(Modifier.width(6.dp))
            ReferenceLoginInnerButton(R.drawable.ic_scan, onScan)''',
    '''            ReferenceLoginInnerButton(R.drawable.ic_arrow_back, onSubmit, enabled && value.isNotBlank())
            Spacer(Modifier.width(6.dp))
            ReferenceLoginInnerButton(R.drawable.ic_scan, onScan)''',
    "license continue arrow",
)
replace_once(
    "            ReferenceLoginInnerButton(R.drawable.ic_ticket, onSubmit, enabled && value.isNotBlank())",
    "            ReferenceLoginInnerButton(R.drawable.ic_ticket, onClick = {})",
    "decorative license ticket",
)
replace_once(
    "                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),",
    "                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text, imeAction = ImeAction.Done),\n                    keyboardActions = KeyboardActions(onDone = { if (enabled && value.isNotBlank()) onSubmit() }),",
    "license ime action",
)
replace_once(
    '''    message.contains("License is invalid or inactive", ignoreCase = true) ->
        quickText("مجوز معتبر یا فعال نیست", "License is invalid or inactive")''',
    '''    message.contains("License is invalid", ignoreCase = true) ->
        quickText("مجوز نامعتبر، منقضی یا بدون حجم باقی‌مانده است", "License is invalid, expired, or has no remaining quota")
    message.contains("This license allows up to", ignoreCase = true) ->
        quickText("تعداد دستگاه‌های مجاز این مجوز تکمیل شده است", "The device limit for this license has been reached")''',
    "license error localization",
)

p.write_text(s)

test = Path("apps/android/src/test/java/org/quickping/app/ui/screens/ReferenceLoginLicenseTest.kt")
test.parent.mkdir(parents=True, exist_ok=True)
test.write_text('''package org.quickping.app.ui.screens

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReferenceLoginLicenseTest {
    @Test fun plainLicenseIsNormalized() {
        assertEquals("ABC123-XY", referenceExtractLicense("  abc123-xy  "))
    }

    @Test fun prefixedLicenseIsNormalized() {
        assertEquals("ABC123-XY", referenceExtractLicense("license: abc123-xy"))
    }

    @Test fun malformedLicenseIsRejected() {
        assertTrue(referenceExtractLicense("bad code with spaces").isBlank())
    }
}
''')
