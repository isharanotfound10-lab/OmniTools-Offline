package com.xanime.app

import android.app.Activity
import android.app.DownloadManager
import android.content.ContentValues
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.URLUtil
import java.io.File
import java.io.FileOutputStream
import android.graphics.Color
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
    private val downloadBridge = BlobDownloadBridge()

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
        // Do not display any page content until its ad-cleanup scripts have run.
        webView.visibility = android.view.View.INVISIBLE
        // Avoid showing previously cached ad scripts/banners while testing the updated filters.
        if (siteName.equals("AllPornComic", ignoreCase = true) ||
            siteName.equals("HitomiLA", ignoreCase = true) ||
            siteName.equals("nHentai", ignoreCase = true) ||
            siteName.equals("NovelCrow", ignoreCase = true) ||
            siteName.equals("MultiPorn", ignoreCase = true) ||
            siteName.equals("KingComix", ignoreCase = true)) {
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
            // HitomiLA creates ZIP downloads as Blob URLs; bridge those to Downloads.
            addJavascriptInterface(downloadBridge, "XAnimeDownloads")
            setDownloadListener { url, userAgent, contentDisposition, mimeType, _ ->
                startRegularDownload(url, userAgent, contentDisposition, mimeType)
            }

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
                    // nHentai should never open separate popup windows; these are commonly
                    // used by ad/click-redirect scripts. Keep normal in-page navigation intact.
                    if (siteName.equals("nHentai", ignoreCase = true) ||
                        siteName.equals("HitomiLA", ignoreCase = true) ||
                        siteName.equals("NovelCrow", ignoreCase = true) ||
                        siteName.equals("MultiPorn", ignoreCase = true) ||
                        siteName.equals("KingComix", ignoreCase = true)) return false
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
                                else { /* silently discard ad pop-ups and external redirect targets */ }
                                closePopup(popup)
                            }
                            return true
                        }
                        override fun onPageStarted(view: WebView?, url: String?, favicon: android.graphics.Bitmap?) {
                            if (routed || url.isNullOrBlank() || url == "about:blank") return
                            routed = true
                            if (isAllowedPage(url)) browser?.loadUrl(url)
                            else { /* silently discard ad pop-ups and external redirect targets */ }
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
                    val path = uri.path?.lowercase().orEmpty()
                    val nhentaiAdRequest = siteName.equals("nHentai", ignoreCase = true) &&
                        (host == "spendstornese.rest" || host.endsWith(".spendstornese.rest") ||
                         path == "/out/spicy-box" || path.startsWith("/out/spicy-box/") ||
                         path.startsWith("/ads/spicybox-300x250"))
                    val hitomiBannerScript = siteName.equals("HitomiLA", ignoreCase = true) &&
                        host == "js.wpadmngr.com" && path == "/static/admanager.js"
                    // MultiPorn's supplied fake robot/notification ad is served from CloudPiko.
                    val multiPornRobotAd = siteName.equals("MultiPorn", ignoreCase = true) &&
                        (host == "cloudpiko.com" || host.endsWith(".cloudpiko.com")) &&
                        path.startsWith("/sb/notifications/utility/default/robot/")
                    // KingComix's supplied page contains JuicyAds iframes, a tracking
                    // iframe and a CloudPiko gambling notification overlay.
                    val kingComixAdRequest = siteName.equals("KingComix", ignoreCase = true) && (
                        host == "poweredby.jads.co" ||
                        host == "t.nettrck.store" ||
                        ((host == "cloudpiko.com" || host.endsWith(".cloudpiko.com")) && (
                            path.startsWith("/sb/notifications/gambling/default/custom-banner/") ||
                            path.startsWith("/sb/notifications/dating/") ||
                            path.startsWith("/sb/ssp/utility/social-media/whatsapp/")
                        ))
                    )
                    return if (nhentaiAdRequest || hitomiBannerScript || multiPornRobotAd || kingComixAdRequest || isAllPornComicAdRequest(uri) || isKnownAdHost(host)) {
                        WebResourceResponse("text/plain", "utf-8", 204, "No Content", emptyMap(),
                            ByteArrayInputStream(ByteArray(0)))
                    } else super.shouldInterceptRequest(view, request)
                }

                override fun onPageStarted(view: WebView?, url: String?, favicon: android.graphics.Bitmap?) {
                    super.onPageStarted(view, url, favicon)
                    // Keep the page hidden while its ad filters are installed so ads never flash.
                    view?.visibility = android.view.View.INVISIBLE
                }

                override fun onPageFinished(view: WebView?, url: String?) {
                    super.onPageFinished(view, url)
                    val page = view ?: return
                    page.evaluateJavascript(adCleanupScript) {
                        page.evaluateJavascript(blobDownloadScript, null)
                        if (siteName.equals("HitomiLA", ignoreCase = true)) {
                            page.evaluateJavascript(hitomiAdCleanupScript) {
                                page.visibility = android.view.View.VISIBLE
                            }
                        } else if (siteName.equals("nHentai", ignoreCase = true)) {
                            page.evaluateJavascript(nhentaiAdCleanupScript) {
                                page.visibility = android.view.View.VISIBLE
                            }
                        } else if (siteName.equals("AllPornComic", ignoreCase = true)) {
                            page.evaluateJavascript(allPornComicAdCleanupScript) {
                                page.visibility = android.view.View.VISIBLE
                            }
                        } else if (siteName.equals("NovelCrow", ignoreCase = true)) {
                            page.evaluateJavascript(novelCrowAdCleanupScript) {
                                page.visibility = android.view.View.VISIBLE
                            }
                        } else if (siteName.equals("MultiPorn", ignoreCase = true)) {
                            page.evaluateJavascript(multiPornAdCleanupScript) {
                                page.visibility = android.view.View.VISIBLE
                            }
                        } else if (siteName.equals("KingComix", ignoreCase = true)) {
                            page.evaluateJavascript(kingComixAdCleanupScript) {
                                page.visibility = android.view.View.VISIBLE
                            }
                        } else {
                            page.visibility = android.view.View.VISIBLE
                        }
                    }
                }

                override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                    if (request == null || !request.isForMainFrame) return false
                    if (siteName.equals("nHentai", ignoreCase = true) &&
                        isNHaentaiAdRedirect(request.url)) return true
                    return !isAllowedPage(request.url.toString())
                }

                @Suppress("DEPRECATION")
                override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean {
                    if (url.isNullOrBlank()) return true
                    if (siteName.equals("nHentai", ignoreCase = true) &&
                        isNHaentaiAdRedirect(Uri.parse(url))) return true
                    return !isAllowedPage(url)
                }
            }
        }
    }

    private fun isNHaentaiAdRedirect(uri: Uri): Boolean {
        val host = uri.host?.lowercase().orEmpty()
        val path = uri.path?.lowercase().orEmpty()
        return (host == allowedHost && (path == "/out/spicy-box" || path.startsWith("/out/spicy-box/"))) ||
            host == "spendstornese.rest" || host.endsWith(".spendstornese.rest")
    }

    private fun isAllowedPage(rawUrl: String): Boolean {
        val uri = runCatching { Uri.parse(rawUrl) }.getOrNull() ?: return false
        val scheme = uri.scheme?.lowercase()
        val host = uri.host?.lowercase() ?: return false
        return (scheme == "https" || scheme == "http") &&
            (host == allowedHost || host.endsWith(".$allowedHost"))
    }

    /** Remove only the KingComix ad slots and notification overlay identified in the supplied page. */
    private val kingComixAdCleanupScript = """
        (function() {
          if (window.__xanimeKingComixAdsInstalled) return;
          window.__xanimeKingComixAdsInstalled = true;

          var cloudPikoSignature = 'pl-1a07bf653e9b175030ac66b6a1052465';
          function removeKingComixAds() {
            if (!document.documentElement) return;

            // Remove the KingComix Patreon membership promotion panel as requested.
            document.querySelectorAll('aside.kc-patreon-panel').forEach(function(panel) {
              if (panel.isConnected) panel.remove();
            });

            // These publisher-owned slots contain the supplied JuicyAds zones
            // 1129302, 1129305 and 1129591. Remove the entire reserved-height slot.
            document.querySelectorAll(
              'aside.kc-ad-slot, .kc-ad-slot-post_bottom, .kc-ad-slot-sidebar'
            ).forEach(function(slot) {
              if (slot.isConnected) slot.remove();
            });

            // Catch the same JuicyAds creative if it is injected outside the
            // known slot markup, without touching ordinary comic images/iframes.
            document.querySelectorAll('iframe.kc-ad-frame, iframe[srcdoc]').forEach(function(frame) {
              if (!frame.isConnected) return;
              var srcdoc = (frame.getAttribute('srcdoc') || '').toLowerCase();
              if (frame.classList.contains('kc-ad-frame') ||
                  srcdoc.indexOf('adsbyjuicy') >= 0 ||
                  srcdoc.indexOf('poweredby.jads.co/js/jads.js') >= 0) {
                var banner = frame.closest('.kc-ad-banner');
                var slot = frame.closest('.kc-ad-slot');
                if (slot) slot.remove();
                else if (banner) banner.remove();
                else frame.remove();
              }
            });

            // Remove the exact CloudPiko notification markup from the supplied
            // KingComix captures, including the dating-message overlay wrapper.
            document.querySelectorAll(
              '[class*="' + cloudPikoSignature + '__wrap"],' +
              '[class*="' + cloudPikoSignature + '__content"],' +
              '[class*="' + cloudPikoSignature + '__closelink"]'
            ).forEach(function(node) {
              if (!node.isConnected) return;
              var wrap = node.closest('[class*="' + cloudPikoSignature + '__wrap"]') || node;
              var frame = wrap.closest('iframe');
              if (frame) wrap = frame;
              var parent = wrap.parentElement;
              if (parent && parent.tagName === 'DIV' &&
                  (parent.children.length === 1 || !parent.textContent.trim())) {
                wrap = parent;
              }
              wrap.remove();
            });

            // A CloudPiko copy may be embedded as a separate frame.
            document.querySelectorAll('iframe').forEach(function(frame) {
              if (!frame.isConnected) return;
              var src = (frame.getAttribute('src') || '').toLowerCase();
              if (src.indexOf('cloudpiko.com/sb/notifications/gambling/default/custom-banner/') >= 0 ||
                  src.indexOf('cloudpiko.com/sb/notifications/dating/') >= 0 ||
                  src.indexOf('cloudpiko.com/sb/ssp/utility/social-media/whatsapp/') >= 0) {
                var parent = frame.parentElement;
                if (parent && parent.tagName === 'DIV' &&
                    (parent.children.length === 1 || !parent.textContent.trim())) parent.remove();
                else frame.remove();
              }
            });
          }

          removeKingComixAds();
          new MutationObserver(removeKingComixAds).observe(document.documentElement, {
            childList: true, subtree: true, attributes: true,
            attributeFilter: ['class', 'src', 'srcdoc', 'style']
          });
          setInterval(removeKingComixAds, 1200);
        })();
    """.trimIndent()

    /** Remove MultiPorn's supplied fake robot notification overlay and its iframe wrapper. */
    private val multiPornAdCleanupScript = """
        (function() {
          if (window.__xanimeMultiPornAdsInstalled) return;
          window.__xanimeMultiPornAdsInstalled = true;

          var signature = 'pl-d437c404dd76f50eb9045a23aa761c74';
          function removeMultiPornAds() {
            if (!document.documentElement) return;
            var nodes = document.querySelectorAll(
              '[class*="' + signature + '__wrap"],' +
              '[class*="' + signature + '__content"],' +
              '[class*="' + signature + '__closelink"]'
            );
            nodes.forEach(function(node) {
              if (!node.isConnected) return;
              var target = node.closest('[class*="' + signature + '__wrap"]') || node;
              // If this ad is inside an iframe, remove the iframe and its reserved slot.
              var frame = target.closest('iframe');
              if (frame) target = frame;
              var parent = target.parentElement;
              if (parent && parent.tagName === 'DIV' &&
                  (parent.children.length === 1 || parent.textContent.trim() === '')) {
                target = parent;
              }
              target.remove();
            });

            // Some copies are delivered as a standalone cross-origin robot-check iframe.
            // Its URL is filtered natively; this catches same-origin copies too.
            document.querySelectorAll('iframe').forEach(function(frame) {
              if (!frame.isConnected) return;
              var src = (frame.getAttribute('src') || '').toLowerCase();
              var isCloudPikoRobot = src.indexOf('cloudpiko.com/sb/notifications/utility/default/robot/') >= 0;
              var hasRobotOverlay = false;
              if (!isCloudPikoRobot) {
                try {
                  var body = frame.contentDocument && frame.contentDocument.body;
                  hasRobotOverlay = !!body && (
                    body.textContent.indexOf('CONFIRM YOU ARE NOT A ROBOT') >= 0 ||
                    !!body.querySelector('[class*="' + signature + '__wrap"]')
                  );
                } catch (e) {}
              }
              if (isCloudPikoRobot || hasRobotOverlay) {
                var wrapper = frame.parentElement;
                if (wrapper && wrapper.tagName === 'DIV' &&
                    (wrapper.children.length === 1 || wrapper.textContent.trim() === '')) {
                  wrapper.remove();
                } else frame.remove();
              }
            });

            // Remove only now-empty wrappers left by the identified overlay.
            document.querySelectorAll('div').forEach(function(node) {
              if (!node.isConnected || node.children.length !== 0) return;
              var cls = typeof node.className === 'string' ? node.className : '';
              if (cls.indexOf(signature) >= 0) node.remove();
            });
          }

          removeMultiPornAds();
          var observer = new MutationObserver(removeMultiPornAds);
          observer.observe(document.documentElement, {
            childList: true, subtree: true, attributes: true,
            attributeFilter: ['class', 'src', 'style']
          });
          setInterval(removeMultiPornAds, 1200);
        })();
    """.trimIndent()

    /** Remove NovelCrow's confirmed ad slots and their full reserved-space wrappers. */
    private val novelCrowAdCleanupScript = """
        (function() {
          if (window.__xanimeNovelCrowAdsInstalled) return;
          window.__xanimeNovelCrowAdsInstalled = true;

          function removeNovelCrowAds() {
            if (!document.documentElement) return;

            // Spot 1882168 is the 240px sidebar block; spot 2089393 is
            // the 75px/728x90 banner; spot 1876095 is the 300x250 reader ad.
            var selectors = [
              '[data-cl-spot="1882168"]',
              '[data-cl-spot="2089393"]',
              '.ai-viewport-2[data-code^="PGRpdiBjbGFzcz0nY29kZS1ibG9jay"]',
              '.ai-viewport-1[data-code^="PGRpdiBjbGFzcz0nY29kZS1ibG9jay"]',
              '[id^="__clb-spot_2089393_"]',
              '[id^="__clb-spot_1876095_"]',
              'iframe[id^="__clb-spot_2089393_"]',
              'iframe[id^="__clb-spot_1876095_"]'
            ].join(',');

            document.querySelectorAll(selectors).forEach(function(node) {
              if (!node.isConnected) return;
              var target = node;

              // Remove the publisher's full fixed-height code block / viewport,
              // not merely its iframe, so no blank 75px/240px/300px gap remains.
              var codeBlock = node.closest('.code-block');
              var viewport = node.closest('.ai-viewport-1, .ai-viewport-2, .ai-viewport-3');
              if (codeBlock) target = codeBlock;
              else if (viewport) target = viewport;
              else {
                var parent = node.parentElement;
                while (parent && parent !== document.body && parent !== document.documentElement) {
                  var h = (parent.style && parent.style.height || '').replace(/\s/g, '');
                  if (parent.classList.contains('code-block') ||
                      h === '75px' || h === '90px' || h === '240px' || h === '250px') {
                    target = parent;
                    break;
                  }
                  if (parent.classList.contains('c-sidebar') ||
                      parent.classList.contains('c-container')) break;
                  target = parent;
                  parent = parent.parentElement;
                }
              }
              target.remove();
            });

            // Remove only empty NovelCrow sidebar/widget shells left behind
            // after those exact ad blocks are removed.
            document.querySelectorAll('.c-sidebar.c-top-second-sidebar').forEach(function(sidebar) {
              var meaningful = sidebar.querySelector(
                'img,video,canvas,iframe:not([id^="__clb-"]),a,button,input,select,textarea'
              );
              var text = (sidebar.innerText || '').replace(/\s+/g, '').trim();
              if (!meaningful && !text) sidebar.remove();
            });
          }

          removeNovelCrowAds();
          new MutationObserver(removeNovelCrowAds).observe(document.documentElement, {
            childList: true, subtree: true, attributes: true,
            attributeFilter: ['id', 'data-cl-spot', 'data-code', 'src', 'class', 'style']
          });
        })();
    """.trimIndent()

    /** Remove the exact AllPornComic 300x250 body-top ad slots, including their reserved space. */
    private val allPornComicAdCleanupScript = """
        (function() {
          if (window.__xanimeAllPornComicSlotsRemoved) return;
          window.__xanimeAllPornComicSlotsRemoved = true;

          function removeAllPornComicSlots() {
            if (!document.documentElement) return;
            var slots = document.querySelectorAll(
              '.ad.c-ads.custom-code.body-top-ads'
            );
            for (var i = slots.length - 1; i >= 0; i--) {
              var slot = slots[i];
              if (slot.querySelector(
                'iframe[src*="/api/spots/230130"],script[src*="/VkOK3.js"]'
              )) {
                slot.remove();
              }
            }
            // Fallback for the exact spot iframe if the publisher changes its wrapper classes.
            document.querySelectorAll('iframe[src*="/api/spots/230130"]').forEach(function(frame) {
              var region = frame.closest('[role="region"][aria-label="Advertisements"]');
              var container = region && region.closest('.ad.c-ads.custom-code.body-top-ads');
              if (container) container.remove();
              else if (region) region.remove();
              else frame.remove();
            });
          }

          removeAllPornComicSlots();
          new MutationObserver(removeAllPornComicSlots).observe(document.documentElement, {
            childList: true, subtree: true, attributes: true,
            attributeFilter: ['src', 'class']
          });
        })();
    """.trimIndent()

    /** Exact ad assets and banner endpoints identified in AllPornComic page source. */
    private fun isAllPornComicAdRequest(uri: Uri): Boolean {
        // Block only the two confirmed AllPornComic ad loaders and their spot iframes.
        // Other resources on this host must remain available to the page.
        if (uri.host?.lowercase() != "js.allporncomic.com") return false
        val path = uri.path?.lowercase().orEmpty()
        return path == "/l7pw5.js" ||
            path == "/aum05.js" ||
            path == "/vkok3.js" ||
            path.startsWith("/api/spots/230130") ||
            path.startsWith("/api/spots/229371") ||
            path.startsWith("/api/spots/229326")
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
            "darnobedienceupscale.com", "ahmybid.net",
            "1b09d1e745.com",
            // nHentai ad delivery and click-redirect domains identified in the supplied file.
            "cdn.tsyndicate.com", "tsyndicate.com",
            "go.lanitesasexige.com", "go.janusaent.com", "fozekikikiminu.com",
            // nHentai sponsored card/image delivery host from the corrected source.
            "spendstornese.rest"
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
            'script[src*="cdn.tsyndicate.com"]',
            '[id^="ts_ad_native_"]', '[id^="ts_ms_"]',
            '[class*="ts_ad_native"]', '.ts-im-container',
            '.ts-im-video-wrapper', '.ts-im-ad-link',
            '.mn-related-container', '.mn-thumb',
            '[data-element="overlay"][data-izone="stop"]',
            '[data-izone="stop"]', '.Ed80ZsLIdBCE2v4',
            'div[id^="__clb-"]'
          ].join(',') + '{display:none!important;visibility:hidden!important;' +
            'max-height:0!important;min-height:0!important;height:0!important;' +
            'margin:0!important;padding:0!important;border:0!important;pointer-events:none!important;}';

          function hasComicArtwork(node) {
            if (!node || !node.querySelectorAll) return false;
            var imgs = node.querySelectorAll('img');
            for (var i = 0; i < imgs.length; i++) {
              var img = imgs[i], src = (img.currentSrc || img.src || '').toLowerCase();
              if (img.naturalWidth > 250 || src.indexOf('/galleries/') >= 0 || src.indexOf('/images/') >= 0 || src.indexOf('/comics/') >= 0) return true;
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
          function isAdOrHidden(el) {
            if (!el || el.nodeType !== 1) return true;
            if (el.matches && el.matches(
              '.Ed80ZsLIdBCE2v4,iframe[id^="__clb-"],div[id^="__clb-"],' +
              '[data-izone="stop"],[data-element="static-title"]'
            )) return true;
            var cs = window.getComputedStyle(el);
            return cs.display === 'none' || cs.visibility === 'hidden' ||
              parseFloat(cs.opacity || '1') === 0;
          }
          function hasMeaningfulContent(el) {
            if (!el || el.nodeType !== 1) return false;
            var walker = document.createTreeWalker(el, NodeFilter.SHOW_TEXT, null);
            var textNode;
            while ((textNode = walker.nextNode())) {
              var textParent = textNode.parentElement;
              if (textParent && textParent.closest('script,style,noscript,template')) continue;
              if ((textNode.nodeValue || '').trim()) return true;
            }
            var descendants = el.querySelectorAll('img,video,canvas,iframe,button,a,input,svg');
            for (var i = 0; i < descendants.length; i++) {
              var item = descendants[i];
              if (isAdOrHidden(item)) continue;
              if (item.tagName === 'IMG') {
                var src = (item.currentSrc || item.src || '').toLowerCase();
                if (src.indexOf('bestcrongreenfunnel.com') >= 0 ||
                    src.indexOf('chaseherbalpasty.com') >= 0) continue;
                if (item.naturalWidth > 0) return true;
              } else if (item.tagName === 'IFRAME') {
                return true;
              } else if (item.getBoundingClientRect().width > 0 &&
                         item.getBoundingClientRect().height > 0) return true;
            }
            return false;
          }
          function collapseEmptyAdAncestors(start) {
            var current = start;
            for (var depth = 0; current && depth < 8; depth++) {
              if (current === document.body || current === document.documentElement) break;
              if (hasComicArtwork(current) || hasMeaningfulContent(current)) break;
              var parent = current.parentElement;
              current.remove();
              current = parent;
            }
          }
          function removeAdNode(node) {
            if (!node || !node.parentElement) return;
            var parent = node.parentElement;
            node.remove();
            collapseEmptyAdAncestors(parent);
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

            // HitomiLA interstitial/contact overlays: the wording and generated CSS
            // classes vary, so identify this ad by its overlay markers and tracking host.
            // Remove the complete overlay directly; generic artwork detection must not
            // mistake its large tracking image for comic artwork and preserve the shell.
            var contactOverlays = document.querySelectorAll(
              '[data-element="overlay"][data-izone="stop"]'
            );
            for (var co = contactOverlays.length - 1; co >= 0; co--) {
              var overlay = contactOverlays[co];
              var trackingImage = overlay.querySelector(
                'img[src*="bestcrongreenfunnel.com"],img[src*="chaseherbalpasty.com"],' +
                'img[src*="darnobedienceupscale.com"]'
              );
              if (trackingImage) overlay.remove();
            }

            // Mobile-only HitomiLA interstitials sometimes render as a full-screen
            // "Skip now" / "Scanning" layer with a fake progress indicator and no
            // stable class or ID. Remove only a viewport-sized fixed overlay matching
            // all of those signals; never remove comic artwork or ordinary page content.
            if (window.innerWidth <= 900 || window.matchMedia('(pointer: coarse)').matches) {
              var mobileLayers = document.querySelectorAll('body *');
              for (var ml = mobileLayers.length - 1; ml >= 0; ml--) {
                var layer = mobileLayers[ml];
                if (!layer || layer.id === styleId || hasComicArtwork(layer)) continue;
                var layerStyle = window.getComputedStyle(layer);
                if (layerStyle.position !== 'fixed' && layerStyle.position !== 'absolute') continue;
                var layerText = (layer.innerText || layer.textContent || '').replace(/\\s+/g, ' ').trim().toLowerCase();
                if (layerText.indexOf('skip now') < 0 || layerText.indexOf('scann') < 0) continue;
                var hasProgress = !!layer.querySelector(
                  '[role="progressbar"],progress,[class*="progress" i],[id*="progress" i]'
                );
                if (!hasProgress) continue;
                var layerRect = layer.getBoundingClientRect();
                if (layerRect.width < window.innerWidth * 0.55 ||
                    layerRect.height < window.innerHeight * 0.35) continue;
                layer.remove();
              }
            }

            // Remove remaining explicitly marked ad zones and their empty wrappers.
            var zones = document.querySelectorAll('[data-izone="stop"]');
            for (var z = zones.length - 1; z >= 0; z--) removeAdNode(zones[z]);

            // Advertisement labels may be rendered separately from the creative.
            var labels = document.querySelectorAll('[data-element="static-title"],[data-element="title"],[aria-label*="advertisement" i]');
            for (var l = 0; l < labels.length; l++) {
              var label = (labels[l].textContent || '').trim().toLowerCase();
              if (label === 'advertisement' || label === 'sponsored') removeAdPlaceholder(labels[l]);
            }

            // nHentai ad delivery scripts and native-ad containers from the supplied source.
            var nhScripts = document.querySelectorAll('script[src*="cdn.tsyndicate.com"]');
            for (var ns = 0; ns < nhScripts.length; ns++) removeAdNode(nhScripts[ns]);
            var nhAds = document.querySelectorAll('[id^="ts_ad_native_"],[id^="ts_ms_"],[class*="ts_ad_native"],.ts-im-container,.ts-im-video-wrapper,.ts-im-ad-link,.mn-related-container,.mn-thumb');
            for (var na = nhAds.length - 1; na >= 0; na--) {
              if (!hasComicArtwork(nhAds[na])) removeAdNode(nhAds[na]);
            }

            // Known third-party script tags, injected banner iframes and their now-empty wrappers.
            var scripts = document.querySelectorAll('script[src*="chaseherbalpasty.com"],script[src*="bestcrongreenfunnel.com"],script[src*="darnobedienceupscale.com"]');
            for (var s = 0; s < scripts.length; s++) {
              removeAdNode(scripts[s]);
            }
            // AllPornComic's ad slots are wrapped in <center> and use class="na".
            // Remove the whole wrapper, not just the blocked iframe, to collapse its gap.
            var allPornComicFrames = document.querySelectorAll(
              'iframe.na[src*="/api/spots/229371"],iframe.na[src*="/api/spots/229326"],' +
              'iframe[src*="js.allporncomic.com/api/spots/229371"],' +
              'iframe[src*="js.allporncomic.com/api/spots/229326"]'
            );
            for (var apf = allPornComicFrames.length - 1; apf >= 0; apf--) {
              var adCenter = allPornComicFrames[apf].closest('center');
              if (adCenter) adCenter.remove();
              else removeAdNode(allPornComicFrames[apf]);
            }
            var allPornComicScripts = document.querySelectorAll(
              'script[src*="js.allporncomic.com/L7pW5.js"],script[src*="js.allporncomic.com/Aum05.js"]'
            );
            for (var aps = 0; aps < allPornComicScripts.length; aps++) {
              removeAdNode(allPornComicScripts[aps]);
            }
            var frames = document.querySelectorAll('iframe[id^="__clb-"],iframe[id*="_container"],iframe[src*="doubleclick"],iframe[src*="googlesyndication"],iframe[src*="chaseherbalpasty.com"],iframe[src*="bestcrongreenfunnel.com"],iframe[src*="darnobedienceupscale.com"],iframe[src*="js.allporncomic.com/api/spots/229371"],iframe[src*="js.allporncomic.com/api/spots/229326"]');
            for (var f = frames.length - 1; f >= 0; f--) {
              // HitomiLA's banner is nested in layout wrappers; remove the empty
              // ancestors after deleting its exact third-party iframe.
              removeAdNode(frames[f]);
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

    /** Remove the two confirmed mobile-only HitomiLA ad overlays by their unique content. */
    private val hitomiAdCleanupScript = """
        (function() {
          if (window.__xanimeHitomiMobileAdsRemoved) return;
          window.__xanimeHitomiMobileAdsRemoved = true;

          function removeAdShell(node) {
            if (!node || !node.parentElement) return;
            var target = node;
            // The mobile creatives are mounted inside a fixed-position wrapper.
            // Remove that wrapper when present so its invisible click layer disappears too.
            for (var i = 0; i < 4 && target.parentElement &&
                 target.parentElement !== document.body; i++) {
              var parent = target.parentElement;
              var style = window.getComputedStyle(parent);
              target = parent;
              if (style.position === 'fixed' || style.position === 'absolute') break;
            }
            if (target && target !== document.body &&
                target !== document.documentElement && target.parentElement) {
              target.remove();
            }
          }

          function cleanHitomiMobileAds() {
            if (!document.documentElement) return;

            // Remove HitomiLA's occasional 900x250 reserved banner slot.
            var bannerSlots = document.querySelectorAll(
              '[data-banner-id="63391"],script[src*="js.wpadmngr.com/static/adManager.js"]'
            );
            for (var b = bannerSlots.length - 1; b >= 0; b--) {
              var banner = bannerSlots[b];
              var shell = banner.closest('.PH8qZXFPTQY7lAgiP');
              if (shell) shell.remove();
              else if (banner.matches('[data-banner-id="63391"]')) banner.remove();
              else removeAdShell(banner);
            }

            // Fake dating/chat creative: "Ayesha (Online)" and its LIKE/REPLY buttons.
            var zones = document.querySelectorAll('[data-izone="uc-area"]');
            for (var i = zones.length - 1; i >= 0; i--) {
              var zone = zones[i];
              var text = (zone.textContent || '').toLowerCase();
              var images = zone.querySelectorAll('img');
              var adAsset = false;
              for (var j = 0; j < images.length; j++) {
                var src = (images[j].src || '').toLowerCase();
                if (src.indexOf('ahmybid.net/') >= 0 ||
                    src.indexOf('bestcrongreenfunnel.com/') >= 0) {
                  adAsset = true;
                  break;
                }
              }
              if (text.indexOf('ayesha (online)') >= 0 ||
                  (text.indexOf('do you like my boobs') >= 0 &&
                   text.indexOf('reply') >= 0) || adAsset) {
                removeAdShell(zone);
              }
            }

            // The remaining mobile offer ad is rendered inside a cross-origin iframe.
            // Its stable title is "offer"; remove its fixed-position wrapper, not page iframes.
            var offerFrames = document.querySelectorAll('iframe[title="offer"]');
            for (var o = offerFrames.length - 1; o >= 0; o--) {
              removeAdShell(offerFrames[o]);
            }

            // Fake antivirus/scanning creative with fabricated Windows file paths.
            var scans = document.querySelectorAll('[data-element="scan"]');
            for (var k = scans.length - 1; k >= 0; k--) {
              var scan = scans[k];
              var scanText = (scan.textContent || '').toLowerCase();
              if (scanText.indexOf('total scanned:') >= 0 &&
                  (scanText.indexOf('commonly infected areas') >= 0 ||
                   scanText.indexOf('windows\\system32') >= 0 ||
                   scanText.indexOf('windowsystem32') >= 0)) {
                var zone = scan.closest('[data-izone="uc-area"]') || scan;
                removeAdShell(zone);
              }
            }

            // Also remove any leftover exact ad assets if their creative is rebuilt.
            document.querySelectorAll(
              'img[src*="assets.ahmybid.net/"],' +
              'img[src*="bestcrongreenfunnel.com/v1/track/"],' +
              'img[src*="d3599d79b7.1b09d1e745.com/"]'
            ).forEach(function(img) {
              var zone = img.closest('[data-izone="uc-area"]');
              if (zone) removeAdShell(zone);
            });
          }

          cleanHitomiMobileAds();
          new MutationObserver(function() {
            cleanHitomiMobileAds();
          }).observe(document.documentElement, {
            childList: true, subtree: true, attributes: true,
            attributeFilter: ['src', 'class', 'style', 'data-izone']
          });
        })();
    """.trimIndent()

    /** Exact nHentai ad elements from the corrected page source. */
    private val nhentaiAdCleanupScript = """
        (function() {
          if (window.__xanimeNhentaiAdsRemoved) return;
          window.__xanimeNhentaiAdsRemoved = true;

          var styleId = 'xanime-nhentai-ad-cleanup';
          var css = [
            '.hdr-slot-m',
            '#sbx-hdr',
            'a#sbx-hdr[href*="/out/spicy-box"]',
            'a[href*="/out/spicy-box"]',
            'video[src*="spicybox-300x250"]',
            'video[poster*="spicybox-300x250"]',
            'img[src*="spendstornese.rest/tsk/"]',
            'img[src*="spendstornese.rest"]'
          ].join(',') + '{display:none!important;visibility:hidden!important;' +
            'width:0!important;height:0!important;max-height:0!important;min-height:0!important;' +
            'margin:0!important;padding:0!important;border:0!important;pointer-events:none!important;}';

          function removeAdElements() {
            if (!document.documentElement) return;
            if (!document.getElementById(styleId)) {
              var style = document.createElement('style');
              style.id = styleId;
              style.textContent = css;
              (document.head || document.documentElement).appendChild(style);
            }

            // Remove the sponsored video slot and its reserved layout space.
            document.querySelectorAll(
              '.hdr-slot-m, #sbx-hdr, a[href*="/out/spicy-box"], ' +
              'video[src*="spicybox-300x250"], video[poster*="spicybox-300x250"]'
            ).forEach(function(node) {
              var parent = node.closest('.hdr-slot-m') ||
                           node.closest('a[href*="/out/spicy-box"]') || node;
              if (parent && parent.parentNode) parent.parentNode.removeChild(parent);
            });

            // Remove only the three-card creative grid when its images come from
            // the exact ad host; do not touch the guest reading-history section.
            document.querySelectorAll('body > .I8Y5R, body .I8Y5R').forEach(function(grid) {
              var adImages = grid.querySelectorAll('img[src*="spendstornese.rest/tsk/"]');
              if (adImages.length > 0 && grid.parentNode) {
                grid.parentNode.removeChild(grid);
              }
            });

            // Remove any remaining exact ad creatives and their nearest ad-card shells.
            document.querySelectorAll('img[src*="spendstornese.rest/tsk/"]').forEach(function(img) {
              var card = img.closest('.Imkm5') || img;
              if (card.parentNode) card.parentNode.removeChild(card);
            });
          }

          removeAdElements();
          new MutationObserver(removeAdElements).observe(document.documentElement, {
            childList: true, subtree: true, attributes: true,
            attributeFilter: ['src','class','id','style']
          });
        })();
    """.trimIndent()

    private fun startRegularDownload(
        rawUrl: String?,
        userAgent: String?,
        contentDisposition: String?,
        mimeType: String?
    ) {
        val url = rawUrl?.takeIf { it.startsWith("https://") || it.startsWith("http://") } ?: return
        runCatching {
            val filename = URLUtil.guessFileName(url, contentDisposition, mimeType)
                .replace(Regex("""[\\/:*?"<>|]"""), "_")
                .ifBlank { "hitomi-download" }
            val request = DownloadManager.Request(Uri.parse(url)).apply {
                setTitle(filename)
                setDescription("Downloading from Hitomi.la")
                setMimeType(mimeType ?: "application/octet-stream")
                setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                setAllowedOverMetered(true)
                setAllowedOverRoaming(true)
                userAgent?.takeIf { it.isNotBlank() }?.let { addRequestHeader("User-Agent", it) }
                CookieManager.getInstance().getCookie(url)?.let { addRequestHeader("Cookie", it) }
                setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, filename)
            }
            val manager = getSystemService(DOWNLOAD_SERVICE) as DownloadManager
            manager.enqueue(request)
            Toast.makeText(this, "Download started: $filename", Toast.LENGTH_SHORT).show()
        }.onFailure {
            Toast.makeText(this, "Could not start download: ${it.message ?: "unknown error"}", Toast.LENGTH_LONG).show()
        }
    }

    /**
     * Receives HitomiLA's generated ZIP Blob in small chunks so large galleries
     * do not need to cross the Javascript bridge as one enormous Base64 string.
     */
    private inner class BlobDownloadBridge {
        private var output: FileOutputStream? = null
        private var tempFile: File? = null
        private var pendingName = "hitomi-download.zip"
        private var pendingMime = "application/zip"

        @Synchronized
        @JavascriptInterface
        fun beginBlobDownload(filename: String?, mime: String?) {
            runCatching {
                output?.close()
                tempFile?.delete()
                pendingName = safeDownloadName(filename ?: "hitomi-download.zip")
                pendingMime = mime?.takeIf { it.contains("/") } ?: "application/zip"
                tempFile = File(cacheDir, "xanime-${System.nanoTime()}.download")
                output = FileOutputStream(tempFile!!)
            }.onFailure { reportBlobFailure(it) }
        }

        @Synchronized
        @JavascriptInterface
        fun appendBlobChunk(base64Chunk: String?) {
            val stream = output ?: return
            runCatching {
                val chunk = android.util.Base64.decode(base64Chunk ?: return, android.util.Base64.DEFAULT)
                stream.write(chunk)
            }.onFailure { reportBlobFailure(it) }
        }

        @Synchronized
        @JavascriptInterface
        fun finishBlobDownload() {
            val stream = output ?: return
            val file = tempFile
            output = null
            tempFile = null
            runCatching {
                stream.flush()
                stream.close()
                if (file == null || !file.exists() || file.length() == 0L) {
                    throw IllegalStateException("The downloaded file was empty")
                }
                saveBlobToDownloads(file, pendingName, pendingMime)
                runOnUiThread {
                    Toast.makeText(this@ComicWebsiteActivity, "Saved to Downloads/XAnime: $pendingName", Toast.LENGTH_LONG).show()
                }
            }.onFailure {
                runCatching { stream.close() }
                runOnUiThread { reportBlobFailure(it) }
            }.also { file?.delete() }
        }

        @Synchronized
        @JavascriptInterface
        fun failBlobDownload(message: String?) {
            runCatching { output?.close() }
            output = null
            tempFile?.delete()
            tempFile = null
            reportBlobFailure(IllegalStateException(message ?: "Download failed"))
        }
    }

    private fun saveBlobToDownloads(file: File, filename: String, mime: String) {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            val values = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, filename)
                put(MediaStore.Downloads.MIME_TYPE, mime)
                put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/XAnime")
                put(MediaStore.Downloads.IS_PENDING, 1)
            }
            val uri = contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                ?: throw IllegalStateException("Could not create the download file")
            try {
                contentResolver.openOutputStream(uri)?.use { out ->
                    file.inputStream().use { input -> input.copyTo(out, 64 * 1024) }
                } ?: throw IllegalStateException("Could not write the download file")
                val complete = ContentValues().apply { put(MediaStore.Downloads.IS_PENDING, 0) }
                contentResolver.update(uri, complete, null, null)
            } catch (error: Throwable) {
                contentResolver.delete(uri, null, null)
                throw error
            }
        } else {
            val directory = getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
                ?: throw IllegalStateException("Downloads folder is unavailable")
            directory.mkdirs()
            file.copyTo(File(directory, filename), overwrite = true)
        }
    }

    private fun safeDownloadName(value: String): String {
        val cleaned = value.substringAfterLast('/')
            .replace(Regex("""[\\/:*?"<>|]"""), "_").trim()
        return cleaned.take(140).ifBlank { "hitomi-download.zip" }
    }

    private fun reportBlobFailure(error: Throwable) {
        runOnUiThread {
            Toast.makeText(this, "Download failed: ${error.message ?: "unknown error"}", Toast.LENGTH_LONG).show()
        }
    }

    /** Intercepts the Blob URL used by HitomiLA's gallery ZIP downloader. */
    private val blobDownloadScript = """
        (function() {
          if (window.__xanimeBlobDownloadsInstalled) return;
          window.__xanimeBlobDownloadsInstalled = true;

          var blobs = Object.create(null);
          var originalCreate = URL.createObjectURL.bind(URL);
          URL.createObjectURL = function(blob) {
            var url = originalCreate(blob);
            if (blob instanceof Blob) blobs[url] = blob;
            return url;
          };

          function sendBlob(blob, filename) {
            if (!(blob instanceof Blob)) {
              XAnimeDownloads.failBlobDownload('The ZIP generator did not return a Blob');
              return;
            }
            var name = (filename || 'hitomi-download.zip').split(/[\\\\/]/).pop();
            var mime = blob.type || 'application/zip';
            XAnimeDownloads.beginBlobDownload(name, mime);
            var offset = 0;
            var chunkSize = 192 * 1024;
            (async function() {
              while (offset < blob.size) {
                var buffer = await blob.slice(offset, offset + chunkSize).arrayBuffer();
                var bytes = new Uint8Array(buffer);
                var binary = '';
                for (var i = 0; i < bytes.length; i++) binary += String.fromCharCode(bytes[i]);
                XAnimeDownloads.appendBlobChunk(btoa(binary));
                offset += bytes.length;
              }
              XAnimeDownloads.finishBlobDownload();
            })().catch(function(error) {
              XAnimeDownloads.failBlobDownload(String(error));
            });
          }

          // HitomiLA's downloader finishes with saveAs(zipBlob, filename).
          // Intercept that final save operation directly rather than waiting
          // for FileSaver.js to create and click a temporary anchor.
          function hookSaveAs() {
            if (typeof window.saveAs === 'function' &&
                !window.saveAs.__xanimeWrapped) {
              var originalSaveAs = window.saveAs;
              var wrappedSaveAs = function(blob, filename) {
                if (blob instanceof Blob) {
                  sendBlob(blob, filename || 'hitomi-download.zip');
                  return;
                }
                return originalSaveAs.apply(this, arguments);
              };
              wrappedSaveAs.__xanimeWrapped = true;
              wrappedSaveAs.__xanimeOriginal = originalSaveAs;
              window.saveAs = wrappedSaveAs;
            }
          }

          function transferUrl(url, filename) {
            var known = blobs[url];
            if (known) {
              sendBlob(known, filename);
              return;
            }
            fetch(url).then(function(response) {
              if (!response.ok) throw new Error('Could not read generated ZIP (' + response.status + ')');
              return response.blob();
            }).then(function(blob) {
              sendBlob(blob, filename);
            }).catch(function(error) {
              XAnimeDownloads.failBlobDownload(String(error));
            });
          }

          function findDownloadLink(event) {
            var path = event.composedPath ? event.composedPath() : [];
            for (var i = 0; i < path.length; i++) {
              var node = path[i];
              if (node && node.tagName === 'A') return node;
            }
            var target = event.target;
            return target && target.closest ? target.closest('a') : null;
          }

          function intercept(event) {
            var link = findDownloadLink(event);
            if (!link) return;
            var href = link.href || link.getAttribute('href') || '';
            if (href.indexOf('blob:') !== 0) return;
            if (!link.hasAttribute('download') && !blobs[href]) return;
            event.preventDefault();
            event.stopPropagation();
            if (event.stopImmediatePropagation) event.stopImmediatePropagation();
            transferUrl(href, link.download || 'hitomi-download.zip');
          }

          document.addEventListener('click', intercept, true);
          var originalAnchorClick = HTMLAnchorElement.prototype.click;
          HTMLAnchorElement.prototype.click = function() {
            var href = this.href || this.getAttribute('href') || '';
            if (href.indexOf('blob:') === 0 &&
                (this.hasAttribute('download') || blobs[href])) {
              transferUrl(href, this.download || 'hitomi-download.zip');
              return;
            }
            return originalAnchorClick.apply(this, arguments);
          };

          hookSaveAs();
          // Some pages load FileSaver after DOM ready; re-check briefly as scripts appear.
          var observer = new MutationObserver(hookSaveAs);
          observer.observe(document.documentElement, {childList:true, subtree:true});
          setTimeout(function() {
            hookSaveAs();
            observer.disconnect();
          }, 10000);
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
