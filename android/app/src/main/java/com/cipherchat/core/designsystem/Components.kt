package com.cipherchat.core.designsystem

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.cipherchat.core.common.AppError
import com.cipherchat.domain.EncryptionIndicator
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun LoadingBox(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary, strokeWidth = 2.dp, modifier = Modifier.size(28.dp))
    }
}

/** Skeleton rows that match the shape of conversation rows, shown instead of a spinner on first load. */
@Composable
fun ListSkeleton(rows: Int = 7, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth().padding(horizontal = Spacing.md, vertical = Spacing.sm), verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
        repeat(rows) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(48.dp).shimmer(RoundedCornerShape(12.dp)))
                Spacer(Modifier.size(Spacing.md))
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(Modifier.height(14.dp).fillMaxWidth(0.45f).shimmer(RoundedCornerShape(4.dp)))
                    Box(Modifier.height(12.dp).fillMaxWidth(0.8f).shimmer(RoundedCornerShape(4.dp)))
                }
            }
        }
    }
}

@Composable
fun ErrorBox(error: AppError, onRetry: (() -> Unit)?, modifier: Modifier = Modifier) {
    Column(
        modifier.fillMaxSize().padding(Spacing.lg),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(Icons.Filled.ErrorOutline, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(36.dp))
        Spacer(Modifier.height(Spacing.md))
        Text(error.message, style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center)
        if (onRetry != null) {
            Spacer(Modifier.height(Spacing.lg))
            SecondaryButton("Try again", onRetry, Modifier.fillMaxWidth(0.6f))
        }
    }
}

@Composable
fun EmptyState(icon: ImageVector, title: String, body: String, modifier: Modifier = Modifier, action: @Composable (() -> Unit)? = null) {
    Column(
        modifier.fillMaxSize().padding(Spacing.lg),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.size(72.dp).glass(RoundedCornerShape(20.dp), GlassLevel.Strong), contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(32.dp))
        }
        Spacer(Modifier.height(Spacing.lg))
        Text(title, style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
        Spacer(Modifier.height(Spacing.sm))
        Text(body, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        if (action != null) {
            Spacer(Modifier.height(Spacing.md))
            action()
        }
    }
}

/** Inline, always-visible message for form errors. */
@Composable
fun FormError(message: String?, modifier: Modifier = Modifier) {
    if (message != null) {
        Text(message, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium, modifier = modifier.semantics { contentDescription = "Error: $message" })
    }
}

/** Reference `input`: 12 px radius, hairline border that becomes a 2 px ink outline on focus. */
@Composable
fun AppTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    password: Boolean = false,
    keyboardType: KeyboardType = KeyboardType.Text,
    singleLine: Boolean = true,
    supportingText: String? = null,
    isError: Boolean = false,
    enabled: Boolean = true,
) {
    var visible by remember { mutableStateOf(false) }
    val c = AppTheme.colors
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        modifier = modifier.fillMaxWidth(),
        singleLine = singleLine,
        enabled = enabled,
        isError = isError,
        shape = MaterialTheme.shapes.medium,
        colors = glassFieldColors(),
        supportingText = supportingText?.let { { Text(it) } },
        visualTransformation = if (password && !visible) PasswordVisualTransformation() else VisualTransformation.None,
        keyboardOptions = KeyboardOptions(keyboardType = if (password) KeyboardType.Password else keyboardType),
        trailingIcon = if (password) {
            {
                IconButton(onClick = { visible = !visible }) {
                    Icon(
                        if (visible) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                        contentDescription = if (visible) "Hide password" else "Show password",
                    )
                }
            }
        } else null,
    )
}

@Composable
fun glassFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = MaterialTheme.colorScheme.primary,
    unfocusedBorderColor = AppTheme.colors.hairline,
    errorBorderColor = MaterialTheme.colorScheme.error,
    focusedContainerColor = AppTheme.colors.glassFill,
    unfocusedContainerColor = AppTheme.colors.glassFill,
    focusedLabelColor = MaterialTheme.colorScheme.primary,
    unfocusedLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
    cursorColor = MaterialTheme.colorScheme.primary,
)

