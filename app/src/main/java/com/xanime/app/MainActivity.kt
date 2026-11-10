package com.xanime.app

import android.app.Activity
import android.app.AlertDialog
import android.widget.CheckBox
import android.graphics.Bitmap
import android.util.LruCache
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.RippleDrawable
import android.content.res.ColorStateList
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.text.Editable
import android.text.TextWatcher
import android.widget.Button
import android.widget.Spinner
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.HorizontalScrollView
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import android.animation.ValueAnimator
import java.lang.ref.WeakReference
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.concurrent.Executors
import java.util.concurrent.ExecutorCompletionService
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Semaphore
import java.util.concurrent.Callable
import org.jsoup.Jsoup

class MainActivity : Activity() {
    private val prefs get() = getSharedPreferences("xanime", MODE_PRIVATE)
    private val amoled get() = prefs.getBoolean("amoled_mode", false)
    private val bgColor: Int get() = if (amoled) Color.BLACK else Color.rgb(2, 6, 9)
    private val surface: Int get() = if (amoled) Color.rgb(8, 16, 20) else Color.rgb(5, 23, 32)
    private val surface2: Int get() = if (amoled) Color.rgb(13, 27, 33) else Color.rgb(7, 39, 51)
    private val accent: Int get() = when (prefs.getString("accent_color", "Purple")) {
        "Pink" -> Color.rgb(255, 76, 164)
        "Cyan" -> Color.rgb(0, 190, 210)
        "Blue" -> Color.rgb(75, 130, 255)
        "Green" -> Color.rgb(60, 190, 130)
        else -> Color.rgb(190, 75, 255)
    }
    private val pink = Color.rgb(255, 76, 164)
    private val muted = Color.rgb(174, 163, 193)
    private val white = Color.WHITE
    private val worker = Executors.newFixedThreadPool(2)
    private val detailWorker = Executors.newFixedThreadPool(4)
    private val imageWorker = Executors.newFixedThreadPool(3)
    private val pendingImages = ConcurrentHashMap<String, MutableList<WeakReference<ImageView>>>()
    private val detailCache = ConcurrentHashMap<String, Pair<EpisodeDetails?, List<String>>>()
    private val detailSlots = Semaphore(4)
    private val imageCache = object : LruCache<String, Bitmap>(16 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount
    }
    private val main = Handler(Looper.getMainLooper())
    private var ambientBackground: AmbientBackgroundDrawable? = null
    private val activeShimmers = mutableSetOf<ShimmerDrawable>()

    private var query = ""
    private var selectedCategory: Pair<String, String>? = null
    private var selectedSort = "latest"
    private var catalogMode = false
    private val categories = listOf(
        "Action" to "action", "Betrayal" to "betrayal", "Big Tits" to "big-tits",
        "Censored" to "censored", "Comedy" to "comedy", "Domination" to "domination",
        "Futanari" to "futanari", "Harem" to "harem", "Incest" to "incest",
        "Maid" to "maid", "Monsters" to "monsters", "Nurse" to "nurse",
        "Romance" to "romance", "Small Tits" to "small-tits", "Tentacles" to "tentacles",
        "Uncensored" to "uncensored", "Yaoi" to "yaoi", "Yuri" to "yuri", "School" to "school", "Shotacon" to "shotacon"
    )
    private var page = 1
    private val maxSearchPages = 10
    private val seenResultUrls = mutableSetOf<String>()
    private val allSearchResults = mutableListOf<SearchResult>()
    private var busy = false
    private var hasCachedSearch = false
    private var cachedAnimeGroups: List<AnimeGroup> = emptyList()
    private var searchScrollY = 0
    private var isDetailScreen = false
    private var showingFavorites = false
    private var showingHentaini = false
    private var hentainiQuery = ""
    private var hentainiGenre: String? = null
    private var hentainiOffset = 0
    private var hentainiTotal = 0
    private var hentainiRequestId = 0
    private var hentainiLoading = false
    private val hentainiResults = mutableListOf<HentainiSeries>()
    private var hentainiSelectedSeries: HentainiSeries? = null
    private var hentainiDetailRequestId = 0
    private val hentainiEpisodeCache = ConcurrentHashMap<String, HentainiEpisodePage>()
    private val expandedAnimeNames = mutableSetOf<String>()
    private val loadingExtraThumbnails = mutableSetOf<String>()
    private lateinit var root: LinearLayout
    private lateinit var content: LinearLayout
    private lateinit var scroll: ScrollView
    private lateinit var pageContent: LinearLayout
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

    override fun onResume() {
        super.onResume()
        if (prefs.getBoolean("ambient_background", true)) ambientBackground?.start()
        activeShimmers.forEach { it.start() }
    }

    override fun onPause() {
        ambientBackground?.stop()
        activeShimmers.forEach { it.stop() }
        super.onPause()
    }

    override fun onDestroy() {
        worker.shutdownNow()
        detailWorker.shutdownNow()
        imageWorker.shutdownNow()
        ambientBackground?.stop()
        activeShimmers.forEach { it.stop() }
        activeShimmers.clear()
        super.onDestroy()
    }

