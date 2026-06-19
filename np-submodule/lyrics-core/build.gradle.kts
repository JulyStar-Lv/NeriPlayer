plugins {
    id("build-logic.android.library.common")
}

android {
    namespace = "io.github.camtulip.metadata.lyrics.core"
}

dependencies {
    implementation(libs.kotlinx.serialization.json)
    testImplementation(kotlin("test"))
    testImplementation(libs.junit)
}
