package com.xanime.app

import org.json.JSONArray
import org.json.JSONObject
import org.jsoup.Jsoup
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

data class HentainiSeries(
    val id: Int,
    val title: String,
    val slug: String,
    val hId: String,
    val hasEpisodes: Boolean,
    val thumbnailUrl: String
) {
    val pageUrl: String
        get() = "https://hentaini.com/h/" + slug
}

data class HentainiSearchPage(
    val results: List<HentainiSeries>,
    val total: Int,
    val offset: Int,
    val limit: Int
) {
    val hasMore: Boolean get() = offset + results.size < total
}

object HentainiApi {
    private const val API_ROOT = "https://admin.hentaini.com/api"
    private const val IMAGE_ROOT = "https://admin.hentaini.com/uploads/"

    fun searchGenre(slug: String, offset: Int, limit: Int = 10): HentainiSearchPage {
        // MainActivity supplies genre slugs for every visible chip. Convert labels
        // such as "big breast" or "small breasts" to the site's hyphenated URL form.
        // Do not maintain a hard-coded allow-list: newly added chips should work too.
        val normalizedSlug = slug.trim()
            .lowercase()
            .replace(Regex("\\s+"), "-")
            .replace(Regex("-+"), "-")
        require(normalizedSlug.matches(Regex("[a-z0-9]+(?:-[a-z0-9]+)*"))) {
            "Invalid Hentaini genre slug: " + slug
        }
        val pageNumber = offset / limit + 1
        val address = "https://hentaini.com/genres/" + normalizedSlug + "?page=" + pageNumber
        val document = Jsoup.parse(request(address, "text/html"), address)
        val results = linkedMapOf<String, HentainiSeries>()
        document.select("a[href]").forEach { anchor ->
            val target = anchor.absUrl("href")
            val uri = runCatching { java.net.URI(target) }.getOrNull() ?: return@forEach
            if (uri.host.orEmpty().lowercase() !in setOf("hentaini.com", "www.hentaini.com")) return@forEach
            val parts = uri.path.orEmpty().trim('/').split('/')
            if (parts.size != 2 || parts[0] != "h") return@forEach
            val slugValue = parts[1]
            if (slugValue.isBlank()) return@forEach
            val image = anchor.selectFirst("img")
            val title = anchor.attr("title").ifBlank { image?.attr("alt").orEmpty() }
                .ifBlank { anchor.text() }.trim()
            if (title.isBlank()) return@forEach
            val imageUrl = image?.let {
                it.absUrl("src").ifBlank { it.absUrl("data-src") }
                    .ifBlank { it.absUrl("data-lazy-src") }
            }.orEmpty()
            val stableId = slugValue.hashCode() and Int.MAX_VALUE
            results.putIfAbsent(slugValue, HentainiSeries(
                id = stableId, title = title, slug = slugValue, hId = "",
                hasEpisodes = true, thumbnailUrl = imageUrl
            ))
        }
        val items = results.values.toList().take(limit)
        val hasNext = document.select("a[rel=next], a.next, .pagination a").any { link ->
            link.text().contains("next", true) || link.text().contains("›") || link.text().contains("»")
        }
        val total = if (hasNext || items.size == limit) offset + items.size + 1 else offset + items.size
        return HentainiSearchPage(items, total, offset, limit)
    }

    fun search(term: String, offset: Int, limit: Int = 10): HentainiSearchPage {
        val encoded = URLEncoder.encode(term, "UTF-8")
        val address = API_ROOT + "/series?filters%5Btitle%5D%5B%24containsi%5D=" + encoded +
            "&populate%5B0%5D=images&populate%5B1%5D=images.image_type" +
            "&pagination%5Bstart%5D=" + offset + "&pagination%5Blimit%5D=" + limit
        val json = JSONObject(request(address, "application/json"))
        return parseSearchResponse(json, offset, limit)
    }

