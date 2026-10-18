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
    implementation(libs.compose.materialIconsCore)
    implementation(libs.compose.materialIconsExtended)
    implementation(libs.kotlinx.coroutinesSwing)
    implementation(libs.kotlinx.coroutinesCore)
    implementation(libs.androidx.lifecycle.viewmodelCompose)
    implementation(libs.androidx.lifecycle.runtimeCompose)

    implementation(libs.compose.uiToolingPreview)

    testImplementation(testFixtures(project(":core")))
    testImplementation(libs.kotlin.testJunit)
    testImplementation(compose.uiTest)
    testImplementation(libs.kotlinx.coroutinesTest)
}

compose.desktop {
    application {
        mainClass = "app.luxion.shogunai.MainKt"

        nativeDistributions {
            targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb)
            packageName = "ShogunAi"
            vendor = "LuxionServer"
            packageVersion = (findProperty("appVersion") as String?) ?: "0.0.0"

            macOS {
                iconFile.set(project.file("icons/icon.icns"))
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
