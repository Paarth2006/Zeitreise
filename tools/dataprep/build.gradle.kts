plugins {
    alias(libs.plugins.kotlinJvm)
    alias(libs.plugins.kotlinSerialization)
    application
}

application {
    mainClass.set("com.paarth.zeitreise.dataprep.MainKt")
}

dependencies {
    implementation(libs.kotlinx.serialization.json)
}
