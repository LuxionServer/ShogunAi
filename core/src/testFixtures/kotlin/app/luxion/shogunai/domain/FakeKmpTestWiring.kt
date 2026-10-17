package app.luxion.shogunai.domain

import app.luxion.shogunai.domain.io.KmpTestWiring

class FakeKmpTestWiring(
    var wiredRepositories: Set<String> = emptySet(),
    var installed: Boolean = false,
    var nonMultiplatformRepositories: Set<String> = emptySet(),
) : KmpTestWiring {

    var installs = 0
        private set

    override fun isWiredInRepository(repositoryPath: String): Boolean = repositoryPath in wiredRepositories

    override fun usesKotlinMultiplatform(repositoryPath: String): Boolean = repositoryPath !in nonMultiplatformRepositories

    override fun isInitScriptInstalled(): Boolean = installed

    override fun initScriptPath(): String = "/home/dev/.gradle/init.d/shogunai-ios-simulator.init.gradle.kts"

    override fun installInitScript() {
        installs++
        installed = true
    }

    override fun uninstallInitScript() {
        installed = false
    }
}
