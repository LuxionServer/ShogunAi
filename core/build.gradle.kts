import kotlinx.kover.gradle.plugin.dsl.CoverageUnit

plugins {
    alias(libs.plugins.kotlinJvm)
    alias(libs.plugins.kover)
    alias(libs.plugins.kotlinSerialization)
    `java-test-fixtures`
}

dependencies {
    api(libs.kotlinx.coroutinesCore)
    implementation(libs.kotlinx.serializationJson)

    testImplementation(libs.kotlin.testJunit)
    testImplementation(libs.kotlinx.coroutinesTest)
}

val generatedBuildInfoDir = layout.buildDirectory.dir("generated/buildInfo")

val generateBuildInfo = tasks.register("generateBuildInfo") {
    val appVersion = (findProperty("appVersion") as String?) ?: "0.0.0"
    val outputDir = generatedBuildInfoDir
    inputs.property("appVersion", appVersion)
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

kover {
    reports {
        verify {
            rule("core lines") {
                minBound(100, CoverageUnit.LINE)
            }
            rule("core branches") {
                minBound(79, CoverageUnit.BRANCH)
            }
        }
    }
}

tasks.named<Test>("test") {
    inputs.files(fileTree(rootDir) { include("cli/src/main/**/*.kt", "desktopApp/src/main/**/*.kt") })
        .withPathSensitivity(PathSensitivity.RELATIVE)
        .withPropertyName("otherModulesSources")
}
