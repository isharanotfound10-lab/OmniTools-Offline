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
        "streamtape", "vidoza", "voe", "dood", "yesterdaymail.com", "yourupload.com", "streamwish.to", "mp4upload.com", "burstcloud.co"
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

    fun parseEpisode(
        html: String,
        pageUrl: String,
        thumbnailToExclude: String = ""
    ): HentainiEpisodePage {
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
        val allVideoObjects = jsonLd.flatMap { findVideoObjects(it) }
        val pageHeading = doc.selectFirst("h1")?.text()?.trim().orEmpty()
        // A page can contain shared/related VideoObjects as well as the
        // current episode. Match canonical identity first; title is a
        // secondary signal. If multiple unrelated objects remain, do not
        // guess and accidentally reuse another episode's media.
        val identityMatches = allVideoObjects.filter { videoObject ->
            videoObjectBelongsToPage(videoObject, pageUrl)
        }
        val titleMatches = allVideoObjects.filter { videoObject ->
            sequenceOf(videoObject.optString("name"), videoObject.optString("headline"))
                .any { it.isNotBlank() && pageHeading.isNotBlank() && it.trim().equals(pageHeading, true) }
        }
        val videoObjects = when {
            identityMatches.isNotEmpty() -> identityMatches
            titleMatches.isNotEmpty() -> titleMatches
            // Never assume a lone VideoObject belongs to this episode.
            // Hentaini can repeat a series-level VideoObject on every episode URL.
            else -> emptyList()
        }
        val structuredTitle = videoObjects.asSequence()
            .flatMap { sequenceOf(it.optString("name"), it.optString("headline")).asSequence() }
            .map { it.trim() }
            .firstOrNull { it.isNotBlank() && !it.equals("Hentaini", true) }
        val title = structuredTitle
            ?: pageHeading.takeIf { it.isNotBlank() }
            ?: doc.title().substringBefore("|").trim()
        val structuredThumbnail = videoObjects.asSequence()
            .flatMap { video ->
                sequenceOf("thumbnailUrl", "thumbnail", "image")
                    .flatMap { key -> findJsonValues(video, key).asSequence() }
            }
            .mapNotNull { jsonImageUrl(it, pageUrl) }
            .firstOrNull { isEpisodeImage(it, thumbnailToExclude) }
        // Hentaini serves episode-specific screenshots through its Nuxt
        // image proxy in og:image/twitter:image metadata. These are not
        // necessarily the series cover, so inspect them before generic DOM
        // images (which can belong to navigation or related content).
        val metadataThumbnail = sequenceOf(
            doc.selectFirst("meta[property=og:image]")?.attr("content"),
            doc.selectFirst("meta[name=twitter:image]")?.attr("content"),
            doc.selectFirst("meta[property=og:image:url]")?.attr("content"),
            doc.selectFirst("link[rel=image_src]")?.attr("href")
        ).filterNotNull()
            .mapNotNull { normalizeUrl(it, pageUrl) }
            .firstOrNull { isEpisodeImage(it, thumbnailToExclude) }
        val thumbnail = sequenceOf(
            structuredThumbnail,
            metadataThumbnail,
            doc.selectFirst("video[poster]")?.absUrl("poster"),
            doc.selectFirst("article img")?.let { imageUrl(it) },
            doc.selectFirst("main img")?.let { imageUrl(it) },
            doc.selectFirst("[class*=episode] img")?.let { imageUrl(it) },
            doc.selectFirst("[class*=thumbnail] img")?.let { imageUrl(it) }
        ).filterNotNull().firstOrNull { isEpisodeImage(it, thumbnailToExclude) }.orEmpty()

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

        // Read players/downloads from the Nuxt SSR episode object, scoped by this page's exact contentUrl.
        extractNuxtEpisodeLinks(html, videoObjects).forEach { (url, label) -> add(url, label) }

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

        // Do not scrape every script on the page: shared bundles and series
        // state can repeat identical provider URLs on every episode.
        // Trust only episode-identified structured data and episode-content DOM links.

        return HentainiEpisodePage(title, thumbnail, found.values.sortedBy { it.host.lowercase() })
    }


    private fun extractNuxtEpisodeLinks(html: String, videoObjects: List<JSONObject>): List<Pair<String, String>> {
        val currentContentUrls = videoObjects.flatMap { findJsonValues(it, "contentUrl") }
            .mapNotNull { (it as? String)?.trim()?.takeIf { value -> value.isNotBlank() } }.toSet()
        if (currentContentUrls.isEmpty()) return emptyList()
        val doc = Jsoup.parse(html)
        val payloadText = doc.selectFirst("script#__NUXT_DATA__")?.data()
            ?.ifBlank { doc.selectFirst("script#__NUXT_DATA__")?.html() }?.trim().orEmpty()
        val payload = runCatching { JSONArray(payloadText) }.getOrNull() ?: return emptyList()
        val cache = mutableMapOf<Int, Any?>()
        for (index in 0 until payload.length()) {
            if (payload.opt(index) !is JSONObject) continue
            val episode = resolveNuxtNode(index, payload, cache, mutableSetOf()) as? JSONObject ?: continue
            val players = runCatching { JSONArray(episode.optString("players")) }.getOrNull() ?: continue
            val urls = (0 until players.length()).mapNotNull { players.optJSONObject(it)?.optString("url") }
            if (currentContentUrls.none { it in urls }) continue
            val result = mutableListOf<Pair<String, String>>()
            for (i in 0 until players.length()) {
                val player = players.optJSONObject(i) ?: continue
                val url = player.optString("url").trim()
                if (url.isNotBlank()) result.add(url to player.optString("name", "Stream"))
            }
            val downloads = runCatching { JSONArray(episode.optString("downloads")) }.getOrNull()
            if (downloads != null) for (i in 0 until downloads.length()) {
                val url = downloads.optJSONObject(i)?.optString("url")?.trim().orEmpty()
                if (url.isNotBlank()) result.add(url to "Download")
            }
            return result
        }
        return emptyList()
    }

    private fun resolveNuxtNode(
        index: Int,
        payload: JSONArray,
        cache: MutableMap<Int, Any?>,
        visiting: MutableSet<Int>
    ): Any? {
        if (index !in 0 until payload.length()) return null
        if (cache.containsKey(index)) return cache[index]
        if (!visiting.add(index)) return null
        val node = payload.opt(index)
        val result: Any? = when (node) {
            is JSONObject -> JSONObject().apply {
                val keys = node.keys()
                while (keys.hasNext()) {
                    val key = keys.next()
                    put(key, resolveNuxtValue(node.opt(key), payload, cache, visiting))
                }
            }
            is JSONArray -> {
                val marker = if (node.length() > 0) node.opt(0) as? String else null
                if (marker in setOf("ShallowReactive", "Reactive", "Ref", "ShallowRef", "ComputedRef", "ReadonlyReactive", "ShallowReadonly")) {
                    resolveNuxtValue(node.opt(1), payload, cache, visiting)
                } else {
                    JSONArray().apply {
                        for (i in 0 until node.length()) put(resolveNuxtValue(node.opt(i), payload, cache, visiting))
                    }
                }
            }
            else -> node
        }
        visiting.remove(index)
        cache[index] = result
        return result
    }

    private fun resolveNuxtValue(
        value: Any?,
        payload: JSONArray,
        cache: MutableMap<Int, Any?>,
        visiting: MutableSet<Int>
    ): Any? = when (value) {
        is Int -> resolveNuxtNode(value, payload, cache, visiting)
        is Long -> if (value >= 0 && value < payload.length()) resolveNuxtNode(value.toInt(), payload, cache, visiting) else value
        else -> value
    }

    private fun videoObjectBelongsToPage(video: JSONObject, pageUrl: String): Boolean {
        val page = runCatching { URI(pageUrl) }.getOrNull() ?: return false
        val identityValues = sequenceOf(
            findJsonValues(video, "url"),
            findJsonValues(video, "@id"),
            findJsonValues(video, "mainEntityOfPage")
        ).flatten()
        return identityValues.any { value ->
            val raw = when (value) {
                is String -> value
                is JSONObject -> value.optString("@id").ifBlank { value.optString("url") }
                else -> ""
            }
            val candidate = normalizeUrl(raw, pageUrl) ?: return@any false
            val uri = runCatching { URI(candidate) }.getOrNull() ?: return@any false
            uri.host.equals(page.host, true) &&
                uri.path.orEmpty().trimEnd('/').equals(page.path.orEmpty().trimEnd('/'), true)
        }
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

    private fun isEpisodeImage(url: String, excluded: String = ""): Boolean {
        if (!url.startsWith("http://") && !url.startsWith("https://")) return false
        if (excluded.isNotBlank() && runCatching {
                URI(url).normalize().toString() == URI(excluded).normalize().toString()
            }.getOrDefault(url == excluded)) return false
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
