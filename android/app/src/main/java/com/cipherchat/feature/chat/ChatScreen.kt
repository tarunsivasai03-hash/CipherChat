package com.cipherchat.feature.chat

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Reply
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.School
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.cipherchat.core.designsystem.AppTheme
import com.cipherchat.core.designsystem.Avatar
import com.cipherchat.core.designsystem.EncryptionBanner
import com.cipherchat.core.designsystem.EncryptionChip
import com.cipherchat.core.designsystem.ErrorBox
import com.cipherchat.core.designsystem.GlassLevel
import com.cipherchat.core.designsystem.Hairline
import com.cipherchat.core.designsystem.ListSkeleton
import com.cipherchat.core.designsystem.SegChip
import com.cipherchat.core.designsystem.Spacing
import com.cipherchat.core.designsystem.TypingDots
import com.cipherchat.core.designsystem.formatClock
import com.cipherchat.core.designsystem.formatDayLabel
import com.cipherchat.core.designsystem.glass
import com.cipherchat.core.designsystem.glassSource
import com.cipherchat.core.designsystem.pressScale
import com.cipherchat.domain.EncryptionPolicy
import com.cipherchat.domain.cipher.CipherRegistry
import com.cipherchat.feature.common.appViewModel
import com.cipherchat.feature.home.ConnectionBanner
import kotlinx.coroutines.launch

private val QuickReactions = listOf("👍", "❤️", "😂", "😮", "🙏")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(conversationId: Long, onBack: () -> Unit, onSettings: () -> Unit) {
    val vm = appViewModel(key = "chat-$conversationId") { ChatViewModel(conversationId, it.chat, it.auth, it.socket) }
    val s by vm.state.collectAsState()
    var decodeTarget by remember { mutableStateOf<MessageUi?>(null) }
    var actionTarget by remember { mutableStateOf<MessageUi?>(null) }

    Scaffold(
        containerColor = Color.Transparent,
        contentWindowInsets = WindowInsets(0),
        topBar = { ChatHeader(s, onBack, onSettings) },
        bottomBar = { Composer(s.composer, vm) },
    ) { padding ->
        Box(Modifier.fillMaxSize()) {
            when {
                s.loading -> Box(Modifier.padding(top = padding.calculateTopPadding())) { ListSkeleton(5) }
                s.error != null && s.items.isEmpty() -> ErrorBox(s.error!!, onRetry = vm::loadLatest, modifier = Modifier.padding(top = padding.calculateTopPadding()))
                s.items.isEmpty() -> EmptyChat(Modifier.padding(top = padding.calculateTopPadding(), bottom = padding.calculateBottomPadding()))
                else -> MessageList(s, vm, padding, onActions = { actionTarget = it })
            }
            Box(Modifier.padding(top = padding.calculateTopPadding())) { ConnectionBanner(s.connection) }
            s.actionError?.let {
                Box(
                    Modifier.align(Alignment.BottomCenter).padding(bottom = padding.calculateBottomPadding() + 8.dp, start = 16.dp, end = 16.dp)
                        .glass(MaterialTheme.shapes.medium, GlassLevel.Strong).clickable { vm.dismissActionError() }.padding(12.dp),
                ) { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium) }
            }
        }
    }

    actionTarget?.let { target ->
        MessageSheet(
            target, s.decoded.containsKey(target.key),
            onDismiss = { actionTarget = null },
            onReact = { vm.react(target, it); actionTarget = null },
            onReply = { vm.setReply(target); actionTarget = null },
            onDecode = { actionTarget = null; if (s.decoded.containsKey(target.key)) vm.hideDecoded(target) else decodeTarget = target },
            onDelete = { vm.delete(target); actionTarget = null },
        )
    }
    decodeTarget?.let { DecodeDialog(it, vm, onDismiss = { decodeTarget = null }) }
}

