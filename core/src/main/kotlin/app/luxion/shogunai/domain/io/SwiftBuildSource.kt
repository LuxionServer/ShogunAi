package app.luxion.shogunai.domain.io

import app.luxion.shogunai.domain.model.DerivedDataFolder

interface SwiftBuildSource {

    fun folderOf(worktreePath: String): DerivedDataFolder?

    suspend fun size(folder: DerivedDataFolder): Long

    suspend fun packagesSize(folder: DerivedDataFolder): Long

    suspend fun delete(folder: DerivedDataFolder)

    companion object {
        val NONE: SwiftBuildSource = object : SwiftBuildSource {
            override fun folderOf(worktreePath: String): DerivedDataFolder? = null

            override suspend fun size(folder: DerivedDataFolder): Long = 0L

            override suspend fun packagesSize(folder: DerivedDataFolder): Long = 0L

            override suspend fun delete(folder: DerivedDataFolder) = Unit
        }
    }
}
