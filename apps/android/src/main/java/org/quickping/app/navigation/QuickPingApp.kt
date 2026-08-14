package org.quickping.app.navigation

import android.app.Activity
import android.net.VpnService
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import org.quickping.app.BuildConfig
import org.quickping.app.core.design.QuickPingTheme
import org.quickping.app.model.ConnectionStatus
import org.quickping.app.state.QuickPingViewModel
import org.quickping.app.ui.screens.AccountScreenWithSignOutConfirmation
import org.quickping.app.ui.screens.GuardianScreen
import org.quickping.app.ui.screens.HomeScreen
import org.quickping.app.ui.screens.NotificationsScreen
import org.quickping.app.ui.screens.ReferenceLoginScreen
import org.quickping.app.ui.screens.ServicesCatalogScreen
import org.quickping.app.ui.screens.SettingsScreen
import org.quickping.app.ui.screens.SplashScreen
import org.quickping.app.ui.screens.SplitTunnelingScreen
import org.quickping.app.ui.screens.VersionScreen

private object Route {
    const val Splash = "splash"
    const val Login = "login"
    const val Home = "home"
    const val Settings = "settings"
    const val Guardian = "settings/guardian"
    const val SplitTunneling = "settings/split-tunneling"
    const val Account = "account"
    const val Services = "account/services"
    const val Notifications = "notifications"
    const val Version = "update/latest"
}

