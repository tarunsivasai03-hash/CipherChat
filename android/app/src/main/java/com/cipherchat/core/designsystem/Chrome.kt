package com.cipherchat.core.designsystem

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/**
 * Frosted top bar. It spans the status bar, blurs whatever scrolls beneath it, and ends in a hairline.
 * Pass [title] for screens, or [leading]/[trailing] slots for custom headers (chat uses its own).
 */
@Composable
fun GlassTopBar(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    onBack: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
) {
    Column(modifier.fillMaxWidth().glass(RoundedCornerShape(0.dp), GlassLevel.Strong, border = false)) {
        Row(
            Modifier.statusBarsPadding().height(60.dp).padding(horizontal = if (onBack != null) 4.dp else Spacing.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (onBack != null) {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
            }
            Column(Modifier.weight(1f).padding(start = if (onBack != null) 4.dp else 0.dp)) {
                Text(title, style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
            }
            actions()
        }
        Hairline()
    }
}

data class NavItem(val label: String, val icon: ImageVector)

/**
 * Floating frosted tab bar. The selected tab expands to show its label (animated weight + fade);
 * the others collapse to icons. 12 px indicator radius, no pills.
 */
@Composable
fun FloatingNavBar(items: List<NavItem>, selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier.navigationBarsPadding().padding(horizontal = Spacing.md, vertical = Spacing.sm).fillMaxWidth()
            .glass(MaterialTheme.shapes.large, GlassLevel.Strong)
            .padding(6.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        items.forEachIndexed { i, item ->
            val isSelected = i == selected
            val weight by animateFloatAsState(if (isSelected) 2.1f else 1f, spring(Spring.DampingRatioLowBouncy, Spring.StiffnessMediumLow), label = "w")
            val bg by animateFloatAsState(if (isSelected) 1f else 0f, label = "bg")
            val source = remember { MutableInteractionSource() }
            Row(
                Modifier.weight(weight).height(48.dp)
                    .pressScale(source, 0.94f)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.16f * bg), MaterialTheme.shapes.medium)
                    .clickable(interactionSource = source, indication = null) { onSelect(i) },
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                Icon(item.icon, contentDescription = item.label, tint = tint, modifier = Modifier.size(22.dp).graphicsLayer { val s = 1f + 0.08f * bg; scaleX = s; scaleY = s })
                AnimatedVisibility(isSelected, enter = fadeIn() + expandHorizontally(), exit = fadeOut() + shrinkHorizontally()) {
                    Text(item.label, style = MaterialTheme.typography.labelMedium, color = tint, maxLines = 1, modifier = Modifier.padding(start = 6.dp))
                }
            }
        }
    }
}

/** A small solid status dot (connection state). Uses the single emerald accent. */
@Composable
fun StatusDot(on: Boolean, modifier: Modifier = Modifier) {
    val color = if (on) AppTheme.colors.accent else MaterialTheme.colorScheme.outline
    Spacer(modifier.size(8.dp).background(color, androidx.compose.foundation.shape.CircleShape))
}

/** Standard pushed screen: transparent page, frosted top bar with back, optional glass snackbar. Content scrolls under the bar. */
@Composable
fun SubScreen(
    title: String,
    onBack: () -> Unit,
    snackbar: androidx.compose.material3.SnackbarHostState? = null,
    actions: @Composable RowScope.() -> Unit = {},
    content: @Composable (androidx.compose.foundation.layout.PaddingValues) -> Unit,
) {
    androidx.compose.material3.Scaffold(
        containerColor = androidx.compose.ui.graphics.Color.Transparent,
        contentWindowInsets = androidx.compose.foundation.layout.WindowInsets(0),
        topBar = { GlassTopBar(title, onBack = onBack, actions = actions) },
        snackbarHost = { if (snackbar != null) GlassSnackbarHost(snackbar) },
        content = content,
    )
}

@Composable
fun GlassSnackbarHost(state: androidx.compose.material3.SnackbarHostState, modifier: Modifier = Modifier) {
    androidx.compose.material3.SnackbarHost(state, modifier.navigationBarsPadding().padding(Spacing.md)) { data ->
        Text(
            data.visuals.message,
            Modifier.fillMaxWidth().glass(MaterialTheme.shapes.medium, GlassLevel.Strong).padding(horizontal = 16.dp, vertical = 14.dp),
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}
