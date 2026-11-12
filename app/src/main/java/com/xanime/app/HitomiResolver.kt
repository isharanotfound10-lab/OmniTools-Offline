package com.xanime.app

import org.mozilla.javascript.Context
import org.mozilla.javascript.Scriptable
import org.mozilla.javascript.ClassShutter
import org.json.JSONArray
import java.net.HttpURLConnection
import java.net.URL

/**
 * Resolves Hitomi gallery image URLs using Hitomi's own common.js + gg.js logic.
 * URLs are calculated from the gallery metadata and current server mapping;
 * no browser cookies, IP-bound token, or guessed CDN hostname is embedded.
 */
object HitomiResolver {
    data class Page(val name: String, val url: String)
    data class Gallery(val title: String, val pages: List<Page>)

    fun resolveGallery(galleryId: String): Gallery {
        require(galleryId.matches(Regex("\\d{1,12}"))) { "Invalid Hitomi gallery ID" }

        val galleryJs = fetchText("https://ltn.hitomi.la/galleries/$galleryId.js")
        val commonJs = fetchText("https://ltn.hitomi.la/common.js")
        val ggJs = fetchText("https://ltn.hitomi.la/gg.js")

        val commonFunctions = Regex(
            "(?s)(?:var\\s+gg.*?|function\\s+subdom.*?)function\\s*show_loading"
        ).find(commonJs)?.value
            ?: throw IllegalStateException("Hitomi URL resolver functions were not found in common.js")

        val context = Context.enter()
        try {
            context.optimizationLevel = -1
            context.languageVersion = Context.VERSION_ES6
            // The resolver only needs JavaScript built-ins. Do not expose Java classes
            // to the remote site scripts.
            context.setClassShutter(ClassShutter { false })
            val scope: Scriptable = context.initStandardObjects()
            context.evaluateString(scope, "var navigator = {}; navigator.userAgent = 'XAnime Android';", "bootstrap", 1, null)
            context.evaluateString(scope, commonFunctions, "hitomi-common.js", 1, null)
            context.evaluateString(
                scope,
                ggJs.replaceFirst(Regex("^\\s*(['\"])use strict\\1;\\s*"), "var "),
                "hitomi-gg.js",
                1,
                null
            )
            context.evaluateString(scope, galleryJs, "hitomi-gallery.js", 1, null)

            val expression = """
                (function() {
                    var result = [];
                    galleryinfo.files.forEach(function(file) {
                        var url = url_from_url_from_hash($galleryId, file, "avif", null, "a");
                        result.push({name: file.name, url: url});
                    });
                    return JSON.stringify({
                        title: galleryinfo.title || "Hitomi Gallery",
                        pages: result
                    });
                })()
            """.trimIndent()
            val json = Context.toString(context.evaluateString(scope, expression, "resolve-gallery", 1, null))
            val root = org.json.JSONObject(json)
            val array: JSONArray = root.getJSONArray("pages")
            val pages = ArrayList<Page>(array.length())
            for (index in 0 until array.length()) {
                val item = array.getJSONObject(index)
                val pageUrl = item.getString("url")
                require(pageUrl.startsWith("https://") && pageUrl.endsWith(".avif")) {
                    "Hitomi returned an invalid image URL"
                }
                pages.add(Page(item.optString("name", "%02d.avif".format(index + 1)), pageUrl))
            }
            if (pages.isEmpty()) throw IllegalStateException("This Hitomi gallery contains no pages")
            return Gallery(root.optString("title", "Hitomi Gallery"), pages)
        } finally {
            Context.exit()
        }
    }

    internal fun fullPathFromHash(hash: String): String {
        require(hash.length >= 3 && hash.matches(Regex("[a-fA-F0-9]+"))) { "Invalid image hash" }
        return "${hash.last()}/${hash.substring(hash.length - 3, hash.length - 1)}/$hash"
    }

    private fun fetchText(address: String): String {
        val connection = (URL(address).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 15000
            readTimeout = 20000
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 Chrome/120 Mobile Safari/537.36")
            setRequestProperty("Accept", "application/javascript,text/plain,*/*")
            setRequestProperty("Referer", "https://hitomi.la/")
        }
        try {
            val code = connection.responseCode
            if (code !in 200..299) throw IllegalStateException("Hitomi resolver request failed (HTTP $code)")
            return connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
        } finally {
            connection.disconnect()
        }
    }
}