/** Reference `button-primary`: solid ink, 8 px radius. 48 dp tall for touch. Content cross-fades to a spinner. */
@Composable
fun PrimaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true, loading: Boolean = false) {
    val source = remember { MutableInteractionSource() }
    Button(
        onClick = onClick,
        enabled = enabled && !loading,
        interactionSource = source,
        modifier = modifier.fillMaxWidth().height(48.dp).pressScale(source),
        shape = MaterialTheme.shapes.small,
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
            disabledContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.35f),
            disabledContentColor = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.7f),
        ),
    ) {
        AnimatedContent(loading, transitionSpec = { fadeIn(tween(160)) togetherWith fadeOut(tween(120)) }, label = "btn") { busy ->
            if (busy) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
            else Text(text, style = MaterialTheme.typography.labelLarge)
        }
    }
}

/** Reference `button-secondary`: hairline outline, ink label. */
@Composable
fun SecondaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    val source = remember { MutableInteractionSource() }
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        interactionSource = source,
        modifier = modifier.height(48.dp).pressScale(source),
        shape = MaterialTheme.shapes.small,
        border = BorderStroke(1.dp, AppTheme.colors.hairline),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.onSurface, containerColor = AppTheme.colors.glassFill),
    ) { Text(text, style = MaterialTheme.typography.labelLarge) }
}

