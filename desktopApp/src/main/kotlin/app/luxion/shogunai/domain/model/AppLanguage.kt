package app.luxion.shogunai.domain.model

import kotlinx.serialization.Serializable

// Language the UI is shown in. English is the default when nothing is stored.
@Serializable
enum class AppLanguage {
    ENGLISH,
    SPANISH,
}
