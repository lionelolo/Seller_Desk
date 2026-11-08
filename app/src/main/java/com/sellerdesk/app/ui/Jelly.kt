package com.sellerdesk.app.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonColors
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs

val Pill = RoundedCornerShape(50)
val Soft = RoundedCornerShape(18.dp)
val Big = RoundedCornerShape(28.dp)

/**
 * Pops an element in like a blob of jelly: it starts squashed and small, then overshoots and
 * wobbles to rest. Width and height use different springs so the wobble feels soft, not mechanical.
 */
fun Modifier.jellyIn(
    delayMs: Int = 0,
    from: Float = 0.8f,
    origin: TransformOrigin = TransformOrigin.Center,
): Modifier = composed {
    val sx = remember { Animatable(from + (1f - from) * 0.4f) }
    val sy = remember { Animatable(from - 0.12f) }
    val fade = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        if (delayMs > 0) delay(delayMs.toLong())
        launch { fade.animateTo(1f, tween(140)) }
        launch { sx.animateTo(1f, spring(dampingRatio = 0.32f, stiffness = 260f)) }
        sy.animateTo(1f, spring(dampingRatio = 0.28f, stiffness = 300f))
    }
    graphicsLayer {
        scaleX = sx.value
        scaleY = sy.value
        alpha = fade.value
        transformOrigin = origin
    }
}

/** Staggered pop-in for the first few items of a list. */
fun Modifier.jellyItem(index: Int): Modifier =
    if (index < 10) jellyIn(delayMs = index * 45, from = 0.88f) else this

/** Squishes while pressed and wobbles back on release. */
fun Modifier.jellyPress(source: InteractionSource, pressedScale: Float = 0.92f): Modifier = composed {
    val pressed by source.collectIsPressedAsState()
    val s by animateFloatAsState(
        targetValue = if (pressed) pressedScale else 1f,
        animationSpec = spring(dampingRatio = 0.28f, stiffness = 520f),
        label = "jellyPress",
    )
    graphicsLayer {
        scaleY = s
        scaleX = 1f + (1f - s) * 0.45f
    }
}

/** A slow breathing pulse, used on the alarm screen. */
fun Modifier.jellyPulse(): Modifier = composed {
    val t = rememberInfiniteTransition(label = "pulse")
    val k by t.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(650, easing = LinearEasing), RepeatMode.Reverse),
        label = "k",
    )
    graphicsLayer {
        scaleX = 1f + 0.07f * k
        scaleY = 1f - 0.04f * k
    }
}

// ───────────── Buttons that squish ─────────────

@Composable
fun JButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    shape: Shape = Pill,
    colors: ButtonColors = ButtonDefaults.buttonColors(),
    content: @Composable RowScope.() -> Unit,
) {
    val src = remember { MutableInteractionSource() }
    Button(
        onClick = onClick,
        modifier = modifier.jellyPress(src),
        enabled = enabled,
        shape = shape,
        colors = colors,
        interactionSource = src,
        content = content,
    )
}

@Composable
fun JOutlinedButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    shape: Shape = Pill,
    colors: ButtonColors = ButtonDefaults.outlinedButtonColors(),
    border: BorderStroke? = null,
    content: @Composable RowScope.() -> Unit,
) {
    val src = remember { MutableInteractionSource() }
    val line = MaterialTheme.colorScheme.onBackground.copy(alpha = if (enabled) 1f else 0.2f)
    OutlinedButton(
        onClick = onClick,
        modifier = modifier.jellyPress(src),
        enabled = enabled,
        shape = shape,
        colors = colors,
        border = border ?: BorderStroke(1.2.dp, line),
        interactionSource = src,
        content = content,
    )
}

@Composable
fun JTextButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable RowScope.() -> Unit,
) {
    val src = remember { MutableInteractionSource() }
    TextButton(
        onClick = onClick,
        modifier = modifier.jellyPress(src, 0.88f),
        enabled = enabled,
        interactionSource = src,
        content = content,
    )
}

/** Black pill when selected, light grey when not. */
@Composable
fun JFilterChip(selected: Boolean, onClick: () -> Unit, label: @Composable () -> Unit) {
    val src = remember { MutableInteractionSource() }
    val bg = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant
    val fg = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
    Box(
        Modifier
            .jellyPress(src, 0.9f)
            .clip(Pill)
            .background(bg)
            .clickable(interactionSource = src, indication = null, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 9.dp),
        contentAlignment = Alignment.Center,
    ) {
        CompositionLocalProvider(LocalContentColor provides fg) {
            androidx.compose.material3.ProvideTextStyle(MaterialTheme.typography.labelLarge) { label() }
        }
    }
}

// ───────────── Bottom navigation: a black blob that slides and stretches ─────────────

@Composable
fun BottomTabs(labels: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    val pos by animateFloatAsState(
        targetValue = selected.toFloat(),
        animationSpec = spring(dampingRatio = 0.42f, stiffness = 240f),
        label = "tabPos",
    )
    // The further the blob still has to travel, the more it stretches.
    val stretch = 1f + (abs(selected - pos)).coerceAtMost(1.6f) * 0.3f

    Box(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp)
            .clip(Pill)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(4.dp)
    ) {
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val tabW = maxWidth / labels.size
            Box(
                Modifier
                    .offset(x = tabW * pos)
                    .width(tabW)
                    .height(44.dp)
                    .graphicsLayer {
                        scaleX = stretch
                        scaleY = 1f - (stretch - 1f) * 0.5f
                    }
                    .clip(Pill)
                    .background(MaterialTheme.colorScheme.primary)
            )
            Row(Modifier.fillMaxWidth()) {
                labels.forEachIndexed { i, name ->
                    val src = remember { MutableInteractionSource() }
                    Box(
                        Modifier
                            .weight(1f)
                            .height(44.dp)
                            .jellyPress(src, 0.9f)
                            .clip(Pill)
                            .clickable(interactionSource = src, indication = null) { onSelect(i) },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            name,
                            style = MaterialTheme.typography.labelLarge,
                            maxLines = 1,
                            fontWeight = if (i == selected) FontWeight.Bold else FontWeight.Medium,
                            color = if (i == selected) MaterialTheme.colorScheme.onPrimary
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

// ───────────── Dialog that bounces open ─────────────

/**
 * Drop-in replacement for Material's AlertDialog (same parameter names), drawn as a black-outlined
 * card that wobbles open like jelly.
 */
@Composable
fun AlertDialog(
    onDismissRequest: () -> Unit,
    confirmButton: @Composable () -> Unit,
    dismissButton: (@Composable () -> Unit)? = null,
    title: (@Composable () -> Unit)? = null,
    text: (@Composable () -> Unit)? = null,
) {
    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            shape = RoundedCornerShape(32.dp),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.onSurface),
            modifier = Modifier
                .padding(horizontal = 24.dp)
                .fillMaxWidth()
                .heightIn(max = 560.dp)
                .jellyIn(from = 0.6f),
        ) {
            Column(Modifier.padding(start = 24.dp, end = 16.dp, top = 24.dp, bottom = 12.dp)) {
                if (title != null) {
                    androidx.compose.material3.ProvideTextStyle(MaterialTheme.typography.titleLarge) { title() }
                    Spacer(Modifier.height(12.dp))
                }
                if (text != null) {
                    Box(Modifier.weight(1f, fill = false).padding(end = 8.dp)) {
                        androidx.compose.material3.ProvideTextStyle(MaterialTheme.typography.bodyMedium) { text() }
                    }
                }
                Spacer(Modifier.height(12.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    dismissButton?.invoke()
                    confirmButton()
                }
            }
        }
    }
}
