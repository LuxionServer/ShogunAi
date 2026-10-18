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
            val rawVersion = (findProperty("appVersion") as String?) ?: "0.0.0"
            // macOS no permite que la versión empiece por 0 (restricción de jpackage / CFBundleVersion).
            // Mapeamos temporalmente "0.x.y" a "1.x.y" para evitar el fallo de compilación en macOS.
            val isMac = System.getProperty("os.name").contains("Mac", ignoreCase = true)
            packageVersion = if (isMac && rawVersion.startsWith("0.")) {
                rawVersion.replaceFirst("0.", "1.")
            } else {
                rawVersion
            }

            macOS {
                iconFile.set(project.file("icons/icon.icns"))
            }
            windows {
                iconFile.set(project.file("icons/icon.ico"))
            }
            linux {
                iconFile.set(project.file("icons/icon.png"))
            }
        }
    }
}
