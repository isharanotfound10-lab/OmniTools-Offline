package com.xanime.app

import android.animation.ValueAnimator
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import android.graphics.PixelFormat
import android.graphics.drawable.Drawable
import android.view.animation.LinearInterpolator
import kotlin.math.sin
import kotlin.random.Random

/**
 * Lightweight animated aurora/galaxy backdrop. It draws directly to the
 * window canvas, reuses its shaders, and keeps particle count deliberately low.
 */
class AmbientBackgroundDrawable(
    private val baseColor: Int,
    accentColor: Int
) : Drawable() {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val matrix = Matrix()
    private var phase = 0f
    private var scrollOffset = 0f
    private var alphaValue = 255
    private val random = Random(7341)
    private val particleX = FloatArray(34) { random.nextFloat() }
    private val particleY = FloatArray(34) { random.nextFloat() }
    private val particleRadius = FloatArray(34) { 0.7f + random.nextFloat() * 1.5f }
    private val particleAlpha = IntArray(34) { 35 + random.nextInt(75) }
    private val violet = accentColor
    private val cyan = Color.rgb(8, 125, 145)
    private val blue = Color.rgb(6, 52, 67)
    private val violetShader = RadialGradient(0f, 0f, 1f,
        intArrayOf(Color.argb(86, Color.red(violet), Color.green(violet), Color.blue(violet)), Color.TRANSPARENT),
        null, Shader.TileMode.CLAMP)
    private val cyanShader = RadialGradient(0f, 0f, 1f,
        intArrayOf(Color.argb(70, Color.red(cyan), Color.green(cyan), Color.blue(cyan)), Color.TRANSPARENT),
        null, Shader.TileMode.CLAMP)
    private val blueShader = RadialGradient(0f, 0f, 1f,
        intArrayOf(Color.argb(62, Color.red(blue), Color.green(blue), Color.blue(blue)), Color.TRANSPARENT),
        null, Shader.TileMode.CLAMP)
    private val animator = ValueAnimator.ofFloat(0f, 1f).apply {
        duration = 28000L
        repeatCount = ValueAnimator.INFINITE
        interpolator = LinearInterpolator()
        addUpdateListener {
            phase = it.animatedValue as Float
            invalidateSelf()
        }
    }

    fun start() {
        if (!animator.isStarted) animator.start()
    }

    fun stop() {
        if (animator.isStarted) animator.cancel()
    }

    fun setScrollOffset(offset: Int) {
        scrollOffset = offset * 0.045f
        invalidateSelf()
    }

    private fun drawGlow(canvas: Canvas, shader: Shader, x: Float, y: Float, radius: Float, scaleX: Float = 1f) {
        matrix.reset()
        matrix.setScale(radius * scaleX, radius)
        matrix.postTranslate(x, y)
        shader.setLocalMatrix(matrix)
        paint.shader = shader
        paint.alpha = alphaValue
        canvas.drawCircle(x, y, radius * 1.42f, paint)
        paint.shader = null
    }

    override fun draw(canvas: Canvas) {
        val w = bounds.width().toFloat()
        val h = bounds.height().toFloat()
        if (w <= 0f || h <= 0f) return

        paint.alpha = alphaValue
        paint.color = baseColor
        paint.shader = null
        canvas.drawColor(baseColor)

        val t = phase * (Math.PI * 2.0)
        drawGlow(canvas, violetShader, w * (0.18f + 0.13f * sin(t).toFloat()),
            h * (0.23f + 0.09f * sin(t * 0.7).toFloat()), w * 0.72f, 1.12f)
        drawGlow(canvas, cyanShader, w * (0.86f + 0.12f * sin(t + 2.1).toFloat()),
            h * (0.62f + 0.13f * sin(t * 0.8 + 1f).toFloat()), w * 0.62f, 0.92f)
        drawGlow(canvas, blueShader, w * (0.48f + 0.17f * sin(t + 4f).toFloat()),
            h * (0.98f + 0.05f * sin(t).toFloat()), w * 0.58f, 1.25f)

        paint.shader = null
        for (i in particleX.indices) {
            val drift = (phase * (0.035f + (i % 5) * 0.008f) + particleY[i] + scrollOffset / h) % 1f
            val x = particleX[i] * w
            val y = drift * h
            paint.color = Color.argb(particleAlpha[i], 220, 218, 255)
            canvas.drawCircle(x, y, particleRadius[i] * resourcesDensity(w), paint)
        }

        // A faint diagonal shooting-star accent, appearing only briefly each cycle.
        val starPhase = (phase * 2.7f) % 1f
        if (starPhase > 0.76f) {
            val progress = (starPhase - 0.76f) / 0.24f
            val x = w * (0.18f + progress * 0.52f)
            val y = h * (0.12f + progress * 0.20f)
            paint.color = Color.argb((90 * (1f - progress)).toInt(), 205, 220, 255)
            paint.strokeWidth = 1.2f
            canvas.drawLine(x, y, x - w * 0.045f, y - h * 0.025f, paint)
        }
    }

    private fun resourcesDensity(width: Float): Float = (width / 400f).coerceIn(0.75f, 1.5f)

    override fun setAlpha(alpha: Int) {
        alphaValue = alpha
        invalidateSelf()
    }

    override fun setColorFilter(colorFilter: android.graphics.ColorFilter?) {
        paint.colorFilter = colorFilter
        invalidateSelf()
    }

    @Suppress("DEPRECATION")
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
}
