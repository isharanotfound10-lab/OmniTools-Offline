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
                allCharactersVerifiedAdult = true
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
