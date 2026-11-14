package app.luxion.shogunai.domain

import app.luxion.shogunai.domain.io.CustomPoolLeaseStore
import app.luxion.shogunai.domain.model.CustomPoolLeases

class FakeCustomPoolLeaseStore(var leases: CustomPoolLeases = CustomPoolLeases()) : CustomPoolLeaseStore {

    var writes = 0

    override fun read(): CustomPoolLeases = leases

    override fun write(leases: CustomPoolLeases) {
        writes++
        this.leases = leases
    }
}
