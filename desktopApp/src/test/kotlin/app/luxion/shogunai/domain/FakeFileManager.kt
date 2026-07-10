package app.luxion.shogunai.domain

import app.luxion.shogunai.domain.io.FileManager
import java.io.IOException

/**
 * Doble de [FileManager] en memoria para tests.
 *
 * @param existing Rutas que se consideran existentes al inicio.
 * @param failCopyFor Si el `source` de una copia está en este conjunto, la copia
 *   lanza [IOException] para simular un fallo.
 */
class FakeFileManager(
    existing: Collection<String> = emptyList(),
    private val failCopyFor: Set<String> = emptySet(),
) : FileManager {

    private val paths = existing.toMutableSet()
    val copied = mutableListOf<Pair<String, String>>()

    override fun exists(path: String): Boolean = path in paths

    override fun copy(source: String, destination: String) {
        if (source in failCopyFor) throw IOException("copia fallida de $source")
        copied += source to destination
        paths += destination
    }
}
