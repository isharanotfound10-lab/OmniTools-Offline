package com.xanime.app

import android.app.Activity
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import java.io.ByteArrayInputStream

/**
 * Hitomi.LA gallery pages are displayed in a normal in-app WebView.
 * Only the supplied ad provider/container is suppressed; Hitomi's own reader
 * scripts, gallery data, navigation and page images remain enabled.
 */
class HitomiWebViewActivity : Activity() {
    private var webView: WebView? = null
    private lateinit var progress: ProgressBar
    private lateinit var pageTitle: TextView
    private val mainHandler = Handler(Looper.getMainLooper())
    private val retried404Urls = mutableSetOf<String>()
    private var initialUrl = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        initialUrl = intent.getStringExtra(EXTRA_URL).orEmpty()
        if (!isAllowedHitomiUrl(initialUrl)) {
            finish()
            return
        }

        window.statusBarColor = Color.rgb(2, 6, 9)
        window.navigationBarColor = Color.rgb(2, 6, 9)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.rgb(2, 6, 9))
            setPadding(dp(10), dp(10), dp(10), dp(8))
        }
        val toolbar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        toolbar.addView(Button(this).apply {
            text = "‹"
            isAllCaps = false
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.rgb(25, 32, 45))
            setOnClickListener {
                val browser = webView
                if (browser?.canGoBack() == true) browser.goBack() else finish()
            }
        }, LinearLayout.LayoutParams(dp(44), dp(42)))
        pageTitle = TextView(this).apply {
            text = intent.getStringExtra(EXTRA_TITLE)?.take(28)?.ifBlank { "HITOMI.LA" } ?: "HITOMI.LA"
            textSize = 14f
            setTextColor(Color.WHITE)
            setPadding(dp(10), 0, dp(6), 0)
        }
        toolbar.addView(pageTitle, LinearLayout.LayoutParams(0, -2, 1f))
        toolbar.addView(Button(this).apply {
            text = "↻"
            isAllCaps = false
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.rgb(25, 32, 45))
            setOnClickListener { webView?.reload() }
        }, LinearLayout.LayoutParams(dp(44), dp(42)))
        root.addView(toolbar, LinearLayout.LayoutParams(-1, dp(44)))

        progress = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            max = 100
            progressTintList = android.content.res.ColorStateList.valueOf(Color.rgb(190, 75, 255))
            progressBackgroundTintList = android.content.res.ColorStateList.valueOf(Color.rgb(30, 38, 48))
        }
        root.addView(progress, LinearLayout.LayoutParams(-1, dp(3)))

        val browser = WebView(this)
        webView = browser
        browser.settings.javaScriptEnabled = true
        browser.settings.domStorageEnabled = true
        browser.settings.loadsImagesAutomatically = true
        browser.settings.useWideViewPort = true
        browser.settings.loadWithOverviewMode = true
        browser.settings.javaScriptCanOpenWindowsAutomatically = false
        browser.settings.setSupportMultipleWindows(false)
        browser.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                progress.progress = newProgress
                progress.visibility = if (newProgress >= 100) android.view.View.GONE else android.view.View.VISIBLE
            }
            override fun onReceivedTitle(view: WebView?, title: String?) {
                pageTitle.text = title?.take(28)?.ifBlank { "HITOMI.LA" } ?: "HITOMI.LA"
            }
        }
        browser.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                if (request == null || !request.isForMainFrame) return false
                val target = request.url.toString()
                if (!isAllowedHitomiUrl(target)) {
                    Toast.makeText(this@HitomiWebViewActivity, "Blocked an external redirect", Toast.LENGTH_SHORT).show()
                    return true
                }
                return false
            }

            @Suppress("DEPRECATION")
            override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean {
                if (url.isNullOrBlank()) return true
                if (!isAllowedHitomiUrl(url)) {
                    Toast.makeText(this@HitomiWebViewActivity, "Blocked an external redirect", Toast.LENGTH_SHORT).show()
                    return true
                }
                return false
            }

            override fun shouldInterceptRequest(view: WebView?, request: WebResourceRequest?): WebResourceResponse? {
                val host = request?.url?.host?.lowercase().orEmpty()
                val path = request?.url?.path?.lowercase().orEmpty()
                val isKnownAd = host == "chaseherbalpasty.com" ||
                    host.endsWith(".chaseherbalpasty.com") ||
                    path.contains("/lv/esnk/") && host != "hitomi.la"
                if (isKnownAd) {
                    return WebResourceResponse("text/plain", "UTF-8", ByteArrayInputStream(ByteArray(0)))
                }
                return super.shouldInterceptRequest(view, request)
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                if (url.isNullOrBlank()) return
                // Remove only the ad wrapper/provider identified in the supplied HTML.
                val cleanup = """
                    (function() {
                      var style = document.getElementById('xanime-hitomi-ad-cleanup');
                      if (!style) {
                        style = document.createElement('style');
                        style.id = 'xanime-hitomi-ad-cleanup';
                        style.textContent = '.wAfMeCtlYbeJH, iframe[src*="chaseherbalpasty.com"], script[src*="chaseherbalpasty.com"] { display:none !important; visibility:hidden !important; }';
                        (document.head || document.documentElement).appendChild(style);
                      }
                      document.querySelectorAll('.wAfMeCtlYbeJH').forEach(function(el) { el.remove(); });
                    })();
                """.trimIndent()
                view?.evaluateJavascript(cleanup, null)
            }

            override fun onReceivedHttpError(
                view: WebView?,
                request: WebResourceRequest?,
                errorResponse: android.webkit.WebResourceResponse?
            ) {
                super.onReceivedHttpError(view, request, errorResponse)
                if (request?.isForMainFrame != true || errorResponse?.statusCode != 404) return
                val failedUrl = request.url.toString()
                if (!isAllowedHitomiUrl(failedUrl) || !retried404Urls.add(failedUrl)) return
                // Hitomi occasionally serves a transient 404 on the first gallery navigation.
                // Retry this exact URL once; never enter an automatic refresh loop.
                mainHandler.postDelayed({
                    if (!isFinishing && !isDestroyed && webView === view &&
                        view?.url == failedUrl) {
                        view.reload()
                    }
                }, 650L)
            }
        }
        root.addView(browser, LinearLayout.LayoutParams(-1, 0, 1f))
        setContentView(root)
        browser.loadUrl(initialUrl)
    }

    override fun onBackPressed() {
        val browser = webView
        if (browser?.canGoBack() == true) browser.goBack() else finish()
    }

    override fun onDestroy() {
        mainHandler.removeCallbacksAndMessages(null)
        webView?.apply {
            stopLoading()
            webChromeClient = null
            webViewClient = WebViewClient()
            destroy()
        }
        webView = null
        super.onDestroy()
    }

    private fun isAllowedHitomiUrl(rawUrl: String): Boolean {
        val uri = runCatching { Uri.parse(rawUrl) }.getOrNull() ?: return false
        val scheme = uri.scheme?.lowercase()
        val host = uri.host?.lowercase() ?: return false
        val allowedHost = host == "hitomi.la" || host.endsWith(".hitomi.la")
        return (scheme == "https" || scheme == "http") && allowedHost
    }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()

    companion object {
        const val EXTRA_URL = "hitomi_url"
        const val EXTRA_TITLE = "hitomi_title"
    }
}
