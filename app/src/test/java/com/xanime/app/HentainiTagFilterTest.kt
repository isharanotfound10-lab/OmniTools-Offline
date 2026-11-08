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

    @Test fun ordinaryGenreTagsAreNotTreatedAsAgeVerification() {
        assertEquals(
            HentainiTagFilter.Result.UNVERIFIED,
            HentainiTagFilter.evaluate(
                listOf("Milf", "Harem", "Big Breast"),
                allCharactersVerifiedAdult = false
            )
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
                listOf("Milf", "Harem"),
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
