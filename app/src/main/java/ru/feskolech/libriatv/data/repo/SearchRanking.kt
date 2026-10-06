package ru.feskolech.libriatv.data.repo

/**
 * The API search is fuzzy: for "чёрный клевер" it returns Black Clover and then anything with
 * "чёрный" in the name. Titles that really match the query are shown first, the rest separately.
 * A title matches when every word of the query starts some word of one of its names (Russian,
 * English or alternative), ignoring case, "ё"/"е" and punctuation.
 */
internal fun matchesQuery(query: String, names: List<String?>): Boolean {
    val words = normalizeWords(query)
    if (words.isEmpty()) return false
    return names.filterNotNull().any { name ->
        val nameWords = normalizeWords(name)
        words.all { w -> nameWords.any { it.startsWith(w) } }
    }
}

private fun normalizeWords(text: String): List<String> =
    text.lowercase().replace('ё', 'е')
        .split(Regex("[^\\p{L}\\p{N}]+"))
        .filter { it.isNotEmpty() }
