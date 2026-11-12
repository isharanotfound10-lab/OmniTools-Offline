package com.xanime.app

import android.app.Activity
import android.app.DownloadManager
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.BitmapFactory
import android.os.Bundle
import android.os.Environment
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors

class HitomiReaderActivity : Activity() {
    private val io = Executors.newFixedThreadPool(3)
    private lateinit var pageList: LinearLayout
    private lateinit var status: TextView
    private lateinit var progress: ProgressBar
    private var pages: List<HitomiResolver.Page> = emptyList()
    private var galleryTitle = "Hitomi Gallery"
    private var galleryId = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        galleryId = intent.getStringExtra(EXTRA_GALLERY_ID).orEmpty()
        galleryTitle = intent.getStringExtra(EXTRA_TITLE).orEmpty().ifBlank { "Hitomi Gallery" }
        if (galleryId.isBlank()) {
            finish()
            return
        }
        buildUi()
        loadGallery()
    }

    private fun buildUi() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(0xFF03070C.toInt())
            setPadding(dp(14), dp(12), dp(14), dp(12))
        }
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        header.addView(Button(this).apply {
            text = "‹ Back"
            isAllCaps = false
            setOnClickListener { finish() }
        }, LinearLayout.LayoutParams(dp(82), dp(44)))
        header.addView(TextView(this).apply {
            text = "HITOMI.LA"
            textSize = 17f
            setTextColor(-1)
            setPadding(dp(12), 0, 0, 0)
        }, LinearLayout.LayoutParams(0, -2, 1f))
        val copyButton = Button(this).apply {
            text = "Copy links"
            isAllCaps = false
            setOnClickListener { copyAllLinks() }
            tag = "copy"
            isEnabled = false
        }
        header.addView(copyButton, LinearLayout.LayoutParams(-2, dp(44)))
        root.addView(header)

        root.addView(TextView(this).apply {
            text = galleryTitle
            textSize = 14f
            setTextColor(-1)
            setPadding(0, dp(12), 0, dp(5))
        })
        status = TextView(this).apply {
            text = "Resolving Hitomi image servers..."
            textSize = 12f
            setTextColor(0xFFB8C3D1.toInt())
            setPadding(0, dp(4), 0, dp(8))
        }
        root.addView(status)
        progress = ProgressBar(this)
        root.addView(progress, LinearLayout.LayoutParams(dp(32), dp(32)).apply {
            gravity = Gravity.CENTER_HORIZONTAL
        })
        val scroll = ScrollView(this).apply { fillViewport = false }
        pageList = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        scroll.addView(pageList, ViewGroup.LayoutParams(-1, -2))
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        setContentView(root)
    }

    private fun loadGallery() {
        io.execute {
            try {
                val gallery = HitomiResolver.resolveGallery(galleryId)
                pages = gallery.pages
                galleryTitle = gallery.title.ifBlank { galleryTitle }
                runOnUiThread {
                    if (isFinishing) return@runOnUiThread
                    progress.visibility = View.GONE
                    status.text = "${pages.size} pages • URLs resolved with Hitomi's current GG logic"
                    findCopyButton()?.isEnabled = true
                    pages.forEachIndexed { index, page -> addPageCard(index, page) }
                }
            } catch (error: Exception) {
                runOnUiThread {
                    if (isFinishing) return@runOnUiThread
                    progress.visibility = View.GONE
                    status.text = "Could not resolve gallery: ${error.message ?: "Network or Hitomi error"}"
                }
            }
        }
    }

    private fun addPageCard(index: Int, page: HitomiResolver.Page) {
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(9), dp(9), dp(9), dp(9))
            background = android.graphics.drawable.GradientDrawable().apply {
                setColor(0xFF0B1722.toInt())
                cornerRadius = dp(13).toFloat()
                setStroke(dp(1), 0x553CA7B8)
            }
        }
        card.addView(TextView(this).apply {
            text = "PAGE ${index + 1}  •  ${page.name}"
            textSize = 11f
            setTextColor(0xFFE6EDF5.toInt())
            setPadding(dp(3), dp(2), dp(3), dp(8))
        })
        val image = ImageView(this).apply {
            adjustViewBounds = true
            scaleType = ImageView.ScaleType.FIT_CENTER
            setBackgroundColor(0xFF050A10.toInt())
            contentDescription = "Hitomi page ${index + 1}"
        }
        card.addView(image, LinearLayout.LayoutParams(-1, -2))
        card.addView(Button(this).apply {
            text = "Download page ${index + 1}"
            isAllCaps = false
            setOnClickListener { downloadPage(page) }
        }, LinearLayout.LayoutParams(-1, dp(42)).apply { topMargin = dp(7) })
        pageList.addView(card, LinearLayout.LayoutParams(-1, -2).apply {
            bottomMargin = dp(12)
        })
        io.execute {
            var connection: HttpURLConnection? = null
            try {
                connection = (URL(page.url).openConnection() as HttpURLConnection).apply {
                    connectTimeout = 15000
                    readTimeout = 25000
                    setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 Chrome/120 Mobile Safari/537.36")
                    setRequestProperty("Referer", "https://hitomi.la/reader/$galleryId.html")
                    setRequestProperty("Accept", "image/avif,image/*,*/*")
                }
                val code = connection.responseCode
                if (code !in 200..299) throw IllegalStateException("HTTP $code")
                val bitmap = connection.inputStream.use { input ->
                    val bytes = input.readBytes()
                    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
                    val targetWidth = resources.displayMetrics.widthPixels.coerceAtLeast(480)
                    var sampleSize = 1
                    while (bounds.outWidth / (sampleSize * 2) >= targetWidth) {
                        sampleSize *= 2
                    }
                    val decodeOptions = BitmapFactory.Options().apply {
                        inSampleSize = sampleSize
                        inPreferredConfig = android.graphics.Bitmap.Config.RGB_565
                    }
                    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, decodeOptions)
                }
                runOnUiThread {
                    if (!isFinishing && bitmap != null) image.setImageBitmap(bitmap)
                    else if (!isFinishing) image.setImageDrawable(null)
                }
            } catch (_: Exception) {
                runOnUiThread {
                    if (!isFinishing) image.contentDescription = "Failed to load page ${index + 1}"
                }
            } finally {
                connection?.disconnect()
            }
        }
    }

    private fun findCopyButton(): Button? {
        val root = (findViewById<View>(android.R.id.content) as? ViewGroup) ?: return null
        fun walk(view: View): Button? {
            if (view is Button && view.tag == "copy") return view
            if (view is ViewGroup) {
                for (i in 0 until view.childCount) walk(view.getChildAt(i))?.let { return it }
            }
            return null
        }
        return walk(root)
    }

    private fun copyAllLinks() {
        if (pages.isEmpty()) return
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("Hitomi image URLs", pages.joinToString("\n") { it.url }))
        Toast.makeText(this, "Copied ${pages.size} direct image URLs", Toast.LENGTH_SHORT).show()
    }

    private fun downloadPage(page: HitomiResolver.Page) {
        try {
            val request = DownloadManager.Request(android.net.Uri.parse(page.url))
                .setTitle(page.name)
                .setDescription("Downloading from HitomiLA")
                .setMimeType("image/avif")
                .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, "XAnime/Hitomi/$galleryId/${page.name.substringBeforeLast('.')}.avif")
                .addRequestHeader("Referer", "https://hitomi.la/reader/$galleryId.html")
                .addRequestHeader("User-Agent", "Mozilla/5.0")
            (getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager).enqueue(request)
            Toast.makeText(this, "Download started: ${page.name}", Toast.LENGTH_SHORT).show()
        } catch (error: Exception) {
            Toast.makeText(this, "Download failed: ${error.message}", Toast.LENGTH_LONG).show()
        }
    }

    override fun onDestroy() {
        io.shutdownNow()
        super.onDestroy()
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    companion object {
        const val EXTRA_GALLERY_ID = "gallery_id"
        const val EXTRA_TITLE = "gallery_title"
    }
}
