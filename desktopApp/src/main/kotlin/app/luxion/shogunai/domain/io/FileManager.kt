package app.luxion.shogunai.domain.io

interface FileManager {

    fun exists(path: String): Boolean

    fun copy(source: String, destination: String)
}
