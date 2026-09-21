package com.nexar.assistant.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.nexar.assistant.ai.live.NexarSessionState
import com.nexar.assistant.ui.theme.NexarBlue
import com.nexar.assistant.ui.theme.NexarCyan
import com.nexar.assistant.ui.theme.NexarError
import com.nexar.assistant.ui.theme.NexarSuccess
import com.nexar.assistant.ui.theme.NexarWarning

@Composable
fun NexarOrb(
    state: NexarSessionState,
    modifier: Modifier = Modifier,
    size: Dp = 200.dp,
    onClick: () -> Unit = {}
) {
    val infiniteTransition = rememberInfiniteTransition(label = "nexar_orb")

    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.9f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = when (state) {
                    NexarSessionState.LISTENING -> 400
                    NexarSessionState.SPEAKING -> 600
                    else -> 2000
                },
                easing = FastOutSlowInEasing
            ),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )

    val glowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 0.8f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = when (state) {
                    NexarSessionState.LISTENING -> 400
                    NexarSessionState.THINKING, NexarSessionState.EXECUTING -> 300
                    else -> 2000
                },
                easing = FastOutSlowInEasing
            ),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glow"
    )

    val rotationAngle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "rotation"
    )

    val (primaryColor, secondaryColor) = when (state) {
        NexarSessionState.DISCONNECTED -> Pair(Color(0xFF334455), Color(0xFF223344))
        NexarSessionState.CONNECTING -> Pair(NexarBlue, Color(0xFF0A2080))
        NexarSessionState.CONNECTED -> Pair(NexarCyan, Color(0xFF006688))
        NexarSessionState.LISTENING -> Pair(NexarSuccess, Color(0xFF004422))
        NexarSessionState.THINKING -> Pair(NexarBlue, NexarCyan)
        NexarSessionState.SPEAKING -> Pair(NexarCyan, Color(0xFF006688))
        NexarSessionState.EXECUTING -> Pair(NexarWarning, Color(0xFF664400))
        NexarSessionState.ERROR -> Pair(NexarError, Color(0xFF440000))
    }

    Box(
        modifier = modifier
            .size(size)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.size(size)) {
            val center = Offset(this.size.width / 2f, this.size.height / 2f)
            val maxRadius = this.size.minDimension / 2f
            val coreRadius = maxRadius * 0.55f * pulseScale
            val glowRadius = maxRadius * 0.8f

            // Outer glow rings
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        primaryColor.copy(alpha = glowAlpha * 0.15f),
                        Color.Transparent
                    ),
                    center = center,
                    radius = maxRadius
                ),
                radius = maxRadius,
                center = center
            )

            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        primaryColor.copy(alpha = glowAlpha * 0.35f),
                        Color.Transparent
                    ),
                    center = center,
                    radius = glowRadius
                ),
                radius = glowRadius,
                center = center
            )

            // Core orb
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        primaryColor.copy(alpha = 1f),
                        secondaryColor.copy(alpha = 0.8f),
                        secondaryColor.copy(alpha = 0.4f)
                    ),
                    center = Offset(center.x - coreRadius * 0.2f, center.y - coreRadius * 0.2f),
                    radius = coreRadius * 1.2f
                ),
                radius = coreRadius,
                center = center
            )

            // Inner highlight
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color.White.copy(alpha = 0.4f),
                        Color.Transparent
                    ),
                    center = Offset(center.x - coreRadius * 0.3f, center.y - coreRadius * 0.3f),
                    radius = coreRadius * 0.5f
                ),
                radius = coreRadius * 0.4f,
                center = Offset(center.x - coreRadius * 0.25f, center.y - coreRadius * 0.25f)
            )

            // Thinking/executing arc
            if (state == NexarSessionState.THINKING ||
                state == NexarSessionState.EXECUTING ||
                state == NexarSessionState.CONNECTING) {
                drawSpinningArc(
                    center = center,
                    radius = coreRadius * 1.3f,
                    color = primaryColor,
                    rotationAngle = rotationAngle
                )
            }

            // Listening waveform dots
            if (state == NexarSessionState.LISTENING) {
                drawListeningIndicator(center, coreRadius, primaryColor, glowAlpha)
            }
        }
    }
}

private fun DrawScope.drawSpinningArc(
    center: Offset,
    radius: Float,
    color: Color,
    rotationAngle: Float
) {
    rotate(rotationAngle, pivot = center) {
        drawArc(
            color = color,
            startAngle = 0f,
            sweepAngle = 240f,
            useCenter = false,
            topLeft = Offset(center.x - radius, center.y - radius),
            size = androidx.compose.ui.geometry.Size(radius * 2, radius * 2),
            style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round)
        )
    }
    rotate(-rotationAngle * 0.7f, pivot = center) {
        drawArc(
            color = color.copy(alpha = 0.5f),
            startAngle = 120f,
            sweepAngle = 180f,
            useCenter = false,
            topLeft = Offset(center.x - radius * 1.1f, center.y - radius * 1.1f),
            size = androidx.compose.ui.geometry.Size(radius * 2.2f, radius * 2.2f),
            style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round)
        )
    }
}

private fun DrawScope.drawListeningIndicator(
    center: Offset,
    radius: Float,
    color: Color,
    alpha: Float
) {
    val dotCount = 5
    val angleStep = 72f
    val dotRadius = radius * 0.12f
    val dotOrbitRadius = radius * 1.35f

    for (i in 0 until dotCount) {
        val angle = Math.toRadians((angleStep * i).toDouble())
        val x = center.x + dotOrbitRadius * Math.cos(angle).toFloat()
        val y = center.y + dotOrbitRadius * Math.sin(angle).toFloat()
        val dotAlpha = if (i % 2 == 0) alpha else 1f - alpha * 0.5f
        drawCircle(
            color = color.copy(alpha = dotAlpha),
            radius = dotRadius,
            center = Offset(x, y)
        )
    }
}
