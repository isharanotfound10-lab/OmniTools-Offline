package com.xanime.app

import org.junit.Assert.assertEquals
import org.junit.Test

class HentainiTagFilterTest {
    @Test fun shotaconIsBlockedEvenIfAdultVerificationFlagIsSet() {
        assertEquals(
            HentainiTagFilter.Result.BLOCKED_MINOR_RELATED_TAG,
            HentainiTagFilter.evaluate(listOf("Shotacon"), allCharactersVerifiedAdult = true)
        )
    }

    @Test fun minorRelatedSynopsisIsBlockedEvenIfAdultVerificationFlagIsSet() {
        assertEquals(
            HentainiTagFilter.Result.BLOCKED_MINOR_RELATED_METADATA,
            HentainiTagFilter.evaluate(
                tags = listOf("Harem"),
                allCharactersVerifiedAdult = true,
                synopsis = "A schoolgirl becomes involved in the story."
            )
        )
    }

    @Test fun schoolStudentInEnglishTitleIsBlocked() {
        assertEquals(
            HentainiTagFilter.Result.BLOCKED_MINOR_RELATED_METADATA,
            HentainiTagFilter.evaluate(
                tags = emptyList(),
                allCharactersVerifiedAdult = true,
                englishTitle = "The High School Student"
            )
        )
    }

    @Test fun everyUserSuppliedGenreIsBlocked() {
        val blocked = listOf(
            "Ahegao", "Anal", "BBW", "Big Breast", "Blackmail", "Bondage",
            "Chikan", "Cosplay", "Futanari", "Garyu", "Harem", "Incest",
            "Maid", "Milf", "Netorare", "Nurse", "Orgy", "Oyakodon",
            "Paizuri", "Rape", "Scholar", "Shotacon", "Small Breasts",
            "Succubus", "Tentacles"
        )
        blocked.forEach { tag ->
            assertEquals(
                "Expected tag to be blocked: " + tag,
                HentainiTagFilter.Result.BLOCKED_MINOR_RELATED_TAG,
                HentainiTagFilter.evaluate(listOf(tag), allCharactersVerifiedAdult = true)
            )
        }
    }

    @Test fun entriesWithoutBlockedGenresStillFailClosedWhenAgeIsUnverified() {
        assertEquals(
            HentainiTagFilter.Result.UNVERIFIED,
            HentainiTagFilter.evaluate(listOf("Drama"), allCharactersVerifiedAdult = false)
        )
    }

    @Test fun unknownTagsFailClosed() {
        assertEquals(
            HentainiTagFilter.Result.UNVERIFIED,
            HentainiTagFilter.evaluate(
                listOf("New tag not in our list"),
                allCharactersVerifiedAdult = false
            )
        )
    }

    @Test fun independentlyVerifiedAdultOnlyMetadataCanPass() {
        assertEquals(
            HentainiTagFilter.Result.ELIGIBLE,
            HentainiTagFilter.evaluate(
                listOf("Drama"),
                allCharactersVerifiedAdult = true,
                title = "Adult-only title"
            )
        )
    }

    @Test fun tagMatchingIsCaseAndWhitespaceInsensitive() {
        assertEquals(
            HentainiTagFilter.Result.BLOCKED_MINOR_RELATED_TAG,
            HentainiTagFilter.evaluate(listOf("  SHOTACON "), allCharactersVerifiedAdult = true)
        )
    }
}
