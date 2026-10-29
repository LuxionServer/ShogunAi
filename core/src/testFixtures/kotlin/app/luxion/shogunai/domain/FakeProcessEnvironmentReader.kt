package app.luxion.shogunai.domain

import app.luxion.shogunai.domain.io.ProcessEnvironmentReader

class FakeProcessEnvironmentReader(var variables: Map<Long, Map<String, String>> = emptyMap()) : ProcessEnvironmentReader {

    val reads = mutableListOf<Long>()

    override suspend fun variable(pid: Long, name: String): String? {
        reads += pid
        return variables[pid]?.get(name)
    }
}
