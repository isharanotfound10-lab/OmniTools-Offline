package com.xanime.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HentainiEpisodeParserTest {
    @Test
    fun parsesNumberedEpisodesAndThumbnails() {
        val html = """
            <html><body>
              <div class="episodes">
                <a href="/h/sample-series/2"><img src="/ep2.jpg"><span>Episode 02</span></a>
                <a href="/h/sample-series/1"><img data-src="/ep1.jpg"><span>Episode 01</span></a>
              </div>
              <a href="/h/another-series/1">Ignore other series</a>
            </body></html>
        """.trimIndent()

        val episodes = HentainiEpisodeParser.parseSeries(
            html, "https://hentaini.com/h/sample-series", "sample-series"
        )

        assertEquals(listOf(1, 2), episodes.map { it.number })
        assertEquals("Episode 1", episodes.first().title)
        assertEquals("https://hentaini.com/ep1.jpg", episodes.first().thumbnailUrl)
    }

    @Test
    fun extractsOnlyCurrentVideoObjectProviderLinksAndThumbnail() {
        val html = """
            <html><head>
              <meta property="og:image" content="https://cdn.example/series-cover.jpg">
              <script type="application/ld+json">
                {
                  "@context":"https://schema.org",
                  "@graph":[
                    {
                      "@type":"VideoObject",
                      "name":"Episode 01",
                      "thumbnailUrl":"https://cdn.example/episode-01-frame.jpeg",
                      "contentUrl":"https://1024terabox.com/s/episode01",
                      "distribution":{"contentUrl":"https://www.fireload.com/episode01"},
                      "embedUrl":"https://www.mediafire.com/file/episode01",
                      "sameAs":"https://1fichier.com/?episode01"
                    },
                    {
                      "@type":"Organization",
                      "name":"Site",
                      "sameAs":"https://1024terabox.com/s/shared-series-link"
                    }
                  ]
                }
              </script>
            </head><body>
              <header><a href="https://1024terabox.com/s/header-shared">1024terabox</a></header>
              <main><h1>Episode 01</h1><img src="https://cdn.example/episode-content.jpg"></main>
              <footer><a href="https://www.mediafire.com/file/footer-shared">Mediafire</a></footer>
            </body></html>
        """.trimIndent()

        val page = HentainiEpisodeParser.parseEpisode(
            html, "https://hentaini.com/h/sample-series/1"
        )

        assertEquals("https://cdn.example/episode-01-frame.jpeg", page.thumbnailUrl)
        assertEquals(4, page.streams.size)
        assertTrue(page.streams.any { it.url == "https://1024terabox.com/s/episode01" })
        assertTrue(page.streams.any { it.url == "https://www.fireload.com/episode01" })
        assertTrue(page.streams.any { it.url == "https://www.mediafire.com/file/episode01" })
        assertTrue(page.streams.any { it.url == "https://1fichier.com/?episode01" })
        assertTrue(page.streams.none { it.url.contains("shared") })
    }

    @Test
    fun selectsOnlyVideoObjectMatchingCurrentEpisodePage() {
        val html = """
            <html><head>
              <script type="application/ld+json">
                {
                  "@context":"https://schema.org",
                  "@graph":[
                    {
                      "@type":"VideoObject",
                      "url":"https://hentaini.com/h/sample-series",
                      "name":"Sample Series",
                      "thumbnailUrl":"https://cdn.example/shared-series-frame.jpeg",
                      "contentUrl":"https://1024terabox.com/s/shared-series-stream"
                    },
                    {
                      "@type":"VideoObject",
                      "url":"https://hentaini.com/h/sample-series/2",
                      "name":"Episode Two",
                      "thumbnailUrl":"https://cdn.example/episode-two-frame.jpeg",
                      "contentUrl":"https://www.fireload.com/episode-two-stream"
                    }
                  ]
                }
              </script>
            </head><body><main><h1>Episode Two</h1></main></body></html>
        """.trimIndent()

        val page = HentainiEpisodeParser.parseEpisode(
            html, "https://hentaini.com/h/sample-series/2"
        )

        assertEquals("Episode Two", page.title)
        assertEquals("https://cdn.example/episode-two-frame.jpeg", page.thumbnailUrl)
        assertEquals(1, page.streams.size)
        assertEquals("https://www.fireload.com/episode-two-stream", page.streams.single().url)
    }

    @Test
    fun supportsArrayAndImageObjectThumbnailsAndPlayerScriptLinks() {
        val html = """
            <html><head>
              <script type="application/ld+json">
                {
                  "@type":"https://schema.org/VideoObject",
                  "name":"Episode One",
                  "thumbnailUrl":[
                    {"@type":"ImageObject","url":"https://cdn.example/ep-one-frame.jpeg"}
                  ]
                }
              </script>
              <script>
                window.playerConfig = {
                  "mirror1":"https:\\/\\/www.fireload.com\\/episode-one",
                  "mirror2":"https:\\/\\/www.mediafire.com\\/file\\/episode-one"
                };
              </script>
            </head><body><main><h1>Episode One</h1></main></body></html>
        """.trimIndent()

        val page = HentainiEpisodeParser.parseEpisode(
            html, "https://hentaini.com/h/sample-series/1"
        )

        assertEquals("https://cdn.example/ep-one-frame.jpeg", page.thumbnailUrl)
        assertTrue(page.streams.any { it.url.contains("fireload.com") })
        assertTrue(page.streams.any { it.url.contains("mediafire.com") })
    }

    @Test
    fun ignoresNoisyEpisodeCardTextAndUsesEpisodeNumberForTitle() {
        val html = """
            <html><body>
              <div class="episode-card">
                <span>67 Anime</span>
                <a href="/h/sample-series/1">Episode 1</a>
              </div>
            </body></html>
        """.trimIndent()

        val episode = HentainiEpisodeParser.parseSeries(
            html, "https://hentaini.com/h/sample-series", "sample-series"
        ).single()

        assertEquals("Episode 1", episode.title)
    }
}
