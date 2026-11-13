package com.xanime.app

import android.app.Activity
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.os.Message
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

/** Generic in-app browser for the comic source tabs. */
class ComicWebsiteActivity : Activity() {
    companion object {
        const val EXTRA_SITE_NAME = "comic_site_name"
        const val EXTRA_SITE_URL = "comic_site_url"
    }

    private var browser: WebView? = null
    private var popupWebView: WebView? = null
    private lateinit var progress: ProgressBar
    private lateinit var pageTitle: TextView
    private var siteName: String = "COMICS"
    private var allowedHost: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        siteName = intent.getStringExtra(EXTRA_SITE_NAME)?.take(40).orEmpty()
        val siteUrl = intent.getStringExtra(EXTRA_SITE_URL).orEmpty()
        val parsed = Uri.parse(siteUrl)
        allowedHost = parsed.host?.lowercase().orEmpty()
        if (siteName.isBlank() || parsed.scheme != "https" || allowedHost.isBlank()) {
            finish()
            return
        }

        window.statusBarColor = Color.rgb(7, 6, 15)
        window.navigationBarColor = Color.rgb(7, 6, 15)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.rgb(7, 6, 15))
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
            setBackgroundColor(Color.rgb(34, 26, 49))
            setOnClickListener { finish() }
        }, LinearLayout.LayoutParams(dp(44), dp(42)))

        pageTitle = TextView(this).apply {
            text = siteName.uppercase()
            textSize = 14f
            setTextColor(Color.WHITE)
            setPadding(dp(10), 0, dp(6), 0)
        }
        toolbar.addView(pageTitle, LinearLayout.LayoutParams(0, -2, 1f))
        toolbar.addView(Button(this).apply {
            text = "↻"
            isAllCaps = false
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.rgb(34, 26, 49))
            setOnClickListener { browser?.reload() }
        }, LinearLayout.LayoutParams(dp(44), dp(42)))
        root.addView(toolbar, LinearLayout.LayoutParams(-1, dp(44)))

        progress = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            max = 100
            progressTintList = android.content.res.ColorStateList.valueOf(Color.rgb(190, 75, 255))
            progressBackgroundTintList = android.content.res.ColorStateList.valueOf(Color.rgb(30, 38, 48))
        }
        root.addView(progress, LinearLayout.LayoutParams(-1, dp(3)))

        val webView = createBrowser()
        // Avoid showing previously cached ad scripts/banners while testing the updated filters.
        if (siteName.equals("AllPornComic", ignoreCase = true) ||
            siteName.equals("HitomiLA", ignoreCase = true)) {
            webView.clearCache(true)
        }
        browser = webView
        root.addView(webView, LinearLayout.LayoutParams(-1, 0, 1f))
        setContentView(root)
        webView.loadUrl(siteUrl)
    }

    private fun createBrowser(): WebView {
        return WebView(this).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.loadsImagesAutomatically = true
            settings.useWideViewPort = true
            settings.loadWithOverviewMode = true
            settings.javaScriptCanOpenWindowsAutomatically = false
            settings.setSupportMultipleWindows(true)

            webChromeClient = object : WebChromeClient() {
                override fun onProgressChanged(view: WebView?, newProgress: Int) {
                    this@ComicWebsiteActivity.progress.progress = newProgress
                    this@ComicWebsiteActivity.progress.visibility =
                        if (newProgress >= 100) android.view.View.GONE else android.view.View.VISIBLE
                }

                override fun onReceivedTitle(view: WebView?, title: String?) {
                    this@ComicWebsiteActivity.pageTitle.text =
                        title?.take(40)?.ifBlank { siteName } ?: siteName
                }

                // Keep user-clicked target=_blank links inside this same browser.
                override fun onCreateWindow(
                    view: WebView?,
                    isDialog: Boolean,
                    isUserGesture: Boolean,
                    resultMsg: Message?
                ): Boolean {
                    if (!isUserGesture || resultMsg == null || popupWebView != null) return false
                    val popup = WebView(this@ComicWebsiteActivity)
                    popupWebView = popup
                    popup.webViewClient = object : WebViewClient() {
                        private var routed = false
                        override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                            val url = request?.url?.toString() ?: return true
                            if (url == "about:blank") return false
                            if (!routed) {
                                routed = true
                                if (isAllowedPage(url)) browser?.loadUrl(url)
                                else Toast.makeText(this@ComicWebsiteActivity, "Blocked an external pop-up", Toast.LENGTH_SHORT).show()
                                closePopup(popup)
                            }
                            return true
                        }
                        override fun onPageStarted(view: WebView?, url: String?, favicon: android.graphics.Bitmap?) {
                            if (routed || url.isNullOrBlank() || url == "about:blank") return
                            routed = true
                            if (isAllowedPage(url)) browser?.loadUrl(url)
                            else Toast.makeText(this@ComicWebsiteActivity, "Blocked an external pop-up", Toast.LENGTH_SHORT).show()
                            closePopup(popup)
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
                    val uri = request?.url ?: return null
                    val host = uri.host?.lowercase().orEmpty()
                    return if (isAllPornComicAdRequest(uri) || isKnownAdHost(host)) {
                        WebResourceResponse("text/plain", "utf-8", 204, "No Content", emptyMap(),
                            ByteArrayInputStream(ByteArray(0)))
                    } else super.shouldInterceptRequest(view, request)
                }

                override fun onPageFinished(view: WebView?, url: String?) {
                    super.onPageFinished(view, url)
                    view?.evaluateJavascript(adCleanupScript, null)
                }

                override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                    if (request == null || !request.isForMainFrame) return false
                    return !isAllowedPage(request.url.toString())
                }

                @Suppress("DEPRECATION")
                override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean {
                    if (url.isNullOrBlank()) return true
                    return !isAllowedPage(url)
                }
            }
        }
    }

    private fun isAllowedPage(rawUrl: String): Boolean {
        val uri = runCatching { Uri.parse(rawUrl) }.getOrNull() ?: return false
        val scheme = uri.scheme?.lowercase()
        val host = uri.host?.lowercase() ?: return false
        return (scheme == "https" || scheme == "http") &&
            (host == allowedHost || host.endsWith(".$allowedHost"))
    }

    /** Exact ad assets and banner endpoints identified in AllPornComic page source. */
    private fun isAllPornComicAdRequest(uri: Uri): Boolean {
        // This host is dedicated to AllPornComic advertising; block every path on it,
        // including future script/spot IDs and tracking endpoints not yet observed.
        return uri.host?.lowercase() == "js.allporncomic.com"
    }

    private fun isKnownAdHost(host: String): Boolean {
        val blockedDomains = listOf(
            "doubleclick.net", "googlesyndication.com", "googleadservices.com",
            "googletagservices.com", "adservice.google.com", "popads.net",
            "popcash.net", "exoclick.com", "juicyads.com", "trafficjunky.com",
            "onclickads.net", "propellerads.com", "adsterra.com", "taboola.com",
            "outbrain.com", "hilltopads.net", "ad-maven.com", "monetag.com",
            // Ad/tracking hosts identified in HitomiLA's injected overlay and banner.
            "chaseherbalpasty.com", "bestcrongreenfunnel.com",
            "darnobedienceupscale.com"
        )
        return blockedDomains.any {
            val domain = it.trim().lowercase()
            host == domain || host.endsWith(".$domain")
        }
    }

    /** Hide clearly labelled ad slots/iframes while preserving comic artwork and reader controls. */
    private val adCleanupScript = """
        (function() {
          if (window.__xanimeAdCleanupInstalled) return;
          window.__xanimeAdCleanupInstalled = true;

          var styleId = 'xanime-comic-ad-cleanup';
          var css = [
            '.adsbygoogle', '.ad-container', '.ad-wrapper', '.ads-container',
            '.ads-wrapper', '.advertisement', '.advertising', '[data-ad]',
            '[data-ad-slot]', '[id^="google_ads"]',
            'iframe[src*="doubleclick"]', 'iframe[src*="googlesyndication"]',
            'iframe[src*="js.allporncomic.com"]', 'iframe[id^="__clb-"]',
            '[data-element="overlay"][data-izone="stop"]',
            '[data-izone="stop"]'
          ].join(',') + '{display:none!important;visibility:hidden!important;' +
            'max-height:0!important;min-height:0!important;height:0!important;' +
            'margin:0!important;padding:0!important;border:0!important;pointer-events:none!important;}';

          function hasComicArtwork(node) {
            if (!node || !node.querySelectorAll) return false;
            var imgs = node.querySelectorAll('img');
            for (var i = 0; i < imgs.length; i++) {
              var img = imgs[i], src = (img.currentSrc || img.src || '').toLowerCase();
              if (img.naturalWidth > 250 || /\\/(galleries|images|comics)\\//.test(src)) return true;
            }
            return false;
          }
          function hide(node) {
            if (!node || node.nodeType !== 1 || hasComicArtwork(node)) return;
            node.style.setProperty('display', 'none', 'important');
            node.style.setProperty('visibility', 'hidden', 'important');
            node.style.setProperty('max-height', '0', 'important');
            node.style.setProperty('min-height', '0', 'important');
            node.style.setProperty('height', '0', 'important');
            node.style.setProperty('margin', '0', 'important');
            node.style.setProperty('padding', '0', 'important');
            node.style.setProperty('pointer-events', 'none', 'important');
          }
          function removeAdPlaceholder(node) {
            if (!node || node.nodeType !== 1 || hasComicArtwork(node)) return;
            var target = node;
            for (var depth = 0; depth < 4 && target.parentElement; depth++) {
              if (target.matches && target.matches('[data-izone="stop"],[data-ad],[data-ad-slot],.ad-container,.ad-wrapper,.advertisement')) break;
              var parent = target.parentElement;
              if (parent === document.body || parent === document.documentElement) break;
              target = parent;
            }
            hide(target);
          }
          function clean() {
            if (!document.documentElement) return;
            if (!document.getElementById(styleId)) {
              var style = document.createElement('style');
              style.id = styleId;
              style.textContent = css;
              (document.head || document.documentElement).appendChild(style);
            }

            // Explicit HitomiLA ad zones, including the modal and its empty placeholder.
            var zones = document.querySelectorAll('[data-izone="stop"],[data-element="overlay"][data-izone="stop"]');
            for (var z = 0; z < zones.length; z++) hide(zones[z]);

            // Advertisement labels may be rendered separately from the creative.
            var labels = document.querySelectorAll('[data-element="static-title"],[data-element="title"],[aria-label*="advertisement" i]');
            for (var l = 0; l < labels.length; l++) {
              var label = (labels[l].textContent || '').trim().toLowerCase();
              if (label === 'advertisement' || label === 'sponsored') removeAdPlaceholder(labels[l]);
            }

            // Known third-party script tags, injected banner iframes and their now-empty wrappers.
            var scripts = document.querySelectorAll('script[src*="chaseherbalpasty.com"],script[src*="bestcrongreenfunnel.com"],script[src*="darnobedienceupscale.com"]');
            for (var s = 0; s < scripts.length; s++) {
              var p = scripts[s].parentElement;
              if (p && !hasComicArtwork(p)) hide(p);
              scripts[s].remove();
            }
            var frames = document.querySelectorAll('iframe[id^="__clb-"],iframe[id*="_container"],iframe[src*="doubleclick"],iframe[src*="googlesyndication"],iframe[src*="chaseherbalpasty.com"],iframe[src*="bestcrongreenfunnel.com"],iframe[src*="darnobedienceupscale.com"],iframe[src*="js.allporncomic.com"]');
            for (var f = 0; f < frames.length; f++) {
              var parent = frames[f].parentElement;
              hide(frames[f]);
              if (parent && !hasComicArtwork(parent)) hide(parent);
            }

            // Remove invisible full-screen click shields used to force ad clicks.
            var layers = document.querySelectorAll('body *');
            for (var n = 0; n < layers.length; n++) {
              var el = layers[n];
              if (el.id === styleId || hasComicArtwork(el)) continue;
              var st = window.getComputedStyle(el);
              if (st.position !== 'fixed' && st.position !== 'absolute') continue;
              var zi = parseInt(st.zIndex || '0', 10);
              var opacity = parseFloat(st.opacity || '1');
              if (zi < 999999 || opacity > 0.05) continue;
              var rect = el.getBoundingClientRect();
              if (rect.width >= window.innerWidth * 0.80 &&
                  rect.height >= window.innerHeight * 0.80) hide(el);
            }

            // Clear ad-only placeholder slots that have no real content.
            var placeholders = document.querySelectorAll('[id*="ad" i],[class*="advert" i],[class*="banner" i],[data-ad],[data-ad-slot]');
            for (var q = 0; q < placeholders.length; q++) {
              var slot = placeholders[q];
              if (slot === document.body || slot === document.documentElement || hasComicArtwork(slot)) continue;
              var text = (slot.textContent || '').trim();
              var hasFrame = !!slot.querySelector('iframe');
              if (!text && !hasFrame && slot.children.length <= 2) hide(slot);
            }
          }

          var scheduled = false;
          function scheduleClean() {
            if (scheduled) return;
            scheduled = true;
            window.requestAnimationFrame(function() {
              scheduled = false;
              clean();
            });
          }
          clean();
          new MutationObserver(scheduleClean).observe(document.documentElement, {
            childList: true, subtree: true, attributes: true,
            attributeFilter: ['src','class','id','style','data-izone']
          });
        })();
    """.trimIndent()

    private fun closePopup(popup: WebView?) {
        if (popup == null) return
        if (popupWebView === popup) popupWebView = null
        popup.stopLoading()
        popup.webChromeClient = null
        popup.webViewClient = WebViewClient()
        popup.destroy()
    }

    override fun onBackPressed() {
        val current = browser
        if (current?.canGoBack() == true) current.goBack() else finish()
    }

    override fun onDestroy() {
        closePopup(popupWebView)
        browser?.apply {
            stopLoading()
            webChromeClient = null
            webViewClient = WebViewClient()
            destroy()
        }
        browser = null
        super.onDestroy()
    }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()
}
