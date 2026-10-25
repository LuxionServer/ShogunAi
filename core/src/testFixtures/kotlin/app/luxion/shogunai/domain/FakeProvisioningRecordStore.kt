package app.luxion.shogunai.domain

import app.luxion.shogunai.domain.io.ProvisioningRecordStore
import java.io.IOException

class FakeProvisioningRecordStore(
    initial: Map<String, Map<String, List<String>>> = emptyMap(),
) : ProvisioningRecordStore {

    val records = initial.toMutableMap()
    var failWrites = false

    override fun read(worktreePath: String): Map<String, List<String>> = records[worktreePath].orEmpty()

    override fun write(worktreePath: String, localFolders: Map<String, List<String>>) {
        if (failWrites) throw IOException("escritura fallida del registro de $worktreePath")
        records[worktreePath] = localFolders
    }
}
