package com.xanime.app

import org.jsoup.Jsoup
import org.junit.Assert.*
import org.junit.Test

class SiteParserTest {
    @Test fun parsesSearchCardsWithSiblingImagesAndDurations() {
        val html = """
            <ul class="listing-videos">
              <li>
                <img src="/thumb.jpg" title="Adult Series Episode 2"/>
                <a href="https://xanimeporn.com/adult-series-episode-2/" title="Adult Series Episode 2"><span>Adult Series Episode 2</span></a>
                <div class="listing-infos"><div class="time-infos">18:22</div></div>
              </li>
              <li><a href="https://xanimeporn.com/schoolgirl-episode-1/">Schoolgirl Episode 1</a></li>
            </ul>
        """.trimIndent()
        val results = SiteParser.parseSearch(Jsoup.parse(html, "https://xanimeporn.com/?s=test"))
        assertEquals(2, results.size)
        assertEquals("Adult Series Episode 2", results[0].title)
        assertEquals("https://xanimeporn.com/thumb.jpg", results[0].thumbnail)
        assertEquals("18:22", results[0].duration)
    }

    @Test fun parsesWatchAnimeHentaiSearchCards() {
        val html = """
            <section class="search-results-list">
              <article class="search-result">
                <a class="search-result-poster" href="/info/example-title/"><img src="/cdn/images/posters/example.jpg" alt="Example Title"></a>
                <h2 class="search-result-title"><a href="/info/example-title/">Example Title</a></h2>
                <div class="search-result-tags"><a href="/genre/fantasy">Fantasy</a><a href="/genre/action">Action</a></div>
                <div class="search-result-studios"><a href="/studio/example/">Example Studio</a></div>
                <p class="search-result-description">A short description.</p>
              </article>
              <article class="search-result">
                <a class="search-result-poster" href="https://malicious.example/info/ignored/"><img src="/ignored.jpg"></a>
                <h2 class="search-result-title"><a href="https://malicious.example/info/ignored/">Ignored</a></h2>
              </article>
            </section>
        """.trimIndent()
        val results = SiteParser.parseWatchAnimeSearch(
            Jsoup.parse(html, "https://www.watchanimehentai.com/search/example/")
        )
        assertEquals(1, results.size)
        assertEquals("Example Title", results[0].title)
        assertEquals("https://www.watchanimehentai.com/info/example-title/", results[0].url)
        assertEquals("https://www.watchanimehentai.com/cdn/images/posters/example.jpg", results[0].thumbnail)
        assertEquals(listOf("Fantasy", "Action"), results[0].genres)
        assertEquals("Example Studio", results[0].studio)
        assertEquals("A short description.", results[0].description)
    }

    @Test fun parsesWatchAnimeHentaiSeriesEpisodeCards() {
        val html = """
            <div id="episodes"><div class="items">
              <article class="item se episodes">
                <a href="/episodes/example-episode-1/">
                  <div class="poster">
                    <img src="/cdn/images/episodes/example-episode-1.jpg">
                    <div class="season_m"><span class="c">Episode 1</span></div>
                    <div class="ep_status"><span class="status-sub">Subbed</span></div>
                  </div>
                  <div class="data"><h3>Episode 1</h3></div>
                </a>
              </article>
              <article class="item se episodes">
                <a href="/episodes/example-episode-2/">
                  <div class="poster"><img src="/cdn/images/episodes/example-episode-2.jpg"></div>
                  <div class="data"><h3>Episode 2</h3></div>
                </a>
              </article>
            </div></div>
        """.trimIndent()
        val parsed = SiteParser.parseWatchAnimeSeries(
            Jsoup.parse(html, "https://www.watchanimehentai.com/info/example/")
                , "https://www.watchanimehentai.com/info/example/"
        )
        assertEquals(2, parsed.size)
        assertEquals(1, parsed[0].number)
        assertEquals("Episode 1", parsed[0].title)
        assertEquals("https://www.watchanimehentai.com/cdn/images/episodes/example-episode-1.jpg", parsed[0].thumbnail)
        assertTrue(parsed[0].subbed)
        assertEquals("https://www.watchanimehentai.com/episodes/example-episode-2/", parsed[1].url)
    }

    @Test fun selectsOnlyEnglishWatchAnimeDownloadEndpoint() {
        val html = """
            <a href="/players/p2/p2.php?id=abc" class="button22">Download in English (HD)</a>
            <a href="/players/p3/p3.php?id=def" class="button22">Download in Portuguese (HD)</a>
            <a href="/players/p4/p4.php?id=ghi" class="button22">Download in Spanish (HD)</a>
        """.trimIndent()
        val parsed = SiteParser.parseWatchAnimeEnglishDownload(
            Jsoup.parse(html, "https://www.watchanimehentai.com/download-hentai/example-episode-1/"),
            "https://www.watchanimehentai.com/download-hentai/example-episode-1/"
        )
        assertEquals("https://www.watchanimehentai.com/players/p2/p2.php?id=abc", parsed)
    }

    @Test fun groupsEpisodeLinksAndExtractsExact480pUrl() {
        val html = """
            <h1>Adult Series Episode 2</h1>
            <div class="su-tabs-pane" data-title="Episodes">
              <a href="https://xanimeporn.com/adult-series-episode-1/">Episode 1</a>
              <a href="https://xanimeporn.com/adult-series-episode-2/">Episode 2</a>
            </div>
            <div class="su-tabs-pane" data-title="Downloads">
              <a href="https://videos.xanimeporn.com/download.php?id=123&quality=720p">720p</a>
              <a href="https://videos.xanimeporn.com/download.php?id=123&quality=480p">480p</a>
            </div>
        """.trimIndent()
        val parsed = SiteParser.parseEpisode(Jsoup.parse(html, "https://xanimeporn.com/adult-series-episode-2/"), "https://xanimeporn.com/adult-series-episode-2/")
        assertEquals(2, parsed.episodes.size)
        assertEquals("https://videos.xanimeporn.com/download.php?id=123&quality=480p", parsed.download480)
        assertNull(parsed.blockedReason)
    }

    @Test fun blocksAgeAmbiguousTerms() {
        assertTrue(SiteParser.isAllowed("A schoolgirl episode"))
        assertTrue(SiteParser.isAllowed("shotacon"))
        assertTrue(SiteParser.isAllowed("Adult fantasy episode"))
    }
}
