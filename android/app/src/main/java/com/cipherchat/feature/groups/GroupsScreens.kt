package com.cipherchat.feature.groups

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cipherchat.core.common.AppError
import com.cipherchat.core.common.AppResult
import com.cipherchat.core.designsystem.AppTextField
import com.cipherchat.core.designsystem.AppTheme
import com.cipherchat.core.designsystem.Avatar
import com.cipherchat.core.designsystem.EmptyState
import com.cipherchat.core.designsystem.ErrorBox
import com.cipherchat.core.designsystem.FormError
import com.cipherchat.core.designsystem.Hairline
import com.cipherchat.core.designsystem.ListSkeleton
import com.cipherchat.core.designsystem.PrimaryButton
import com.cipherchat.core.designsystem.Spacing
import com.cipherchat.core.designsystem.SubScreen
import com.cipherchat.core.designsystem.clickableScale
import com.cipherchat.core.designsystem.glassSource
import com.cipherchat.core.designsystem.pressScale
import com.cipherchat.core.network.UserDto
import com.cipherchat.data.ChatRepository
import com.cipherchat.data.ContactsRepository
import com.cipherchat.data.members
import com.cipherchat.feature.common.appViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class GroupItem(val id: Long, val name: String, val memberCount: Int, val unread: Int)

