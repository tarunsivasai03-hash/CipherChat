package com.cipherchat.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.cipherchat.core.designsystem.AppTextField
import com.cipherchat.core.designsystem.AppTheme
import com.cipherchat.core.designsystem.Avatar
import com.cipherchat.core.designsystem.CompactButton
import com.cipherchat.core.designsystem.EncryptionBanner
import com.cipherchat.core.designsystem.ErrorBox
import com.cipherchat.core.designsystem.Eyebrow
import com.cipherchat.core.designsystem.FormError
import com.cipherchat.core.designsystem.GlassLevel
import com.cipherchat.core.designsystem.Hairline
import com.cipherchat.core.designsystem.ListSkeleton
import com.cipherchat.core.designsystem.PrimaryButton
import com.cipherchat.core.designsystem.SecondaryButton
import com.cipherchat.core.designsystem.SegChip
import com.cipherchat.core.designsystem.Spacing
import com.cipherchat.core.designsystem.SubScreen
import com.cipherchat.core.designsystem.appear
import com.cipherchat.core.designsystem.clickableScale
import com.cipherchat.core.designsystem.formatListTime
import com.cipherchat.core.designsystem.glass
import com.cipherchat.core.designsystem.glassSource
import com.cipherchat.core.security.BiometricHelper
import com.cipherchat.data.ThemeMode
import com.cipherchat.domain.EncryptionIndicator
import com.cipherchat.feature.common.appViewModel
import java.time.Instant

@Composable
private fun NavRow(title: String, subtitle: String? = null, onClick: () -> Unit) {
    Column(Modifier.fillMaxWidth().clickableScale(onClick)) {
        Row(Modifier.padding(horizontal = Spacing.md, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                subtitle?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1) }
            }
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Hairline(Modifier.padding(start = Spacing.md))
    }
}

@Composable
private fun SwitchRow(title: String, subtitle: String, checked: Boolean, enabled: Boolean = true, onChange: (Boolean) -> Unit) {
    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.padding(horizontal = Spacing.md, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.width(Spacing.md))
            Switch(
                checked = checked,
                onCheckedChange = onChange,
                enabled = enabled,
                colors = SwitchDefaults.colors(
                    checkedTrackColor = MaterialTheme.colorScheme.primary,
                    checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                    checkedBorderColor = MaterialTheme.colorScheme.primary,
                    uncheckedTrackColor = AppTheme.colors.glassFillStrong,
                    uncheckedThumbColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    uncheckedBorderColor = AppTheme.colors.hairline,
                ),
            )
        }
        Hairline(Modifier.padding(start = Spacing.md))
    }
}

