package app.luxion.shogunai.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class Project(
    val id: String,
    val name: String,
    val config: ProjectConfig,
)
