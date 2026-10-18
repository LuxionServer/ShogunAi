package app.luxion.shogunai.ui.palette

private const val EXACT_SCORE = 1_000_000
private const val PREFIX_SCORE = 500_000
private const val SUBSEQUENCE_SCORE = 10_000
private const val WORD_START_BONUS = 100
private const val CONTIGUOUS_BONUS = 30
private const val GAP_PENALTY = 1
private const val ContextScoreDivisor = 1000
private val WordSeparators = setOf('/', '-', '_', ' ')

fun matchScore(query: String, key: String): Int? {
    val needle = query.filterNot(Char::isWhitespace).lowercase()
    if (needle.isEmpty()) return 0
    val haystack = key.lowercase()
    if (haystack == needle) return EXACT_SCORE
    if (haystack.startsWith(needle)) return PREFIX_SCORE - key.length
    return subsequenceScore(needle, haystack, key)?.let { SUBSEQUENCE_SCORE + it }
}

private fun subsequenceScore(needle: String, haystack: String, key: String): Int? {
    if (needle.length > haystack.length) return null
    var previous = IntArray(haystack.length) { index ->
        if (haystack[index] == needle[0]) wordStartBonus(key, index) else NoMatch
    }
    for (i in 1 until needle.length) {
        val current = IntArray(haystack.length) { NoMatch }
        for (j in i until haystack.length) {
            if (haystack[j] != needle[i]) continue
            current[j] = bestPrevious(previous, j)?.let { it + wordStartBonus(key, j) } ?: continue
        }
        previous = current
    }
    return previous.filter { it != NoMatch }.maxOrNull()
}

private fun bestPrevious(previous: IntArray, j: Int): Int? {
    var best: Int? = null
    for (k in 0 until j) {
        if (previous[k] == NoMatch) continue
        val step = if (k == j - 1) CONTIGUOUS_BONUS else -(j - k - 1) * GAP_PENALTY
        val candidate = previous[k] + step
        if (best == null || candidate > best) best = candidate
    }
    return best
}

private fun wordStartBonus(key: String, index: Int): Int = if (isWordStart(key, index)) WORD_START_BONUS else 0

private fun isWordStart(key: String, index: Int): Boolean {
    if (index == 0) return true
    val before = key[index - 1]
    return before in WordSeparators || (before.isLowerCase() && key[index].isUpperCase())
}

private const val NoMatch = Int.MIN_VALUE

fun rankPaletteItems(items: List<PaletteItem>, query: String, recentIds: List<String>): List<PaletteItem> {
    if (query.isBlank()) return recentOrViews(items, recentIds)
    val recency = recentIds.withIndex().associate { it.value to it.index }
    return items
        .mapNotNull { item -> bestScore(item, query)?.let { item to it } }
        .sortedWith(
            compareByDescending<Pair<PaletteItem, Int>> { it.second }
                .thenBy { recency[it.first.id] ?: Int.MAX_VALUE }
                .thenBy { it.first.title.lowercase() },
        )
        .map { it.first }
}

private fun bestScore(item: PaletteItem, query: String): Int? {
    val own = item.keys.mapNotNull { matchScore(query, it) }
    val inherited = item.contextKeys.mapNotNull { matchScore(query, it) }.map { it / ContextScoreDivisor }
    return (own + inherited).maxOrNull()
}

private fun recentOrViews(items: List<PaletteItem>, recentIds: List<String>): List<PaletteItem> {
    val byId = items.associateBy { it.id }
    val recent = recentIds.mapNotNull(byId::get)
    return recent.ifEmpty { items.filter { it.kind == PaletteKind.VIEW } }
}

fun matchedIndices(query: String, text: String): Set<Int> {
    val needle = query.filterNot(Char::isWhitespace).lowercase()
    if (needle.isEmpty()) return emptySet()
    val haystack = text.lowercase()
    val start = haystack.indexOf(needle)
    if (start >= 0) return (start until start + needle.length).toSet()
    val indices = mutableSetOf<Int>()
    var from = 0
    for (char in needle) {
        val found = haystack.indexOf(char, from)
        if (found < 0) return emptySet()
        indices += found
        from = found + 1
    }
    return indices
}

fun recordRecent(recentIds: List<String>, id: String): List<String> =
    (listOf(id) + recentIds.filterNot { it == id }).take(MaxRecentItems)

const val MaxRecentItems = 20
