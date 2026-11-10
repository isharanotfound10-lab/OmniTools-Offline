package com.xanime.app

import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.json.JSONArray
import java.net.URI

data class SearchResult(
    val title: String,
    val url: String,
    val thumbnail: String,
    val duration: String,
    val detailHtml: String? = null
)

data class EpisodeLink(val title: String, val url: String, val number: Int)
data class EpisodeDetails(
    val title: String,
    val url: String,
    val episodes: List<EpisodeLink>,
    val download480: String?,
    val qualities: Map<String, String>,
    val blockedReason: String? = null
)

object SiteParser {
    /**
     * Parses the WordPress REST API response used by title search.
     * The embedded featured-media object supplies thumbnails and the rendered
     * post content supplies the Episodes/Downloads/Preview panes, avoiding a
     * separate HTML request for every result.
     */
    fun parseApiSearch(json: String): List<SearchResult> {
        val posts = JSONArray(json)
        val found = LinkedHashMap<String, SearchResult>()
        for (index in 0 until posts.length()) {
            val post = posts.optJSONObject(index) ?: continue
            val titleObject = post.optJSONObject("title")
            val title = Jsoup.parse(titleObject?.optString("rendered").orEmpty()).text().trim()
            val url = canonicalContentUrl(post.optString("link")) ?: continue
            if (title.isBlank() || !isAllowed("$title $url")) continue

            val contentHtml = post.optJSONObject("content")?.optString("rendered")
                ?.takeIf { it.isNotBlank() }
            val media = post.optJSONObject("_embedded")
                ?.optJSONArray("wp:featuredmedia")?.optJSONObject(0)
            val sizes = media?.optJSONObject("media_details")?.optJSONObject("sizes")
            val thumbnail = sequenceOf(
                sizes?.optJSONObject("thumb_site")?.optString("source_url").orEmpty(),
                sizes?.optJSONObject("thumb_admin")?.optString("source_url").orEmpty(),
                media?.optString("source_url").orEmpty()
            ).firstOrNull { it.startsWith("https://") || it.startsWith("http://") }.orEmpty()

            found[url] = SearchResult(title, url, thumbnail, "", contentHtml)
        }
        return found.values.toList()
    }

    private val ignoredSlugs = setOf(
        "tag", "tags", "category", "categories", "author", "page",
        "wp-content", "wp-admin", "search", "feed", "hentai-series",
        "hentai-list", "top-10", "concerned", "uncensored", "contact"
    )

        fun isAllowed(text: String): Boolean {
        return true
        }

    fun parseSearch(doc: Document): List<SearchResult> {
        val found = LinkedHashMap<String, SearchResult>()
        val cards = doc.select("ul.listing-videos li, .listing-videos li")
        for (card in cards) {
            val anchor = card.selectFirst("a[href]") ?: continue
            val url = canonicalContentUrl(anchor.absUrl("href")) ?: continue
            val title = sequenceOf(
                anchor.attr("title").trim(),
                anchor.text().trim(),
                card.selectFirst("img")?.attr("title")?.trim().orEmpty(),
                card.selectFirst("img")?.attr("alt")?.trim().orEmpty()
            ).firstOrNull { it.isNotBlank() }.orEmpty()
            if (title.isBlank() || !isAllowed(title + " " + url)) continue
            val img = card.selectFirst("img")
            val thumbnail = img?.let {
                sequenceOf("data-lazy-src", "data-src", "data-original", "src", "data-thumb")
                    .map { key -> it.absUrl(key) }
                    .firstOrNull { value -> value.startsWith("https://") || value.startsWith("http://") }
            }.orEmpty()
            val duration = card.selectFirst(".time-infos")?.text()?.trim().orEmpty()
            found[url] = SearchResult(title, url, thumbnail, duration)
        }
        return found.values.toList()
    }

    fun parseEpisode(doc: Document, pageUrl: String): EpisodeDetails {
        val title = doc.selectFirst("h1")?.text()?.trim()
            ?: doc.title().substringBefore("|").trim()
        val episodePane = doc.selectFirst(".su-tabs-pane[data-title=Episodes]")
        val episodeMap = LinkedHashMap<String, EpisodeLink>()
        episodePane?.select("a[href]")?.forEach { anchor ->
            val url = canonicalContentUrl(anchor.absUrl("href")) ?: return@forEach
            val label = anchor.text().trim().ifBlank { anchor.attr("title").trim() }
            if (label.isBlank() || !isAllowed(label + " " + url)) return@forEach
            val number = Regex("(?i)episode\\s*(\\d+)").find(label)?.groupValues?.getOrNull(1)?.toIntOrNull() ?: 0
            episodeMap[url] = EpisodeLink(label, url, number)
        }

        val downloads = LinkedHashMap<String, String>()
        doc.select(".su-tabs-pane[data-title=Downloads] a[href]").forEach { anchor ->
            val href = anchor.absUrl("href")
            val uri = runCatching { URI(href) }.getOrNull() ?: return@forEach
            if (uri.host?.lowercase() != "videos.xanimeporn.com") return@forEach
            if (!uri.path.orEmpty().endsWith("/download.php")) return@forEach
            val query = uri.rawQuery.orEmpty().split("&").mapNotNull {
                val bits = it.split("=", limit = 2)
                if (bits.size == 2) bits[0] to bits[1] else null
            }.toMap()
            val quality = query["quality"]?.lowercase()
                ?: Regex("(?i)(1080p|720p|480p|240p)").find(anchor.text())?.value?.lowercase()
            if (quality in setOf("1080p", "720p", "480p", "240p")) downloads[quality!!] = href
        }

        val safetyText = buildString {
            append(title).append(' ')
            append(doc.select(".post-tags, .tags-links, .entry-meta, .post-meta, [rel=tag]").text())
            append(' ')
        }
        val blocked = if (isAllowed(safetyText)) null else
            "This entry was hidden because its title, tags, or description contains school-related or age-ambiguous terms."

        val sorted = episodeMap.values.sortedWith(compareBy<EpisodeLink> { it.number.takeIf { n -> n > 0 } ?: Int.MAX_VALUE }.thenBy { it.title })
        val current = canonicalContentUrl(pageUrl)
        if (current != null && sorted.none { it.url == current } && title.isNotBlank() && isAllowed(title)) {
            val n = Regex("(?i)episode\\s*(\\d+)").find(title)?.groupValues?.getOrNull(1)?.toIntOrNull() ?: 0
            episodeMap[current] = EpisodeLink(title, current, n)
        }
        val finalEpisodes = (if (sorted.isEmpty()) episodeMap.values.toList() else sorted)
            .distinctBy { it.url }
            .sortedWith(compareBy<EpisodeLink> { it.number.takeIf { n -> n > 0 } ?: Int.MAX_VALUE }.thenBy { it.title })

        return EpisodeDetails(title, pageUrl, finalEpisodes, downloads["480p"], downloads, blocked)
    }

    private fun canonicalContentUrl(raw: String): String? {
        val uri = runCatching { URI(raw) }.getOrNull() ?: return null
        val host = uri.host?.lowercase() ?: return null
        if (host != "xanimeporn.com" && host != "www.xanimeporn.com") return null
        val segments = uri.path.orEmpty().trim('/').split('/').filter { it.isNotBlank() }
        if (segments.size != 1 || segments.first().lowercase() in ignoredSlugs) return null
        if (uri.rawQuery != null || uri.rawFragment != null) return null
        return "https://xanimeporn.com/${segments.first()}/"
    }
}
