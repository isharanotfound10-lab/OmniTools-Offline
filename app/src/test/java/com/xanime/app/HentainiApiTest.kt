package com.xanime.app

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HentainiApiTest {
    @Test
    fun parsesSeriesCardsAndPagination() {
        val json = JSONObject(
            """
            {
              "data": [
                {
                  "id": 32,
                  "title": "Example Series",
                  "hasEpisodes": true,
                  "h_id": "605901",
                  "url": "example-series",
                  "images": [
                    {
                      "path": "example-screenshot.jpg",
                      "image_type": { "name": "screenshot" }
                    },
                    {
                      "path": "example-cover.jpg",
                      "image_type": { "name": "cover" }
                    }
                  ]
                }
              ],
              "meta": {
                "pagination": { "start": 0, "limit": 10, "total": 15 }
              }
            }
            """.trimIndent()
        )

        val page = HentainiApi.parseSearchResponse(json)

        assertEquals(1, page.results.size)
        assertEquals("Example Series", page.results[0].title)
        assertEquals("example-series", page.results[0].slug)
        assertEquals("https://hentaini.com/h/example-series", page.results[0].pageUrl)
        assertEquals("https://admin.hentaini.com/uploads/example-cover.jpg", page.results[0].thumbnailUrl)
        assertTrue(page.results[0].hasEpisodes)
        assertEquals(15, page.total)
        assertTrue(page.hasMore)
    }

    @Test
    fun handlesNoResultsAndNoEpisodes() {
        val json = JSONObject(
            """
            {
              "data": [{
                "id": 1,
                "title": "Empty",
                "url": "empty",
                "hasEpisodes": false,
                "images": []
              }],
              "meta": { "pagination": { "start": 0, "limit": 10, "total": 1 } }
            }
            """.trimIndent()
        )

        val page = HentainiApi.parseSearchResponse(json)

        assertEquals(1, page.results.size)
        assertFalse(page.results[0].hasEpisodes)
        assertFalse(page.hasMore)
    }
}
