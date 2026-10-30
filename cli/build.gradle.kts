import kotlinx.kover.gradle.plugin.dsl.CoverageUnit

plugins {
    alias(libs.plugins.kotlinJvm)
    alias(libs.plugins.kover)
    application
}

version = (findProperty("appVersion") as String?) ?: "0.0.0"

val e2eTest: SourceSet = sourceSets.create("e2eTest")

dependencies {
    implementation(project(":core"))
    implementation(libs.kotlinx.serializationJson)

    testImplementation(testFixtures(project(":core")))
    testImplementation(libs.kotlin.testJunit)
    testImplementation(libs.kotlinx.coroutinesTest)

    "e2eTestImplementation"(libs.kotlin.testJunit)
    "e2eTestImplementation"(libs.kotlinx.serializationJson)
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
                minBound(100, CoverageUnit.BRANCH)
            }
        }
    }
}

val e2eTestTask = tasks.register<Test>("e2eTest") {
    description = "Runs the end-to-end tests against the installed shogun distribution."
    group = "verification"
    testClassesDirs = e2eTest.output.classesDirs
    classpath = e2eTest.runtimeClasspath
    dependsOn(tasks.installDist)
    inputs.files(tasks.installDist).withPropertyName("installation")
    maxParallelForks = (Runtime.getRuntime().availableProcessors() / 2).coerceAtLeast(1)
    val installation = layout.buildDirectory.dir("install/shogun")
    val runtimeClasspath = e2eTest.runtimeClasspath
    jvmArgumentProviders.add(
        CommandLineArgumentProvider {
            listOf(
                "-Dshogun.e2e.installation=${installation.get().asFile.absolutePath}",
                "-Dshogun.e2e.classpath=${runtimeClasspath.asPath}",
            )
        },
    )
}

tasks.test {
    dependsOn(e2eTestTask)
}
