package com.xanime.app

import android.app.Activity
import android.graphics.Color
import android.os.Bundle
import android.os.Message
import android.net.Uri
import android.view.Gravity
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import java.io.ByteArrayInputStream
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast

/**
 * HentaiRead is hosted in a normal in-app WebView.
 * The website handles its own scripts, reader navigation, images and verification pages;
 * this activity does not scrape or download comic pages.
 */
class HentaiReadComicsActivity : Activity() {
    private var webView: WebView? = null
    private var popupWebView: WebView? = null
    private lateinit var progress: ProgressBar
    private lateinit var pageTitle: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
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
        val back = Button(this).apply {
            text = "‹"
            isAllCaps = false
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.rgb(25, 32, 45))
            setOnClickListener { finish() }
        }
        toolbar.addView(back, LinearLayout.LayoutParams(dp(44), dp(42)))
        pageTitle = TextView(this).apply {
            text = "HENTAIREAD"
            textSize = 14f
            setTextColor(Color.WHITE)
            setPadding(dp(10), 0, dp(6), 0)
        }
        toolbar.addView(pageTitle, LinearLayout.LayoutParams(0, -2, 1f))
        val refresh = Button(this).apply {
            text = "↻"
            isAllCaps = false
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.rgb(25, 32, 45))
            setOnClickListener { webView?.reload() }
        }
        toolbar.addView(refresh, LinearLayout.LayoutParams(dp(44), dp(42)))
        root.addView(toolbar, LinearLayout.LayoutParams(-1, dp(44)))

        progress = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            max = 100
            progressTintList = android.content.res.ColorStateList.valueOf(Color.rgb(190, 75, 255))
            progressBackgroundTintList = android.content.res.ColorStateList.valueOf(Color.rgb(30, 38, 48))
        }
        root.addView(progress, LinearLayout.LayoutParams(-1, dp(3)))

        val browser = createBrowser()
        webView = browser
        root.addView(browser, LinearLayout.LayoutParams(-1, 0, 1f))
        setContentView(root)
        browser.loadUrl("https://hentairead.com/")
    }

    private fun createBrowser(): WebView {
        return WebView(this).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.loadsImagesAutomatically = true
            settings.useWideViewPort = true
            settings.loadWithOverviewMode = true
            // HentaiRead's user-initiated reader links may use target=_blank.
            settings.javaScriptCanOpenWindowsAutomatically = false
            settings.setSupportMultipleWindows(true)

            webChromeClient = object : WebChromeClient() {
                override fun onProgressChanged(view: WebView?, newProgress: Int) {
                    this@HentaiReadComicsActivity.progress.progress = newProgress
                    this@HentaiReadComicsActivity.progress.visibility = if (newProgress >= 100) android.view.View.GONE else android.view.View.VISIBLE
                }

                override fun onReceivedTitle(view: WebView?, title: String?) {
                    this@HentaiReadComicsActivity.pageTitle.text = title?.take(28)?.ifBlank { "HENTAIREAD" } ?: "HENTAIREAD"
                }

                // Accept only user-initiated new windows and route their first real
                // HentaiRead URL into the existing browser instead of opening a tab.
                override fun onCreateWindow(
                    view: WebView?,
                    isDialog: Boolean,
                    isUserGesture: Boolean,
                    resultMsg: Message?
                ): Boolean {
                    if (!isUserGesture || resultMsg == null || popupWebView != null) return false

                    val popup = WebView(this@HentaiReadComicsActivity)
                    popupWebView = popup
                    popup.webViewClient = object : WebViewClient() {
                        private var routed = false

                        override fun onPageStarted(view: WebView?, url: String?, favicon: android.graphics.Bitmap?) {
                            if (routed || url.isNullOrBlank() || url == "about:blank") return
                            routed = true
                            if (!allowMainFrameUrl(url)) {
                                webView?.loadUrl(url)
                            }
                            closePopup(popup)
                        }

                        override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                            val url = request?.url?.toString() ?: return true
                            if (url == "about:blank") return false
                            if (!routed) {
                                routed = true
                                if (!allowMainFrameUrl(url)) webView?.loadUrl(url)
                                closePopup(popup)
                            }
                            return true
                        }
                    }

                    val transport = resultMsg.obj as? WebView.WebViewTransport ?: run {
                        closePopup(popup)
                        return false
                    }
                    transport.webView = popup
                    resultMsg.sendToTarget()
                    return true
                }

                override fun onCloseWindow(window: WebView?) {
                    if (window === popupWebView) closePopup(window)
                }
            }

            webViewClient = object : WebViewClient() {
                override fun shouldInterceptRequest(view: WebView?, request: WebResourceRequest?): WebResourceResponse? {
                    val host = request?.url?.host?.lowercase() ?: return null
                    return if (isKnownAdHost(host)) {
                        WebResourceResponse(
                            "text/plain",
                            "utf-8",
                            204,
                            "No Content",
                            emptyMap(),
                            ByteArrayInputStream(ByteArray(0))
                        )
                    } else {
                        super.shouldInterceptRequest(view, request)
                    }
                }

                override fun onPageFinished(view: WebView?, url: String?) {
                    super.onPageFinished(view, url)
                    view?.evaluateJavascript(adCleanupScript, null)
                }

                override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                    if (request == null || !request.isForMainFrame) return false
                    return allowMainFrameUrl(request.url.toString())
                }

                @Suppress("DEPRECATION")
                override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean {
                    if (url.isNullOrBlank()) return true
                    return allowMainFrameUrl(url)
                }
            }
        }
    }

    private fun closePopup(popup: WebView?) {
        if (popup == null) return
        if (popupWebView === popup) popupWebView = null
        popup.stopLoading()
        popup.webChromeClient = null
        popup.webViewClient = WebViewClient()
        popup.destroy()
    }

    override fun onBackPressed() {
        val browser = webView
        if (browser?.canGoBack() == true) browser.goBack() else finish()
    }

    override fun onDestroy() {
        closePopup(popupWebView)
        webView?.apply {
            stopLoading()
            webChromeClient = null
            webViewClient = WebViewClient()
            destroy()
        }
        webView = null
        super.onDestroy()
    }

    /** Block only dedicated advertising networks; HentaiRead and henread.xyz content stays untouched. */
    private fun isKnownAdHost(host: String): Boolean {
        val blockedDomains = listOf(
            "doubleclick.net",
            "googlesyndication.com",
            "googleadservices.com",
            "googletagservices.com",
            "adservice.google.com",
            "popads.net",
            "popcash.net",
            "exoclick.com",
            "juicyads.com",
            "trafficjunky.com",
            "onclickads.net",
            "propellerads.com",
            "adsterra.com",
            "taboola.com",
            "outbrain.com"
        )
        return blockedDomains.any { host == it || host.endsWith(".$it") }
    }

    /** Cosmetic cleanup for common ad containers; does not remove comic images or reader controls. */
    private val adCleanupScript = """
        (function() {
          // Only apply reader-specific banner cleanup on actual comic page URLs.
          if (!/\/english\/p\//i.test(location.pathname)) return;

          var styleId = 'xanime-reader-ad-cleanup';
          var css = [
            '.adsbygoogle', '.ad-container', '.ad-wrapper', '.ads-container',
            '.ads-wrapper', '.advert', '.advertisement', '.advertising',
            '[data-ad]', '[id^="google_ads"]', '[id*="advert"]',
            '[class*="advert"]', 'iframe[src*="doubleclick"]',
            'iframe[src*="googlesyndication"]'
          ].join(',') + '{display:none!important;visibility:hidden!important;' +
            'max-height:0!important;min-height:0!important;height:0!important;' +
            'margin:0!important;padding:0!important;border:0!important;}';

          function hasComicImage(node) {
            if (!node || !node.querySelectorAll) return false;
            var images = node.querySelectorAll('img');
            for (var i = 0; i < images.length; i++) {
              var src = images[i].currentSrc || images[i].src || '';
              if (/henread\.xyz/i.test(src)) return true;
            }
            return false;
          }

          function hideAdNode(node) {
            if (!node || node.nodeType !== 1 || hasComicImage(node)) return;
            node.style.setProperty('display', 'none', 'important');
            node.style.setProperty('height', '0', 'important');
            node.style.setProperty('min-height', '0', 'important');
            node.style.setProperty('max-height', '0', 'important');
            node.style.setProperty('margin', '0', 'important');
            node.style.setProperty('padding', '0', 'important');
          }

          function clean() {
            if (!document.documentElement) return;
            if (!document.getElementById(styleId)) {
              var style = document.createElement('style');
              style.id = styleId;
              style.textContent = css;
              (document.head || document.documentElement).appendChild(style);
            }

            // Catch ad iframes even when the site uses generated class names.
            var frames = document.querySelectorAll('iframe[src], iframe[id], iframe[name]');
            for (var i = 0; i < frames.length; i++) {
              var frame = frames[i];
              var details = [
                frame.src, frame.id, frame.name,
                frame.getAttribute('title'), frame.getAttribute('aria-label')
              ].join(' ').toLowerCase();
              if (!/(adserv|doubleclick|googlesyndication|googleadservices|popads|popcash|exoclick|juicyads|trafficjunky|onclickads|propellerads|adsterra|taboola|outbrain|(^|[\W_])ads?([\W_]|$))/i.test(details)) continue;

              var target = frame;
              hideAdNode(target);
              for (var level = 0; level < 2 && target.parentElement; level++) {
                var parent = target.parentElement;
                if (hasComicImage(parent)) break;
                hideAdNode(parent);
                target = parent;
              }
            }

            // Hide clearly named ad blocks, but never an element containing a comic page image.
            var candidates = document.querySelectorAll('[id], [class], [data-ad], [aria-label]');
            for (var j = 0; j < candidates.length; j++) {
              var el = candidates[j];
              if (el.id === styleId || hasComicImage(el)) continue;
              var label = [
                el.id, typeof el.className === 'string' ? el.className : '',
                el.getAttribute('data-ad'), el.getAttribute('aria-label')
              ].join(' ').toLowerCase();
              if (/(^|[\W_])(ad|ads|advert|advertisement|banner-ad)([\W_]|$)/i.test(label)) hideAdNode(el);
            }
          }

          clean();
          new MutationObserver(clean).observe(document.documentElement, {
            childList: true, subtree: true, attributes: true,
            attributeFilter: ['src', 'class', 'id']
          });
        })();
    """.trimIndent()

    /** Keep top-level browsing on HentaiRead; reader images and other subresources remain untouched. */
    private fun allowMainFrameUrl(rawUrl: String): Boolean {
        val uri = runCatching { Uri.parse(rawUrl) }.getOrNull() ?: return true
        val scheme = uri.scheme?.lowercase() ?: return true
        val host = uri.host?.lowercase() ?: return true
        val allowedHost = host == "hentairead.com" || host.endsWith(".hentairead.com")
        val allowed = (scheme == "https" || scheme == "http") && allowedHost
        if (!allowed) {
            Toast.makeText(this, "Blocked an unexpected pop-up or external redirect", Toast.LENGTH_SHORT).show()
        }
        return !allowed
    }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()
}
