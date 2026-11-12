package com.xanime.app

import android.app.Activity
import android.app.AlertDialog
import android.app.DownloadManager
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
import android.os.Environment
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
import java.util.concurrent.Future
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
    private var hitomiSearchRequestId = 0
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
    private var activeSearchTask: Future<*>? = null
    // Spaced clipboard writes allow keyboard clipboard history to observe each link.
    private var bulkCopyRequest = 0
    private var bulkCopyTask: Future<*>? = null
    private var bulkCopyDialog: AlertDialog? = null
    private var bulkCopyRunnable: Runnable? = null
    @Volatile private var searchGeneration = 0
    private var hasCachedSearch = false
    private var cachedAnimeGroups: List<AnimeGroup> = emptyList()
    private var favoriteAnimeGroups: List<AnimeGroup> = emptyList()
    @Volatile private var favoriteLoadGeneration = 0
    private var searchScrollY = 0
    private var isDetailScreen = false
    private var showingFavorites = false
    private var showingComics = false
    private var selectedComicSource = "HentaiRead"
    private var showingHentaini = false
    private var showingWatchAnimeHentai = false
    private var watchAnimeQuery = ""
    private var watchAnimeSelectedGenre: String? = null
    private val watchAnimeGenres = listOf("Action" to "action",
"Adventure" to "adventure",
"Ahegao" to "ahegao",
"Anal" to "anal",
"Big Tits" to "big-tits",
"Blackmail" to "blackmail",
"Blowjob" to "blowjob",
"Bondage" to "bondage",
"Brainwashed" to "brainwashed",
"Bukakke" to "bukakke",
"Cat Girl" to "cat-girl",
"Comedy" to "comedy",
"Cosplay" to "cosplay",
"Cream Pie" to "cream-pie",
"Creampie" to "creampie",
"Dark Skin" to "dark-skin",
"Deepthroat" to "deepthroat",
"Demons" to "demons",
"Doctor" to "doctor",
"Double Penatration" to "double-penatration",
"Elf" to "elf",
"Facial" to "facial",
"Fantasy" to "fantasy",
"Female Doctor" to "female-doctor",
"Female Teacher" to "female-teacher",
"Femdom" to "femdom",
"Footjob" to "footjob",
"Futanari" to "futanari",
"Gangbang" to "gangbang",
"Gyaru" to "gyaru",
"Harem" to "harem",
"Horny Slut" to "horny-slut",
"Housewife" to "housewife",
"Humiliation" to "humiliation",
"Incest" to "incest",
"Inflation" to "inflation",
"Internal Cumshot" to "internal-cumshot",
"Lactation" to "lactation",
"Large Breasts" to "large-breasts",
"Loli" to "loli",
"Magical Girls" to "magical-girls",
"Maid" to "maid",
"Masturbation" to "masturbation",
"Megane" to "megane",
"Milf" to "milf",
"Mind Break" to "mind-break",
"Molestation" to "molestation",
"Monsters" to "monsters",
"NTR" to "ntr",
"Nuns" to "nuns",
"Nurses" to "nurses",
"Office Ladies" to "office-ladies",
"Orgy" to "orgy",
"Pov" to "pov",
"Pregnant" to "pregnant",
"Princess" to "princess",
"Public Sex" to "public-sex",
"Rim Job" to "rim-job",
"Romance" to "romance",
"School Girls" to "school-girls",
"School Life" to "school-life",
"Sci Fi" to "sci-fi",
"Shimapan" to "shimapan",
"Shoutacon" to "shoutacon",
"Slaves" to "slaves",
"Squirting" to "squirting",
"Stocking" to "stocking",
"Submission" to "submission",
"Succubus" to "succubus",
"Super Power" to "super-power",
"Supernatural" to "supernatural",
"Swimsuit" to "swimsuit",
"Tentacles" to "tentacles",
"Three Some" to "three-some",
"Tits" to "tits",
"Tits Fuck" to "tits-fuck",
"Torture" to "torture",
"Toys" to "toys",
"Train Molestation" to "train-molestation",
"Tsundere" to "tsundere",
"Uncensored" to "uncensored",
"Urination" to "urination",
"Vampire" to "vampire",
"Vanilla" to "vanilla",
"Virgins" to "virgins",
"X Ray" to "x-ray",
"Yaoi" to "yaoi",
"Yuri" to "yuri",)
    private var watchAnimePage = 1
    private var watchAnimeRequestId = 0
    private var watchAnimeLoading = false
    private var watchAnimeResults: List<WatchAnimeSearchResult> = emptyList()
    private var watchAnimeSelectedSeries: WatchAnimeSearchResult? = null
    private var watchAnimeEpisodes: List<WatchAnimeEpisode> = emptyList()
    private var watchAnimeSeriesLoading = false
    private var watchAnimeSeriesRequestId = 0
    private var showingHentainiFavorites = false
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
        if (!showingWatchAnimeHentai && !showingComics) {
            header.addView(actionButton(
                if (showingHentaini) { if (showingHentainiFavorites) "‹ Search" else "♥ Favorites" }
                else { if (showingFavorites) "‹ Home" else "♥ Favorites" }, false
            ) {
                if (showingHentaini) showingHentainiFavorites = !showingHentainiFavorites
                else showingFavorites = !showingFavorites
                showHome()
            }, LinearLayout.LayoutParams(-2, dp(44)).apply { marginEnd = dp(7) })

        }
        header.addView(actionButton("⚙", false) { showSettings() },
            LinearLayout.LayoutParams(dp(44), dp(44)))
        pageContent.addView(header)

        // Top-level library switcher: the existing source tabs stay grouped under Hentai.
        val libraryTabs = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        libraryTabs.addView(actionButton("Hentai", !showingComics) {
            if (showingComics) {
                showingComics = false
                showHome()
            }
        }, LinearLayout.LayoutParams(0, dp(42), 1f).apply { marginEnd = dp(7) })
        libraryTabs.addView(actionButton("Comics", showingComics) {
            if (!showingComics) {
                showingComics = true
                showHome()
            }
        }, LinearLayout.LayoutParams(0, dp(42), 1f))
        pageContent.addView(libraryTabs, LinearLayout.LayoutParams(-1, dp(44)).apply { topMargin = dp(10) })

        if (showingComics) {
            pageContent.addView(label("COMICS", 22, white, true).apply {
                setPadding(0, dp(20), 0, dp(8))
            })
            pageContent.addView(label("CHOOSE A WEBSITE", 9, muted, true).apply {
                letterSpacing = .12f
                setPadding(0, dp(3), 0, dp(9))
            })
            // Keep all comic sources in the same normal tab row.
            val comicTabs = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
            }
            addWebsiteTab(comicTabs, "HentaiRead", listOf("READ"),
                selectedComicSource == "HentaiRead", 5) {
                selectedComicSource = "HentaiRead"
                showHome()
            }
            addWebsiteTab(comicTabs, "HitomiLA", listOf("READ", "DOWNLOAD"),
                selectedComicSource == "HitomiLA", 5) {
                selectedComicSource = "HitomiLA"
                showHome()
            }
            addWebsiteTab(comicTabs, "AllPornComic", emptyList(),
                selectedComicSource == "AllPornComic") {
                selectedComicSource = "AllPornComic"
                showHome()
            }
            pageContent.addView(comicTabs, LinearLayout.LayoutParams(-1, -2).apply {
                topMargin = dp(12)
            })

            when (selectedComicSource) {
                "HentaiRead" -> {
                    pageContent.addView(infoCard("Click Read to Browse And Read Hentai Manga. Enjoy!"),
                        LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(10) })
                    pageContent.addView(actionButton("📖  READ", true) {
                        startActivity(Intent(this, HentaiReadComicsActivity::class.java))
                    }, LinearLayout.LayoutParams(-1, dp(52)).apply { topMargin = dp(10) })
                }
                "HitomiLA" -> showHitomiLaHome()
                "AllPornComic" -> pageContent.addView(infoCard("AllPornComic will be added later."),
                    LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(10) })
            }
            setContentView(root)
            animateScreen(root)
            return
        }

        val sourceTabs = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        addWebsiteTab(sourceTabs, "XAnime", listOf("DIRECT LINKS"),
            !showingHentaini && !showingWatchAnimeHentai, 6) {
            if (showingWatchAnimeHentai) watchAnimeRequestId++
            val keepFavorites = if (showingHentaini) showingHentainiFavorites else showingFavorites
            showingHentaini = false
            showingWatchAnimeHentai = false
            showingFavorites = keepFavorites
            watchAnimeLoading = false
            showHome()
        }
        addWebsiteTab(sourceTabs, "Hentaini",
            listOf("MEGA", "MEDIAFIRE", "TERABOX", "DIRECT LINKS"), showingHentaini, 6) {
            if (showingWatchAnimeHentai) watchAnimeRequestId++
            val keepFavorites = if (showingHentaini) showingHentainiFavorites else showingFavorites
            showingHentaini = true
            showingWatchAnimeHentai = false
            showingHentainiFavorites = keepFavorites
            watchAnimeLoading = false
            showHome()
        }
        addWebsiteTab(sourceTabs, "WatchAnimeHentai", listOf("IN-APP DOWNLOAD"),
            showingWatchAnimeHentai) {
            if (showingWatchAnimeHentai) return@addWebsiteTab
            showingHentaini = false
            showingWatchAnimeHentai = true
            showingFavorites = false
            showHome()
        }
        pageContent.addView(sourceTabs, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(12) })

        if (showingHentaini) {
            showHentainiHome()
            return
        }
        if (showingWatchAnimeHentai) {
            showWatchAnimeHentaiHome()
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
        scroll.setOnScrollChangeListener { _, _, scrollY, _, _ ->
            ambientBackground?.setScrollOffset(scrollY)
            loadVisibleImages(pageContent)
        }
        scroll.post { loadVisibleImages(pageContent) }
        if (showingFavorites) {
            status.text = "Your saved series • loading episode details"
            favoriteAnimeGroups = loadFavoriteGroups()
            renderAnimeGroups(favoriteAnimeGroups)
            pageText.text = "FAVORITES • ${favoriteAnimeGroups.size} SERIES"
            loadFavoriteGroupDetails(favoriteAnimeGroups)
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
        if (showingWatchAnimeHentai && watchAnimeSelectedSeries != null) {
            watchAnimeSeriesRequestId++
            watchAnimeSelectedSeries = null
            watchAnimeEpisodes = emptyList()
            watchAnimeSeriesLoading = false
            showHome()
        } else if (showingHentaini && hentainiSelectedSeries != null) {
            hentainiDetailRequestId++
            hentainiSelectedSeries = null
            showHome()
        } else if (showingHentaini) {
            showingHentaini = false
            showHome()
        } else if (isDetailScreen && (hasCachedSearch || showingFavorites)) showHome() else super.onBackPressed()
    }



    private fun showWatchAnimeHentaiHome() {
        pageContent.addView(label("WATCH ANIME HENTAI", 22, white, true).apply {
            setPadding(0, dp(22), 0, dp(8))
        })
        pageContent.addView(label("EPISODES • ENGLISH DOWNLOADS", 9, muted, true).apply {
            letterSpacing = .08f
            setPadding(0, 0, 0, dp(12))
        })

        watchAnimeSelectedSeries?.let { selected ->
            pageContent.addView(actionButton("‹ BACK TO SEARCH RESULTS", false) {
                watchAnimeSeriesRequestId++
                watchAnimeSelectedSeries = null
                watchAnimeEpisodes = emptyList()
                watchAnimeSeriesLoading = false
                showHome()
            }, LinearLayout.LayoutParams(-1, dp(42)).apply { bottomMargin = dp(10) })
            pageContent.addView(label(selected.title, 18, white, true).apply {
                setPadding(0, dp(5), 0, dp(12))
            })
            if (selected.thumbnail.isNotBlank()) {
                val poster = ImageView(this).apply {
                    scaleType = ImageView.ScaleType.CENTER_CROP
                    background = rounded(surface2, 14)
                }
                pageContent.addView(poster, LinearLayout.LayoutParams(dp(116), dp(156)).apply {
                    bottomMargin = dp(12)
                })
                loadImage(selected.thumbnail, poster)
            }
            when {
                watchAnimeSeriesLoading -> pageContent.addView(infoCard("Loading episode list..."))
                watchAnimeEpisodes.isEmpty() -> pageContent.addView(infoCard("No episodes were found on this series page."))
                else -> {
                    pageContent.addView(label("${watchAnimeEpisodes.size} EPISODES", 10, accent, true).apply {
                        setPadding(0, dp(4), 0, dp(10))
                    })
                    watchAnimeEpisodes.forEachIndexed { index, episode ->
                        val row = LinearLayout(this).apply {
                            orientation = LinearLayout.HORIZONTAL
                            gravity = Gravity.CENTER_VERTICAL
                            setPadding(dp(9), dp(9), dp(9), dp(9))
                            background = rounded(cardSurface(), cardRadius())
                        }
                        val thumb = ImageView(this).apply {
                            scaleType = ImageView.ScaleType.CENTER_CROP
                            background = rounded(surface2, 9)
                        }
                        row.addView(thumb, LinearLayout.LayoutParams(dp(78), dp(104)).apply {
                            marginEnd = dp(10)
                        })
                        if (episode.thumbnail.isNotBlank()) loadImage(episode.thumbnail, thumb)
                        val detail = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
                        detail.addView(label(episode.title, 14, white, true))
                        detail.addView(label(if (episode.subbed) "SUBBED" else "ENGLISH OPTION CHECKED ON PAGE", 9, muted, true).apply {
                            setPadding(0, dp(5), 0, dp(8))
                        })
                        detail.addView(actionButton("↓ DOWNLOAD", true) {
                            downloadWatchAnimeEpisode(episode)
                        }, LinearLayout.LayoutParams(-1, dp(40)))
                        row.addView(detail, LinearLayout.LayoutParams(0, -2, 1f))
                        pageContent.addView(row, LinearLayout.LayoutParams(-1, -2).apply {
                            bottomMargin = dp(9)
                        })
                        animateEntrance(row, index)
                    }
                }
            }
            setContentView(root)
            animateScreen(root)
            scroll.post { loadVisibleImages(pageContent) }
            isDetailScreen = true
            return
        }

        search = EditText(this).apply {
            hint = "Search WatchAnimeHentai..."
            setHintTextColor(muted)
            setTextColor(white)
            textSize = 14f
            isSingleLine = true
            imeOptions = EditorInfo.IME_ACTION_SEARCH
            setPadding(dp(13), 0, dp(10), 0)
            background = ripple(glassDrawable(14))
            setText(watchAnimeQuery)
        }
        val searchRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        searchRow.addView(search, LinearLayout.LayoutParams(0, dp(44), 1f).apply {
            marginEnd = dp(7)
        })
        searchRow.addView(actionButton("SEARCH ↗") {
            watchAnimeQuery = search.text.toString().trim()
            watchAnimeSelectedGenre = null
            watchAnimePage = 1
            watchAnimeResults = emptyList()
            runWatchAnimeSearch()
        }, LinearLayout.LayoutParams(dp(88), dp(44)))
        pageContent.addView(searchRow, LinearLayout.LayoutParams(-1, -2).apply {
            topMargin = dp(7)
        })

        pageContent.addView(label("GENRES", 10, muted, true).apply {
            setPadding(0, dp(12), 0, dp(6))
            letterSpacing = .1f
        })

        val genreSearchInput = EditText(this).apply {
            hint = "Search genres..."
            setHintTextColor(muted)
            setTextColor(white)
            textSize = 14f
            isSingleLine = true
            imeOptions = EditorInfo.IME_ACTION_DONE
            setPadding(dp(13), 0, dp(10), 0)
            background = ripple(glassDrawable(14))
        }
        pageContent.addView(genreSearchInput, LinearLayout.LayoutParams(-1, dp(42)).apply {
            bottomMargin = dp(7)
        })

        val genreActionRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        genreActionRow.addView(actionButton("ALL GENRES", watchAnimeSelectedGenre == null) {
            if (watchAnimeSelectedGenre != null) {
                watchAnimeSelectedGenre = null
                watchAnimeQuery = ""
                search.setText("")
                genreSearchInput.setText("")
                watchAnimePage = 1
                watchAnimeResults = emptyList()
                runWatchAnimeSearch()
            }
        }, LinearLayout.LayoutParams(-2, dp(34)))
        pageContent.addView(genreActionRow, LinearLayout.LayoutParams(-1, dp(38)).apply {
            bottomMargin = dp(5)
        })

        val genreSuggestions = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            visibility = View.GONE
            setPadding(dp(7), dp(7), dp(7), dp(7))
            background = rounded(surface, 13)
        }
        pageContent.addView(genreSuggestions, LinearLayout.LayoutParams(-1, -2).apply {
            bottomMargin = dp(7)
        })

        fun refreshGenreSuggestions(rawQuery: String) {
            genreSuggestions.removeAllViews()
            val term = rawQuery.trim()
            if (term.isBlank()) {
                genreSuggestions.visibility = View.GONE
                return
            }
            val matches = watchAnimeGenres
                .filter { it.first.contains(term, ignoreCase = true) || it.second.contains(term, ignoreCase = true) }
                .take(8)
            genreSuggestions.visibility = View.VISIBLE
            if (matches.isEmpty()) {
                genreSuggestions.addView(label("No matching genres", 12, muted, false).apply {
                    setPadding(dp(9), dp(10), dp(9), dp(10))
                })
                return
            }
            matches.forEach { (genreName, slug) ->
                genreSuggestions.addView(actionButton(genreName, watchAnimeSelectedGenre == slug) {
                    watchAnimeSelectedGenre = slug
                    watchAnimeQuery = ""
                    search.setText("")
                    genreSearchInput.setText("")
                    watchAnimePage = 1
                    watchAnimeResults = emptyList()
                    runWatchAnimeSearch()
                }, LinearLayout.LayoutParams(-1, dp(38)).apply {
                    bottomMargin = dp(4)
                })
            }
        }

        genreSearchInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
            override fun afterTextChanged(s: Editable?) {
                refreshGenreSuggestions(s?.toString().orEmpty())
            }
        })
        search.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                watchAnimeQuery = search.text.toString().trim()
                watchAnimeSelectedGenre = null
                watchAnimePage = 1
                watchAnimeResults = emptyList()
                runWatchAnimeSearch()
                true
            } else false
        }

        status = label(
            when {
                watchAnimeSelectedGenre != null -> "Browse genre: ${watchAnimeGenres.firstOrNull { it.second == watchAnimeSelectedGenre }?.first ?: watchAnimeSelectedGenre}"
                watchAnimeQuery.isBlank() -> "Search the WatchAnimeHentai catalogue"
                else -> "Results for: $watchAnimeQuery"
            },
            13, muted, true
        ).apply { setPadding(0, dp(16), 0, dp(8)) }
        pageContent.addView(status)
        progress = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            visibility = View.GONE
            isIndeterminate = true
        }
        pageContent.addView(progress, LinearLayout.LayoutParams(-1, dp(4)))
        content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, dp(8), 0, dp(10))
        }
        pageContent.addView(content, LinearLayout.LayoutParams(-1, -2))
        pageText = label("WATCHANIMEHENTAI SEARCH", 10, muted, true).apply {
            gravity = Gravity.CENTER
            setPadding(0, dp(10), 0, dp(4))
        }
        pageContent.addView(pageText)

        if (watchAnimeResults.isNotEmpty()) {
            status.text = "${watchAnimeResults.size} WatchAnimeHentai results"
            renderWatchAnimeResults(watchAnimeResults)
        } else if (watchAnimeQuery.isNotBlank() && !watchAnimeLoading) {
            runWatchAnimeSearch()
        } else if (watchAnimeQuery.isBlank() && watchAnimeSelectedGenre == null) {
            content.addView(infoCard("Search titles or choose a genre to browse the catalogue."))
        }
        setContentView(root)
        animateScreen(root)
        scroll.post { loadVisibleImages(pageContent) }
        isDetailScreen = false
    }

    private fun openWatchAnimeSeries(series: WatchAnimeSearchResult) {
        watchAnimeSelectedSeries = series
        watchAnimeEpisodes = emptyList()
        watchAnimeSeriesLoading = true
        val requestId = ++watchAnimeSeriesRequestId
        showHome()
        worker.execute {
            try {
                val html = request(series.url)
                val episodes = SiteParser.parseWatchAnimeSeries(Jsoup.parse(html, series.url), series.url)
                main.post {
                    if (isFinishing || !showingWatchAnimeHentai ||
                        requestId != watchAnimeSeriesRequestId ||
                        watchAnimeSelectedSeries?.url != series.url) return@post
                    watchAnimeEpisodes = episodes
                    watchAnimeSeriesLoading = false
                    showHome()
                }
            } catch (_: Exception) {
                main.post {
                    if (isFinishing || requestId != watchAnimeSeriesRequestId) return@post
                    watchAnimeSeriesLoading = false
                    watchAnimeEpisodes = emptyList()
                    Toast.makeText(this, "Could not load this episode list.", Toast.LENGTH_LONG).show()
                    showHome()
                }
            }
        }
    }

    private fun safeDownloadFileName(rawName: String): String {
        val cleaned = rawName
            .replace(Regex("[\\u0000-\\u001F\\\\/:*?\"<>|]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
            .trim('.')
            .take(120)
            .trim()
        return (cleaned.ifBlank { "WatchAnimeHentai" } + ".mp4")
    }

    private fun downloadWatchAnimeEpisode(episode: WatchAnimeEpisode) {
        val seriesTitle = watchAnimeSelectedSeries?.title.orEmpty()
        Toast.makeText(this, "Preparing English download...", Toast.LENGTH_SHORT).show()
        worker.execute {
            try {
                val downloadPageUrl = episode.url.replace("/episodes/", "/download-hentai/")
                val downloadPage = Jsoup.parse(request(downloadPageUrl), downloadPageUrl)
                val englishEndpoint = SiteParser.parseWatchAnimeEnglishDownload(downloadPage, downloadPageUrl)
                    ?: throw IllegalStateException("English download option was not found")
                main.post {
                    try {
                        val baseTitle = seriesTitle.ifBlank { "WatchAnimeHentai" }
                        val fileName = safeDownloadFileName("${baseTitle} - Episode ${episode.number}")
                        val downloadRequest = DownloadManager.Request(Uri.parse(englishEndpoint)).apply {
                            setTitle("WatchAnimeHentai • Episode ${episode.number}")
                            setDescription("English video download")
                            setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                            setAllowedNetworkTypes(
                                DownloadManager.Request.NETWORK_WIFI or DownloadManager.Request.NETWORK_MOBILE
                            )
                            addRequestHeader("Referer", downloadPageUrl)
                            addRequestHeader("User-Agent", "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 Chrome/124.0 Mobile Safari/537.36")
                            setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName)
                        }
                        val manager = getSystemService(DOWNLOAD_SERVICE) as DownloadManager
                        manager.enqueue(downloadRequest)
                        Toast.makeText(this, "Download started • check notifications", Toast.LENGTH_LONG).show()
                    } catch (_: Exception) {
                        Toast.makeText(this, "Could not start the download.", Toast.LENGTH_LONG).show()
                    }
                }
            } catch (_: Exception) {
                main.post {
                    if (!isFinishing) Toast.makeText(
                        this,
                        "English download link unavailable. The site may require an updated player link.",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    }

    private fun runWatchAnimeSearch(append: Boolean = false) {
        if (watchAnimeLoading || (watchAnimeQuery.isBlank() && watchAnimeSelectedGenre == null)) {
            if (watchAnimeQuery.isBlank() && watchAnimeSelectedGenre == null) status.text = "Enter a title or choose a genre."
            return
        }
        watchAnimeLoading = true
        val requestId = ++watchAnimeRequestId
        val requestedPage = watchAnimePage
        status.text = if (append) "Loading more results..." else "Searching WatchAnimeHentai..."
        progress.visibility = View.VISIBLE
        if (!append) content.removeAllViews()
        pageText.text = "SEARCHING • PAGE $requestedPage"
        worker.execute {
            try {
                val address = watchAnimeSelectedGenre?.let { slug ->
                    "https://www.watchanimehentai.com/genre/$slug/" +
                        if (requestedPage > 1) "$requestedPage/" else ""
                } ?: run {
                    val encoded = URLEncoder.encode(watchAnimeQuery.trim(), "UTF-8")
                    "https://www.watchanimehentai.com/search/$encoded/" +
                        if (requestedPage > 1) "?pagination=$requestedPage" else ""
                }
                val pageDocument = Jsoup.parse(request(address), address)
                val parsed = if (watchAnimeSelectedGenre != null) {
                    SiteParser.parseWatchAnimeGenre(pageDocument)
                } else {
                    SiteParser.parseWatchAnimeSearch(pageDocument)
                }
                main.post {
                    if (isFinishing || !showingWatchAnimeHentai || requestId != watchAnimeRequestId) return@post
                    watchAnimeLoading = false
                    progress.visibility = View.GONE
                    val merged = if (append) watchAnimeResults + parsed else parsed
                    watchAnimeResults = merged.distinctBy { it.url }
                    status.text = if (watchAnimeResults.isEmpty()) "No results found on WatchAnimeHentai"
                        else "${watchAnimeResults.size} WatchAnimeHentai results"
                    renderWatchAnimeResults(watchAnimeResults)
                    pageText.text = "WATCHANIMEHENTAI • PAGE $requestedPage"
                }
            } catch (_: Exception) {
                main.post {
                    if (isFinishing || !showingWatchAnimeHentai || requestId != watchAnimeRequestId) return@post
                    watchAnimeLoading = false
                    progress.visibility = View.GONE
                    status.text = "WatchAnimeHentai search unavailable"
                    if (!append) content.addView(infoCard("Could not load WatchAnimeHentai results. Check your connection and try again."))
                    pageText.text = "SEARCH FAILED"
                }
            }
        }
    }

    private fun renderWatchAnimeResults(results: List<WatchAnimeSearchResult>) {
        content.removeAllViews()
        if (results.isEmpty()) {
            content.addView(infoCard("No results found. Try another search."))
            return
        }
        results.forEachIndexed { index, result ->
            val card = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding(dp(10), dp(10), dp(10), dp(10))
                background = rounded(cardSurface(), cardRadius())
                isClickable = true
                isFocusable = true
                setOnClickListener {
                    openWatchAnimeSeries(result)
                }
            }
            val poster = ImageView(this).apply {
                scaleType = ImageView.ScaleType.CENTER_CROP
                background = rounded(surface2, 10)
            }
            card.addView(poster, LinearLayout.LayoutParams(dp(104), dp(150)).apply {
                marginEnd = dp(12)
            })
            if (result.thumbnail.isNotBlank()) loadImage(result.thumbnail, poster)
            val info = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
            info.addView(label(result.title, 15, white, true).apply { maxLines = 3 })
            if (result.genres.isNotEmpty()) {
                info.addView(label(result.genres.joinToString(" • "), 10, muted, true).apply {
                    setPadding(0, dp(6), 0, dp(4))
                    maxLines = 3
                })
            }
            if (result.studio.isNotBlank()) info.addView(label(result.studio, 11, accent, true))
            if (result.description.isNotBlank()) {
                info.addView(label(result.description, 12, muted, false).apply {
                    maxLines = 4
                    setPadding(0, dp(5), 0, 0)
                })
            }
            card.addView(info, LinearLayout.LayoutParams(0, -2, 1f))
            content.addView(card, LinearLayout.LayoutParams(-1, -2).apply {
                bottomMargin = dp(12)
            })
            animateEntrance(card, index)
        }
        if (watchAnimePage < 10) {
            content.addView(actionButton("NEXT PAGE  →") {
                if (!watchAnimeLoading) {
                    watchAnimePage++
                    runWatchAnimeSearch(append = true)
                }
            }, LinearLayout.LayoutParams(-1, dp(46)).apply {
                topMargin = dp(8)
                bottomMargin = dp(12)
            })
        }
    }

    private fun showHentainiHome() {
        pageContent.addView(label(if (showingHentainiFavorites) "YOUR FAVORITES" else "HENTAINI", 22, white, true).apply {
            setPadding(0, dp(22), 0, dp(8))
        })
        if (showingHentainiFavorites) {
            val saved = loadHentainiFavorites()
            pageContent.addView(label("SAVED SERIES • " + saved.size, 10, accent, true).apply {
                setPadding(0, 0, 0, dp(12))
            })
            if (saved.isNotEmpty()) {
                pageContent.addView(actionButton("COPY DOWNLOAD STREAMS", true) {
                    showHentainiBulkSeriesPicker(saved)
                }, LinearLayout.LayoutParams(-1, dp(46)).apply { bottomMargin = dp(8) })
                pageContent.addView(label("Choose series and providers, then copy each stream link separately.", 11, muted, false).apply {
                    setPadding(dp(2), 0, dp(2), dp(12))
                })
            }
            val savedList = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
            pageContent.addView(savedList, LinearLayout.LayoutParams(-1, -2))
            if (saved.isEmpty()) savedList.addView(infoCard("No favorites yet. Tap the heart on any Hentaini series to save it here."))
            else saved.forEach { addHentainiCard(it, savedList) }
            setContentView(root)
            animateScreen(root)
            isDetailScreen = false
            return
        }
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
        val searchRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        searchRow.addView(input, LinearLayout.LayoutParams(0, dp(48), 1f).apply {
            marginEnd = dp(8)
        })
        pageContent.addView(searchRow, LinearLayout.LayoutParams(-1, dp(52)))

        val genreScroller = HorizontalScrollView(this).apply { isHorizontalScrollBarEnabled = false }
        val genreRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        listOf("Ahegao" to "ahegao","Anal" to "anal","BBW" to "bbw","Big Breast" to "big breast","Blackmail" to "blackmail","Bondage" to "bondage","Chikan" to "chikan","Cosplay" to "cosplay","Futanari" to "futanari","Garyu" to "garyu","Harem" to "harem","Incest" to "incest","Maid" to "maid","Milf" to "milf","Netorare" to "netorare","Nurse" to "nurse","Orgy" to "orgy","Oyakodon" to "oyakodon","Paizuri" to "paizuri","Rape" to "rape","Scholar" to "scholar","Shotacon" to "shotacon","Small Breasts" to "small breasts","Succubus" to "succubus","Tentacles" to "tentacles",).forEach { (name, slug) ->
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
        searchRow.addView(actionButton("SEARCH ↗") { submitSearch() },
            LinearLayout.LayoutParams(dp(100), dp(48)))
        input.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                submitSearch()
                true
            } else false
        }
        if (hentainiGenre != null) {
            hentainiSearch("", true, statusView, progressView, results, loadMore, hentainiGenre)
        } else if (hentainiQuery.isNotBlank()) {
            hentainiSearch(hentainiQuery, true, statusView, progressView, results, loadMore, null)
        } else {
            results.addView(infoCard("Search titles or choose a genre to browse."))
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
        lateinit var card: LinearLayout
        lateinit var favoriteButton: Button
        card = LinearLayout(this).apply {
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
        favoriteButton = actionButton(if (isHentainiFavorite(series)) "♥" else "♡", false) {
            toggleHentainiFavorite(series)
            val isSaved = isHentainiFavorite(series)
            favoriteButton.text = if (isSaved) "♥" else "♡"
            // Removing a saved card updates only this row; don't rebuild the
            // whole Hentaini screen or reset the user's current scroll position.
            if (showingHentainiFavorites && !isSaved) {
                output.removeView(card)
                if (output.childCount == 0) {
                    output.addView(infoCard("No favorites yet. Tap the heart on any Hentaini series to save it here."))
                }
            }
        }
        card.addView(favoriteButton, LinearLayout.LayoutParams(dp(38), dp(38)).apply { marginStart = dp(5) })
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


    private fun hentainiFavoriteEntries(): Set<String> =
        prefs.getStringSet("hentaini_favorite_series", emptySet())?.toSet() ?: emptySet()

    private fun isHentainiFavorite(series: HentainiSeries): Boolean =
        hentainiFavoriteEntries().any { encoded ->
            runCatching { org.json.JSONObject(encoded).optString("slug") == series.slug }.getOrDefault(false)
        }

    private fun toggleHentainiFavorite(series: HentainiSeries) {
        val entries = hentainiFavoriteEntries().toMutableSet()
        val existing = entries.firstOrNull { encoded ->
            runCatching { org.json.JSONObject(encoded).optString("slug") == series.slug }.getOrDefault(false)
        }
        if (existing != null) {
            entries.remove(existing)
            Toast.makeText(this, "Removed from Hentaini Favorites", Toast.LENGTH_SHORT).show()
        } else {
            val item = org.json.JSONObject()
                .put("id", series.id).put("title", series.title).put("slug", series.slug)
                .put("hId", series.hId).put("hasEpisodes", series.hasEpisodes)
                .put("thumbnailUrl", series.thumbnailUrl)
            entries.add(item.toString())
            Toast.makeText(this, "Added to Hentaini Favorites", Toast.LENGTH_SHORT).show()
        }
        prefs.edit().putStringSet("hentaini_favorite_series", entries).apply()
    }

    private fun loadHentainiFavorites(): List<HentainiSeries> =
        hentainiFavoriteEntries().mapNotNull { encoded ->
            runCatching {
                val item = org.json.JSONObject(encoded)
                val slug = item.optString("slug").takeIf { it.isNotBlank() } ?: return@runCatching null
                HentainiSeries(item.optInt("id"), item.optString("title"), slug,
                    item.optString("hId"), item.optBoolean("hasEpisodes", true), item.optString("thumbnailUrl"))
            }.getOrNull()
        }.sortedBy { it.title.lowercase() }

    private fun showHentainiBulkSeriesPicker(groups: List<HentainiSeries>) {
        if (groups.isEmpty()) return
        val selected = BooleanArray(groups.size)
        AlertDialog.Builder(this)
            .setTitle("Choose Hentaini series")
            .setMultiChoiceItems(groups.map { it.title }.toTypedArray(), selected) { _, which, checked ->
                selected[which] = checked
            }
            .setPositiveButton("CHOOSE STREAMS") { _, _ ->
                val chosen = groups.filterIndexed { index, _ -> selected[index] }
                if (chosen.isEmpty()) {
                    Toast.makeText(this, "Select at least one series.", Toast.LENGTH_SHORT).show()
                } else showHentainiBulkStreamPicker(chosen)
            }
            .setNegativeButton("CANCEL", null)
            .show()
    }

    private fun showHentainiBulkStreamPicker(groups: List<HentainiSeries>) {
        val providers = listOf(
            "terabox" to "1024TERABOX   🔥 HOT",
            "fireload" to "FIRELOAD   🔥 HOT",
            "mediafire" to "MEDIAFIRE   🔥 HOT",
            "mega" to "MEGA   🔥 HOT",
            "yesterdaymail" to "YESTERDAYMEAIL   • Direct Video",
            "other" to "Everything else"
        )
        val selected = BooleanArray(providers.size)
        AlertDialog.Builder(this)
            .setTitle("Choose download streams")
            .setMultiChoiceItems(providers.map { it.second }.toTypedArray(), selected) { _, which, checked ->
                selected[which] = checked
            }
            .setPositiveButton("SET PREFIXES") { _, _ ->
                val chosen = providers.filterIndexed { index, _ -> selected[index] }.map { it.first }
                if (chosen.isEmpty()) {
                    Toast.makeText(this, "Choose at least one stream provider.", Toast.LENGTH_SHORT).show()
                } else showHentainiBulkPrefixEditor(groups, chosen)
            }
            .setNegativeButton("BACK") { _, _ -> showHentainiBulkSeriesPicker(groups) }
            .show()
    }

    private fun showHentainiBulkPrefixEditor(groups: List<HentainiSeries>, providers: List<String>) {
        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(8), dp(18), dp(8))
        }
        val addPrefix = android.widget.CheckBox(this).apply {
            text = "Add prefix to each copied link"
            isChecked = true
        }
        panel.addView(addPrefix, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(8) })
        val inputs = linkedMapOf<String, EditText>()
        val defaults = mapOf(
            "terabox" to ".terabox",
            "fireload" to ".download",
            "mediafire" to ".mediafire",
            "mega" to ".mega",
            "yesterdaymail" to ".download",
            "other" to ".download"
        )
        val labels = mapOf(
            "terabox" to "1024TERABOX",
            "fireload" to "FIRELOAD",
            "mediafire" to "MEDIAFIRE",
            "mega" to "MEGA",
            "yesterdaymail" to "YESTERDAYMEAIL",
            "other" to "EVERYTHING ELSE"
        )
        providers.forEach { provider ->
            panel.addView(label(labels[provider].orEmpty() + " PREFIX", 10, accent, true).apply {
                setPadding(0, dp(7), 0, dp(3))
            })
            val field = EditText(this).apply {
                setText(defaults[provider].orEmpty())
                hint = "Prefix"
                setSingleLine(true)
                setTextColor(white)
                setHintTextColor(muted)
                background = ripple(glassDrawable(12))
                setPadding(dp(12), dp(8), dp(12), dp(8))
            }
            inputs[provider] = field
            panel.addView(field, LinearLayout.LayoutParams(-1, dp(44)).apply { bottomMargin = dp(4) })
        }
        addPrefix.setOnCheckedChangeListener { _, checked ->
            inputs.values.forEach { it.isEnabled = checked }
        }
        AlertDialog.Builder(this)
            .setTitle("Set prefixes before copying")
            .setMessage("Each provider uses its own prefix. You can edit these now.")
            .setView(panel)
            .setPositiveButton("START COPYING") { _, _ ->
                val prefixes = inputs.mapValues { it.value.text.toString().trim() }
                startHentainiBulkCopy(groups, providers.toSet(), prefixes, addPrefix.isChecked)
            }
            .setNegativeButton("BACK") { _, _ -> showHentainiBulkStreamPicker(groups) }
            .show()
    }

    private fun hentainiBulkProviderKey(stream: HentainiStream): String {
        val host = Uri.parse(stream.url).host.orEmpty().lowercase()
        val identity = host + " " + stream.host.lowercase()
        return when {
            identity.contains("1024terabox") || identity.contains("terabox") -> "terabox"
            identity.contains("fireload") -> "fireload"
            identity.contains("mediafire") -> "mediafire"
            identity.contains("mega.nz") || stream.host.equals("MEGA", true) -> "mega"
            identity.contains("yesterdaymail") -> "yesterdaymail"
            else -> "other"
        }
    }

    private fun startHentainiBulkCopy(
        groups: List<HentainiSeries>,
        selectedProviders: Set<String>,
        prefixes: Map<String, String>,
        addPrefix: Boolean
    ) {
        cancelBulkCopy(false)
        val requestId = ++bulkCopyRequest
        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(8), dp(20), dp(8))
        }
        val progressText = label("Loading Hentaini episode streams...", 12, muted, false)
        val progressBar = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            isIndeterminate = true
        }
        panel.addView(progressText, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(12) })
        panel.addView(progressBar, LinearLayout.LayoutParams(-1, dp(5)))
        val dialog = AlertDialog.Builder(this)
            .setTitle("Hentaini bulk copy")
            .setView(panel)
            .setNegativeButton("CANCEL", null)
            .create()
        bulkCopyDialog = dialog
        dialog.setOnCancelListener {
            if (bulkCopyRequest == requestId) cancelBulkCopy(false)
        }
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_NEGATIVE)?.setOnClickListener { cancelBulkCopy() }
        }
        dialog.show()

        bulkCopyTask = detailWorker.submit {
            val collected = LinkedHashMap<String, String>()
            var episodesChecked = 0
            var episodesFailed = 0
            try {
                groups.forEachIndexed { seriesIndex, series ->
                    if (Thread.currentThread().isInterrupted) return@submit
                    val episodes = runCatching { HentainiApi.loadEpisodes(series) }.getOrNull()
                    if (episodes.isNullOrEmpty()) {
                        episodesFailed++
                        main.post {
                            if (bulkCopyRequest == requestId && dialog.isShowing) {
                                progressText.text = "Could not load episodes for " + series.title
                            }
                        }
                        return@forEachIndexed
                    }
                    episodes.forEachIndexed { episodeIndex, episode ->
                        if (Thread.currentThread().isInterrupted) return@submit
                        val details = hentainiEpisodeCache[episode.url] ?: runCatching {
                            HentainiApi.loadEpisodePage(episode)
                        }.getOrNull()?.also { hentainiEpisodeCache[episode.url] = it }
                        episodesChecked++
                        if (details == null) {
                            episodesFailed++
                        } else {
                            details.streams.forEach { stream ->
                                val provider = hentainiBulkProviderKey(stream)
                                if (provider in selectedProviders) {
                                    collected.putIfAbsent(stream.url, prefixes[provider].orEmpty())
                                }
                            }
                        }
                        val checked = episodesChecked
                        main.post {
                            if (bulkCopyRequest == requestId && dialog.isShowing) {
                                progressText.text = series.title + " • Series " + (seriesIndex + 1) + "/" + groups.size +
                                    " • Episode " + (episodeIndex + 1) + "/" + episodes.size + " • " + checked + " checked"
                            }
                        }
                    }
                }
            } catch (_: Exception) {
                // Keep any links already found if an individual page fails.
            }
            if (Thread.currentThread().isInterrupted) return@submit
            val links = collected.toList()
            main.post {
                if (bulkCopyRequest != requestId || !dialog.isShowing) return@post
                if (links.isEmpty()) {
                    progressBar.visibility = View.GONE
                    progressText.text = "No matching streams found. Checked " + episodesChecked +
                        " episodes; " + episodesFailed + " pages failed."
                    dialog.getButton(AlertDialog.BUTTON_NEGATIVE)?.text = "CLOSE"
                    dialog.getButton(AlertDialog.BUTTON_NEGATIVE)?.setOnClickListener { dialog.dismiss() }
                    return@post
                }
                progressBar.isIndeterminate = false
                progressBar.max = links.size
                progressBar.progress = 0
                val clipboardEntries = links.map { (url, prefix) ->
                    if (addPrefix && prefix.isNotBlank()) "$prefix $url" else url
                }
                progressText.text = "Found " + links.size + " unique streams. Copying one at a time..."
                copyBulkLinksOneByOne(clipboardEntries, 0, requestId, dialog, progressText, progressBar, episodesFailed)
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
                        if (expanded) {
                            hiddenLinks.visibility = View.VISIBLE
                            animateReveal(hiddenLinks)
                        } else {
                            hiddenLinks.animate().alpha(0f).translationY(-dp(4).toFloat()).setDuration(120)
                                .withEndAction {
                                    hiddenLinks.visibility = View.GONE
                                    hiddenLinks.alpha = 1f
                                    hiddenLinks.translationY = 0f
                                }.start()
                        }
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
        if (reset) cancelActiveSearch()
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

    private fun cancelActiveSearch() {
        searchGeneration++
        activeSearchTask?.cancel(true)
        activeSearchTask = null
        busy = false
    }

    private fun runSearch(append: Boolean = false) {
        if (busy || (query.isBlank() && selectedCategory == null && !catalogMode)) return
        busy = true
        val generation = searchGeneration
        val requestedPage = page
        status.text = if (append) "Loading more results..." else "Searching..."
        progress.visibility = View.VISIBLE
        progress.progress = requestedPage
        if (!append) content.removeAllViews()
        pageText.text = "SEARCHING PAGE $requestedPage"
        activeSearchTask = worker.submit {
            try {
                val address = if (query.isBlank()) {
                    CatalogUrlBuilder.build(selectedCategory?.second, selectedSort, requestedPage)
                } else {
                    val encoded = URLEncoder.encode(query, "UTF-8")
                    "https://xanimeporn.com/?s=$encoded&paged=$requestedPage"
                }
                val html = request(address)
                if (Thread.currentThread().isInterrupted || generation != searchGeneration) return@submit
                val parsed = SiteParser.parseSearch(Jsoup.parse(html, address))
                val uniqueResults = LinkedHashMap<String, SearchResult>()
                parsed.forEach { result ->
                    val key = animeName(result.title).lowercase().replace(Regex("[^a-z0-9]+"), " ").trim()
                    if (key.isNotBlank() && !uniqueResults.containsKey(key)) uniqueResults[key] = result
                }
                val existingNames = if (append) cachedAnimeGroups.map {
                    it.name.lowercase().replace(Regex("[^a-z0-9]+"), " ").trim()
                }.toSet() else emptySet()
                // Pagination can repeat posts between adjacent pages. Filter by
                // both normalized series title and canonical URL before loading
                // any detail pages, so duplicates do not trigger extra requests.
                val newUniqueResults = uniqueResults.filter { (key, result) ->
                    key !in existingNames && result.url !in seenResultUrls
                }
                val resultList = newUniqueResults.values.toList()
                val placeholders = resultList.map { result ->
                    AnimeGroup(
                        animeName(result.title), result, null, emptyList(),
                        Regex("(?i)\\\\[\\\\s*sub\\\\s*[- ]?\\\\s*eng\\\\s*]")
                            .containsMatchIn(result.title)
                    )
                }
                if (Thread.currentThread().isInterrupted || generation != searchGeneration) return@submit
                main.post {
                    if (generation != searchGeneration || isFinishing) return@post
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
                                if (generation != searchGeneration || isFinishing) return@post
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
                if (generation != searchGeneration) return@submit
                main.post {
                    if (generation != searchGeneration || isFinishing) return@post
                    busy = false
                    activeSearchTask = null
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
                        if (!append) seenResultUrls.clear()
                        parsed.forEach { seenResultUrls.add(it.url) }
                        if (append) allSearchResults.addAll(parsed.filter { it.url !in allSearchResults.map(SearchResult::url).toSet() })
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
                if (generation != searchGeneration) return@submit
                main.post {
                    if (generation != searchGeneration || isFinishing) return@post
                    busy = false
                    activeSearchTask = null
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
        if (showingFavorites) {
            content.addView(actionButton("COPY DOWNLOAD LINKS", true) {
                showBulkCopySeriesPicker(groups)
            }, LinearLayout.LayoutParams(-1, dp(46)).apply { bottomMargin = dp(8) })
            content.addView(label("Choose series and qualities, then save each link separately in your keyboard clipboard.", 11, muted, false).apply {
                setPadding(dp(2), 0, dp(2), dp(12))
            })
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
                    if (episodes.isNotEmpty()) "${episodes.size} Episodes"
                    else if (showingFavorites && group.details == null) "Loading episodes..."
                    else "Episode count unavailable",
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
                if (count > 0) "$count Episodes"
                else if (showingFavorites && group.details == null) "Loading episodes..."
                else "Episode count unavailable",
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
                            renderAnimeGroups(if (showingFavorites) favoriteAnimeGroups else cachedAnimeGroups)
                        } else {
                            expandedAnimeNames.add(groupKey)
                            renderAnimeGroups(if (showingFavorites) favoriteAnimeGroups else cachedAnimeGroups)
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
                .put("thumbnail", group.thumbnails.firstOrNull { it.isNotBlank() } ?: group.firstResult.thumbnail)
                .put("subEng", group.hasSubEng)
            entries.add(item.toString())
            Toast.makeText(this, "Added to Favorites", Toast.LENGTH_SHORT).show()
        }
        prefs.edit().putStringSet("favorite_series", entries).apply()
        if (showingFavorites) {
            favoriteLoadGeneration++
            favoriteAnimeGroups = loadFavoriteGroups()
            renderAnimeGroups(favoriteAnimeGroups)
            loadFavoriteGroupDetails(favoriteAnimeGroups)
        } else renderAnimeGroups(cachedAnimeGroups)
    }

    private fun loadFavoriteGroups(): List<AnimeGroup> =
        favoriteEntries().mapNotNull { encoded ->
            runCatching {
                val item = org.json.JSONObject(encoded)
                val title = item.optString("title").takeIf { it.isNotBlank() } ?: return@runCatching null
                val url = item.optString("url").takeIf { it.isNotBlank() } ?: return@runCatching null
                val thumbnail = item.optString("thumbnail")
                val result = SearchResult(title, url, thumbnail, "")
                val savedThumbnails = listOf(thumbnail).filter { it.isNotBlank() }
                AnimeGroup(title, result, null, savedThumbnails, item.optBoolean("subEng"))
            }.getOrNull()
        }.sortedBy { it.name.lowercase() }

    // Favorites persist only the series card data. Re-fetch the episode list
    // and each episode's own preview images when Favorites is opened.
    private fun loadFavoriteGroupDetails(groups: List<AnimeGroup>) {
        val generation = ++favoriteLoadGeneration
        if (groups.isEmpty()) return
        detailWorker.execute {
            val resolved = groups.map { group ->
                if (Thread.currentThread().isInterrupted) return@execute
                val cached = if (prefs.getBoolean("cache_enabled", true)) detailCache[group.firstResult.url] else null
                val data = cached ?: runCatching {
                    val html = request(group.firstResult.url)
                    val doc = Jsoup.parse(html, group.firstResult.url)
                    val details = SiteParser.parseEpisode(doc, group.firstResult.url)
                    details to extractEpisodeThumbnails(doc, details.episodes, group.firstResult)
                }.getOrElse { null to emptyList() }.also {
                    if (prefs.getBoolean("cache_enabled", true)) detailCache[group.firstResult.url] = it
                }
                group.copy(details = data.first, thumbnails = data.second)
            }
            main.post {
                if (isFinishing || !showingFavorites || generation != favoriteLoadGeneration) return@post
                favoriteAnimeGroups = resolved
                status.text = "Your saved series • episode details loaded"
                renderAnimeGroups(favoriteAnimeGroups)
                val expandedGroups = favoriteAnimeGroups.filter {
                    it.name.lowercase() in expandedAnimeNames && (it.details?.episodes?.size ?: 0) > 4
                }
                expandedGroups.forEach { fetchRemainingThumbnails(it) }
            }
        }
    }

    private fun showBulkCopySeriesPicker(groups: List<AnimeGroup>) {
        if (groups.isEmpty()) return
        val selected = BooleanArray(groups.size)
        AlertDialog.Builder(this)
            .setTitle("Choose favorite series")
            .setMultiChoiceItems(groups.map { it.name }.toTypedArray(), selected) { _, which, checked ->
                selected[which] = checked
            }
            .setPositiveButton("CHOOSE QUALITY") { _, _ ->
                val chosen = groups.filterIndexed { index, _ -> selected[index] }
                if (chosen.isEmpty()) {
                    Toast.makeText(this, "Select at least one series.", Toast.LENGTH_SHORT).show()
                } else showBulkCopyQualityPicker(chosen)
            }
            .setNegativeButton("CANCEL", null)
            .show()
    }

    private fun showBulkCopyQualityPicker(groups: List<AnimeGroup>) {
        val qualities = listOf("240p", "480p", "720p", "1080p")
        val labels = arrayOf("240p", "480p   🔥 HOT", "720p", "1080p")
        val selected = booleanArrayOf(false, true, false, false)
        AlertDialog.Builder(this)
            .setTitle("Choose download qualities")
            .setMultiChoiceItems(labels, selected) { _, which, checked ->
                selected[which] = checked
            }
            .setPositiveButton("START COPYING") { _, _ ->
                val chosenQualities = qualities.filterIndexed { index, _ -> selected[index] }
                if (chosenQualities.isEmpty()) {
                    Toast.makeText(this, "Choose at least one quality.", Toast.LENGTH_SHORT).show()
                } else startBulkCopy(groups, chosenQualities)
            }
            .setNegativeButton("BACK") { _, _ -> showBulkCopySeriesPicker(groups) }
            .show()
    }

    private fun startBulkCopy(groups: List<AnimeGroup>, qualities: List<String>) {
        cancelBulkCopy(false)
        val requestId = ++bulkCopyRequest
        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(8), dp(20), dp(8))
        }
        val prefixToggle = android.widget.CheckBox(this).apply {
            text = "Add prefix to each copied link"
            isChecked = true
        }
        val prefixInput = EditText(this).apply {
            setText(".download")
            hint = "Prefix"
            setSingleLine(true)
            isEnabled = true
        }
        prefixToggle.setOnCheckedChangeListener { _, checked -> prefixInput.isEnabled = checked }
        val progressText = label("Preparing episode links...", 12, muted, false)
        val progressBar = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            isIndeterminate = true
        }
        panel.addView(prefixToggle, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(4) })
        panel.addView(prefixInput, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(12) })
        panel.addView(progressText, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(12) })
        panel.addView(progressBar, LinearLayout.LayoutParams(-1, dp(5)))
        val dialog = AlertDialog.Builder(this)
            .setTitle("Bulk copy download links")
            .setView(panel)
            .setNegativeButton("CANCEL", null)
            .create()
        bulkCopyDialog = dialog
        dialog.setOnCancelListener {
            if (bulkCopyRequest == requestId) cancelBulkCopy(false)
        }
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_NEGATIVE)?.setOnClickListener { cancelBulkCopy() }
        }
        dialog.show()

        bulkCopyTask = detailWorker.submit {
            val collected = LinkedHashSet<String>()
            var episodesChecked = 0
            var episodesFailed = 0
            try {
                groups.forEachIndexed { seriesIndex, group ->
                    if (Thread.currentThread().isInterrupted) return@submit
                    val firstDetails = runCatching {
                        val html = request(group.firstResult.url)
                        SiteParser.parseEpisode(Jsoup.parse(html, group.firstResult.url), group.firstResult.url)
                    }.getOrNull()
                    if (firstDetails == null) {
                        episodesFailed++
                        main.post {
                            if (bulkCopyRequest == requestId && dialog.isShowing) {
                                progressText.text = "Could not open " + group.name + "; continuing..."
                            }
                        }
                        return@forEachIndexed
                    }
                    val episodes = firstDetails.episodes.ifEmpty {
                        listOf(EpisodeLink(firstDetails.title, firstDetails.url, 0))
                    }
                    episodes.forEachIndexed { episodeIndex, episode ->
                        if (Thread.currentThread().isInterrupted) return@submit
                        val details = if (episode.url.trimEnd('/') == firstDetails.url.trimEnd('/')) {
                            firstDetails
                        } else runCatching {
                            val html = request(episode.url)
                            SiteParser.parseEpisode(Jsoup.parse(html, episode.url), episode.url)
                        }.getOrNull()
                        episodesChecked++
                        if (details == null) {
                            episodesFailed++
                        } else {
                            qualities.forEach { quality ->
                                details.qualities[quality]?.takeIf {
                                    it.startsWith("http://") || it.startsWith("https://")
                                }?.let { collected.add(it) }
                            }
                        }
                        val checked = episodesChecked
                        main.post {
                            if (bulkCopyRequest == requestId && dialog.isShowing) {
                                progressText.text = group.name + " • Series " + (seriesIndex + 1) + "/" + groups.size +
                                    " • Episode " + (episodeIndex + 1) + "/" + episodes.size + " • " + checked + " checked"
                            }
                        }
                    }
                }
            } catch (_: Exception) {
                // Keep links already collected if one page fails.
            }
            if (Thread.currentThread().isInterrupted) return@submit
            val links = collected.toList()
            main.post {
                if (bulkCopyRequest != requestId || !dialog.isShowing) return@post
                if (links.isEmpty()) {
                    progressBar.visibility = View.GONE
                    progressText.text = "No matching download links found. Checked " + episodesChecked +
                        " episodes; " + episodesFailed + " pages failed."
                    dialog.getButton(AlertDialog.BUTTON_NEGATIVE)?.text = "CLOSE"
                    dialog.getButton(AlertDialog.BUTTON_NEGATIVE)?.setOnClickListener { dialog.dismiss() }
                    return@post
                }
                progressBar.isIndeterminate = false
                progressBar.max = links.size
                progressBar.progress = 0
                val prefix = if (prefixToggle.isChecked) prefixInput.text.toString().trim() else ""
                val clipboardEntries = if (prefix.isEmpty()) links else links.map { "$prefix $it" }
                progressText.text = "Found " + links.size + " unique links. Copying one at a time..."
                copyBulkLinksOneByOne(clipboardEntries, 0, requestId, dialog, progressText, progressBar, episodesFailed)
            }
        }
    }

    private fun copyBulkLinksOneByOne(
        links: List<String>,
        index: Int,
        requestId: Int,
        dialog: AlertDialog,
        progressText: TextView,
        progressBar: ProgressBar,
        failedPages: Int
    ) {
        if (bulkCopyRequest != requestId || !dialog.isShowing) return
        if (index >= links.size) {
            bulkCopyRunnable = null
            progressText.text = "Done! Copied " + links.size + " separate clipboard entries." +
                (if (failedPages > 0) " " + failedPages + " pages could not be read." else "") +
                " Keep Facemoji's clipboard history enabled to access them."
            dialog.getButton(AlertDialog.BUTTON_NEGATIVE)?.text = "DONE"
            dialog.getButton(AlertDialog.BUTTON_NEGATIVE)?.setOnClickListener { dialog.dismiss() }
            Toast.makeText(this, links.size.toString() + " links copied separately", Toast.LENGTH_LONG).show()
            return
        }
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("XAnime link " + (index + 1), links[index]))
        progressBar.progress = index + 1
        progressText.text = "Copied " + (index + 1) + " / " + links.size + " • Keep the app open"
        val next = Runnable {
            copyBulkLinksOneByOne(links, index + 1, requestId, dialog, progressText, progressBar, failedPages)
        }
        bulkCopyRunnable = next
        // Give Facemoji and other keyboard clipboard listeners time to retain this entry.
        main.postDelayed(next, 1200L)
    }

    private fun cancelBulkCopy(dismissDialog: Boolean = true) {
        bulkCopyRequest++
        bulkCopyTask?.cancel(true)
        bulkCopyTask = null
        bulkCopyRunnable?.let { main.removeCallbacks(it) }
        bulkCopyRunnable = null
        val dialog = bulkCopyDialog
        bulkCopyDialog = null
        if (dismissDialog && dialog?.isShowing == true) dialog.dismiss()
    }

    private fun fetchRemainingThumbnails(group: AnimeGroup) {
        if (prefs.getBoolean("data_saver_mode", false)) return
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
                if (showingFavorites) {
                    favoriteAnimeGroups = favoriteAnimeGroups.map {
                        if (it.name.lowercase() == key) it.copy(thumbnails = thumbnails) else it
                    }
                } else {
                    cachedAnimeGroups = cachedAnimeGroups.map {
                        if (it.name.lowercase() == key) it.copy(thumbnails = thumbnails) else it
                    }
                }
                if (expandedAnimeNames.contains(key)) {
                    val y = scroll.scrollY
                    renderAnimeGroups(if (showingFavorites) favoriteAnimeGroups else cachedAnimeGroups)
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
        target.contentDescription = "Thumbnail waiting to load"
        val cacheEnabled = getSharedPreferences("xanime", MODE_PRIVATE).getBoolean("cache_enabled", true)
        if (cacheEnabled) imageCache.get(url)?.let { bitmap ->
            target.animate().cancel()
            stopThumbnailShimmer(target)
            target.setImageBitmap(bitmap)
            target.contentDescription = "Episode thumbnail"
            target.setOnClickListener(null)
            return
        }

        if (!isViewVisible(target)) {
            if (target.getTag(R.id.tag_load_image_visibility_url) != url) {
                target.setTag(R.id.tag_load_image_visibility_url, url)
                val observer = object : android.view.ViewTreeObserver.OnGlobalLayoutListener,
                    android.view.ViewTreeObserver.OnScrollChangedListener {
                    override fun onGlobalLayout() = checkVisibility()
                    override fun onScrollChanged() = checkVisibility()
                    private fun checkVisibility() {
                        if (!target.isAttachedToWindow) return
                        if (isViewVisible(target)) {
                            val tree = target.viewTreeObserver
                            if (tree.isAlive) {
                                tree.removeOnGlobalLayoutListener(this)
                                tree.removeOnScrollChangedListener(this)
                            }
                            target.setTag(R.id.tag_load_image_visibility_url, null)
                            loadImage(url, target)
                        }
                    }
                }
                target.viewTreeObserver.addOnGlobalLayoutListener(observer)
                target.viewTreeObserver.addOnScrollChangedListener(observer)
            }
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
                            inSampleSize = when {
                                prefs.getBoolean("data_saver_mode", false) -> 4
                                prefs.getBoolean("high_quality_thumbnails", false) -> 1
                                else -> 2
                            }
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

    private fun isViewVisible(view: View): Boolean {
        if (!view.isShown || view.width <= 0 || view.height <= 0) return false
        val visible = android.graphics.Rect()
        return view.getGlobalVisibleRect(visible) && visible.width() > 0 && visible.height() > 0
    }

    private fun loadVisibleImages(parent: View) {
        if (parent is ImageView) {
            val url = parent.tag as? String
            if (!url.isNullOrBlank() && parent.drawable == null && isViewVisible(parent)) loadImage(url, parent)
            return
        }
        if (parent is ViewGroup) {
            for (index in 0 until parent.childCount) loadVisibleImages(parent.getChildAt(index))
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
        option("Data Saver (smaller thumbnails, no extra preloading)", "data_saver_mode", false)
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


    private data class HitomiGalleryResult(
        val id: String,
        val title: String,
        val url: String
    )

    // Native HitomiLA search screen. Search is restricted to gallery titles.
    private fun showHitomiLaHome() {
        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }
        panel.addView(label("HITOMI.LA", 18, white, true).apply {
            setPadding(0, dp(16), 0, dp(5))
        })
        panel.addView(label("SEARCH GALLERIES BY TITLE", 9, muted, true).apply {
            letterSpacing = .1f
            setPadding(0, 0, 0, dp(9))
        })

        val input = EditText(this).apply {
            hint = "Enter a gallery title..."
            setHintTextColor(muted)
            setTextColor(white)
            textSize = 14f
            isSingleLine = true
            imeOptions = EditorInfo.IME_ACTION_SEARCH
            setPadding(dp(13), 0, dp(10), 0)
            background = ripple(glassDrawable(14))
        }
        val statusText = TextView(this).apply {
            text = "Search HitomiLA galleries by name."
            textSize = 12f
            setTextColor(muted)
            setPadding(dp(2), dp(12), dp(2), dp(8))
        }
        val results = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }
        val searchButton = actionButton("SEARCH ↗", true) {
            runHitomiTitleSearch(input.text.toString(), results, statusText)
        }
        val searchRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        searchRow.addView(input, LinearLayout.LayoutParams(0, dp(46), 1f).apply {
            marginEnd = dp(7)
        })
        searchRow.addView(searchButton, LinearLayout.LayoutParams(dp(96), dp(46)))
        panel.addView(searchRow, LinearLayout.LayoutParams(-1, -2))
        input.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                runHitomiTitleSearch(input.text.toString(), results, statusText)
                true
            } else false
        }
        panel.addView(statusText, LinearLayout.LayoutParams(-1, -2))
        panel.addView(results, LinearLayout.LayoutParams(-1, -2))
        pageContent.addView(panel, LinearLayout.LayoutParams(-1, -2).apply {
            topMargin = dp(10)
        })
    }

    private fun runHitomiTitleSearch(
        rawQuery: String,
        resultsContainer: LinearLayout,
        statusText: TextView
    ) {
        val term = rawQuery.trim()
        if (term.isEmpty()) {
            statusText.text = "Enter a gallery title to search."
            resultsContainer.removeAllViews()
            return
        }

        val requestId = ++hitomiSearchRequestId
        resultsContainer.removeAllViews()
        statusText.text = "Searching HitomiLA..."
        worker.execute {
            var connection: HttpURLConnection? = null
            try {
                val expression = "title:$term"
                val encoded = URLEncoder.encode(expression, "UTF-8")
                val url = URL("https://hitomi.la/search.html?$encoded")
                connection = (url.openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    connectTimeout = 15000
                    readTimeout = 20000
                    setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 Chrome/120 Mobile Safari/537.36")
                    setRequestProperty("Accept", "text/html,application/xhtml+xml")
                    setRequestProperty("Referer", "https://hitomi.la/")
                }
                val code = connection.responseCode
                if (code !in 200..299) throw IllegalStateException("HitomiLA returned HTTP $code")
                val html = connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
                val document = Jsoup.parse(html, "https://hitomi.la/")
                val cards = document.select(".gallery-content > div")
                val found = mutableListOf<HitomiGalleryResult>()
                for (card in cards) {
                    val link = card.selectFirst("h1 a[href*=/galleries/]")
                        ?: card.selectFirst("a[href*=/galleries/]")
                        ?: continue
                    val href = link.attr("href")
                    val id = Regex("/galleries/(\\d+)").find(href)?.groupValues?.getOrNull(1)
                        ?: continue
                    val title = link.text().trim()
                    if (title.isNotEmpty() && title.contains(term, ignoreCase = true)) {
                        found.add(HitomiGalleryResult(id, title, "https://hitomi.la/galleries/$id.html"))
                    }
                }
                runOnUiThread {
                    if (requestId != hitomiSearchRequestId || isFinishing) return@runOnUiThread
                    resultsContainer.removeAllViews()
                    if (found.isEmpty()) {
                        statusText.text = "No matching gallery titles found."
                    } else {
                        statusText.text = "Found ${found.size} matching galleries."
                        found.distinctBy { it.id }.take(30).forEach { item ->
                            val card = LinearLayout(this).apply {
                                orientation = LinearLayout.VERTICAL
                                setPadding(dp(14), dp(12), dp(14), dp(12))
                                background = glassDrawable(14)
                            }
                            card.addView(TextView(this).apply {
                                text = item.title
                                textSize = 13f
                                setTextColor(white)
                                typeface = Typeface.DEFAULT_BOLD
                            })
                            card.addView(TextView(this).apply {
                                text = "Gallery #${item.id}"
                                textSize = 10f
                                setTextColor(muted)
                                setPadding(0, dp(5), 0, 0)
                            })
                            resultsContainer.addView(card, LinearLayout.LayoutParams(-1, -2).apply {
                                bottomMargin = dp(8)
                            })
                        }
                    }
                }
            } catch (error: Exception) {
                runOnUiThread {
                    if (requestId != hitomiSearchRequestId || isFinishing) return@runOnUiThread
                    statusText.text = "Search failed: ${error.message ?: "Unable to reach HitomiLA"}"
                }
            } finally {
                connection?.disconnect()
            }
        }
    }

    private fun addWebsiteTab(
        container: LinearLayout,
        title: String,
        tags: List<String>,
        selected: Boolean,
        endMargin: Int = 0,
        footerActionLabel: String? = null,
        footerAction: (() -> Unit)? = null,
        action: () -> Unit
    ) {
        val column = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
        }
        column.addView(actionButton(title, selected, action),
            LinearLayout.LayoutParams(-1, dp(43)))
        if (tags.isNotEmpty()) {
            column.addView(TextView(this).apply {
                text = tags.joinToString("  ·  ")
                textSize = 8f
                setTextColor(muted)
                gravity = Gravity.CENTER
                maxLines = 2
                setLineSpacing(dp(1).toFloat(), 1f)
                setPadding(dp(3), dp(7), dp(3), dp(6))
                background = glassDrawable(10)
            }, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(5) })
        }
        if (footerActionLabel != null && footerAction != null) {
            column.addView(actionButton(footerActionLabel, true, footerAction),
                LinearLayout.LayoutParams(-1, dp(40)).apply { topMargin = dp(8) })
        }
        container.addView(column, LinearLayout.LayoutParams(0, -2, 1f).apply {
            if (endMargin > 0) marginEnd = dp(endMargin)
        })
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
