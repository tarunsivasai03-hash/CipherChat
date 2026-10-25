package com.cipherchat.feature.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cipherchat.core.designsystem.Avatar
import com.cipherchat.core.designsystem.EmptyState
import com.cipherchat.core.designsystem.ErrorBox
import com.cipherchat.core.designsystem.GlassLevel
import com.cipherchat.core.designsystem.Hairline
import com.cipherchat.core.designsystem.ListSkeleton
import com.cipherchat.core.designsystem.SecondaryButton
import com.cipherchat.core.designsystem.Spacing
import com.cipherchat.core.designsystem.appear
import com.cipherchat.core.designsystem.clickableScale
import com.cipherchat.core.designsystem.formatListTime
import com.cipherchat.core.designsystem.glass
import com.cipherchat.core.designsystem.glassFieldColors
import com.cipherchat.core.designsystem.glassSource
import com.cipherchat.core.designsystem.pressScale
import com.cipherchat.core.network.ConnectionState
import com.cipherchat.feature.common.appViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatsTab(padding: PaddingValues, onOpenChat: (Long) -> Unit, onNewChat: () -> Unit) {
    val vm = appViewModel { ChatsViewModel(it.chat, it.auth, it.socket) }
    val s by vm.state.collectAsState()
    val top = padding.calculateTopPadding()
    val bottom = padding.calculateBottomPadding()

    Box(Modifier.fillMaxSize()) {
        PullToRefreshBox(isRefreshing = s.refreshing && s.totalCached > 0, onRefresh = vm::refresh, modifier = Modifier.fillMaxSize().padding(top = top)) {
            when {
                s.items.isEmpty() && s.refreshing && s.totalCached == 0 -> Column { SearchField(s.query, vm::onQuery); ListSkeleton() }
                s.items.isEmpty() && s.error != null && s.totalCached == 0 -> ErrorBox(s.error!!, onRetry = vm::refresh)
                else -> LazyColumn(Modifier.fillMaxSize().glassSource(), contentPadding = PaddingValues(bottom = bottom + 88.dp)) {
                    item(key = "search") { SearchField(s.query, vm::onQuery) }
                    item(key = "banner") { ConnectionBanner(s.connection) }
                    if (s.error != null) item(key = "err") {
                        Text("Couldn't refresh: ${s.error!!.message}", color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(Spacing.md), style = MaterialTheme.typography.bodyMedium)
                    }
                    when {
                        s.items.isEmpty() && s.query.isNotBlank() -> item(key = "nomatch") {
                            Box(Modifier.fillParentMaxHeight(0.6f)) { EmptyState(Icons.Filled.Search, "No matches", "Nothing matches “${s.query}”.") }
                        }
                        s.items.isEmpty() -> item(key = "empty") {
                            Box(Modifier.fillParentMaxHeight(0.7f)) {
                                EmptyState(
                                    Icons.Filled.ChatBubbleOutline,
                                    "No conversations yet",
                                    "Add a contact, then start a chat. Or try the Cipher tab on your own.",
                                    action = { SecondaryButton("Start a chat", onNewChat, Modifier.fillMaxWidth(0.6f)) },
                                )
                            }
                        }
                        else -> items(s.items, key = { it.id }) { item ->
                            Box(Modifier.animateItem()) { ConversationRow(item) { onOpenChat(item.id) } }
                        }
                    }
                }
            }
        }
        NewChatButton(onNewChat, Modifier.align(Alignment.BottomEnd).padding(end = Spacing.md, bottom = bottom + Spacing.md))
    }
}

@Composable
private fun NewChatButton(onClick: () -> Unit, modifier: Modifier) {
    val source = remember { MutableInteractionSource() }
    Box(
        modifier.size(56.dp).pressScale(source, 0.92f)
            .background(MaterialTheme.colorScheme.primary, MaterialTheme.shapes.large)
            .clickable(interactionSource = source, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Icon(Icons.Filled.Add, contentDescription = "New conversation", tint = MaterialTheme.colorScheme.onPrimary) }
}

@Composable
private fun SearchField(query: String, onQuery: (String) -> Unit) {
    OutlinedTextField(
        value = query,
        onValueChange = onQuery,
        placeholder = { Text("Search chats", color = MaterialTheme.colorScheme.onSurfaceVariant) },
        leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant) },
        trailingIcon = {
            AnimatedVisibility(query.isNotEmpty(), enter = fadeIn(), exit = fadeOut()) {
                IconButton(onClick = { onQuery("") }) { Icon(Icons.Filled.Clear, contentDescription = "Clear search") }
            }
        },
        singleLine = true,
        shape = MaterialTheme.shapes.medium,
        colors = glassFieldColors(),
        modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.md, vertical = Spacing.sm),
    )
}

@Composable
fun ConnectionBanner(state: ConnectionState) {
    AnimatedVisibility(state != ConnectionState.CONNECTED, enter = fadeIn() + expandVertically(), exit = fadeOut() + shrinkVertically()) {
        Surface(color = MaterialTheme.colorScheme.tertiaryContainer, modifier = Modifier.fillMaxWidth()) {
            Text(
                if (state == ConnectionState.CONNECTING) "Connecting…" else "Offline · showing saved messages. New ones will send when you reconnect.",
                modifier = Modifier.padding(horizontal = Spacing.md, vertical = 8.dp),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onTertiaryContainer,
            )
        }
    }
}

/** A ruled row (reference: lists are rows separated by hairlines, not boxed tiles). */
@Composable
private fun ConversationRow(item: ConversationItem, onClick: () -> Unit) {
    Column(Modifier.fillMaxWidth().clickableScale(onClick)) {
        Row(Modifier.padding(horizontal = Spacing.md, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Avatar(item.title, size = 48)
            Spacer(Modifier.width(Spacing.md))
            Column(Modifier.weight(1f)) {
                Text(
                    item.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = if (item.unread > 0) FontWeight.Bold else FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.size(2.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (item.previewIsCipher) {
                        Icon(Icons.Filled.School, contentDescription = "Cipher message", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(4.dp))
                    }
                    Text(
                        item.preview,
                        style = MaterialTheme.typography.bodyLarge.copy(fontSize = 15.sp),
                        color = if (item.unread > 0) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Spacer(Modifier.width(Spacing.sm))
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                item.timeMs?.let { Text(formatListTime(it), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                if (item.unread > 0) {
                    Box(
                        Modifier.background(MaterialTheme.colorScheme.primary, MaterialTheme.shapes.extraSmall).padding(horizontal = 7.dp, vertical = 2.dp),
                    ) {
                        Text(
                            if (item.unread > 99) "99+" else item.unread.toString(),
                            color = MaterialTheme.colorScheme.onPrimary,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
            }
        }
        Hairline(Modifier.padding(start = Spacing.md + 48.dp + Spacing.md))
    }
}