/** Warm, quiet avatar tints (no saturated colours), chosen from the name so a person always looks the same. */
@Composable
fun Avatar(name: String, modifier: Modifier = Modifier, size: Int = 44) {
    val dark = AppTheme.colors.isDark
    val tints = if (dark) {
        listOf(0xFF3D3A33, 0xFF34322E, 0xFF3A3630, 0xFF2F2E2B, 0xFF403C35)
    } else {
        listOf(0xFFE3DACC, 0xFFDDD6C8, 0xFFE8DDD0, 0xFFD6D0C2, 0xFFE9E1D3)
    }
    val tint = Color(tints[Math.floorMod(name.hashCode(), tints.size)])
    val initials = name.trim().split(" ").filter { it.isNotEmpty() }.take(2).joinToString("") { it.first().uppercase() }.ifEmpty { "?" }
    Box(
        modifier.size(size.dp).background(tint, RoundedCornerShape((size / 4).dp)),
        contentAlignment = Alignment.Center,
    ) {
        Text(initials, color = MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
    }
}

/** Compact status chip for a security state. Never reads as "secure" unless the state is a genuinely verified one. */
@Composable
fun EncryptionChip(indicator: EncryptionIndicator, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    val (icon, container, content) = when (indicator) {
        EncryptionIndicator.E2E_VERIFIED -> Triple(Icons.Filled.Lock, AppTheme.colors.accent.copy(alpha = 0.16f), scheme.onSurface)
        EncryptionIndicator.EDUCATIONAL_CIPHER -> Triple(Icons.Filled.School, scheme.secondaryContainer, scheme.onSecondaryContainer)
        EncryptionIndicator.VERIFICATION_FAILED -> Triple(Icons.Filled.ErrorOutline, scheme.errorContainer, scheme.onErrorContainer)
        else -> Triple(Icons.Filled.LockOpen, scheme.tertiaryContainer, scheme.onTertiaryContainer)
    }
    Surface(color = container, contentColor = content, shape = MaterialTheme.shapes.extraSmall, modifier = modifier) {
        Row(Modifier.padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(13.dp))
            Spacer(Modifier.size(6.dp))
            Text(indicator.title, style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
fun EncryptionBanner(indicator: EncryptionIndicator, modifier: Modifier = Modifier) {
    Column(
        modifier.fillMaxWidth().padding(horizontal = Spacing.md, vertical = Spacing.sm)
            .glass(MaterialTheme.shapes.large)
            .padding(Spacing.md),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        EncryptionChip(indicator)
        Text(indicator.explanation, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** Floating frosted container for in-page groups (settings blocks, composer panels). */
@Composable
fun GlassCard(modifier: Modifier = Modifier, contentPadding: PaddingValues = PaddingValues(Spacing.md), content: @Composable () -> Unit) {
    Box(modifier.glass(MaterialTheme.shapes.large).padding(contentPadding)) { content() }
}

/** Reference `eyebrow`. */
@Composable
fun Eyebrow(text: String, modifier: Modifier = Modifier) {
    Text(text.uppercase(), modifier = modifier, style = EyebrowStyle, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

/** The 1 px rule that separates list rows (lists are ruled, not boxed). */
@Composable
fun Hairline(modifier: Modifier = Modifier) {
    HorizontalDivider(modifier, thickness = 1.dp, color = AppTheme.colors.hairline.copy(alpha = if (AppTheme.colors.isDark) 0.9f else 0.7f))
}

private val timeFmt = DateTimeFormatter.ofPattern("HH:mm")
private val dateFmt = DateTimeFormatter.ofPattern("d MMM")
private val dayFmt = DateTimeFormatter.ofPattern("EEEE, d MMMM")

fun formatClock(epochMs: Long): String = timeFmt.format(Instant.ofEpochMilli(epochMs).atZone(ZoneId.systemDefault()))

/** "14:05" for today, "3 Oct" otherwise. */
fun formatListTime(epochMs: Long, today: LocalDate = LocalDate.now()): String {
    val zoned = Instant.ofEpochMilli(epochMs).atZone(ZoneId.systemDefault())
    return if (zoned.toLocalDate() == today) timeFmt.format(zoned) else dateFmt.format(zoned)
}

/** "Today", "Yesterday" or "Friday, 24 October" for day separators in a conversation. */
fun formatDayLabel(epochMs: Long, today: LocalDate = LocalDate.now()): String {
    val d = Instant.ofEpochMilli(epochMs).atZone(ZoneId.systemDefault()).toLocalDate()
    return when (d) {
        today -> "Today"
        today.minusDays(1) -> "Yesterday"
        else -> dayFmt.format(d)
    }
}

fun sameDay(a: Long, b: Long): Boolean {
    val z = ZoneId.systemDefault()
    return Instant.ofEpochMilli(a).atZone(z).toLocalDate() == Instant.ofEpochMilli(b).atZone(z).toLocalDate()
}

/** Selectable option chip (cipher pickers, theme, filters). Reference radius: small controls get 8 px, no pills. */
@Composable
fun SegChip(label: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val bg by androidx.compose.animation.animateColorAsState(
        if (selected) MaterialTheme.colorScheme.primary else AppTheme.colors.glassFill, tween(200), label = "chipBg",
    )
    val fg by androidx.compose.animation.animateColorAsState(
        if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface, tween(200), label = "chipFg",
    )
    val source = remember { MutableInteractionSource() }
    Box(
        modifier.pressScale(source, 0.95f)
            .background(bg, MaterialTheme.shapes.small)
            .border(1.dp, if (selected) bg else AppTheme.colors.hairline, MaterialTheme.shapes.small)
            .clickable(interactionSource = source, indication = null, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 9.dp),
        contentAlignment = Alignment.Center,
    ) { Text(label, color = fg, style = MaterialTheme.typography.labelMedium, maxLines = 1) }
}

/** Small in-row action (36 dp tall): solid ink when [filled], hairline outline otherwise. */
@Composable
fun CompactButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, filled: Boolean = false, enabled: Boolean = true) {
    val source = remember { MutableInteractionSource() }
    val bg = if (filled) MaterialTheme.colorScheme.primary else AppTheme.colors.glassFill
    val fg = if (filled) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
    Box(
        modifier.height(36.dp).pressScale(source, 0.94f)
            .background(if (enabled) bg else bg.copy(alpha = bg.alpha * 0.5f), MaterialTheme.shapes.small)
            .then(if (filled) Modifier else Modifier.border(1.dp, AppTheme.colors.hairline, MaterialTheme.shapes.small))
            .clickable(interactionSource = source, indication = null, enabled = enabled, onClick = onClick)
            .padding(horizontal = 14.dp),
        contentAlignment = Alignment.Center,
    ) { Text(text, color = fg, style = MaterialTheme.typography.labelMedium) }
}
