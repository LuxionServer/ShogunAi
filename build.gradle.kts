import org.jetbrains.kotlin.gradle.dsl.KotlinJvmProjectExtension

plugins {
    alias(libs.plugins.composeMultiplatform) apply false
    alias(libs.plugins.composeCompiler) apply false
    alias(libs.plugins.kotlinJvm) apply false
    alias(libs.plugins.kotlinSerialization) apply false
}

val testSourceSets = setOf("test", "testFixtures")

subprojects {
    plugins.withId("org.jetbrains.kotlin.jvm") {
        extensions.configure<KotlinJvmProjectExtension> {
            compilerOptions.allWarningsAsErrors.set(true)
            sourceSets.matching { it.name in testSourceSets }.configureEach {
                languageSettings.optIn("kotlinx.coroutines.ExperimentalCoroutinesApi")
            }
        }
    }
}
