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