    fun parseSearchResponse(json: JSONObject, requestedOffset: Int = 0, requestedLimit: Int = 10): HentainiSearchPage {
        val data = json.optJSONArray("data") ?: JSONArray()
        val results = (0 until data.length()).mapNotNull { index ->
            parseSeries(data.optJSONObject(index))
        }
        val pagination = json.optJSONObject("meta")?.optJSONObject("pagination")
        val total = pagination?.optInt("total", requestedOffset + results.size)
            ?: (requestedOffset + results.size)
        val offset = pagination?.optInt("start", requestedOffset) ?: requestedOffset
        val limit = pagination?.optInt("limit", requestedLimit) ?: requestedLimit
        return HentainiSearchPage(results, total, offset, limit)
    }

    fun parseSeries(item: JSONObject?): HentainiSeries? {
        if (item == null) return null
        val fields = item.optJSONObject("attributes") ?: item
        val id = item.optInt("id", fields.optInt("id", -1))
        val title = fields.optString("title").trim()
        val slug = fields.optString("url").ifBlank { fields.optString("slug") }.trim()
        if (id < 0 || title.isBlank() || slug.isBlank()) return null

        val images = fields.optJSONArray("images")
            ?: fields.optJSONObject("images")?.optJSONArray("data")
        var selectedPath = ""
        var firstPath = ""
        if (images != null) {
            for (i in 0 until images.length()) {
                val image = images.optJSONObject(i) ?: continue
                val imageFields = image.optJSONObject("attributes") ?: image
                val path = imageFields.optString("path")
                    .ifBlank { imageFields.optString("url") }
                    .ifBlank {
                        imageFields.optJSONObject("formats")?.optJSONObject("thumbnail")?.optString("url").orEmpty()
                    }
                if (path.isBlank()) continue
                if (firstPath.isBlank()) firstPath = path
                val imageType = imageFields.optJSONObject("image_type")
                    ?: imageFields.optJSONObject("imageType")
                if (imageType?.optString("name") == "cover") {
                    selectedPath = path
                    break
                }
            }
        }
        val imagePath = selectedPath.ifBlank { firstPath }
        val thumbnail = when {
            imagePath.isBlank() -> ""
            imagePath.startsWith("http://") || imagePath.startsWith("https://") -> imagePath
            imagePath.startsWith("/") -> "https://admin.hentaini.com" + imagePath
            else -> IMAGE_ROOT + imagePath
        }
        return HentainiSeries(
            id = id,
            title = title,
            slug = slug,
            hId = fields.optString("h_id"),
            hasEpisodes = fields.optBoolean("hasEpisodes", false),
            thumbnailUrl = thumbnail
        )
    }

    /**
     * Search metadata only exposes hasEpisodes, not a count. Probe likely
     * public episode endpoints and the public series page. Unknown stays null.
     */
    fun countEpisodes(series: HentainiSeries): Int? {
        val id = series.id.toString()
        val candidates = listOf(
            API_ROOT + "/episodes?filters%5Bseries%5D%5Bid%5D%5B%24eq%5D=" + id + "&pagination%5Blimit%5D=1",
            API_ROOT + "/episodes?filters%5Bseries_id%5D%5B%24eq%5D=" + id + "&pagination%5Blimit%5D=1",
            if (series.hId.isNotBlank()) API_ROOT + "/episodes?filters%5Bh_id%5D%5B%24eq%5D=" +
                URLEncoder.encode(series.hId, "UTF-8") + "&pagination%5Blimit%5D=1" else ""
        ).filter { it.isNotBlank() }

        for (candidate in candidates) {
            val parsed = runCatching {
                parseEpisodeCount(JSONObject(request(candidate, "application/json")))
            }.getOrNull()
            if (parsed != null && parsed > 0) return parsed
        }

        val detailCount = runCatching {
            val json = JSONObject(request(API_ROOT + "/series/" + id + "?populate=*", "application/json"))
            countInJson(json)
        }.getOrNull()
        if (detailCount != null && detailCount > 0) return detailCount

        val htmlCount = runCatching {
            countEpisodesInHtml(request(series.pageUrl, "text/html"), series.slug)
        }.getOrNull()
        if (htmlCount != null && htmlCount > 0) return htmlCount

        // The hasEpisodes flag is only a hint and can be stale. Never turn it into an exact count.
        return null
    }

    private fun parseEpisodeCount(json: JSONObject): Int? {
        val pagination = json.optJSONObject("meta")?.optJSONObject("pagination")
        if (pagination?.has("total") == true) return pagination.optInt("total")
        return json.optJSONArray("data")?.length()
    }

