plugins {
    // Позволяет toolchain скачать JDK 21, если на машине его нет.
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

rootProject.name = "LagLens"
