package app.luxion.shogunai.domain.model

import kotlinx.serialization.Serializable

// App theme mode: fixed light/dark, or automatic (follows the operating system).
@Serializable
enum class ThemeMode {
    LIGHT,
    DARK,
    SYSTEM,
}
