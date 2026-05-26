package com.bios.app.alerts

/**
 * A scientific reference the owner can verify themselves. Every
 * citation surfaced by Bios must carry a stable, alive web URL —
 * a plain prose citation is unverifiable and rots silently
 * (#305). DOIs are preferred over publisher URLs because they
 * survive journal mergers and URL reshuffles.
 *
 * Construct via [doi] for journal articles, or the primary
 * constructor for guideline PDFs, books, and other web-stable
 * sources.
 */
data class Citation(
    val text: String,
    val url: String,
) {
    init {
        require(url.startsWith("https://")) {
            "Citation URL must be https — got: $url"
        }
    }

    companion object {
        fun doi(text: String, doi: String): Citation =
            Citation(text = text, url = "https://doi.org/$doi")
    }
}
