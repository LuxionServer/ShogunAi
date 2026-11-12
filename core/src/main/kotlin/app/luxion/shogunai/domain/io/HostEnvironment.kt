package app.luxion.shogunai.domain.io

import app.luxion.shogunai.domain.model.HostOs
import app.luxion.shogunai.domain.model.HostPathSource

interface HostEnvironment {
    val os: HostOs
    val home: String
    val shell: String?
    val path: HostPath
    val userBinFolders: List<String>

    fun variable(name: String): String?
}

data class HostPath(val folders: List<String>, val source: HostPathSource)
