package com.xanime.app

import android.animation.ValueAnimator
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.Shader
import android.graphics.drawable.Drawable
import android.view.animation.LinearInterpolator

/** Small animated placeholder sweep used only while a thumbnail is loading. */
class ShimmerDrawable(
    private val baseColor: Int,
    private val accentColor: Int
) : Drawable() {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val matrix = Matrix()
    private var phase = 0f
    private var alphaValue = 255
    private var shader: LinearGradient? = null
    private val animator = ValueAnimator.ofFloat(0f, 1f).apply {
        duration = 1150L
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

    override fun onBoundsChange(bounds: android.graphics.Rect) {
        super.onBoundsChange(bounds)
        shader = LinearGradient(0f, 0f, 240f, 0f,
            intArrayOf(baseColor, Color.argb(105, Color.red(accentColor), Color.green(accentColor), Color.blue(accentColor)), baseColor),
            floatArrayOf(0f, 0.5f, 1f), Shader.TileMode.CLAMP)
    }

    override fun draw(canvas: Canvas) {
        val b = bounds
        val currentShader = shader ?: return
        matrix.setTranslate((phase * (b.width() + 240f)) - 240f, 0f)
        currentShader.setLocalMatrix(matrix)
        paint.shader = currentShader
        paint.alpha = alphaValue
        canvas.drawRoundRect(b.left.toFloat(), b.top.toFloat(), b.right.toFloat(), b.bottom.toFloat(),
            10f, 10f, paint)
        paint.shader = null
    }

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
