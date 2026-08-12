package org.quickping.app.state

import android.app.Application
import android.content.Intent
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.Job
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import org.quickping.app.QuickPingApplication
import org.quickping.app.data.network.ApiException
import org.quickping.app.data.network.BootstrapPayload
import org.quickping.app.model.ConnectionStatus
import org.quickping.app.vpn.QuickPingVpnService
import org.quickping.app.vpn.ServiceState

class QuickPingViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = (application as QuickPingApplication).repository
    private var loginJob: Job? = null
    private val _state = MutableStateFlow(QuickPingUiState())
    val state: StateFlow<QuickPingUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            _state.update { it.copy(busy = true) }
            val bootstrap = repository.restoreSession()
            _state.update { current ->
                val restored = bootstrap?.let { current.withBootstrap(it) } ?: current
                restored.copy(
                    initialized = true,
                    signedIn = bootstrap != null,
                    busy = false,
                )
            }
        }
        viewModelScope.launch {
            QuickPingVpnService.state.collect { serviceState ->
                _state.update {
                    it.copy(
                        connectionStatus = when (serviceState) {
                            ServiceState.Disconnected -> ConnectionStatus.Disconnected
                            ServiceState.Connecting -> ConnectionStatus.Connecting
                            ServiceState.Connected -> ConnectionStatus.Connected
                            ServiceState.Error -> ConnectionStatus.Error
                        },
                    )
                }
            }
        }
    }

    fun connectVpn() {
        val current = _state.value
        if (current.servers.isEmpty() || current.service.id.isBlank()) {
            _state.update { it.copy(connectionStatus = ConnectionStatus.Error) }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(connectionStatus = ConnectionStatus.Connecting) }
            runCatching {
                val config = repository.runtimeConfig(current.service.id, current.selectedServerId)
                val application = getApplication<Application>()
                ContextCompat.startForegroundService(
                    application,
                    Intent(application, QuickPingVpnService::class.java)
                        .setAction(QuickPingVpnService.ACTION_CONNECT)
                        .putExtra(QuickPingVpnService.EXTRA_CONFIG_JSON, config),
                )
            }.onFailure {
                _state.update { state -> state.copy(connectionStatus = ConnectionStatus.Error) }
            }
        }
    }

    fun disconnectVpn() {
        val application = getApplication<Application>()
        application.startService(
            Intent(application, QuickPingVpnService::class.java)
                .setAction(QuickPingVpnService.ACTION_DISCONNECT),
        )
    }

    fun selectServer(id: String) = _state.update { it.copy(selectedServerId = id) }

    fun requestEmailCode(email: String) {
        loginJob?.cancel()
        loginJob = viewModelScope.launch {
            _state.update {
                it.copy(
                    busy = true,
                    pendingEmail = email,
                    loginChallengeId = null,
                    loginDebugCode = null,
                    loginError = null,
                )
            }
            runCatching { repository.requestEmailCode(email, "fa") }
                .onSuccess { challenge ->
                    _state.update {
                        it.copy(
                            busy = false,
                            loginChallengeId = challenge.id,
                            loginDebugCode = challenge.debugCode,
                        )
                    }
                }
                .onFailure { error ->
                    if (error is CancellationException) return@onFailure
                    _state.update { it.copy(busy = false, loginError = error.loginMessage()) }
                }
        }
    }

    fun verifyEmailCode(code: String) {
        val challengeId = _state.value.loginChallengeId ?: return
        loginJob?.cancel()
        loginJob = viewModelScope.launch {
            _state.update { it.copy(busy = true, loginError = null) }
            runCatching { repository.verifyEmailCode(challengeId, code) }
                .onSuccess { bootstrap ->
                    _state.update {
                        it.withBootstrap(bootstrap).copy(
                            signedIn = true,
                            busy = false,
                            loginChallengeId = null,
                            loginDebugCode = null,
                            loginError = null,
                        )
                    }
                }
                .onFailure { error ->
                    if (error is CancellationException) return@onFailure
                    _state.update { it.copy(busy = false, loginError = error.loginMessage()) }
                }
        }
    }

    fun cancelLoginChallenge() {
        loginJob?.cancel()
        loginJob = null
        _state.update {
            it.copy(
                busy = false,
                loginChallengeId = null,
                loginDebugCode = null,
                loginError = null,
            )
        }
    }

    fun notifyGoogleLoginRequiresConfiguration() = _state.update {
        it.copy(loginError = "ورود با گوگل پس از ثبت شناسهٔ OAuth برنامه فعال می‌شود")
    }

    fun signOut() {
        repository.signOut()
        _state.value = QuickPingUiState(initialized = true)
    }

    fun updateSetting(transform: (org.quickping.app.model.AppSettings) -> org.quickping.app.model.AppSettings) {
        _state.update {
            val updated = transform(it.settings)
            getApplication<Application>()
                .getSharedPreferences("quickping", android.content.Context.MODE_PRIVATE)
                .edit()
                .putBoolean("auto_connect", updated.autoConnect)
                .apply()
            it.copy(settings = updated)
        }
    }

    fun toggleGuardian(id: String) {
        _state.update { state ->
            state.copy(
                guardianCategories = state.guardianCategories.map { category ->
                    if (category.id == id) category.copy(enabled = !category.enabled) else category
                },
            )
        }
    }
}

private fun QuickPingUiState.withBootstrap(payload: BootstrapPayload): QuickPingUiState {
    val service = payload.services.firstOrNull() ?: emptyService
    val servers = payload.serversByService[service.id].orEmpty()
    return copy(
        user = payload.user,
        service = service,
        servers = servers,
        selectedServerId = servers.firstOrNull()?.id.orEmpty(),
        guardianCategories = payload.guardianCategories,
        notifications = payload.notifications,
    )
}

private fun Throwable.loginMessage(): String = when (this) {
    is ApiException -> message
    is java.net.SocketTimeoutException -> "پاسخی از سرور دریافت نشد؛ دوباره تلاش کنید"
    is java.io.IOException -> "اتصال به سرور برقرار نشد"
    else -> "خطای پیش‌بینی‌نشده‌ای رخ داد"
}
