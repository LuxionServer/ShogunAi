import kotlinx.kover.gradle.plugin.dsl.CoverageUnit
import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import org.jetbrains.compose.desktop.application.tasks.AbstractJPackageTask

plugins {
    alias(libs.plugins.kotlinJvm)
    alias(libs.plugins.kover)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
}

@OptIn(org.jetbrains.compose.ExperimentalComposeLibrary::class)
dependencies {
    implementation(project(":core"))
    implementation(project(":cli"))

    implementation(compose.desktop.currentOs)
    implementation(compose.runtime)
    implementation(compose.foundation)
    implementation(compose.material3)
    implementation(compose.ui)
    implementation(libs.compose.materialIconsExtended)
    implementation(libs.kotlinx.coroutinesSwing)
    implementation(libs.kotlinx.coroutinesCore)
    implementation(libs.kotlinx.serializationJson)
    implementation(libs.androidx.lifecycle.viewmodelCompose)
    implementation(libs.androidx.lifecycle.runtimeCompose)
    implementation(libs.filekit.dialogs)

    testImplementation(testFixtures(project(":core")))
    testImplementation(libs.kotlin.testJunit)
    testImplementation(libs.kotlin.reflect)
    testImplementation(compose.uiTest)
    testImplementation(libs.kotlinx.coroutinesTest)
    testImplementation(libs.kotlinx.serializationJson)
}

val e2ePackage = "app/luxion/shogunai/e2e/**"
val e2eRoot = layout.buildDirectory.dir("e2e")
val isWindows = System.getProperty("os.name").startsWith("Windows")
val shogunLaunchers = if (isWindows) listOf("shogun.exe", "shogun.cmd", "shogun.bat") else listOf("shogun")
val pathWithoutShogun = System.getenv("PATH").orEmpty().split(File.pathSeparator)
    .filter { it.isNotBlank() && shogunLaunchers.none { name -> File(it, name).exists() } }

val desktopE2eTest by tasks.registering(Test::class) {
    description = "Runs the desktop app's end-to-end tests in an isolated home."
    group = "verification"
    testClassesDirs = sourceSets.test.get().output.classesDirs
    classpath = sourceSets.test.get().runtimeClasspath
    include(e2ePackage)
    outputs.upToDateWhen { false }
    outputs.cacheIf { false }
    testLogging {
        events("failed")
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
    }
    val root = e2eRoot.get().asFile
    val home = root.resolve("home")
    val temporary = root.resolve("tmp")
    val bin = root.resolve("bin")
    doFirst {
        root.deleteRecursively()
        listOf(home, temporary, bin).forEach { it.mkdirs() }
    }
    systemProperty("user.home", home.absolutePath)
    systemProperty("java.io.tmpdir", temporary.absolutePath)
    systemProperty("shogunai.e2e.home", home.absolutePath)
    systemProperty("shogunai.e2e.bin", bin.absolutePath)
    val isolatedVariables = listOf("CLAUDE_CONFIG_DIR", "HOME", "USERPROFILE", "SHELL", "PATH", "GIT_CONFIG_NOSYSTEM")
    environment = environment.filterKeys { key -> isolatedVariables.none { it.equals(key, ignoreCase = true) } } + mapOf(
        "HOME" to home.absolutePath,
        "USERPROFILE" to home.absolutePath,
        "SHELL" to "",
        "PATH" to (listOf(bin.absolutePath) + pathWithoutShogun).joinToString(File.pathSeparator),
        "GIT_CONFIG_NOSYSTEM" to "1",
    )
}

tasks.test {
    exclude(e2ePackage)
    dependsOn(desktopE2eTest)
}

fun macBundleVersion(version: String): String {
    val (major, minor, patch) = version.split(".")
    return "${major.toInt() + 1}.$minor.$patch"
}

val isMacOs = System.getProperty("os.name").startsWith("Mac")
val notifierSources = layout.projectDirectory.dir("macos/notifier")
val notifierResourcesRoot = layout.buildDirectory.dir("notifier")

val buildNotifier by tasks.registering {
    description = "Builds and signs the macOS helper app that posts ShogunAi's notifications."
    val sources = notifierSources
    val icon = layout.projectDirectory.file("icons/icon.icns")
    val resourcesRoot = notifierResourcesRoot
    val version = (findProperty("appVersion") as String?) ?: "0.0.0"
    val identity = findProperty("macSigningIdentity") as String?
    val keychain = findProperty("macSigningKeychain") as String?
    val onMac = isMacOs
    val architecture = if (System.getProperty("os.arch") in setOf("aarch64", "arm64")) "arm64" else "x86_64"
    inputs.dir(sources)
    inputs.file(icon)
    inputs.property("version", version)
    inputs.property("identity", identity.orEmpty())
    outputs.dir(resourcesRoot)
    onlyIf { onMac }
    doLast {
        fun run(vararg command: String) {
            val exitCode = ProcessBuilder(*command).inheritIO().start().waitFor()
            check(exitCode == 0) { "${command.joinToString(" ")} exited with $exitCode" }
        }
        val root = resourcesRoot.get().asFile
        root.deleteRecursively()
        val bundle = root.resolve("macos/ShogunAi Notifications.app/Contents")
        bundle.resolve("MacOS").mkdirs()
        bundle.resolve("Resources").mkdirs()
        bundle.resolve("Info.plist").writeText(sources.file("Info.plist").asFile.readText().replace("@VERSION@", version))
        icon.asFile.copyTo(bundle.resolve("Resources/icon.icns"))
        run(
            "swiftc", "-O", "-target", "$architecture-apple-macos12.0",
            "-o", bundle.resolve("MacOS/shogunai-notifier").path,
            sources.file("main.swift").asFile.path,
        )
        val signing = if (identity != null) {
            listOf("--options", "runtime", "--timestamp", "--sign", "Developer ID Application: $identity") +
                (keychain?.let { listOf("--keychain", it) } ?: emptyList())
        } else {
            listOf("--sign", "-")
        }
        run(*(listOf("codesign", "--force") + signing + bundle.parentFile.path).toTypedArray())
    }
}