    private fun baseLayout(): LinearLayout {
        activeShimmers.forEach { it.stop() }
        activeShimmers.clear()
        ambientBackground?.stop()
        ambientBackground = AmbientBackgroundDrawable(bgColor, accent)
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(20), dp(18), dp(14))
            background = ambientBackground
            if (prefs.getBoolean("ambient_background", true)) ambientBackground?.start()
            layoutParams = ViewGroup.LayoutParams(-1, -1)
        }
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
        animateScreen(page)
    }

    private fun showHome() {
        window.statusBarColor = bgColor
        window.navigationBarColor = bgColor
        root = baseLayout()
        // One page-level scroll view keeps the search controls and every result
        // in the same scrollable area instead of trapping scrolling in results.
        scroll = ScrollView(this).apply { isFillViewport = false }
        pageContent = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        scroll.addView(pageContent, ViewGroup.LayoutParams(-1, -2))
        root.addView(scroll, LinearLayout.LayoutParams(-1, -1))
        val header = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL; orientation = LinearLayout.HORIZONTAL }
        val titles = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        titles.addView(label("XAnime", 25, white, true))
        titles.addView(label(":|  DISCOVER • WATCH • ENJOY", 9, muted, true).apply {
            letterSpacing = .12f
            setPadding(dp(2), dp(3), 0, 0)
        })
        header.addView(titles, LinearLayout.LayoutParams(0, -2, 1f))
        if (!showingHentaini) {
            header.addView(actionButton(if (showingFavorites) "‹ Home" else "♥ Favorites", false) {
                showingFavorites = !showingFavorites
                showHome()
            }, LinearLayout.LayoutParams(-2, dp(44)).apply { marginEnd = dp(7) })
        }
        header.addView(actionButton("⚙", false) { showSettings() },
            LinearLayout.LayoutParams(dp(44), dp(44)))
        pageContent.addView(header)

        val sourceTabs = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        sourceTabs.addView(actionButton("XAnime", !showingHentaini) {
            showingHentaini = false
            showHome()
        }, LinearLayout.LayoutParams(0, dp(40), 1f).apply { marginEnd = dp(8) })
        sourceTabs.addView(actionButton("Hentaini", showingHentaini) {
            showingHentaini = true
            showingFavorites = false
            showHome()
        }, LinearLayout.LayoutParams(0, dp(40), 1f))
        pageContent.addView(sourceTabs, LinearLayout.LayoutParams(-1, dp(42)).apply { topMargin = dp(12) })

        if (showingHentaini) {
            showHentainiHome()
            return
        }

        // Keep the search controls compact so results get most of the screen.
        search = EditText(this).apply {
            hint = "Search Hanime Titles..."; setHintTextColor(muted); setTextColor(white)
            textSize = 14f; isSingleLine = true; imeOptions = EditorInfo.IME_ACTION_SEARCH
            setPadding(dp(13), 0, dp(10), 0); background = ripple(glassDrawable(14))
            setText(query)
        }
        val searchRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        searchRow.addView(search, LinearLayout.LayoutParams(0, dp(44), 1f).apply {
            marginEnd = dp(7)
        })
        searchRow.addView(actionButton("SEARCH ↗") { startSearch(true) },
            LinearLayout.LayoutParams(dp(88), dp(44)))
        pageContent.addView(searchRow, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(7) })

        val filtersPanel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            visibility = View.GONE
        }
        if (!showingFavorites) {
            val categoryScroller = HorizontalScrollView(this).apply { isHorizontalScrollBarEnabled = false }
            val categoryRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            categoryRow.addView(actionButton("All", selectedCategory == null && catalogMode) {
                selectAllCategories()
            }, LinearLayout.LayoutParams(-2, dp(34)).apply { marginEnd = dp(6) })
            categories.forEach { (name, slug) ->
                val isSelected = selectedCategory?.second == slug
                categoryRow.addView(actionButton(name, isSelected) {
                    selectCategory(name, slug)
                }, LinearLayout.LayoutParams(-2, dp(34)).apply { marginEnd = dp(6) })
            }
            categoryScroller.addView(categoryRow)
            filtersPanel.addView(label("CATEGORIES", 9, muted, true).apply {
                setPadding(0, dp(5), 0, dp(4))
            })
            filtersPanel.addView(categoryScroller, LinearLayout.LayoutParams(-1, dp(37)))
            if (query.isBlank()) {
                val sortScroller = HorizontalScrollView(this).apply { isHorizontalScrollBarEnabled = false }
                val sortRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
                listOf(
                    "latest" to "Latest",
                    "views" to "Most Viewed",
                    "longest" to "Longest",
                    "rate" to "Top Rated",
                    "random" to "Random"
                ).forEach { (filter, title) ->
                    sortRow.addView(actionButton(title, selectedSort == filter) {
                        selectSort(filter)
                    }, LinearLayout.LayoutParams(-2, dp(34)).apply { marginEnd = dp(6) })
                }
                sortScroller.addView(sortRow)
                filtersPanel.addView(label("SORT BY", 9, muted, true).apply {
                    setPadding(0, dp(5), 0, dp(4))
                })
                filtersPanel.addView(sortScroller, LinearLayout.LayoutParams(-1, dp(37)))
            }
        }
        lateinit var filtersToggle: Button
        filtersToggle = actionButton(
            if (selectedCategory != null || catalogMode || selectedSort != "latest") "FILTERS • ACTIVE ▾" else "FILTERS ▾",
            false
        ) {
            val opening = filtersPanel.visibility != View.VISIBLE
            if (opening) {
                filtersPanel.visibility = View.VISIBLE
                animateReveal(filtersPanel)
            } else {
                filtersPanel.animate().alpha(0f).translationY(-dp(5).toFloat()).setDuration(130)
                    .withEndAction {
                        filtersPanel.visibility = View.GONE
                        filtersPanel.alpha = 1f
                        filtersPanel.translationY = 0f
                    }.start()
            }
            filtersToggle.text = if (opening) "HIDE FILTERS ▴" else if (selectedCategory != null || catalogMode || selectedSort != "latest") "FILTERS • ACTIVE ▾" else "FILTERS ▾"
        }
        pageContent.addView(filtersToggle, LinearLayout.LayoutParams(-1, dp(44)).apply { topMargin = dp(4) })
        pageContent.addView(filtersPanel, LinearLayout.LayoutParams(-1, -2))
        search.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) { startSearch(true); true } else false
        }

        status = label(if (query.isBlank()) "Search the catalogue" else "Results for: $query", 14, muted, true).apply {
            setPadding(0, dp(18), 0, dp(8))
        }
        pageContent.addView(status)
        progress = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            visibility = View.GONE
            max = maxSearchPages
            progress = 0
            isIndeterminate = false
        }
        pageContent.addView(progress, LinearLayout.LayoutParams(-1, dp(5)).apply { topMargin = dp(5) })
        content = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(0, dp(4), 0, dp(10)) }
        if (query.isBlank() && selectedCategory == null && !catalogMode) content.addView(infoCard("Search titles, choose a category, or select a sort filter to browse the full catalogue."))
        pageContent.addView(content, LinearLayout.LayoutParams(-1, -2))
        pageText = label("SEARCH RESULTS FROM PAGE 1 ONLY", 10, muted, true).apply {
            gravity = Gravity.CENTER
            setPadding(0, dp(10), 0, dp(4))
        }
        pageContent.addView(pageText)
        setContentView(root)
        animateScreen(root)
        scroll.setOnScrollChangeListener { _, _, scrollY, _, _ -> ambientBackground?.setScrollOffset(scrollY) }
        if (showingFavorites) {
            status.text = "Your saved series"
            val favorites = loadFavoriteGroups()
            renderAnimeGroups(favorites)
            pageText.text = "FAVORITES • ${favorites.size} SERIES"
        } else if (hasCachedSearch) {
            status.text = if (cachedAnimeGroups.isEmpty()) "No results for: $query" else
                "${cachedAnimeGroups.size} unique anime • cached results"
            renderAnimeGroups(cachedAnimeGroups)
            pageText.text = "PAGE $page COMPLETE • ${cachedAnimeGroups.size} UNIQUE ANIME"
            scroll.post { scroll.scrollTo(0, searchScrollY) }
        } else if (query.isNotBlank() || selectedCategory != null || catalogMode) {
            runSearch()
        }
        isDetailScreen = false
    }

    override fun onBackPressed() {
        if (showingHentaini && hentainiSelectedSeries != null) {
            hentainiDetailRequestId++
            hentainiSelectedSeries = null
            showHome()
        } else if (showingHentaini) {
            showingHentaini = false
            showHome()
        } else if (isDetailScreen && (hasCachedSearch || showingFavorites)) showHome() else super.onBackPressed()
    }


    private fun showHentainiHome() {
        pageContent.addView(label("HENTAINI", 22, white, true).apply {
            setPadding(0, dp(22), 0, dp(8))
        })
        pageContent.addView(label("LIVE SEARCH", 10, accent, true).apply {
            letterSpacing = .12f
            setPadding(0, 0, 0, dp(12))
        })

        val input = EditText(this).apply {
            hint = "Search Hentaini titles..."
            setHintTextColor(muted)
            setTextColor(white)
            textSize = 15f
            isSingleLine = true
            imeOptions = EditorInfo.IME_ACTION_SEARCH
            setPadding(dp(15), 0, dp(15), 0)
            background = ripple(glassDrawable(16))
            setText(hentainiQuery)
        }
        pageContent.addView(input, LinearLayout.LayoutParams(-1, dp(52)))

        val genreScroller = HorizontalScrollView(this).apply { isHorizontalScrollBarEnabled = false }
        val genreRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        listOf("Action" to "action","Comedy" to "comedy","Fantasy" to "fantasy","Ahegao" to "ahegao","Anal" to "anal","BBW" to "bbw","Big Breast" to "big breast","Blackmail" to "blackmail","Bondage" to "bondage","Chikan" to "chikan","Cosplay" to "cosplay","Futanari" to "futanari","Garyu" to "garyu","Harem" to "harem","Incest" to "incest","Maid" to "maid","Milf" to "milf","Netorare" to "netorare","Nurse" to "nurse","Orgy" to "orgy","Oyakodon" to "oyakodon","Paizuri" to "paizuri","Rape" to "rape","Scholar" to "scholar","Shotacon" to "shotacon","Small Breasts" to "small breasts","Succubus" to "succubus","Tentacles" to "tentacles",).forEach { (name, slug) ->
            genreRow.addView(actionButton(name, hentainiGenre == slug) {
                hentainiGenre = if (hentainiGenre == slug) null else slug
                hentainiQuery = ""
                showHome()
            }, LinearLayout.LayoutParams(-2, dp(38)).apply { marginEnd = dp(8) })
        }
        genreScroller.addView(genreRow)
        pageContent.addView(genreScroller, LinearLayout.LayoutParams(-1, dp(44)).apply { topMargin = dp(9) })

        val statusView = label(
            if (hentainiQuery.isBlank()) "Search Hentaini Titles" else "Results for: " + hentainiQuery,
            13, muted, true
        ).apply { setPadding(0, dp(14), 0, dp(8)) }
        pageContent.addView(statusView)

        val progressView = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            visibility = View.GONE
            isIndeterminate = true
        }
        pageContent.addView(progressView, LinearLayout.LayoutParams(-1, dp(4)))

        val results = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, dp(12), 0, dp(12))
        }
        pageContent.addView(results, LinearLayout.LayoutParams(-1, -2))

        val loadMore = actionButton("LOAD MORE RESULTS", false) { }
        loadMore.setOnClickListener {
            hentainiSearch(hentainiQuery, false, statusView, progressView, results, loadMore, hentainiGenre)
        }
        loadMore.visibility = View.GONE
        pageContent.addView(loadMore, LinearLayout.LayoutParams(-1, dp(44)).apply { topMargin = dp(6) })

        fun submitSearch() {
            hentainiQuery = input.text.toString().trim()
            hentainiGenre = null
            hentainiSearch(hentainiQuery, true, statusView, progressView, results, loadMore, null)
        }
        val debounce = Runnable { submitSearch() }
        input.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                main.removeCallbacks(debounce)
                main.postDelayed(debounce, 500)
            }
            override fun afterTextChanged(s: Editable?) = Unit
        })
        input.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                main.removeCallbacks(debounce)
                submitSearch()
                true
            } else false
        }
        if (hentainiGenre != null) {
            hentainiSearch("", true, statusView, progressView, results, loadMore, hentainiGenre)
        } else if (hentainiQuery.isNotBlank()) {
            hentainiSearch(hentainiQuery, true, statusView, progressView, results, loadMore, null)
        } else {
            results.addView(infoCard("Search titles or choose Ahegao or MILF to browse by genre."))
        }

        setContentView(root)
        animateScreen(root)
        isDetailScreen = false
    }

    private fun hentainiSearch(
        term: String,
        reset: Boolean,
        statusView: TextView,
        progressView: ProgressBar,
        output: LinearLayout,
        loadMoreButton: Button?,
        genre: String? = null
    ) {
        if (term.isBlank() && genre.isNullOrBlank()) {
            hentainiRequestId++
            hentainiLoading = false
            hentainiResults.clear()
            hentainiOffset = 0
            hentainiTotal = 0
            statusView.text = "Enter a title to search."
            output.removeAllViews()
            loadMoreButton?.visibility = View.GONE
            progressView.visibility = View.GONE
            return
        }
        if (hentainiLoading && !reset) return

        val requestId = ++hentainiRequestId
        if (reset) {
            hentainiOffset = 0
            hentainiTotal = 0
            hentainiResults.clear()
            output.removeAllViews()
        }
        hentainiLoading = true
        statusView.text = if (reset) {
            if (genre != null) "Loading " + genre.uppercase() + " titles..." else "Searching Hentaini..."
        } else "Loading more results..."
        progressView.visibility = View.VISIBLE
        loadMoreButton?.isEnabled = false

        val requestedOffset = hentainiOffset
        worker.execute {
            try {
                val page = if (!genre.isNullOrBlank()) HentainiApi.searchGenre(genre, requestedOffset, 10)
                    else HentainiApi.search(term, requestedOffset, 10)
                main.post {
                    if (isFinishing || requestId != hentainiRequestId || !showingHentaini) return@post
                    hentainiLoading = false
                    progressView.visibility = View.GONE
                    loadMoreButton?.isEnabled = true
                    hentainiResults.addAll(page.results)
                    hentainiOffset = page.offset + page.results.size
                    hentainiTotal = page.total
                    statusView.text = if (hentainiResults.isEmpty()) {
                        if (genre != null) "No titles found in " + genre.uppercase() else "No results for: " + term
                    } else {
                        hentainiResults.size.toString() + " of " + hentainiTotal + " results"
                    }
                    page.results.forEach { series -> addHentainiCard(series, output) }
                    loadMoreButton?.visibility =
                        if (hentainiOffset < hentainiTotal && page.results.isNotEmpty()) View.VISIBLE else View.GONE
                    if (page.results.isEmpty() && hentainiResults.isEmpty()) {
                        output.addView(infoCard("No matching series were found."))
                    }
                }
            } catch (error: Exception) {
                main.post {
                    if (isFinishing || requestId != hentainiRequestId || !showingHentaini) return@post
                    hentainiLoading = false
                    progressView.visibility = View.GONE
                    loadMoreButton?.isEnabled = true
                    statusView.text = "Hentaini search failed: " + (error.message ?: error.javaClass.simpleName)
                    if (hentainiResults.isEmpty()) {
                        output.removeAllViews()
                        output.addView(infoCard("Could not load live results. " + (error.message ?: "Please try again.")))
                    } else {
                        Toast.makeText(this, "Could not load more results.", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    private fun addHentainiCard(series: HentainiSeries, output: LinearLayout) {
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(11), dp(11), dp(13), dp(11))
            background = ripple(glassDrawable(16))
            isClickable = true
            isFocusable = true
            setOnClickListener {
                hentainiSelectedSeries = series
                showHentainiSeries(series)
            }
        }
        val thumbnail = ImageView(this).apply {
            scaleType = ImageView.ScaleType.CENTER_CROP
            background = rounded(surface2, 10)
            contentDescription = "Series thumbnail"
        }
        card.addView(thumbnail, LinearLayout.LayoutParams(dp(88), dp(118)))
        val details = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(13), 0, 0, 0)
        }
        details.addView(label(series.title, 14, white, true).apply { maxLines = 3 })
        val episodeCount = label(
            "Checking episodes...",
            12, muted, false
        ).apply { setPadding(0, dp(8), 0, 0) }
        episodeCount.tag = series.id
        details.addView(episodeCount)
        card.addView(details, LinearLayout.LayoutParams(0, -2, 1f))
        card.addView(label("OPEN  ›", 9, accent, true))
        output.addView(card, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(10) })
        animateEntrance(card, output.childCount - 1)
        addPressFeedback(card)
        if (series.thumbnailUrl.isNotBlank()) loadImage(series.thumbnailUrl, thumbnail)

        // Look up the real count for every series; hasEpisodes can be stale.
        detailWorker.execute {
            val count = HentainiApi.countEpisodes(series)
            main.post {
                if (isFinishing || episodeCount.tag != series.id) return@post
                episodeCount.text = when {
                    count != null -> count.toString() + " Episodes"
                    else -> "Episode count unavailable"
                }
            }
        }
    }


    private fun showHentainiSeries(series: HentainiSeries) {
        showingHentaini = true
        isDetailScreen = true
        val requestId = ++hentainiDetailRequestId
        val page = baseLayout()
        page.addView(actionButton("‹ BACK TO HENTAINI SEARCH", false) {
            hentainiDetailRequestId++
            hentainiSelectedSeries = null
            showHome()
        })
        page.addView(label(series.title, 20, white, true).apply {
            setPadding(0, dp(18), 0, dp(7))
        })
        val statusView = label("Loading episode list...", 12, muted, false).apply {
            setPadding(0, 0, 0, dp(12))
        }
        page.addView(statusView)
        val progressView = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            isIndeterminate = true
        }
        page.addView(progressView, LinearLayout.LayoutParams(-1, dp(4)))
        val scrollView = ScrollView(this)
        val list = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, dp(12), 0, dp(12))
        }
        scrollView.addView(list)
        page.addView(scrollView, LinearLayout.LayoutParams(-1, 0, 1f))
        setContentView(page)
        animateScreen(page)

        worker.execute {
            val episodes = runCatching { HentainiApi.loadEpisodes(series) }.getOrNull()
            main.post {
                if (isFinishing || requestId != hentainiDetailRequestId ||
                    hentainiSelectedSeries?.id != series.id) return@post
                progressView.visibility = View.GONE
                if (episodes.isNullOrEmpty()) {
                    statusView.text = "Could not find episodes"
                    list.addView(infoCard("No episode links were found on this series page."))
                    return@post
                }
                statusView.text = episodes.size.toString() + " EPISODES • CHECKING DOWNLOAD STREAMS"
                episodes.forEach { episode ->
                    addHentainiEpisodeCard(episode, series, list, requestId)
                }
            }
        }
    }

    private fun addHentainiEpisodeCard(
        episode: HentainiEpisode,
        series: HentainiSeries,
        output: LinearLayout,
        requestId: Int
    ) {
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(11), dp(11), dp(11), dp(11))
            background = glassDrawable(16)
        }
        val heading = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val thumbnail = ImageView(this).apply {
            scaleType = ImageView.ScaleType.CENTER_CROP
            background = rounded(surface2, 10)
            contentDescription = episode.title + " thumbnail"
        }
        heading.addView(thumbnail, LinearLayout.LayoutParams(dp(104), dp(72)))
        val titleBlock = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(12), 0, 0, 0)
        }
        val episodeTitle = label(
            episode.title.ifBlank { "Episode " + episode.number.toString() },
            14, white, true
        ).apply { maxLines = 2 }
        titleBlock.addView(episodeTitle)
        titleBlock.addView(label(
            "EPISODE " + episode.number.toString().padStart(2, '0'),
            10, muted, true
        ).apply { setPadding(0, dp(5), 0, 0) })
        heading.addView(titleBlock, LinearLayout.LayoutParams(0, -2, 1f))
        card.addView(heading)

        val streams = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, dp(10), 0, 0)
        }
        streams.addView(label("Finding available download streams...", 11, muted, false))
        card.addView(streams)
        output.addView(card, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(10) })
        animateEntrance(card, output.childCount - 1)

        // Wait for the episode page's own image. The thumbnail attached to
        // a series-list card may be the shared series/search image.

        detailWorker.execute {
            val details = hentainiEpisodeCache[episode.url] ?: runCatching {
                HentainiApi.loadEpisodePage(episode)
            }.getOrNull()?.also { hentainiEpisodeCache[episode.url] = it }
            main.post {
                if (isFinishing || requestId != hentainiDetailRequestId ||
                    hentainiSelectedSeries?.id != series.id) return@post
                streams.removeAllViews()
                val resolvedThumbnail = details?.thumbnailUrl.orEmpty()
                // Replace the temporary URL-derived label with the actual
                // episode title from its VideoObject/page heading when present.
                val resolvedTitle = details?.title.orEmpty().trim()
                if (resolvedTitle.isNotBlank() &&
                    !resolvedTitle.equals("Hentaini", true) &&
                    !resolvedTitle.equals("Episode", true)
                ) {
                    episodeTitle.text = resolvedTitle
                    thumbnail.contentDescription = resolvedTitle + " thumbnail"
                }
                // Always prefer the per-episode image resolved from this page.
                if (resolvedThumbnail.isNotBlank()) {
                    loadImage(resolvedThumbnail, thumbnail)
                }
                val available = details?.streams.orEmpty()
                if (available.isEmpty()) {
                    streams.addView(label("No download streams found for this episode.", 11, muted, false))
                    return@post
                }
                streams.addView(label(
                    available.size.toString() + " AVAILABLE DOWNLOAD STREAMS",
                    9, accent, true
                ).apply { setPadding(0, 0, 0, dp(8)) })
                val collapsedHosts = setOf(
                    "1fichier.com",
                    "burstcloud.co",
                    "mp4upload.com",
                    "streamwish.to",
                    "yourupload.com"
                )
                fun isCollapsedProvider(stream: HentainiStream): Boolean {
                    val host = Uri.parse(stream.url).host.orEmpty().lowercase()
                    return collapsedHosts.any { host == it || host.endsWith("." + it) }
                }
                fun addStreamButton(stream: HentainiStream, target: LinearLayout) {
                    target.addView(actionButton("COPY " + stream.host.uppercase(), false) {
                        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        clipboard.setPrimaryClip(ClipData.newPlainText(
                            stream.host + " download link", stream.url
                        ))
                        Toast.makeText(this, stream.host + " link copied!", Toast.LENGTH_SHORT).show()
                    }, LinearLayout.LayoutParams(-1, dp(42)).apply { bottomMargin = dp(7) })
                }

                val regularStreams = available.filterNot { isCollapsedProvider(it) }
                val moreStreams = available.filter { isCollapsedProvider(it) }
                regularStreams.forEach { addStreamButton(it, streams) }

                if (moreStreams.isNotEmpty()) {
                    val hiddenLinks = LinearLayout(this).apply {
                        orientation = LinearLayout.VERTICAL
                        visibility = View.GONE
                    }
                    moreStreams.forEach { addStreamButton(it, hiddenLinks) }

                    lateinit var moreButton: Button
                    moreButton = actionButton("MORE LINKS ⬇︎", false) {
                        val expanded = hiddenLinks.visibility != View.VISIBLE
                        hiddenLinks.visibility = if (expanded) View.VISIBLE else View.GONE
                        moreButton.text = if (expanded) "LESS LINKS ⬆︎" else "MORE LINKS ⬇︎"
                    }
                    streams.addView(moreButton, LinearLayout.LayoutParams(-1, dp(42)).apply {
                        topMargin = dp(2)
                        bottomMargin = dp(7)
                    })
                    streams.addView(hiddenLinks)
                }
            }
        }
    }

    private fun startSearch(reset: Boolean) {
        val value = search.text.toString().trim()
        if (value.isBlank()) { status.text = "Enter a search term or choose a category."; return }
        selectedCategory = null
        catalogMode = false
        showingFavorites = false
        query = value
        if (reset) {
            page = 1
            seenResultUrls.clear()
            allSearchResults.clear()
            hasCachedSearch = false
            cachedAnimeGroups = emptyList()
            expandedAnimeNames.clear()
            searchScrollY = 0
        }
        runSearch()
    }

    private fun selectAllCategories() {
        if (busy) return
        selectedCategory = null
        showingFavorites = false
        query = ""
        catalogMode = true
        search.setText("")
        resetCatalogResults()
        showHome()
    }

    private fun selectSort(filter: String) {
        if (busy) return
        selectedSort = filter
        showingFavorites = false
        query = ""
        catalogMode = true
        search.setText("")
        resetCatalogResults()
        showHome()
    }

    private fun resetCatalogResults() {
        page = 1
        seenResultUrls.clear()
        allSearchResults.clear()
        hasCachedSearch = false
        cachedAnimeGroups = emptyList()
        expandedAnimeNames.clear()
        searchScrollY = 0
    }

    private fun selectCategory(name: String, slug: String) {
        if (busy) return
        selectedCategory = name to slug
        showingFavorites = false
        catalogMode = true
        query = ""
        search.setText("")
        page = 1
        seenResultUrls.clear()
        allSearchResults.clear()
        hasCachedSearch = false
        cachedAnimeGroups = emptyList()
        expandedAnimeNames.clear()
        searchScrollY = 0
        showHome()
    }

    private fun runSearch(append: Boolean = false) {
        if (busy || (query.isBlank() && selectedCategory == null && !catalogMode)) return
        busy = true
        val requestedPage = page
        status.text = if (append) "Loading more results..." else "Searching..."
        progress.visibility = View.VISIBLE
        progress.progress = requestedPage
        if (!append) content.removeAllViews()
        pageText.text = "SEARCHING PAGE $requestedPage"
        worker.execute {
            try {
                val address = if (query.isBlank()) {
                    CatalogUrlBuilder.build(selectedCategory?.second, selectedSort, requestedPage)
                } else {
                    val encoded = URLEncoder.encode(query, "UTF-8")
                    "https://xanimeporn.com/?s=$encoded&paged=$requestedPage"
                }
                val html = request(address)
                val parsed = SiteParser.parseSearch(Jsoup.parse(html, address))
                val uniqueResults = LinkedHashMap<String, SearchResult>()
                parsed.forEach { result ->
                    val key = animeName(result.title).lowercase().replace(Regex("[^a-z0-9]+"), " ").trim()
                    if (key.isNotBlank() && !uniqueResults.containsKey(key)) uniqueResults[key] = result
                }
                val existingNames = if (append) cachedAnimeGroups.map {
                    it.name.lowercase().replace(Regex("[^a-z0-9]+"), " ").trim()
                }.toSet() else emptySet()
                val newUniqueResults = uniqueResults.filterKeys { it !in existingNames }
                val resultList = newUniqueResults.values.toList()
                val placeholders = resultList.map { result ->
                    AnimeGroup(
                        animeName(result.title), result, null, emptyList(),
                        Regex("(?i)\\\\[\\\\s*sub\\\\s*[- ]?\\\\s*eng\\\\s*]")
                            .containsMatchIn(result.title)
                    )
                }
                main.post {
                    progress.max = resultList.size.coerceAtLeast(1)
                    progress.progress = 0
                    if (append) cachedAnimeGroups = cachedAnimeGroups + placeholders
                    else cachedAnimeGroups = placeholders
                    hasCachedSearch = true
                    status.text = "Found ${parsed.size} results • loading ${resultList.size} anime"
                    pageText.text = "LOADING DETAILS • PAGE $requestedPage"
                    renderAnimeGroups(cachedAnimeGroups)
                }

                if (resultList.isNotEmpty()) {
                    val completion = ExecutorCompletionService<AnimeGroup>(detailWorker)
                    resultList.forEach { result ->
                        completion.submit(Callable {
                            val cached = if (getSharedPreferences("xanime", MODE_PRIVATE).getBoolean("cache_enabled", true)) detailCache[result.url] else null
                            val data = if (cached != null) cached else {
                                val permits = if (getSharedPreferences("xanime", MODE_PRIVATE).getBoolean("fast_search", true)) 1 else 2
                                detailSlots.acquire(permits)
                                try {
                                    runCatching {
                                        val detailHtml = request(result.url)
                                        val detailDoc = Jsoup.parse(detailHtml, result.url)
                                        val episodeDetails = SiteParser.parseEpisode(detailDoc, result.url)
                                        episodeDetails to extractEpisodeThumbnails(detailDoc, episodeDetails.episodes, result)
                                    }.getOrElse { null to emptyList() }
                                } finally {
                                    detailSlots.release(permits)
                                }.also { if (getSharedPreferences("xanime", MODE_PRIVATE).getBoolean("cache_enabled", true)) detailCache[result.url] = it }
                            }
                            AnimeGroup(
                                animeName(result.title), result, data.first, data.second,
                                Regex("(?i)\\\\[\\\\s*sub\\\\s*[- ]?\\\\s*eng\\\\s*]")
                                    .containsMatchIn(result.title)
                            )
                        })
                    }
                    resultList.indices.forEach { index ->
                        val completed = runCatching { completion.take().get() }.getOrNull()
                        if (completed != null) {
                            main.post {
                                cachedAnimeGroups = cachedAnimeGroups.map {
                                    if (it.firstResult.url == completed.firstResult.url) completed else it
                                }
                                progress.progress = index + 1
                                status.text = "Loaded anime details ${index + 1} of ${resultList.size}"
                                renderAnimeGroups(cachedAnimeGroups)
                            }
                        }
                    }
                }
                main.post {
                    busy = false
                    progress.visibility = View.GONE
                    if (parsed.isEmpty() && !append) {
                        hasCachedSearch = true
                        cachedAnimeGroups = emptyList()
                        status.text = "No results found"
                        activeShimmers.forEach { it.stop() }
        activeShimmers.clear()
        content.removeAllViews()
                        content.addView(infoCard("No matching adult-only results were found. Try another search term."))
                    } else {
                        if (append) allSearchResults.addAll(parsed)
                        else {
                            allSearchResults.clear()
                            allSearchResults.addAll(parsed)
                        }
                        hasCachedSearch = true
                        status.text = "${allSearchResults.size} search results • ${cachedAnimeGroups.size} unique anime"
                        renderAnimeGroups(cachedAnimeGroups)
                    }
                    pageText.text = if (page >= maxSearchPages) {
                        "PAGE $page COMPLETE • END OF AVAILABLE PAGES"
                    } else {
                        "PAGE $page COMPLETE • ${cachedAnimeGroups.size} UNIQUE ANIME"
                    }
                }
            } catch (error: Exception) {
                main.post {
                    busy = false
                    progress.visibility = View.GONE
                    if (append) page = (requestedPage - 1).coerceAtLeast(1)
                    status.text = "Search unavailable"
                    pageText.text = if (append) "COULD NOT LOAD PAGE ${requestedPage} • PREVIOUS RESULTS KEPT" else "SEARCH FAILED"
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
        if (busy || page >= maxSearchPages || (query.isBlank() && selectedCategory == null && !catalogMode)) return
        page += 1
        runSearch(append = true)
    }

    private fun renderNextPageButton() {
        if (page >= maxSearchPages) return
        content.addView(actionButton("NEXT PAGE  →") {
            loadNextPage()
        }, LinearLayout.LayoutParams(-1, dp(46)).apply {
            topMargin = dp(8)
            bottomMargin = dp(12)
        })
    }

    private data class AnimeGroup(val name: String, val firstResult: SearchResult, val details: EpisodeDetails?, val thumbnails: List<String>, val hasSubEng: Boolean)

    private fun animeName(title: String): String = title
        .replace(Regex("(?i)\\s*\\[\\s*sub\\s*[- ]?\\s*eng\\s*]"), "")
        .replace(Regex("(?i)\\s*episode\\s*#?\\s*\\d+.*$"), "")
        .replace(Regex("\\s*[-–—:]\\s*$"), "")
        .trim().ifBlank { title.trim() }

    private fun renderAnimeGroups(groups: List<AnimeGroup>) {
        val previousScrollY = if (::scroll.isInitialized) scroll.scrollY else searchScrollY
        activeShimmers.forEach { it.stop() }
        activeShimmers.clear()
        content.removeAllViews()
        if (groups.isEmpty()) {
            content.addView(infoCard(if (showingFavorites)
                "No favorites yet. Tap the heart on any series to save it here."
            else "No matching results were found."))
            return
        }
        groups.forEach { group ->
            val groupKey = group.name.lowercase()
            val expanded = groupKey in expandedAnimeNames
            val card = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(13), dp(13), dp(13), dp(13))
                background = ripple(if (prefs.getString("card_style", "Glass") == "Glass") glassDrawable(cardRadius()) else rounded(cardSurface(), cardRadius()))
                isClickable = true
                isFocusable = true
                setOnClickListener {
                    searchScrollY = scroll.scrollY
                    val episodeDetails = group.details
                    if (episodeDetails != null) showEpisode(episodeDetails) else openEpisode(group.firstResult.url)
                }
            }
            val heading = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }
            heading.addView(label(group.name, 15, white, true).apply { maxLines = 2 },
                LinearLayout.LayoutParams(0, -2, 1f))
            if (group.hasSubEng) heading.addView(label("SUB-ENG", 9, pink, true).apply {
                setPadding(dp(9), dp(6), dp(9), dp(6))
                background = rounded(surface2, 10)
            })
            heading.addView(actionButton(if (isFavorite(group)) "♥" else "♡", false) {
                toggleFavorite(group)
            }, LinearLayout.LayoutParams(dp(42), dp(36)).apply { marginStart = dp(8) })
            card.addView(heading)

            val episodes = group.details?.episodes.orEmpty()
            if (prefs.getString("layout_mode", "Gallery") == "Compact") {
                val previewRow = LinearLayout(this).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    setPadding(0, dp(10), 0, 0)
                }
                val preview = ImageView(this).apply {
                    scaleType = ImageView.ScaleType.CENTER_CROP
                    background = rounded(surface2, 10)
                    layoutParams = LinearLayout.LayoutParams(dp(104), dp(72))
                }
                previewRow.addView(preview)
                val previewUrl = group.thumbnails.firstOrNull()?.takeIf { it.isNotBlank() }
                    ?: group.firstResult.thumbnail
                if (previewUrl.isNotBlank()) loadImage(previewUrl, preview)
                val previewInfo = LinearLayout(this).apply {
                    orientation = LinearLayout.VERTICAL
                    setPadding(dp(12), 0, 0, 0)
                }
                previewInfo.addView(label(
                    if (episodes.isNotEmpty()) "${episodes.size} Episodes" else "Episode count loading",
                    12, white, true
                ))
                previewInfo.addView(label(
                    if (group.hasSubEng) "SUB-ENG" else "Anime episode list",
                    10, muted, false
                ).apply { setPadding(0, dp(5), 0, 0) })
                previewRow.addView(previewInfo, LinearLayout.LayoutParams(0, -2, 1f))
                card.addView(previewRow)
            } else {
                val expanded = group.name.lowercase() in expandedAnimeNames
                val visibleEpisodes = if (expanded) episodes else episodes.take(4)
                val gallery = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
                visibleEpisodes.chunked(4).forEachIndexed { rowIndex, rowEpisodes ->
                    val row = LinearLayout(this).apply {
                        orientation = LinearLayout.HORIZONTAL
                        gravity = Gravity.CENTER_VERTICAL
                    }
                    rowEpisodes.forEachIndexed { columnIndex, episode ->
                        val episodeIndex = rowIndex * 4 + columnIndex
                        val tile = LinearLayout(this).apply {
                            orientation = LinearLayout.VERTICAL
                            gravity = Gravity.CENTER
                            background = rounded(surface2, 10)
                            clipToOutline = true
                        }
                        val image = ImageView(this).apply {
                            scaleType = ImageView.ScaleType.CENTER_CROP
                            background = rounded(surface2, 10)
                            layoutParams = LinearLayout.LayoutParams(-1, dp(72))
                        }
                        tile.addView(image)
                        val number = episode.number.takeIf { it > 0 }?.let { "EP $it" } ?: "EP " + (episodeIndex + 1)
                        tile.addView(label(number, 9, muted, true).apply {
                            gravity = Gravity.CENTER
                            setPadding(0, dp(5), 0, dp(5))
                        })
                        row.addView(tile, LinearLayout.LayoutParams(0, -2, 1f).apply {
                            if (columnIndex > 0) marginStart = dp(7)
                        })
                        group.thumbnails.getOrNull(episodeIndex)?.takeIf { it.isNotBlank() }?.let {
                            loadImage(it, image)
                        }
                    }
                    repeat(4 - rowEpisodes.size) {
                        row.addView(View(this), LinearLayout.LayoutParams(0, dp(1), 1f).apply {
                            if (row.childCount > 0) marginStart = dp(7)
                        })
                    }
                    gallery.addView(row, LinearLayout.LayoutParams(-1, -2).apply {
                        if (rowIndex > 0) topMargin = dp(8)
                    })
                }
                if (episodes.isEmpty()) {
                    val image = ImageView(this).apply {
                        scaleType = ImageView.ScaleType.CENTER_CROP
                        background = rounded(surface2, 10)
                        layoutParams = LinearLayout.LayoutParams(-1, dp(72))
                    }
                    gallery.addView(image)
                    if (group.firstResult.thumbnail.isNotBlank()) loadImage(group.firstResult.thumbnail, image)
                }
                card.addView(gallery, LinearLayout.LayoutParams(-1, -2).apply {
                    topMargin = dp(11)
                })
            }

            val footer = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }
            val count = episodes.size
            footer.addView(label(
                if (count > 0) "$count Episodes" else "Episode count unavailable",
                12, muted, false
            ), LinearLayout.LayoutParams(0, -2, 1f))
            if (count > 4) {
                val more = label(
                    if (expanded) "SHOW LESS  ↑" else "+${count - 4} MORE  ↓",
                    10, accent, true
                ).apply {
                    setPadding(dp(10), dp(7), dp(10), dp(7))
                    background = ripple(glassDrawable(10))
                    isClickable = true
                    isFocusable = true
                    setOnClickListener {
                        if (expanded) {
                            expandedAnimeNames.remove(groupKey)
                            renderAnimeGroups(cachedAnimeGroups)
                        } else {
                            expandedAnimeNames.add(groupKey)
                            renderAnimeGroups(cachedAnimeGroups)
                            fetchRemainingThumbnails(group)
                        }
                    }
                }
                footer.addView(more)
            }
            card.addView(footer, LinearLayout.LayoutParams(-1, -2).apply {
                topMargin = dp(10)
            })
            content.addView(card, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(11) })
            animateEntrance(card, content.childCount - 1)
        }
        if (!showingFavorites) renderNextPageButton()
        scroll.post { scroll.scrollTo(0, previousScrollY) }
    }


    private fun favoriteEntries(): Set<String> =
        prefs.getStringSet("favorite_series", emptySet())?.toSet() ?: emptySet()

    private fun isFavorite(group: AnimeGroup): Boolean =
        favoriteEntries().any { encoded ->
            runCatching { org.json.JSONObject(encoded).optString("url") == group.firstResult.url }.getOrDefault(false)
        }

    private fun toggleFavorite(group: AnimeGroup) {
        val entries = favoriteEntries().toMutableSet()
        val existing = entries.firstOrNull { encoded ->
            runCatching { org.json.JSONObject(encoded).optString("url") == group.firstResult.url }.getOrDefault(false)
        }
        if (existing != null) {
            entries.remove(existing)
            Toast.makeText(this, "Removed from Favorites", Toast.LENGTH_SHORT).show()
        } else {
            val item = org.json.JSONObject()
                .put("title", group.name)
                .put("url", group.firstResult.url)
                .put("thumbnail", group.firstResult.thumbnail)
                .put("subEng", group.hasSubEng)
            entries.add(item.toString())
            Toast.makeText(this, "Added to Favorites", Toast.LENGTH_SHORT).show()
        }
        prefs.edit().putStringSet("favorite_series", entries).apply()
        if (showingFavorites) renderAnimeGroups(loadFavoriteGroups())
        else renderAnimeGroups(cachedAnimeGroups)
    }

    private fun loadFavoriteGroups(): List<AnimeGroup> =
        favoriteEntries().mapNotNull { encoded ->
            runCatching {
                val item = org.json.JSONObject(encoded)
                val title = item.optString("title").takeIf { it.isNotBlank() } ?: return@runCatching null
                val url = item.optString("url").takeIf { it.isNotBlank() } ?: return@runCatching null
                val thumbnail = item.optString("thumbnail")
                val result = SearchResult(title, url, thumbnail, "")
                AnimeGroup(title, result, null, emptyList(), item.optBoolean("subEng"))
            }.getOrNull()
        }.sortedBy { it.name.lowercase() }

    private fun fetchRemainingThumbnails(group: AnimeGroup) {
        val episodes = group.details?.episodes.orEmpty()
        val key = group.name.lowercase()
        if (episodes.size <= 4 || !loadingExtraThumbnails.add(key)) return
        worker.execute {
            val thumbnails = group.thumbnails.toMutableList()
            while (thumbnails.size < episodes.size) thumbnails.add("")
            for (index in 4 until episodes.size) {
                if (thumbnails[index].isNotBlank()) continue
                val thumbnail = runCatching {
                    val episodeHtml = request(episodes[index].url)
                    extractEpisodePageThumbnail(Jsoup.parse(episodeHtml, episodes[index].url))
                }.getOrNull().orEmpty()
                if (thumbnail.isNotBlank()) thumbnails[index] = thumbnail
            }
            main.post {
                loadingExtraThumbnails.remove(key)
                cachedAnimeGroups = cachedAnimeGroups.map {
                    if (it.name.lowercase() == key) it.copy(thumbnails = thumbnails) else it
                }
                if (expandedAnimeNames.contains(key)) {
                    val y = scroll.scrollY
                    renderAnimeGroups(cachedAnimeGroups)
                    scroll.post { scroll.scrollTo(0, y) }
                }
            }
        }
    }

    private fun extractEpisodeThumbnails(
        doc: org.jsoup.nodes.Document,
        episodes: List<EpisodeLink>,
        firstResult: SearchResult
    ): List<String> {
        val byUrl = LinkedHashMap<String, String>()

        // Some themes put thumbnails directly beside the episode links.
        doc.select(".su-tabs-pane[data-title=Episodes] a[href]").forEach { anchor ->
            val image = anchor.selectFirst("img") ?: anchor.parent()?.selectFirst("img")
            val thumbnail = image?.let { imageUrl(it) }.orEmpty()
            if (thumbnail.isNotBlank()) {
                byUrl[anchor.absUrl("href").trimEnd('/')] = thumbnail
            }
        }

        // The episode list often contains text-only links. In that case each
        // episode page has its own featured/preview image, so read those pages.
        episodes.take(4).forEach { episode ->
            val key = episode.url.trimEnd('/')
            if (byUrl[key].isNullOrBlank()) {
                val thumbnail = if (key == firstResult.url.trimEnd('/')) {
                    extractEpisodePageThumbnail(doc)
                } else {
                    runCatching {
                        val episodeHtml = request(episode.url)
                        extractEpisodePageThumbnail(Jsoup.parse(episodeHtml, episode.url))
                    }.getOrNull().orEmpty()
                }
                if (thumbnail.isNotBlank()) byUrl[key] = thumbnail
            }
        }

        if (firstResult.thumbnail.isNotBlank()) {
            byUrl.putIfAbsent(firstResult.url.trimEnd('/'), firstResult.thumbnail)
        }
        return episodes.take(4).map { byUrl[it.url.trimEnd('/')].orEmpty() }
    }

    private fun extractEpisodePageThumbnail(doc: org.jsoup.nodes.Document): String {
        val candidates = sequenceOf(
            doc.selectFirst("meta[property=og:image]")?.absUrl("content"),
            doc.selectFirst("meta[name=twitter:image]")?.absUrl("content"),
            doc.selectFirst("video[poster]")?.absUrl("poster"),
            doc.selectFirst(".post-thumbnail img")?.let { imageUrl(it) },
            doc.selectFirst(".wp-post-image")?.let { imageUrl(it) },
            doc.selectFirst(".entry-content img")?.let { imageUrl(it) },
            doc.selectFirst("article img")?.let { imageUrl(it) }
        )
        return candidates.filterNotNull().firstOrNull {
            (it.startsWith("https://") || it.startsWith("http://")) &&
                !it.contains("logo", ignoreCase = true) &&
                !it.contains("avatar", ignoreCase = true) &&
                !it.contains("icon", ignoreCase = true)
        }.orEmpty()
    }

    private fun imageUrl(image: org.jsoup.nodes.Element): String {
        return sequenceOf("data-lazy-src", "data-src", "data-original", "src", "data-thumb")
            .map { key -> image.absUrl(key) }
            .firstOrNull { it.startsWith("https://") || it.startsWith("http://") }
            .orEmpty()
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
        animateEntrance(card, content.childCount - 1)
        addPressFeedback(card)
        if (item.thumbnail.isNotBlank()) loadImage(item.thumbnail, image)
    }

    private fun openEpisode(url: String) {
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
        isDetailScreen = true
        val page = baseLayout()
        page.addView(actionButton("‹ BACK", false) { showHome() })
        page.addView(label("Loading episode...", 16, white, true).apply { setPadding(0, dp(28), 0, 0) })
        setContentView(page)
        animateScreen(page)
    }

    private fun showEpisode(item: EpisodeDetails) {
        isDetailScreen = true
        val page = baseLayout()
        page.addView(actionButton("‹ BACK TO SEARCH", false) { showHome() })
        page.addView(label(item.title, 21, white, true).apply { setPadding(0, dp(20), 0, dp(8)) })
        if (item.blockedReason != null) {
            page.addView(infoCard("This entry is hidden by the adult-safety filter."))
            setContentView(page)
        animateScreen(page)
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
                background = ripple(glassDrawable(14))
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
        animateScreen(page)
    }

    private fun showErrorDetail(message: String) {
        isDetailScreen = true
        val page = baseLayout()
        page.addView(actionButton("‹ BACK", false) { showHome() })
        page.addView(infoCard(message))
        setContentView(page)
        animateScreen(page)
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
        target.tag = url
        target.contentDescription = "Loading thumbnail"
        val cacheEnabled = getSharedPreferences("xanime", MODE_PRIVATE).getBoolean("cache_enabled", true)
        if (cacheEnabled) imageCache.get(url)?.let { bitmap ->
            target.animate().cancel()
            stopThumbnailShimmer(target)
            target.setImageBitmap(bitmap)
            target.contentDescription = "Episode thumbnail"
            target.setOnClickListener(null)
            return
        }

        // Keep one request per URL even when search cards are rebuilt while
        // detail pages finish. Every currently visible ImageView gets the
        // bitmap as soon as this individual download completes.
        val watchers = pendingImages[url]
        if (watchers != null) {
            synchronized(watchers) { watchers.add(WeakReference(target)) }
            startThumbnailPulse(target)
            return
        }

        val newWatchers = mutableListOf(WeakReference(target))
        val existing = pendingImages.putIfAbsent(url, newWatchers)
        if (existing != null) {
            synchronized(existing) { existing.add(WeakReference(target)) }
            startThumbnailPulse(target)
            return
        }
        startThumbnailPulse(target)

        imageWorker.execute {
            var bitmap: Bitmap? = null
            try {
                val connection = (URL(url).openConnection() as HttpURLConnection).apply {
                    connectTimeout = 7000
                    readTimeout = 7000
                    setRequestProperty("User-Agent", "Mozilla/5.0")
                    setRequestProperty("Accept", "image/avif,image/webp,image/apng,image/*,*/*;q=0.8")
                }
                bitmap = try {
                    connection.inputStream.use { stream ->
                        val options = BitmapFactory.Options().apply {
                            inSampleSize = if (getSharedPreferences("xanime", MODE_PRIVATE)
                                    .getBoolean("high_quality_thumbnails", false)) 1 else 2
                        }
                        BitmapFactory.decodeStream(stream, null, options)
                    }
                } finally {
                    connection.disconnect()
                }
                if (bitmap != null && getSharedPreferences("xanime", MODE_PRIVATE)
                        .getBoolean("cache_enabled", true)) {
                    imageCache.put(url, bitmap)
                }
            } catch (_: Exception) { }

            val completedBitmap = bitmap
            val registered = pendingImages.remove(url).orEmpty()
            main.post {
                registered.forEach { reference ->
                    val view = reference.get() ?: return@forEach
                    if (isFinishing || view.tag != url) return@forEach
                    view.animate().cancel()
                    stopThumbnailShimmer(view)
                    if (completedBitmap != null) {
                        view.alpha = 0f
                        view.setImageBitmap(completedBitmap)
                        if (animationsEnabled()) {
                            view.animate().alpha(1f).setDuration(220)
                                .setInterpolator(android.view.animation.DecelerateInterpolator()).start()
                        } else view.alpha = 1f
                        view.contentDescription = "Episode thumbnail"
                        view.setOnClickListener(null)
                    } else {
                        view.setImageResource(android.R.drawable.ic_menu_rotate)
                        view.contentDescription = "Thumbnail failed. Tap to retry."
                        view.setOnClickListener {
                            view.setImageDrawable(null)
                            loadImage(url, view)
                        }
                    }
                }
            }
        }
    }

    private fun startThumbnailPulse(view: ImageView) {
        view.setImageDrawable(null)
        if (prefs.getBoolean("shimmer_loading", true)) {
            (view.background as? ShimmerDrawable)?.stop()
            val shimmer = ShimmerDrawable(surface2, accent)
            activeShimmers.add(shimmer)
            view.background = shimmer
            shimmer.start()
        } else {
            view.background = rounded(surface2, 10)
            view.alpha = 0.62f
        }
    }

    private fun stopThumbnailShimmer(view: ImageView) {
        (view.background as? ShimmerDrawable)?.let {
            it.stop()
            activeShimmers.remove(it)
        }
        view.background = rounded(surface2, 10)
        view.alpha = 1f
    }

    private fun showSettings() {
        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(22), dp(8), dp(22), dp(4))
        }
        fun option(title: String, key: String, default: Boolean) {
            panel.addView(CheckBox(this).apply {
                text = title
                textSize = 14f
                setTextColor(white)
                buttonTintList = android.content.res.ColorStateList.valueOf(accent)
                isChecked = prefs.getBoolean(key, default)
                setOnCheckedChangeListener { _, checked ->
                    prefs.edit().putBoolean(key, checked).apply()
                }
            })
        }
        fun selector(title: String, key: String, values: List<String>, default: String) {
            panel.addView(label(title, 12, muted, true).apply {
                setPadding(0, dp(10), 0, dp(4))
            })
            val spinner = Spinner(this)
            spinner.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, values)
            spinner.setSelection(values.indexOf(prefs.getString(key, default)).coerceAtLeast(0))
            spinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                override fun onNothingSelected(parent: AdapterView<*>?) = Unit
                override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                    prefs.edit().putString(key, values[position]).apply()
                }
            }
            panel.addView(spinner, LinearLayout.LayoutParams(-1, dp(44)))
        }
        panel.addView(label("PERSONALIZATION", 11, accent, true).apply {
            setPadding(0, dp(4), 0, dp(4))
        })
        selector("Accent colour", "accent_color", listOf("Purple", "Pink", "Cyan", "Blue", "Green"), "Purple")
        selector("Card design", "card_style", listOf("Glass", "Solid", "Minimal"), "Glass")
        selector("Content layout", "layout_mode", listOf("Gallery", "Compact"), "Gallery")
        option("AMOLED black background", "amoled_mode", false)
        option("Animated aurora + galaxy particles", "ambient_background", true)
        option("Shimmer thumbnail loading", "shimmer_loading", true)
        panel.addView(label("PERFORMANCE", 11, accent, true).apply {
            setPadding(0, dp(12), 0, dp(4))
        })
        option("Smooth animations", "smooth_animations", true)
        option("Fast search (up to 4 concurrent detail requests)", "fast_search", true)
        option("High-quality thumbnails (uses more data)", "high_quality_thumbnails", false)
        option("Cache search details and thumbnails", "cache_enabled", true)
        AlertDialog.Builder(this)
            .setTitle("XAnime settings")
            .setView(panel)
            .setPositiveButton("APPLY") { _, _ -> showHome() }
            .setNeutralButton("CLEAR CACHE") { _, _ ->
                detailCache.clear()
                imageCache.evictAll()
                Toast.makeText(this, "Cache cleared", Toast.LENGTH_SHORT).show()
            }
            .show()
    }

    private fun animationsEnabled(): Boolean =
        prefs.getBoolean("smooth_animations", true)

    private fun animateReveal(view: View) {
        if (!animationsEnabled()) {
            view.alpha = 1f
            view.translationY = 0f
            return
        }
        view.alpha = 0f
        view.translationY = -dp(5).toFloat()
        view.animate().alpha(1f).translationY(0f).setDuration(190)
            .setInterpolator(android.view.animation.DecelerateInterpolator()).start()
    }

    private fun animateEntrance(view: View, index: Int) {
        if (!animationsEnabled()) return
        view.animate().cancel()
        view.alpha = 0f
        view.translationY = dp(10).toFloat()
        view.scaleX = 0.985f
        view.scaleY = 0.985f
        view.animate().alpha(1f).translationY(0f).scaleX(1f).scaleY(1f)
            .setStartDelay((index.coerceAtMost(6) * 28).toLong())
            .setDuration(230)
            .setInterpolator(android.view.animation.DecelerateInterpolator(1.6f))
            .start()
    }

    private fun addPressFeedback(view: View) {
        view.setOnTouchListener { target, event ->
            if (!animationsEnabled()) return@setOnTouchListener false
            when (event.actionMasked) {
                android.view.MotionEvent.ACTION_DOWN ->
                    target.animate().scaleX(0.985f).scaleY(0.985f).setDuration(85).start()
                android.view.MotionEvent.ACTION_UP,
                android.view.MotionEvent.ACTION_CANCEL ->
                    target.animate().scaleX(1f).scaleY(1f).setDuration(150)
                        .setInterpolator(android.view.animation.OvershootInterpolator(1.15f)).start()
            }
            false
        }
    }

    private fun infoCard(message: String) = TextView(this).apply {
        text = message; textSize = 13f; setTextColor(muted)
        setPadding(dp(16), dp(18), dp(16), dp(18)); background = glassDrawable(16)
    }

    private fun actionButton(text: String, primary: Boolean = true, action: () -> Unit) =
        Button(this).apply {
            this.text = text; textSize = 11f; isAllCaps = false; typeface = Typeface.DEFAULT_BOLD
            setTextColor(white)
            val fill = if (primary) accent else surface2
            background = ripple(GradientDrawable().apply {
                setColor(fill)
                cornerRadius = dp(14).toFloat()
                setStroke(dp(1), Color.argb(85, 235, 220, 255))
            })
            setOnClickListener {
                if (getSharedPreferences("xanime", MODE_PRIVATE).getBoolean("smooth_animations", true)) {
                    animate().scaleX(0.97f).scaleY(0.97f).setDuration(65).withEndAction {
                        animate().scaleX(1f).scaleY(1f).setDuration(85).start()
                    }.start()
                }
                action()
            }
        }

    private fun glassDrawable(radius: Int): GradientDrawable =
        GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
            intArrayOf(Color.argb(205, 8, 35, 47), Color.argb(188, 3, 17, 27))).apply {
            cornerRadius = dp(radius).toFloat()
            setStroke(dp(1), Color.argb(112, 34, 174, 197))
        }

    private fun ripple(content: Drawable): RippleDrawable =
        RippleDrawable(ColorStateList.valueOf(Color.argb(76, 100, 220, 235)), content, null)

    private fun animateScreen(view: View) {
        if (!prefs.getBoolean("smooth_animations", true)) return
        view.alpha = 0f
        view.translationY = dp(7).toFloat()
        view.post {
            if (!isFinishing) view.animate().alpha(1f).translationY(0f).setDuration(230).start()
        }
    }

    private fun label(text: String, size: Int, color: Int, bold: Boolean) = TextView(this).apply {
        this.text = text; textSize = size.toFloat(); setTextColor(color)
        if (bold) typeface = Typeface.DEFAULT_BOLD
    }

    private fun cardSurface(): Int = when (prefs.getString("card_style", "Glass")) {
        "Solid" -> surface2
        "Minimal" -> Color.TRANSPARENT
        else -> surface
    }

    private fun cardRadius(): Int = when (prefs.getString("card_style", "Glass")) {
        "Minimal" -> 8
        "Solid" -> 14
        else -> 18
    }

    private fun rounded(color: Int, radius: Int) = GradientDrawable().apply {
        setColor(color); cornerRadius = dp(radius).toFloat()
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
}
