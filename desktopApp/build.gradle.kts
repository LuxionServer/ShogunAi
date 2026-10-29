import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    alias(libs.plugins.kotlinJvm)
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


    testImplementation(testFixtures(project(":core")))
    testImplementation(libs.kotlin.testJunit)
    testImplementation(compose.uiTest)
    testImplementation(libs.kotlinx.coroutinesTest)
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

            modules("java.net.http", "java.management", "jdk.management")

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
