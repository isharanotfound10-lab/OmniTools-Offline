package com.xanime.app

import org.jsoup.nodes.Document

data class HentaiReadComic(
    val title: String,
    val url: String,
    val cover: String,
    val genres: List<String>,
    val rating: String
)

data class HentaiReadDetails(
    val title: String,
    val url: String,
    val cover: String,
    val description: String,
    val readerUrl: String
)

object HentaiReadParser {
    fun parseListing(doc: Document): List<HentaiReadComic> {
        val found = LinkedHashMap<String, HentaiReadComic>()
        doc.select(".manga-item").forEach { card ->
            val anchor = card.selectFirst(".manga-item__link[href]")
                ?: card.selectFirst("a[href]") ?: return@forEach
            val url = anchor.absUrl("href")
            if (!url.startsWith("https://hentairead.com/hentai/")) return@forEach
            val image = card.selectFirst(".manga-item__img-inner img, img")
            val cover = image?.let {
                sequenceOf("data-lazy-src", "data-src", "data-original", "src")
                    .map { key -> it.absUrl(key) }
                    .firstOrNull { value -> value.startsWith("https://") }
            }.orEmpty().ifBlank {
                val style = card.selectFirst(".manga-item__img-inner")?.attr("style").orEmpty()
                Regex("""url\\(['"]?(https?://[^)'"]+)""").find(style)?.groupValues?.getOrNull(1).orEmpty()
            }
            val title = sequenceOf(
                anchor.attr("title").trim(),
                anchor.text().trim(),
                image?.attr("alt")?.trim().orEmpty(),
                image?.attr("title")?.trim().orEmpty()
            ).firstOrNull { it.isNotBlank() }.orEmpty()
            if (title.isBlank()) return@forEach
            val genres = card.select(".manga-item__cat-link")
                .map { it.text().trim() }.filter { it.isNotBlank() }
            val rating = card.selectFirst(".manga-item__rating")?.text()?.trim().orEmpty()
            found[url] = HentaiReadComic(title, url, cover, genres, rating)
        }
        return found.values.toList()
    }

    fun parseDetails(doc: Document, pageUrl: String): HentaiReadDetails {
        val title = doc.selectFirst("h1")?.text()?.trim()
            ?.takeIf { it.isNotBlank() }
            ?: doc.selectFirst("meta[property=og:title]")?.attr("content")?.trim().orEmpty()
            .ifBlank { doc.title().substringBefore("|").trim() }
        val cover = doc.selectFirst("meta[property=og:image]")?.absUrl("content")
            ?.takeIf { it.startsWith("https://") }
            ?: doc.selectFirst(".manga-item__img-inner img, .summary_image img, img.wp-post-image")
                ?.let { sequenceOf("data-src", "data-lazy-src", "src").map { key -> it.absUrl(key) }.firstOrNull { it.startsWith("https://") } }
                .orEmpty()
        val description = doc.selectFirst(".manga-item__description, .description, .entry-content, .summary__content")
            ?.text()?.trim().orEmpty()
        val reader = doc.select("a[href]").firstOrNull {
            val href = it.absUrl("href")
            href.startsWith("https://hentairead.com/") &&
                (href.contains("/english/") || href.contains("/reader/"))
        }?.absUrl("href") ?: pageUrl.trimEnd('/') + "/english/p/1/"
        return HentaiReadDetails(title, pageUrl, cover, description, reader)
    }

    fun readerPageUrl(detailsUrl: String, page: Int): String =
        detailsUrl.trimEnd('/') + "/english/p/" + page.coerceAtLeast(1) + "/"
}
