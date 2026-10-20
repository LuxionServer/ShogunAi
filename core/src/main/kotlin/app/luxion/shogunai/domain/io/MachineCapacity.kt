package app.luxion.shogunai.domain.io

interface MachineCapacity {

    fun totalMemoryBytes(): Long?

    fun cores(): Int
}