@Composable
fun SettingsTab(padding: PaddingValues, onProfile: () -> Unit, onPrivacy: () -> Unit, onDevices: () -> Unit, onServer: () -> Unit, onDelete: () -> Unit) {
    val vm = appViewModel { SettingsTabViewModel(it.settings, it.auth) }
    val s by vm.state.collectAsState()
    Column(
        Modifier.fillMaxSize().glassSource().verticalScroll(rememberScrollState())
            .padding(top = padding.calculateTopPadding() + Spacing.md, bottom = padding.calculateBottomPadding() + Spacing.md),
    ) {
        s.user?.let { u ->
            Row(
                Modifier.padding(horizontal = Spacing.md).fillMaxWidth().appear(0).glass(MaterialTheme.shapes.large, GlassLevel.Strong)
                    .clickableScale(onProfile).padding(Spacing.md),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Avatar(u.displayName, size = 56)
                Spacer(Modifier.width(Spacing.md))
                Column(Modifier.weight(1f)) {
                    Text(u.displayName, style = MaterialTheme.typography.titleLarge)
                    Text("@${u.username}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Edit profile", tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Eyebrow("Appearance", Modifier.padding(horizontal = Spacing.md).padding(top = Spacing.lg, bottom = Spacing.sm))
        Row(Modifier.padding(horizontal = Spacing.md), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ThemeMode.entries.forEach { m ->
                SegChip(m.name.lowercase().replaceFirstChar { it.uppercase() }, s.prefs?.themeMode == m, { vm.setTheme(m) })
            }
        }
        Eyebrow("Account", Modifier.padding(horizontal = Spacing.md).padding(top = Spacing.lg, bottom = Spacing.xs))
        NavRow("Privacy & security", "App lock, discoverability, blocked users", onPrivacy)
        NavRow("Devices & sessions", "See where you're signed in", onDevices)
        NavRow("Server", s.prefs?.baseUrl, onServer)
        NavRow("Delete account", "Permanently remove your account and data", onDelete)
        Box(Modifier.padding(Spacing.md)) { SecondaryButton("Sign out", vm::signOut, Modifier.fillMaxWidth()) }
        Text(
            "CipherChat ${com.cipherchat.BuildConfig.VERSION_NAME} · Private end-to-end chat is not available in this version.",
            Modifier.padding(horizontal = Spacing.md),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
fun ProfileScreen(onBack: () -> Unit) {
    val vm = appViewModel { ProfileViewModel(it.auth) }
    val s by vm.state.collectAsState()
    SubScreen("Profile", onBack) { padding ->
        Column(Modifier.padding(top = padding.calculateTopPadding()).padding(Spacing.md), verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Avatar(s.displayName.ifEmpty { s.username }, size = 72)
                Spacer(Modifier.width(Spacing.md))
                Text("@${s.username}", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            AppTextField(s.displayName, vm::onName, "Display name", enabled = !s.saving)
            FormError(s.error)
            s.message?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = AppTheme.colors.accent) }
            PrimaryButton("Save", vm::save, loading = s.saving)
        }
    }
}

@Composable
fun PrivacySecurityScreen(onBack: () -> Unit, onEncryptionStatus: () -> Unit) {
    val vm = appViewModel { PrivacyViewModel(it.settings, it.auth, it.contacts) }
    val s by vm.state.collectAsState()
    val context = LocalContext.current
    val biometricAvailable = remember { BiometricHelper.isAvailable(context) }
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(s.message) { s.message?.let { snackbar.showSnackbar(it); vm.messageShown() } }

    SubScreen("Privacy & security", onBack, snackbar) { padding ->
        LazyColumn(Modifier.fillMaxSize().glassSource(), contentPadding = PaddingValues(top = padding.calculateTopPadding(), bottom = Spacing.xl)) {
            item { EncryptionBanner(EncryptionIndicator.E2E_UNAVAILABLE) }
            item { NavRow("Encryption status & verification", "What is and isn't protected", onEncryptionStatus) }
            item {
                SwitchRow(
                    "App lock",
                    if (biometricAvailable) "Require biometrics or your device PIN to open CipherChat" else "Set up a screen lock or biometrics on your device to enable this",
                    s.prefs?.biometricLock == true,
                    enabled = biometricAvailable,
                    onChange = vm::setBiometric,
                )
            }
            item { SwitchRow("Block screenshots", "Also hides CipherChat in the recent-apps view", s.prefs?.blockScreenshots == true, onChange = vm::setBlockScreenshots) }
            item {
                SwitchRow("Discoverable", "Let people find you by searching your username", s.discoverable == true, enabled = s.discoverable != null, onChange = vm::setDiscoverable)
            }
            item { Eyebrow("Blocked users", Modifier.padding(horizontal = Spacing.md).padding(top = Spacing.lg, bottom = Spacing.sm)) }
            if (s.blocks.isEmpty()) item { Text("You haven't blocked anyone.", Modifier.padding(horizontal = Spacing.md), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            items(s.blocks, key = { it.id }) { b ->
                Column(Modifier.animateItem()) {
                    Row(Modifier.padding(horizontal = Spacing.md, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("${b.user.displayName} (@${b.user.username})", Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
                        CompactButton("Unblock", { vm.unblock(b) })
                    }
                    Hairline(Modifier.padding(start = Spacing.md))
                }
            }
            item {
                Text(
                    "Notifications: this version does not send push notifications, so no message content is ever given to a push provider.",
                    Modifier.padding(Spacing.md),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
fun DevicesScreen(onBack: () -> Unit) {
    val vm = appViewModel { DevicesViewModel(it.auth) }
    val s by vm.state.collectAsState()
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(s.message) { s.message?.let { snackbar.showSnackbar(it); vm.messageShown() } }
    SubScreen("Devices & sessions", onBack, snackbar) { padding ->
        Box(Modifier.fillMaxSize().padding(top = padding.calculateTopPadding())) {
            when {
                s.loading -> ListSkeleton(3)
                s.error != null -> ErrorBox(s.error!!, onRetry = vm::load)
                else -> LazyColumn(Modifier.fillMaxSize().glassSource()) {
                    items(s.devices, key = { it.id }) { d ->
                        Column(Modifier.animateItem()) {
                            Row(Modifier.padding(horizontal = Spacing.md, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(d.name + if (d.current) " · this device" else "", style = MaterialTheme.typography.titleMedium)
                                    val seen = d.lastSeenAt?.let { runCatching { Instant.parse(it).toEpochMilli() }.getOrNull() }?.let { formatListTime(it) }
                                    Text(
                                        (if (d.status == "revoked") "Signed out" else "Active") + (seen?.let { " · last seen $it" } ?: ""),
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                if (d.status == "active" && !d.current) CompactButton("Sign out", { vm.revoke(d) })
                            }
                            Hairline(Modifier.padding(start = Spacing.md))
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun DeleteAccountScreen(onBack: () -> Unit) {
    val vm = appViewModel { DeleteAccountViewModel(it.auth) }
    val s by vm.state.collectAsState()
    SubScreen("Delete account", onBack) { padding ->
        Column(
            Modifier.verticalScroll(rememberScrollState()).padding(top = padding.calculateTopPadding()).padding(Spacing.md),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            Text("This permanently deletes your account.", style = MaterialTheme.typography.headlineSmall)
            Text(
                "• Your profile, contacts, friend requests and blocks are removed.\n" +
                    "• Every message you sent is deleted from the server.\n" +
                    "• One-to-one chats with you are deleted for the other person too.\n" +
                    "• You'll leave all groups; groups continue without you.\n" +
                    "• All sessions are signed out and this device's local data is erased.\n\n" +
                    "It can't be undone. Copies other people already have (screenshots, copied text) can't be removed.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            AppTextField(s.password, vm::onPassword, "Confirm with your password", password = true, enabled = !s.loading)
            Row(Modifier.fillMaxWidth().clickableScale({ vm.onConfirmed(!s.confirmed) }), verticalAlignment = Alignment.CenterVertically) {
                androidx.compose.material3.Checkbox(
                    checked = s.confirmed,
                    onCheckedChange = vm::onConfirmed,
                    colors = androidx.compose.material3.CheckboxDefaults.colors(checkedColor = MaterialTheme.colorScheme.primary, checkmarkColor = MaterialTheme.colorScheme.onPrimary),
                )
                Text("I understand this is permanent", style = MaterialTheme.typography.bodyMedium)
            }
            FormError(s.error)
            Button(
                onClick = vm::delete,
                enabled = s.confirmed && s.password.isNotEmpty() && !s.loading,
                modifier = Modifier.fillMaxWidth().height(48.dp),
                shape = MaterialTheme.shapes.small,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error, contentColor = MaterialTheme.colorScheme.onError),
            ) { Text(if (s.loading) "Deleting…" else "Delete my account", style = MaterialTheme.typography.labelLarge) }
        }
    }
}

@Composable
fun ServerSettingsScreen(onBack: () -> Unit) {
    val vm = appViewModel { ServerViewModel(it.settings, it.apiClient) }
    val s by vm.state.collectAsState()
    SubScreen("Server", onBack) { padding ->
        Column(
            Modifier.verticalScroll(rememberScrollState()).padding(top = padding.calculateTopPadding()).padding(Spacing.md),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            AppTextField(s.url, vm::onUrl, "Server address", isError = s.error != null, supportingText = s.error ?: "e.g. https://chat.example.com/")
            Text(
                "Emulator: the host computer is http://10.0.2.2:5000/ (debug builds only). Physical device: use your computer's LAN address, e.g. http://192.168.1.20:5000/, with the firewall allowing the port. Release builds only accept https://.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            s.message?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = AppTheme.colors.accent) }
            PrimaryButton("Save", vm::save)
        }
    }
}
