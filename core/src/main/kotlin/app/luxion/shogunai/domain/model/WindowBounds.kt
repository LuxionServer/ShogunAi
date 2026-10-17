package app.luxion.shogunai.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class WindowBounds(
    val x: Int,
    val y: Int,
    val width: Int,
    val height: Int,
    val maximized: Boolean = false,
)
