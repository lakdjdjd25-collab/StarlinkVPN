package org.quickping.app.state

import android.app.Application
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.os.Build
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.Job
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.net.InetSocketAddress
import java.net.Socket
import kotlin.math.roundToInt
import org.quickping.app.QuickPingApplication
import org.quickping.app.data.network.ApiException
import org.quickping.app.data.network.BootstrapPayload
import org.quickping.app.data.settings.QuickPingSettingsStore
import org.quickping.app.model.InstalledApp
import org.quickping.app.model.ConnectionStatus
import org.quickping.app.vpn.QuickPingVpnService
import org.quickping.app.vpn.ServiceState
import org.quickping.app.vpn.VpnRuntimeStore

class QuickPingViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = (application as QuickPingApplication).repository
    private val settingsStore = QuickPingSettingsStore(application)
    private val runtimeStore = VpnRuntimeStore(application)
    private var loginJob: Job? = null
    private var pingJob: Job? = null
    private var accountJob: Job? = null
    private var restartJob: Job? = null
    private val _state = MutableStateFlow(QuickPingUiState(settings = settingsStore.load()))
    val state: StateFlow<QuickPingUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            _state.update { it.copy(busy = true) }
            val bootstrap = repository.restoreSession()
            _state.update { current ->
                val restored = bootstrap?.let { current.withBootstrap(it, mergeGuardian(it)) } ?: current
                restored.copy(
                    initialized = true,
                    signedIn = bootstrap != null,
                    busy = false,
                )
            }
        }
        viewModelScope.launch {
            QuickPingVpnService.status.collect { serviceStatus ->
                _state.update {
                    it.copy(
                        connectionStatus = when (serviceStatus.state) {
                            ServiceState.Disconnected -> ConnectionStatus.Disconnected
                            ServiceState.Connecting -> ConnectionStatus.Connecting
                            ServiceState.Connected -> ConnectionStatus.Connected
                            ServiceState.Error -> ConnectionStatus.Error
                        },
                        connectionError = serviceStatus.failure?.userMessage,
                        connectionErrorCode = serviceStatus.failure?.code,
                    )
                }
            }
        }
    }

    fun connectVpn() {
        val current = _state.value
        if (current.servers.isEmpty() || current.service.id.isBlank()) {
            _state.update {
                it.copy(
                    connectionStatus = ConnectionStatus.Error,
                    connectionError = "هیچ سرور فعالی برای اتصال وجود ندارد",
                    connectionErrorCode = "no_server",
                )
            }
            return
        }
        viewModelScope.launch {
            _state.update {
                it.copy(
                    connectionStatus = ConnectionStatus.Connecting,
                    connectionError = null,
                    connectionErrorCode = null,
                )
            }
            runCatching {
                val config = repository.runtimeConfig(current.service.id, current.selectedServerId)
                runtimeStore.write(config)
                val application = getApplication<Application>()
                ContextCompat.startForegroundService(
                    application,
                    Intent(application, QuickPingVpnService::class.java)
                        .setAction(QuickPingVpnService.ACTION_CONNECT)
                )
            }.onFailure { error ->
                _state.update { state ->
                    state.copy(
                        connectionStatus = ConnectionStatus.Error,
                        connectionError = error.connectionMessage(),
                        connectionErrorCode = if (error is ApiException) error.code else "config_download",
                    )
                }
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

    fun refreshServerPings() {
        if (!_state.value.settings.autoPing || _state.value.servers.isEmpty() || pingJob?.isActive == true) return
        val targets = _state.value.servers.filter { it.host.isNotBlank() && it.port in 1..65535 }
        if (targets.isEmpty()) return
        pingJob = viewModelScope.launch {
            val semaphore = Semaphore(4)
            val results = targets.map { server ->
                async(Dispatchers.IO) {
                    semaphore.withPermit { server.id to tcpPing(server.host, server.port) }
                }
            }.awaitAll().toMap()
            _state.update { state ->
                state.copy(
                    servers = state.servers.map { server ->
                        if (results.containsKey(server.id)) server.copy(pingMs = results[server.id]) else server
                    },
                )
            }
        }
    }

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
            runCatching { repository.requestEmailCode(email, _state.value.settings.language.code) }
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

    fun loginWithPassword(email: String, password: String) {
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
            runCatching { repository.loginWithPassword(email, password) }
                .onSuccess { bootstrap ->
                    val guardian = mergeGuardian(bootstrap)
                    _state.update {
                        it.withBootstrap(bootstrap, guardian).copy(
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

    fun verifyEmailCode(code: String) {
        val challengeId = _state.value.loginChallengeId ?: return
        loginJob?.cancel()
        loginJob = viewModelScope.launch {
            _state.update { it.copy(busy = true, loginError = null) }
            runCatching { repository.verifyEmailCode(challengeId, code) }
                .onSuccess { bootstrap ->
                    val guardian = mergeGuardian(bootstrap)
                    _state.update {
                        it.withBootstrap(bootstrap, guardian).copy(
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
        it.copy(loginError = "ورود گوگل پس از ثبت شناسهٔ رسمی OAuth فعال می‌شود؛ فعلاً از ایمیل و رمز عبور استفاده کنید")
    }

    fun notifyLoginHelp() = _state.update {
        it.copy(loginError = "ایمیل و رمز عبور حسابی را که در پنل مدیریت ساخته شده وارد کنید")
    }

    fun signOut() {
        disconnectVpn()
        repository.signOut()
        _state.value = QuickPingUiState(initialized = true, settings = settingsStore.load())
    }

    fun requestPasswordChange() {
        accountJob?.cancel()
        accountJob = viewModelScope.launch {
            _state.update {
                it.copy(
                    accountActionBusy = true,
                    accountActionError = null,
                    passwordChangeChallengeId = null,
                    passwordChangeDebugCode = null,
                )
            }
            runCatching { repository.requestPasswordChange() }
                .onSuccess { challenge ->
                    _state.update {
                        it.copy(
                            accountActionBusy = false,
                            passwordChangeChallengeId = challenge.id,
                            passwordChangeDebugCode = challenge.debugCode,
                        )
                    }
                }
                .onFailure { error ->
                    if (error is CancellationException) return@onFailure
                    _state.update {
                        it.copy(accountActionBusy = false, accountActionError = error.accountMessage())
                    }
                }
        }
    }

    fun confirmPasswordChange(code: String, newPassword: String) {
        val challengeId = _state.value.passwordChangeChallengeId ?: return
        accountJob?.cancel()
        accountJob = viewModelScope.launch {
            _state.update { it.copy(accountActionBusy = true, accountActionError = null) }
            runCatching { repository.confirmPasswordChange(challengeId, code, newPassword) }
                .onSuccess {
                    disconnectVpn()
                    repository.signOut()
                    _state.value = QuickPingUiState(
                        initialized = true,
                        settings = settingsStore.load(),
                        loginError = "گذرواژه با موفقیت تغییر کرد؛ دوباره وارد شوید",
                    )
                }
                .onFailure { error ->
                    if (error is CancellationException) return@onFailure
                    _state.update {
                        it.copy(accountActionBusy = false, accountActionError = error.accountMessage())
                    }
                }
        }
    }

    fun deleteAccount(password: String) {
        accountJob?.cancel()
        accountJob = viewModelScope.launch {
            _state.update { it.copy(accountActionBusy = true, accountActionError = null) }
            runCatching { repository.deleteAccount(password) }
                .onSuccess {
                    disconnectVpn()
                    repository.signOut()
                    _state.value = QuickPingUiState(
                        initialized = true,
                        settings = settingsStore.load(),
                        loginError = "حساب کاربری حذف شد",
                    )
                }
                .onFailure { error ->
                    if (error is CancellationException) return@onFailure
                    _state.update {
                        it.copy(accountActionBusy = false, accountActionError = error.accountMessage())
                    }
                }
        }
    }

    fun clearAccountAction() {
        accountJob?.cancel()
        accountJob = null
        _state.update {
            it.copy(
                accountActionBusy = false,
                accountActionError = null,
                passwordChangeChallengeId = null,
                passwordChangeDebugCode = null,
            )
        }
    }

    fun updateSetting(transform: (org.quickping.app.model.AppSettings) -> org.quickping.app.model.AppSettings) {
        val previous = _state.value.settings
        val candidate = transform(previous)
        val updated = if (!candidate.splitTunnelingEnabled && !candidate.rememberSplitTunnelSettings) {
            candidate.copy(splitTunnelPackages = emptySet(), splitTunnelAddresses = emptyList())
        } else {
            candidate
        }
        settingsStore.save(updated)
        _state.update { it.copy(settings = updated) }
        if (previous.vpnRuntimeFingerprint() != updated.vpnRuntimeFingerprint()) restartVpnIfActive()
    }

    fun resetSettings() {
        val previous = _state.value.settings
        val reset = settingsStore.reset()
        _state.update { state ->
            val guardian = state.guardianCategories.map { category ->
                category.copy(enabled = category.id in DEFAULT_GUARDIAN_CATEGORIES)
            }
            settingsStore.saveGuardian(guardian)
            state.copy(settings = reset, guardianCategories = guardian)
        }
        if (previous.vpnRuntimeFingerprint() != reset.vpnRuntimeFingerprint()) restartVpnIfActive()
    }

    fun toggleGuardian(id: String) {
        _state.update { state ->
            val updated = state.guardianCategories.map { category ->
                if (category.id == id) category.copy(enabled = !category.enabled) else category
            }
            settingsStore.saveGuardian(updated)
            state.copy(guardianCategories = updated)
        }
        restartVpnIfActive()
    }

    fun loadInstalledApps() {
        if (_state.value.loadingInstalledApps || _state.value.installedApps.isNotEmpty()) return
        viewModelScope.launch {
            _state.update { it.copy(loadingInstalledApps = true) }
            val apps = withContext(Dispatchers.IO) {
                val manager = getApplication<Application>().packageManager
                val installed = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    manager.getInstalledApplications(android.content.pm.PackageManager.ApplicationInfoFlags.of(0))
                } else {
                    @Suppress("DEPRECATION")
                    manager.getInstalledApplications(0)
                }
                val selected = _state.value.settings.splitTunnelPackages
                installed.asSequence()
                    .filter { info -> manager.getLaunchIntentForPackage(info.packageName) != null || info.packageName in selected }
                    .filterNot { it.packageName == getApplication<Application>().packageName }
                    .map { info ->
                        InstalledApp(
                            packageName = info.packageName,
                            label = manager.getApplicationLabel(info).toString().ifBlank { info.packageName },
                            systemApp = info.flags and ApplicationInfo.FLAG_SYSTEM != 0,
                        )
                    }
                    .distinctBy(InstalledApp::packageName)
                    .sortedWith { left, right -> left.label.compareTo(right.label, ignoreCase = true) }
                    .toList()
            }
            _state.update { it.copy(installedApps = apps, loadingInstalledApps = false) }
        }
    }

    private fun mergeGuardian(payload: BootstrapPayload): List<org.quickping.app.model.GuardianCategory> {
        val merged = settingsStore.mergeGuardian(payload.guardianCategories)
        settingsStore.saveGuardian(merged)
        return merged
    }

    private fun restartVpnIfActive() {
        if (_state.value.connectionStatus !in setOf(ConnectionStatus.Connected, ConnectionStatus.Connecting)) return
        restartJob?.cancel()
        restartJob = viewModelScope.launch {
            disconnectVpn()
            withTimeoutOrNull(5_000) {
                QuickPingVpnService.status.first { status -> status.state == ServiceState.Disconnected }
            }
            delay(150)
            connectVpn()
        }
    }

    private companion object {
        val DEFAULT_GUARDIAN_CATEGORIES = setOf("malware", "ads", "youtube", "phishing")
    }
}

private fun org.quickping.app.model.AppSettings.vpnRuntimeFingerprint(): List<Any> = listOf(
    shareHotspot,
    proxyModeEnabled,
    localProxyEnabled,
    splitTunnelingEnabled,
    splitTunnelMode,
    splitTunnelPackages,
    splitTunnelAddresses,
    blockIrDomains,
    guardianEnabled,
    dnsProvider,
    proxyPort,
    reconnectOnNetworkChange,
    strictRoute,
    ipv6Enabled,
    mtu,
)

private fun tcpPing(host: String, port: Int): Int? = runCatching {
    val started = System.nanoTime()
    Socket().use { socket -> socket.connect(InetSocketAddress(host, port), 2_500) }
    ((System.nanoTime() - started) / 1_000_000.0).roundToInt().coerceAtLeast(1)
}.getOrNull()

private fun QuickPingUiState.withBootstrap(
    payload: BootstrapPayload,
    guardian: List<org.quickping.app.model.GuardianCategory>,
): QuickPingUiState {
    val service = payload.services.firstOrNull() ?: emptyService
    val servers = payload.serversByService[service.id].orEmpty()
    return copy(
        user = payload.user,
        service = service,
        servers = servers,
        selectedServerId = servers.firstOrNull()?.id.orEmpty(),
        guardianCategories = guardian,
        notifications = payload.notifications,
        release = payload.release,
    )
}

private fun Throwable.loginMessage(): String = when (this) {
    is ApiException -> message
    is java.net.SocketTimeoutException -> "پاسخی از سرور دریافت نشد؛ دوباره تلاش کنید"
    is java.io.IOException -> "اتصال به سرور برقرار نشد"
    else -> "خطای پیش‌بینی‌نشده‌ای رخ داد"
}

private fun Throwable.connectionMessage(): String = when (this) {
    is ApiException -> when (code) {
        "node_unavailable" -> "سرور انتخاب‌شده موقتاً در دسترس نیست"
        "service_not_found" -> "سرویس فعال پیدا نشد؛ حساب را دوباره همگام کنید"
        "quota_exhausted" -> "حجم سرویس به پایان رسیده است"
        "invalid_config" -> "پیکربندی سرور ناقص یا ناسازگار است"
        else -> message.ifBlank { "دریافت پیکربندی سرور ناموفق بود" }
    }
    is java.net.SocketTimeoutException -> "دریافت پیکربندی سرور بیش از حد طول کشید"
    is java.io.IOException -> "ارتباط امن با پنل مدیریت برقرار نشد"
    is IllegalArgumentException -> "پیکربندی دریافت‌شده معتبر نیست"
    else -> "آماده‌سازی اتصال ناموفق بود"
}

private fun Throwable.accountMessage(): String = when (this) {
    is ApiException -> when (code) {
        "invalid_code" -> "کد تأیید نامعتبر یا منقضی شده است"
        "invalid_password" -> "گذرواژه صحیح نیست"
        "try_later" -> "برای دریافت کد جدید کمی صبر کنید"
        "email_unavailable" -> "ارسال کد ایمیل فعلاً امکان‌پذیر نیست"
        "invalid_input" -> message
        else -> message.ifBlank { "انجام عملیات حساب ناموفق بود" }
    }
    is java.net.SocketTimeoutException -> "پاسخی از سرور دریافت نشد"
    is java.io.IOException -> "ارتباط امن با پنل مدیریت برقرار نشد"
    else -> "انجام عملیات حساب ناموفق بود"
}
