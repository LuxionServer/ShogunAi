import kotlinx.kover.gradle.plugin.dsl.CoverageUnit
import org.jetbrains.compose.desktop.application.dsl.TargetFormat

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
    implementation(libs.androidx.lifecycle.viewmodelCompose)
    implementation(libs.androidx.lifecycle.runtimeCompose)
    implementation(libs.filekit.dialogs)

    testImplementation(testFixtures(project(":core")))
    testImplementation(libs.kotlin.testJunit)
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
                minBound(83, CoverageUnit.LINE)
            }
            rule("desktop app branches") {
                minBound(66, CoverageUnit.BRANCH)
            }
        }
    }
}
