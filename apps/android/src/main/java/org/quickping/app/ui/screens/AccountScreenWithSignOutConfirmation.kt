package org.quickping.app.ui.screens

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import org.quickping.app.core.design.Peyda
import org.quickping.app.core.design.QuickPingColors
import org.quickping.app.core.design.quickText
import org.quickping.app.model.Service
import org.quickping.app.model.UserInfo

@Composable
fun AccountScreenWithSignOutConfirmation(
    user: UserInfo,
    service: Service,
    busy: Boolean,
    error: String?,
    onConfirmPasswordChange: (String, String) -> Unit,
    onDeleteAccount: (String) -> Unit,
    onClearAction: () -> Unit,
    onBack: () -> Unit,
    onSignOut: () -> Unit,
    onServices: () -> Unit,
) {
    var showSignOutConfirmation by rememberSaveable { mutableStateOf(false) }

    AccountScreen(
        user = user,
        service = service,
        busy = busy,
        error = error,
        onConfirmPasswordChange = onConfirmPasswordChange,
        onDeleteAccount = onDeleteAccount,
        onClearAction = onClearAction,
        onBack = onBack,
        onSignOut = { showSignOutConfirmation = true },
        onServices = onServices,
    )

    if (showSignOutConfirmation) {
        AlertDialog(
            onDismissRequest = { showSignOutConfirmation = false },
            containerColor = QuickPingColors.SurfaceHigh,
            title = {
                Text(
                    text = quickText("خروج از حساب کاربری", "Sign out"),
                    color = QuickPingColors.TextPrimary,
                    fontFamily = Peyda,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                )
            },
            text = {
                Text(
                    text = quickText(
                        "آیا مطمئن هستید که می‌خواهید از حساب کاربری خارج شوید؟",
                        "Are you sure you want to sign out of your account?",
                    ),
                    color = QuickPingColors.TextSecondary,
                    fontFamily = Peyda,
                    textAlign = TextAlign.Center,
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showSignOutConfirmation = false
                        onSignOut()
                    },
                ) {
                    Text(
                        text = quickText("خروج از حساب", "Sign out"),
                        color = QuickPingColors.Danger,
                        fontFamily = Peyda,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { showSignOutConfirmation = false }) {
                    Text(
                        text = quickText("انصراف", "Cancel"),
                        color = Color(0xFFB8BBC4),
                        fontFamily = Peyda,
                    )
                }
            },
        )
    }
}
