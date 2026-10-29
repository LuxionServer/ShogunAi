package app.luxion.shogunai.ui.worktree

import app.luxion.shogunai.ui.i18n.Strings
import java.time.Duration
import java.time.Instant

fun relativeTime(from: Instant, now: Instant, strings: Strings): String {
    val elapsed = Duration.between(from, now).coerceAtLeast(Duration.ZERO)
    val days = elapsed.toDays()
    return when {
        elapsed.toMinutes() < 1 -> strings.justNow
        elapsed.toHours() < 1 -> strings.minutesAgo(elapsed.toMinutes())
        days < 1 -> strings.hoursAgo(elapsed.toHours())
        days < DAYS_PER_MONTH -> strings.daysAgo(days)
        days < DAYS_PER_YEAR -> strings.monthsAgo(days / DAYS_PER_MONTH)
        else -> strings.yearsAgo(days / DAYS_PER_YEAR)
    }
}

fun compactRelativeTime(from: Instant, now: Instant, strings: Strings): String {
    val elapsed = Duration.between(from, now).coerceAtLeast(Duration.ZERO)
    val days = elapsed.toDays()
    return when {
        elapsed.toMinutes() < 1 -> strings.compactNow
        elapsed.toHours() < 1 -> strings.compactMinutes(elapsed.toMinutes())
        days < 1 -> strings.compactHours(elapsed.toHours())
        days < DAYS_PER_MONTH -> strings.compactDays(days)
        days < DAYS_PER_YEAR -> strings.compactMonths(days / DAYS_PER_MONTH)
        else -> strings.compactYears(days / DAYS_PER_YEAR)
    }
}

private const val DAYS_PER_MONTH = 30L
private const val DAYS_PER_YEAR = 365L
