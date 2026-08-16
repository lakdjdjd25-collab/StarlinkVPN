package org.quickping.app.navigation

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.net.VpnService
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import org.quickping.app.core.design.QuickPingTheme
import org.quickping.app.model.ConnectionStatus
import org.quickping.app.state.QuickPingViewModel
import org.quickping.app.ui.screens.AccountScreenWithSignOutConfirmation
import org.quickping.app.ui.screens.GuardianScreen
import org.quickping.app.ui.screens.HomeScreen
import org.quickping.app.ui.screens.NotificationsScreen
import org.quickping.app.ui.screens.PolishedLoginScreen
import org.quickping.app.ui.screens.ServicesScreen
import org.quickping.app.ui.screens.SettingsScreen
import org.quickping.app.ui.screens.SplashScreen
import org.quickping.app.ui.screens.SplitTunnelingScreen
import org.quickping.app.ui.screens.VersionScreen
import kotlin.coroutines.resume

private fun openTelegramManager(context: Context, username: String) {
    val safeUsername = username.trim().removePrefix("@").takeIf {
        it.matches(Regex("[A-Za-z0-9_]{5,32}"))
    } ?: "Folwn"
    val appUri = Uri.parse("tg://resolve?domain=$safeUsername")
    val webUri = Uri.parse("https://t.me/$safeUsername")
    try {
        context.startActivity(Intent(Intent.ACTION_VIEW, appUri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (_: ActivityNotFoundException) {
        context.startActivity(Intent(Intent.ACTION_VIEW, webUri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}

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

private suspend fun Lifecycle.awaitResumed() {
    if (currentState.isAtLeast(Lifecycle.State.RESUMED)) return
    suspendCancellableCoroutine { continuation ->
        lateinit var observer: LifecycleEventObserver
        observer = LifecycleEventObserver { source, _ ->
            if (source.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED) && continuation.isActive) {
                source.lifecycle.removeObserver(observer)
                continuation.resume(Unit)
            }
        }
        continuation.invokeOnCancellation { removeObserver(observer) }
        addObserver(observer)
    }
}

/**
 * Top-level pages all have Home as their canonical parent. If a user taps a new top-level action
 * while the previous page is still popping, first collapse the stack to Home, then wait for the
 * real Home back-stack entry to be RESUMED before pushing the requested destination. This avoids
 * transparent/blank entries without introducing arbitrary timing delays.
 */
private suspend fun NavHostController.openTopLevelWhenHomeIsReady(route: String) {
    if (currentDestination?.route == route) return

    if (currentDestination?.route != Route.Home) {
        val returnedHome = popBackStack(Route.Home, inclusive = false)
        if (!returnedHome && currentDestination?.route != Route.Home) {
            navigate(Route.Home) {
                launchSingleTop = true
            }
        }
    }

    val homeEntry = runCatching { getBackStackEntry(Route.Home) }.getOrNull() ?: return
    homeEntry.lifecycle.awaitResumed()
    if (currentBackStackEntry == homeEntry && currentDestination?.route == Route.Home) {
        navigate(route) {
            popUpTo(Route.Home) { inclusive = false }
            launchSingleTop = true
        }
    }
}

private fun NavHostController.returnToHomeSafely() {
    if (currentDestination?.route == Route.Home) return
    val returnedHome = popBackStack(Route.Home, inclusive = false)
    if (!returnedHome && currentDestination?.route != Route.Home) {
        navigate(Route.Home) {
            launchSingleTop = true
        }
    }
}

@Composable
fun QuickPingApp(
    navController: NavHostController = rememberNavController(),
    quickPingViewModel: QuickPingViewModel = viewModel(),
) {
    val state by quickPingViewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val navigationScope = rememberCoroutineScope()
    var topLevelNavigationJob by remember { mutableStateOf<Job?>(null) }
    val openTopLevel: (String) -> Unit = { route ->
        topLevelNavigationJob?.cancel()
        topLevelNavigationJob = navigationScope.launch {
            navController.openTopLevelWhenHomeIsReady(route)
        }
    }
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
            topLevelNavigationJob?.cancel()
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
                PolishedLoginScreen(
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
            composable(
                route = Route.Home,
                enterTransition = { EnterTransition.None },
                exitTransition = { ExitTransition.None },
                popEnterTransition = { EnterTransition.None },
                popExitTransition = { ExitTransition.None },
            ) {
                LaunchedEffect(Unit) {
                    quickPingViewModel.refreshAccountState()
                    while (true) {
                        delay(60_000)
                        quickPingViewModel.refreshAccountState()
                    }
                }
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
                    onSettings = { openTopLevel(Route.Settings) },
                    onAccount = { openTopLevel(Route.Account) },
                    onNotifications = { openTopLevel(Route.Notifications) },
                    onUpgrade = { openTelegramManager(context, state.management.telegramUsername) },
                )
            }
            composable(
                route = Route.Settings,
                enterTransition = { EnterTransition.None },
                exitTransition = { ExitTransition.None },
                popEnterTransition = { EnterTransition.None },
                popExitTransition = { ExitTransition.None },
            ) {
                SettingsScreen(
                    settings = state.settings,
                    onUpdateSettings = quickPingViewModel::updateSetting,
                    onResetSettings = quickPingViewModel::resetSettings,
                    onBack = navController::returnToHomeSafely,
                    onSplitTunneling = { navController.navigate(Route.SplitTunneling) },
                    onGuardian = { navController.navigate(Route.Guardian) },
                    onAccount = { openTopLevel(Route.Account) },
                    onNotifications = { openTopLevel(Route.Notifications) },
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
            composable(
                route = Route.Account,
                enterTransition = { EnterTransition.None },
                exitTransition = { ExitTransition.None },
                popEnterTransition = { EnterTransition.None },
                popExitTransition = { ExitTransition.None },
            ) {
                LaunchedEffect(Unit) { quickPingViewModel.refreshAccountState() }
                AccountScreenWithSignOutConfirmation(
                    user = state.user,
                    service = state.service,
                    busy = state.accountActionBusy,
                    error = state.accountActionError,
                    onConfirmPasswordChange = quickPingViewModel::changePassword,
                    onDeleteAccount = quickPingViewModel::deleteAccount,
                    onClearAction = quickPingViewModel::clearAccountAction,
                    onBack = navController::returnToHomeSafely,
                    onSignOut = quickPingViewModel::signOut,
                    onServices = { navController.navigate(Route.Services) },
                )
            }
            composable(Route.Services) {
                ServicesScreen(
                    service = state.service,
                    onBack = navController::popBackStack,
                )
            }
            composable(
                route = Route.Notifications,
                enterTransition = { EnterTransition.None },
                exitTransition = { ExitTransition.None },
                popEnterTransition = { EnterTransition.None },
                popExitTransition = { ExitTransition.None },
            ) {
                LaunchedEffect(Unit) { quickPingViewModel.refreshNotificationsAndMarkRead() }
                NotificationsScreen(
                    notifications = state.notifications,
                    onBack = navController::returnToHomeSafely,
                )
            }
            composable(Route.Version) {
                VersionScreen(release = state.release, onBack = navController::popBackStack)
            }
        }
    }
}
