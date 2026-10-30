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
                minBound(83, CoverageUnit.BRANCH)
            }
        }
    }
}

val fakeRunnerLauncherTask = tasks.register<Exec>("fakeRunnerLauncher") {
    description = "Compiles the executable that stands in for herdr.exe in the end-to-end tests on Windows."
    group = "verification"
    val windows = System.getProperty("os.name").startsWith("Windows", ignoreCase = true)
    val source = layout.projectDirectory.file("src/e2eTest/windows/FakeRunnerLauncher.cs")
    val launcher = layout.buildDirectory.file("fake-runner-launcher/FakeRunnerLauncher.exe")
    onlyIf { windows }
    inputs.file(source).withPropertyName("source")
    outputs.file(launcher).withPropertyName("launcher")
    executable = providers.environmentVariable("WINDIR")
        .map { "$it\\Microsoft.NET\\Framework64\\v4.0.30319\\csc.exe" }
        .getOrElse("csc")
    argumentProviders.add(
        CommandLineArgumentProvider {
            listOf("-nologo", "-out:${launcher.get().asFile.absolutePath}", source.asFile.absolutePath)
        },
    )
    doFirst { launcher.get().asFile.parentFile.mkdirs() }
}

val e2eTestTask = tasks.register<Test>("e2eTest") {
    description = "Runs the end-to-end tests against the installed shogun distribution."
    group = "verification"
    testClassesDirs = e2eTest.output.classesDirs
    classpath = e2eTest.runtimeClasspath
    dependsOn(tasks.installDist)
    inputs.files(tasks.installDist).withPropertyName("installation")
    dependsOn(fakeRunnerLauncherTask)
    inputs.files(fakeRunnerLauncherTask).withPropertyName("runnerLauncher")
    maxParallelForks = (Runtime.getRuntime().availableProcessors() / 2).coerceAtLeast(1)
    val installation = layout.buildDirectory.dir("install/shogun")
    val runtimeClasspath = e2eTest.runtimeClasspath
    val runnerLauncher = layout.buildDirectory.file("fake-runner-launcher/FakeRunnerLauncher.exe")
    jvmArgumentProviders.add(
        CommandLineArgumentProvider {
            listOf(
                "-Dshogun.e2e.installation=${installation.get().asFile.absolutePath}",
                "-Dshogun.e2e.classpath=${runtimeClasspath.asPath}",
                "-Dshogun.e2e.runnerLauncher=${runnerLauncher.get().asFile.absolutePath}",
            )
        },
    )
}

tasks.test {
    dependsOn(e2eTestTask)
}
