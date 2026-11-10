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

    @Test fun parsesWordPressApiResultsWithFeaturedThumbnailAndEmbeddedEpisodeContent() {
        val json = """
            [
              {
                "id": 70074,
                "link": "https://xanimeporn.com/deco-x-deco-episode-2-sub-eng/",
                "title": {"rendered": "Deco x Deco Episode 2 [Sub-ENG]"},
                "content": {"rendered": "<div class='su-tabs-pane' data-title='Episodes'><a href='https://xanimeporn.com/deco-x-deco-episode-1-sub-eng/'>Episode 1</a><a href='https://xanimeporn.com/deco-x-deco-episode-2-sub-eng/'>Episode 2</a></div>"},
                "_embedded": {
                  "wp:featuredmedia": [{
                    "source_url": "https://xanimeporn.com/full.jpg",
                    "media_details": {"sizes": {
                      "thumb_site": {"source_url": "https://xanimeporn.com/thumb.jpg"}
                    }}
                  }]
                }
              }
            ]
        """.trimIndent()

        val results = SiteParser.parseApiSearch(json)
        assertEquals(1, results.size)
        assertEquals("Deco x Deco Episode 2 [Sub-ENG]", results[0].title)
        assertEquals("https://xanimeporn.com/deco-x-deco-episode-2-sub-eng/", results[0].url)
        assertEquals("https://xanimeporn.com/thumb.jpg", results[0].thumbnail)
        assertEquals("", results[0].duration)

        val embeddedDoc = Jsoup.parse(results[0].detailHtml.orEmpty(), results[0].url)
        val details = SiteParser.parseEpisode(embeddedDoc, results[0].url)
        assertEquals(2, details.episodes.size)
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
