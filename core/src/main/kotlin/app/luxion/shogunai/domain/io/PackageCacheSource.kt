package app.luxion.shogunai.domain.io

import app.luxion.shogunai.domain.model.PackageCacheSizes

fun interface PackageCacheSource {
    suspend fun sizes(): PackageCacheSizes

    companion object {
        val NONE = PackageCacheSource { PackageCacheSizes() }
    }
}
