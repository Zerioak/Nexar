package com.nexar.assistant.service

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import android.view.View
import android.view.animation.LinearInterpolator

class NexarOrbView(context: Context) : View(context) {

    private var currentState = OrbState.IDLE
    private var pulseAlpha = 1.0f
    private var pulseAnimator: ValueAnimator? = null
    private var rotationAngle = 0f
    private var rotationAnimator: ValueAnimator? = null

    private val corePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 3f
    }

    init {
        startIdleAnimation()
    }

    private fun startIdleAnimation() {
        pulseAnimator?.cancel()
        pulseAnimator = ValueAnimator.ofFloat(0.6f, 1.0f).apply {
            duration = 2000
            repeatCount = ValueAnimator.INFINITE
            repeatMode = ValueAnimator.REVERSE
            interpolator = LinearInterpolator()
            addUpdateListener {
                pulseAlpha = it.animatedValue as Float
                invalidate()
            }
            start()
        }
    }

    private fun startListeningAnimation() {
        pulseAnimator?.cancel()
        pulseAnimator = ValueAnimator.ofFloat(0.4f, 1.0f).apply {
            duration = 500
            repeatCount = ValueAnimator.INFINITE
            repeatMode = ValueAnimator.REVERSE
            interpolator = LinearInterpolator()
            addUpdateListener {
                pulseAlpha = it.animatedValue as Float
                invalidate()
            }
            start()
        }
    }

    private fun startThinkingAnimation() {
        pulseAnimator?.cancel()
        rotationAnimator?.cancel()
        rotationAnimator = ValueAnimator.ofFloat(0f, 360f).apply {
            duration = 1000
            repeatCount = ValueAnimator.INFINITE
            interpolator = LinearInterpolator()
            addUpdateListener {
                rotationAngle = it.animatedValue as Float
                invalidate()
            }
            start()
        }
    }

    private fun stopRotationAnimation() {
        rotationAnimator?.cancel()
        rotationAnimator = null
        rotationAngle = 0f
    }

    fun setState(state: OrbState) {
        if (currentState == state) return
        currentState = state
        when (state) {
            OrbState.IDLE -> {
                stopRotationAnimation()
                startIdleAnimation()
            }
            OrbState.LISTENING -> {
                stopRotationAnimation()
                startListeningAnimation()
            }
            OrbState.THINKING, OrbState.EXECUTING -> {
                pulseAnimator?.cancel()
                startThinkingAnimation()
            }
            OrbState.SPEAKING -> {
                stopRotationAnimation()
                startListeningAnimation()
            }
            OrbState.ERROR -> {
                stopRotationAnimation()
                pulseAnimator?.cancel()
                pulseAlpha = 0.8f
                invalidate()
            }
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val cx = width / 2f
        val cy = height / 2f
        val radius = minOf(cx, cy) * 0.85f

        val (coreColor, glowColor) = when (currentState) {
            OrbState.IDLE -> Pair(0xFF00D4FF.toInt(), 0x4400D4FF.toInt())
            OrbState.LISTENING -> Pair(0xFF00FF88.toInt(), 0x4400FF88.toInt())
            OrbState.THINKING -> Pair(0xFF1A4AFF.toInt(), 0x441A4AFF.toInt())
            OrbState.EXECUTING -> Pair(0xFFFFAA00.toInt(), 0x44FFAA00.toInt())
            OrbState.SPEAKING -> Pair(0xFF00D4FF.toInt(), 0x8800D4FF.toInt())
            OrbState.ERROR -> Pair(0xFFFF4444.toInt(), 0x44FF4444.toInt())
        }

        // Draw outer glow
        glowPaint.apply {
            shader = RadialGradient(
                cx, cy, radius * 1.4f,
                intArrayOf(glowColor, Color.TRANSPARENT),
                floatArrayOf(0.0f, 1.0f),
                Shader.TileMode.CLAMP
            )
            alpha = (pulseAlpha * 200).toInt()
        }
        canvas.drawCircle(cx, cy, radius * 1.4f, glowPaint)

        // Draw core
        corePaint.apply {
            shader = RadialGradient(
                cx - radius * 0.2f, cy - radius * 0.2f, radius,
                intArrayOf(
                    Color.argb(255, Color.red(coreColor), Color.green(coreColor), Color.blue(coreColor)),
                    Color.argb(180, Color.red(coreColor) / 2, Color.green(coreColor) / 2, Color.blue(coreColor) / 2)
                ),
                floatArrayOf(0.0f, 1.0f),
                Shader.TileMode.CLAMP
            )
            alpha = (pulseAlpha * 255).toInt()
        }
        canvas.drawCircle(cx, cy, radius, corePaint)

        // Draw inner highlight
        val highlightPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb((pulseAlpha * 120).toInt(), 255, 255, 255)
        }
        canvas.drawCircle(cx - radius * 0.25f, cy - radius * 0.25f, radius * 0.35f, highlightPaint)

        // Draw rotation ring for thinking
        if (currentState == OrbState.THINKING || currentState == OrbState.EXECUTING) {
            ringPaint.color = coreColor
            ringPaint.alpha = 200
            canvas.save()
            canvas.rotate(rotationAngle, cx, cy)
            canvas.drawArc(
                cx - radius * 1.15f, cy - radius * 1.15f,
                cx + radius * 1.15f, cy + radius * 1.15f,
                0f, 270f, false, ringPaint
            )
            canvas.restore()
        }

        // Draw NEXAR text indicator
        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 10f * resources.displayMetrics.density
            textAlign = Paint.Align.CENTER
            alpha = (pulseAlpha * 200).toInt()
        }
        canvas.drawText("N", cx, cy + textPaint.textSize * 0.4f, textPaint)
    }

    override fun onDetachedFromWindow() {
        pulseAnimator?.cancel()
        rotationAnimator?.cancel()
        super.onDetachedFromWindow()
    }
}
