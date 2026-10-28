package app.luxion.shogunai.domain.io

import app.luxion.shogunai.domain.model.DerivedDataFolder

interface DerivedDataSource {

    fun isSupported(): Boolean

    suspend fun folders(): List<DerivedDataFolder>

    suspend fun size(folder: DerivedDataFolder): Long

    suspend fun packagesSize(folder: DerivedDataFolder): Long

    suspend fun compilationCacheBytes(): Long?

    suspend fun freeBytes(): Long?

    fun projectExists(workspacePath: String): Boolean

    fun isOnUnavailableVolume(workspacePath: String): Boolean

    suspend fun delete(folder: DerivedDataFolder)
}
