// Pure Kotlin module: domain models and the small interfaces ("ports") that data modules share.
plugins {
    alias(libs.plugins.kotlin.jvm)
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    // Flow appears in the ports; nothing else from coroutines leaks into the models.
    api(libs.kotlinx.coroutines.core)
}
