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
          var styleId = 'xanime-comic-ad-cleanup';
          var css = [
            '.adsbygoogle', '.ad-container', '.ad-wrapper', '.ads-container',
            '.ads-wrapper', '.advertisement', '.advertising', '[data-ad]',
            '[data-ad-slot]', '[id^="google_ads"]',
            'iframe[src*="doubleclick"]', 'iframe[src*="googlesyndication"]',
            'iframe[src*="js.allporncomic.com"]',
            'center:has(iframe[src*="js.allporncomic.com"])',
            '[data-element="overlay"][data-izone="stop"]',
            'iframe[id^="__clb-"]',
          ].join(',') + '{display:none!important;visibility:hidden!important;' +
            'max-height:0!important;min-height:0!important;height:0!important;' +
            'margin:0!important;padding:0!important;border:0!important;}';

          function containsLargeComicImage(node) {
            if (!node || !node.querySelectorAll) return false;
            var imgs = node.querySelectorAll('img');
            for (var i = 0; i < imgs.length; i++) {
              var img = imgs[i];
              var src = (img.currentSrc || img.src || '').toLowerCase();
              if (img.naturalWidth > 250 || /\/galleries\/|\/images\/|\/comics\//.test(src)) return true;
            }
            return false;
          }
          function hide(node) {
            if (!node || node.nodeType !== 1 || containsLargeComicImage(node)) return;
            node.style.setProperty('display', 'none', 'important');
            node.style.setProperty('max-height', '0', 'important');
            node.style.setProperty('min-height', '0', 'important');
            node.style.setProperty('height', '0', 'important');
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
            // HitomiLA's injected modal overlay uses stable data attributes despite randomized classes.
            var overlays = document.querySelectorAll('[data-element="overlay"][data-izone="stop"]');
            for (var o = 0; o < overlays.length; o++) hide(overlays[o]);
            // Remove randomized full-screen ad layers carrying a Skip now label.
            var skipLabels = document.querySelectorAll('button,a,[role="button"],div,span');
            for (var k = 0; k < skipLabels.length; k++) {
              var label = (skipLabels[k].innerText || skipLabels[k].textContent || '').replace(/\\s+/g, ' ').trim().toLowerCase();
              if (!/^(skip now|skip ad|close ad)$/.test(label)) continue;
              var candidate = skipLabels[k];
              for (var depth = 0; candidate && depth < 8; depth++, candidate = candidate.parentElement) {
                var rect = candidate.getBoundingClientRect();
                var pos = window.getComputedStyle(candidate).position;
                if ((pos === 'fixed' || pos === 'absolute') && rect.width >= window.innerWidth * 0.45 && rect.height >= window.innerHeight * 0.35) { hide(candidate); break; }
              }
            }
            // Remove oversized fixed third-party ad frames, preserving first-party reader frames.
            var fixedFrames = document.querySelectorAll('iframe[src]');
            for (var q = 0; q < fixedFrames.length; q++) {
              var ff = fixedFrames[q], fr = ff.getBoundingClientRect(), fp = window.getComputedStyle(ff).position, fh = '';
              try { fh = new URL(ff.src, location.href).hostname.toLowerCase(); } catch (_) {}
              var firstParty = fh === location.hostname || fh.endsWith('.' + location.hostname);
              if (!firstParty && (fp === 'fixed' || fp === 'absolute') && fr.width >= window.innerWidth * 0.45 && fr.height >= window.innerHeight * 0.35) { hide(ff); if (ff.parentElement && !containsLargeComicImage(ff.parentElement)) hide(ff.parentElement); }
            }

            // Remove the ad script wrapper and its companion banner iframe by stable ID prefix.
            var adScripts = document.querySelectorAll('script[src*="chaseherbalpasty.com"]');
            for (var s = 0; s < adScripts.length; s++) {
              var wrapper = adScripts[s].parentElement;
              if (wrapper && !containsLargeComicImage(wrapper)) hide(wrapper);
            }
            var adFrames = document.querySelectorAll('iframe[id^="__clb-"],iframe[id*="_container"]');
            for (var a = 0; a < adFrames.length; a++) {
              var frameParent = adFrames[a].parentElement;
              hide(adFrames[a]);
              if (frameParent && !containsLargeComicImage(frameParent)) hide(frameParent);
            }

            var frames = document.querySelectorAll('iframe[src],iframe[id],iframe[name]');
            for (var i = 0; i < frames.length; i++) {
              var frame = frames[i];
              var details = [frame.src, frame.id, frame.name, frame.title].join(' ').toLowerCase();
              if (details.indexOf('js.allporncomic.com') !== -1 || details.indexOf('chaseherbalpasty.com') !== -1 || details.indexOf('bestcrongreenfunnel.com') !== -1 || details.indexOf('darnobedienceupscale.com') !== -1 || /(adserv|doubleclick|googlesyndication|googleadservices|popads|popcash|exoclick|juicyads|trafficjunky|onclickads|propellerads|adsterra|taboola|outbrain|ad-maven|hilltop)/.test(details)) {
                hide(frame);
                if (frame.parentElement && !containsLargeComicImage(frame.parentElement)) hide(frame.parentElement);
              }
            }
            var nodes = document.querySelectorAll('[id],[class],[data-ad],[aria-label]');
            for (var j = 0; j < nodes.length; j++) {
              var el = nodes[j];
              if (el.id === styleId || containsLargeComicImage(el)) continue;
              var name = [el.id, typeof el.className === 'string' ? el.className : '',
                el.getAttribute('data-ad'), el.getAttribute('aria-label')].join(' ').toLowerCase();
              if (/(^|[\W_])(ad|ads|advert|advertisement|banner-ad)([\W_]|$)/.test(name)) hide(el);
            }
          }
          clean();
          new MutationObserver(clean).observe(document.documentElement, {
            childList: true, subtree: true, attributes: true,
            attributeFilter: ['src','class','id']
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
