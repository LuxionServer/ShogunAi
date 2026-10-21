package app.luxion.shogunai.ui.worktree

import app.luxion.shogunai.domain.model.Worktree
import app.luxion.shogunai.domain.model.WorktreeSort
import java.math.BigInteger
import java.time.Instant

private val DigitRuns = Regex("\\d+|\\D+")

fun naturalComparator(): Comparator<String> = Comparator { first, second ->
    val firstParts = DigitRuns.findAll(first.lowercase()).map { it.value }.toList()
    val secondParts = DigitRuns.findAll(second.lowercase()).map { it.value }.toList()
    for ((left, right) in firstParts.zip(secondParts)) {
        val bothNumbers = left[0].isDigit() && right[0].isDigit()
        val order = if (bothNumbers) BigInteger(left).compareTo(BigInteger(right)) else left.compareTo(right)
        if (order != 0) return@Comparator order
    }
    val byLength = firstParts.size.compareTo(secondParts.size)
    if (byLength != 0) byLength else first.compareTo(second)
}

fun worktreeSortLabel(worktree: Worktree): String =
    worktree.branch ?: worktree.path.replace('\\', '/').trimEnd('/').substringAfterLast('/')

fun <T> sortWorktreeItems(
    items: List<T>,
    sort: WorktreeSort,
    worktreeOf: (T) -> Worktree,
    activityDateOf: (T) -> Instant?,
): List<T> {
    val natural = naturalComparator()
    val byName = Comparator<T> { first, second ->
        natural.compare(worktreeSortLabel(worktreeOf(first)), worktreeSortLabel(worktreeOf(second)))
    }
    val byActivity = Comparator<T> { first, second ->
        val left = activityDateOf(first)
        val right = activityDateOf(second)
        when {
            left == null && right == null -> 0
            left == null -> 1
            right == null -> -1
            else -> right.compareTo(left)
        }
    }
    val main = compareByDescending<T> { worktreeOf(it).isMain }
    val order = when (sort) {
        WorktreeSort.NAME -> main.then(byName)
        WorktreeSort.ACTIVITY -> main.then(byActivity).then(byName)
    }
    return items.sortedWith(order)
}
