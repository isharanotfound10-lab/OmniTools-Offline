package com.xanime.app

import org.jsoup.Jsoup
import org.json.JSONArray
import org.json.JSONObject
import java.net.URI

data class HentainiEpisode(
    val number: Int,
    val title: String,
    val url: String,
    val thumbnailUrl: String
)

data class HentainiStream(val host: String, val url: String)

data class HentainiEpisodePage(
    val title: String,
    val thumbnailUrl: String,
    val streams: List<HentainiStream>
)

object HentainiEpisodeParser {
    private val knownHosts = listOf(
        "1024terabox", "terabox", "fireload", "mediafire", "1fichier",
        "mega.nz", "drive.google", "gofile", "pixeldrain", "mixdrop",
        "streamtape", "vidoza", "voe", "dood"
    )

    fun parseSeries(html: String, pageUrl: String, slug: String): List<HentainiEpisode> {
        val doc = Jsoup.parse(html, pageUrl)
        val pathPrefix = "/h/" + slug.trim('/') + "/"
        val episodes = linkedMapOf<String, HentainiEpisode>()

        doc.select("a[href]").forEach { anchor ->
            val absolute = anchor.absUrl("href").substringBefore('#').trimEnd('/')
            val uri = runCatching { URI(absolute) }.getOrNull() ?: return@forEach
            if (uri.host.orEmpty().lowercase() !in setOf("hentaini.com", "www.hentaini.com")) return@forEach
            val path = uri.path.orEmpty()
            if (!path.startsWith(pathPrefix)) return@forEach
            val number = path.removePrefix(pathPrefix).trim('/').toIntOrNull() ?: return@forEach
            if (number <= 0) return@forEach

            // Prefer an image belonging to this episode card. Only inspect a
            // nearby wrapper when it contains exactly one episode link.
            val image = anchor.selectFirst("img") ?: anchor.parents().firstOrNull { parent ->
                parent.select("a[href]").size == 1 && parent.select("img").isNotEmpty()
            }?.selectFirst("img")
            val thumbnail = image?.let { imageUrl(it) }.orEmpty()
            // Episode-card text often includes counters and unrelated labels
            // (for example "67 AnimeEpisode 1"). Use the URL's episode number
            // as the stable, clean title instead of polluted wrapper text.
            val title = "Episode " + number
            episodes[absolute] = HentainiEpisode(number, title, absolute, thumbnail)
        }

        val urlPattern = Regex("""https?://(?:www\.)?hentaini\.com/h/""" + Regex.escape(slug) + """/(\d+)/?""", RegexOption.IGNORE_CASE)
        urlPattern.findAll(html).forEach { match ->
            val number = match.groupValues[1].toIntOrNull() ?: return@forEach
            val url = match.value.trimEnd('/')
            episodes.putIfAbsent(url, HentainiEpisode(number, "Episode " + number.toString().padStart(2, '0'), url, ""))
        }
        return episodes.values.sortedBy { it.number }
    }

