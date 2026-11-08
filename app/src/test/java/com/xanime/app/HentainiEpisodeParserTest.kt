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
        assertEquals("Episode 01", episodes.first().title)
        assertEquals("https://hentaini.com/ep1.jpg", episodes.first().thumbnailUrl)
    }

    @Test
    fun extractsKnownProviderLinksAndKeepsOnlyDownloadHosts() {
        val html = """
            <html><head><meta property="og:image" content="https://cdn.example/episode.jpg"></head>
            <body>
              <h1>Episode 01</h1>
              <a href="https://1024terabox.com/s/example">1024terabox</a>
              <a href="https://www.fireload.com/example">Fireload</a>
              <a href="https://www.mediafire.com/file/example">Mediafire</a>
              <a href="https://example.org/about">About us</a>
              <script>var share = "https://1fichier.com/?example";</script>
            </body></html>
        """.trimIndent()

        val page = HentainiEpisodeParser.parseEpisode(
            html, "https://hentaini.com/h/sample-series/1"
        )

        assertEquals(4, page.streams.size)
        assertTrue(page.streams.any { it.host == "1024terabox" })
        assertTrue(page.streams.any { it.host == "Fireload" })
        assertTrue(page.streams.any { it.host == "Mediafire" })
        assertTrue(page.streams.any { it.host == "1fichier" })
        assertEquals("https://cdn.example/episode.jpg", page.thumbnailUrl)
    }
}
