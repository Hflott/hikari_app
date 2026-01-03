package tv.anime.app.ui

/**
 * Normalizes HTML-ish descriptions (AniList / remote metadata) into readable plain text.
 *
 * - Converts <br> to line breaks
 * - Strips simple formatting tags
 * - Decodes a few common HTML entities
 * - Collapses excessive whitespace
 */
internal fun normalizeDescriptionText(raw: String): String {
    return raw
        .replace(Regex("<br\\s*/?>", RegexOption.IGNORE_CASE), "\n")
        .replace(Regex("</?i>", RegexOption.IGNORE_CASE), "")
        .replace(Regex("</?b>", RegexOption.IGNORE_CASE), "")
        .replace(Regex("<[^>]*>"), "")
        .replace("&amp;", "&")
        .replace("&quot;", "\"")
        .replace("&#39;", "'")
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        // Preserve newlines but collapse repeated spaces.
        .replace(Regex("[\\t\\r]+"), " ")
        .replace(Regex("[ ]{2,}"), " ")
        .trim()
}
