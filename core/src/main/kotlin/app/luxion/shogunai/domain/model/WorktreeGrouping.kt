package app.luxion.shogunai.domain.model

import kotlinx.serialization.Serializable

@Serializable
enum class Grouping { BY_STATE, BY_PROJECT, BY_CHANGE }

enum class GroupedView(val defaultGrouping: Grouping) {
    ACTIVE(Grouping.BY_STATE),
    ALL(Grouping.BY_PROJECT),
    MULTIREPO(Grouping.BY_CHANGE),
}
