package app.luxion.shogunai.ui.palette

private const val EXACT_SCORE = 1_000_000
private const val PREFIX_SCORE = 500_000
private const val WORD_PREFIX_SCORE = 200_000
private const val SUBSEQUENCE_SCORE = 10_000
private const val WORD_START_BONUS = 100
private const val CONTIGUOUS_BONUS = 30
private const val GAP_PENALTY = 1
private const val ContextScoreDivisor = 1000
private val WordSeparators = setOf('/', '-', '_', ' ')

private data class Match(val score: Int, val scattered: Boolean)

fun matchScore(query: String, key: String): Int? = match(query, key)?.score

private fun match(query: String, key: String): Match? {
    val needle = query.filterNot(Char::isWhitespace).lowercase()
    if (needle.isEmpty()) return Match(0, scattered = false)
    val haystack = key.lowercase()
    if (haystack == needle) return Match(EXACT_SCORE, scattered = false)
    if (haystack.startsWith(needle)) return Match(PREFIX_SCORE - key.length, scattered = false)
    if (startsAWord(needle, haystack, key)) return Match(WORD_PREFIX_SCORE - key.length, scattered = false)
    return subsequenceScore(needle, haystack, key)?.let { Match(SUBSEQUENCE_SCORE + it, scattered = true) }
}

private fun startsAWord(needle: String, haystack: String, key: String): Boolean =
    haystack.indices.any { haystack.startsWith(needle, it) && isWordStart(key, it) }

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
                .thenBy { it.first.kind.tieOrder() }
                .thenBy { it.first.title.lowercase() },
        )
        .map { it.first }
}

private fun PaletteKind.tieOrder(): Int = when (this) {
    PaletteKind.PROJECT -> 0
    PaletteKind.VIEW, PaletteKind.SETTING -> 1
    PaletteKind.ACTION -> 2
    PaletteKind.WORKTREE -> 3
}

private fun bestScore(item: PaletteItem, query: String): Int? {
    val shown = listOfNotNull(item.title, item.context)
    fun shownScore(key: String): Int? =
        match(query, key)?.takeUnless { it.scattered && shown.none { text -> matchedIndices(query, text).isNotEmpty() } }?.score
    val own = item.keys.mapNotNull(::shownScore)
    val inherited = item.contextKeys.mapNotNull(::shownScore).map { it / ContextScoreDivisor }
    return (own + inherited).maxOrNull()
}

private fun recentOrViews(items: List<PaletteItem>, recentIds: List<String>): List<PaletteItem> {
    val byId = items.associateBy { it.id }
    val recent = recentIds.mapNotNull(byId::get)
    return recent.ifEmpty { items.filter { it.kind == PaletteKind.VIEW || it.kind == PaletteKind.SETTING } }
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
