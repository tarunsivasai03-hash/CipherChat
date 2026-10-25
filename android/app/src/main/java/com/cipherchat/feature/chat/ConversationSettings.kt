package com.cipherchat.feature.chat

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cipherchat.core.common.AppResult
import com.cipherchat.core.database.ConversationEntity
import com.cipherchat.core.designsystem.Avatar
import com.cipherchat.core.designsystem.CompactButton
import com.cipherchat.core.designsystem.EncryptionBanner
import com.cipherchat.core.designsystem.Eyebrow
import com.cipherchat.core.designsystem.Hairline
import com.cipherchat.core.designsystem.SecondaryButton
import com.cipherchat.core.designsystem.Spacing
import com.cipherchat.core.designsystem.SubScreen
import com.cipherchat.core.designsystem.clickableScale
import com.cipherchat.core.network.MemberDto
import com.cipherchat.core.network.UserDto
import com.cipherchat.data.AuthRepository
import com.cipherchat.data.ChatRepository
import com.cipherchat.data.ContactsRepository
import com.cipherchat.data.members
import com.cipherchat.data.peer
import com.cipherchat.domain.EncryptionPolicy
import com.cipherchat.feature.common.appViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SettingsUiState(
    val conversation: ConversationEntity? = null,
    val members: List<MemberDto> = emptyList(),
    val myId: Long? = null,
    val iAmAdmin: Boolean = false,
    val addable: List<UserDto> = emptyList(),
    val message: String? = null,
    val left: Boolean = false,
)

