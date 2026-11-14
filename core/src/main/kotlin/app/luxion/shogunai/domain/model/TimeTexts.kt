package app.luxion.shogunai.domain.model

import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

fun Duration.toWaitText(): String = when {
    toMinutes() >= 1 && toSecondsPart() == 0 -> "${toMinutes()} min"
    toMinutes() >= 1 -> "${toMinutes()} min ${toSecondsPart()} s"
    else -> "$seconds s"
}

fun Instant.toLastSeenText(): String = LAST_SEEN.format(this)

private val LAST_SEEN = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(ZoneId.systemDefault())
