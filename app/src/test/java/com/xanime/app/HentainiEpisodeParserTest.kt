package com.xanime.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HentainiEpisodeParserTest {

    @Test
    fun extractsOnlyTheCurrentEpisodePlayersAndDownloadsFromNuxtPayload() {
        val hls = "https://vs1.yesterdaymail.com/series/147/1358/147-1358.m3u8"
        val html = """
            <html><head>
              <script type="application/ld+json">
                {"@type":"VideoObject","url":"https://hentaini.com/h/sample-series/1","contentUrl":"$hls"}
              </script>
              <script id="__NUXT_DATA__" type="application/json">[
                ["ShallowReactive",1],
                {"data":2},
                ["ShallowReactive",3],
                {"episode":4},
                {"data":5},
                [6],
                {"id":7,"episode_number":8,"players":9,"downloads":10},
                1358,
                1,
                "[{\"name\":\"HLS\",\"url\":\"$hls\"},{\"name\":\"Yourupload\",\"url\":\"https://www.yourupload.com/embed/ep1\"}]",
                "[{\"url\":\"https://www.fireload.com/ep1.mp4\"},{\"url\":\"https://www.mediafire.com/file/ep1/file\"}]"
              ]</script>
            </head><body><main><h1>Episode 1</h1></main></body></html>
        """.trimIndent()

        val page = HentainiEpisodeParser.parseEpisode(html, "https://hentaini.com/h/sample-series/1")
        assertTrue(page.streams.any { it.url == hls })
        assertTrue(page.streams.any { it.url == "https://www.yourupload.com/embed/ep1" })
        assertTrue(page.streams.any { it.url == "https://www.fireload.com/ep1.mp4" })
        assertTrue(page.streams.any { it.url == "https://www.mediafire.com/file/ep1/file" })

        val otherEpisode = HentainiEpisodeParser.parseEpisode(
            html,
            "https://hentaini.com/h/sample-series/2"
        )
        assertTrue(otherEpisode.streams.isEmpty())
    }

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
    fun readsHentainiEpisodeScreenshotFromOgImageProxyUrl() {
        val html = """
            <html><head>
              <meta property="og:image" content="https://hentaini.com/_ipx/_/https://admin.hentaini.com/uploads/Kyonyuu_Kazoku_Saimin_screenshot_88f7f8cede.jpeg">
            </head><body><main><h1>Episode 1</h1></main></body></html>
        """.trimIndent()

        val page = HentainiEpisodeParser.parseEpisode(
            html, "https://hentaini.com/h/kyonyuu-kazoku-saimin/1"
        )

        assertEquals(
            "https://hentaini.com/_ipx/_/https://admin.hentaini.com/uploads/Kyonyuu_Kazoku_Saimin_screenshot_88f7f8cede.jpeg",
            page.thumbnailUrl
        )
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
    fun supportsArrayAndImageObjectThumbnailsButIgnoresUnscopedScriptLinks() {
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
        assertTrue(page.streams.isEmpty())
    }

    @Test
    fun doesNotReuseUnidentifiedSingleVideoObjectAcrossEpisodePages() {
        val html = """
            <html><head>
              <script type="application/ld+json">
                {
                  "@type":"VideoObject",
                  "name":"Series title",
                  "thumbnailUrl":"https://cdn.example/shared-series-image.jpeg",
                  "contentUrl":"https://1024terabox.com/s/shared-series-link",
                  "embedUrl":"https://www.mediafire.com/file/shared-series-link"
                }
              </script>
            </head><body><main><h1>Episode</h1></main></body></html>
        """.trimIndent()

        val first = HentainiEpisodeParser.parseEpisode(
            html, "https://hentaini.com/h/sample-series/1"
        )
        val second = HentainiEpisodeParser.parseEpisode(
            html, "https://hentaini.com/h/sample-series/2"
        )

        assertTrue(first.thumbnailUrl.isEmpty())
        assertTrue(second.thumbnailUrl.isEmpty())
        assertTrue(first.streams.isEmpty())
        assertTrue(second.streams.isEmpty())
    }

    @Test
    fun excludesTheSeriesCardThumbnailWhenResolvingEpisodeImages() {
        val image = "https://cdn.example/shared-series-image.jpeg"
        val html = """
            <html><head>
              <meta property="og:image" content="$image">
            </head><body><main><h1>Episode 1</h1></main></body></html>
        """.trimIndent()

        val page = HentainiEpisodeParser.parseEpisode(
            html, "https://hentaini.com/h/sample-series/1", image
        )

        assertTrue(page.thumbnailUrl.isEmpty())
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
