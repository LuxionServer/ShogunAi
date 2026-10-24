package app.luxion.shogunai.domain

import app.luxion.shogunai.domain.io.DerivedDataSizeStore
import app.luxion.shogunai.domain.model.RememberedSize

class FakeDerivedDataSizeStore(remembered: Map<String, RememberedSize> = emptyMap()) : DerivedDataSizeStore {

    var sizes: Map<String, RememberedSize> = remembered
    var saves = 0

    override fun load(): Map<String, RememberedSize> = sizes

    override fun save(sizes: Map<String, RememberedSize>) {
        this.sizes = sizes
        saves++
    }
}