    private fun countInJson(value: Any?): Int? {
        if (value is JSONObject) {
            val keys = value.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                val child = value.opt(key)
                if (key.equals("episodes", ignoreCase = true) ||
                    key.equals("episodeList", ignoreCase = true) ||
                    key.equals("episode_list", ignoreCase = true)
                ) {
                    if (child is JSONArray) return child.length()
                    if (child is JSONObject) {
                        val data = child.optJSONArray("data")
                        if (data != null) return data.length()
                    }
                }
                val nested = countInJson(child)
                if (nested != null) return nested
            }
        } else if (value is JSONArray) {
            for (i in 0 until value.length()) {
                val nested = countInJson(value.opt(i))
                if (nested != null) return nested
            }
        }
        return null
    }

    private fun countEpisodesInHtml(html: String, slug: String): Int {
        val document = Jsoup.parse(html)
        val episodeLinks = linkedSetOf<String>()
        document.select("a[href]").forEach { link ->
            val href = link.attr("href")
            val text = link.text()
            val underSeriesPath = href.contains("/h/" + slug + "/") && href.trimEnd('/') != "/h/" + slug
            val episodeLike = href.contains("/episode", ignoreCase = true) ||
                href.contains("/ep-", ignoreCase = true) ||
                Regex("(?i)\\bepisode\\s*#?\\d+").containsMatchIn(text)
            if (underSeriesPath || episodeLike) episodeLinks.add(href)
        }
        return episodeLinks.size
    }


    fun loadEpisodes(series: HentainiSeries): List<HentainiEpisode> {
        val html = request(series.pageUrl, "text/html")
        return HentainiEpisodeParser.parseSeries(html, series.pageUrl, series.slug)
    }

    fun loadEpisodePage(episode: HentainiEpisode): HentainiEpisodePage {
        val html = request(episode.url, "text/html")
        // Exclude the series-list image so it cannot silently stand in for
        // a missing screenshot belonging to the current episode.
        val parsed = HentainiEpisodeParser.parseEpisode(html, episode.url, episode.thumbnailUrl)
        if (parsed.streams.isNotEmpty()) return parsed

        // The site's Download control can lead to a separate page containing
        // the host choices. Follow only same-site download pages.
        val downloadPage = runCatching {
            val document = Jsoup.parse(html, episode.url)
            val episodeUri = java.net.URI(episode.url)
            val episodePath = episodeUri.path.orEmpty().trimEnd('/')
            document.select("main a[href], article a[href], .entry-content a[href], .download a[href], a[href]")
                .firstOrNull { anchor ->
                    val target = anchor.absUrl("href")
                    val targetUri = runCatching { java.net.URI(target) }.getOrNull()
                        ?: return@firstOrNull false
                    val sameSite = targetUri.host.orEmpty().lowercase() in
                        setOf("hentaini.com", "www.hentaini.com")
                    val targetPath = targetUri.path.orEmpty()
                    // Only accept a download page nested under this exact
                    // episode URL; a matching numeric segment elsewhere is ambiguous.
                    val belongsToEpisode = targetPath.startsWith(episodePath + "/")
                    anchor.text().contains("download", ignoreCase = true) &&
                        sameSite && target != episode.url && belongsToEpisode
                }?.absUrl("href")
        }.getOrNull()
        if (downloadPage.isNullOrBlank()) return parsed

        val downloadHtml = runCatching { request(downloadPage, "text/html") }.getOrNull()
            ?: return parsed
        val downloadParsed = HentainiEpisodeParser.parseEpisode(
            downloadHtml, downloadPage, episode.thumbnailUrl
        )
        return parsed.copy(streams = downloadParsed.streams)
    }

    private fun request(address: String, accept: String): String {
        val connection = (URL(address).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 7000
            readTimeout = 10000
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 Chrome/124.0 Mobile Safari/537.36")
            setRequestProperty("Accept", accept)
        }
        return try {
            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val body = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
            if (code !in 200..299) throw IllegalStateException("Hentaini request failed: HTTP " + code)
            body
        } finally {
            connection.disconnect()
        }
    }
}
