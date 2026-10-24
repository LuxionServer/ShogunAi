package app.luxion.shogunai.domain.io

interface LauncherFiles {
    fun exists(path: String): Boolean

    fun isWritableFolder(folder: String): Boolean

    fun writeText(path: String, text: String)

    fun readText(path: String): String?

    fun linkTarget(link: String): String?

    fun createLink(target: String, link: String)

    fun delete(path: String)
}

interface UserPathEntries {
    suspend fun contains(folder: String): Boolean

    suspend fun add(folder: String)

    suspend fun remove(folder: String)
}
