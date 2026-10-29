package app.luxion.shogunai.domain.io

interface ProcessEnvironmentReader {

    suspend fun variable(pid: Long, name: String): String?
}
