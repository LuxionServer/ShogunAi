package app.luxion.shogunai.domain.model

import kotlinx.serialization.Serializable

// Modo de tema de la app: claro/oscuro fijos, o automático (sigue al sistema operativo).
@Serializable
enum class ThemeMode {
    LIGHT,
    DARK,
    SYSTEM,
}
