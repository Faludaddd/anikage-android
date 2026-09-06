package com.anikage.app.core.util

/**
 * Turns raw HTML synopses (AniList / Anikage API) into display text.
 *
 * Handles the exact shape the APIs return: <br> / <p> / <li> block tags,
 * inline tags, HTML entities, and the trailing editorial attribution line
 * AniList appends to many descriptions ("(Source: MU)", "(Source: Official
 * Site)", ...).
 *
 * All patterns are compiled once up-front: this code runs during UI
 * composition, so per-call `toRegex()` is wasteful — and, as the v1.8.0
 * crash proved, an invalid pattern becomes a hard crash on every render.
 * (The old pattern "(Source: [^)]+\)?\s*$" left its opening "(" unescaped,
 * so it opened a group that never closed and Pattern.compile() threw
 * PatternSyntaxException — fixed in v1.8.1.)
 */
object HtmlText {

    /** `<br>`, `<br/>`, `<br />` → line break. */
    private val BR = Regex("<br\\s*/?>")

    /** Closing block tags → line break (paragraph / list separation). */
    private val BLOCK_CLOSE = Regex("</p>|</li>")

    /** Any remaining tag. */
    private val TAG = Regex("<[^>]*>")

    /**
     * Trailing "(Source: ...)" attribution. The literal parentheses MUST
     * be escaped: an unescaped "(" opens a regex group that is never
     * closed, making the whole pattern invalid.
     */
    private val SOURCE_TAIL = Regex("\\(Source: [^)]+\\)?\\s*$")

    /** Clean an HTML synopsis for display (entity-decoded, line-broken, trimmed). */
    fun clean(html: String): String = html
        .replace(BR, "\n")
        .replace(BLOCK_CLOSE, "\n")
        .replace(TAG, "")
        .replace("&amp;", "&")
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace("&quot;", "\"")
        .replace("&#039;", "'")
        .replace("&#39;", "'")
        .replace("&nbsp;", " ")
        .replace(SOURCE_TAIL, "")
        .trim()
}
