package com.cipherchat.navigation

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Contacts
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.unit.dp
import com.cipherchat.core.designsystem.EmphasizedEasing
import com.cipherchat.core.designsystem.FloatingNavBar
import com.cipherchat.core.designsystem.GlassTopBar
import com.cipherchat.core.designsystem.NavItem
import com.cipherchat.core.designsystem.StatusDot
import com.cipherchat.core.network.ConnectionState
import com.cipherchat.data.SessionState
import com.cipherchat.feature.auth.LoginScreen
import com.cipherchat.feature.auth.OnboardingScreen
import com.cipherchat.feature.auth.RegisterScreen
import com.cipherchat.feature.auth.SplashScreen
import com.cipherchat.feature.chat.ChatScreen
import com.cipherchat.feature.chat.ConversationSettingsScreen
import com.cipherchat.feature.cipher.CipherTab
import com.cipherchat.feature.common.appContainer
import com.cipherchat.feature.contacts.ContactsTab
import com.cipherchat.feature.contacts.NewChatScreen
import com.cipherchat.feature.contacts.RequestsScreen
import com.cipherchat.feature.groups.CreateGroupScreen
import com.cipherchat.feature.groups.GroupsTab
import com.cipherchat.feature.home.ChatsTab
import com.cipherchat.feature.security.VerificationScreen
import com.cipherchat.feature.settings.DeleteAccountScreen
import com.cipherchat.feature.settings.DevicesScreen
import com.cipherchat.feature.settings.PrivacySecurityScreen
import com.cipherchat.feature.settings.ProfileScreen
import com.cipherchat.feature.settings.ServerSettingsScreen
import com.cipherchat.feature.settings.SettingsTab
import kotlinx.coroutines.launch

/** Typed destinations: every route is built through these, never from loose strings at call sites. */
sealed class Dest(val route: String) {
    data object Splash : Dest("splash")
    data object Onboarding : Dest("onboarding")
    data object Login : Dest("login")
    data object Register : Dest("register")
    data object Main : Dest("main")
    data object NewChat : Dest("new_chat")
    data object CreateGroup : Dest("create_group")
    data object Requests : Dest("requests")
    data object Profile : Dest("profile")
    data object Privacy : Dest("privacy")
    data object Devices : Dest("devices")
    data object DeleteAccount : Dest("delete_account")
    data object Server : Dest("server")
    data object Verification : Dest("verification")
    data object Chat : Dest("chat/{id}") {
        const val ARG = "id"
        fun create(id: Long) = "chat/$id"
    }
    data object ChatSettings : Dest("chat_settings/{id}") {
        const val ARG = "id"
        fun create(id: Long) = "chat_settings/$id"
    }
}

private val authRoutes = setOf(Dest.Splash.route, Dest.Onboarding.route, Dest.Login.route, Dest.Register.route, Dest.Server.route)

// Screen transitions: a short fade with a small horizontal drift. Forward moves in from the right, back from the left.
private const val DRIFT = 14
private val enter: AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition = {
    fadeIn(tween(320, delayMillis = 60, easing = EmphasizedEasing)) + slideInHorizontally(tween(420, easing = EmphasizedEasing)) { it / DRIFT }
}
private val exit: AnimatedContentTransitionScope<NavBackStackEntry>.() -> ExitTransition = {
    fadeOut(tween(180)) + slideOutHorizontally(tween(320, easing = EmphasizedEasing)) { -it / (DRIFT * 2) }
}
private val popEnter: AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition = {
    fadeIn(tween(320, delayMillis = 60, easing = EmphasizedEasing)) + slideInHorizontally(tween(420, easing = EmphasizedEasing)) { -it / (DRIFT * 2) }
}
private val popExit: AnimatedContentTransitionScope<NavBackStackEntry>.() -> ExitTransition = {
    fadeOut(tween(180)) + slideOutHorizontally(tween(320, easing = EmphasizedEasing)) { it / DRIFT }
}

