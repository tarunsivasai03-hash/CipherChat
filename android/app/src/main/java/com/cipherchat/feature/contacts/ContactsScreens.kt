package com.cipherchat.feature.contacts

import androidx.compose.foundation.background
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
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.GroupAdd
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHostState
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
import com.cipherchat.core.designsystem.Avatar
import com.cipherchat.core.designsystem.CompactButton
import com.cipherchat.core.designsystem.EmptyState
import com.cipherchat.core.designsystem.ErrorBox
import com.cipherchat.core.designsystem.GlassSnackbarHost
import com.cipherchat.core.designsystem.Hairline
import com.cipherchat.core.designsystem.ListSkeleton
import com.cipherchat.core.designsystem.Spacing
import com.cipherchat.core.designsystem.SubScreen
import com.cipherchat.core.designsystem.clickableScale
import com.cipherchat.core.designsystem.glassFieldColors
import com.cipherchat.core.designsystem.glassSource
import com.cipherchat.core.network.UserDto
import com.cipherchat.feature.common.appViewModel

@Composable
private fun rememberContactsVm(key: String) = appViewModel(key = key) { ContactsViewModel(it.contacts, it.chat, it.socket) }

/** Ruled user row: square avatar, name, handle, optional trailing action. */
@Composable
fun UserRow(user: UserDto, trailing: @Composable () -> Unit = {}, onClick: (() -> Unit)? = null) {
    Column(Modifier.fillMaxWidth().then(if (onClick != null) Modifier.clickableScale(onClick) else Modifier)) {
        Row(Modifier.padding(horizontal = Spacing.md, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Avatar(user.displayName, size = 44)
            Spacer(Modifier.width(Spacing.md))
            Column(Modifier.weight(1f)) {
                Text(user.displayName, style = MaterialTheme.typography.titleMedium)
                Text("@${user.username}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            trailing()
        }
        Hairline(Modifier.padding(start = Spacing.md + 44.dp + Spacing.md))
    }
}

@Composable
private fun SearchBox(query: String, onQuery: (String) -> Unit, hint: String) {
    OutlinedTextField(
        value = query,
        onValueChange = onQuery,
        placeholder = { Text(hint, color = MaterialTheme.colorScheme.onSurfaceVariant) },
        leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant) },
        singleLine = true,
        shape = MaterialTheme.shapes.medium,
        colors = glassFieldColors(),
        modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.md, vertical = Spacing.sm),
    )
}

@Composable
fun ContactsTab(padding: PaddingValues, onOpenChat: (Long) -> Unit, onRequests: () -> Unit) {
    val vm = rememberContactsVm("contacts-tab")
    val s by vm.state.collectAsState()
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(s.message) { s.message?.let { snackbar.showSnackbar(it); vm.messageShown() } }
    LaunchedEffect(s.openChatId) { s.openChatId?.let { onOpenChat(it); vm.chatOpened() } }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().padding(top = padding.calculateTopPadding())) {
            SearchBox(s.query, vm::onQuery, "Find people by username")
            if (s.query.trim().length >= 3) {
                SearchResults(s, vm, padding)
            } else when {
                s.loading -> ListSkeleton(5)
                s.error != null && s.contacts.isEmpty() -> ErrorBox(s.error!!, onRetry = { vm.refresh() })
                else -> LazyColumn(Modifier.fillMaxSize().glassSource(), contentPadding = PaddingValues(bottom = padding.calculateBottomPadding() + Spacing.md)) {
                    item(key = "requests") {
                        Column(Modifier.fillMaxWidth().clickableScale(onRequests)) {
                            Row(Modifier.padding(horizontal = Spacing.md, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Filled.PersonAdd, contentDescription = null)
                                Spacer(Modifier.width(Spacing.md))
                                Text("Friend requests", Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                                if (s.incoming.isNotEmpty()) {
                                    Text(
                                        "${s.incoming.size}",
                                        Modifier.background(MaterialTheme.colorScheme.primary, MaterialTheme.shapes.extraSmall).padding(horizontal = 7.dp, vertical = 2.dp),
                                        color = MaterialTheme.colorScheme.onPrimary,
                                        style = MaterialTheme.typography.labelSmall,
                                    )
                                    Spacer(Modifier.width(8.dp))
                                }
                                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Hairline()
                        }
                    }
                    if (s.contacts.isEmpty()) item(key = "empty") {
                        Box(Modifier.fillMaxWidth().padding(top = Spacing.xxl)) {
                            EmptyState(Icons.Filled.Person, "No contacts yet", "Search for a friend's username above and send them a request.")
                        }
                    }
                    items(s.contacts, key = { it.id }) { u ->
                        var menu by remember { mutableStateOf(false) }
                        Box(Modifier.animateItem()) {
                            UserRow(u, onClick = { vm.openChat(u) }, trailing = {
                                Box {
                                    IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, contentDescription = "Options for ${u.displayName}") }
                                    DropdownMenu(menu, { menu = false }) {
                                        DropdownMenuItem(text = { Text("Message") }, onClick = { menu = false; vm.openChat(u) })
                                        DropdownMenuItem(text = { Text("Remove contact") }, onClick = { menu = false; vm.remove(u) })
                                        DropdownMenuItem(text = { Text("Block", color = MaterialTheme.colorScheme.error) }, onClick = { menu = false; vm.block(u) })
                                    }
                                }
                            })
                        }
                    }
                }
            }
        }
        GlassSnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).padding(bottom = padding.calculateBottomPadding()))
    }
}

