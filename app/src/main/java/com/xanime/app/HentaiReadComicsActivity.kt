package com.xanime.app

import android.app.Activity
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import org.jsoup.Jsoup
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.concurrent.Executors

class HentaiReadComicsActivity : Activity() {
    private val io = Executors.newSingleThreadExecutor()
    private val bg = Color.rgb(2, 6, 9)
    private val panel = Color.rgb(7, 28, 38)
    private val accent = Color.rgb(190, 75, 255)
    private val white = Color.WHITE
    private val muted = Color.rgb(174, 163, 193)
    private lateinit var root: LinearLayout
    private lateinit var body: LinearLayout
    private var query = ""
    private var currentPage = 1
    private var currentScreen = "home"
    private var selected: HentaiReadDetails? = null
    private var webView: WebView? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        showHome(load = true)
    }

    override fun onDestroy() {
        webView?.destroy()
        io.shutdownNow()
        super.onDestroy()
    }

    private fun base(title: String, back: (() -> Unit)? = null) {
        root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(18), dp(16), dp(12))
            setBackgroundColor(bg)
        }
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        if (back != null) header.addView(button("‹", false) { back() },
            LinearLayout.LayoutParams(dp(44), dp(42)).apply { marginEnd = dp(8) })
        val heading = text(title, 21, white, true)
        header.addView(heading, LinearLayout.LayoutParams(0, -2, 1f))
        root.addView(header, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(14) })
        val scroll = ScrollView(this).apply { isFillViewport = false }
        body = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        scroll.addView(body, ViewGroup.LayoutParams(-1, -2))
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        setContentView(root)
    }

    private fun showHome(load: Boolean = false) {
        currentScreen = "home"
        selected = null
        base("HENTAIREAD  •  COMICS")
        body.addView(text("Search and browse the HentaiRead catalogue", 12, muted, false)
            .apply { setPadding(0, 0, 0, dp(12)) })
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        val input = EditText(this).apply {
            hint = "Search comics..."
            setHintTextColor(muted)
            setTextColor(white)
            textSize = 14f
            isSingleLine = true
            setPadding(dp(12), 0, dp(10), 0)
            background = rounded(panel, 14)
            setText(query)
        }
        row.addView(input, LinearLayout.LayoutParams(0, dp(46), 1f).apply { marginEnd = dp(8) })
        row.addView(button("SEARCH") {
            query = input.text.toString().trim()
            currentPage = 1
            loadListing()
        }, LinearLayout.LayoutParams(dp(92), dp(46)))
        body.addView(row)
        body.addView(button("BROWSE HOMEPAGE", false) {
            query = ""
            currentPage = 1
            loadListing()
        }, LinearLayout.LayoutParams(-1, dp(42)).apply { topMargin = dp(8) })
        if (load) loadListing()
    }

    private fun listingUrl(): String {
        if (query.isBlank()) {
            return if (currentPage == 1) "https://hentairead.com/"
            else "https://hentairead.com/page/$currentPage/"
        }
        val encoded = URLEncoder.encode(query, "UTF-8")
        return if (currentPage == 1) "https://hentairead.com/?s=$encoded"
        else "https://hentairead.com/page/$currentPage/?s=$encoded"
    }

    private fun loadListing() {
        val url = listingUrl()
        body.removeViews(2, (body.childCount - 2).coerceAtLeast(0))
        body.addView(progressCard("Loading comics..."))
        io.execute {
            val result = runCatching { Jsoup.parse(request(url), url) }
            runOnUiThread {
                if (isFinishing || currentScreen != "home") return@runOnUiThread
                body.removeViews(2, (body.childCount - 2).coerceAtLeast(0))
                if (result.isFailure) {
                    body.addView(info("HentaiRead could not be loaded directly. Its security verification may be blocking the app. You can open the site in the in-app browser and complete verification there."))
                    body.addView(button("OPEN HENTAIREAD IN BROWSER") {
                        openWeb("https://hentairead.com/")
                    }, LinearLayout.LayoutParams(-1, dp(46)).apply { topMargin = dp(10) })
                    return@runOnUiThread
                }
                val comics = HentaiReadParser.parseListing(result.getOrThrow())
                if (comics.isEmpty()) {
                    body.addView(info("No comic cards were found. If the website is showing a security check, open it in the in-app browser first."))
                    body.addView(button("OPEN WEBSITE") { openWeb(url) },
                        LinearLayout.LayoutParams(-1, dp(44)).apply { topMargin = dp(10) })
                } else {
                    comics.forEachIndexed { index, comic ->
                        val card = LinearLayout(this).apply {
                            orientation = LinearLayout.HORIZONTAL
                            gravity = Gravity.CENTER_VERTICAL
                            setPadding(dp(9), dp(9), dp(9), dp(9))
                            background = rounded(panel, 14)
                            isClickable = true
                            setOnClickListener { loadDetails(comic.url) }
                        }
                        val cover = ImageView(this).apply {
                            scaleType = ImageView.ScaleType.CENTER_CROP
                            background = rounded(Color.rgb(15, 35, 45), 9)
                        }
                        card.addView(cover, LinearLayout.LayoutParams(dp(88), dp(116)).apply { marginEnd = dp(11) })
                        if (comic.cover.isNotBlank()) loadCover(comic.cover, cover)
                        val details = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
                        details.addView(text(comic.title, 14, white, true))
                        if (comic.genres.isNotEmpty()) details.addView(text(comic.genres.joinToString(" • "), 10, muted, false)
                            .apply { setPadding(0, dp(6), 0, 0) })
                        if (comic.rating.isNotBlank()) details.addView(text("★ " + comic.rating, 10, accent, true)
                            .apply { setPadding(0, dp(5), 0, 0) })
                        card.addView(details, LinearLayout.LayoutParams(0, -2, 1f))
                        body.addView(card, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(9) })
                    }
                    body.addView(button("NEXT PAGE  →", false) {
                        currentPage++
                        loadListing()
                    }, LinearLayout.LayoutParams(-1, dp(44)).apply { topMargin = dp(5) })
                }
            }
        }
    }

    private fun loadDetails(url: String) {
        currentScreen = "details"
        base("COMIC DETAILS") { showHome() }
        body.addView(progressCard("Loading comic details..."))
        io.execute {
            val result = runCatching {
                val doc = Jsoup.parse(request(url), url)
                HentaiReadParser.parseDetails(doc, url)
            }
            runOnUiThread {
                if (isFinishing || currentScreen != "details") return@runOnUiThread
                body.removeAllViews()
                if (result.isFailure) {
                    body.addView(info("Could not load this comic directly. You can open the page in the in-app browser."))
                    body.addView(button("OPEN COMIC PAGE") { openWeb(url) })
                    return@runOnUiThread
                }
                val details = result.getOrThrow()
                selected = details
                body.addView(text(details.title, 19, white, true).apply {
                    setPadding(0, 0, 0, dp(12))
                })
                if (details.cover.isNotBlank()) {
                    val cover = ImageView(this).apply { scaleType = ImageView.ScaleType.FIT_CENTER }
                    body.addView(cover, LinearLayout.LayoutParams(dp(150), dp(205)).apply { bottomMargin = dp(12) })
                    loadCover(details.cover, cover)
                }
                if (details.description.isNotBlank()) body.addView(info(details.description))
                body.addView(button("READ COMIC  ↗") { openWeb(details.readerUrl) },
                    LinearLayout.LayoutParams(-1, dp(48)).apply { topMargin = dp(14) })
                body.addView(button("OPEN COMIC WEBSITE", false) { openWeb(details.url) },
                    LinearLayout.LayoutParams(-1, dp(44)).apply { topMargin = dp(8) })
            }
        }
    }

    private fun openWeb(url: String) {
        currentScreen = "reader"
        webView?.destroy()
        root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(14), dp(16), dp(10))
            setBackgroundColor(bg)
        }
        root.addView(button("‹ BACK TO COMIC", false) { onBackPressed() },
            LinearLayout.LayoutParams(-1, dp(42)).apply { bottomMargin = dp(8) })
        val view = WebView(this)
        webView = view
        CookieManager.getInstance().setAcceptCookie(true)
        view.settings.javaScriptEnabled = true
        view.settings.domStorageEnabled = true
        view.settings.loadsImagesAutomatically = true
        view.settings.userAgentString = "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36"
        view.webChromeClient = WebChromeClient()
        view.webViewClient = WebViewClient()
        root.addView(view, LinearLayout.LayoutParams(-1, 0, 1f))
        setContentView(root)
        view.loadUrl(url)
    }

    private fun request(url: String): String {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 12000
            readTimeout = 18000
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 Chrome/124.0 Mobile Safari/537.36")
            setRequestProperty("Accept", "text/html,application/xhtml+xml")
            setRequestProperty("Accept-Language", "en-US,en;q=0.9")
        }
        return try {
            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            if (code !in 200..299) throw IllegalStateException("HTTP $code")
            stream.bufferedReader(Charsets.UTF_8).use { it.readText() }
        } finally { connection.disconnect() }
    }

    private fun loadCover(url: String, target: ImageView) {
        io.execute {
            val bitmap = runCatching {
                val connection = URL(url).openConnection()
                connection.connectTimeout = 8000
                connection.readTimeout = 10000
                connection.getInputStream().use { BitmapFactory.decodeStream(it) }
            }.getOrNull()
            runOnUiThread {
                if (!isFinishing && target.isAttachedToWindow && bitmap != null) target.setImageBitmap(bitmap)
            }
        }
    }

    private fun progressCard(message: String): View = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding(dp(14), dp(14), dp(14), dp(14))
        background = rounded(panel, 14)
        addView(ProgressBar(this@HentaiReadComicsActivity), LinearLayout.LayoutParams(dp(24), dp(24)))
        addView(text(message, 12, muted, false), LinearLayout.LayoutParams(-2, -2).apply { marginStart = dp(12) })
    }

    private fun info(message: String) = TextView(this).apply {
        text = message
        textSize = 12f
        setTextColor(muted)
        setPadding(dp(14), dp(14), dp(14), dp(14))
        background = rounded(panel, 14)
    }

    private fun text(value: String, size: Int, color: Int, bold: Boolean) = TextView(this).apply {
        text = value
        textSize = size.toFloat()
        setTextColor(color)
        if (bold) typeface = Typeface.DEFAULT_BOLD
    }

    private fun button(value: String, primary: Boolean = true, action: () -> Unit) = Button(this).apply {
        text = value
        textSize = 11f
        isAllCaps = false
        typeface = Typeface.DEFAULT_BOLD
        setTextColor(white)
        background = rounded(if (primary) accent else panel, 13)
        setOnClickListener { action() }
    }

    private fun rounded(color: Int, radius: Int) = GradientDrawable().apply {
        setColor(color)
        cornerRadius = dp(radius).toFloat()
        setStroke(dp(1), Color.argb(75, 235, 220, 255))
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()

    override fun onBackPressed() {
        when (currentScreen) {
            "reader" -> {
                webView?.destroy()
                webView = null
                if (selected != null) loadDetails(selected!!.url) else showHome()
            }
            "details" -> showHome()
            else -> super.onBackPressed()
        }
    }
}
