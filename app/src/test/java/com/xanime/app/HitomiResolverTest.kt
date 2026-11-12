package com.xanime.app

import org.junit.Assert.assertEquals
import org.junit.Test

class HitomiResolverTest {
    @Test
    fun fullPathFromHashUsesLastHexDigitAndPreviousTwoDigits() {
        val hash = "a062b56a5cab90e2c7beb74b6df96a9856af18add48d4cbd301fb230730fd59d"
        assertEquals("9/d5/$hash", HitomiResolver.fullPathFromHash(hash))
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsMalformedHashes() {
        HitomiResolver.fullPathFromHash("not-a-hash")
    }
}
