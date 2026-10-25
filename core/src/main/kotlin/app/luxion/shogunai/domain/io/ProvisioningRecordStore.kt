package app.luxion.shogunai.domain.io

interface ProvisioningRecordStore {

    fun read(worktreePath: String): Map<String, List<String>>

    fun write(worktreePath: String, localFolders: Map<String, List<String>>)

    companion object {
        val NONE = object : ProvisioningRecordStore {
            override fun read(worktreePath: String): Map<String, List<String>> = emptyMap()

            override fun write(worktreePath: String, localFolders: Map<String, List<String>>) = Unit
        }
    }
}
