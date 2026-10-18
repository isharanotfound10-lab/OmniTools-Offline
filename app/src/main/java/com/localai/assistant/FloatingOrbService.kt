package com.localai.assistant

import android.animation.ValueAnimator
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.drawable.GradientDrawable
import android.os.IBinder
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import kotlin.math.min
import java.util.concurrent.Executors

class FloatingOrbService : Service() {
    private var orbView: OrbView? = null
    private var windowManager: WindowManager? = null
    private var orbParams: WindowManager.LayoutParams? = null
    private var commandPanel: LinearLayout? = null
    private var commandParams: WindowManager.LayoutParams? = null
    private var commandInput: EditText? = null
    private var responseText: TextView? = null
    private var pulseAnimator: ValueAnimator? = null
    private var orbitAnimator: ValueAnimator? = null
    private var dockedRight = false
    private var expanded = false
    private var commandBusy = false
    private val commandWorker = Executors.newSingleThreadExecutor()
    private val mainHandler = Handler(Looper.getMainLooper())

    override fun onCreate() {
        super.onCreate()
        if (!Settings.canDrawOverlays(this)) { stopSelf(); return }
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        val size = dp(72)
        val params = WindowManager.LayoutParams(
            size, size, WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 0
            y = dp(180)
        }
        orbParams = params
        val view = OrbView().apply {
            contentDescription = "Local AI floating orb. Tap to type a command; drag vertically and release to dock at a screen edge."
            isClickable = true
            alpha = IDLE_ALPHA
        }
        orbView = view
        windowManager?.addView(view, params)
        startAnimations(view)
        installDragAndTap(view, params)
    }

    private fun startAnimations(view: OrbView) {
        pulseAnimator = ValueAnimator.ofFloat(0.97f, 1.035f).apply {
            duration = 1900L; repeatCount = ValueAnimator.INFINITE; repeatMode = ValueAnimator.REVERSE
            interpolator = AccelerateDecelerateInterpolator()
            addUpdateListener { view.pulseScale = it.animatedValue as Float; view.invalidate() }
            start()
        }
        orbitAnimator = ValueAnimator.ofFloat(0f, 360f).apply {
            duration = 14000L; repeatCount = ValueAnimator.INFINITE
            interpolator = android.view.animation.LinearInterpolator()
            addUpdateListener { view.orbitAngle = it.animatedValue as Float; view.invalidate() }
            start()
        }
    }

