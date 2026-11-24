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
import android.widget.FrameLayout
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
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
    private val bgColor: Int get() = Color.rgb(3, 2, 10)
    private val surface: Int get() = if (amoled) Color.rgb(8, 5, 18) else Color.rgb(13, 7, 27)
    private val surface2: Int get() = if (amoled) Color.rgb(18, 11, 35) else Color.rgb(24, 13, 45)
    private val accent: Int get() = when (prefs.getString("accent_color", "Purple")) {
        "Pink", "Neon Rose" -> Color.rgb(255, 76, 164)
        "Cyan", "Ice Cyan" -> Color.rgb(0, 205, 225)
        "Blue", "Electric Blue" -> Color.rgb(75, 130, 255)
        "Green", "Emerald" -> Color.rgb(45, 210, 145)
        "Magenta" -> Color.rgb(225, 55, 220)
        "Amber" -> Color.rgb(255, 170, 55)
        "Crimson" -> Color.rgb(245, 65, 95)
        "Lavender" -> Color.rgb(178, 155, 255)
        else -> Color.rgb(174, 65, 255)
    }
    private val pink = Color.rgb(255, 76, 164)
    private val muted = Color.rgb(177, 166, 205)
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
    private var libraryTabTransitionPending = false
    private var libraryTabTransitionDirection = 1f
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
    private var showingJav = false
    private var showingReal = false
    private var javQuery = ""
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
            setPadding(dp(16), dp(18), dp(16), dp(14))
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
        val header = LinearLayout(this).apply {
            gravity = Gravity.CENTER_VERTICAL
            orientation = LinearLayout.HORIZONTAL
            setPadding(dp(2), dp(2), dp(2), dp(8))
        }
        val titles = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        titles.addView(label("XAnime", 25, white, true))
        titles.addView(label("✦  DISCOVER  •  WATCH  •  ENJOY", 9, muted, true).apply {
            letterSpacing = .15f
            setPadding(dp(2), dp(4), 0, 0)
        })
        titles.addView(label("YOUR PERSONAL MEDIA UNIVERSE", 8, accent, true).apply {
            letterSpacing = .18f
            setPadding(dp(2), dp(5), 0, 0)
        })
        header.addView(titles, LinearLayout.LayoutParams(0, -2, 1f))
        if (!showingWatchAnimeHentai && !showingComics && !showingJav && !showingReal) {
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

        // Top-level library switcher: source tabs stay grouped under Hentai.
        val libraryTabs = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(5), dp(5), dp(5), dp(5))
            background = glassDrawable(20)
        }
        val currentLibraryTab = when {
            showingComics -> 1
            showingJav -> 2
            showingReal -> 3
            else -> 0
        }
        fun switchLibraryTab(target: Int) {
            if (target == currentLibraryTab) return
            libraryTabTransitionDirection = if (target > currentLibraryTab) 1f else -1f
            libraryTabTransitionPending = true
            showingComics = target == 1
            showingJav = target == 2
            showingReal = target == 3
            showHome()
        }
        libraryTabs.addView(actionButton("Hentai", currentLibraryTab == 0) {
            switchLibraryTab(0)
        }, LinearLayout.LayoutParams(0, dp(42), 1f).apply { marginEnd = dp(5) })
        libraryTabs.addView(actionButton("Comics", currentLibraryTab == 1) {
            switchLibraryTab(1)
        }, LinearLayout.LayoutParams(0, dp(42), 1f).apply { marginEnd = dp(5) })
        libraryTabs.addView(actionButton("Jav", currentLibraryTab == 2) {
            switchLibraryTab(2)
        }, LinearLayout.LayoutParams(0, dp(42), 1f).apply { marginEnd = dp(5) })
        libraryTabs.addView(actionButton("Real", currentLibraryTab == 3) {
            switchLibraryTab(3)
        }, LinearLayout.LayoutParams(0, dp(42), 1f))
        pageContent.addView(libraryTabs, LinearLayout.LayoutParams(-1, dp(54)).apply {
            topMargin = dp(13)
            bottomMargin = dp(4)
        })

        if (showingReal) {
            pageContent.addView(label("REAL", 22, white, true).apply {
                setPadding(0, dp(20), 0, dp(8))
            })
            pageContent.addView(infoCard("Browse supported adult-video platforms in XAnime. Use only lawful content featuring consenting adults."),
                LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(8) })

            val realSites = listOf("18porn" to "https://18porn.sex/",
"18tokyo" to "https://18tokyo.com/",
"3pornstarmovies" to "https://3pornstarmovies.com/",
"429men" to "https://429men.com/",
"4wank" to "https://4wank.com/",
"7dakevo11" to "https://7dakevo11.info/",
"Aagmaaluncut" to "https://aagmaaluncut.com/",
"Absoluporn" to "https://absoluporn.com/",
"Acgmhn" to "https://acgmhn.com/",
"Actressx" to "https://actressx.com/",
"Adultprime" to "https://adultprime.com/",
"Aicelebs" to "https://aicelebs.st/",
"Aiexotic" to "https://aiexotic.com/",
"Allboner" to "https://allboner.com/",
"Amateurdoporn" to "https://amateurdoporn.com/",
"Analegg" to "https://analegg.com/",
"Analporn" to "https://analporn.tv/",
"Analpornxl" to "https://analpornxl.com/",
"Analvids" to "https://analvids.com/",
"Animalincum" to "https://animalincum.com/",
"Animehentaixhub" to "https://animehentaixhub.com/",
"Antarvasna3" to "https://antarvasna3.com/",
"Anygay" to "https://anygay.com/",
"Anysex" to "https://anysex.com/",
"Anyshemale" to "https://anyshemale.com/",
"Apclips" to "https://apclips.com/",
"Aporntv" to "https://aporntv.com/",
"Arabsex" to "https://arabsex.plus/",
"Asian-girl" to "https://asian-girl.net/",
"Asianmilfhub" to "https://asianmilfhub.com/",
"Asianviralhub" to "https://asianviralhub.com/",
"Asianwifeporn" to "https://asianwifeporn.com/",
"Aslimms" to "https://aslimms.com/",
"Asmrhoney" to "https://asmrhoney.com/",
"Asmrx" to "https://asmrx.me/",
"Avjoy" to "https://avjoy.me/",
"B4bz" to "https://b4bz.com/",
"Babepump" to "https://babepump.com/",
"Baddiesonly" to "https://baddiesonly.tv/",
"Balkanjizz" to "https://balkanjizz.com/",
"Bigassmonster" to "https://bigassmonster.com/",
"Bigbuttholes" to "https://bigbuttholes.com/",
"Bigfuck" to "https://bigfuck.tv/",
"Bigwank" to "https://bigwank.com/",
"Bingato" to "https://bingato.com/",
"Bitchwithdick" to "https://bitchwithdick.com/",
"Blacktowhite" to "https://blacktowhite.net/",
"Blowjobit" to "https://blowjobit.com/",
"Blowjobs" to "https://blowjobs.pro/",
"Bodgirls" to "https://bodgirls.com/",
"Bokepking" to "https://bokepking.cam/",
"Bootyexpo" to "https://bootyexpo.net/",
"Bqsex" to "https://bqsex.com/",
"Bravoporn" to "https://bravoporn.com/",
"Bravoteens" to "https://bravoteens.com/",
"Brutalporn" to "https://brutalporn.me/",
"Brwnze" to "https://brwnze.com/",
"Bsex" to "https://bsex.tv/",
"Bubblecum" to "https://bubblecum.com/",
"Bubblecum" to "https://bubblecum.to/",
"Bukkake" to "https://bukkake.to/",
"Cake" to "https://cake.ski/",
"Camsmut" to "https://camsmut.com/",
"Cartoonporn" to "https://cartoonporn.to/",
"Celebfakes" to "https://celebfakes.site/",
"Celebjihad" to "https://celebjihad.com/",
"Celebritymovieblog" to "https://celebritymovieblog.com/",
"Cfnmtoob" to "https://cfnmtoob.com/",
"Checkporn" to "https://checkporn.cc/",
"Chezcathy" to "https://chezcathy.com/",
"Clips69" to "https://clips69.com/",
"Cloudbate" to "https://cloudbate.com/",
"Clubeadulto" to "https://clubeadulto.net/",
"Cluset" to "https://cluset.com/",
"Cosxplay" to "https://cosxplay.com/",
"Createporn" to "https://createporn.com/",
"Cuckoldplacetube" to "https://cuckoldplacetube.com/",
"Cumzie" to "https://cumzie.com/",
"Cxfakes" to "https://cxfakes.com/",
"Daftporn" to "https://daftporn.com/",
"Daftsex" to "https://daftsex.eu/",
"Dailymotion" to "https://dailymotion.com/",
"Damnvids" to "https://damnvids.com/",
"Darkero" to "https://darkero.com/",
"Darknaija" to "https://darknaija.com/",
"Darknessporn" to "https://darknessporn.com/",
"Desi49" to "https://desi49.rent/",
"Desi49" to "https://desi49.to/",
"Desibf.com" to "https://desibf.com.co/",
"Desibfhd" to "https://desibfhd.com/",
"Desidekho2" to "https://desidekho2.watch/",
"Desihub2" to "https://desihub2.com/",
"Desikahani2" to "https://desikahani2.net/",
"Desileak49" to "https://desileak49.com/",
"Desimmsxxx2" to "https://desimmsxxx2.com/",
"Desinx" to "https://desinx.com/",
"Desiporn" to "https://desiporn.one/",
"Desipornvideos" to "https://desipornvideos.cc/",
"Desisex" to "https://desisex.xxx/",
"Desitales2" to "https://desitales2.com/",
"Desix" to "https://desix.to/",
"Desixxx" to "https://desixxx.blog/",
"Desixxx" to "https://desixxx.love/",
"Devki" to "https://devki.net/",
"Dirtydoglinks" to "https://dirtydoglinks.com/",
"Dirtyshack" to "https://dirtyshack.com/",
"Djatoya" to "https://djatoya.com/",
"Djav" to "https://djav.org/",
"Dogcumshot" to "https://dogcumshot.net/",
"Dogspics" to "https://dogspics.net/",
"Dp-vids" to "https://dp-vids.com/",
"Dreamydolls" to "https://dreamydolls.com/",
"E621" to "https://e621.net/",
"Ebony8" to "https://ebony8.com/",
"Ebooxa" to "https://ebooxa.com/",
"Ebuchka" to "https://ebuchka.top/",
"Efukt" to "https://efukt.com/",
"Elitebabes" to "https://elitebabes.com/",
"Empflix" to "https://empflix.com/",
"Empressleak" to "https://empressleak.xyz/",
"Eromeporn" to "https://eromeporn.com/",
"Eropornx" to "https://eropornx.com/",
"Eroprofile" to "https://eroprofile.com/",
"Erotik" to "https://erotik.com/",
"Everydayporn" to "https://everydayporn.com/",
"Exposedtostrangers" to "https://exposedtostrangers.com/",
"Faapy" to "https://faapy.com/",
"Fansporno" to "https://fansporno.com/",
"Faperoni" to "https://faperoni.com/",
"Fapforher" to "https://fapforher.com/",
"Fapgoat" to "https://fapgoat.com/",
"Fapguru" to "https://fapguru.com/",
"Fapix" to "https://fapix.info/",
"Faplane" to "https://faplane.com/",
"Fapnado" to "https://fapnado.xxx/",
"Fapnow" to "https://fapnow.xxx/",
"Faptor" to "https://faptor.com/",
"Fauxporn" to "https://fauxporn.com/",
"Feet9" to "https://feet9.com/",
"Femdomtb" to "https://femdomtb.com/",
"Femdomvc" to "https://femdomvc.com/",
"Fetishburg" to "https://fetishburg.com/",
"Fetishshrine" to "https://fetishshrine.com/",
"Fhaccess" to "https://fhaccess.com/",
"Fhchannel" to "https://fhchannel.com/",
"Fhofficial" to "https://fhofficial.com/",
"Fhplatinum" to "https://fhplatinum.com/",
"Filmnudes" to "https://filmnudes.com/",
"Filmyfantasy" to "https://filmyfantasy.com/",
"Fngml" to "https://fngml.com/",
"Footgoon" to "https://footgoon.com/",
"Footstockings" to "https://footstockings.com/",
"Forcedsex" to "https://forcedsex.org/",
"Forcedsextube" to "https://forcedsextube.net/",
"Freemasala" to "https://freemasala.com/",
"Freeporn" to "https://freeporn.ooo/",
"Freepublicporn" to "https://freepublicporn.com/",
"Freesexvideos" to "https://freesexvideos.xxx/",
"Freshporno" to "https://freshporno.org/",
"Fsi52" to "https://fsi52.com/",
"Fsiblog.co" to "https://fsiblog.co.im/",
"Fsiblog" to "https://fsiblog.tube/",
"Fsiblogmms" to "https://fsiblogmms.com/",
"Fsiblogxx" to "https://fsiblogxx.com/",
"Fucker" to "https://fucker.com/",
"Fullfamilyincest" to "https://fullfamilyincest.com/",
"Fullhindibfvideos" to "https://fullhindibfvideos.com/",
"Fyxxr" to "https://fyxxr.to/",
"Gachalifeporn" to "https://gachalifeporn.com/",
"Gamebus001" to "https://gamebus001.cc/",
"Gay" to "https://gay.bingo/",
"Gay0day" to "https://gay0day.com/",
"Gay4" to "https://gay4.net/",
"Gayck" to "https://gayck.com/",
"Gayjack" to "https://gayjack.com/",
"Gayjavu" to "https://gayjavu.com/",
"Gaymenring" to "https://gaymenring.com/",
"Gaysbeinggay" to "https://gaysbeinggay.com/",
"Gaysexfarm" to "https://gaysexfarm.com/",
"Gelbooru" to "https://gelbooru.com/",
"Giantess" to "https://giantess.porn/",
"Girlspee" to "https://girlspee.video/",
"Green" to "https://green.porn/",
"Hanime1" to "https://hanime1.me/",
"Hard3r" to "https://hard3r.com/",
"Hdporn" to "https://hdporn.gg/",
"Heavy-r" to "https://heavy-r.com/",
"Heissepornos" to "https://heissepornos.net/",
"Hellmoms" to "https://hellmoms.com/",
"Hello" to "https://hello.porn/",
"Hentai-moon" to "https://hentai-moon.com/",
"Hentai4k" to "https://hentai4k.com/",
"Hentai4porn" to "https://hentai4porn.com/",
"Hentaini" to "https://hentaini.com/",
"Hentaiplay" to "https://hentaiplay.net/",
"Hentaismile" to "https://hentaismile.com/",
"Hentaisx" to "https://hentaisx.com/",
"Hentaitoon" to "https://hentaitoon.tv/",
"Heroine-xxx" to "https://heroine-xxx.com/",
"Heroinex" to "https://heroinex.com/",
"Hiden" to "https://hiden.live/",
"Hindibfxxx2" to "https://hindibfxxx2.com/",
"Hindichudaivideos" to "https://hindichudaivideos.com/",
"Hoes" to "https://hoes.tube/",
"Homemoviestube" to "https://homemoviestube.com/",
"Hornysharks" to "https://hornysharks.com/",
"Hotmarathistories" to "https://hotmarathistories.com/",
"Hotmovs" to "https://hotmovs.tube/",
"Hotpic" to "https://hotpic.cc/",
"Hotpic2" to "https://hotpic2.cc/",
"Hotpic3" to "https://hotpic3.cc/",
"Hotscope" to "https://hotscope.tv/",
"Hotstash" to "https://hotstash.net/",
"Hotxv" to "https://hotxv.com/",
"Houseofyre" to "https://houseofyre.com/",
"Hqbang" to "https://hqbang.com/",
"Hugedickz" to "https://hugedickz.com/",
"Hugewangs" to "https://hugewangs.com/",
"Huyamba" to "https://huyamba.name/",
"Hypnosis" to "https://hypnosis.porn/",
"Iamsissy" to "https://iamsissy.com/",
"Icegay" to "https://icegay.tv/",
"Icegaytube" to "https://icegaytube.tv/",
"Ifuckedyourgf" to "https://ifuckedyourgf.com/",
"Incestflix.com" to "https://incestflix.com.co/",
"Indian-porn" to "https://indian-porn.pro/",
"Indianbfclips" to "https://indianbfclips.com/",
"Indianbfvideos" to "https://indianbfvideos.com/",
"Indiangaysite2" to "https://indiangaysite2.com/",
"Indiaxnxxx" to "https://indiaxnxxx.com/",
"Itpornit" to "https://itpornit.com/",
"Itsmyfetish" to "https://itsmyfetish.com/",
"Japan-whores" to "https://japan-whores.com/",
"Japteenx" to "https://japteenx.com/",
"Jilhub" to "https://jilhub.org/",
"Jizzberry" to "https://jizzberry.com/",
"K9vidz" to "https://k9vidz.com/",
"Kaamdesi" to "https://kaamdesi.com/",
"Kama" to "https://kama.tube/",
"Kamakathalu" to "https://kamakathalu.com/",
"Kamareels2" to "https://kamareels2.com/",
"Kambimalayalamkathakal" to "https://kambimalayalamkathakal.com/",
"Kandavideos" to "https://kandavideos.com/",
"Kandavideos" to "https://kandavideos.net/",
"Katestube" to "https://katestube.com/",
"Katitube" to "https://katitube.com/",
"Keekass" to "https://keekass.com/",
"Khafan" to "https://khafan.net/",
"Kinkytom" to "https://kinkytom.com/",
"Knackvideos" to "https://knackvideos.com/",
"Koselarab" to "https://koselarab.rent/",
"Kwiky" to "https://kwiky.com/",
"Leak-xxx" to "https://leak-xxx.com/",
"Leaknudes" to "https://leaknudes.com/",
"Leaktube" to "https://leaktube.org/",
"Leakxporn" to "https://leakxporn.com/",
"Leakyourporn" to "https://leakyourporn.com/",
"Lechetube" to "https://lechetube.com/",
"Lesb8" to "https://lesb8.com/",
"Lesbianbliss" to "https://lesbianbliss.com/",
"Lesbianporn8" to "https://lesbianporn8.net/",
"Lesbosland" to "https://lesbosland.com/",
"Leslez" to "https://leslez.com/",
"Libidoxxx" to "https://libidoxxx.com/",
"Ligadasnovinhas" to "https://ligadasnovinhas.com/",
"Luxuretv" to "https://luxuretv.com/",
"Madnessporn" to "https://madnessporn.com/",
"Madurascalientesx" to "https://madurascalientesx.com/",
"Manporn" to "https://manporn.xxx/",
"Many" to "https://many.xxx/",
"Mastibaba2" to "https://mastibaba2.com/",
"Mature" to "https://mature.nl/",
"Matureclub" to "https://matureclub.com/",
"Messytube" to "https://messytube.com/",
"Metadoll" to "https://metadoll.to/",
"Milkporntube" to "https://milkporntube.com/",
"Mixedwrestling" to "https://mixedwrestling.video/",
"Mmsvibex" to "https://mmsvibex.fit/",
"Mmsvibex" to "https://mmsvibex.life/",
"Mmtube" to "https://mmtube.net/",
"Moannest" to "https://moannest.com/",
"Modelsgonebad" to "https://modelsgonebad.com/",
"Modporn" to "https://modporn.com/",
"Momslust" to "https://momslust.com/",
"Multporn" to "https://multporn.net/",
"Mustvideos" to "https://mustvideos.com/",
"Mydesii" to "https://mydesii.com/",
"Mydesixxx4" to "https://mydesixxx4.com/",
"Myimg" to "https://myimg.tv/",
"Mymusclevideo" to "https://mymusclevideo.com/",
"Mzansipeaches" to "https://mzansipeaches.com/",
"Mzansipornvideos" to "https://mzansipornvideos.com/",
"Naijafap" to "https://naijafap.com/",
"Naijared" to "https://naijared.com/",
"Nakedgirls" to "https://nakedgirls.mobi/",
"Napiszex" to "https://napiszex.com/",
"Naughtymachinima" to "https://naughtymachinima.com/",
"Nebyda" to "https://nebyda.com/",
"Nekyda" to "https://nekyda.com/",
"Nemyda" to "https://nemyda.com/",
"Nenyda" to "https://nenyda.com/",
"Nepaliporn" to "https://nepaliporn.video/",
"Neryda" to "https://neryda.com/",
"Nevyda" to "https://nevyda.com/",
"Newhomep" to "https://newhomep.com/",
"Nikporn" to "https://nikporn.com/",
"Notfans" to "https://notfans.net/",
"Nudebase" to "https://nudebase.com/",
"Nudecelebgifs" to "https://nudecelebgifs.com/",
"Nudeindians2" to "https://nudeindians2.net/",
"Nudeleted" to "https://nudeleted.com/",
"Nuditok" to "https://nuditok.com/",
"Nxxn" to "https://nxxn.live/",
"Ohentai" to "https://ohentai.org/",
"Omeglevault" to "https://omeglevault.com/",
"Onlinestars" to "https://onlinestars.net/",
"Onlygayvideo" to "https://onlygayvideo.com/",
"Onlyhentaistuff" to "https://onlyhentaistuff.com/",
"Pervertium" to "https://pervertium.com/",
"Perverttube" to "https://perverttube.com/",
"Piggiesgetfed" to "https://piggiesgetfed.com/",
"Pimpbunnyhub" to "https://pimpbunnyhub.com/",
"Pisshamster" to "https://pisshamster.com/",
"Pissvids" to "https://pissvids.com/",
"Pompomhub" to "https://pompomhub.com/",
"Poop" to "https://poop.rip/",
"Poopeegirls" to "https://poopeegirls.com/",
"Porcore" to "https://porcore.com/",
"Porn-soul" to "https://porn-soul.org/",
"Porn300" to "https://porn300.com/",
"Porn3dx" to "https://porn3dx.com/",
"Pornbado" to "https://pornbado.com/",
"Porndaft" to "https://porndaft.com/",
"Porndiff" to "https://porndiff.com/",
"Porndr" to "https://porndr.tv/",
"Porndroids" to "https://porndroids.com/",
"Porngem" to "https://porngem.com/",
"Pornhd" to "https://pornhd.cc/",
"Pornissimo" to "https://pornissimo.org/",
"Pornkut" to "https://pornkut.com/",
"Porno-cum" to "https://porno-cum.com/",
"Porno-klipovi" to "https://porno-klipovi.com/",
"Pornolandia" to "https://pornolandia.xxx/",
"Pornoperso" to "https://pornoperso.com/",
"Pornoreino" to "https://pornoreino.com/",
"Pornouzz" to "https://pornouzz.com/",
"Pornox" to "https://pornox.hu/",
"Pornozinho" to "https://pornozinho.xxx/",
"Pornreels" to "https://pornreels.tv/",
"Pornstar-scenes" to "https://pornstar-scenes.com/",
"Pornstars" to "https://pornstars.tube/",
"Porntb" to "https://porntb.com/",
"Porntotal" to "https://porntotal.com/",
"Pornwex" to "https://pornwex.tv/",
"Pornx" to "https://pornx.fr/",
"Povpoof" to "https://povpoof.com/",
"Premiumfacials" to "https://premiumfacials.com/",
"Privatehomeclips" to "https://privatehomeclips.com/",
"Prostoporno" to "https://prostoporno.dev/",
"Public-porno" to "https://public-porno.com/",
"Publicsexhub" to "https://publicsexhub.com/",
"Punishbang" to "https://punishbang.com/",
"Punishworld" to "https://punishworld.com/",
"R34anim" to "https://r34anim.com/",
"Rainblow" to "https://rainblow.xxx/",
"Rat" to "https://rat.xxx/",
"Ratedgross" to "https://ratedgross.com/",
"Realbooru" to "https://realbooru.com/",
"Realdeepfakes" to "https://realdeepfakes.to/",
"Reddclips" to "https://reddclips.com/",
"Reddxxx" to "https://reddxxx.com/",
"Redporn" to "https://redporn.xxx/",
"Reelsbaba" to "https://reelsbaba.com/",
"Reelsbaba2" to "https://reelsbaba2.com/",
"Reelsmunkey" to "https://reelsmunkey.com/",
"Rtgallery" to "https://rtgallery.net/",
"Rule34" to "https://rule34.gg/",
"Rule34" to "https://rule34.world/",
"Rule34" to "https://rule34.xyz/",
"Rule34gen" to "https://rule34gen.com/",
"Rule34vault" to "https://rule34vault.com/",
"Samusar" to "https://samusar.com/",
"Sanguinic" to "https://sanguinic.com/",
"Saradahentai" to "https://saradahentai.com/",
"Scatarchive" to "https://scatarchive.com/",
"Se" to "https://se.video/",
"Segavideo" to "https://segavideo.it/",
"Seksohub" to "https://seksohub.com/",
"Severeporn" to "https://severeporn.com/",
"Sex1x" to "https://sex1x.to/",
"Sex3" to "https://sex3.com/",
"Sex6x" to "https://sex6x.com/",
"Sexalarab" to "https://sexalarab.com/",
"Sexalarab" to "https://sexalarab.net/",
"Sexanimalvideos" to "https://sexanimalvideos.com/",
"Sexcelebrity" to "https://sexcelebrity.to/",
"Sexcelebrity" to "https://sexcelebrity.tv/",
"Sexenligne" to "https://sexenligne.com/",
"Sexlikereal" to "https://sexlikereal.com/",
"Sexmix" to "https://sexmix.net/",
"Sexnyk" to "https://sexnyk.com/",
"Sexocean" to "https://sexocean.net/",
"Sextvx" to "https://sextvx.com/",
"Sexu" to "https://sexu.name/",
"Sexvid" to "https://sexvid.pro/",
"Sexvid1" to "https://sexvid1.com/",
"Sexyscope" to "https://sexyscope.online/",
"Sfmcompile" to "https://sfmcompile.club/",
"Shagzy" to "https://shagzy.com/",
"Shameless" to "https://shameless.com/",
"Shemale" to "https://shemale.movie/",
"Shemale6" to "https://shemale6.com/",
"Shemalesin" to "https://shemalesin.com/",
"Shesfreaky" to "https://shesfreaky.com/",
"Sheshaft" to "https://sheshaft.com/",
"Shittytube" to "https://shittytube.com/",
"Shooshtime" to "https://shooshtime.com/",
"Shorts" to "https://shorts.xxx/",
"Shrmha" to "https://shrmha.pro/",
"Sick-r" to "https://sick-r.com/",
"Sickjunk" to "https://sickjunk.com/",
"Simpbun" to "https://simpbun.com/",
"Sissyhypno" to "https://sissyhypno.com/",
"Sizetube" to "https://sizetube.com/",
"Sleazyneasy" to "https://sleazyneasy.com/",
"Slporn" to "https://slporn.live/",
"Smutzo" to "https://smutzo.com/",
"Solopornoitaliani" to "https://solopornoitaliani.xxx/",
"Some" to "https://some.porn/",
"Spanked" to "https://spanked.to/",
"Spicymms" to "https://spicymms.com/",
"Starwank" to "https://starwank.com/",
"Suj" to "https://suj.mobi/",
"Swallowedporn" to "https://swallowedporn.com/",
"Taboodesi" to "https://taboodesi.net/",
"Taboodude" to "https://taboodude.com/",
"Taboojizz" to "https://taboojizz.com/",
"Tabooporn" to "https://tabooporn.to/",
"Tamilkamaveri2" to "https://tamilkamaveri2.com/",
"Tamilscandalsx" to "https://tamilscandalsx.com/",
"Teensexonline" to "https://teensexonline.com/",
"Teenxy" to "https://teenxy.com/",
"Terk" to "https://terk.nl/",
"Thematureporn" to "https://thematureporn.net/",
"Thepornfans" to "https://thepornfans.com/",
"Thepornjoy" to "https://thepornjoy.org/",
"Thepornmove" to "https://thepornmove.net/",
"Thepornstar" to "https://thepornstar.com/",
"Thethothub" to "https://thethothub.com/",
"Thetranny" to "https://thetranny.com/",
"Thothub" to "https://thothub.mx/",
"Thotslife" to "https://thotslife.com/",
"Thrupnies" to "https://thrupnies.com/",
"Tickle" to "https://tickle.porn/",
"Tik" to "https://tik.porn/",
"Trahkino" to "https://trahkino.cc/",
"Trahkino" to "https://trahkino.me/",
"Trannygem" to "https://trannygem.com/",
"Transtube" to "https://transtube.tv/",
"Transvids" to "https://transvids.tv/",
"Trash" to "https://trash.porn/",
"Trashreality" to "https://trashreality.com/",
"Trexporn" to "https://trexporn.net/",
"Trueamateurporn" to "https://trueamateurporn.org/",
"Tsthots" to "https://tsthots.com/",
"Tubepornclassic" to "https://tubepornclassic.com/",
"Tubepussy" to "https://tubepussy.org/",
"Tubewolf" to "https://tubewolf.com/",
"Tuilx" to "https://tuilx.net/",
"Turkifsahane" to "https://turkifsahane.com/",
"Turkifsahub" to "https://turkifsahub.com/",
"Turkifsamin" to "https://turkifsamin.org/",
"Tvoe-porno6" to "https://tvoe-porno6.click/",
"Twerkqueens" to "https://twerkqueens.com/",
"Twinkporn" to "https://twinkporn.one/",
"Txnhh" to "https://txnhh.com/",
"Uiporn" to "https://uiporn.asia/",
"Ukbops" to "https://ukbops.com/",
"Ultimatehentaixhub" to "https://ultimatehentaixhub.com/",
"Ultrathots" to "https://ultrathots.com/",
"Unusualpornx" to "https://unusualpornx.com/",
"Uporno" to "https://uporno.xxx/",
"Urduchudai" to "https://urduchudai.com/",
"Vagina" to "https://vagina.nl/",
"Vazounudes" to "https://vazounudes.com/",
"Videocelebs" to "https://videocelebs.net/",
"Videopornoitaliani" to "https://videopornoitaliani.com/",
"Vidzx" to "https://vidzx.org/",
"Violacionesporno" to "https://violacionesporno.pro/",
"Vip4k" to "https://vip4k.com/",
"Viralchut" to "https://viralchut.com/",
"Viralkand.com" to "https://viralkand.com.co/",
"Viralrani" to "https://viralrani.com/",
"Viralsex" to "https://viralsex.shop/",
"Viralsexyvideo" to "https://viralsexyvideo.com/",
"Viralthots" to "https://viralthots.tv/",
"Vjav" to "https://vjav.com/",
"Voyeurflash" to "https://voyeurflash.com/",
"Voyeurhit" to "https://voyeurhit.com/",
"Voyeurhit" to "https://voyeurhit.tube/",
"Vrphub" to "https://vrphub.com/",
"Vrporn" to "https://vrporn.com/",
"Vulgarx" to "https://vulgarx.com/",
"Wankado" to "https://wankado.com/",
"Wankoz" to "https://wankoz.com/",
"Wapbold" to "https://wapbold.com/",
"Wapbold" to "https://wapbold.net/",
"Waptap" to "https://waptap.com/",
"Webcamjackers" to "https://webcamjackers.com/",
"Webpussi" to "https://webpussi.com/",
"Wegotdicks" to "https://wegotdicks.com/",
"Wegotexposed" to "https://wegotexposed.ink/",
"Wierdjapan" to "https://wierdjapan.com/",
"Wildskirts" to "https://wildskirts.com/",
"X-tg" to "https://x-tg.tube/",
"X-video" to "https://x-video.tube/",
"X3arabi" to "https://x3arabi.com/",
"Xamadores" to "https://xamadores.com/",
"Xbabe" to "https://xbabe.com/",
"Xbondage" to "https://xbondage.porn/",
"Xcafe" to "https://xcafe.com/",
"Xepn" to "https://xepn.com/",
"Xerotica" to "https://xerotica.com/",
"Xfantazy" to "https://xfantazy.org/",
"Xfemaledom" to "https://xfemaledom.com/",
"Xhaccess" to "https://xhaccess.com/",
"Xhand" to "https://xhand.com/",
"Xhvid" to "https://xhvid.com/",
"Xkreeda" to "https://xkreeda.com/",
"Xlecx" to "https://xlecx.one/",
"Xlilith" to "https://xlilith.com/",
"Xlx" to "https://xlx.xxx/",
"Xmaal" to "https://xmaal.in/",
"Xmateur" to "https://xmateur.com/",
"Xmidget" to "https://xmidget.com/",
"Xmishti" to "https://xmishti.com/",
"Xmoan" to "https://xmoan.com/",
"Xmovies" to "https://xmovies.com/",
"Xnxx-ru" to "https://xnxx-ru.com/",
"Xnxxvideos" to "https://xnxxvideos.me/",
"Xorgasmo" to "https://xorgasmo.com/",
"Xpee" to "https://xpee.com/",
"Xshemale" to "https://xshemale.tv/",
"Xstream" to "https://xstream.cc/",
"Xtits" to "https://xtits.com/",
"Xtits" to "https://xtits.xxx/",
"Xv-ru" to "https://xv-ru.com/",
"Xv-vid" to "https://xv-vid.com/",
"Xvideos-ar" to "https://xvideos-ar.com/",
"Xvideos-india" to "https://xvideos-india.com/",
"Xvidioo" to "https://xvidioo.com/",
"Xvix" to "https://xvix.eu/",
"Xxx-videos" to "https://xxx-videos.video/",
"Xxxfollow" to "https://xxxfollow.com/",
"Xxxhindi.com" to "https://xxxhindi.com.co/",
"Xxxhindi" to "https://xxxhindi.to/",
"Xxxi" to "https://xxxi.porn/",
"Xxxindianstories" to "https://xxxindianstories.com/",
"Xxxshemaleporn" to "https://xxxshemaleporn.com/",
"Xxxviral" to "https://xxxviral.shop/",
"Xxxvogue" to "https://xxxvogue.net/",
"Xzxxtube" to "https://xzxxtube.com/",
"Yaebuxlo" to "https://yaebuxlo.ru/",
"Yesporn" to "https://yesporn.vip/",
"Yesvids" to "https://yesvids.com/",
"Youramateurporn" to "https://youramateurporn.com/",
"Yourlust" to "https://yourlust.com/",
"Zatube" to "https://zatube.com/",
"Zbgalleries" to "https://zbgalleries.com/",
"Zbporn" to "https://zbporn.tv/",
"Zzcartoon" to "https://zzcartoon.com/",
)
            val realSearch = EditText(this).apply {
                hint = "Search websites…"
                setSingleLine(true)
                textSize = 14f
                setTextColor(white)
                setHintTextColor(muted)
                setPadding(dp(14), dp(10), dp(14), dp(10))
                background = ripple(glassDrawable(16))
                elevation = dp(1).toFloat()
            }
            pageContent.addView(realSearch, LinearLayout.LayoutParams(-1, dp(48)).apply {
                topMargin = dp(12)
                bottomMargin = dp(8)
            })
            val realAdapter = WebsiteDirectoryAdapter("No matching websites.") { site ->
                startActivity(Intent(this, ComicWebsiteActivity::class.java).apply {
                    putExtra(ComicWebsiteActivity.EXTRA_SITE_NAME, site.first)
                    putExtra(ComicWebsiteActivity.EXTRA_SITE_URL, site.second)
                    putExtra(ComicWebsiteActivity.EXTRA_BLOCK_POPUPS, true)
                    putExtra(ComicWebsiteActivity.EXTRA_DIRECT_LINK_BUTTON, true)
                })
            }
            val realSiteList = createWebsiteRecycler(realAdapter)
            fun renderRealSites(filter: String) {
                realAdapter.submitList(realSites.filter { it.first.contains(filter, ignoreCase = true) ||
                    it.second.contains(filter, ignoreCase = true) })
            }
            realSearch.addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                    renderRealSites(s?.toString().orEmpty())
                }
                override fun afterTextChanged(s: Editable?) {}
            })
            renderRealSites("")
            installDirectoryRecycler(realSiteList)
            setContentView(root)
            animateScreen(root)
            return
        }

        if (showingComics) {
            pageContent.addView(label("COMIC WEBSITES", 22, white, true).apply {
                setPadding(0, dp(20), 0, dp(8))
            })
            val comicWebsites = listOf(
                "HentaiRead" to "https://hentairead.com/",
                "HitomiLA" to "https://hitomi.la/",
                "AllPornComic" to "https://allporncomic.com/home-3/",
                "NovelCrow" to "https://novelcrow.com/",
                "MultiPorn" to "https://multporn.com/",
                "KingComix" to "https://kingcomix.com/",
                "nHentai.net" to "https://nhentai.net/",
                "nHentai.to" to "https://nhentai.to/"
            )
            val comicSearch = EditText(this).apply {
                hint = "Search ${comicWebsites.size} websites..."
                setHintTextColor(muted)
                setTextColor(white)
                textSize = 14f
                isSingleLine = true
                imeOptions = EditorInfo.IME_ACTION_SEARCH
                setPadding(dp(13), 0, dp(10), 0)
                background = ripple(glassDrawable(14))
            }
            pageContent.addView(comicSearch, LinearLayout.LayoutParams(-1, dp(46)).apply {
                topMargin = dp(6)
                bottomMargin = dp(10)
            })

            val comicAdapter = WebsiteDirectoryAdapter("No matching comic websites.") { site ->
                if (site.first == "HentaiRead") {
                    startActivity(Intent(this, HentaiReadComicsActivity::class.java))
                } else {
                    startActivity(Intent(this, ComicWebsiteActivity::class.java).apply {
                        putExtra(ComicWebsiteActivity.EXTRA_SITE_NAME, "Comics • " + site.first)
                        putExtra(ComicWebsiteActivity.EXTRA_SITE_URL, site.second)
                        putExtra(ComicWebsiteActivity.EXTRA_BLOCK_POPUPS, true)
                    })
                }
            }
            val comicList = createWebsiteRecycler(comicAdapter)

            fun renderComicWebsites(filter: String) {
                val normalized = filter.trim()
                comicAdapter.submitList(comicWebsites.filter { (name, url) ->
                    name.contains(normalized, ignoreCase = true) ||
                        url.contains(normalized, ignoreCase = true)
                })
            }

            renderComicWebsites("")
            comicSearch.addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                    renderComicWebsites(s?.toString().orEmpty())
                }
                override fun afterTextChanged(s: Editable?) = Unit
            })
            installDirectoryRecycler(comicList)
            setContentView(root)
            animateScreen(root)
            return
        }

        if (showingJav) {
            pageContent.addView(label("JAV WEBSITES", 22, white, true).apply {
                setPadding(0, dp(20), 0, dp(8))
            })
            val javSearch = EditText(this).apply {
                hint = "Search 48 websites..."
                setHintTextColor(muted)
                setTextColor(white)
                textSize = 14f
                isSingleLine = true
                imeOptions = EditorInfo.IME_ACTION_SEARCH
                setPadding(dp(13), 0, dp(10), 0)
                background = ripple(glassDrawable(14))
                setText(javQuery)
            }
            pageContent.addView(javSearch, LinearLayout.LayoutParams(-1, dp(46)).apply {
                topMargin = dp(6)
                bottomMargin = dp(10)
            })

            val javWebsites = listOf(
                "MissAV" to "https://missav.ws/",
                "SupJav" to "https://supjav.com/",
                "JavGuru" to "https://jav.guru/",
                "BestJavPorn" to "https://bestjavporn.com/",
                "Javtiful" to "https://javtiful.com/",
                "7MMTV" to "https://7mmtv.tv/",
                "KissJAV (ShyAV)" to "https://kissjav.com/",
                "JAVHD.today" to "https://javhd.today/",
                "VJAV" to "https://vjav.com/",
                "91Porn" to "https://91porn.com/",
                "JAVGG" to "https://javgg.net/",
                "JavBangers" to "https://javbangers.com/",
                "JAV GIGA" to "https://javgiga.com/",
                "JAVSeen" to "https://javseen.tv/",
                "JAV Tsunami" to "https://javtsunami.com/",
                "Korean Porn Movie" to "https://koreanpornmovie.com/",
                "nJAV" to "https://njav.com/",
                "JAVSB" to "https://jav.sb/",
                "xKorean" to "https://xkorean.biz/",
                "Japan Whores" to "https://japan-whores.com/",
                "FC2PPV" to "https://fc2ppv.tv/",
                "SexKbj" to "https://sexkbj.com/",
                "JAVLeak" to "https://javleak.com/",
                "JavFun" to "https://javfun.me/",
                "AVCrempie" to "https://avcreampie.com/",
                "JAVhay" to "https://javhay.net/",
                "BlackedJAV" to "https://blackedjav.com/",
                "ABJAV" to "https://abjav.com/",
                "PornHub Asian" to "https://www.pornhub.com/video?c=asian",
                "Senzuri Tube" to "https://senzuri.tube/",
                "Roshy" to "https://roshy.tv/",
                "JAVQuick" to "https://javquick.com/",
                "nJAV TV" to "https://njavtv.cc/",
                "TokyoMotion" to "https://tokyomotion.com/",
                "FunLove" to "https://funlove.info/",
                "MaxJAV" to "https://maxjav.com/",
                "JAV WINE" to "https://jav.wine/",
                "JAVHDTube" to "https://javhdtube.com/",
                "JavBest TV" to "https://javbest.tv/",
                "VivaMaxPH" to "https://vivamaxph.com/",
                "AV Porner" to "https://avporner.com/",
                "ZeroJAV" to "https://zerojav.com/",
                "Jav Mobile" to "https://javmobile.net/",
                "VivamaxStream" to "https://vivamaxstream.com/",
                "AsianViral" to "https://asianviral.net/",
                "XXBUM" to "https://xxbum.com/",
                "OMGJAV" to "https://omgjav.com/",
                "Manko" to "https://manko.fun/"
            )
            val javAdapter = WebsiteDirectoryAdapter("No matching JAV websites.") { site ->
                startActivity(Intent(this, ComicWebsiteActivity::class.java).apply {
                    putExtra(ComicWebsiteActivity.EXTRA_SITE_NAME, "JAV • " + site.first)
                    putExtra(ComicWebsiteActivity.EXTRA_SITE_URL, site.second)
                    putExtra(ComicWebsiteActivity.EXTRA_BLOCK_POPUPS, true)
                })
            }
            val javList = createWebsiteRecycler(javAdapter)

            fun renderJavWebsites(filter: String) {
                val normalized = filter.trim()
                javAdapter.submitList(javWebsites.filter { it.first.contains(normalized, ignoreCase = true) ||
                    it.second.contains(normalized, ignoreCase = true) })
            }
            renderJavWebsites(javQuery)
            javSearch.addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                    javQuery = s?.toString().orEmpty()
                    renderJavWebsites(javQuery)
                }
                override fun afterTextChanged(s: Editable?) = Unit
            })
            installDirectoryRecycler(javList)
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
        // Cards are attached only after this render pass. Re-scan once layout
        // has completed so thumbnails queued while detached are actually started.
        scroll.post {
            scroll.scrollTo(0, previousScrollY)
            loadVisibleImages(pageContent)
        }
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
                minHeight = dp(50)
                setPadding(dp(10), dp(5), dp(10), dp(5))
                buttonTintList = android.content.res.ColorStateList.valueOf(accent)
                background = ripple(glassDrawable(12))
                isChecked = prefs.getBoolean(key, default)
                setOnCheckedChangeListener { _, checked ->
                    prefs.edit().putBoolean(key, checked).apply()
                }
            }, LinearLayout.LayoutParams(-1, -2).apply {
                bottomMargin = dp(5)
            })
        }
        fun selector(title: String, key: String, values: List<String>, default: String) {
            panel.addView(label(title, 12, muted, true).apply {
                setPadding(0, dp(10), 0, dp(4))
            })
            val spinner = Spinner(this)
            spinner.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, values)
            spinner.background = ripple(glassDrawable(14))
            spinner.dropDownVerticalOffset = dp(6)
            spinner.setPopupBackgroundDrawable(rounded(Color.rgb(19, 10, 35), 16))
            spinner.setSelection(values.indexOf(prefs.getString(key, default)).coerceAtLeast(0))
            spinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                override fun onNothingSelected(parent: AdapterView<*>?) = Unit
                override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                    prefs.edit().putString(key, values[position]).apply()
                    view?.let { animateReveal(it) }
                }
            }
            panel.addView(spinner, LinearLayout.LayoutParams(-1, dp(44)))
        }
        panel.addView(label("PERSONALIZATION", 11, accent, true).apply {
            setPadding(0, dp(4), 0, dp(4))
        })
        selector("Accent colour", "accent_color", listOf("Purple", "Lavender", "Neon Rose", "Magenta", "Electric Blue", "Ice Cyan", "Emerald", "Amber", "Crimson"), "Purple")
        selector("Card design", "card_style", listOf("Glass", "Solid", "Minimal"), "Glass")
        selector("Content layout", "layout_mode", listOf("Gallery", "Compact"), "Gallery")
        option("AMOLED black background", "amoled_mode", false)
        option("Animated aurora + galaxy particles", "ambient_background", true)
        option("Shimmer thumbnail loading", "shimmer_loading", true)
        panel.addView(label("PERFORMANCE", 11, accent, true).apply {
            setPadding(0, dp(12), 0, dp(4))
        })
        option("Smooth animations", "smooth_animations", true)
        selector("Animation feel", "motion_profile", listOf("Cinematic", "Smooth", "Snappy"), "Smooth")
        option("Haptic touch feedback", "haptic_feedback", true)
        option("Fast search (up to 4 concurrent detail requests)", "fast_search", true)
        option("High-quality thumbnails (uses more data)", "high_quality_thumbnails", false)
        option("Cache search details and thumbnails", "cache_enabled", true)
        option("Data Saver (smaller thumbnails, no extra preloading)", "data_saver_mode", false)
        // Keep the full settings catalogue reachable on compact phone screens.
        val settingsScroll = ScrollView(this).apply {
            isFillViewport = false
            overScrollMode = View.OVER_SCROLL_IF_CONTENT_SCROLLS
            addView(panel, android.view.ViewGroup.LayoutParams(-1, -2))
        }
        val dialog = AlertDialog.Builder(this)
            .setTitle("PERSONALIZE XANIME")
            .setView(settingsScroll)
            .setPositiveButton("APPLY") { _, _ -> showHome() }
            .setNeutralButton("CLEAR CACHE") { _, _ ->
                detailCache.clear()
                imageCache.evictAll()
                Toast.makeText(this, "Cache cleared", Toast.LENGTH_SHORT).show()
            }
            .create()
        dialog.setOnShowListener {
            dialog.window?.setBackgroundDrawable(glassDrawable(22))
            dialog.getButton(AlertDialog.BUTTON_POSITIVE)?.setTextColor(accent)
            dialog.getButton(AlertDialog.BUTTON_NEUTRAL)?.setTextColor(muted)
            val decor = dialog.window?.decorView
            if (animationsEnabled() && decor != null) {
                decor.alpha = 0f
                decor.scaleX = .94f
                decor.scaleY = .94f
                decor.translationY = dp(12).toFloat()
                decor.animate().alpha(1f).scaleX(1f).scaleY(1f).translationY(0f)
                    .setDuration(motionDuration(280))
                    .setInterpolator(motionInterpolator())
                    .start()
            }
        }
        dialog.show()
    }

    private fun animationsEnabled(): Boolean =
        prefs.getBoolean("smooth_animations", true)

    private fun motionDuration(base: Long): Long {
        if (!animationsEnabled()) return 0L
        val multiplier = when (prefs.getString("motion_profile", "Smooth")) {
            "Cinematic" -> 1.22f
            "Snappy" -> 0.68f
            else -> 1f
        }
        return (base * multiplier).toLong().coerceAtLeast(90L)
    }

    private fun motionInterpolator(): android.view.animation.Interpolator =
        when (prefs.getString("motion_profile", "Smooth")) {
            "Cinematic" -> android.view.animation.OvershootInterpolator(0.72f)
            "Snappy" -> android.view.animation.DecelerateInterpolator(2.1f)
            else -> android.view.animation.DecelerateInterpolator(1.45f)
        }

    private fun animateReveal(view: View) {
        if (!animationsEnabled()) {
            view.alpha = 1f
            view.translationY = 0f
            return
        }
        view.alpha = 0f
        view.translationY = -dp(5).toFloat()
        view.animate().alpha(1f).translationY(0f).setDuration(motionDuration(260))
            .setInterpolator(motionInterpolator()).start()
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
            .setDuration(motionDuration(310))
            .setInterpolator(motionInterpolator())
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

    // HitomiLA uses the same simple in-app reader entry as HentaiRead.
    private fun showHitomiLaHome() {
        pageContent.addView(infoCard("Click Read to Browse And Read HitomiLA."),
            LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(10) })
        pageContent.addView(actionButton("📖  READ", true) {
            startActivity(Intent(this, ComicWebsiteActivity::class.java).apply {
                putExtra(ComicWebsiteActivity.EXTRA_SITE_NAME, "HitomiLA")
                putExtra(ComicWebsiteActivity.EXTRA_SITE_URL, "https://hitomi.la/")
            })
        }, LinearLayout.LayoutParams(-1, dp(52)).apply { topMargin = dp(10) })
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
                // Hitomi's search page expects the raw search expression, not a title: prefix.
                val encoded = URLEncoder.encode(term, "UTF-8")
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
                val found = mutableListOf<HitomiGalleryResult>()
                // Hitomi gallery links can use category paths such as /manga/ or /doujinshi/,
                // not only the legacy /galleries/ path.
                val galleryLinks = document.select(
                    ".gallery-content a[href], a[href*=/galleries/], a[href*=/manga/], " +
                        "a[href*=/doujinshi/], a[href*=/artistcg/], a[href*=/gamecg/]"
                )
                for (link in galleryLinks) {
                    val href = link.attr("href")
                    if (!href.endsWith(".html", ignoreCase = true)) continue
                    val id = Regex("(?:/galleries/|/[^/]+/)[^/]*?(\\d+)\\.html(?:$|[?#])")
                        .find(href)?.groupValues?.getOrNull(1)
                        ?: continue
                    val title = link.text().trim()
                        .ifEmpty { link.parent()?.selectFirst("h1")?.text()?.trim().orEmpty() }
                    if (title.isNotEmpty() && title.contains(term, ignoreCase = true)) {
                        val absoluteUrl = if (href.startsWith("http")) href else "https://hitomi.la$href"
                        found.add(HitomiGalleryResult(id, title, absoluteUrl))
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
        scrollableTab: Boolean = false,
        action: () -> Unit
    ) {
        val column = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
        }
        val tabButton = actionButton(title, selected, action).apply {
            if (scrollableTab) maxLines = 1
        }
        column.addView(tabButton, LinearLayout.LayoutParams(-1, dp(43)))
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
        container.addView(column, LinearLayout.LayoutParams(
            if (scrollableTab) dp(112) else 0,
            -2,
            if (scrollableTab) 0f else 1f
        ).apply {
            if (endMargin > 0) marginEnd = dp(endMargin)
            if (scrollableTab && endMargin == 0) marginEnd = dp(8)
        })
    }


    private inner class WebsiteDirectoryAdapter(
        private val emptyMessage: String,
        private val onOpen: (Pair<String, String>) -> Unit
    ) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
        private var sites: List<Pair<String, String>> = emptyList()
        private val emptyType = 1

        fun submitList(items: List<Pair<String, String>>) {
            sites = items
            notifyDataSetChanged()
        }

        override fun getItemViewType(position: Int): Int =
            if (sites.isEmpty()) emptyType else 0

        override fun getItemCount(): Int = if (sites.isEmpty()) 1 else sites.size

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
            if (viewType == emptyType) {
                return object : RecyclerView.ViewHolder(TextView(this@MainActivity).apply {
                    text = emptyMessage
                    textSize = 13f
                    setTextColor(muted)
                    gravity = Gravity.CENTER
                    setPadding(dp(16), dp(18), dp(16), dp(18))
                    background = glassDrawable(16)
                }) {}
            }
            return object : RecyclerView.ViewHolder(FrameLayout(this@MainActivity)) {}
        }

        override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
            if (holder.itemViewType == emptyType) return
            val site = sites[position]
            val frame = holder.itemView as FrameLayout
            frame.removeAllViews()
            frame.addView(websiteCard(site.first, site.second) {
                val currentPosition = holder.bindingAdapterPosition
                if (currentPosition != RecyclerView.NO_POSITION && currentPosition < sites.size) {
                    onOpen(sites[currentPosition])
                }
            }, FrameLayout.LayoutParams(-1, dp(68)).apply {
                bottomMargin = dp(9)
            })
        }
    }

    private fun createWebsiteRecycler(adapter: WebsiteDirectoryAdapter): RecyclerView =
        RecyclerView(this).apply {
            layoutManager = LinearLayoutManager(this@MainActivity)
            this.adapter = adapter
            setHasFixedSize(true)
            itemAnimator = null
            overScrollMode = View.OVER_SCROLL_NEVER
            clipToPadding = false
            setPadding(0, dp(2), 0, dp(8))
        }

    private fun installDirectoryRecycler(directory: RecyclerView) {
        scroll.removeView(pageContent)
        root.removeView(scroll)
        val shell = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }
        shell.addView(pageContent, LinearLayout.LayoutParams(-1, -2))
        shell.addView(directory, LinearLayout.LayoutParams(-1, 0, 1f))
        root.addView(shell, LinearLayout.LayoutParams(-1, -1))
    }

    // A full-surface website row makes navigation feel like a deliberate, tappable card.
    // Uses a lightweight monogram instead of network-loaded favicons to keep scrolling smooth.
    private fun websiteCard(siteName: String, siteUrl: String, action: () -> Unit) =
        LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            isClickable = true
            isFocusable = true
            setPadding(dp(12), dp(9), dp(13), dp(9))
            background = ripple(glassDrawable(17))
            elevation = dp(2).toFloat()

            val mark = TextView(this@MainActivity).apply {
                text = siteName.trim().take(1).uppercase()
                textSize = 18f
                typeface = Typeface.create("sans-serif", Typeface.BOLD)
                gravity = Gravity.CENTER
                setTextColor(white)
                background = GradientDrawable(
                    GradientDrawable.Orientation.TOP_BOTTOM,
                    intArrayOf(accentWithAlpha(210), accentWithAlpha(90))
                ).apply {
                    cornerRadius = dp(13).toFloat()
                    setStroke(dp(1), accentWithAlpha(180))
                }
            }
            addView(mark, LinearLayout.LayoutParams(dp(46), dp(46)).apply {
                marginEnd = dp(12)
            })

            val details = LinearLayout(this@MainActivity).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER_VERTICAL
            }
            details.addView(TextView(this@MainActivity).apply {
                text = siteName
                textSize = 14f
                maxLines = 1
                ellipsize = android.text.TextUtils.TruncateAt.END
                typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
                setTextColor(white)
            })
            val domain = Uri.parse(siteUrl).host?.removePrefix("www.") ?: siteUrl
            details.addView(TextView(this@MainActivity).apply {
                text = domain
                textSize = 10f
                maxLines = 1
                ellipsize = android.text.TextUtils.TruncateAt.END
                setTextColor(muted)
                setPadding(0, dp(4), 0, 0)
            })
            addView(details, LinearLayout.LayoutParams(0, -2, 1f))

            addView(TextView(this@MainActivity).apply {
                text = "↗"
                textSize = 22f
                gravity = Gravity.CENTER
                setTextColor(accent)
                contentDescription = "Open $siteName"
            }, LinearLayout.LayoutParams(dp(30), dp(42)))

            setOnTouchListener { target, event ->
                if (!animationsEnabled()) return@setOnTouchListener false
                when (event.actionMasked) {
                    android.view.MotionEvent.ACTION_DOWN ->
                        target.animate().scaleX(.985f).scaleY(.985f).alpha(.88f)
                            .setDuration(85).start()
                    android.view.MotionEvent.ACTION_UP,
                    android.view.MotionEvent.ACTION_CANCEL ->
                        target.animate().scaleX(1f).scaleY(1f).alpha(1f)
                            .setDuration(160).setInterpolator(android.view.animation.DecelerateInterpolator()).start()
                }
                false
            }
            setOnClickListener {
                if (prefs.getBoolean("haptic_feedback", true)) {
                    performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY)
                }
                action()
            }
        }

    private fun infoCard(message: String) = TextView(this).apply {
        text = message; textSize = 13f; setTextColor(muted)
        setPadding(dp(16), dp(18), dp(16), dp(18)); background = glassDrawable(16)
    }

    private fun actionButton(text: String, primary: Boolean = true, action: () -> Unit) =
        Button(this).apply {
            this.text = text
            textSize = 12f
            isAllCaps = false
            typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
            setTextColor(white)
            minHeight = dp(44)
            minimumHeight = dp(44)
            setPadding(dp(14), dp(8), dp(14), dp(8))
            background = ripple(GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                if (primary) intArrayOf(
                    Color.argb(245, Color.red(accent), Color.green(accent), Color.blue(accent)),
                    Color.argb(210, Color.red(accent) / 2, Color.green(accent) / 2, Color.blue(accent) / 2)
                ) else intArrayOf(Color.rgb(31, 20, 52), Color.rgb(13, 8, 25))
            ).apply {
                cornerRadius = dp(17).toFloat()
                setStroke(dp(1), if (primary) accentWithAlpha(190) else accentWithAlpha(125))
            })
            elevation = dp(if (primary) 5 else 1).toFloat()
            stateListAnimator = null
            setOnTouchListener { target, event ->
                if (!animationsEnabled()) return@setOnTouchListener false
                when (event.actionMasked) {
                    android.view.MotionEvent.ACTION_DOWN -> target.animate().scaleX(.965f).scaleY(.965f)
                        .setDuration(90).setInterpolator(android.view.animation.DecelerateInterpolator()).start()
                    android.view.MotionEvent.ACTION_UP, android.view.MotionEvent.ACTION_CANCEL ->
                        target.animate().scaleX(1f).scaleY(1f).setDuration(220)
                            .setInterpolator(android.view.animation.OvershootInterpolator(1.35f)).start()
                }
                false
            }
            setOnClickListener {
                if (prefs.getBoolean("haptic_feedback", true)) {
                    performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY)
                }
                action()
            }
        }

    private fun accentWithAlpha(alpha: Int): Int =
        Color.argb(alpha, Color.red(accent), Color.green(accent), Color.blue(accent))

    private fun glassDrawable(radius: Int): GradientDrawable =
        GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
            intArrayOf(Color.rgb(28, 16, 48), Color.rgb(8, 5, 19))).apply {
            cornerRadius = dp(radius).toFloat()
            // Every glass surface follows the selected accent instead of staying purple.
            setStroke(dp(1), accentWithAlpha(150))
        }

    private fun ripple(content: Drawable): RippleDrawable =
        RippleDrawable(ColorStateList.valueOf(accentWithAlpha(82)), content, null)

    private fun animateScreen(view: View) {
        if (!animationsEnabled()) {
            libraryTabTransitionPending = false
            view.alpha = 1f
            view.translationX = 0f
            view.translationY = 0f
            view.scaleX = 1f
            view.scaleY = 1f
            return
        }
        view.animate().cancel()
        if (libraryTabTransitionPending) {
            val direction = libraryTabTransitionDirection
            libraryTabTransitionPending = false
            view.alpha = 0.82f
            view.translationX = direction * dp(24).toFloat()
            view.translationY = 0f
            view.scaleX = 1f
            view.scaleY = 1f
            view.post {
                if (!isFinishing) view.animate()
                    .alpha(1f).translationX(0f)
                    .setDuration(motionDuration(245))
                    .setInterpolator(android.view.animation.DecelerateInterpolator(1.7f))
                    .start()
            }
            return
        }
        view.alpha = 0f
        view.translationX = 0f
        view.translationY = dp(13).toFloat()
        view.scaleX = .992f
        view.scaleY = .992f
        view.post {
            if (!isFinishing) view.animate()
                .alpha(1f).translationY(0f).scaleX(1f).scaleY(1f)
                .setDuration(motionDuration(360))
                .setInterpolator(motionInterpolator())
                .start()
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
