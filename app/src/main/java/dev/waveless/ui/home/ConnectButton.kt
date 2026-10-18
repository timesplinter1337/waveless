package dev.waveless.ui.home

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PowerSettingsNew
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.toPath
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.graphics.shapes.Morph
import androidx.graphics.shapes.RoundedPolygon
import dev.waveless.service.VpnStatus

private enum class Look { Idle, Busy, On }

private fun VpnStatus.look() = when (this) {
    VpnStatus.Connecting, VpnStatus.Disconnecting -> Look.Busy
    is VpnStatus.Connected -> Look.On
    else -> Look.Idle
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
private fun Look.polygon(): RoundedPolygon = when (this) {
    Look.Idle -> MaterialShapes.Cookie9Sided
    Look.Busy -> MaterialShapes.SoftBurst
    Look.On -> MaterialShapes.Cookie12Sided
}

/**
 * Hero power button. Its container morphs between Material shapes
 * (cookie → burst → scalloped circle) with the expressive spatial spring,
 * squishes on press, and slowly spins while connecting or connected.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ConnectButton(
    status: VpnStatus,
    label: String,
    stateLabel: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 224.dp,
) {
    val motion = MaterialTheme.motionScheme
    val colors = MaterialTheme.colorScheme
    val look = status.look()

    // Morph from whatever was shown last to the new target.
    var from by remember { mutableStateOf(look) }
    var to by remember { mutableStateOf(look) }
    val progress = remember { Animatable(1f) }
    LaunchedEffect(look) {
        if (look == to) return@LaunchedEffect
        from = to
        to = look
        progress.snapTo(0f)
        progress.animateTo(1f, motion.slowSpatialSpec())
    }
    val morph = remember(from, to) { Morph(from.polygon(), to.polygon()) }

    val spin = rememberInfiniteTransition(label = "spin")
    val rotation by spin.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(if (look == Look.Busy) 4_000 else 24_000, easing = LinearEasing), RepeatMode.Restart),
        label = "rotation",
    )

    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.9f else 1f, motion.fastSpatialSpec(), label = "press")

    val container by animateColorAsState(
        when (look) {
            Look.Idle -> colors.surfaceContainerHighest
            Look.Busy -> colors.tertiaryContainer
            Look.On -> colors.primary
        },
        motion.defaultEffectsSpec(),
        label = "container",
    )
    val content by animateColorAsState(
        when (look) {
            Look.Idle -> colors.onSurfaceVariant
            Look.Busy -> colors.onTertiaryContainer
            Look.On -> colors.onPrimary
        },
        motion.defaultEffectsSpec(),
        label = "content",
    )

    Box(
        modifier = modifier
            .size(size)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .semantics {
                contentDescription = label
                stateDescription = stateLabel
            }
            .clip(CircleShape)
            .clickable(interactionSource = interaction, indication = null, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .matchParentSize()
                .graphicsLayer { rotationZ = if (look == Look.Idle) 0f else rotation }
                .clip(MorphShape(morph, progress.value))
                .background(container)
        )
        AnimatedContent(
            targetState = look == Look.Busy,
            transitionSpec = {
                (fadeIn(motion.defaultEffectsSpec()) + scaleIn(motion.defaultSpatialSpec(), initialScale = 0.6f))
                    .togetherWith(fadeOut(motion.fastEffectsSpec()) + scaleOut(targetScale = 0.6f))
            },
            label = "icon",
        ) { busy ->
            if (busy) {
                LoadingIndicator(Modifier.size(size * 0.45f), color = content)
            } else {
                Icon(
                    Icons.Rounded.PowerSettingsNew,
                    contentDescription = null,
                    tint = content,
                    modifier = Modifier.size(size * 0.32f),
                )
            }
        }
    }
}

/** Shape that renders a [Morph] at [progress], scaled from unit space to the layout size. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
private class MorphShape(private val morph: Morph, private val progress: Float) : Shape {
    private val path = Path()
    private val matrix = Matrix()

    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        morph.toPath(progress, path)
        matrix.reset()
        matrix.scale(size.width, size.height)
        path.transform(matrix)
        return Outline.Generic(path)
    }
}
