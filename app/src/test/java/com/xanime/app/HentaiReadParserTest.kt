package com.xanime.app

import org.jsoup.Jsoup
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HentaiReadParserTest {
    @Test fun parsesHomepageAndSearchComicCards() {
        val html = """
            <div class="manga-grid">
              <article class="manga-item">
                <a class="manga-item__link" href="/hentai/adult-example/" title="Adult Example">Adult Example</a>
                <div class="manga-item__img-inner"><img src="/covers/example.jpg" alt="Adult Example"></div>
                <a class="manga-item__cat-link" href="/genre/fantasy/">Fantasy</a>
                <span class="manga-item__rating">4.8</span>
              </article>
              <article class="manga-item">
                <a class="manga-item__link" href="https://malicious.example/hentai/nope/">Ignore</a>
              </article>
            </div>
        """.trimIndent()
        val parsed = HentaiReadParser.parseListing(Jsoup.parse(html, "https://hentairead.com/?s=example"))
        assertEquals(1, parsed.size)
        assertEquals("Adult Example", parsed[0].title)
        assertEquals("https://hentairead.com/hentai/adult-example/", parsed[0].url)
        assertEquals("https://hentairead.com/covers/example.jpg", parsed[0].cover)
        assertEquals(listOf("Fantasy"), parsed[0].genres)
        assertEquals("4.8", parsed[0].rating)
    }

    @Test fun parsesComicDetailsAndReaderLink() {
        val html = """
            <meta property="og:title" content="Adult Example">
            <meta property="og:image" content="https://hencover.xyz/cover/example.jpg">
            <h1>Adult Example</h1>
            <div class="entry-content">A sample description.</div>
            <a href="/hentai/adult-example/english/p/1/">Read in English</a>
        """.trimIndent()
        val parsed = HentaiReadParser.parseDetails(
            Jsoup.parse(html, "https://hentairead.com/hentai/adult-example/"),
            "https://hentairead.com/hentai/adult-example/"
        )
        assertEquals("Adult Example", parsed.title)
        assertEquals("https://hencover.xyz/cover/example.jpg", parsed.cover)
        assertEquals("A sample description.", parsed.description)
        assertEquals("https://hentairead.com/hentai/adult-example/english/p/1/", parsed.readerUrl)
    }

    @Test fun buildsReaderPageNumbers() {
        assertEquals(
            "https://hentairead.com/hentai/adult-example/english/p/3/",
            HentaiReadParser.readerPageUrl("https://hentairead.com/hentai/adult-example/", 3)
        )
        assertTrue(HentaiReadParser.readerPageUrl("https://hentairead.com/hentai/adult-example/", 0).endsWith("/p/1/"))
    }
}
