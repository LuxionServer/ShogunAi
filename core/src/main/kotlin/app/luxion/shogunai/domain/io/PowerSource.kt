package app.luxion.shogunai.domain.io

interface PowerSource {

    suspend fun isOnBattery(): Boolean?

    companion object {
        val UNKNOWN = object : PowerSource {
            override suspend fun isOnBattery(): Boolean? = null
        }
    }
}
