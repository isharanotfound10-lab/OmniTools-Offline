package com.xanime.app

/**
 * Conservative metadata risk checks for Hentaini catalogue entries.
 *
 * Genre tags and descriptions are not proof of character ages. An entry is
 * only ELIGIBLE when an independent, trustworthy source verifies that every
 * depicted character is an adult. Unknown metadata therefore fails closed.
 */
object HentainiTagFilter {
    enum class Result {
        BLOCKED_MINOR_RELATED_TAG,
        BLOCKED_MINOR_RELATED_METADATA,
        UNVERIFIED,
        ELIGIBLE
    }

    // All 25 tags supplied by the user are blocked for Hentaini results.
    private val blockedTags: Set<String> = setOf()

    // These are risk flags, not an exhaustive classifier. No match does not
    // establish that the characters are adults.
    private val minorRelatedPatterns: List<Regex> = listOf()

    /**
     * The Hentaini API does not provide trusted per-series verification that
     * every character is an adult. Callers must therefore pass false unless
     * such independent verification actually exists.
     */
    fun evaluate(
        tags: Collection<String>,
        allCharactersVerifiedAdult: Boolean,
        title: String = "",
        englishTitle: String = "",
        synopsis: String = ""
    ): Result {
        val normalized = tags.map { it.trim().lowercase() }.filter { it.isNotEmpty() }

        if (normalized.any { it in blockedTags }) {
            return Result.BLOCKED_MINOR_RELATED_TAG
        }

        val metadata = listOf(title, englishTitle, synopsis).joinToString(" ")
        if (minorRelatedPatterns.any { it.containsMatchIn(metadata) }) {
            return Result.BLOCKED_MINOR_RELATED_METADATA
        }

        if (!allCharactersVerifiedAdult) return Result.UNVERIFIED
        return Result.ELIGIBLE
    }

    /** All blocked tags, exposed for tests and diagnostics. */
    val knownTags: Set<String> = blockedTags
}