@Composable
private fun EmptyChat(modifier: Modifier) {
    Column(modifier.fillMaxSize().padding(Spacing.lg), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        Text("Say hello", style = MaterialTheme.typography.displayLarge.copy(fontSize = androidx.compose.ui.unit.TextUnit(34f, androidx.compose.ui.unit.TextUnitType.Sp)))
        Spacer(Modifier.height(Spacing.sm))
        Text(
            "Send a message, or tap the cap icon below to code it with a cipher.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun ChatHeader(s: ChatUiState, onBack: () -> Unit, onSettings: () -> Unit) {
    var explain by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth().glass(RoundedCornerShape(0.dp), GlassLevel.Strong, border = false)) {
        Row(Modifier.statusBarsPadding().height(64.dp).padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
            Avatar(s.title.ifEmpty { "?" }, size = 40)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(s.title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val sub = when {
                        s.typingNames.isNotEmpty() -> "typing"
                        s.isGroup -> "${s.memberCount} members"
                        else -> null
                    }
                    if (sub != null) Text(sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (s.typingNames.isNotEmpty()) TypingDots()
                }
            }
            IconButton(onClick = onSettings) { Icon(Icons.Filled.Info, contentDescription = "Conversation settings") }
        }
        Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            EncryptionChip(s.indicator, Modifier.clickable { explain = !explain })
            s.disappearSeconds?.let {
                Spacer(Modifier.width(Spacing.sm))
                Text("Disappears after ${describeTimer(it)}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        AnimatedVisibility(explain, enter = fadeIn() + expandVertically(), exit = fadeOut() + shrinkVertically()) {
            Text(
                s.indicator.explanation,
                Modifier.padding(start = 16.dp, end = 16.dp, bottom = 12.dp),
                style = MaterialTheme.typography.bodyLarge.copy(fontSize = androidx.compose.ui.unit.TextUnit(15f, androidx.compose.ui.unit.TextUnitType.Sp)),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Hairline()
    }
}

private fun describeTimer(seconds: Int) = when (seconds) {
    10 -> "10 seconds"
    60 -> "1 minute"
    3600 -> "1 hour"
    86400 -> "24 hours"
    else -> "$seconds s"
}

@Composable
private fun MessageList(s: ChatUiState, vm: ChatViewModel, padding: PaddingValues, onActions: (MessageUi) -> Unit) {
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val rows = remember(s.items) { ChatRows.build(s.items, ::formatDayLabel) }
    val reversed = remember(rows) { rows.asReversed() }
    // reverseLayout: index 0 is the newest row; the end of the list is the oldest loaded one.
    val nearOldest by remember(reversed.size) {
        derivedStateOf {
            val last = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            reversed.isNotEmpty() && last >= reversed.size - 4
        }
    }
    val showDown by remember { derivedStateOf { listState.firstVisibleItemIndex > 3 } }
    LaunchedEffect(nearOldest, s.hasMore) { if (nearOldest && s.hasMore) vm.loadOlder() }
    LaunchedEffect(reversed.firstOrNull()?.key) { if (listState.firstVisibleItemIndex <= 1) listState.animateScrollToItem(0) }

    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            reverseLayout = true,
            modifier = Modifier.fillMaxSize().glassSource(),
            contentPadding = PaddingValues(
                start = 16.dp, end = 16.dp,
                top = padding.calculateTopPadding() + 12.dp,
                bottom = padding.calculateBottomPadding() + 8.dp,
            ),
        ) {
            items(reversed, key = { it.key }) { row ->
                Box(Modifier.animateItem()) {
                    when (row) {
                        is ChatRow.Day -> DayChip(row.label)
                        is ChatRow.Msg -> Bubble(row, s, vm, onActions)
                    }
                }
            }
            if (s.loadingOlder) item(key = "older") {
                Box(Modifier.fillMaxWidth().padding(Spacing.sm), contentAlignment = Alignment.Center) {
                    androidx.compose.material3.CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onSurface)
                }
            }
        }
        AnimatedVisibility(
            showDown,
            modifier = Modifier.align(Alignment.BottomEnd).padding(end = 16.dp, bottom = padding.calculateBottomPadding() + 12.dp),
            enter = scaleIn(spring(Spring.DampingRatioMediumBouncy)) + fadeIn(),
            exit = scaleOut() + fadeOut(),
        ) {
            Box(
                Modifier.size(44.dp).glass(MaterialTheme.shapes.medium, GlassLevel.Strong).clickable { scope.launch { listState.animateScrollToItem(0) } },
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Filled.KeyboardArrowDown, contentDescription = "Jump to latest") }
        }
    }
}

