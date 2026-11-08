package com.xanime.app

import org.junit.Assert.assertEquals
import org.junit.Test

class HentainiTagFilterTest {
    @Test fun everyUserSuppliedGenreIsBlocked() {
        HentainiTagFilter.knownTags.forEach { tag ->
            assertEquals(
                "Expected tag '$tag' to be blocked",
                HentainiTagFilter.Result.BLOCKED_MINOR_RELATED_TAG,
                HentainiTagFilter.evaluate(listOf(tag), allCharactersVerifiedAdult = true)
            )
        }
        assertEquals(25, HentainiTagFilter.knownTags.size)
    }

    @Test fun minorRelatedSynopsisIsBlockedEvenIfAdultVerificationFlagIsSet() {
        assertEquals(
            HentainiTagFilter.Result.BLOCKED_MINOR_RELATED_METADATA,
            HentainiTagFilter.evaluate(
                tags = emptyList(),
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

    @Test fun ordinaryUnrecognizedGenreIsNotAgeVerification() {
        assertEquals(
            HentainiTagFilter.Result.UNVERIFIED,
            HentainiTagFilter.evaluate(
                listOf("Unrecognized genre"),
                allCharactersVerifiedAdult = false
            )
        )
    }

    @Test fun missingAdultVerificationFailsClosed() {
        assertEquals(
            HentainiTagFilter.Result.UNVERIFIED,
            HentainiTagFilter.evaluate(emptyList(), allCharactersVerifiedAdult = false)
        )
    }

    @Test fun independentlyVerifiedAdultOnlyMetadataCanPass() {
        assertEquals(
            HentainiTagFilter.Result.ELIGIBLE,
            HentainiTagFilter.evaluate(
                emptyList(),
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
