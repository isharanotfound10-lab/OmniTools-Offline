package com.xanime.app

import java.net.URI

data class JavSite(
    val name: String,
    val host: String,
    val homeUrl: String = "https://$host"
)

data class JavSearchResult(
    val siteName: String,
    val title: String,
    val url: String
)

object JavSiteCatalog {
    // Exactly the 62 website entries (#1–#62) on the supplied directory page.
    // The three Reddit community entries are intentionally excluded.
    val sites = listOf(
        JavSite("MissAV", "missav.ws"),
        JavSite("SupJav", "supjav.com"),
        JavSite("VJAV", "vjav.com"),
        JavSite("JavGuru", "jav.guru"),
        JavSite("JAVMost", "javmost.ws"),
        JavSite("KissJAV (ShyAV)", "kissjav.com"),
        JavSite("7MMTV", "7mmtv.tv"),
        JavSite("BestJavPorn", "bestjavporn.com"),
        JavSite("JAV GIGA", "javgiga.com"),
        JavSite("JavDoe", "javdoe.com"),
        JavSite("JavBangers", "javbangers.com"),
        JavSite("91Porn", "91porn.com"),
        JavSite("JAVHD.today", "javhd.today"),
        JavSite("SexTB", "sextb.net"),
        JavSite("JAVGG", "javgg.net"),
        JavSite("Javtiful", "javtiful.com"),
        JavSite("JAVSeen", "javseen.tv"),
        JavSite("JAV Tsunami", "javtsunami.com"),
        JavSite("Korean Porn Movie", "koreanpornmovie.com"),
        JavSite("nJAV", "njav.com"),
        JavSite("JAVSB", "jav.sb"),
        JavSite("MythAV", "mythav.com"),
        JavSite("xKorean", "xkorean.biz"),
        JavSite("TokyoMotion.net", "tokyomotion.net"),
        JavSite("Japan Whores", "japan-whores.com"),
        JavSite("FC2PPV", "fc2ppv.tv"),
        JavSite("HereXXX", "herexxx.com"),
        JavSite("SexKbj", "sexkbj.com"),
        JavSite("JAVLeak", "javleak.com"),
        JavSite("JavFun", "javfun.me"),
        JavSite("AVCrempie", "avcrempie.com"),
        JavSite("JavBraze", "javbraze.com"),
        JavSite("JAVhay", "javhay.net"),
        JavSite("BlackedJAV", "blackedjav.com"),
        JavSite("ABJAV", "abjav.com"),
        JavSite("PornHub Asian", "pornhub.com"),
        JavSite("XNXX Japan", "xnxx.com"),
        JavSite("123AV", "njav.tv"),
        JavSite("Senzuri Tube", "senzuri.tube"),
        JavSite("Roshy", "roshy.tv"),
        JavSite("JAVQuick", "javquick.com"),
        JavSite("nJAV TV", "njavtv.cc"),
        JavSite("TokyoMotion", "tokyomotion.com"),
        JavSite("FunLove", "funlove.info"),
        JavSite("MaxJAV", "maxjav.com"),
        JavSite("Ark JAV", "arkjav.com"),
        JavSite("JAV WINE", "jav.wine"),
        JavSite("JAVHDTube", "javhdtube.com"),
        JavSite("JavBest TV", "javbest.tv"),
        JavSite("BestJapanesePorn.tv", "bestjapaneseporn.tv"),
        JavSite("JAV.com.co", "jav.com.co"),
        JavSite("VivaMaxPH", "vivamaxph.com"),
        JavSite("AVHWA", "avhwa.com"),
        JavSite("AV Porner", "avporner.com"),
        JavSite("ZeroJAV", "zerojav.com"),
        JavSite("Jav Mobile", "javmobile.net"),
        JavSite("VivamaxStream", "vivamaxstream.com"),
        JavSite("Ikisoda", "ikisoda.com"),
        JavSite("AsianViral", "asianviral.net"),
        JavSite("XXBUM", "xxbum.com"),
        JavSite("JAVMIT", "javmit.com"),
        JavSite("OMGJAV", "omgjav.com")
    )

    fun isAllowed(rawUrl: String): Boolean {
        return try {
            val uri = URI(rawUrl)
            val scheme = uri.scheme?.lowercase()
            val host = uri.host?.lowercase()?.removePrefix("www.") ?: return false
            (scheme == "http" || scheme == "https") && sites.any { site ->
                host == site.host.removePrefix("www.") || host.endsWith(".${site.host.removePrefix("www.")}")
            }
        } catch (_: Exception) {
            false
        }
    }

    fun hostMatchesSite(rawUrl: String, site: JavSite): Boolean {
        return try {
            val host = URI(rawUrl).host?.lowercase()?.removePrefix("www.") ?: return false
            val expected = site.host.lowercase().removePrefix("www.")
            host == expected || host.endsWith(".$expected")
        } catch (_: Exception) {
            false
        }
    }
}

object JavSearchRules {
    private fun normalizeCodeText(value: String): String {
        return value.trim().uppercase(java.util.Locale.ROOT)
            .replace('_', '-')
            // Ignore zero-padding in numeric code segments after a separator:
            // MOON-059 and moon-59 are treated as the same code.
            .replace(Regex("([A-Z][A-Z0-9]*-)0+(\\d+)")) { match ->
                match.groupValues[1] + match.groupValues[2]
            }
    }

    fun searchVariants(code: String): List<String> {
        if (code.isBlank()) return emptyList()
        val raw = code.trim().replace('_', '-')
        val normalized = normalizeCodeText(raw)
        val zeroPadded = normalized.replace(Regex("-(\\d+)$")) { match ->
            "-" + "0" + match.groupValues[1]
        }
        return linkedSetOf(raw, normalized, zeroPadded).toList()
    }

    fun titleMatches(title: String, code: String): Boolean {
        if (code.isBlank()) return false
        val normalizedCode = normalizeCodeText(code)
        val normalizedTitle = normalizeCodeText(title)
        val exactCode = Regex("(?<![A-Z0-9])" + Regex.escape(normalizedCode) + "(?![A-Z0-9])")
        return exactCode.containsMatchIn(normalizedTitle)
    }
}
