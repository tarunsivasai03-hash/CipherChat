package com.cipherchat.feature.auth

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cipherchat.core.designsystem.AppTextField
import com.cipherchat.core.designsystem.AppTheme
import com.cipherchat.core.designsystem.BubbleMark
import com.cipherchat.core.designsystem.EmphasizedEasing
import com.cipherchat.core.designsystem.Eyebrow
import com.cipherchat.core.designsystem.FormError
import com.cipherchat.core.designsystem.GlassLevel
import com.cipherchat.core.designsystem.LocalMotionEnabled
import com.cipherchat.core.designsystem.PrimaryButton
import com.cipherchat.core.designsystem.ScrambleText
import com.cipherchat.core.designsystem.Spacing
import com.cipherchat.core.designsystem.appear
import com.cipherchat.core.designsystem.glass
import com.cipherchat.feature.common.appViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** The mark assembles itself: the second bubble swings 180 degrees into place (the ROT13 idea), then the name fades in. */
@Composable
fun SplashScreen(ready: Boolean, onReady: () -> Unit) {
    val motion = LocalMotionEnabled.current
    val rotation = remember { Animatable(if (motion) 0f else 180f) }
    val label = remember { Animatable(if (motion) 0f else 1f) }
    LaunchedEffect(Unit) {
        if (motion) {
            rotation.animateTo(180f, tween(1000, easing = EmphasizedEasing))
            label.animateTo(1f, tween(450))
        }
    }
    LaunchedEffect(ready) {
        if (ready) {
            delay(if (motion) 1500 else 0)
            onReady()
        }
    }
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            BubbleMark(Modifier.size(120.dp), MaterialTheme.colorScheme.onSurface, rotation.value)
            Spacer(Modifier.height(Spacing.lg))
            Text(
                "CipherChat",
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.graphicsLayer { alpha = label.value; translationY = (1f - label.value) * 10.dp.toPx() },
            )
        }
    }
}

private data class OnboardingPage(val eyebrow: String, val title: String, val body: String)

private val onboardingPages = listOf(
    OnboardingPage("Secret ciphers", "Scramble messages for fun.", "Caesar, Vigenère, ROT13, substitution and emoji codes. Classroom ciphers: great for puzzles, never for secrets."),
    OnboardingPage("Real conversations", "Chat with the people you add.", "Contacts, one-to-one chats and groups, delivered in real time, in plain text or cipher-coded."),
    OnboardingPage("Honest privacy", "No pretending.", "End-to-end encrypted chat isn't available yet, and CipherChat will never claim otherwise. Messages are protected in transit; the server can read them."),
)

