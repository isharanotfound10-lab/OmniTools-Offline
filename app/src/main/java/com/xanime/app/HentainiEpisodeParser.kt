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
            val title = sequenceOf(
                anchor.text().trim(),
                anchor.attr("title").trim(),
                image?.attr("alt")?.trim().orEmpty()
            ).firstOrNull { it.isNotBlank() } ?: "Episode " + number.toString().padStart(2, '0')
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
        val title = doc.selectFirst("h1")?.text()?.trim()
            ?.takeIf { it.isNotBlank() } ?: doc.title().substringBefore("|").trim()
        // Social preview metadata is commonly the SERIES cover on every
        // episode page. Use only media embedded in the episode content itself.
        // Hentaini's episode pages expose the actual frame in VideoObject
        // JSON-LD. og:image/twitter:image are usually the shared series cover.
        val jsonLd = doc.select("script[type=application/ld+json]").mapNotNull { script ->
            parseJsonLd(script.data().ifBlank { script.html() })
        }
        val structuredThumbnail = jsonLd.asSequence()
            .flatMap { findJsonValues(it, "thumbnailUrl").asSequence() }
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
            val text = label.orEmpty().lowercase()
            val known = knownHosts.firstOrNull { host.contains(it) || text.contains(it) }
            val genericLabels = setOf("download", "download now", "stream", "open", "click here", "about us", "home", "contact", "privacy policy", "terms")
            if (known == null && (text.isBlank() || text in genericLabels || text.length > 40)) return
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

        doc.select("a[href], iframe[src], [data-url], [data-href], [data-link], [data-download-url]").forEach { element ->
            val label = element.text().ifBlank { element.attr("title") }.ifBlank { element.attr("aria-label") }
            listOf("href", "src", "data-url", "data-href", "data-link", "data-download-url").forEach { key ->
                val value = element.attr(key)
                if (value.isNotBlank()) add(value, label)
            }
            val onclick = element.attr("onclick")
            Regex("""['"]((?:https?:)?//[^'"]+)['"]""").findAll(onclick).forEach {
                add(it.groupValues[1], label)
            }
        }

        val scripts = doc.select("script").joinToString("\n") { it.data() + it.html() }
            .replace("\\/", "/")
            .replace("\\u0026", "&")
            .replace("&amp;", "&")
        // Structured episode metadata can also contain provider URLs nested
        // in arrays/objects. Extract only hosts we explicitly recognize.
        jsonLd.asSequence().flatMap { findAllJsonStrings(it).asSequence() }.forEach { value ->
            val candidate = value.trim()
            val host = runCatching { URI(candidate).host.orEmpty() }.getOrDefault("")
            if (knownHosts.any { host.contains(it) }) add(candidate, null)
        }
        Regex("""https?://[^\s"'<>\\]+""").findAll(scripts).forEach { match ->
            val candidate = match.value.trimEnd(',', ';', ')', ']', '}')
            val host = runCatching { URI(candidate).host.orEmpty() }.getOrDefault("")
            if (knownHosts.any { host.contains(it) }) add(candidate, null)
        }

        doc.select("button, [role=button]").forEach { button ->
            val label = button.text().trim()
            if (label.isBlank()) return@forEach
            button.select("a[href], [data-url], [data-href]").forEach { child ->
                add(child.attr("href").ifBlank { child.attr("data-url") }.ifBlank { child.attr("data-href") }, label)
            }
            val data = sequenceOf("data-url", "data-href", "data-link", "data-download-url")
                .map { button.attr(it) }.firstOrNull { it.isNotBlank() }
            if (data != null) add(data, label)
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
            is JSONObject -> value.optString("url").ifBlank { value.optString("contentUrl") }
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

    private fun imageUrl(image: org.jsoup.nodes.Element): String =
        sequenceOf("data-lazy-src", "data-src", "data-original", "src", "data-thumb")
            .map { image.absUrl(it) }
            .firstOrNull { it.startsWith("http://") || it.startsWith("https://") }
            .orEmpty()

    private fun normalizeUrl(raw: String, base: String): String? {
        var value = raw.trim().replace("&amp;", "&").replace("\\/", "/").trim(' ', '"', '\'')
        if (value.startsWith("//")) value = "https:" + value
        if (value.startsWith("/")) value = URI(base).resolve(value).toString()
        if (!value.startsWith("http://") && !value.startsWith("https://")) return null
        return runCatching { URI(value).takeIf { it.host != null }?.toString() }.getOrNull()
    }
}