    fun parseEpisode(html: String, pageUrl: String): HentainiEpisodePage {
        val doc = Jsoup.parse(html, pageUrl)
        // Social preview metadata is commonly the SERIES cover on every
        // episode page. Use only media embedded in the episode content itself.
        // Hentaini's episode pages expose the actual frame in VideoObject
        // JSON-LD. og:image/twitter:image are usually the shared series cover.
        val jsonLd = doc.select("script[type=application/ld+json]").mapNotNull { script ->
            parseJsonLd(script.data().ifBlank { script.html() })
        }
        // Only use VideoObject metadata. Other JSON-LD objects can describe
        // the series itself and repeat its cover/providers on every episode.
        val videoObjects = jsonLd.flatMap { findVideoObjects(it) }
        val structuredTitle = videoObjects.asSequence()
            .flatMap { findJsonValues(it, "name").asSequence() + findJsonValues(it, "headline").asSequence() }
            .filterIsInstance<String>()
            .map { it.trim() }
            .firstOrNull { it.isNotBlank() && !it.equals("Hentaini", true) }
        val pageHeading = doc.selectFirst("h1")?.text()?.trim().orEmpty()
        val title = structuredTitle
            ?: pageHeading.takeIf { it.isNotBlank() }
            ?: doc.title().substringBefore("|").trim()
        val structuredThumbnail = videoObjects.asSequence()
            .flatMap { video ->
                sequenceOf("thumbnailUrl", "thumbnail", "image")
                    .flatMap { key -> findJsonValues(video, key).asSequence() }
            }
            .mapNotNull { jsonImageUrl(it, pageUrl) }
            .firstOrNull { isEpisodeImage(it) }
        val thumbnail = sequenceOf(
            structuredThumbnail,
            doc.selectFirst("video[poster]")?.absUrl("poster"),
            doc.selectFirst("article img")?.let { imageUrl(it) },
            doc.selectFirst("main img")?.let { imageUrl(it) },
            doc.selectFirst("[class*=episode] img")?.let { imageUrl(it) },
            doc.selectFirst("[class*=thumbnail] img")?.let { imageUrl(it) }
        ).filterNotNull().firstOrNull { isEpisodeImage(it) }.orEmpty()

        val found = linkedMapOf<String, HentainiStream>()
        fun add(raw: String?, label: String?) {
            val url = normalizeUrl(raw ?: return, pageUrl) ?: return
            val uri = runCatching { URI(url) }.getOrNull() ?: return
            val host = uri.host?.lowercase().orEmpty()
            if (host.isBlank() || host.endsWith("hentaini.com")) return
            // A provider label alone is not enough: it can point to a shared
            // redirect or a page-wide navigation item. The actual URL host
            // must identify a supported file host.
            val known = knownHosts.firstOrNull { host.contains(it) } ?: return
            val provider = known?.let {
                when {
                    it.contains("terabox") -> "1024terabox"
                    it == "mega.nz" -> "MEGA"
                    it == "drive.google" -> "Google Drive"
                    it == "1fichier" -> "1fichier"
                    else -> it.replaceFirstChar { c -> c.uppercase() }
                }
            } ?: label?.trim()?.takeIf { it.isNotBlank() } ?: host.removePrefix("www.")
            val key = provider.lowercase()
            if (found.values.none { it.url == url }) found[key] = HentainiStream(provider, url)
        }

        // Extract links from the current VideoObject only. This prevents
        // links belonging to a related episode/series JSON-LD block leaking
        // into every episode card.
        videoObjects.asSequence().flatMap { findAllJsonStrings(it).asSequence() }.forEach { value ->
            val candidate = value.trim().replace("\\/", "/").replace("\\u0026", "&")
            val host = runCatching { URI(candidate).host.orEmpty() }.getOrDefault("")
            if (knownHosts.any { host.contains(it) }) add(candidate, null)
        }

        // DOM fallback is restricted to episode content and actual supported
        // provider hosts; skip global header/footer/sidebar navigation.
        val contentRoots = doc.select("main, article, .entry-content, .episode-content, [class*=episode-content], [class*=download-links], [class*=download], [id*=download]")
        val scanRoots = contentRoots.ifEmpty { listOfNotNull(doc.body()) }.distinct()
        scanRoots.forEach { scanRoot ->
            scanRoot.select("a[href], iframe[src], [data-url], [data-href], [data-link], [data-download-url], button, [role=button]").forEach { element ->
                val label = element.text().ifBlank { element.attr("title") }.ifBlank { element.attr("aria-label") }
                listOf("href", "src", "data-url", "data-href", "data-link", "data-download-url", "data-src").forEach { key ->
                    val value = element.attr(key)
                    if (value.isNotBlank()) add(value, label)
                }
                val onclick = element.attr("onclick")
                Regex("""['"]((?:https?:)?//[^'"]+)['"]""").findAll(onclick).forEach {
                    add(it.groupValues[1], label)
                }
            }
        }

        // Some pages expose the same episode's provider URLs in a player
        // configuration script rather than JSON-LD. Only inspect scripts
        // when structured VideoObject metadata is absent.
        if (found.isEmpty()) {
            // Player configurations often live in page-specific scripts beside
            // a VideoObject that contains only title/thumbnail metadata.
            // Search scripts even when VideoObject exists, but accept only
            // explicit supported-provider URLs and deduplicate them.
            val scripts = doc.select("script").joinToString("\n") { it.data() + it.html() }
                .replace("\\/", "/")
                .replace("\\u0026", "&")
                .replace("\\u002f", "/")
                .replace("\\u002F", "/")
                .replace("&amp;", "&")
                .replace("&#038;", "&")
            Regex("""https?://[^\s"'<>\\]+""").findAll(scripts).forEach { match ->
                val candidate = match.value.trimEnd(',', ';', ')', ']', '}')
                val host = runCatching { URI(candidate).host.orEmpty() }.getOrDefault("")
                if (knownHosts.any { host.contains(it) }) add(candidate, null)
            }
        }

        return HentainiEpisodePage(title, thumbnail, found.values.sortedBy { it.host.lowercase() })
    }

