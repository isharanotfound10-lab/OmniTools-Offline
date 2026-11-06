package com.xanime.app

import android.app.Activity
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
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
import android.content.Intent
import android.net.Uri
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.concurrent.Executors
import org.jsoup.Jsoup

class MainActivity : Activity() {
    private val bg = Color.rgb(10, 7, 22)
    private val panel = Color.rgb(27, 18, 48)
    private val accent = Color.rgb(178, 91, 255)
    private val muted = Color.rgb(174, 162, 196)
    private val worker = Executors.newFixedThreadPool(3)
    private val main = Handler(Looper.getMainLooper())
    private var currentPage = 1
    private var currentQuery = ""
    private lateinit var results: LinearLayout
    private lateinit var status: TextView
    private lateinit var pageLabel: TextView
    private lateinit var previous: Button
    private lateinit var next: Button
    private lateinit var searchBox: EditText
    private lateinit var progress: ProgressBar

    data class SearchResult(val title: String, val link: String, val image: String)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = bg
        window.navigationBarColor = bg
        if (!getSharedPreferences("xanime", MODE_PRIVATE).getBoolean("adult_confirmed", false)) showAgeGate() else showHome()
    }

    override fun onDestroy() {
        super.onDestroy()
        worker.shutdownNow()
    }

    private fun baseLayout() = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        gravity = Gravity.CENTER_HORIZONTAL
        setPadding(dp(20), dp(24), dp(20), dp(18))
        setBackgroundColor(bg)
        layoutParams = ViewGroup.LayoutParams(-1, -1)
    }

    private fun showAgeGate() {
        val root = baseLayout().apply { gravity = Gravity.CENTER }
        root.addView(TextView(this).apply {
            text = "X"; textSize = 54f; typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.WHITE); gravity = Gravity.CENTER; background = rounded(accent, 28)
            layoutParams = LinearLayout.LayoutParams(dp(92), dp(92)).apply { bottomMargin = dp(24) }
        })
        root.addView(label("XANIME", 30, Color.WHITE, true))
        root.addView(label("ADULT CONTENT", 12, accent, true).apply {
            letterSpacing = 0.18f; setPadding(0, dp(8), 0, dp(24))
        })
        root.addView(label("You must be 18 years or older to continue.", 16, Color.WHITE, true))
        root.addView(label("By continuing, you confirm that you are an adult and that viewing adult material is legal where you live.", 13, muted, false).apply {
            gravity = Gravity.CENTER; setPadding(0, dp(12), 0, dp(28))
        })
        root.addView(actionButton("I AM 18 OR OLDER") {
            getSharedPreferences("xanime", MODE_PRIVATE).edit().putBoolean("adult_confirmed", true).apply()
            showHome()
        })
        root.addView(actionButton("EXIT", false) { finish() })
        setContentView(root)
    }

    private fun showHome() {
        val root = baseLayout().apply { gravity = Gravity.TOP }
        val header = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        header.addView(TextView(this).apply {
            text = "X"; textSize = 25f; typeface = Typeface.DEFAULT_BOLD; gravity = Gravity.CENTER
            setTextColor(Color.WHITE); background = rounded(accent, 16)
            layoutParams = LinearLayout.LayoutParams(dp(48), dp(48))
        })
        val titleBox = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(12), 0, 0, 0) }
        titleBox.addView(label("XANIME", 22, Color.WHITE, true))
        titleBox.addView(label("SEARCH XANIMEPORN.COM", 10, muted, true))
        header.addView(titleBox, LinearLayout.LayoutParams(0, -2, 1f))
        root.addView(header)

        searchBox = EditText(this).apply {
            hint = "Search anything..."; setHintTextColor(muted); setTextColor(Color.WHITE)
            textSize = 15f; isSingleLine = true; imeOptions = EditorInfo.IME_ACTION_SEARCH
            setPadding(dp(16), 0, dp(16), 0); background = rounded(panel, 16)
            setText(currentQuery)
        }
        root.addView(searchBox, LinearLayout.LayoutParams(-1, dp(54)).apply { topMargin = dp(26) })
        root.addView(actionButton("SEARCH") { startSearch(true) },
            LinearLayout.LayoutParams(-1, dp(48)).apply { topMargin = dp(10) })
        searchBox.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) { startSearch(true); true } else false
        }

        status = label(if (currentQuery.isBlank()) "Search XAnimePorn" else "Results for: $currentQuery", 15, muted, true).apply {
            setPadding(0, dp(20), 0, dp(10))
        }
        root.addView(status)
        progress = ProgressBar(this).apply { visibility = View.GONE }
        root.addView(progress, LinearLayout.LayoutParams(-2, dp(42)))
        val scroll = ScrollView(this)
        results = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(0, dp(4), 0, dp(12)) }
        if (currentQuery.isBlank()) {
            results.addView(label("Enter a keyword above to search the website.", 14, muted, false).apply {
                gravity = Gravity.CENTER; setPadding(dp(18), dp(32), dp(18), dp(32)); background = rounded(panel, 18)
            })
        }
        scroll.addView(results)
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        val pages = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        previous = actionButton("‹ PREVIOUS", false) { if (currentPage > 1) { currentPage--; runSearch() } }
        next = actionButton("NEXT ›", false) { currentPage++; runSearch() }
        previous.isEnabled = currentPage > 1
        pages.addView(previous, LinearLayout.LayoutParams(0, dp(44), 1f).apply { rightMargin = dp(8) })
        pageLabel = label("PAGE $currentPage", 12, muted, true).apply { gravity = Gravity.CENTER }
        pages.addView(pageLabel, LinearLayout.LayoutParams(dp(82), dp(44)))
        pages.addView(next, LinearLayout.LayoutParams(0, dp(44), 1f).apply { leftMargin = dp(8) })
        root.addView(pages)
        setContentView(root)
        if (currentQuery.isNotBlank()) runSearch()
    }

    private fun startSearch(reset: Boolean) {
        val q = searchBox.text.toString().trim()
        if (q.isBlank()) {
            status.text = "Type something to search."
            return
        }
        currentQuery = q
        if (reset) currentPage = 1
        runSearch()
    }

    private fun runSearch() {
        if (currentQuery.isBlank()) return
        status.text = "Searching xanimeporn.com..."
        progress.visibility = View.VISIBLE
        results.removeAllViews()
        previous.isEnabled = currentPage > 1
        pageLabel.text = "PAGE $currentPage"
        next.isEnabled = false
        worker.execute {
            try {
                val encoded = URLEncoder.encode(currentQuery, "UTF-8")
                val address = "https://xanimeporn.com/?s=$encoded&paged=$currentPage"
                val connection = (URL(address).openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    connectTimeout = 15000
                    readTimeout = 20000
                    setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 12) AppleWebKit/537.36 Chrome/120 Mobile Safari/537.36")
                    setRequestProperty("Accept", "text/html,application/xhtml+xml")
                }
                val html = connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
                connection.disconnect()
                val doc = Jsoup.parse(html, address)
                val parsed = parseResults(doc)
                main.post {
                    progress.visibility = View.GONE
                    if (parsed.isEmpty()) {
                        status.text = "No results found"
                        results.addView(label("No matching results were found. Try another keyword.", 14, muted, false).apply {
                            gravity = Gravity.CENTER; setPadding(dp(18), dp(30), dp(18), dp(30)); background = rounded(panel, 18)
                        })
                    } else {
                        status.text = "${parsed.size} results for: $currentQuery"
                        parsed.forEach { addResultCard(it) }
                        next.isEnabled = parsed.size >= 10
                    }
                }
            } catch (e: Exception) {
                main.post {
                    progress.visibility = View.GONE
                    status.text = "Search failed"
                    results.removeAllViews()
                    results.addView(label("Couldn't connect to xanimeporn.com. Check your internet connection and try again.\n\n${e.javaClass.simpleName}", 14, muted, false).apply {
                        gravity = Gravity.CENTER; setPadding(dp(18), dp(30), dp(18), dp(30)); background = rounded(panel, 18)
                    })
                }
            }
        }
    }

    private fun parseResults(doc: org.jsoup.nodes.Document): List<SearchResult> {
        val found = LinkedHashMap<String, SearchResult>()
        val blockedTerms = listOf(
            "shotacon", "lolicon", "loli", "shota", "underage", "child",
            "elementary school", "middle school", "high school"
        )
        val ignoredPaths = setOf(
            "tag", "tags", "category", "categories", "author", "page",
            "wp-content", "wp-admin", "search", "feed",
            "hentai-series", "hentai-list", "top-10", "concerned",
            "uncensored", "contact"
        )
        val ignoredTitles = setOf(
            "hentai series", "hentai list", "top 10", "concerned",
            "uncensored", "contact"
        )

        // Only accept links that look like actual content entries, not the
        // site's one-segment navigation/menu links.
        for (anchor in doc.select("a[href]")) {
            if (anchor.closest("header, nav, footer, .menu, .navigation, .site-header, .site-footer") != null) continue
            val uri = runCatching { Uri.parse(anchor.absUrl("href")) }.getOrNull() ?: continue
            val host = uri.host?.lowercase().orEmpty()
            if (host != "xanimeporn.com" && host != "www.xanimeporn.com") continue

            val segments = uri.path.orEmpty().trim('/').split('/').filter { it.isNotBlank() }
            if (segments.size != 1 || segments[0].lowercase() in ignoredPaths) continue
            if (uri.query != null || uri.fragment != null) continue

            val href = uri.toString().trimEnd('/') + "/"
            val title = sequenceOf(
                anchor.text().trim(),
                anchor.attr("title").trim(),
                anchor.attr("aria-label").trim()
            ).firstOrNull { it.length >= 3 }.orEmpty()
            if (title.isBlank()) continue
            if (title.lowercase().replace(Regex("[^a-z0-9]+"), " ").trim() in ignoredTitles) continue

            // The thumbnail and title can be separate sibling links. Walk up the
            // nearby card containers to locate the image instead of requiring it
            // to be nested inside the title link.
            var container: org.jsoup.nodes.Element? = anchor
            var imageUrl = ""
            repeat(6) {
                val current = container ?: return@repeat
                if (imageUrl.isBlank()) {
                    val img = current.selectFirst("img")
                    if (img != null) {
                        imageUrl = sequenceOf(
                            img.absUrl("data-src"),
                            img.absUrl("data-lazy-src"),
                            img.absUrl("data-original"),
                            img.absUrl("src"),
                            img.absUrl("data-thumb"),
                            img.absUrl("srcset").substringBefore(",").substringBefore(" ")
                        ).firstOrNull { it.startsWith("https://") || it.startsWith("http://") }.orEmpty()
                    }
                }
                container = current.parent()
            }

            val safetyText = (title + " " + href).lowercase()
            if (blockedTerms.any { safetyText.contains(it) }) continue

            val candidate = SearchResult(title, href, imageUrl)
            val existing = found[href]
            // Some cards contain two links to the same post; keep the more
            // descriptive title and whichever duplicate has a thumbnail.
            if (existing == null ||
                (candidate.title.length > existing.title.length) ||
                (existing.image.isBlank() && candidate.image.isNotBlank())
            ) {
                found[href] = candidate
            }
        }

        return found.values.take(30)
    }

    private fun addResultCard(item: SearchResult) {
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(10), dp(10), dp(10), dp(10)); background = rounded(panel, 18)
            isClickable = true; isFocusable = true
            setOnClickListener { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(item.link))) }
        }
        val image = ImageView(this).apply {
            scaleType = ImageView.ScaleType.CENTER_CROP
            background = rounded(Color.rgb(48, 35, 68), 12)
            layoutParams = LinearLayout.LayoutParams(dp(104), dp(76))
        }
        card.addView(image)
        val title = label(item.title, 14, Color.WHITE, true).apply {
            maxLines = 3; setPadding(dp(12), 0, dp(4), 0)
        }
        card.addView(title, LinearLayout.LayoutParams(0, -2, 1f))
        results.addView(card, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(10) })
        if (item.image.isNotBlank()) worker.execute {
            try {
                val c = (URL(item.image).openConnection() as HttpURLConnection).apply {
                    connectTimeout = 10000; readTimeout = 10000
                    setRequestProperty("User-Agent", "Mozilla/5.0")
                }
                val bitmap = c.inputStream.use { android.graphics.BitmapFactory.decodeStream(it) }
                c.disconnect()
                if (bitmap != null) main.post { if (!isFinishing) image.setImageBitmap(bitmap) }
            } catch (_: Exception) { }
        }
    }

    private fun actionButton(text: String, primary: Boolean = true, action: () -> Unit) =
        Button(this).apply {
            this.text = text; textSize = 12f; isAllCaps = false; typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.WHITE); background = rounded(if (primary) accent else panel, 14)
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
