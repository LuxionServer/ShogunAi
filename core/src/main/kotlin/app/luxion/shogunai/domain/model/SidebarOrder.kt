package app.luxion.shogunai.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class SidebarOrder(
    val topLevel: List<String> = emptyList(),
    val multirepos: Map<String, List<String>> = emptyMap(),
)
