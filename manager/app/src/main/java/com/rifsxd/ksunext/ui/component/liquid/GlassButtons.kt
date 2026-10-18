package com.rifsxd.ksunext.ui.component.liquid

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonColors
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ButtonElevation
import androidx.compose.material3.CardColors
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CardElevation
import androidx.compose.material3.IconButtonColors
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

private val DefaultButtonShape: Shape = RoundedCornerShape(18.dp)

/**
 * 按下时轻微回弹 + 高光增强的通用玻璃点击层。
 */
@Composable
private fun Modifier.glassPressable(
    enabled: Boolean,
    interactionSource: MutableInteractionSource,
    onClick: () -> Unit,
    role: Role = Role.Button,
): Modifier {
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed && enabled) 0.965f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMediumLow,
        ),
        label = "glassPressScale",
    )
    return this
        .graphicsLayer {
            scaleX = scale
            scaleY = scale
        }
        .clickable(
            interactionSource = interactionSource,
            indication = null,
            enabled = enabled,
            role = role,
            onClick = onClick,
        )
        .alpha(if (enabled) 1f else 0.45f)
}

@Composable
fun GlassButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    shape: Shape = DefaultButtonShape,
    colors: ButtonColors = ButtonDefaults.buttonColors(),
    elevation: ButtonElevation? = null,
    border: BorderStroke? = null,
    contentPadding: PaddingValues = ButtonDefaults.ContentPadding,
    interactionSource: MutableInteractionSource? = null,
    content: @Composable RowScope.() -> Unit,
) {
    val style = rememberLiquidGlassStyle()
    val source = interactionSource ?: remember { MutableInteractionSource() }
    val accent = colors.accentFor(enabled)

    Box(
        modifier = modifier
            .defaultMinSize(minHeight = 40.dp)
            .clip(shape)
            .liquidGlass(shape, style, elevation = 6.dp, accent = accent, enabled = enabled)
            .glassPressable(enabled, source, onClick),
        contentAlignment = Alignment.Center,
    ) {
        CompositionLocalProvider(LocalContentColor provides colors.contentFor(enabled)) {
            Row(
                modifier = Modifier.padding(contentPadding),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
                content = content,
            )
        }
    }
}

@Composable
fun GlassOutlinedButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    shape: Shape = DefaultButtonShape,
    colors: ButtonColors = ButtonDefaults.outlinedButtonColors(),
    elevation: ButtonElevation? = null,
    border: BorderStroke? = null,
    contentPadding: PaddingValues = ButtonDefaults.ContentPadding,
    interactionSource: MutableInteractionSource? = null,
    content: @Composable RowScope.() -> Unit,
) {
    val style = rememberLiquidGlassStyle()
    val source = interactionSource ?: remember { MutableInteractionSource() }

    Box(
        modifier = modifier
            .defaultMinSize(minHeight = 40.dp)
            .clip(shape)
            .liquidGlass(shape, style, elevation = 4.dp, accent = null, enabled = enabled)
            .glassPressable(enabled, source, onClick),
        contentAlignment = Alignment.Center,
    ) {
        CompositionLocalProvider(LocalContentColor provides colors.contentFor(enabled)) {
            Row(
                modifier = Modifier.padding(contentPadding),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
                content = content,
            )
        }
    }
}

@Composable
fun GlassTextButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    shape: Shape = DefaultButtonShape,
    colors: ButtonColors = ButtonDefaults.textButtonColors(),
    elevation: ButtonElevation? = null,
    border: BorderStroke? = null,
    contentPadding: PaddingValues = ButtonDefaults.TextButtonContentPadding,
    interactionSource: MutableInteractionSource? = null,
    content: @Composable RowScope.() -> Unit,
) {
    val source = interactionSource ?: remember { MutableInteractionSource() }

    Box(
        modifier = modifier
            .defaultMinSize(minHeight = 40.dp)
            .clip(shape)
            .glassPressable(enabled, source, onClick),
        contentAlignment = Alignment.Center,
    ) {
        CompositionLocalProvider(LocalContentColor provides colors.contentFor(enabled)) {
            Row(
                modifier = Modifier.padding(contentPadding),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
                content = content,
            )
        }
    }
}

