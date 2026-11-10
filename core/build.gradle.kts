plugins { alias(libs.plugins.kotlin.jvm) }

kotlin { jvmToolchain(21) }

dependencies {
    api(libs.okhttp)
    api(libs.gson)
    api(libs.orgjson)
    api(libs.coroutines.core)
    testImplementation(libs.junit)
}

tasks.test { useJUnit() }
