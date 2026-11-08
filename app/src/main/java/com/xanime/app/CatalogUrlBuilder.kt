package com.xanime.app

/** Builds native catalogue URLs for site-wide and category-scoped browsing. */
object CatalogUrlBuilder {
    private val allowedFilters = setOf("latest", "views", "longest", "rate", "random")

    fun build(categorySlug: String?, filter: String, page: Int): String {
        require(filter in allowedFilters) { "Unsupported catalogue filter: $filter" }
        require(page >= 1) { "Page must be at least 1" }

        val path = buildString {
            append("https://xanimeporn.com/")
            if (!categorySlug.isNullOrBlank()) {
                append(categorySlug.trim('/'))
                append('/')
            }
            if (page > 1) append("page/$page/")
        }
        val query = if (categorySlug.isNullOrBlank()) "filtre=$filter&cat=0" else "filtre=$filter"
        return "$path?$query"
    }
}
