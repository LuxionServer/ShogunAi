package app.luxion.shogunai.domain.model

import kotlinx.serialization.Serializable

@Serializable
enum class WorktreeSort { NAME, ACTIVITY }

enum class SortedView { ACTIVE, ALL, PROJECT }

const val DEFAULT_INACTIVE_AFTER_DAYS = 3
val INACTIVE_AFTER_DAYS_RANGE = 1..90