@Composable
fun GlassFilledTonalButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    shape: Shape = DefaultButtonShape,
    colors: ButtonColors = ButtonDefaults.filledTonalButtonColors(),
    elevation: ButtonElevation? = null,
    border: BorderStroke? = null,
    contentPadding: PaddingValues = ButtonDefaults.ContentPadding,
    interactionSource: MutableInteractionSource? = null,
    content: @Composable RowScope.() -> Unit,
) {
    val style = rememberLiquidGlassStyle()
    val source = interactionSource ?: remember { MutableInteractionSource() }
    val accent = colors.accentFor(enabled)

    Box(
        modifier = modifier
            .defaultMinSize(minHeight = 40.dp)
            .clip(shape)
            .liquidGlass(shape, style, elevation = 7.dp, accent = accent, enabled = enabled)
            .glassPressable(enabled, source, onClick),
        contentAlignment = Alignment.Center,
    ) {
        CompositionLocalProvider(LocalContentColor provides colors.contentFor(enabled)) {
            Row(
                modifier = Modifier.padding(contentPadding),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
                content = content,
            )
        }
    }
}

@Composable
fun GlassIconButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    colors: IconButtonColors = IconButtonDefaults.iconButtonColors(),
    shape: Shape = CircleShape,
    interactionSource: MutableInteractionSource? = null,
    content: @Composable () -> Unit,
) {
    val style = rememberLiquidGlassStyle()
    val source = interactionSource ?: remember { MutableInteractionSource() }

    Box(
        modifier = modifier
            .defaultMinSize(minWidth = 40.dp, minHeight = 40.dp)
            .clip(shape)
            .liquidGlass(shape, style, elevation = 3.dp, accent = null, enabled = enabled)
            .glassPressable(enabled, source, onClick),
        contentAlignment = Alignment.Center,
    ) {
        CompositionLocalProvider(LocalContentColor provides colors.contentFor(enabled)) {
            content()
        }
    }
}

@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(24.dp),
    colors: CardColors = CardDefaults.cardColors(),
    elevation: CardElevation = CardDefaults.cardElevation(),
    border: BorderStroke? = null,
    onClick: (() -> Unit)? = null,
    enabled: Boolean = true,
    content: @Composable ColumnScope.() -> Unit,
) {
    val style = rememberLiquidGlassStyle()
    val interaction = remember { MutableInteractionSource() }
    val accent = colors.accentForCard(enabled)
    val pressable = if (onClick != null) {
        Modifier.glassPressable(enabled, interaction, onClick)
    } else {
        Modifier
    }

    Box(
        modifier = modifier
            .clip(shape)
            .liquidGlass(shape, style, elevation = 8.dp, accent = accent, enabled = enabled)
            .then(pressable),
    ) {
        CompositionLocalProvider(LocalContentColor provides colors.contentFor(enabled)) {
            Column(content = content)
        }
    }
}

/**
 * 只有按钮的 containerColor 被显式改过（primary / error 之类的语义色）时才染色，
 * 默认中性玻璃保持无色，避免整屏按钮花花绿绿。
 */
@Composable
private fun ButtonColors.accentFor(enabled: Boolean): Color? {
    val neutral = ButtonDefaults.buttonColors()
    val current = if (enabled) containerColor else disabledContainerColor
    val fallback = if (enabled) neutral.containerColor else neutral.disabledContainerColor
    return if (current != fallback && current != Color.Transparent) current else null
}

@Composable
private fun CardColors.accentForCard(enabled: Boolean): Color? {
    val neutral = CardDefaults.cardColors()
    val current = if (enabled) containerColor else disabledContainerColor
    val fallback = if (enabled) neutral.containerColor else neutral.disabledContainerColor
    return if (current != fallback && current != Color.Transparent) current else null
}

private fun ButtonColors.contentFor(enabled: Boolean): Color =
    if (enabled) contentColor else disabledContentColor

private fun IconButtonColors.contentFor(enabled: Boolean): Color =
    if (enabled) contentColor else disabledContentColor

private fun CardColors.contentFor(enabled: Boolean): Color =
    if (enabled) contentColor else disabledContentColor
