package com.xanime.app

/**
 * Conservative eligibility checks for Hentaini metadata.
 *
 * Content genres describe themes, not character ages. Therefore no genre in the
 * currently observed Hentaini tag list is sufficient to verify that every
 * depicted character is an adult.
 */
object HentainiTagFilter {
    enum class Result {
        BLOCKED_MINOR_RELATED_TAG,
        UNVERIFIED,
        ELIGIBLE
    }

    private val blockedTags = setOf(
        "shotacon"
    )

    /**
     * Returns ELIGIBLE only when an independent, trustworthy source explicitly
     * verifies that every depicted character is an adult and no blocked tag is
     * present. A site's generic 18+ label or genre list is not that verification.
     */
    fun evaluate(
        tags: Collection<String>,
        allCharactersVerifiedAdult: Boolean
    ): Result {
        val normalized = tags.map { it.trim().lowercase() }.filter { it.isNotEmpty() }

        if (normalized.any { it in blockedTags }) {
            return Result.BLOCKED_MINOR_RELATED_TAG
        }

        if (!allCharactersVerifiedAdult) {
            return Result.UNVERIFIED
        }

        return Result.ELIGIBLE
    }

    /** The tags currently supplied by the user, normalized for consistent parsing. */
    val knownTags: Set<String> = setOf(
        "ahegao", "anal", "bbw", "big breast", "blackmail", "bondage",
        "chikan", "cosplay", "futanari", "garyu", "harem", "incest",
        "maid", "milf", "netorare", "nurse", "orgy", "oyakodon",
        "paizuri", "rape", "scholar", "shotacon", "small breasts",
        "succubus", "tentacles"
    )
}