@Composable
fun OnboardingScreen(onFinish: () -> Unit) {
    val pager = rememberPagerState(pageCount = { onboardingPages.size })
    val scope = rememberCoroutineScope()
    Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(horizontal = Spacing.lg, vertical = Spacing.md)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            BubbleMark(Modifier.size(32.dp), MaterialTheme.colorScheme.onSurface)
            TextButton(onClick = onFinish) { Text("Skip", color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
        HorizontalPager(pager, Modifier.weight(1f)) { i ->
            val page = onboardingPages[i]
            val offset = pager.getOffsetDistanceInPages(i)
            Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center) {
                // Parallax: the illustration drifts faster than the text as the page is dragged.
                Box(
                    Modifier.fillMaxWidth().height(240.dp).graphicsLayer { translationX = offset * size.width * 0.45f; alpha = 1f - kotlin.math.abs(offset) * 0.8f }
                        .glass(MaterialTheme.shapes.extraLarge, GlassLevel.Strong),
                    contentAlignment = Alignment.Center,
                ) { OnboardingArt(i, active = pager.currentPage == i) }
                Spacer(Modifier.height(Spacing.xl))
                Column(Modifier.graphicsLayer { translationX = offset * size.width * 0.15f; alpha = 1f - kotlin.math.abs(offset) }) {
                    Eyebrow(page.eyebrow)
                    Spacer(Modifier.height(Spacing.sm))
                    Text(page.title, style = MaterialTheme.typography.displayLarge.copy(fontSize = 38.sp, lineHeight = 42.sp))
                    Spacer(Modifier.height(Spacing.md))
                    Text(page.body, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        Row(Modifier.padding(bottom = Spacing.lg), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            repeat(onboardingPages.size) { i ->
                val width by animateDpAsState(if (pager.currentPage == i) 28.dp else 8.dp, tween(300, easing = EmphasizedEasing), label = "dot")
                Box(Modifier.height(4.dp).size(width, 4.dp).background(if (pager.currentPage == i) MaterialTheme.colorScheme.primary else AppTheme.colors.hairline, RoundedCornerShape(2.dp)))
            }
        }
        val last = pager.currentPage == onboardingPages.lastIndex
        PrimaryButton(if (last) "Get started" else "Next", onClick = {
            if (last) onFinish() else scope.launch { pager.animateScrollToPage(pager.currentPage + 1, animationSpec = tween(520, easing = EmphasizedEasing)) }
        })
    }
}

@Composable
private fun OnboardingArt(page: Int, active: Boolean) {
    when (page) {
        0 -> {
            // A live demo: the word flips between plain text and its Caesar +3 form.
            var showCipher by remember { mutableStateOf(false) }
            LaunchedEffect(active) {
                while (active) {
                    delay(2200)
                    showCipher = !showCipher
                }
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Eyebrow(if (showCipher) "Caesar · shift 3" else "Plain text")
                Spacer(Modifier.height(Spacing.sm))
                ScrambleText(if (showCipher) "Khoor, Zruog" else "Hello, World", MaterialTheme.typography.displayLarge.copy(fontSize = 34.sp))
            }
        }
        1 -> {
            val rot = remember { Animatable(0f) }
            LaunchedEffect(active) {
                if (active) {
                    rot.snapTo(0f)
                    rot.animateTo(180f, tween(1400, easing = EmphasizedEasing))
                }
            }
            BubbleMark(Modifier.size(132.dp), MaterialTheme.colorScheme.onSurface, rot.value)
        }
        else -> Icon(Icons.Filled.LockOpen, contentDescription = null, tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(64.dp))
    }
}

@Composable
fun LoginScreen(onRegister: () -> Unit, onServerSettings: () -> Unit) {
    val vm = appViewModel { LoginViewModel(it.auth) }
    val s by vm.state.collectAsState()
    AuthScaffold("Welcome back", "Sign in to continue", "New here? Create an account", onRegister, onServerSettings) {
        AppTextField(s.username, vm::onUsername, "Username", enabled = !s.loading, isError = s.usernameError != null, supportingText = s.usernameError)
        AppTextField(s.password, vm::onPassword, "Password", password = true, enabled = !s.loading)
        FormError(s.error)
        PrimaryButton("Sign in", vm::submit, loading = s.loading)
    }
}

@Composable
fun RegisterScreen(onLogin: () -> Unit, onServerSettings: () -> Unit) {
    val vm = appViewModel { RegisterViewModel(it.auth) }
    val s by vm.state.collectAsState()
    AuthScaffold("Create your account", "Pick a unique username", "Already have an account? Sign in", onLogin, onServerSettings) {
        AppTextField(s.username, vm::onUsername, "Username", enabled = !s.loading, isError = s.usernameError != null, supportingText = s.usernameError ?: "Letters, digits, _ and . · 3–32 characters")
        AppTextField(s.displayName, vm::onDisplayName, "Display name (optional)", enabled = !s.loading)
        AppTextField(s.password, vm::onPassword, "Password", password = true, enabled = !s.loading, isError = s.passwordError != null, supportingText = s.passwordError ?: "At least ${AuthValidation.MIN_PASSWORD} characters")
        AppTextField(s.confirm, vm::onConfirm, "Confirm password", password = true, enabled = !s.loading, isError = s.confirmError != null, supportingText = s.confirmError)
        FormError(s.error)
        PrimaryButton("Create account", vm::submit, loading = s.loading)
    }
}

@Composable
private fun AuthScaffold(
    title: String,
    subtitle: String,
    switchText: String,
    onSwitch: () -> Unit,
    onServerSettings: () -> Unit,
    form: @Composable ColumnScope.() -> Unit,
) {
    Column(
        Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().imePadding().verticalScroll(rememberScrollState()).padding(horizontal = Spacing.lg),
    ) {
        Spacer(Modifier.height(Spacing.xl))
        BubbleMark(Modifier.size(44.dp).appear(0), MaterialTheme.colorScheme.onSurface)
        Spacer(Modifier.height(Spacing.lg))
        Text(title, style = MaterialTheme.typography.displayLarge.copy(fontSize = 40.sp, lineHeight = 44.sp), modifier = Modifier.appear(1))
        Spacer(Modifier.height(Spacing.sm))
        Text(subtitle, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.appear(2))
        Spacer(Modifier.height(Spacing.lg))
        Column(
            Modifier.fillMaxWidth().appear(3).glass(MaterialTheme.shapes.large, GlassLevel.Strong).padding(Spacing.md),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) { form() }
        Spacer(Modifier.height(Spacing.sm))
        TextButton(onClick = onSwitch, Modifier.align(Alignment.CenterHorizontally).appear(4)) { Text(switchText, color = MaterialTheme.colorScheme.onSurface) }
        Spacer(Modifier.weight(1f, fill = false).height(Spacing.lg))
        TextButton(onClick = onServerSettings, Modifier.align(Alignment.CenterHorizontally)) {
            Text("Server settings", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelMedium)
        }
    }
}
