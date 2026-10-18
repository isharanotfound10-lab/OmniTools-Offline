package com.localai.assistant

import android.animation.ValueAnimator
import android.app.Service
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.os.IBinder
import android.provider.Settings
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.animation.AccelerateDecelerateInterpolator
import kotlin.math.min

class FloatingOrbService : Service() {
    private var orbView: OrbView? = null
    private var windowManager: WindowManager? = null
    private var layoutParams: WindowManager.LayoutParams? = null
    private var pulseAnimator: ValueAnimator? = null
    private var orbitAnimator: ValueAnimator? = null

    override fun onCreate() {
        super.onCreate()
        if (!Settings.canDrawOverlays(this)) {
            stopSelf()
            return
        }

        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        val size = dp(72)
        val params = WindowManager.LayoutParams(
            size,
            size,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = dp(16)
            y = dp(180)
        }
        layoutParams = params

        val view = OrbView().apply {
            contentDescription = "Local AI floating orb. Tap to open Local AI; drag to move."
            isClickable = true
        }
        orbView = view
        windowManager?.addView(view, params)
        startAnimations(view)
        installDragAndTap(view, params)
    }

    private fun startAnimations(view: OrbView) {
        pulseAnimator = ValueAnimator.ofFloat(0.97f, 1.035f).apply {
            duration = 1900L
            repeatCount = ValueAnimator.INFINITE
            repeatMode = ValueAnimator.REVERSE
            interpolator = AccelerateDecelerateInterpolator()
            addUpdateListener {
                view.pulseScale = it.animatedValue as Float
                view.invalidate()
            }
            start()
        }
        orbitAnimator = ValueAnimator.ofFloat(0f, 360f).apply {
            duration = 14000L
            repeatCount = ValueAnimator.INFINITE
            interpolator = android.view.animation.LinearInterpolator()
            addUpdateListener {
                view.orbitAngle = it.animatedValue as Float
                view.invalidate()
            }
            start()
        }
    }

    private fun installDragAndTap(view: View, params: WindowManager.LayoutParams) {
        var downX = 0f
        var downY = 0f
        var startX = 0
        var startY = 0
        var moved = false
        val dragThreshold = dp(7)

        view.setOnTouchListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downX = event.rawX
                    downY = event.rawY
                    startX = params.x
                    startY = params.y
                    moved = false
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = event.rawX - downX
                    val dy = event.rawY - downY
                    if (kotlin.math.abs(dx) > dragThreshold || kotlin.math.abs(dy) > dragThreshold) {
                        moved = true
                    }
                    if (moved) {
                        params.x = startX + dx.toInt()
                        params.y = startY + dy.toInt()
                        try {
                            windowManager?.updateViewLayout(view, params)
                        } catch (_: Exception) {
                        }
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (!moved) {
                        try {
                            startActivity(Intent(this, MainActivity::class.java).apply {
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                            })
                        } catch (_: Exception) {
                        }
                    }
                    view.performClick()
                    true
                }
                MotionEvent.ACTION_CANCEL -> true
                else -> false
            }
        }
    }

    override fun onDestroy() {
        pulseAnimator?.cancel()
        orbitAnimator?.cancel()
        orbView?.let { view ->
            try {
                windowManager?.removeView(view)
            } catch (_: Exception) {
            }
        }
        orbView = null
        windowManager = null
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()

    private inner class OrbView : View(this@FloatingOrbService) {
        private val density = resources.displayMetrics.density
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.DITHER_FLAG)
        var pulseScale = 1f
        var orbitAngle = 0f

        init {
            setLayerType(View.LAYER_TYPE_HARDWARE, null)
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            val cx = width / 2f
            val cy = height / 2f
            val baseRadius = min(width, height) * 0.355f
            val radius = baseRadius * pulseScale

            canvas.save()
            canvas.translate(cx, cy)

            // Soft aura; the view itself stays transparent and circular.
            paint.style = Paint.Style.FILL
            paint.shader = RadialGradient(
                0f, 0f, radius * 1.65f,
                intArrayOf(0x887303C0.toInt(), 0x447303C0, 0x00EC38BC),
                floatArrayOf(0f, 0.58f, 1f),
                Shader.TileMode.CLAMP
            )
            canvas.drawCircle(0f, 0f, radius * 1.65f, paint)

            // A thin moving glass-like orbit around the sphere.
            paint.shader = null
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 1.35f * density
            paint.color = 0xBFFFF0FF.toInt()
            canvas.save()
            canvas.rotate(orbitAngle)
            canvas.drawOval(
                RectF(-radius * 1.22f, -radius * 0.58f, radius * 1.22f, radius * 0.58f),
                paint
            )
            canvas.restore()

            paint.strokeWidth = 0.8f * density
            paint.color = 0x88EC38BC.toInt()
            canvas.save()
            canvas.rotate(-orbitAngle * 0.62f + 42f)
            canvas.drawOval(
                RectF(-radius * 1.08f, -radius * 0.43f, radius * 1.08f, radius * 0.43f),
                paint
            )
            canvas.restore()

            // The orb is a true round radial-gradient sphere, not a square tile.
            paint.style = Paint.Style.FILL
            paint.shader = RadialGradient(
                -radius * 0.34f, -radius * 0.42f, radius * 1.55f,
                intArrayOf(
                    Color.rgb(255, 226, 255),
                    Color.rgb(236, 56, 188),
                    Color.rgb(115, 3, 192),
                    Color.rgb(20, 3, 47)
                ),
                floatArrayOf(0f, 0.26f, 0.69f, 1f),
                Shader.TileMode.CLAMP
            )
            canvas.drawCircle(0f, 0f, radius, paint)
            paint.shader = null

            // Small specular highlight for a liquid-glass finish.
            paint.color = 0xBFFFFFFF.toInt()
            canvas.drawOval(
                RectF(-radius * 0.48f, -radius * 0.62f, radius * 0.02f, -radius * 0.39f),
                paint
            )
            paint.color = 0x66FFFFFF
            canvas.drawCircle(radius * 0.47f, radius * 0.34f, radius * 0.075f, paint)

            // Four-point star mark.
            val star = android.graphics.Path().apply {
                moveTo(0f, -radius * 0.39f)
                cubicTo(radius * 0.035f, -radius * 0.11f, radius * 0.11f, -radius * 0.035f, radius * 0.39f, 0f)
                cubicTo(radius * 0.11f, radius * 0.035f, radius * 0.035f, radius * 0.11f, 0f, radius * 0.39f)
                cubicTo(-radius * 0.035f, radius * 0.11f, -radius * 0.11f, radius * 0.035f, -radius * 0.39f, 0f)
                cubicTo(-radius * 0.11f, -radius * 0.035f, -radius * 0.035f, -radius * 0.11f, 0f, -radius * 0.39f)
                close()
            }
            paint.color = Color.WHITE
            canvas.drawPath(star, paint)

            canvas.restore()
        }

        override fun performClick(): Boolean {
            super.performClick()
            return true
        }
    }
}
