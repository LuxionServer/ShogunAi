package app.luxion.shogunai.domain.io

import app.luxion.shogunai.domain.model.HostOs

interface HostEnvironment {
    val os: HostOs
    val home: String
    val shell: String?
    val path: HostPath

    fun variable(name: String): String?
}

data class HostPath(val folders: List<String>, val source: HostPathSource)

enum class HostPathSource { LOGIN_SHELL, PROCESS, FALLBACK }