@Composable
fun AppNavigation() {
    val nav = rememberNavController()
    val container = appContainer()
    val session by container.auth.session.collectAsState()
    val prefs by container.settings.prefs.collectAsState(initial = null)
    val scope = rememberCoroutineScope()
    val backStack by nav.currentBackStackEntryAsState()
    val current = backStack?.destination?.route

    // Session is the single source of truth for "which half of the app am I in".
    LaunchedEffect(session, current) {
        when {
            session is SessionState.LoggedIn && current != null && current in setOf(Dest.Login.route, Dest.Register.route, Dest.Onboarding.route) ->
                nav.navigate(Dest.Main.route) { popUpTo(0) }
            session is SessionState.LoggedOut && current != null && current !in authRoutes ->
                nav.navigate(Dest.Login.route) { popUpTo(0) }
        }
    }

    NavHost(
        nav,
        startDestination = Dest.Splash.route,
        modifier = Modifier.fillMaxSize(),
        enterTransition = enter,
        exitTransition = exit,
        popEnterTransition = popEnter,
        popExitTransition = popExit,
    ) {
        composable(Dest.Splash.route, exitTransition = { fadeOut(tween(300)) }) {
            SplashScreen(ready = session !is SessionState.Loading && prefs != null) {
                val next = when {
                    prefs?.onboardingDone != true && session !is SessionState.LoggedIn -> Dest.Onboarding.route
                    session is SessionState.LoggedIn -> Dest.Main.route
                    else -> Dest.Login.route
                }
                nav.navigate(next) { popUpTo(Dest.Splash.route) { inclusive = true } }
            }
        }
        composable(Dest.Onboarding.route) {
            OnboardingScreen(onFinish = {
                scope.launch { container.settings.setOnboardingDone() }
                nav.navigate(if (session is SessionState.LoggedIn) Dest.Main.route else Dest.Register.route) { popUpTo(Dest.Onboarding.route) { inclusive = true } }
            })
        }
        composable(Dest.Login.route) {
            LoginScreen(onRegister = { nav.navigate(Dest.Register.route) }, onServerSettings = { nav.navigate(Dest.Server.route) })
        }
        composable(Dest.Register.route) {
            // Register may be the root (first run) or sit on top of Login, so replace it instead of popping.
            RegisterScreen(
                onLogin = { nav.navigate(Dest.Login.route) { popUpTo(Dest.Register.route) { inclusive = true } } },
                onServerSettings = { nav.navigate(Dest.Server.route) },
            )
        }
        composable(Dest.Main.route, enterTransition = { fadeIn(tween(400)) }) { MainScaffold(nav) }

        composable(Dest.NewChat.route) {
            NewChatScreen(
                onBack = { nav.popBackStack() },
                onOpenChat = { id -> nav.navigate(Dest.Chat.create(id)) { popUpTo(Dest.NewChat.route) { inclusive = true } } },
                onNewGroup = { nav.navigate(Dest.CreateGroup.route) },
            )
        }
        composable(Dest.CreateGroup.route) {
            CreateGroupScreen(onBack = { nav.popBackStack() }, onCreated = { id ->
                nav.navigate(Dest.Chat.create(id)) { popUpTo(Dest.Main.route) }
            })
        }
        composable(Dest.Requests.route) { RequestsScreen(onBack = { nav.popBackStack() }) }
        composable(Dest.Chat.route, arguments = listOf(navArgument(Dest.Chat.ARG) { type = NavType.LongType })) { entry ->
            val id = entry.arguments!!.getLong(Dest.Chat.ARG)
            ChatScreen(id, onBack = { nav.popBackStack() }, onSettings = { nav.navigate(Dest.ChatSettings.create(id)) })
        }
        composable(Dest.ChatSettings.route, arguments = listOf(navArgument(Dest.ChatSettings.ARG) { type = NavType.LongType })) { entry ->
            val id = entry.arguments!!.getLong(Dest.ChatSettings.ARG)
            ConversationSettingsScreen(
                id,
                onBack = { nav.popBackStack() },
                onVerification = { nav.navigate(Dest.Verification.route) },
                onLeft = { nav.popBackStack(Dest.Main.route, inclusive = false) },
            )
        }
        composable(Dest.Profile.route) { ProfileScreen(onBack = { nav.popBackStack() }) }
        composable(Dest.Privacy.route) { PrivacySecurityScreen(onBack = { nav.popBackStack() }, onEncryptionStatus = { nav.navigate(Dest.Verification.route) }) }
        composable(Dest.Devices.route) { DevicesScreen(onBack = { nav.popBackStack() }) }
        composable(Dest.DeleteAccount.route) { DeleteAccountScreen(onBack = { nav.popBackStack() }) }
        composable(Dest.Server.route) { ServerSettingsScreen(onBack = { nav.popBackStack() }) }
        composable(Dest.Verification.route) { VerificationScreen(onBack = { nav.popBackStack() }) }
    }
}

