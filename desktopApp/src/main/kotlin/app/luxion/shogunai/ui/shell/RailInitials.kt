package app.luxion.shogunai.ui.shell

fun railInitials(labels: List<String>): Map<String, String> {
    val defaults = labels.distinct().associateWith(::defaultTile)
    val colliding = defaults.entries.groupBy({ it.value }, { it.key }).values.filter { it.size > 1 }.flatten().toSet()
    return defaults.mapValues { (label, tile) ->
        if (label in colliding) distinguishingTile(label, colliding.filter { it != label && defaults[it] == tile }) ?: tile else tile
    }
}

private fun defaultTile(label: String): String {
    val words = words(label)
    val tile = if (words.size > 1) "${words[0].first()}${words[1].first()}" else (words.firstOrNull() ?: label).take(2)
    return tile.uppercase()
}

private fun distinguishingTile(label: String, others: List<String>): String? {
    val letters = letters(label)
    val first = letters.firstOrNull() ?: return null
    val taken = others.flatMap { letters(it).drop(1) }.toSet()
    val next = letters.drop(1).firstOrNull { it !in taken } ?: return null
    return "$first$next".uppercase()
}

private fun letters(label: String): List<Char> = label.lowercase().filter(Char::isLetterOrDigit).toList()

private fun words(label: String): List<String> =
    label.replace(CamelBoundary, " ").split(WordSeparators).filter { it.isNotEmpty() }

private val CamelBoundary = Regex("(?<=[a-z0-9])(?=[A-Z])")
private val WordSeparators = Regex("[\\s_.\\-]+")
