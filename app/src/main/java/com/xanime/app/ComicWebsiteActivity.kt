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
        // Do not display any page content until its ad-cleanup scripts have run.
        webView.visibility = android.view.View.INVISIBLE
        // Avoid showing previously cached ad scripts/banners while testing the updated filters.
        if (siteName.equals("AllPornComic", ignoreCase = true) ||
            siteName.equals("HitomiLA", ignoreCase = true) ||
            siteName.equals("nHentai", ignoreCase = true)) {
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
                    // nHentai should never open separate popup windows; these are commonly
                    // used by ad/click-redirect scripts. Keep normal in-page navigation intact.
                    if (siteName.equals("nHentai", ignoreCase = true)) return false
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
                    return if (nhentaiAdRequest || isAllPornComicAdRequest(uri) || isKnownAdHost(host)) {
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
                        if (siteName.equals("nHentai", ignoreCase = true)) {
                            page.evaluateJavascript(nhentaiAdCleanupScript) {
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

    /** Exact ad assets and banner endpoints identified in AllPornComic page source. */
    private fun isAllPornComicAdRequest(uri: Uri): Boolean {
        // Block only the two confirmed AllPornComic ad loaders and their spot iframes.
        // Other resources on this host must remain available to the page.
        if (uri.host?.lowercase() != "js.allporncomic.com") return false
        val path = uri.path?.lowercase().orEmpty()
        return path == "/l7pw5.js" ||
            path == "/aum05.js" ||
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
            "darnobedienceupscale.com",
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