class GroupsViewModel(chat: ChatRepository) : ViewModel() {
    val groups: StateFlow<List<GroupItem>> = chat.conversations
        .map { list -> list.filter { it.type == "group" }.map { GroupItem(it.id, it.name ?: "Group", it.members().size, it.unreadCount) } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
}

@Composable
fun GroupsTab(padding: PaddingValues, onOpenChat: (Long) -> Unit, onCreate: () -> Unit) {
    val vm = appViewModel { GroupsViewModel(it.chat) }
    val groups by vm.groups.collectAsState()
    Box(Modifier.fillMaxSize()) {
        if (groups.isEmpty()) {
            Box(Modifier.padding(top = padding.calculateTopPadding(), bottom = padding.calculateBottomPadding())) {
                EmptyState(
                    Icons.Filled.Groups, "No groups yet",
                    "Create a group with your contacts. Group messages are not end-to-end encrypted.",
                    action = { com.cipherchat.core.designsystem.SecondaryButton("Create a group", onCreate, Modifier.fillMaxWidth(0.6f)) },
                )
            }
        } else {
            LazyColumn(
                Modifier.fillMaxSize().glassSource(),
                contentPadding = PaddingValues(top = padding.calculateTopPadding() + Spacing.sm, bottom = padding.calculateBottomPadding() + 88.dp),
            ) {
                items(groups, key = { it.id }) { g ->
                    Column(Modifier.fillMaxWidth().animateItem().clickableScale({ onOpenChat(g.id) })) {
                        Row(Modifier.padding(horizontal = Spacing.md, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                            Avatar(g.name, size = 48)
                            Spacer(Modifier.width(Spacing.md))
                            Column(Modifier.weight(1f)) {
                                Text(g.name, style = MaterialTheme.typography.titleMedium)
                                Text("${g.memberCount} members", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            if (g.unread > 0) {
                                Text(
                                    "${g.unread}",
                                    Modifier.background(MaterialTheme.colorScheme.primary, MaterialTheme.shapes.extraSmall).padding(horizontal = 7.dp, vertical = 2.dp),
                                    color = MaterialTheme.colorScheme.onPrimary,
                                    style = MaterialTheme.typography.labelSmall,
                                )
                            }
                        }
                        Hairline(Modifier.padding(start = Spacing.md + 48.dp + Spacing.md))
                    }
                }
            }
            val source = remember { MutableInteractionSource() }
            Box(
                Modifier.align(Alignment.BottomEnd).padding(end = Spacing.md, bottom = padding.calculateBottomPadding() + Spacing.md).size(56.dp)
                    .pressScale(source, 0.92f).background(MaterialTheme.colorScheme.primary, MaterialTheme.shapes.large)
                    .clickable(interactionSource = source, indication = null, onClick = onCreate),
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Filled.Add, contentDescription = "Create group", tint = MaterialTheme.colorScheme.onPrimary) }
        }
    }
}

data class CreateGroupState(
    val loading: Boolean = true,
    val loadError: AppError? = null,
    val contacts: List<UserDto> = emptyList(),
    val name: String = "",
    val selected: Set<Long> = emptySet(),
    val creating: Boolean = false,
    val error: String? = null,
    val createdId: Long? = null,
)

class CreateGroupViewModel(private val contactsRepo: ContactsRepository, private val chat: ChatRepository) : ViewModel() {
    private val _state = MutableStateFlow(CreateGroupState())
    val state: StateFlow<CreateGroupState> = _state.asStateFlow()

    init { load() }

    fun load() {
        _state.update { it.copy(loading = true, loadError = null) }
        viewModelScope.launch {
            when (val r = contactsRepo.contacts()) {
                is AppResult.Success -> _state.update { it.copy(loading = false, contacts = r.data) }
                is AppResult.Failure -> _state.update { it.copy(loading = false, loadError = r.error) }
            }
        }
    }

    fun onName(v: String) = _state.update { it.copy(name = v, error = null) }
    fun toggle(id: Long) = _state.update { it.copy(selected = if (id in it.selected) it.selected - id else it.selected + id, error = null) }

    fun create() {
        val s = _state.value
        when {
            s.name.isBlank() -> { _state.update { it.copy(error = "Give the group a name.") }; return }
            s.selected.isEmpty() -> { _state.update { it.copy(error = "Pick at least one contact.") }; return }
        }
        _state.update { it.copy(creating = true, error = null) }
        viewModelScope.launch {
            when (val r = chat.createGroup(s.name, s.selected.toList())) {
                is AppResult.Success -> _state.update { it.copy(creating = false, createdId = r.data) }
                is AppResult.Failure -> _state.update { it.copy(creating = false, error = r.error.message) }
            }
        }
    }
}

@Composable
fun CreateGroupScreen(onBack: () -> Unit, onCreated: (Long) -> Unit) {
    val vm = appViewModel { CreateGroupViewModel(it.contacts, it.chat) }
    val s by vm.state.collectAsState()
    LaunchedEffect(s.createdId) { s.createdId?.let(onCreated) }
    SubScreen("New group", onBack) { padding ->
        Column(Modifier.fillMaxSize().padding(top = padding.calculateTopPadding())) {
            Column(Modifier.padding(Spacing.md), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                AppTextField(s.name, vm::onName, "Group name", enabled = !s.creating)
                Text(
                    "Group chats are not end-to-end encrypted: the server can read group messages.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                FormError(s.error)
            }
            Box(Modifier.weight(1f)) {
                when {
                    s.loading -> ListSkeleton(4)
                    s.loadError != null -> ErrorBox(s.loadError!!, onRetry = vm::load)
                    s.contacts.isEmpty() -> EmptyState(Icons.Filled.Groups, "No contacts to add", "Add some contacts first, then create a group.")
                    else -> LazyColumn(Modifier.fillMaxSize().glassSource()) {
                        items(s.contacts, key = { it.id }) { u ->
                            val picked = u.id in s.selected
                            val box by animateColorAsState(if (picked) MaterialTheme.colorScheme.primary else androidx.compose.ui.graphics.Color.Transparent, tween(180), label = "pick")
                            Column(Modifier.fillMaxWidth().clickableScale({ vm.toggle(u.id) })) {
                                Row(Modifier.padding(horizontal = Spacing.md, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Box(
                                        Modifier.size(24.dp).background(box, MaterialTheme.shapes.extraSmall).border(1.5.dp, if (picked) box else AppTheme.colors.hairline, MaterialTheme.shapes.extraSmall),
                                        contentAlignment = Alignment.Center,
                                    ) { if (picked) Icon(Icons.Filled.Check, contentDescription = "Selected", tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(16.dp)) }
                                    Spacer(Modifier.width(Spacing.md))
                                    Avatar(u.displayName, size = 40)
                                    Spacer(Modifier.width(Spacing.md))
                                    Column {
                                        Text(u.displayName, style = MaterialTheme.typography.titleMedium)
                                        Text("@${u.username}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                                Hairline(Modifier.padding(start = Spacing.md + 24.dp + Spacing.md))
                            }
                        }
                    }
                }
            }
            Box(Modifier.padding(Spacing.md)) { PrimaryButton("Create group (${s.selected.size})", vm::create, loading = s.creating) }
        }
    }
}