@Composable
fun QuickPingApp(
    navController: NavHostController = rememberNavController(),
    quickPingViewModel: QuickPingViewModel = viewModel(),
) {
    val state by quickPingViewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val mandatoryUpdate = state.release?.let { release ->
        release.versionCode > BuildConfig.VERSION_CODE &&
            (release.mandatory || BuildConfig.VERSION_CODE < release.minimumVersionCode)
    } == true

    val vpnPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) quickPingViewModel.connectVpn()
    }
    LaunchedEffect(state.initialized, state.signedIn) {
        val currentRoute = navController.currentDestination?.route
        if (
            state.initialized &&
            !state.signedIn &&
            currentRoute != null &&
            currentRoute !in setOf(Route.Splash, Route.Login)
        ) {
            navController.navigate(Route.Login) {
                popUpTo(Route.Home) { inclusive = true }
                launchSingleTop = true
            }
        }
    }
    val onToggleVpn = {
        if (state.connectionStatus in setOf(ConnectionStatus.Connected, ConnectionStatus.Connecting)) {
            quickPingViewModel.disconnectVpn()
        } else {
            val permissionIntent = if (state.settings.proxyModeEnabled) null else VpnService.prepare(context)
            if (permissionIntent == null) {
                quickPingViewModel.connectVpn()
            } else {
                vpnPermissionLauncher.launch(permissionIntent)
            }
        }
    }

    QuickPingTheme(languageCode = state.settings.language.code) {
        if (mandatoryUpdate) {
            VersionScreen(
                release = state.release,
                onBack = null,
            )
        } else {
            NavHost(
                navController = navController,
                startDestination = Route.Splash,
            ) {
                composable(Route.Splash) {
                    SplashScreen(ready = state.initialized) {
                        navController.navigate(if (state.signedIn) Route.Home else Route.Login) {
                            popUpTo(Route.Splash) { inclusive = true }
                        }
                    }
                }
                composable(Route.Login) {
                    LaunchedEffect(state.signedIn) {
                        if (state.signedIn) {
                            navController.navigate(Route.Home) {
                                popUpTo(Route.Login) { inclusive = true }
                            }
                        }
                    }
                    ReferenceLoginScreen(
                        language = state.settings.language,
                        busy = state.busy,
                        challengeId = state.loginChallengeId,
                        debugCode = state.loginDebugCode,
                        error = state.loginError,
                        onLanguageChange = { language ->
                            quickPingViewModel.updateSetting { it.copy(language = language) }
                        },
                        onRequestEmailCode = quickPingViewModel::requestEmailCode,
                        onPasswordLogin = quickPingViewModel::loginWithPassword,
                        onVerifyCode = quickPingViewModel::verifyEmailCode,
                        onCancelChallenge = quickPingViewModel::cancelLoginChallenge,
                        onGoogleRequested = {
                            val activity = context as? Activity
                            if (activity != null) {
                                quickPingViewModel.loginWithGoogle(activity)
                            } else {
                                quickPingViewModel.notifyGoogleLoginRequiresConfiguration()
                            }
                        },
                        onHelpRequested = quickPingViewModel::notifyLoginHelp,
                    )
                }
                composable(Route.Home) {
                    LaunchedEffect(
                        state.settings.autoPing,
                        state.servers.joinToString(separator = ",", transform = { it.id }),
                    ) {
                        quickPingViewModel.refreshServerPings()
                    }
                    HomeScreen(
                        state = state,
                        onToggleConnection = onToggleVpn,
                        onSelectServer = quickPingViewModel::selectServer,
                        onSettings = { navController.navigate(Route.Settings) },
                        onAccount = { navController.navigate(Route.Account) },
                        onNotifications = { navController.navigate(Route.Notifications) },
                    )
                }
                composable(Route.Settings) {
                    SettingsScreen(
                        settings = state.settings,
                        onUpdateSettings = quickPingViewModel::updateSetting,
                        onResetSettings = quickPingViewModel::resetSettings,
                        onBack = navController::popBackStack,
                        onSplitTunneling = { navController.navigate(Route.SplitTunneling) },
                        onGuardian = { navController.navigate(Route.Guardian) },
                        onAccount = { navController.navigate(Route.Account) },
                        onNotifications = { navController.navigate(Route.Notifications) },
                        onVersion = { navController.navigate(Route.Version) },
                    )
                }
                composable(Route.Guardian) {
                    GuardianScreen(
                        enabled = state.settings.guardianEnabled,
                        categories = state.guardianCategories,
                        onEnabledChange = { value ->
                            quickPingViewModel.updateSetting { it.copy(guardianEnabled = value) }
                        },
                        onToggleCategory = quickPingViewModel::toggleGuardian,
                        onBack = navController::popBackStack,
                    )
                }
                composable(Route.SplitTunneling) {
                    LaunchedEffect(Unit) { quickPingViewModel.loadInstalledApps() }
                    SplitTunnelingScreen(
                        settings = state.settings,
                        installedApps = state.installedApps,
                        loadingApps = state.loadingInstalledApps,
                        onUpdateSettings = quickPingViewModel::updateSetting,
                        onBack = navController::popBackStack,
                    )
                }
                composable(Route.Account) {
                    AccountScreenWithSignOutConfirmation(
                        user = state.user,
                        service = state.service,
                        busy = state.accountActionBusy,
                        error = state.accountActionError,
                        passwordChallengeId = state.passwordChangeChallengeId,
                        passwordDebugCode = state.passwordChangeDebugCode,
                        onRequestPasswordCode = quickPingViewModel::requestPasswordChange,
                        onConfirmPasswordChange = quickPingViewModel::confirmPasswordChange,
                        onDeleteAccount = quickPingViewModel::deleteAccount,
                        onClearAction = quickPingViewModel::clearAccountAction,
                        onBack = navController::popBackStack,
                        onSignOut = quickPingViewModel::signOut,
                        onServices = { navController.navigate(Route.Services) },
                    )
                }
                composable(Route.Services) {
                    ServicesCatalogScreen(
                        services = state.services,
                        currentServiceId = state.service.id,
                        onSelectService = quickPingViewModel::selectService,
                        onBack = navController::popBackStack,
                    )
                }
                composable(Route.Notifications) {
                    NotificationsScreen(
                        notifications = state.notifications,
                        onBack = navController::popBackStack,
                    )
                }
                composable(Route.Version) {
                    VersionScreen(release = state.release, onBack = navController::popBackStack)
                }
            }
        }
    }
}