class ConversationSettingsViewModel(
    private val id: Long,
    private val chat: ChatRepository,
    private val contacts: ContactsRepository,
    private val auth: AuthRepository,
) : ViewModel() {
    private val extra = MutableStateFlow(SettingsUiState())

    val state: StateFlow<SettingsUiState> = combine(chat.conversation(id), extra) { conv, e ->
        val members = conv?.members().orEmpty()
        val me = auth.currentUserId
        e.copy(
            conversation = conv,
            members = members,
            myId = me,
            iAmAdmin = members.any { it.user.id == me && it.role == "admin" },
            addable = e.addable.filter { c -> members.none { it.user.id == c.id } },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsUiState())

    init { viewModelScope.launch { chat.refreshConversation(id) } }

    private fun report(r: AppResult<*>, ok: String? = null) {
        when (r) {
            is AppResult.Failure -> extra.update { it.copy(message = r.error.message) }
            is AppResult.Success -> if (ok != null) extra.update { it.copy(message = ok) }
        }
    }

    fun setTimer(seconds: Int?) { viewModelScope.launch { report(chat.setDisappearTimer(id, seconds), "Timer updated.") } }
    fun rename(name: String) { viewModelScope.launch { report(chat.rename(id, name), "Group renamed.") } }
    fun remove(userId: Long) { viewModelScope.launch { report(chat.removeMember(id, userId), "Member removed.") } }
    fun leave() { viewModelScope.launch { val r = chat.removeMember(id, auth.currentUserId ?: return@launch); report(r); if (r is AppResult.Success) extra.update { it.copy(left = true) } } }
    fun add(userId: Long) { viewModelScope.launch { report(chat.addMembers(id, listOf(userId)), "Member added.") } }

    fun loadAddable() {
        viewModelScope.launch {
            (contacts.contacts() as? AppResult.Success)?.let { r -> extra.update { it.copy(addable = r.data) } }
        }
    }

    fun blockPeer(userId: Long) { viewModelScope.launch { report(contacts.block(userId), "User blocked."); chat.refreshConversations() } }
    fun messageShown() = extra.update { it.copy(message = null) }
}

private val Timers = listOf<Pair<String, Int?>>("Off" to null, "10 seconds" to 10, "1 minute" to 60, "1 hour" to 3600, "24 hours" to 86400)

@Composable
fun ConversationSettingsScreen(id: Long, onBack: () -> Unit, onVerification: () -> Unit, onLeft: () -> Unit) {
    val vm = appViewModel(key = "convsettings-$id") { ConversationSettingsViewModel(id, it.chat, it.contacts, it.auth) }
    val s by vm.state.collectAsState()
    val snackbar = remember { SnackbarHostState() }
    var showAdd by remember { mutableStateOf(false) }
    var confirmLeave by remember { mutableStateOf(false) }
    LaunchedEffect(s.message) { s.message?.let { snackbar.showSnackbar(it); vm.messageShown() } }
    LaunchedEffect(s.left) { if (s.left) onLeft() }
    val conv = s.conversation
    val isGroup = conv?.type == "group"
    val canChangeTimer = !isGroup || s.iAmAdmin

    SubScreen(if (isGroup) "Group info" else "Conversation", onBack, snackbar) { padding ->
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(top = padding.calculateTopPadding(), bottom = Spacing.xl),
        ) {
            EncryptionBanner(EncryptionPolicy.forConversation(false))
            Column(Modifier.fillMaxWidth().clickableScale(onVerification)) {
                Row(Modifier.padding(horizontal = Spacing.md, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("Security & identity verification", Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Hairline()
            }

            Eyebrow("Disappearing messages", Modifier.padding(horizontal = Spacing.md, vertical = Spacing.md))
            Text(
                "After the timer, new messages are no longer served by the server and are removed from this device. This can't erase screenshots, copies, or anything another person already saw or saved.",
                Modifier.padding(horizontal = Spacing.md, vertical = 0.dp),
                style = MaterialTheme.typography.bodyLarge.copy(fontSize = androidx.compose.ui.unit.TextUnit(15f, androidx.compose.ui.unit.TextUnitType.Sp)),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.padding(top = Spacing.sm))
            Timers.forEach { (label, secs) ->
                val selected = conv?.disappearSeconds == secs
                Column(Modifier.fillMaxWidth().clickableScale({ vm.setTimer(secs) }, enabled = canChangeTimer)) {
                    Row(Modifier.padding(horizontal = Spacing.md, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.titleSmall, color = if (canChangeTimer) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant)
                        if (selected) Icon(Icons.Filled.Check, contentDescription = "Selected")
                    }
                    Hairline(Modifier.padding(start = Spacing.md))
                }
            }
            if (!canChangeTimer) Text("Only group admins can change this.", Modifier.padding(horizontal = Spacing.md, vertical = Spacing.sm), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

            if (isGroup) {
                Row(Modifier.fillMaxWidth().padding(horizontal = Spacing.md, vertical = Spacing.md), verticalAlignment = Alignment.CenterVertically) {
                    Eyebrow("Members · ${s.members.size}", Modifier.weight(1f))
                    if (s.iAmAdmin) CompactButton("Add", { vm.loadAddable(); showAdd = true })
                }
                s.members.forEach { m ->
                    Column(Modifier.fillMaxWidth()) {
                        Row(Modifier.padding(horizontal = Spacing.md, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                            Avatar(m.user.displayName, size = 40)
                            Spacer(Modifier.width(Spacing.md))
                            Column(Modifier.weight(1f)) {
                                Text(m.user.displayName + if (m.user.id == s.myId) " (you)" else "", style = MaterialTheme.typography.titleSmall)
                                Text(if (m.role == "admin") "Admin · @${m.user.username}" else "@${m.user.username}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            if (s.iAmAdmin && m.user.id != s.myId) IconButton(onClick = { vm.remove(m.user.id) }) { Icon(Icons.Filled.Close, contentDescription = "Remove ${m.user.displayName}") }
                        }
                        Hairline(Modifier.padding(start = Spacing.md + 40.dp + Spacing.md))
                    }
                }
                SecondaryButton("Leave group", { confirmLeave = true }, Modifier.padding(Spacing.md).fillMaxWidth())
            } else {
                conv?.peer(s.myId)?.let { peer ->
                    SecondaryButton("Block ${peer.user.displayName}", { vm.blockPeer(peer.user.id) }, Modifier.padding(Spacing.md).fillMaxWidth())
                }
            }
        }
    }

    if (showAdd) {
        AlertDialog(
            onDismissRequest = { showAdd = false },
            containerColor = MaterialTheme.colorScheme.surface,
            shape = MaterialTheme.shapes.extraLarge,
            title = { Text("Add to group", style = MaterialTheme.typography.titleLarge) },
            text = {
                if (s.addable.isEmpty()) Text("None of your contacts are available to add.", style = MaterialTheme.typography.bodyLarge)
                else Column { s.addable.forEach { u -> TextButton(onClick = { vm.add(u.id); showAdd = false }) { Text("${u.displayName} (@${u.username})", color = MaterialTheme.colorScheme.onSurface) } } }
            },
            confirmButton = { TextButton(onClick = { showAdd = false }) { Text("Close", color = MaterialTheme.colorScheme.onSurface) } },
        )
    }
    if (confirmLeave) {
        AlertDialog(
            onDismissRequest = { confirmLeave = false },
            containerColor = MaterialTheme.colorScheme.surface,
            shape = MaterialTheme.shapes.extraLarge,
            title = { Text("Leave this group?", style = MaterialTheme.typography.titleLarge) },
            text = { Text("You'll stop receiving messages and the local copy will be removed from this device.", style = MaterialTheme.typography.bodyLarge) },
            confirmButton = { TextButton(onClick = { confirmLeave = false; vm.leave() }) { Text("Leave", color = MaterialTheme.colorScheme.error) } },
            dismissButton = { TextButton(onClick = { confirmLeave = false }) { Text("Cancel", color = MaterialTheme.colorScheme.onSurfaceVariant) } },
        )
    }
}
