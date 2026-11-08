package com.xanime.app

import org.jsoup.Jsoup
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
        val thumbnail = sequenceOf(
            doc.selectFirst("video[poster]")?.absUrl("poster"),
            doc.selectFirst("article img")?.let { imageUrl(it) },
            doc.selectFirst("main img")?.let { imageUrl(it) },
            doc.selectFirst("[class*=episode] img")?.let { imageUrl(it) },
            doc.selectFirst("[class*=thumbnail] img")?.let { imageUrl(it) }
        ).filterNotNull().firstOrNull {
            (it.startsWith("http://") || it.startsWith("https://")) &&
                !it.substringAfterLast('/').contains("logo", true) &&
                !it.substringAfterLast('/').contains("avatar", true)
        }.orEmpty()

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
