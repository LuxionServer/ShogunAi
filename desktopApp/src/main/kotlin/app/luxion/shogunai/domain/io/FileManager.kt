package app.luxion.shogunai.domain.io

/**
 * Puerto de dominio para operaciones de ficheros.
 *
 * Se abstrae del sistema de ficheros real para que los casos de uso —como la
 * copia de archivos de secretos— sean unitariamente testeables sin tocar disco.
 */
interface FileManager {

    /** Indica si existe un fichero o directorio en [path]. */
    fun exists(path: String): Boolean

    /**
     * Copia [source] a [destination], sobrescribiendo si ya existía.
     *
     * @throws java.io.IOException si la copia falla.
     */
    fun copy(source: String, destination: String)
}
