plugins {
    alias(libs.plugins.kotlinJvm)
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