private val tabs = listOf(
    NavItem("Chats", Icons.Filled.Chat),
    NavItem("Contacts", Icons.Filled.Contacts),
    NavItem("Groups", Icons.Filled.Groups),
    NavItem("Cipher", Icons.Filled.School),
    NavItem("Settings", Icons.Filled.Settings),
)

@Composable
private fun MainScaffold(nav: NavHostController) {
    var tabIndex by rememberSaveable { mutableIntStateOf(0) }
    val connection by appContainer().socket.state.collectAsState()
    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            GlassTopBar(
                title = tabs[tabIndex].label,
                actions = {
                    Row(Modifier.padding(end = 4.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                        StatusDot(connection == ConnectionState.CONNECTED)
                        Spacer(Modifier.width(6.dp))
                        Text(
                            when (connection) {
                                ConnectionState.CONNECTED -> "Live"
                                ConnectionState.CONNECTING -> "Connecting"
                                ConnectionState.DISCONNECTED -> "Offline"
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
            )
        },
        bottomBar = { FloatingNavBar(tabs, tabIndex, onSelect = { tabIndex = it }) },
    ) { padding ->
        AnimatedContent(
            targetState = tabIndex,
            transitionSpec = {
                val dir = if (targetState > initialState) 1 else -1
                (fadeIn(tween(260, delayMillis = 60, easing = EmphasizedEasing)) + slideInHorizontally(tween(360, easing = EmphasizedEasing)) { dir * it / 10 }) togetherWith
                    (fadeOut(tween(140)) + slideOutHorizontally(tween(260, easing = EmphasizedEasing)) { -dir * it / 14 })
            },
            label = "tabs",
            modifier = Modifier.fillMaxSize(),
        ) { index ->
            Box(Modifier.fillMaxSize()) {
                when (index) {
                    0 -> ChatsTab(padding, onOpenChat = { nav.navigate(Dest.Chat.create(it)) }, onNewChat = { nav.navigate(Dest.NewChat.route) })
                    1 -> ContactsTab(padding, onOpenChat = { nav.navigate(Dest.Chat.create(it)) }, onRequests = { nav.navigate(Dest.Requests.route) })
                    2 -> GroupsTab(padding, onOpenChat = { nav.navigate(Dest.Chat.create(it)) }, onCreate = { nav.navigate(Dest.CreateGroup.route) })
                    3 -> CipherTab(padding)
                    else -> SettingsTab(
                        padding,
                        onProfile = { nav.navigate(Dest.Profile.route) },
                        onPrivacy = { nav.navigate(Dest.Privacy.route) },
                        onDevices = { nav.navigate(Dest.Devices.route) },
                        onServer = { nav.navigate(Dest.Server.route) },
                        onDelete = { nav.navigate(Dest.DeleteAccount.route) },
                    )
                }
            }
        }
    }
}
