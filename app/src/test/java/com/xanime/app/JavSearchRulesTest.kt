package com.xanime.app

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class JavSearchRulesTest {
    @Test fun matchesCodeAnywhereInFullTitleIgnoringCase() {
        assertTrue(JavSearchRules.titleMatches("New JAV Release ABC-123 Full Video", "abc-123"))
        assertTrue(JavSearchRules.titleMatches("abc-123 extra title text", "ABC-123"))
    }

    @Test fun matchesCodesIgnoringCaseAndNumericZeroPadding() {
        assertTrue(JavSearchRules.titleMatches("MOON-059 full video", "moon-59"))
        assertTrue(JavSearchRules.titleMatches("moon-59 full video", "MOON-059"))
        assertTrue(JavSearchRules.titleMatches("[Moon-059] Full Title", "MOON-59"))
        assertTrue(JavSearchRules.searchVariants("moon-59").contains("MOON-059"))
        assertTrue(JavSearchRules.searchVariants("MOON-059").contains("MOON-59"))
    }

    @Test fun rejectsPartialCodeMatches() {
        assertFalse(JavSearchRules.titleMatches("ABC123 Full Video", "ABC-123"))
        assertFalse(JavSearchRules.titleMatches("MOON-590 Full Video", "MOON-59"))
        assertFalse(JavSearchRules.titleMatches("Different title", "ABC-123"))
        assertFalse(JavSearchRules.titleMatches("ABC-123", " "))
    }

    @Test fun allowsOnlyListedSiteHosts() {
        assertTrue(JavSiteCatalog.isAllowed("https://www.javgg.net/watch/abc"))
        assertTrue(JavSiteCatalog.isAllowed("https://sub.missav.ws/video"))
        assertFalse(JavSiteCatalog.isAllowed("https://example.com/watch/abc"))
        assertFalse(JavSiteCatalog.isAllowed("javascript:alert(1)"))
    }

    @Test fun directoryContainsOnlyThe62WebsiteEntries() {
        assertTrue(JavSiteCatalog.sites.size == 62)
        assertFalse(JavSiteCatalog.sites.any { it.name.startsWith("Reddit", ignoreCase = true) })
    }
}
