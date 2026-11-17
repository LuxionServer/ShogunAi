package app.luxion.shogunai.domain.io

import app.luxion.shogunai.domain.model.DyldCacheFolder

interface DyldCacheSource {

    suspend fun folders(): List<DyldCacheFolder>

    suspend fun currentBuild(): String?

    companion object {
        val NONE: DyldCacheSource = object : DyldCacheSource {
            override suspend fun folders(): List<DyldCacheFolder> = emptyList()

            override suspend fun currentBuild(): String? = null
        }
    }
}
