package com.cipherchat

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.cipherchat.core.designsystem.CipherChatTheme
import com.cipherchat.core.designsystem.GlassBackground
import com.cipherchat.core.designsystem.LocalMotionEnabled
import com.cipherchat.core.designsystem.rememberMotionEnabled
import androidx.compose.runtime.CompositionLocalProvider
import com.cipherchat.core.designsystem.BubbleMark
import com.cipherchat.core.designsystem.PrimaryButton
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.navigationBarsPadding
import com.cipherchat.core.security.BiometricHelper
import com.cipherchat.data.SessionState
import com.cipherchat.data.ThemeMode
import com.cipherchat.navigation.AppNavigation
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

class MainActivity : FragmentActivity() {
    /** True while the app lock is engaged. Re-engaged whenever the app leaves the foreground. */
    private val locked = MutableStateFlow(false)
    private var lockEnabled = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val container = (application as CipherChatApp).container

        // Apply user privacy preferences that need window-level flags.
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.CREATED) {
                container.settings.prefs.collect { prefs ->
                    // System bar icons must contrast with the *app* theme, not the system theme.
                    val night = (resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) ==
                        android.content.res.Configuration.UI_MODE_NIGHT_YES
                    val dark = prefs.themeMode == ThemeMode.DARK || (prefs.themeMode == ThemeMode.SYSTEM && night)
                    val bar = if (dark) SystemBarStyle.dark(android.graphics.Color.TRANSPARENT) else SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT)
                    enableEdgeToEdge(statusBarStyle = bar, navigationBarStyle = bar)
                    lockEnabled = prefs.biometricLock
                    if (prefs.blockScreenshots) window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
                    else window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
                }
            }
        }
        // Cold start with the lock on: begin locked (only matters when someone is signed in).
        lifecycleScope.launch {
            val prefs = container.settings.current()
            if (prefs.biometricLock && savedInstanceState == null) locked.value = true
        }

        setContent {
            val prefs by container.settings.prefs.collectAsState(initial = null)
            val session by container.auth.session.collectAsState()
            val isLocked by locked.collectAsState()
            val motion = rememberMotionEnabled()
            CipherChatTheme(prefs?.themeMode ?: ThemeMode.SYSTEM) {
                CompositionLocalProvider(LocalMotionEnabled provides motion) {
                    // One backdrop for the whole app: screens are transparent so the glass layers always have colour to refract.
                    GlassBackground {
                        if (isLocked && session is SessionState.LoggedIn) LockScreen(onUnlock = ::promptUnlock) else AppNavigation()
                    }
                }
            }
        }
    }

    override fun onStop() {
        super.onStop()
        if (lockEnabled && !isChangingConfigurations) locked.value = true
    }

    private fun promptUnlock() {
        BiometricHelper.prompt(
            this,
            title = "Unlock CipherChat",
            subtitle = "Confirm it's you",
            onSuccess = { locked.value = false },
            onFailure = { /* stay locked; the user can try again with the button */ },
        )
    }
}

@Composable
private fun LockScreen(onUnlock: () -> Unit) {
    LaunchedEffect(Unit) { onUnlock() }
    Column(
        Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        BubbleMark(Modifier.size(88.dp), MaterialTheme.colorScheme.onSurface)
        Text("CipherChat is locked", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(top = 24.dp, bottom = 8.dp))
        Text("Confirm it's you to continue.", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 24.dp))
        PrimaryButton("Unlock", onUnlock)
    }
}
