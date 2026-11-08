package com.xanime.app

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.concurrent.Executors
import org.jsoup.Jsoup

class MainActivity : Activity() {
    private val bgColor = Color.rgb(7, 6, 15)
    private val surface = Color.rgb(24, 19, 36)
    private val surface2 = Color.rgb(34, 26, 49)
    private val accent = Color.rgb(190, 75, 255)
    private val pink = Color.rgb(255, 76, 164)
    private val muted = Color.rgb(174, 163, 193)
    private val white = Color.WHITE
    private val worker = Executors.newFixedThreadPool(4)
    private val main = Handler(Looper.getMainLooper())

    private var query = ""
    private var page = 1
    private val maxSearchPages = 5
    private val seenResultUrls = mutableSetOf<String>()
    private val allSearchResults = mutableListOf<SearchResult>()
    private var busy = false
    private lateinit var root: LinearLayout
    private lateinit var content: LinearLayout
    private lateinit var scroll: ScrollView
    private lateinit var status: TextView
    private lateinit var progress: ProgressBar
    private lateinit var search: EditText
    private lateinit var pageText: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = bgColor
        window.navigationBarColor = bgColor
        window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LOW_PROFILE
        if (!getSharedPreferences("xanime", MODE_PRIVATE).getBoolean("adult_confirmed", false)) showAgeGate()
        else showHome()
    }

    override fun onDestroy() {
        worker.shutdownNow()
        super.onDestroy()
    }

    private fun baseLayout(): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(18), dp(20), dp(18), dp(14))
        setBackgroundColor(bgColor)
        layoutParams = ViewGroup.LayoutParams(-1, -1)
    }

    private fun showAgeGate() {
        val page = baseLayout().apply { gravity = Gravity.CENTER }
        page.addView(TextView(this).apply {
            text = "X"; textSize = 48f; typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER; setTextColor(white); background = rounded(accent, 28)
            layoutParams = LinearLayout.LayoutParams(dp(86), dp(86)).apply { bottomMargin = dp(22) }
        })
        page.addView(label("XANIME", 29, white, true))
        page.addView(label("ADULT CONTENT ONLY", 11, pink, true).apply {
            letterSpacing = .16f; setPadding(0, dp(8), 0, dp(22))
        })
        page.addView(label("You must be 18 or older to continue.", 16, white, true))
        page.addView(label("By continuing, you confirm that you are an adult and that viewing adult material is legal where you live.", 13, muted, false).apply {
            gravity = Gravity.CENTER; setPadding(dp(8), dp(12), dp(8), dp(24))
        })
        page.addView(actionButton("I AM 18 OR OLDER") {
            getSharedPreferences("xanime", MODE_PRIVATE).edit().putBoolean("adult_confirmed", true).apply()
            showHome()
        })
        page.addView(actionButton("EXIT", false) { finish() })
        setContentView(page)
    }

    private fun showHome() {
        root = baseLayout()
        val header = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL; orientation = LinearLayout.HORIZONTAL }
        header.addView(TextView(this).apply {
            text = "X"; textSize = 25f; typeface = Typeface.DEFAULT_BOLD; gravity = Gravity.CENTER
            setTextColor(white); background = rounded(accent, 16)
            layoutParams = LinearLayout.LayoutParams(dp(48), dp(48))
        })
        val titles = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(12), 0, 0, 0) }
        titles.addView(label("XANIME", 22, white, true))
        titles.addView(label("ADULT-ONLY LIBRARY", 10, muted, true))
        header.addView(titles, LinearLayout.LayoutParams(0, -2, 1f))
        root.addView(header)

        search = EditText(this).apply {
            hint = "Search titles..."; setHintTextColor(muted); setTextColor(white)
            textSize = 15f; isSingleLine = true; imeOptions = EditorInfo.IME_ACTION_SEARCH
            setPadding(dp(15), 0, dp(15), 0); background = rounded(surface, 16)
            setText(query)
        }
        root.addView(search, LinearLayout.LayoutParams(-1, dp(52)).apply { topMargin = dp(22) })
        root.addView(actionButton("SEARCH") { startSearch(true) }, LinearLayout.LayoutParams(-1, dp(46)).apply { topMargin = dp(9) })
        search.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) { startSearch(true); true } else false
        }

        status = label(if (query.isBlank()) "Search the catalogue" else "Results for: $query", 14, muted, true).apply {
            setPadding(0, dp(18), 0, dp(8))
        }
        root.addView(status)
        progress = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            visibility = View.GONE
            max = maxSearchPages
            progress = 0
            isIndeterminate = false
        }
        root.addView(progress, LinearLayout.LayoutParams(-1, dp(5)).apply { topMargin = dp(5) })
        scroll = ScrollView(this).apply {
            isFillViewport = true
            setOnScrollChangeListener { _, _, scrollY, _, _ ->
                val child = getChildAt(0)
                if (child != null && child.bottom - (scrollY + height) <= dp(320)) loadNextPage()
            }
        }
        content = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(0, dp(4), 0, dp(10)) }
        if (query.isBlank()) content.addView(infoCard("Search for an adult title to discover its available episodes."))
        scroll.addView(content)
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        pageText = label("AUTOMATICALLY LOADING UP TO 5 PAGES", 10, muted, true).apply {
            gravity = Gravity.CENTER
            setPadding(0, dp(10), 0, dp(4))
        }
        root.addView(pageText)
        setContentView(root)
        if (query.isNotBlank()) runSearch()
    }

    private fun startSearch(reset: Boolean) {
        val value = search.text.toString().trim()
        if (value.isBlank()) { status.text = "Enter a search term."; return }
        query = value
        if (reset) {
            page = 1
            seenResultUrls.clear()
            allSearchResults.clear()
        }
        runSearch()
    }

    private fun runSearch(append: Boolean = false) {
        if (busy || query.isBlank()) return
        busy = true
        val requestedPage = page
        status.text = if (append) "Loading more results..." else "Searching..."
        progress.visibility = View.VISIBLE
        progress.progress = requestedPage
        if (!append) content.removeAllViews()
        pageText.text = "LOADING PAGE $requestedPage OF $maxSearchPages"
        worker.execute {
            try {
                val encoded = URLEncoder.encode(query, "UTF-8")
                val address = "https://xanimeporn.com/?s=$encoded&paged=$requestedPage"
                val html = request(address)
                val parsed = SiteParser.parseSearch(Jsoup.parse(html, address))
                main.post {
                    busy = false
                    if (parsed.isEmpty()) {
                        progress.visibility = View.GONE
                        if (!append) {
                            status.text = "No safe results found"
                            content.addView(infoCard("No matching adult-only results were found. Try another search term."))
                            pageText.text = "NO RESULTS"
                        } else {
                            page = (requestedPage - 1).coerceAtLeast(1)
                            status.text = "${seenResultUrls.size} results for “$query”"
                            renderAnimeGroups()
                            pageText.text = "ALL AVAILABLE RESULTS LOADED • PAGE $page"
                        }
                    } else {
                        val fresh = parsed.filter { seenResultUrls.add(it.url) }
                        allSearchResults.addAll(fresh)
                        status.text = "Loading results for “$query” • ${seenResultUrls.size} found"
                        if (requestedPage < maxSearchPages) {
                            pageText.text = "LOADING ALL RESULTS • PAGE $requestedPage OF $maxSearchPages"
                            // Fetch every available page automatically; the user only scrolls the combined list.
                            scroll.post { loadNextPage() }
                        } else {
                            progress.visibility = View.GONE
                            status.text = "${seenResultUrls.size} results for “$query”"
                            renderAnimeGroups()
                            pageText.text = "ALL RESULTS LOADED • 5 PAGES"
                        }
                    }
                }
            } catch (error: Exception) {
                main.post {
                    busy = false
                    progress.visibility = View.GONE
                    if (append) {
                        page = (requestedPage - 1).coerceAtLeast(1)
                        renderAnimeGroups()
                    }
                    status.text = "Search unavailable"
                    pageText.text = if (append) "COULD NOT LOAD PAGE ${requestedPage} • PARTIAL RESULTS SHOWN" else "SEARCH FAILED"
                    if (!append) content.addView(infoCard("Could not load search results. Check your connection and try again."))
                }
            }
        }
    }

    private fun loadNextPageIfViewportIsShort() {
        if (busy || page >= maxSearchPages || !::scroll.isInitialized) return
        val child = scroll.getChildAt(0) ?: return
        if (child.height <= scroll.height + scroll.scrollY + dp(80)) loadNextPage()
    }

    private fun loadNextPage() {
        if (busy || page >= maxSearchPages || query.isBlank()) return
        page += 1
        runSearch(append = true)
    }

    private data class AnimeGroup(val name: String, val episodes: List<SearchResult>, val hasSubEng: Boolean)

    private fun animeName(title: String): String = title
        .replace(Regex("(?i)\\s*\\[\\s*sub\\s*[- ]?\\s*eng\\s*]"), "")
        .replace(Regex("(?i)\\s*episode\\s*#?\\s*\\d+.*$"), "")
        .replace(Regex("\\s*[-–—:]\\s*$"), "")
        .trim().ifBlank { title.trim() }

    private fun renderAnimeGroups() {
        content.removeAllViews()
        val groups = allSearchResults.groupBy {
            animeName(it.title).lowercase().replace(Regex("[^a-z0-9]+"), " ").trim()
        }.values.map { entries ->
            val sorted = entries.distinctBy { it.url }.sortedWith(
                compareBy<SearchResult> {
                    Regex("(?i)episode\\s*#?\\s*(\\d+)").find(it.title)
                        ?.groupValues?.getOrNull(1)?.toIntOrNull() ?: Int.MAX_VALUE
                }.thenBy { it.title }
            )
            AnimeGroup(
                animeName(sorted.first().title),
                sorted,
                sorted.any { Regex("(?i)\\[\\s*sub\\s*[- ]?\\s*eng\\s*]").containsMatchIn(it.title) }
            )
        }.sortedBy { it.name.lowercase() }

        if (groups.isEmpty()) {
            content.addView(infoCard("No matching results were found."))
            return
        }
        groups.forEach { group ->
            val card = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(15), dp(15), dp(15), dp(15))
                background = rounded(surface, 18)
                isClickable = true
                isFocusable = true
                setOnClickListener { showAnimeGroup(group) }
            }
            val details = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
            details.addView(label(group.name, 15, white, true).apply { maxLines = 2 })
            details.addView(label("${group.episodes.size} Episodes", 12, muted, false).apply {
                setPadding(0, dp(6), 0, 0)
            })
            card.addView(details, LinearLayout.LayoutParams(0, -2, 1f))
            if (group.hasSubEng) card.addView(label("SUB-ENG", 9, pink, true).apply {
                setPadding(dp(9), dp(6), dp(9), dp(6))
                background = rounded(surface2, 10)
            })
            content.addView(card, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(10) })
        }
    }

    private fun showAnimeGroup(group: AnimeGroup) {
        val pageView = baseLayout()
        pageView.addView(actionButton("‹ BACK TO SEARCH", false) { showHome() })
        pageView.addView(label(group.name, 21, white, true).apply { setPadding(0, dp(20), 0, dp(5)) })
        pageView.addView(label("${group.episodes.size} Episodes", 12, muted, false).apply {
            setPadding(0, 0, 0, dp(12))
        })
        val listScroll = ScrollView(this)
        val list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        group.episodes.forEach { item ->
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(10), dp(10), dp(10), dp(10))
                background = rounded(surface, 16)
                isClickable = true
                isFocusable = true
                setOnClickListener { openEpisode(item.url) }
            }
            val thumb = ImageView(this).apply {
                scaleType = ImageView.ScaleType.CENTER_CROP
                background = rounded(surface2, 10)
                layoutParams = LinearLayout.LayoutParams(dp(92), dp(64))
            }
            row.addView(thumb)
            val info = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(12), 0, 0, 0)
            }
            info.addView(label(item.title, 13, white, true).apply { maxLines = 2 })
            if (item.duration.isNotBlank()) info.addView(label(item.duration, 11, muted, false).apply {
                setPadding(0, dp(5), 0, 0)
            })
            row.addView(info, LinearLayout.LayoutParams(0, -2, 1f))
            list.addView(row, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(9) })
            if (item.thumbnail.isNotBlank()) loadImage(item.thumbnail, thumb)
        }
        listScroll.addView(list)
        pageView.addView(listScroll, LinearLayout.LayoutParams(-1, 0, 1f))
        setContentView(pageView)
    }

    private fun addResultCard(item: SearchResult) {
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(10), dp(10), dp(10), dp(10)); background = rounded(surface, 18)
            isClickable = true; isFocusable = true
            setOnClickListener { openEpisode(item.url) }
        }
        val image = ImageView(this).apply {
            scaleType = ImageView.ScaleType.CENTER_CROP
            background = rounded(surface2, 12)
            layoutParams = LinearLayout.LayoutParams(dp(112), dp(78))
        }
        card.addView(image)
        val details = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(12), 0, 0, 0) }
        details.addView(label(item.title, 14, white, true).apply { maxLines = 3 })
        if (item.duration.isNotBlank()) details.addView(label(item.duration, 11, muted, false).apply { setPadding(0, dp(6), 0, 0) })
        card.addView(details, LinearLayout.LayoutParams(0, -2, 1f))
        content.addView(card, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(10) })
        if (item.thumbnail.isNotBlank()) loadImage(item.thumbnail, image)
    }

    private fun openEpisode(url: String) {
        if (busy) return
        showLoadingDetail()
        worker.execute {
            try {
                val html = request(url)
                val detail = SiteParser.parseEpisode(Jsoup.parse(html, url), url)
                main.post { showEpisode(detail) }
            } catch (_: Exception) {
                main.post { showErrorDetail("Could not load this episode. Please try again.") }
            }
        }
    }

    private fun showLoadingDetail() {
        val page = baseLayout()
        page.addView(actionButton("‹ BACK", false) { showHome() })
        page.addView(label("Loading episode...", 16, white, true).apply { setPadding(0, dp(28), 0, 0) })
        setContentView(page)
    }

    private fun showEpisode(item: EpisodeDetails) {
        val page = baseLayout()
        page.addView(actionButton("‹ BACK TO SEARCH", false) { showHome() })
        page.addView(label(item.title, 21, white, true).apply { setPadding(0, dp(20), 0, dp(8)) })
        if (item.blockedReason != null) {
            page.addView(infoCard("This entry is hidden by the adult-safety filter."))
            setContentView(page)
            return
        }

        page.addView(label("${item.episodes.size} EPISODES", 11, pink, true).apply { setPadding(0, dp(10), 0, dp(12)) })
        val scroll = ScrollView(this)
        val body = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        item.episodes.forEach { ep ->
            val selected = ep.url == item.url
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(14), dp(13), dp(14), dp(13))
                background = rounded(if (selected) surface2 else surface, 14)
                isClickable = true; isFocusable = true
                setOnClickListener { if (!selected) openEpisode(ep.url) }
            }
            row.addView(label(ep.title, 14, if (selected) pink else white, selected), LinearLayout.LayoutParams(0, -2, 1f))
            row.addView(label(if (selected) "CURRENT" else "OPEN", 9, muted, true))
            body.addView(row, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(8) })
        }
        body.addView(label("DOWNLOAD LINKS", 11, pink, true).apply { setPadding(0, dp(18), 0, dp(10)) })
        if (item.download480 != null) {
            body.addView(actionButton("COPY 480p DOWNLOAD LINK") { copyLink(item.download480) },
                LinearLayout.LayoutParams(-1, dp(48)).apply { bottomMargin = dp(9) })
        } else {
            body.addView(infoCard("480p unavailable for this episode."))
        }
        item.qualities.forEach { (quality, url) ->
            if (quality != "480p") {
                val line = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
                line.addView(label(quality.uppercase(), 13, white, true), LinearLayout.LayoutParams(0, dp(44), 1f))
                line.addView(actionButton("COPY LINK", false) { copyLink(url) }, LinearLayout.LayoutParams(dp(120), dp(40)))
                body.addView(line, LinearLayout.LayoutParams(-1, -2))
            }
        }
        body.addView(actionButton("OPEN EPISODE PAGE", false) {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(item.url)))
        }, LinearLayout.LayoutParams(-1, dp(44)).apply { topMargin = dp(16) })
        scroll.addView(body)
        page.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        setContentView(page)
    }

    private fun showErrorDetail(message: String) {
        val page = baseLayout()
        page.addView(actionButton("‹ BACK", false) { showHome() })
        page.addView(infoCard(message))
        setContentView(page)
    }

    private fun copyLink(url: String) {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("480p download link", url))
        Toast.makeText(this, "✓ Link copied!", Toast.LENGTH_SHORT).show()
    }

    private fun request(address: String): String {
        val connection = (URL(address).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 15000
            readTimeout = 20000
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 Chrome/124.0 Mobile Safari/537.36")
            setRequestProperty("Accept", "text/html,application/xhtml+xml")
            setRequestProperty("Accept-Language", "en-US,en;q=0.9")
        }
        return try {
            val stream = if (connection.responseCode in 200..299) connection.inputStream else connection.errorStream
            stream.bufferedReader(Charsets.UTF_8).use { it.readText() }
        } finally { connection.disconnect() }
    }

    private fun loadImage(url: String, target: ImageView) {
        worker.execute {
            try {
                val connection = (URL(url).openConnection() as HttpURLConnection).apply {
                    connectTimeout = 9000; readTimeout = 9000
                    setRequestProperty("User-Agent", "Mozilla/5.0")
                }
                val bitmap = connection.inputStream.use { BitmapFactory.decodeStream(it) }
                connection.disconnect()
                if (bitmap != null) main.post { if (!isFinishing) target.setImageBitmap(bitmap) }
            } catch (_: Exception) { }
        }
    }

    private fun infoCard(message: String) = TextView(this).apply {
        text = message; textSize = 13f; setTextColor(muted)
        setPadding(dp(16), dp(18), dp(16), dp(18)); background = rounded(surface, 16)
    }

    private fun actionButton(text: String, primary: Boolean = true, action: () -> Unit) =
        Button(this).apply {
            this.text = text; textSize = 11f; isAllCaps = false; typeface = Typeface.DEFAULT_BOLD
            setTextColor(white); background = rounded(if (primary) accent else surface2, 14)
            setOnClickListener { action() }
        }

    private fun label(text: String, size: Int, color: Int, bold: Boolean) = TextView(this).apply {
        this.text = text; textSize = size.toFloat(); setTextColor(color)
        if (bold) typeface = Typeface.DEFAULT_BOLD
    }

    private fun rounded(color: Int, radius: Int) = GradientDrawable().apply {
        setColor(color); cornerRadius = dp(radius).toFloat()
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
}
