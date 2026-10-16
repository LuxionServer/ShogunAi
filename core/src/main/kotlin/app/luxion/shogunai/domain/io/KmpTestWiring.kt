package app.luxion.shogunai.domain.io

interface KmpTestWiring {

    fun isWiredInRepository(repositoryPath: String): Boolean

    fun isInitScriptInstalled(): Boolean

    fun initScriptPath(): String

    fun installInitScript()

    fun uninstallInitScript()
}