tasks.matching { it.name == "prepareAppResources" }.configureEach { dependsOn(buildNotifier) }

tasks.withType<JavaExec>().matching { it.name.startsWith("hotRun") }.configureEach {
    dependsOn(buildNotifier)
    systemProperty("compose.application.resources.dir", notifierResourcesRoot.get().dir("macos").asFile.absolutePath)
}

compose.desktop {
    application {
        mainClass = "app.luxion.shogunai.MainKt"

        buildTypes.release.proguard {
            configurationFiles.from(project.file("proguard-rules.pro"))
        }

        nativeDistributions {
            targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb)
            packageName = "ShogunAi"
            vendor = "LuxionServer"
            val appVersion = (findProperty("appVersion") as String?) ?: "0.0.0"
            packageVersion = appVersion

            modules("java.net.http", "java.management", "jdk.management", "jdk.net", "jdk.security.auth")
            appResourcesRootDir.set(notifierResourcesRoot)

            macOS {
                packageVersion = macBundleVersion(appVersion)
                iconFile.set(project.file("icons/icon.icns"))
                bundleID = "app.luxion.shogunai"
                entitlementsFile.set(project.file("macos/entitlements.plist"))
                runtimeEntitlementsFile.set(project.file("macos/entitlements.plist"))
                infoPlist {
                    extraKeysRawXml = """
                        <key>NSAppleEventsUsageDescription</key>
                        <string>ShogunAi opens worktrees in your terminal and brings app windows forward.</string>
                    """.trimIndent()
                }
                val signingIdentity = findProperty("macSigningIdentity") as String?
                if (signingIdentity != null) {
                    signing {
                        sign.set(true)
                        identity.set(signingIdentity)
                        (findProperty("macSigningKeychain") as String?)?.let { keychain.set(it) }
                    }
                }
            }
            windows {
                iconFile.set(project.file("icons/icon.ico"))
                upgradeUuid = "964E9CC7-8B48-3DF7-8C9E-946E463F038D"
            }
            linux {
                iconFile.set(project.file("icons/icon.png"))
            }
        }
    }
}

tasks.withType<org.jetbrains.compose.desktop.application.tasks.AbstractJLinkTask>().configureEach {
    doLast {
        val javaExecutable = File(javaHome.get(), "bin/java.exe")
        if (javaExecutable.exists()) {
            javaExecutable.copyTo(destinationDir.get().asFile.resolve("bin/java.exe"), overwrite = true)
        }
    }
}

tasks.withType<AbstractJPackageTask>().configureEach {
    if (targetFormat == TargetFormat.Deb) {
        val packageFolder = destinationDir
        val unpackedRoot = temporaryDir
        doLast {
            fun run(vararg command: String) {
                val exitCode = ProcessBuilder(*command).inheritIO().start().waitFor()
                check(exitCode == 0) { "${command.joinToString(" ")} exited with $exitCode" }
            }
            val menuCommand = "xdg-desktop-menu "
            val warnings = mapOf(
                "postinst" to "ShogunAi: not added to the applications menu, which needs a writable /etc/xdg/menus.",
                "prerm" to "ShogunAi: no applications menu entry to remove.",
            )
            packageFolder.get().asFile.listFiles { file -> file.extension == "deb" }.orEmpty().forEach { deb ->
                val unpacked = unpackedRoot.resolve(deb.nameWithoutExtension)
                unpacked.deleteRecursively()
                run("dpkg-deb", "--raw-extract", deb.path, unpacked.path)
                warnings.forEach { (script, warning) ->
                    val file = unpacked.resolve("DEBIAN/$script")
                    val lines = file.readLines()
                    check(lines.any { it.startsWith(menuCommand) }) {
                        "jpackage's $script no longer runs $menuCommand, so the .deb's maintainer scripts can't be rewritten."
                    }
                    file.writeText(
                        lines.joinToString("\n", postfix = "\n") { line ->
                            if (line.startsWith(menuCommand)) "$line || echo \"$warning\" >&2" else line
                        },
                    )
                }
                run("dpkg-deb", "--root-owner-group", "--build", unpacked.path, deb.path)
                unpacked.deleteRecursively()
            }
        }
    }
}

tasks.withType<JavaExec>().matching { it.name == "run" }.configureEach {
    (findProperty("benchmarkHome") as String?)?.let { systemProperty("user.home", it) }
    (findProperty("benchmarkJfr") as String?)?.let { recording ->
        val delay = findProperty("benchmarkDelaySeconds") ?: "20"
        val duration = findProperty("benchmarkSeconds") ?: "60"
        doFirst { jvmArgs("-XX:StartFlightRecording=delay=${delay}s,duration=${duration}s,filename=$recording,settings=default") }
    }
}

kover {
    reports {
        filters {
            excludes {
                annotatedBy("androidx.compose.runtime.Composable", "androidx.compose.ui.tooling.preview.Preview")
                classes("*ComposableSingletons*")
            }
        }
        verify {
            rule("desktop app lines") {
                minBound(96, CoverageUnit.LINE)
            }
            rule("desktop app branches") {
                minBound(90, CoverageUnit.BRANCH)
            }
        }
    }
}
