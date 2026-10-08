// Bundled metadata catalog: recognises games by hash or name. The catalog database is not stored in
// git; it is generated at build time from pinned No-Intro DAT files (see tools/catalog).
import java.net.URI
import java.net.URLEncoder
import java.security.MessageDigest

plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "com.thorium.data.metadata"
    compileSdk = 37

    defaultConfig {
        minSdk = 29
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

/** Downloads the pinned DAT files, checks their SHA-256 and builds catalog.db with the Python tool. */
abstract class BuildCatalog : DefaultTask() {
    @get:InputFile abstract val lockFile: RegularFileProperty
    @get:InputFile abstract val script: RegularFileProperty
    @get:Internal abstract val cacheDir: DirectoryProperty
    @get:OutputDirectory abstract val assetsDir: DirectoryProperty
    @get:Inject abstract val exec: ExecOperations

    @TaskAction
    fun build() {
        val lines = lockFile.get().asFile.readLines().filter { it.isNotBlank() && !it.startsWith("#") }
        val commit = lines.first { it.startsWith("commit ") }.removePrefix("commit ").trim()
        val cache = cacheDir.get().asFile.resolve(commit).also { it.mkdirs() }

        for (line in lines.filterNot { it.startsWith("commit ") }) {
            val hash = line.substringBefore("  ")
            val name = line.substringAfter("  ")
            val file = cache.resolve("$name.dat")
            if (!file.exists() || sha256(file.readBytes()) != hash) {
                val encoded = URLEncoder.encode(name, "UTF-8").replace("+", "%20")
                val url = "https://raw.githubusercontent.com/libretro/libretro-database/$commit/metadat/no-intro/$encoded.dat"
                logger.lifecycle("Downloading $name.dat")
                val bytes = try {
                    URI(url).toURL().openStream().use { it.readBytes() }
                } catch (e: Exception) {
                    throw GradleException("Could not download $url (${e.message}). The catalog needs the DAT files once; they are cached in ${cache}.", e)
                }
                if (sha256(bytes) != hash) throw GradleException("$name.dat does not match the SHA-256 in tools/catalog/dats.lock")
                file.writeBytes(bytes)
            }
        }

        val out = assetsDir.get().asFile.also { it.mkdirs() }
        try {
            exec.exec {
                commandLine("python3", "-I", script.get().asFile.absolutePath, cache.absolutePath, out.resolve("catalog.db").absolutePath)
            }
        } catch (e: Exception) {
            throw GradleException("Building the catalog needs python3 on the PATH (${e.message})", e)
        }
    }

    private fun sha256(bytes: ByteArray) =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
}

val buildCatalog = tasks.register<BuildCatalog>("buildCatalog") {
    lockFile.set(rootProject.layout.projectDirectory.file("tools/catalog/dats.lock"))
    script.set(rootProject.layout.projectDirectory.file("tools/catalog/build_catalog.py"))
    cacheDir.set(rootProject.layout.projectDirectory.dir(".gradle/catalog-dats"))
    assetsDir.set(layout.buildDirectory.dir("generated/catalog"))
}

androidComponents {
    onVariants { variant ->
        variant.sources.assets?.addGeneratedSourceDirectory(buildCatalog, BuildCatalog::assetsDir)
    }
}

dependencies {
    implementation(project(":core:model"))
    implementation(libs.okhttp)
    implementation(libs.kotlinx.coroutines.core)

    testImplementation(project(":core:model"))
    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
}
