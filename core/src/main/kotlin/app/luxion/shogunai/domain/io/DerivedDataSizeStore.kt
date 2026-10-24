package app.luxion.shogunai.domain.io

import app.luxion.shogunai.domain.model.RememberedSize

interface DerivedDataSizeStore {

    fun load(): Map<String, RememberedSize>

    fun save(sizes: Map<String, RememberedSize>)

    companion object {
        val NONE: DerivedDataSizeStore = object : DerivedDataSizeStore {
            override fun load(): Map<String, RememberedSize> = emptyMap()

            override fun save(sizes: Map<String, RememberedSize>) = Unit
        }
    }
}
