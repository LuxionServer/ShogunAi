package app.luxion.shogunai.infrastructure

import app.luxion.shogunai.domain.model.DesktopAppProvider
import app.luxion.shogunai.domain.model.DockerComposeProvider
import app.luxion.shogunai.domain.model.EmulatorProvider
import app.luxion.shogunai.domain.model.EnvironmentEntry
import app.luxion.shogunai.domain.model.RepositoryConfigEdit
import app.luxion.shogunai.domain.model.GradleProvider
import app.luxion.shogunai.domain.model.IosSimulatorProvider
import app.luxion.shogunai.domain.model.MavenProvider
import app.luxion.shogunai.domain.model.RepositoryProviders
import app.luxion.shogunai.domain.model.RepositoryProvisioning
import app.luxion.shogunai.domain.model.WorktreeError
import app.luxion.shogunai.domain.model.XcodeProvider
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.readText
import kotlin.io.path.writeText
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RepositoryProvisioningFilesTest {

    private val repository: Path = Files.createTempDirectory("main-repo")
    private val files = RepositoryProvisioningFiles()
    private val configFile = repository.resolve(".shogunai.json")
    private val worktreeInclude = repository.resolve(".worktreeinclude")

    @AfterTest
    fun cleanUp() {
        repository.toFile().deleteRecursively()
    }

    @Test
    fun `reads nothing when neither file exists`() {
        assertEquals(RepositoryProvisioning(), files.read(repository.toString()))
    }

    @Test
    fun `reads copy patterns and ignores unknown fields`() {
        configFile.writeText("""{ "copy": ["local.properties", ".env*"], "hooks": { "postCreate": ["npm", "install"] } }""")

        val provisioning = files.read(repository.toString())

        assertEquals(listOf("local.properties", ".env*"), provisioning.copyPatterns)
    }

    @Test
    fun `reads several post-creation commands`() {
        configFile.writeText("""{ "hooks": { "postCreate": [["npm", "install"], ["direnv", "allow"]] } }""")

        assertEquals(
            listOf(listOf("npm", "install"), listOf("direnv", "allow")),
            files.read(repository.toString()).postCreateCommands,
        )
    }

    @Test
    fun `reads a single post-creation command given as one argv`() {
        configFile.writeText("""{ "hooks": { "postCreate": ["npm", "install"] } }""")

        assertEquals(listOf(listOf("npm", "install")), files.read(repository.toString()).postCreateCommands)
    }

    @Test
    fun `declares no post-creation command without hooks`() {
        configFile.writeText("""{ "hooks": {} }""")
        assertEquals(emptyList(), files.read(repository.toString()).postCreateCommands)

        configFile.writeText("""{ "copy": [".env"] }""")
        assertEquals(emptyList(), files.read(repository.toString()).postCreateCommands)
    }

    @Test
    fun `fails with InvalidRepositoryConfig on invalid post-creation commands`() {
        listOf(
            """{ "hooks": { "postCreate": "npm install" } }""",
            """{ "hooks": { "postCreate": [[]] } }""",
            """{ "hooks": { "postCreate": [["npm", 1]] } }""",
            """{ "hooks": { "postCreate": [["npm"], "ls"] } }""",
            """{ "hooks": [] }""",
        ).forEach { content ->
            configFile.writeText(content)

            val error = assertFailsWith<WorktreeError.InvalidRepositoryConfig>(content) { files.read(repository.toString()) }

            assertTrue(error.path.endsWith(".shogunai.json"))
        }
    }

    @Test
    fun `reads link patterns`() {
        configFile.writeText("""{ "link": ["/local.properties", "keys/"] }""")

        assertEquals(listOf("/local.properties", "keys/"), files.read(repository.toString()).linkPatterns)
    }

    @Test
    fun `fails with InvalidRepositoryConfig when link is not a list of strings`() {
        configFile.writeText("""{ "link": "local.properties" }""")

        assertFailsWith<WorktreeError.InvalidRepositoryConfig> { files.read(repository.toString()) }
    }

    @Test
    fun `keeps hooks and link when adding patterns`() {
        configFile.writeText("""{ "hooks": { "postCreate": [["npm", "install"]] }, "link": ["keys/"] }""")

        files.addPatterns(repository.toString(), listOf(".env"))

        val written = Json.parseToJsonElement(configFile.readText()).jsonObject
        assertEquals("keys/", written.getValue("link").jsonArray.single().jsonPrimitive.content)
        assertEquals(
            "npm",
            written.getValue("hooks").jsonObject.getValue("postCreate").jsonArray.single().jsonArray.first().jsonPrimitive.content,
        )
        assertEquals(".env", written.getValue("copy").jsonArray.single().jsonPrimitive.content)
    }

    @Test
    fun `reads worktreeinclude skipping comments and blank lines`() {
        worktreeInclude.writeText("# secrets\n\n.env.local\n  config/keys/  \n")

        val provisioning = files.read(repository.toString())

        assertEquals(listOf(".env.local", "config/keys/"), provisioning.worktreeIncludePatterns)
    }

    @Test
    fun `merges the patterns of both files without duplicates`() {
        configFile.writeText("""{ "copy": [".env", "local.properties"] }""")
        worktreeInclude.writeText(".env\n.idea/\n")

        val provisioning = files.read(repository.toString())

        assertEquals(listOf(".env", "local.properties", ".idea/"), provisioning.allPatterns)
    }

    @Test
    fun `treats an empty file or a file without copy as no patterns`() {
        configFile.writeText("   ")
        assertEquals(emptyList(), files.read(repository.toString()).copyPatterns)

        configFile.writeText("{}")
        assertEquals(emptyList(), files.read(repository.toString()).copyPatterns)
    }

    @Test
    fun `fails with InvalidRepositoryConfig on malformed json`() {
        configFile.writeText("""{ "copy": [".env", }""")

        val error = assertFailsWith<WorktreeError.InvalidRepositoryConfig> { files.read(repository.toString()) }

        assertTrue(error.path.endsWith(".shogunai.json"))
        assertFalse(error.detail.orEmpty().contains("\n"), "keeps only the first line of the parser's message")
    }

    @Test
    fun `fails with InvalidRepositoryConfig when copy is not a list of strings`() {
        configFile.writeText("""{ "copy": ".env" }""")
        assertFailsWith<WorktreeError.InvalidRepositoryConfig> { files.read(repository.toString()) }

        configFile.writeText("""{ "copy": [".env", 3] }""")
        assertFailsWith<WorktreeError.InvalidRepositoryConfig> { files.read(repository.toString()) }

        configFile.writeText("""[".env"]""")
        assertFailsWith<WorktreeError.InvalidRepositoryConfig> { files.read(repository.toString()) }
    }

    @Test
    fun `reads the gradle provider and ignores unknown providers`() {
        configFile.writeText("""{ "providers": { "gradle": {}, "cocoapods": {} } }""")

        assertEquals(RepositoryProviders(gradle = GradleProvider()), files.read(repository.toString()).providers)
    }

    @Test
    fun `reads the gradle provider settings`() {
        configFile.writeText(
            """{ "providers": { "gradle": { "daemonMaxHeap": "2g", "idleTimeoutMinutes": 30, "kotlinDaemonMaxHeap": "1024M" } } }""",
        )

        val gradle = files.read(repository.toString()).providers.gradle

        assertEquals(GradleProvider(daemonMaxHeap = "2g", idleTimeoutMinutes = 30, kotlinDaemonMaxHeap = "1024M"), gradle)
        assertEquals(true, gradle?.hasSettings)
    }

    @Test
    fun `declares the gradle provider without settings`() {
        configFile.writeText("""{ "providers": { "gradle": {} } }""")

        assertEquals(false, files.read(repository.toString()).providers.gradle?.hasSettings)
    }

    @Test
    fun `fails with InvalidRepositoryConfig on invalid gradle provider settings`() {
        listOf(
            """{ "providers": { "gradle": { "daemonMaxHeap": "2 GB" } } }""",
            """{ "providers": { "gradle": { "daemonMaxHeap": "" } } }""",
            """{ "providers": { "gradle": { "daemonMaxHeap": "2048" } } }""",
            """{ "providers": { "gradle": { "kotlinDaemonMaxHeap": 1 } } }""",
            """{ "providers": { "gradle": { "idleTimeoutMinutes": 0 } } }""",
            """{ "providers": { "gradle": { "idleTimeoutMinutes": -5 } } }""",
            """{ "providers": { "gradle": { "idleTimeoutMinutes": 1.5 } } }""",
            """{ "providers": { "gradle": { "idleTimeoutMinutes": "30" } } }""",
        ).forEach { content ->
            configFile.writeText(content)
            val error = assertFailsWith<WorktreeError.InvalidRepositoryConfig>(content) { files.read(repository.toString()) }
            assertEquals(true, error.message.orEmpty().contains("providers.gradle."), content)
        }
    }

    @Test
    fun `reads the xcode provider with and without compilation caching`() {
        configFile.writeText("""{ "providers": { "xcode": { "compilationCache": true } } }""")
        assertEquals(XcodeProvider(compilationCache = true), files.read(repository.toString()).providers.xcode)

        configFile.writeText("""{ "providers": { "xcode": {} } }""")
        assertEquals(XcodeProvider(), files.read(repository.toString()).providers.xcode)
    }

    @Test
    fun `fails with InvalidRepositoryConfig on an invalid xcode provider`() {
        listOf(
            """{ "providers": { "xcode": true } }""",
            """{ "providers": { "xcode": { "compilationCache": "yes" } } }""",
            """{ "providers": { "xcode": { "compilationCache": 1 } } }""",
        ).forEach { content ->
            configFile.writeText(content)
            assertFailsWith<WorktreeError.InvalidRepositoryConfig>(content) { files.read(repository.toString()) }
        }
    }

    @Test
    fun `reads the maven provider with and without a heap`() {
        configFile.writeText("""{ "providers": { "maven": { "mavenMaxHeap": "1g" } } }""")
        assertEquals(MavenProvider("1g"), files.read(repository.toString()).providers.maven)

        configFile.writeText("""{ "providers": { "maven": {} } }""")
        assertEquals(MavenProvider(), files.read(repository.toString()).providers.maven)
    }

    @Test
    fun `fails with InvalidRepositoryConfig naming the field on an invalid maven provider`() {
        listOf(
            """{ "providers": { "maven": true } }""",
            """{ "providers": { "maven": { "mavenMaxHeap": "1 GB" } } }""",
            """{ "providers": { "maven": { "mavenMaxHeap": "1000" } } }""",
            """{ "providers": { "maven": { "mavenMaxHeap": 1 } } }""",
        ).forEach { content ->
            configFile.writeText(content)
            val error = assertFailsWith<WorktreeError.InvalidRepositoryConfig>(content) { files.read(repository.toString()) }
            assertTrue(error.message.orEmpty().contains("providers.maven"), content)
        }
    }

    @Test
    fun `reads the docker compose provider with its port variables`() {
        configFile.writeText("""{ "providers": { "docker-compose": { "portVariables": ["DB_PORT"] } } }""")
        assertEquals(DockerComposeProvider(listOf("DB_PORT")), files.read(repository.toString()).providers.dockerCompose)

        configFile.writeText("""{ "providers": { "docker-compose": {} } }""")
        assertEquals(DockerComposeProvider(), files.read(repository.toString()).providers.dockerCompose)
    }

    @Test
    fun `fails with InvalidRepositoryConfig on invalid docker compose port variables`() {
        mapOf(
            """{ "providers": { "docker-compose": true } }""" to "providers.docker-compose",
            """{ "providers": { "docker-compose": { "portVariables": "DB_PORT" } } }""" to "portVariables",
            """{ "providers": { "docker-compose": { "portVariables": [1] } } }""" to "portVariables",
            """{ "providers": { "docker-compose": { "portVariables": ["1DB"] } } }""" to "1DB",
            """{ "providers": { "docker-compose": { "portVariables": ["PATH"] } } }""" to "PATH",
            """{ "providers": { "docker-compose": { "portVariables": ["https_proxy"] } } }""" to "https_proxy",
            """{ "providers": { "docker-compose": { "portVariables": ["DOCKER_HOST"] } } }""" to "DOCKER_HOST",
            """{ "providers": { "docker-compose": { "portVariables": ["DB_PORT", "DB_PORT"] } } }""" to "DB_PORT",
        ).forEach { (content, mention) ->
            configFile.writeText(content)
            val error = assertFailsWith<WorktreeError.InvalidRepositoryConfig>(content) { files.read(repository.toString()) }
            assertTrue(error.message.orEmpty().contains(mention), content)
        }
    }

    @Test
    fun `writes maven and docker compose options and omits empty ones`() {
        write(
            edit(
                providers = RepositoryProviders(
                    maven = MavenProvider("1g"),
                    dockerCompose = DockerComposeProvider(listOf("DB_PORT", "API_PORT")),
                ),
            ),
        )
        val providers = written().getValue("providers").jsonObject
        assertEquals("1g", providers.getValue("maven").jsonObject.getValue("mavenMaxHeap").jsonPrimitive.content)
        assertEquals(
            listOf("DB_PORT", "API_PORT"),
            providers.getValue("docker-compose").jsonObject.getValue("portVariables").jsonArray.map { it.jsonPrimitive.content },
        )

        write(edit(providers = RepositoryProviders(maven = MavenProvider(), dockerCompose = DockerComposeProvider())))
        val emptied = written().getValue("providers").jsonObject
        assertEquals(emptySet(), emptied.getValue("maven").jsonObject.keys)
        assertEquals(emptySet(), emptied.getValue("docker-compose").jsonObject.keys)
    }

    @Test
    fun `reads the emulator provider with and without a preferred AVD`() {
        configFile.writeText("""{ "providers": { "emulator": { "avd": "Pixel_8_API_35" } } }""")
        assertEquals(EmulatorProvider("Pixel_8_API_35"), files.read(repository.toString()).providers.emulator)

        configFile.writeText("""{ "providers": { "emulator": {} } }""")
        assertEquals(EmulatorProvider(), files.read(repository.toString()).providers.emulator)
    }

    @Test
    fun `reads the iOS simulator provider with and without preferences`() {
        configFile.writeText("""{ "providers": { "ios-simulator": { "device": "iPhone 16", "runtime": "iOS 18.5" } } }""")
        assertEquals(IosSimulatorProvider("iPhone 16", "iOS 18.5"), files.read(repository.toString()).providers.iosSimulator)

        configFile.writeText("""{ "providers": { "ios-simulator": {} } }""")
        assertEquals(IosSimulatorProvider(), files.read(repository.toString()).providers.iosSimulator)
    }

    @Test
    fun `fails with InvalidRepositoryConfig on an invalid iOS simulator provider`() {
        listOf(
            """{ "providers": { "ios-simulator": true } }""",
            """{ "providers": { "ios-simulator": { "device": 16 } } }""",
            """{ "providers": { "ios-simulator": { "runtime": "" } } }""",
        ).forEach { content ->
            configFile.writeText(content)
            assertFailsWith<WorktreeError.InvalidRepositoryConfig>(content) { files.read(repository.toString()) }
        }
    }

    @Test
    fun `fails with InvalidRepositoryConfig on an invalid emulator provider`() {
        listOf(
            """{ "providers": { "emulator": true } }""",
            """{ "providers": { "emulator": { "avd": 35 } } }""",
            """{ "providers": { "emulator": { "avd": "" } } }""",
        ).forEach { content ->
            configFile.writeText(content)
            assertFailsWith<WorktreeError.InvalidRepositoryConfig>(content) { files.read(repository.toString()) }
        }
    }

    @Test
    fun `reads the desktop app provider with an isolated home by default`() {
        configFile.writeText("""{ "providers": { "desktop-app": { "module": ":desktopApp" } } }""")
        assertEquals(DesktopAppProvider(":desktopApp"), files.read(repository.toString()).providers.desktopApp)

        configFile.writeText("""{ "providers": { "desktop-app": { "module": ":apps:desktop", "isolatedHome": false } } }""")
        assertEquals(DesktopAppProvider(":apps:desktop", isolatedHome = false), files.read(repository.toString()).providers.desktopApp)
    }

    @Test
    fun `fails with InvalidRepositoryConfig on an invalid desktop app provider`() {
        listOf(
            """{ "providers": { "desktop-app": true } }""",
            """{ "providers": { "desktop-app": {} } }""",
            """{ "providers": { "desktop-app": { "module": "" } } }""",
            """{ "providers": { "desktop-app": { "module": "desktopApp/build.gradle.kts" } } }""",
            """{ "providers": { "desktop-app": { "module": ":desktopApp --init-script x" } } }""",
            """{ "providers": { "desktop-app": { "module": ":desktopApp", "isolatedHome": "yes" } } }""",
        ).forEach { content ->
            configFile.writeText(content)
            val error = assertFailsWith<WorktreeError.InvalidRepositoryConfig>(content) { files.read(repository.toString()) }
            assertTrue("providers.desktop-app" in error.detail.orEmpty(), content)
        }
    }

    @Test
    fun `editing other providers keeps the desktop app provider`() {
        configFile.writeText("""{ "providers": { "desktop-app": { "module": ":desktopApp" } } }""")

        write(edit(providers = RepositoryProviders(gradle = GradleProvider())))

        assertEquals(DesktopAppProvider(":desktopApp"), files.read(repository.toString()).providers.desktopApp)
    }

    @Test
    fun `declares no provider without the field or the file`() {
        assertEquals(RepositoryProviders(), files.read(repository.toString()).providers)

        configFile.writeText("""{ "copy": [".env"] }""")
        assertEquals(RepositoryProviders(), files.read(repository.toString()).providers)

        configFile.writeText("""{ "providers": { "cocoapods": true } }""")
        assertEquals(RepositoryProviders(), files.read(repository.toString()).providers)
    }

    @Test
    fun `fails with InvalidRepositoryConfig when providers or a known provider is not an object`() {
        configFile.writeText("""{ "providers": ["gradle"] }""")
        assertFailsWith<WorktreeError.InvalidRepositoryConfig> { files.read(repository.toString()) }

        configFile.writeText("""{ "providers": { "gradle": true } }""")
        assertFailsWith<WorktreeError.InvalidRepositoryConfig> { files.read(repository.toString()) }
    }

    @Test
    fun `reads the environment and its file in declaration order`() {
        configFile.writeText("""{ "env": { "PORT": "${'$'}{port}", "APP_SUFFIX": "-${'$'}{worktree}" }, "envFile": ".env.shogunai" }""")

        val environment = files.read(repository.toString()).environment

        assertEquals(listOf("PORT", "APP_SUFFIX"), environment.variables.map { it.name })
        assertEquals(listOf("${'$'}{port}", "-${'$'}{worktree}"), environment.variables.map { it.source })
        assertEquals(".env.shogunai", environment.envFile)
    }

    @Test
    fun `names the variable whose value has a line break`() {
        configFile.writeText("""{ "env": { "APP_NAME": "demo\nexport OTHER=1" } }""")

        val error = assertFailsWith<WorktreeError.InvalidRepositoryConfig> { files.read(repository.toString()) }

        assertTrue(error.message.orEmpty().contains("APP_NAME"))
    }

    @Test
    fun `declares an empty environment without the field`() {
        configFile.writeText("""{ "copy": [".env"] }""")

        assertTrue(files.read(repository.toString()).environment.isEmpty)
    }

    @Test
    fun `fails with InvalidRepositoryConfig on an invalid environment`() {
        listOf(
            """{ "env": ["PORT"] }""",
            """{ "env": { "PORT": 3000 } }""",
            """{ "env": { "1PORT": "x" } }""",
            """{ "env": { "A": "${'$'}{branch}" } }""",
            """{ "envFile": "/tmp/shared.env" }""",
            """{ "envFile": "../shared.env" }""",
            """{ "envFile": 3 }""",
            """{ "env": { "XDG_CONFIG_HOME": "x" } }""",
            """{ "env": { "APP_NAME": "demo\nexport OTHER=1" } }""",
            """{ "env": { "APP_NAME": "a\u202Eb" } }""",
            """{ "envFile": ".env\nshogunai" }""",
            """{ "envFile": ".env\u200Bshogunai" }""",
        ).forEach { content ->
            configFile.writeText(content)
            assertFailsWith<WorktreeError.InvalidRepositoryConfig>(content) { files.read(repository.toString()) }
        }
    }

    @Test
    fun `keeps the environment when adding patterns`() {
        configFile.writeText("""{ "env": { "PORT": "${'$'}{port}" }, "envFile": ".env.shogunai" }""")

        files.addPatterns(repository.toString(), listOf("/local.properties"))

        val environment = files.read(repository.toString()).environment
        assertEquals(listOf("PORT"), environment.variables.map { it.name })
        assertEquals(".env.shogunai", environment.envFile)
    }

    @Test
    fun `keeps providers when adding patterns`() {
        configFile.writeText("""{ "providers": { "gradle": {} } }""")

        files.addPatterns(repository.toString(), listOf("/local.properties"))

        val provisioning = files.read(repository.toString())
        assertEquals(RepositoryProviders(gradle = GradleProvider()), provisioning.providers)
        assertEquals(listOf("/local.properties"), provisioning.copyPatterns)
    }

    @Test
    fun `creates the file when adding patterns to a repository without one`() {
        files.addPatterns(repository.toString(), listOf("/local.properties"))

        assertEquals(listOf("/local.properties"), files.read(repository.toString()).copyPatterns)
        assertTrue(configFile.readText().endsWith("\n"))
    }

    @Test
    fun `appends patterns without duplicates and keeps other fields`() {
        configFile.writeText("""{ "copy": [".env"], "hooks": { "postCreate": ["npm", "install"] } }""")

        files.addPatterns(repository.toString(), listOf(".env", "/local.properties"))

        val written = Json.parseToJsonElement(configFile.readText()).jsonObject
        assertEquals(listOf(".env", "/local.properties"), written.getValue("copy").jsonArray.map { it.jsonPrimitive.content })
        assertEquals(
            listOf("npm", "install"),
            written.getValue("hooks").jsonObject.getValue("postCreate").jsonArray.map { it.jsonPrimitive.content },
        )
    }

    @Test
    fun `refuses to overwrite a file it can't read`() {
        val malformed = """{ "copy": [".env", }"""
        configFile.writeText(malformed)

        assertFailsWith<WorktreeError.InvalidRepositoryConfig> {
            files.addPatterns(repository.toString(), listOf("/local.properties"))
        }

        assertEquals(malformed, configFile.readText())
    }

    @Test
    fun `leaves no temporary files behind`() {
        files.addPatterns(repository.toString(), listOf("/local.properties"))

        assertTrue(configFile.exists())
        assertFalse(Files.list(repository).use { stream -> stream.anyMatch { it.fileName.toString().endsWith(".tmp") } })
    }

    private fun edit(
        providers: RepositoryProviders = RepositoryProviders(),
        environment: List<EnvironmentEntry> = emptyList(),
        envFile: String? = null,
    ) = RepositoryConfigEdit(providers = providers, environment = environment, envFile = envFile)

    private fun write(edit: RepositoryConfigEdit) =
        files.write(repository.toString(), edit, files.fingerprint(repository.toString()))

    private fun written() = Json.parseToJsonElement(configFile.readText()).jsonObject

    @Test
    fun `turning a provider on creates the file with only that provider`() {
        write(edit(providers = RepositoryProviders(gradle = GradleProvider())))

        assertEquals(setOf("providers"), written().keys)
        assertEquals(emptySet(), written().getValue("providers").jsonObject.getValue("gradle").jsonObject.keys)
    }

    @Test
    fun `writes gradle options`() {
        write(edit(providers = RepositoryProviders(gradle = GradleProvider(daemonMaxHeap = "2g", idleTimeoutMinutes = 30))))

        val gradle = written().getValue("providers").jsonObject.getValue("gradle").jsonObject
        assertEquals("2g", gradle.getValue("daemonMaxHeap").jsonPrimitive.content)
        assertEquals("30", gradle.getValue("idleTimeoutMinutes").jsonPrimitive.content)
        assertFalse("kotlinDaemonMaxHeap" in gradle)
    }

    @Test
    fun `written provider options read back identically`() {
        val providers = RepositoryProviders(
            gradle = GradleProvider("2g", 30, "1g"),
            emulator = EmulatorProvider("Pixel_8"),
            iosSimulator = IosSimulatorProvider("iPhone 16", "iOS 18.0"),
            xcode = XcodeProvider(compilationCache = true),
            maven = MavenProvider(mavenMaxHeap = "1g"),
            dockerCompose = DockerComposeProvider(portVariables = listOf("DB_PORT", "API_PORT")),
        )

        write(edit(providers = providers))

        assertEquals(providers, files.read(repository.toString()).providers)
    }

    @Test
    fun `turning a provider off removes only that provider`() {
        configFile.writeText("""{ "providers": { "gradle": {}, "emulator": { "avd": "Pixel_8" } } }""")

        write(edit(providers = RepositoryProviders(gradle = GradleProvider())))

        val providers = written().getValue("providers").jsonObject
        assertEquals(setOf("gradle"), providers.keys)
    }

    @Test
    fun `turning the last provider off removes the providers field`() {
        configFile.writeText("""{ "copy": [".env"], "providers": { "gradle": {} } }""")

        write(edit())

        assertEquals(listOf("copy"), written().keys.toList())
    }

    @Test
    fun `keeps copy patterns, unknown fields, unknown providers and unknown options`() {
        configFile.writeText(
            """{ "copy": [".env"], "team": "mobile", "providers": { "cocoapods": { "x": 1 }, "gradle": { "future": true, "daemonMaxHeap": "1g" } } }""",
        )

        write(edit(providers = RepositoryProviders(gradle = GradleProvider(idleTimeoutMinutes = 10))))

        val result = written()
        assertEquals(listOf("copy", "team", "providers"), result.keys.toList())
        assertEquals(".env", result.getValue("copy").jsonArray.single().jsonPrimitive.content)
        assertEquals("mobile", result.getValue("team").jsonPrimitive.content)
        val providers = result.getValue("providers").jsonObject
        assertEquals(listOf("cocoapods", "gradle"), providers.keys.toList())
        val gradle = providers.getValue("gradle").jsonObject
        assertEquals("true", gradle.getValue("future").jsonPrimitive.content)
        assertEquals("10", gradle.getValue("idleTimeoutMinutes").jsonPrimitive.content)
        assertFalse("daemonMaxHeap" in gradle)
    }

    @Test
    fun `keeps the position of fields it rewrites`() {
        configFile.writeText("""{ "env": { "A": "1" }, "copy": [".env"], "envFile": ".env.old" }""")

        write(edit(environment = listOf(EnvironmentEntry("B", "\${port}")), envFile = ".env.new"))

        assertEquals(listOf("env", "copy", "envFile"), written().keys.toList())
        assertEquals("\${port}", written().getValue("env").jsonObject.getValue("B").jsonPrimitive.content)
    }

    @Test
    fun `creates the file with only the env field`() {
        write(edit(environment = listOf(EnvironmentEntry("PORT", "\${port}"))))

        assertEquals(setOf("env"), written().keys)
    }

    @Test
    fun `writes the environment and its file`() {
        write(edit(environment = listOf(EnvironmentEntry("PORT", "\${port}")), envFile = ".env.shogunai"))

        val declaration = files.read(repository.toString()).environment
        assertEquals(listOf("PORT"), declaration.variables.map { it.name })
        assertEquals(".env.shogunai", declaration.envFile)
    }

    @Test
    fun `clearing the environment removes env and envFile`() {
        configFile.writeText("""{ "env": { "A": "1" }, "envFile": ".env", "copy": ["x"] }""")

        write(edit())

        assertEquals(listOf("copy"), written().keys.toList())
    }

    @Test
    fun `writes nothing when there is nothing to declare and no file`() {
        write(edit())

        assertFalse(configFile.exists())
    }

    @Test
    fun `adds copy patterns together with the other fields`() {
        configFile.writeText("""{ "copy": [".env"] }""")

        write(RepositoryConfigEdit(providers = RepositoryProviders(gradle = GradleProvider()), addCopyPatterns = listOf("/local.properties", ".env")))

        assertEquals(
            listOf(".env", "/local.properties"),
            written().getValue("copy").jsonArray.map { it.jsonPrimitive.content },
        )
        assertTrue("providers" in written())
    }

    @Test
    fun `adds copy and link patterns in one write`() {
        configFile.writeText("""{ "link": ["/local.properties"] }""")

        files.addPatterns(repository.toString(), listOf("*.jks"), listOf("/keys", "/local.properties"))

        assertEquals(listOf("*.jks"), written().getValue("copy").jsonArray.map { it.jsonPrimitive.content })
        assertEquals(listOf("/local.properties", "/keys"), written().getValue("link").jsonArray.map { it.jsonPrimitive.content })
    }

    @Test
    fun `adds link patterns without duplicates and keeps the other fields`() {
        configFile.writeText("""{ "team": "mobile", "copy": [".env"], "link": ["/local.properties"] }""")

        write(RepositoryConfigEdit(addLinkPatterns = listOf("/app/google-services.json", "/local.properties")))

        assertEquals(
            listOf("/local.properties", "/app/google-services.json"),
            written().getValue("link").jsonArray.map { it.jsonPrimitive.content },
        )
        assertEquals(listOf(".env"), written().getValue("copy").jsonArray.map { it.jsonPrimitive.content })
        assertEquals("mobile", written().getValue("team").jsonPrimitive.content)
    }

    @Test
    fun `preview shows the resulting file without writing it`() {
        configFile.writeText("""{ "team": "mobile" }""")

        val preview = files.preview(repository.toString(), edit(providers = RepositoryProviders(gradle = GradleProvider())))

        assertTrue("\"team\": \"mobile\"" in preview)
        assertTrue("\"gradle\"" in preview)
        assertEquals("""{ "team": "mobile" }""", configFile.readText())
    }

    @Test
    fun `refuses to write over a file it can't read`() {
        val malformed = """{ "copy": [".env", }"""
        configFile.writeText(malformed)
        val fingerprint = files.fingerprint(repository.toString())

        assertFailsWith<WorktreeError.InvalidRepositoryConfig> {
            files.write(repository.toString(), edit(providers = RepositoryProviders(gradle = GradleProvider())), fingerprint)
        }

        assertEquals(malformed, configFile.readText())
    }

    @Test
    fun `refuses to write when the file changed on disk`() {
        configFile.writeText("""{ "copy": [".env"] }""")
        val fingerprint = files.fingerprint(repository.toString())
        configFile.writeText("""{ "copy": [".env", "other"] }""")

        assertFailsWith<WorktreeError.RepositoryConfigChanged> {
            files.write(repository.toString(), edit(providers = RepositoryProviders(gradle = GradleProvider())), fingerprint)
        }

        assertEquals("""{ "copy": [".env", "other"] }""", configFile.readText())
    }

    @Test
    fun `refuses to write when the file was created meanwhile`() {
        val fingerprint = files.fingerprint(repository.toString())
        configFile.writeText("""{ "copy": [".env"] }""")

        assertFailsWith<WorktreeError.RepositoryConfigChanged> {
            files.write(repository.toString(), edit(environment = listOf(EnvironmentEntry("A", "1"))), fingerprint)
        }

        assertEquals("""{ "copy": [".env"] }""", configFile.readText())
    }

    @Test
    fun `moving local files also refuses when the file changed on disk`() {
        configFile.writeText("""{ "copy": [".env"] }""")
        val fingerprint = files.fingerprint(repository.toString())
        configFile.writeText("""{ "copy": [] }""")

        assertFailsWith<WorktreeError.RepositoryConfigChanged> {
            files.addPatterns(repository.toString(), listOf("/local.properties"), expectedFingerprint = fingerprint)
        }
    }

    @Test
    fun `fingerprint changes with the content and marks an absent file`() {
        val absent = files.fingerprint(repository.toString())
        configFile.writeText("{}")
        val empty = files.fingerprint(repository.toString())
        configFile.writeText("""{ "copy": [] }""")

        assertEquals("absent", absent)
        assertTrue(empty != absent)
        assertTrue(files.fingerprint(repository.toString()) != empty)
    }

    @Test
    fun `fails with InvalidRepositoryConfig on a reserved variable name and accepts a regular one`() {
        listOf("PATH", "LD_PRELOAD", "GIT_SSH_COMMAND").forEach { name ->
            configFile.writeText("""{ "env": { "$name": "x" } }""")
            val error = assertFailsWith<WorktreeError.InvalidRepositoryConfig>(name) { files.read(repository.toString()) }
            assertTrue(error.detail.orEmpty().contains(name), name)
        }
        configFile.writeText("""{ "env": { "PORT": "x" } }""")
        assertEquals(listOf("PORT"), files.read(repository.toString()).environment.variables.map { it.name })
    }
}
