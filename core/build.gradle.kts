plugins {
    alias(libs.plugins.kotlinJvm)
    alias(libs.plugins.kotlinSerialization)
    `java-test-fixtures`
}

dependencies {
    api(libs.kotlinx.coroutinesCore)
    implementation(libs.kotlinx.serializationJson)

    testImplementation(libs.kotlin.testJunit)
    testImplementation(libs.kotlinx.coroutinesTest)
}