    private fun parseJsonLd(source: String): Any? {
        val value = source.trim().removePrefix("\uFEFF")
        if (value.isBlank()) return null
        return runCatching {
            when (value.first()) {
                '[' -> JSONArray(value)
                '{' -> JSONObject(value)
                else -> null
            }
        }.getOrNull()
    }

    private fun findVideoObjects(value: Any?, depth: Int = 0): List<JSONObject> {
        if (depth > 20) return emptyList()
        val result = mutableListOf<JSONObject>()
        when (value) {
            is JSONObject -> {
                val type = value.opt("@type")
                val isVideo = when (type) {
                    is String -> type.equals("VideoObject", true) || type.endsWith("/VideoObject", true) ||
                        type.endsWith("#VideoObject", true)
                    is JSONArray -> (0 until type.length()).any {
                        val candidate = type.optString(it)
                        candidate.equals("VideoObject", true) ||
                            candidate.endsWith("/VideoObject", true) ||
                            candidate.endsWith("#VideoObject", true)
                    }
                    else -> false
                }
                if (isVideo) result.add(value)
                val keys = value.keys()
                while (keys.hasNext()) result.addAll(findVideoObjects(value.opt(keys.next()), depth + 1))
            }
            is JSONArray -> for (i in 0 until value.length()) {
                result.addAll(findVideoObjects(value.opt(i), depth + 1))
            }
        }
        return result
    }

    private fun findJsonValues(value: Any?, key: String, depth: Int = 0): List<Any> {
        if (depth > 20) return emptyList()
        val result = mutableListOf<Any>()
        when (value) {
            is JSONObject -> {
                val keys = value.keys()
                while (keys.hasNext()) {
                    val childKey = keys.next()
                    val child = value.opt(childKey)
                    if (childKey.equals(key, ignoreCase = true) && child != null && child != JSONObject.NULL) {
                        result.add(child)
                    }
                    result.addAll(findJsonValues(child, key, depth + 1))
                }
            }
            is JSONArray -> for (i in 0 until value.length()) {
                result.addAll(findJsonValues(value.opt(i), key, depth + 1))
            }
        }
        return result
    }

    private fun findAllJsonStrings(value: Any?, depth: Int = 0): List<String> {
        if (depth > 20) return emptyList()
        val result = mutableListOf<String>()
        when (value) {
            is String -> result.add(value)
            is JSONObject -> {
                val keys = value.keys()
                while (keys.hasNext()) result.addAll(findAllJsonStrings(value.opt(keys.next()), depth + 1))
            }
            is JSONArray -> for (i in 0 until value.length()) {
                result.addAll(findAllJsonStrings(value.opt(i), depth + 1))
            }
        }
        return result
    }

    private fun jsonImageUrl(value: Any, base: String): String? {
        val raw = when (value) {
            is String -> value
            is JSONObject -> value.optString("url")
                .ifBlank { value.optString("contentUrl") }
                .ifBlank { value.optString("@id") }
            is JSONArray -> (0 until value.length())
                .asSequence()
                .mapNotNull { jsonImageUrl(value.opt(it) ?: return@mapNotNull null, base) }
                .firstOrNull().orEmpty()
            else -> ""
        }
        if (raw.isBlank()) return null
        return normalizeUrl(raw, base)
    }

    private fun isEpisodeImage(url: String): Boolean {
        if (!url.startsWith("http://") && !url.startsWith("https://")) return false
        val filename = url.substringAfterLast('/').substringBefore('?')
        return !filename.contains("logo", true) &&
            !filename.contains("avatar", true) &&
            !filename.contains("cover", true)
    }

    private fun imageUrl(image: org.jsoup.nodes.Element): String {
        val direct = sequenceOf(
            "data-lazy-src", "data-src", "data-original", "data-thumb",
            "data-echo", "data-lazy", "src"
        ).map { image.absUrl(it) }
            .firstOrNull { it.startsWith("http://") || it.startsWith("https://") }
        if (direct != null) return direct
        val srcset = image.attr("data-srcset").ifBlank { image.attr("srcset") }
        val candidate = srcset.split(',').firstOrNull()?.trim()?.substringBefore(' ') ?: return ""
        return normalizeUrl(candidate, image.baseUri()).orEmpty()
    }

    private fun normalizeUrl(raw: String, base: String): String? {
        var value = raw.trim().replace("&amp;", "&").replace("\\/", "/").trim(' ', '"', '\'')
        if (value.startsWith("//")) value = "https:" + value
        if (value.startsWith("/")) value = URI(base).resolve(value).toString()
        if (!value.startsWith("http://") && !value.startsWith("https://")) return null
        return runCatching { URI(value).takeIf { it.host != null }?.toString() }.getOrNull()
    }
}
