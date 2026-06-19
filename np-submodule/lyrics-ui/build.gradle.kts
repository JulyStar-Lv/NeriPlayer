plugins {
    id("build-logic.android.library.common")
    id("build-logic.android.compose")
}

android {
    namespace = "io.github.camtulip.metadata.lyrics.ui"
}

dependencies {
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.material3)
    implementation(libs.androidx.foundation)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.androidx.animation)
    implementation(libs.androidx.ui.graphics)

    implementation(project(":lyrics-core"))

    testImplementation(libs.junit)
}
