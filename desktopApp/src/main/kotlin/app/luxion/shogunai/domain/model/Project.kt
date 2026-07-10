package app.luxion.shogunai.domain.model

import kotlinx.serialization.Serializable

/**
 * A known project: an identity and display name on top of a [ProjectConfig].
 */
@Serializable
data class Project(
    val id: String,
    val name: String,
    val config: ProjectConfig,
)
