package com.rifsxd.ksunext.ui.component.liquid

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * HydraSU 液态玻璃材质（Liquid Glass）。
 *
 * 官方 KernelSU 的实现依赖 miuix-blur（Kotlin 2.4.20 / Compose 1.12）里的 Backdrop + AGSL
 * 折射，本 fork 是 Kotlin 2.2 / Compose 1.8，无法引入。这里用纯 Compose 的分层绘制复刻同一
 * 观感：半透玻璃体 + 边缘折射带（bevel）+ 顶左镜面高光 + 底部内阴影 + 玻璃外沿高光描边。
 *
 * 全部基于 drawWithCache / Path / Brush 这类稳定 API，minSdk 26 可直接跑，不需要 API 33。
 */
@Immutable
data class LiquidGlassStyle(
    val tint: Color,
    val tintTopAlpha: Float,
    val tintBottomAlpha: Float,
    val rimTop: Color,
    val rimBottom: Color,
    val innerShadow: Color,
    val specular: Color,
    val refraction: Dp,
    val shadow: Color,
) {
    companion object {
        fun light(): LiquidGlassStyle = LiquidGlassStyle(
            tint = Color.White,
            tintTopAlpha = 0.74f,
            tintBottomAlpha = 0.44f,
            rimTop = Color.White.copy(alpha = 0.95f),
            rimBottom = Color.White.copy(alpha = 0.28f),
            innerShadow = Color(0xFF0B1A24).copy(alpha = 0.18f),
            specular = Color.White.copy(alpha = 0.60f),
            refraction = 7.dp,
            shadow = Color(0xFF0B1A24).copy(alpha = 0.16f),
        )

        fun dark(): LiquidGlassStyle = LiquidGlassStyle(
            tint = Color.White,
            tintTopAlpha = 0.20f,
            tintBottomAlpha = 0.06f,
            rimTop = Color.White.copy(alpha = 0.58f),
            rimBottom = Color.White.copy(alpha = 0.12f),
            innerShadow = Color.Black.copy(alpha = 0.38f),
            specular = Color.White.copy(alpha = 0.30f),
            refraction = 7.dp,
            shadow = Color.Black.copy(alpha = 0.50f),
        )
    }
}

@Composable
fun rememberLiquidGlassStyle(): LiquidGlassStyle {
    val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    return remember(isDark) { if (isDark) LiquidGlassStyle.dark() else LiquidGlassStyle.light() }
}

/**
 * 把任意 composable 变成一块液态玻璃。
 *
 * @param shape 玻璃外形，按钮建议用胶囊或 18dp 圆角
 * @param style 材质令牌，默认按深浅色自动选
 * @param elevation 外投影高度
 * @param accent 强调色（primary / error 按钮用），会把玻璃体染色但保留透明感
 * @param enabled 关闭时压暗
 */
fun Modifier.liquidGlass(
    shape: Shape,
    style: LiquidGlassStyle,
    elevation: Dp = 6.dp,
    accent: Color? = null,
    enabled: Boolean = true,
): Modifier {
    val drawModifier = drawWithCache {
        val outline = shape.createOutline(size, layoutDirection, this)
        val path = Path().apply {
            when (outline) {
                is Outline.Rectangle -> addRect(outline.rect)
                is Outline.Rounded -> addRoundRect(outline.roundRect)
                is Outline.Generic -> addPath(outline.path)
            }
        }

        val width = size.width
        val height = size.height
        val refractionPx = style.refraction.toPx()
        val hairline = 1.dp.toPx()
        val alphaScale = if (enabled) 1f else 0.45f

        // 玻璃体：顶部亮、底部沉的半透明渐变
        val bodyTop = if (accent != null) lerp(style.tint, accent, 0.55f) else style.tint
        val bodyBottom = if (accent != null) lerp(style.tint, accent, 0.75f) else style.tint
        val bodyBrush = Brush.verticalGradient(
            colors = listOf(
                bodyTop.copy(alpha = if (accent != null) 0.46f else style.tintTopAlpha),
                bodyBottom.copy(alpha = if (accent != null) 0.28f else style.tintBottomAlpha),
            ),
        )

        // 边缘折射带：上沿发亮 → 中段透明 → 下沿压暗，形成透镜弯折的错觉
        val bevelBrush = Brush.verticalGradient(
            0.00f to style.rimTop,
            0.28f to Color.Transparent,
            0.70f to Color.Transparent,
            1.00f to style.innerShadow,
        )

        // 顶左镜面高光
        val specularBrush = Brush.radialGradient(
            colors = listOf(style.specular.copy(alpha = style.specular.alpha * alphaScale), Color.Transparent),
            center = Offset(width * 0.18f, height * 0.06f),
            radius = width.coerceAtLeast(height) * 0.85f,
        )

        // 外沿细高光
        val rimBrush = Brush.verticalGradient(
            colors = listOf(style.rimTop, style.rimBottom),
        )

        onDrawBehind {
            clipPath(path) {
                drawRect(brush = bodyBrush)
                drawRect(brush = specularBrush)
                // 折射带用加粗描边画在形状内侧
                drawPath(
                    path = path,
                    brush = bevelBrush,
                    style = Stroke(width = refractionPx * 1.7f),
                    alpha = alphaScale,
                )
            }
            // 锐利外沿，压在折射带上面
            drawPath(
                path = path,
                brush = rimBrush,
                style = Stroke(width = hairline),
                alpha = alphaScale,
            )
        }
    }

    return this
        .shadow(
            elevation = if (enabled) elevation else 0.dp,
            shape = shape,
            clip = false,
            ambientColor = style.shadow,
            spotColor = style.shadow,
        )
        .then(drawModifier)
}

/**
 * 玻璃容器：矩形/圆角卡片、面板、底栏都能直接用。
 */
@Composable
fun LiquidGlassSurface(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(24.dp),
    style: LiquidGlassStyle = rememberLiquidGlassStyle(),
    elevation: Dp = 8.dp,
    accent: Color? = null,
    enabled: Boolean = true,
    content: @Composable BoxScope.() -> Unit,
) {
    Box(
        modifier = modifier
            .liquidGlass(shape, style, elevation, accent, enabled)
            .clip(shape),
        content = content,
    )
}
