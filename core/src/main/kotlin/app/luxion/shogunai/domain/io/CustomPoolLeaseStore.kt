package app.luxion.shogunai.domain.io

import app.luxion.shogunai.domain.model.CustomPoolLeases

interface CustomPoolLeaseStore {

    fun read(): CustomPoolLeases

    fun write(leases: CustomPoolLeases)
}
