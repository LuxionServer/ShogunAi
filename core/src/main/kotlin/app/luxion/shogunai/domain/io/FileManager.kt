package app.luxion.shogunai.domain.io

interface FileManager {

    fun exists(path: String): Boolean

    fun isInside(root: String, path: String): Boolean

    fun copy(source: String, destination: String)

    fun createSymbolicLink(target: String, link: String)

    fun isSymbolicLinkTo(link: String, target: String): Boolean

    fun hasSameContent(first: String, second: String): Boolean

    fun directorySize(path: String, excluding: Set<String> = emptySet()): Long

    fun isDirectory(path: String): Boolean

    fun modificationStamps(path: String): List<Pair<String, Long>>

    fun readProperties(path: String): Map<String, String>

    fun writeText(path: String, text: String)

    fun readText(path: String): String?

    fun lastModifiedMillis(path: String): Long?
}

class SymbolicLinksNotSupported(cause: Throwable? = null) : Exception("Symbolic links aren't supported here", cause)
