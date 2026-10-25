package com.cipherchat.feature.cipher

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.cipherchat.core.designsystem.AppTheme
import com.cipherchat.core.designsystem.CompactButton
import com.cipherchat.core.designsystem.EncryptionChip
import com.cipherchat.core.designsystem.Eyebrow
import com.cipherchat.core.designsystem.FormError
import com.cipherchat.core.designsystem.GlassLevel
import com.cipherchat.core.designsystem.PrimaryButton
import com.cipherchat.core.designsystem.ScrambleText
import com.cipherchat.core.designsystem.SecondaryButton
import com.cipherchat.core.designsystem.SegChip
import com.cipherchat.core.designsystem.Spacing
import com.cipherchat.core.designsystem.appear
import com.cipherchat.core.designsystem.glass
import com.cipherchat.core.designsystem.glassFieldColors
import com.cipherchat.core.designsystem.glassSource
import com.cipherchat.domain.EncryptionIndicator
import com.cipherchat.domain.cipher.CipherRegistry

@Composable
fun CipherTab(padding: PaddingValues = PaddingValues()) {
    val vm: CipherViewModel = viewModel()
    val s by vm.state.collectAsState()
    val clipboard = LocalClipboardManager.current

    Column(
        Modifier.fillMaxSize().glassSource().verticalScroll(rememberScrollState())
            .padding(top = padding.calculateTopPadding() + Spacing.md, bottom = padding.calculateBottomPadding() + Spacing.md)
            .padding(horizontal = Spacing.md),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            EncryptionChip(EncryptionIndicator.EDUCATIONAL_CIPHER)
        }

        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            CipherRegistry.all.forEach { c -> SegChip(c.displayName, s.cipher.id == c.id, { vm.selectCipher(c) }) }
        }

        Column(Modifier.fillMaxWidth().appear(0).glass(MaterialTheme.shapes.large).padding(Spacing.md), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(s.cipher.description, style = MaterialTheme.typography.bodyLarge)
            Text("Educational only: do not use this to protect anything private.", style = MaterialTheme.typography.labelSmall, color = AppTheme.colors.warning)
        }

        OutlinedTextField(
            value = s.input,
            onValueChange = vm::onInput,
            label = { Text("Your text") },
            textStyle = MaterialTheme.typography.bodyLarge,
            shape = MaterialTheme.shapes.medium,
            colors = glassFieldColors(),
            modifier = Modifier.fillMaxWidth(),
            minLines = 3,
            maxLines = 8,
        )
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ExampleMessages.forEach { ex -> CompactButton(ex.take(22) + if (ex.length > 22) "…" else "", { vm.useExample(ex) }) }
        }

        if (s.cipher.requiresKey || s.cipher.id == "emoji") {
            OutlinedTextField(
                value = s.key,
                onValueChange = vm::onKey,
                label = { Text("Key") },
                supportingText = { Text(s.keyProblem ?: s.cipher.keyHint) },
                isError = s.keyProblem != null,
                singleLine = true,
                shape = MaterialTheme.shapes.medium,
                colors = glassFieldColors(),
                modifier = Modifier.fillMaxWidth(),
            )
        } else {
            Text("This cipher has no key.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm), modifier = Modifier.fillMaxWidth()) {
            PrimaryButton("Encrypt", vm::encrypt, Modifier.weight(1f))
            SecondaryButton("Decrypt", vm::decrypt, Modifier.weight(1f))
        }
        FormError(s.error)

        Column(Modifier.fillMaxWidth().glass(MaterialTheme.shapes.large, GlassLevel.Strong).padding(Spacing.md)) {
            Eyebrow("Output")
            Spacer(Modifier.height(Spacing.sm))
            if (s.output.isEmpty()) {
                Text("Result appears here", style = MaterialTheme.typography.bodyLarge.copy(fontFamily = FontFamily.Monospace), color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                ScrambleText(s.output, MaterialTheme.typography.bodyLarge.copy(fontFamily = FontFamily.Monospace), color = MaterialTheme.colorScheme.onSurface)
            }
            Spacer(Modifier.height(Spacing.md))
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CompactButton("Copy output", { clipboard.setText(AnnotatedString(s.output)) }, enabled = s.output.isNotEmpty())
                CompactButton("Use as input", vm::swap, enabled = s.output.isNotEmpty())
                CompactButton("Copy input", { clipboard.setText(AnnotatedString(s.input)) }, enabled = s.input.isNotEmpty())
                CompactButton("Clear", vm::clear)
            }
        }
    }
}