@Composable
private fun SearchResults(s: ContactsUiState, vm: ContactsViewModel, padding: PaddingValues) {
    when {
        s.searching -> ListSkeleton(3)
        s.searchError != null -> Text(s.searchError, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(Spacing.md))
        s.results.isEmpty() -> EmptyState(Icons.Filled.Search, "No one found", "Only people who allow discovery appear in search. Check the spelling of the username.")
        else -> LazyColumn(Modifier.fillMaxSize().glassSource(), contentPadding = PaddingValues(bottom = padding.calculateBottomPadding())) {
            items(s.results, key = { it.id }) { u ->
                val isContact = s.contacts.any { it.id == u.id }
                val pending = u.id in s.requestedIds || s.outgoing.any { it.recipient.id == u.id }
                Box(Modifier.animateItem()) {
                    UserRow(u, trailing = {
                        when {
                            isContact -> CompactButton("Message", { vm.openChat(u) })
                            pending -> Text("Requested", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            else -> CompactButton("Add", { vm.sendRequest(u) }, filled = true)
                        }
                    })
                }
            }
        }
    }
}

@Composable
fun RequestsScreen(onBack: () -> Unit) {
    val vm = rememberContactsVm("requests")
    val s by vm.state.collectAsState()
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(s.message) { s.message?.let { snackbar.showSnackbar(it); vm.messageShown() } }

    SubScreen("Friend requests", onBack, snackbar) { padding ->
        Box(Modifier.fillMaxSize().padding(top = padding.calculateTopPadding())) {
            when {
                s.loading -> ListSkeleton(4)
                s.error != null -> ErrorBox(s.error!!, onRetry = { vm.refresh() })
                s.incoming.isEmpty() && s.outgoing.isEmpty() -> EmptyState(Icons.Filled.PersonAdd, "No pending requests", "Requests you send or receive will show up here.")
                else -> LazyColumn(Modifier.fillMaxSize().glassSource()) {
                    if (s.incoming.isNotEmpty()) item { SectionTitle("Received") }
                    items(s.incoming, key = { "in-${it.id}" }) { r ->
                        Box(Modifier.animateItem()) {
                            UserRow(r.sender, trailing = {
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    IconButton(onClick = { vm.reject(r) }) { Icon(Icons.Filled.Close, contentDescription = "Decline ${r.sender.displayName}") }
                                    CompactButton("Accept", { vm.accept(r) }, filled = true)
                                }
                            })
                        }
                    }
                    if (s.outgoing.isNotEmpty()) item { SectionTitle("Sent") }
                    items(s.outgoing, key = { "out-${it.id}" }) { r ->
                        Box(Modifier.animateItem()) {
                            UserRow(r.recipient, trailing = { Text("Waiting", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) })
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SectionTitle(text: String) {
    com.cipherchat.core.designsystem.Eyebrow(text, Modifier.padding(horizontal = Spacing.md, vertical = Spacing.md))
}

/** Contact picker used to start a direct chat. */
@Composable
fun NewChatScreen(onBack: () -> Unit, onOpenChat: (Long) -> Unit, onNewGroup: () -> Unit) {
    val vm = rememberContactsVm("new-chat")
    val s by vm.state.collectAsState()
    LaunchedEffect(s.openChatId) { s.openChatId?.let { onOpenChat(it); vm.chatOpened() } }
    SubScreen("New conversation", onBack) { padding ->
        Box(Modifier.fillMaxSize().padding(top = padding.calculateTopPadding())) {
            when {
                s.loading -> ListSkeleton(5)
                s.error != null -> ErrorBox(s.error!!, onRetry = { vm.refresh() })
                else -> LazyColumn(Modifier.fillMaxSize().glassSource()) {
                    item {
                        Column(Modifier.fillMaxWidth().clickableScale(onNewGroup)) {
                            Row(Modifier.padding(horizontal = Spacing.md, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Filled.GroupAdd, contentDescription = null)
                                Spacer(Modifier.width(Spacing.md))
                                Text("New group", style = MaterialTheme.typography.titleMedium)
                            }
                            Hairline()
                        }
                    }
                    if (s.contacts.isEmpty()) item { Text("You have no contacts yet. Add some from the Contacts tab.", Modifier.padding(Spacing.md), color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    items(s.contacts, key = { it.id }) { u -> Box(Modifier.animateItem()) { UserRow(u, onClick = { vm.openChat(u) }) } }
                }
            }
        }
    }
}
