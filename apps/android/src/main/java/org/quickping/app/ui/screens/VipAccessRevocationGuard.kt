package org.quickping.app.ui.screens

import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import org.quickping.app.model.ConnectionStatus
import org.quickping.app.state.QuickPingUiState

@Composable
internal fun VipAccessRevocationGuard(
    state: QuickPingUiState,
    onDisconnect: () -> Unit,
) {
    val context = LocalContext.current
    var previousSelectedId by remember(state.service.id) { mutableStateOf(state.selectedServerId) }
    var previousSelectedWasVip by remember(state.service.id) {
        mutableStateOf(state.servers.firstOrNull { it.id == state.selectedServerId }?.isVip == true)
    }

    LaunchedEffect(
        state.selectedServerId,
        state.servers.joinToString(separator = ",") { it.id },
        state.connectionStatus,
    ) {
        val previousServerRemoved = previousSelectedId.isNotBlank() &&
            state.servers.none { it.id == previousSelectedId }
        if (
            previousSelectedWasVip &&
            previousServerRemoved &&
            state.connectionStatus in setOf(ConnectionStatus.Connected, ConnectionStatus.Connecting)
        ) {
            onDisconnect()
            Toast.makeText(
                context,
                vipAccessChangedMessage(state.settings.language.code),
                Toast.LENGTH_LONG,
            ).show()
        }
        previousSelectedId = state.selectedServerId
        previousSelectedWasVip = state.servers.firstOrNull { it.id == state.selectedServerId }?.isVip == true
    }
}

private fun vipAccessChangedMessage(language: String): String = when (language) {
    "en" -> "Your access to this server changed. Please choose another server."
    "nl" -> "Je toegang tot deze server is gewijzigd. Kies een andere server."
    "ar" -> "تم تغيير وصولك إلى هذا الخادم. يرجى اختيار خادم آخر."
    "tr" -> "Bu sunucuya erişiminiz değişti. Lütfen başka bir sunucu seçin."
    "ru" -> "Доступ к этому серверу изменён. Выберите другой сервер."
    "hi" -> "इस सर्वर तक आपकी पहुँच बदल गई है। कृपया दूसरा सर्वर चुनें।"
    "zh" -> "您对此服务器的访问权限已更改。请选择其他服务器。"
    "ur" -> "اس سرور تک آپ کی رسائی تبدیل ہو گئی ہے۔ براہ کرم دوسرا سرور منتخب کریں۔"
    else -> "دسترسی شما به این سرور تغییر کرده است. لطفاً سرور دیگری انتخاب کنید."
}
