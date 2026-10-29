import kotlinx.kover.gradle.plugin.dsl.CoverageUnit

plugins {
    alias(libs.plugins.kotlinJvm)
    alias(libs.plugins.kover)
    application
}

version = (findProperty("appVersion") as String?) ?: "0.0.0"

dependencies {
    implementation(project(":core"))
    implementation(libs.kotlinx.serializationJson)

    testImplementation(testFixtures(project(":core")))
    testImplementation(libs.kotlin.testJunit)
    testImplementation(libs.kotlinx.coroutinesTest)
}

application {
    mainClass = "app.luxion.shogunai.cli.MainKt"
    applicationName = "shogun"
}

kover {
    reports {
        filters {
            excludes {
                classes("app.luxion.shogunai.cli.MainKt")
            }
        }
        verify {
            rule("cli lines") {
                minBound(100, CoverageUnit.LINE)
            }
            rule("cli branches") {
                minBound(83, CoverageUnit.BRANCH)
            }
        }
    }
}
