import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    alias(libs.plugins.kotlinJvm)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.kotlinSerialization)
}

dependencies {
    implementation(projects.shared)

    implementation(compose.desktop.currentOs)
    implementation(compose.runtime)
    implementation(compose.foundation)
    implementation(compose.material3)
    implementation(compose.ui)
    implementation(libs.compose.materialIconsCore)
    implementation(libs.compose.materialIconsExtended)
    implementation(libs.kotlinx.coroutinesSwing)
    implementation(libs.kotlinx.coroutinesCore)
    implementation(libs.kotlinx.serializationJson)
    implementation(libs.androidx.lifecycle.viewmodelCompose)
    implementation(libs.androidx.lifecycle.runtimeCompose)

    implementation(libs.compose.uiToolingPreview)

    testImplementation(libs.kotlin.testJunit)
    testImplementation(libs.kotlinx.coroutinesTest)
}

compose.desktop {
    application {
        mainClass = "app.luxion.shogunai.MainKt"

        nativeDistributions {
            targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb)
            packageName = "ShogunAi"
            packageVersion = (findProperty("appVersion") as String?) ?: "0.0.0"

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

val generatedBuildInfoDir = layout.buildDirectory.dir("generated/buildInfo")

val generateBuildInfo = tasks.register("generateBuildInfo") {
    val appVersion = (findProperty("appVersion") as String?) ?: "0.0.0"
    val outputDir = generatedBuildInfoDir
    outputs.dir(outputDir)
    doLast {
        val packageDir = outputDir.get().asFile.resolve("app/luxion/shogunai")
        packageDir.mkdirs()
        packageDir.resolve("BuildInfo.kt").writeText(
            """
            package app.luxion.shogunai

            object BuildInfo {
                const val VERSION = "$appVersion"
            }

            """.trimIndent(),
        )
    }
}

sourceSets {
    main {
        kotlin.srcDir(generatedBuildInfoDir)
    }
}

tasks.named("compileKotlin") {
    dependsOn(generateBuildInfo)
}