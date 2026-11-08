package com.xanime.app

import org.junit.Assert.assertEquals
import org.junit.Test

class CatalogUrlBuilderTest {
    @Test fun buildsSiteWideRandomUrl() {
        assertEquals("https://xanimeporn.com/?filtre=random&cat=0", CatalogUrlBuilder.build(null, "random", 1))
    }

    @Test fun buildsCategoryRandomUrlWithoutLosingCategory() {
        assertEquals("https://xanimeporn.com/action/?filtre=random", CatalogUrlBuilder.build("action", "random", 1))
    }

    @Test fun keepsFilterAndCategoryOnLaterPages() {
        assertEquals("https://xanimeporn.com/action/page/2/?filtre=rate", CatalogUrlBuilder.build("action", "rate", 2))
    }

    @Test fun buildsSiteWideLaterPageUrl() {
        assertEquals("https://xanimeporn.com/page/3/?filtre=views&cat=0", CatalogUrlBuilder.build(null, "views", 3))
    }
}