    private fun installDragAndTap(view: View, params: WindowManager.LayoutParams) {
        var downX = 0f
        var downY = 0f
        var startX = 0
        var startY = 0
        var moved = false
        val threshold = dp(7)
        view.setOnTouchListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downX = event.rawX; downY = event.rawY
                    startX = params.x; startY = params.y; moved = false
                    view.animate().alpha(1f).setDuration(120L).start()
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = event.rawX - downX
                    val dy = event.rawY - downY
                    if (kotlin.math.abs(dx) > threshold || kotlin.math.abs(dy) > threshold) moved = true
                    if (moved) {
                        val metrics = resources.displayMetrics
                        val maxY = (metrics.heightPixels - dp(72)).coerceAtLeast(0)
                        params.x = (startX + dx.toInt()).coerceIn(0, (metrics.widthPixels - dp(72)).coerceAtLeast(0))
                        params.y = (startY + dy.toInt()).coerceIn(0, maxY)
                        try { windowManager?.updateViewLayout(view, params) } catch (_: Exception) {}
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (moved) snapToNearestEdge(params, view) else toggleCommandPanel()
                    view.performClick()
                    true
                }
                MotionEvent.ACTION_CANCEL -> true
                else -> false
            }
        }
    }

    private fun snapToNearestEdge(params: WindowManager.LayoutParams, view: View) {
        val screenWidth = resources.displayMetrics.widthPixels
        val maxX = (screenWidth - dp(72)).coerceAtLeast(0)
        dockedRight = params.x + dp(36) >= screenWidth / 2
        params.x = if (dockedRight) maxX else 0
        try { windowManager?.updateViewLayout(view, params) } catch (_: Exception) {}
        if (!expanded) view.animate().alpha(IDLE_ALPHA).setDuration(180L).start()
        if (expanded) positionCommandPanel()
    }

    private fun toggleCommandPanel() {
        if (expanded) { closeCommandPanel(); return }
        expanded = true
        orbView?.animate()?.alpha(1f)?.setDuration(120L)?.start()
        showCommandPanel()
    }

    private fun showCommandPanel() {
        if (commandPanel != null) return
        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(10), dp(14), dp(10))
            background = GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                intArrayOf(Color.argb(247, 15, 5, 38), Color.argb(245, 83, 12, 123))
            ).apply {
                cornerRadius = dp(24).toFloat()
                setStroke(dp(1), Color.argb(190, 236, 56, 188))
            }
            elevation = dp(12).toFloat()
        }
        val header = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        val heading = TextView(this).apply {
            text = "✦  QUICK ACTIONS"
            textSize = 12f; setTextColor(Color.WHITE); letterSpacing = 0.08f
        }
        val close = TextView(this).apply {
            text = "×"; textSize = 26f; setTextColor(Color.WHITE); gravity = Gravity.CENTER
            setPadding(dp(12), 0, dp(2), 0)
            setOnClickListener { closeCommandPanel() }
        }
        header.addView(heading, LinearLayout.LayoutParams(0, dp(34), 1f))
        header.addView(close, LinearLayout.LayoutParams(dp(36), dp(34)))
        panel.addView(header)

        val composer = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            background = GradientDrawable().apply {
                setColor(Color.argb(235, 38, 22, 61)); cornerRadius = dp(18).toFloat()
                setStroke(dp(1), Color.argb(120, 255, 255, 255))
            }
            setPadding(dp(8), dp(2), dp(5), dp(2))
        }
        val edit = EditText(this).apply {
            hint = "What should I do?"
            setHintTextColor(Color.rgb(190, 165, 211)); setTextColor(Color.WHITE)
            textSize = 15f; maxLines = 3; minHeight = dp(46); setPadding(dp(8), dp(4), dp(6), dp(4))
            background = null; imeOptions = EditorInfo.IME_ACTION_SEND
            setOnEditorActionListener { _, actionId, _ ->
                if (actionId == EditorInfo.IME_ACTION_SEND) { submitCommand(); true } else false
            }
        }
        val send = TextView(this).apply {
            text = "GO"; textSize = 13f; gravity = Gravity.CENTER; setTextColor(Color.WHITE)
            background = GradientDrawable(GradientDrawable.Orientation.TL_BR, intArrayOf(0xFF7303C0.toInt(), 0xFFEC38BC.toInt())).apply { cornerRadius = dp(15).toFloat() }
            setOnClickListener { submitCommand() }
        }
        composer.addView(edit, LinearLayout.LayoutParams(0, -2, 1f))
        composer.addView(send, LinearLayout.LayoutParams(dp(44), dp(44)))
        panel.addView(composer, LinearLayout.LayoutParams(-1, -2))
        val result = TextView(this).apply {
            text = "Ready • tasks run on this phone."
            textSize = 12f; setTextColor(Color.rgb(239, 220, 255))
            setPadding(dp(4), dp(8), dp(4), dp(2)); visibility = View.GONE
        }
        panel.addView(result)
        commandPanel = panel; commandInput = edit; responseText = result

        val width = (resources.displayMetrics.widthPixels - dp(20)).coerceAtLeast(dp(240))
        val params = WindowManager.LayoutParams(
            width, WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE or WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE
        }
        commandParams = params
        positionCommandPanel()
        try {
            windowManager?.addView(panel, params)
            panel.alpha = 0f
            panel.translationX = if (dockedRight) dp(18).toFloat() else -dp(18).toFloat()
            panel.animate().alpha(1f).translationX(0f).setDuration(220L).start()
            edit.requestFocus()
            edit.post {
                (getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager)
                    .showSoftInput(edit, InputMethodManager.SHOW_IMPLICIT)
            }
        } catch (_: Exception) {
            commandPanel = null; commandInput = null; responseText = null; expanded = false
        }
    }

    private fun positionCommandPanel() {
        val params = commandParams ?: return
        val panelWidth = (resources.displayMetrics.widthPixels - dp(20)).coerceAtLeast(dp(240))
        params.width = panelWidth
        params.x = if (dockedRight) (resources.displayMetrics.widthPixels - panelWidth - dp(8)).coerceAtLeast(0) else dp(8)
        params.y = ((orbParams?.y ?: dp(180)) - dp(12)).coerceAtLeast(dp(8))
        try { commandPanel?.let { windowManager?.updateViewLayout(it, params) } } catch (_: Exception) {}
    }

    private fun submitCommand() {
        val command = commandInput?.text?.toString()?.trim().orEmpty()
        if (command.isEmpty() || commandBusy) return
        commandInput?.setText("")
        (getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager)
            .hideSoftInputFromWindow(commandInput?.windowToken, 0)
        commandBusy = true
        responseText?.text = "Working on your task…"
        responseText?.visibility = View.VISIBLE

        commandWorker.execute {
            val result = try {
                AgentActions.execute(this@FloatingOrbService, command) ?: when {
                    command.contains("mp4", true) || command.contains("convert", true) || command.contains("transcode", true) ->
                        "File conversion isn't available yet. This version can launch supported apps and settings; a real conversion engine still needs to be added."
                    else -> LocalAgentRuntime.executeWithModel(command) ?: OfflineBrain.respond(command)
                }
            } catch (e: Exception) {
                "Task failed: ${e.message ?: "agent error"}. You can retry or use a supported action."
            }
            mainHandler.post {
                responseText?.text = result
                responseText?.visibility = View.VISIBLE
                commandBusy = false
            }
        }
    }

    private fun closeCommandPanel() {
        commandPanel?.let { panel ->
            try { windowManager?.removeView(panel) } catch (_: Exception) {}
        }
        commandPanel = null; commandParams = null; commandInput = null; responseText = null
        expanded = false
        orbView?.animate()?.alpha(IDLE_ALPHA)?.setDuration(220L)?.start()
    }

    override fun onDestroy() {
        pulseAnimator?.cancel(); orbitAnimator?.cancel()
        commandPanel?.let { try { windowManager?.removeView(it) } catch (_: Exception) {} }
        orbView?.let { try { windowManager?.removeView(it) } catch (_: Exception) {} }
        commandPanel = null; orbView = null; windowManager = null
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private inner class OrbView : View(this@FloatingOrbService) {
        private val density = resources.displayMetrics.density
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.DITHER_FLAG)
        var pulseScale = 1f
        var orbitAngle = 0f
        init { setLayerType(View.LAYER_TYPE_HARDWARE, null) }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            val cx = width / 2f; val cy = height / 2f
            val radius = min(width, height) * 0.355f * pulseScale
            canvas.save(); canvas.translate(cx, cy)
            paint.style = Paint.Style.FILL
            paint.shader = RadialGradient(0f, 0f, radius * 1.65f,
                intArrayOf(0x887303C0.toInt(), 0x447303C0, 0x00EC38BC),
                floatArrayOf(0f, 0.58f, 1f), Shader.TileMode.CLAMP)
            canvas.drawCircle(0f, 0f, radius * 1.65f, paint)
            paint.shader = null; paint.style = Paint.Style.STROKE
            paint.strokeWidth = 1.35f * density; paint.color = 0xBFFFF0FF.toInt()
            canvas.save(); canvas.rotate(orbitAngle)
            canvas.drawOval(RectF(-radius * 1.22f, -radius * 0.58f, radius * 1.22f, radius * 0.58f), paint)
            canvas.restore()
            paint.strokeWidth = 0.8f * density; paint.color = 0x88EC38BC.toInt()
            canvas.save(); canvas.rotate(-orbitAngle * 0.62f + 42f)
            canvas.drawOval(RectF(-radius * 1.08f, -radius * 0.43f, radius * 1.08f, radius * 0.43f), paint)
            canvas.restore()
            paint.style = Paint.Style.FILL
            paint.shader = RadialGradient(-radius * 0.34f, -radius * 0.42f, radius * 1.55f,
                intArrayOf(Color.rgb(255, 226, 255), Color.rgb(236, 56, 188), Color.rgb(115, 3, 192), Color.rgb(20, 3, 47)),
                floatArrayOf(0f, 0.26f, 0.69f, 1f), Shader.TileMode.CLAMP)
            canvas.drawCircle(0f, 0f, radius, paint); paint.shader = null
            paint.color = 0xBFFFFFFF.toInt()
            canvas.drawOval(RectF(-radius * 0.48f, -radius * 0.62f, radius * 0.02f, -radius * 0.39f), paint)
            paint.color = 0x66FFFFFF
            canvas.drawCircle(radius * 0.47f, radius * 0.34f, radius * 0.075f, paint)
            val star = android.graphics.Path().apply {
                moveTo(0f, -radius * 0.39f)
                cubicTo(radius * 0.035f, -radius * 0.11f, radius * 0.11f, -radius * 0.035f, radius * 0.39f, 0f)
                cubicTo(radius * 0.11f, radius * 0.035f, radius * 0.035f, radius * 0.11f, 0f, radius * 0.39f)
                cubicTo(-radius * 0.035f, radius * 0.11f, -radius * 0.11f, radius * 0.035f, -radius * 0.39f, 0f)
                cubicTo(-radius * 0.11f, -radius * 0.035f, -radius * 0.035f, -radius * 0.11f, 0f, -radius * 0.39f)
                close()
            }
            paint.color = Color.WHITE; canvas.drawPath(star, paint)
            canvas.restore()
        }
        override fun performClick(): Boolean { super.performClick(); return true }
    }

    companion object { private const val IDLE_ALPHA = 0.20f }
}
