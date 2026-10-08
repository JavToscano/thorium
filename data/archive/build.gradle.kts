// ZIP and 7z extraction with protection against hostile archives. Pure Kotlin / Java.
plugins {
    alias(libs.plugins.kotlin.jvm)
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    // Apache-2.0. Its 7z support needs xz (0BSD) for LZMA.
    implementation(libs.commons.compress)
    implementation(libs.xz)

    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
}

tasks.test {
    useJUnitPlatform()
}
