import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.compose)
}

kotlin { jvmToolchain(21) }

dependencies {
    implementation(project(":core"))
    implementation(compose.desktop.currentOs)
    implementation(libs.coroutines.swing)
    implementation(libs.vlcj)
    implementation(libs.jna.platform)
    testImplementation(libs.junit)
}

compose.desktop {
    application {
        mainClass = "com.ipvio.stream.desktop.MainKt"
        nativeDistributions {
            targetFormats(TargetFormat.AppImage)
            packageName = "IPVIO Stream"
            packageVersion = "0.1.0"
            description = "IPTV player for your own playlists"
            vendor = "IPVIO Stream Desktop contributors"
        }
    }
}