@Composable
private fun DayChip(label: String) {
    Box(Modifier.fillMaxWidth().padding(vertical = 14.dp), contentAlignment = Alignment.Center) {
        Text(
            label,
            Modifier.background(AppTheme.colors.glassFillStrong, MaterialTheme.shapes.extraSmall).padding(horizontal = 10.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun Bubble(row: ChatRow.Msg, s: ChatUiState, vm: ChatViewModel, onActions: (MessageUi) -> Unit) {
    val m = row.m
    val colors = AppTheme.colors
    val mine = m.mine
    val big = 18.dp
    val joined = 5.dp
    val tail = 4.dp
    val shape = RoundedCornerShape(
        topStart = if (!mine && !row.firstInRun) joined else big,
        topEnd = if (mine && !row.firstInRun) joined else big,
        bottomEnd = if (mine) (if (row.lastInRun) tail else joined) else big,
        bottomStart = if (!mine) (if (row.lastInRun) tail else joined) else big,
    )
    val fg = if (mine) colors.onBubbleOut else MaterialTheme.colorScheme.onSurface
    val muted = fg.copy(alpha = 0.62f)
    val decoded = s.decoded[m.key]

    Column(
        Modifier.fillMaxWidth().padding(top = if (row.firstInRun) 10.dp else 2.dp),
        horizontalAlignment = if (mine) Alignment.End else Alignment.Start,
    ) {
        if (s.isGroup && !mine && row.firstInRun) {
            Text(m.senderName, Modifier.padding(start = 6.dp, bottom = 3.dp), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        val source = remember { MutableInteractionSource() }
        Column(
            Modifier.widthIn(max = 312.dp).pressScale(source, 0.985f)
                .clip(shape)
                .background(if (mine) colors.bubbleOut else colors.glassFillStrong)
                .then(if (mine) Modifier else Modifier.border(1.dp, Brush.verticalGradient(listOf(colors.glassBorderTop, colors.glassBorderBottom)), shape))
                .combinedClickable(
                    interactionSource = source,
                    indication = null,
                    onClick = { if (m.label == DeliveryLabel.FAILED) vm.retry(m) },
                    onLongClick = { onActions(m) },
                )
                .semantics { contentDescription = "${m.senderName}: ${m.text}" }
                .padding(horizontal = 14.dp, vertical = 9.dp),
        ) {
            m.replyPreview?.let {
                Row(Modifier.padding(bottom = 6.dp).height(androidx.compose.foundation.layout.IntrinsicSize.Min)) {
                    Box(Modifier.width(3.dp).fillMaxSize().background(fg.copy(alpha = 0.5f), RoundedCornerShape(2.dp)))
                    Text(it, Modifier.padding(start = 8.dp), style = MaterialTheme.typography.bodyMedium, color = muted, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
            }
            if (m.kind == "cipher") {
                EncryptionChip(EncryptionPolicy.forMessageKind("cipher"), Modifier.padding(bottom = 6.dp))
            }
            Text(m.text, style = MaterialTheme.typography.bodyLarge, color = fg, fontFamily = if (m.kind == "cipher") FontFamily.Monospace else null)
            AnimatedVisibility(decoded != null, enter = fadeIn() + expandVertically(), exit = fadeOut() + shrinkVertically()) {
                Column(Modifier.padding(top = 8.dp).background(fg.copy(alpha = 0.10f), MaterialTheme.shapes.small).padding(10.dp)) {
                    Text("Decoded on this device", style = MaterialTheme.typography.labelSmall, color = muted)
                    Text(decoded.orEmpty(), style = MaterialTheme.typography.bodyLarge, color = fg)
                }
            }
            if (row.lastInRun) {
                Text(formatClock(m.timeMs), Modifier.align(Alignment.End).padding(top = 3.dp), style = MaterialTheme.typography.labelSmall, color = muted)
            }
        }
        if (m.reactions.isNotEmpty()) {
            Row(Modifier.padding(top = 3.dp, start = 4.dp, end = 4.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                m.reactions.forEach { r ->
                    Text(
                        "${r.emoji} ${r.count}",
                        Modifier.background(if (r.mine) MaterialTheme.colorScheme.primary.copy(alpha = 0.22f) else colors.glassFillStrong, MaterialTheme.shapes.extraSmall)
                            .border(1.dp, colors.hairline, MaterialTheme.shapes.extraSmall)
                            .clickable { vm.react(m, r.emoji) }.padding(horizontal = 7.dp, vertical = 2.dp),
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
            }
        }
        val label = m.label
        if (label != null && (row.lastInRun || label == DeliveryLabel.FAILED || label == DeliveryLabel.SENDING)) {
            Text(
                when (label) {
                    DeliveryLabel.SENDING -> "Sending…"
                    DeliveryLabel.SENT -> "Sent"
                    DeliveryLabel.DELIVERED -> if (s.isGroup) "Delivered to ${m.deliveredCount}" else "Delivered"
                    DeliveryLabel.FAILED -> "Failed · tap to retry"
                },
                Modifier.padding(top = 3.dp, end = 4.dp),
                style = MaterialTheme.typography.labelSmall,
                color = if (label == DeliveryLabel.FAILED) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (label == DeliveryLabel.FAILED) {
                m.failureReason?.let { Text(it, Modifier.padding(end = 4.dp), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error) }
                TextButton(onClick = { vm.discard(m) }) { Text("Discard", color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
        }
    }
}

@Composable
private fun Composer(c: ComposerState, vm: ChatViewModel) {
    val colors = AppTheme.colors
    Column(
        Modifier.imePadding().navigationBarsPadding().padding(horizontal = 12.dp, vertical = 8.dp)
            .glass(MaterialTheme.shapes.extraLarge, GlassLevel.Strong)
            .padding(10.dp)
            .animateContentSize(spring(Spring.DampingRatioNoBouncy, Spring.StiffnessMedium)),
    ) {
        AnimatedVisibility(c.replyTo != null, enter = fadeIn() + expandVertically(), exit = fadeOut() + shrinkVertically()) {
            c.replyTo?.let {
                Row(Modifier.fillMaxWidth().padding(bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.width(3.dp).height(32.dp).background(MaterialTheme.colorScheme.primary, RoundedCornerShape(2.dp)))
                    Column(Modifier.weight(1f).padding(start = 10.dp)) {
                        Text("Replying to ${it.senderName}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(it.text, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium)
                    }
                    IconButton(onClick = { vm.setReply(null) }) { Icon(Icons.Filled.Close, contentDescription = "Cancel reply") }
                }
            }
        }
        AnimatedVisibility(c.cipherMode, enter = fadeIn() + expandVertically(), exit = fadeOut() + shrinkVertically()) {
            Column(Modifier.padding(bottom = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    CipherRegistry.all.forEach { cipher -> SegChip(cipher.displayName, c.cipher.id == cipher.id, { vm.selectCipher(cipher) }) }
                }
                if (c.cipher.requiresKey || c.cipher.id == "emoji") {
                    OutlinedTextField(
                        value = c.cipherKey,
                        onValueChange = vm::onCipherKey,
                        label = { Text("Key (never sent)") },
                        supportingText = { Text(c.cipher.keyHint) },
                        singleLine = true,
                        shape = MaterialTheme.shapes.medium,
                        colors = com.cipherchat.core.designsystem.glassFieldColors(),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                Text(
                    "Educational cipher: not secure. Share the key with your friend another way so they can decode it.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        AnimatedVisibility(c.error != null) {
            Text(c.error.orEmpty(), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(bottom = 6.dp))
        }
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            val toggleBg by animateColorAsState(if (c.cipherMode) MaterialTheme.colorScheme.primary else Color.Transparent, tween(200), label = "toggle")
            val toggleFg by animateColorAsState(if (c.cipherMode) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant, tween(200), label = "toggleFg")
            Box(
                Modifier.size(44.dp).background(toggleBg, MaterialTheme.shapes.medium).clickable { vm.setCipherMode(!c.cipherMode) },
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Filled.School, contentDescription = if (c.cipherMode) "Switch to plain message" else "Send as cipher", tint = toggleFg) }
            TextField(
                value = c.text,
                onValueChange = vm::onText,
                placeholder = { Text(if (c.cipherMode) "Message to encode with ${c.cipher.displayName}" else "Message", color = MaterialTheme.colorScheme.onSurfaceVariant) },
                textStyle = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.weight(1f),
                shape = MaterialTheme.shapes.medium,
                maxLines = 5,
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.Transparent, unfocusedContainerColor = Color.Transparent,
                    focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent, disabledIndicatorColor = Color.Transparent,
                    cursorColor = MaterialTheme.colorScheme.onSurface,
                ),
            )
            val enabled = c.text.isNotBlank()
            val sendBg by animateColorAsState(if (enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.10f), tween(200), label = "send")
            val sendScale by androidx.compose.animation.core.animateFloatAsState(if (enabled) 1f else 0.9f, spring(Spring.DampingRatioMediumBouncy), label = "sendScale")
            val source = remember { MutableInteractionSource() }
            Box(
                Modifier.size(44.dp).graphicsLayer { scaleX = sendScale; scaleY = sendScale }.pressScale(source, 0.9f)
                    .background(sendBg, MaterialTheme.shapes.medium)
                    .clickable(interactionSource = source, indication = null, enabled = enabled, onClick = vm::send),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Filled.ArrowUpward, contentDescription = "Send", tint = if (enabled) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MessageSheet(
    m: MessageUi,
    decodedShown: Boolean,
    onDismiss: () -> Unit,
    onReact: (String) -> Unit,
    onReply: () -> Unit,
    onDecode: () -> Unit,
    onDelete: () -> Unit,
) {
    val clipboard = LocalClipboardManager.current
    val mine = m.reactions.firstOrNull { it.mine }?.emoji
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(),
        containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f),
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
    ) {
        Column(Modifier.navigationBarsPadding().padding(horizontal = Spacing.md, vertical = Spacing.sm), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            Text(
                m.text, Modifier.fillMaxWidth().glass(MaterialTheme.shapes.medium).padding(12.dp),
                style = MaterialTheme.typography.bodyLarge, maxLines = 3, overflow = TextOverflow.Ellipsis,
            )
            if (m.serverId != null) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    QuickReactions.forEach { e ->
                        val selected = mine == e
                        Box(
                            Modifier.size(52.dp).background(if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.22f) else AppTheme.colors.glassFill, MaterialTheme.shapes.medium)
                                .border(1.dp, AppTheme.colors.hairline, MaterialTheme.shapes.medium).clickable { onReact(e) },
                            contentAlignment = Alignment.Center,
                        ) { Text(e, style = MaterialTheme.typography.titleLarge) }
                    }
                }
            }
            if (m.serverId != null) SheetAction(Icons.AutoMirrored.Filled.Reply, "Reply", onReply)
            SheetAction(Icons.Filled.ContentCopy, "Copy", onClick = { clipboard.setText(AnnotatedString(m.text)); onDismiss() })
            if (m.kind == "cipher") SheetAction(Icons.Filled.LockOpen, if (decodedShown) "Hide decoded text" else "Decode…", onDecode)
            if (m.mine && m.serverId != null) SheetAction(Icons.Filled.DeleteOutline, "Delete for everyone", onDelete, destructive = true)
            Spacer(Modifier.height(Spacing.sm))
        }
    }
}

@Composable
private fun SheetAction(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onClick: () -> Unit, destructive: Boolean = false) {
    val tint = if (destructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
    Row(Modifier.fillMaxWidth().clip(MaterialTheme.shapes.medium).clickable(onClick = onClick).padding(horizontal = 12.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, tint = tint)
        Spacer(Modifier.width(16.dp))
        Text(label, style = MaterialTheme.typography.titleSmall, color = tint)
    }
}

@Composable
private fun DecodeDialog(target: MessageUi, vm: ChatViewModel, onDismiss: () -> Unit) {
    var cipherId by remember { mutableStateOf(CipherRegistry.byId(target.meta ?: "")?.id ?: CipherRegistry.all.first().id) }
    val cipher = CipherRegistry.byId(cipherId)!!
    var key by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        shape = MaterialTheme.shapes.extraLarge,
        title = { Text("Decode message", style = MaterialTheme.typography.titleLarge) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                Text("Decoding happens on this device. The key is not stored or sent.", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    CipherRegistry.all.forEach { c -> SegChip(c.displayName, c.id == cipherId, { cipherId = c.id; error = null }) }
                }
                if (cipher.requiresKey || cipher.id == "emoji") {
                    OutlinedTextField(
                        key, { key = it; error = null }, label = { Text("Key") }, supportingText = { Text(cipher.keyHint) },
                        singleLine = true, isError = error != null, shape = MaterialTheme.shapes.medium, colors = com.cipherchat.core.designsystem.glassFieldColors(),
                    )
                }
                error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium) }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val err = vm.decode(target, cipherId, key)
                if (err == null) onDismiss() else error = err
            }) { Text("Decode", color = MaterialTheme.colorScheme.onSurface) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = MaterialTheme.colorScheme.onSurfaceVariant) } },
    )
}
