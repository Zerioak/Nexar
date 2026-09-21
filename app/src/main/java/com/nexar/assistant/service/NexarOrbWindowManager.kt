package com.nexar.assistant.service

import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.provider.Settings
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import com.nexar.assistant.MainActivity
import com.nexar.assistant.utils.NexarLogger

class NexarOrbWindowManager(private val context: Context) {

    companion object {
        private const val TAG = "NexarOrb"
    }

    private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private var orbView: View? = null
    private var params: WindowManager.LayoutParams? = null

    private var initialX = 0
    private var initialY = 0
    private var initialTouchX = 0f
    private var initialTouchY = 0f
    private var isLongPress = false
    private val longPressHandler = android.os.Handler(android.os.Looper.getMainLooper())
    private val longPressRunnable = Runnable {
        isLongPress = true
        hide()
    }

    fun show() {
        if (!Settings.canDrawOverlays(context)) {
            NexarLogger.w(TAG, "Overlay permission not granted")
            return
        }

        if (orbView != null) return

        val savedX = 100
        val savedY = 300

        val overlayType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        params = WindowManager.LayoutParams(
            dpToPx(64),
            dpToPx(64),
            savedX,
            savedY,
            overlayType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
        }

        val orb = createOrbView()
        orbView = orb

        orb.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    isLongPress = false
                    initialX = params!!.x
                    initialY = params!!.y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    longPressHandler.postDelayed(longPressRunnable, 700)
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = (event.rawX - initialTouchX).toInt()
                    val dy = (event.rawY - initialTouchY).toInt()
                    if (Math.abs(dx) > 5 || Math.abs(dy) > 5) {
                        longPressHandler.removeCallbacks(longPressRunnable)
                    }
                    params!!.x = initialX + dx
                    params!!.y = initialY + dy
                    try {
                        windowManager.updateViewLayout(orb, params)
                    } catch (e: Exception) { }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    longPressHandler.removeCallbacks(longPressRunnable)
                    val dx = Math.abs(event.rawX - initialTouchX)
                    val dy = Math.abs(event.rawY - initialTouchY)
                    if (!isLongPress && dx < 10 && dy < 10) {
                        // Tap - open NEXAR
                        val intent = Intent(context, MainActivity::class.java).apply {
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
                        }
                        context.startActivity(intent)
                    }
                    true
                }
                else -> false
            }
        }

        try {
            windowManager.addView(orb, params)
        } catch (e: Exception) {
            NexarLogger.e(TAG, "Failed to add orb view", e)
            orbView = null
        }
    }

    private fun createOrbView(): View {
        // Create orb using Canvas drawing in a custom View
        return NexarOrbView(context)
    }

    fun hide() {
        orbView?.let { view ->
            try {
                windowManager.removeView(view)
            } catch (e: Exception) {
                NexarLogger.e(TAG, "Error removing orb view", e)
            }
            orbView = null
        }
    }

    fun updateState(state: OrbState) {
        (orbView as? NexarOrbView)?.setState(state)
    }

    private fun dpToPx(dp: Int): Int {
        return (dp * context.resources.displayMetrics.density).toInt()
    }
}

enum class OrbState {
    IDLE, LISTENING, THINKING, EXECUTING, SPEAKING, ERROR
}
