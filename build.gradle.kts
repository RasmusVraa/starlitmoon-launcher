import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import java.io.File

plugins {
    kotlin("jvm") version "2.1.10"
    kotlin("plugin.serialization") version "2.1.10"
    id("org.jetbrains.kotlin.plugin.compose") version "2.1.10"
    id("org.jetbrains.compose") version "1.7.3"
}

group = "ru.starlitmoon"
version = "1.1.15"

repositories {
    google()
    mavenCentral()
    maven("https://maven.pkg.jetbrains.space/public/p/compose/dev")
}

dependencies {
    implementation(compose.desktop.currentOs)
    implementation(compose.material3)
    implementation(compose.materialIconsExtended)

    implementation("io.ktor:ktor-client-core:3.1.1")
    implementation("io.ktor:ktor-client-cio:3.1.1")
    implementation("io.ktor:ktor-client-content-negotiation:3.1.1")
    implementation("io.ktor:ktor-serialization-kotlinx-json:3.1.1")
    implementation("io.ktor:ktor-client-logging:3.1.1")

    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.8.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-swing:1.10.1")

    implementation("net.java.dev.jna:jna:5.15.0")
    implementation("net.java.dev.jna:jna-platform:5.15.0")

    // skinview3d WebGL preview (same engine as the site LK)
    val javafxVer = "21.0.5"
    val javafxOs = "win"
    listOf("base", "graphics", "controls", "swing", "web", "media").forEach { name ->
        implementation("org.openjfx:javafx-$name:$javafxVer:$javafxOs")
    }
}

kotlin {
    jvmToolchain(17)
}

val javafxModules =
    "javafx.base,javafx.graphics,javafx.controls,javafx.swing,javafx.web,javafx.media"

fun javafxModulePath(): String =
    configurations.named("runtimeClasspath").get().files
        .filter { it.name.startsWith("javafx-") }
        .joinToString(File.pathSeparator) { it.absolutePath }

compose.desktop {
    application {
        mainClass = "ru.starlitmoon.launcher.MainKt"

        buildTypes.release.proguard {
            isEnabled.set(false)
        }

        nativeDistributions {
            targetFormats(TargetFormat.Exe)
            packageName = "StarlitMoonLauncher"
            packageVersion = "1.1.15"
            description = "StarlitMoon Minecraft Launcher"
            vendor = "StarlitMoon"
            copyright = "StarlitMoon"

            windows {
                menuGroup = "StarlitMoon"
                dirChooser = false
                perUserInstall = true
                console = false
                iconFile.set(project.file("src/main/resources/icon.ico"))
            }
        }
    }
}

afterEvaluate {
    val mp = javafxModulePath()
    tasks.withType<JavaExec>().configureEach {
        jvmArgs("--module-path", mp, "--add-modules", javafxModules)
    }
    // Packaged EXE: dependency jars (incl. JavaFX) are under app/
    compose.desktop.application.jvmArgs.clear()
    compose.desktop.application.jvmArgs.addAll(
        listOf(
            "--module-path", "app",
            "--add-modules", javafxModules,
        ),
    )
}

compose.desktop.application.nativeDistributions.modules(
    "java.instrument",
    "java.net.http",
    "jdk.unsupported",
    "jdk.unsupported.desktop",
    "jdk.jsobject",
    "jdk.xml.dom",
)

