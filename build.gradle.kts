plugins {
    java
}

group = "io.github.mrbuggi"
version = property("pluginVersion") as String

repositories {
    mavenCentral()
    // Репозиторий PaperMC — отсюда тянется paper-api.
    maven("https://repo.papermc.io/repository/maven-public/")
}

dependencies {
    // compileOnly, а НЕ implementation: API предоставляет сам сервер в рантайме.
    // Если написать implementation — соберётся, но API попытается попасть внутрь JAR,
    // а на некоторых конфигурациях это приводит к конфликтам классов.
    compileOnly("io.papermc.paper:paper-api:${property("paperApiVersion")}")
}

java {
    // Единый источник версии Java. Toolchain сам скачает JDK 21, если его нет.
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

tasks {
    compileJava {
        // Комментарии и строки отчёта на русском — без UTF-8 в консоли будут «кракозябры».
        options.encoding = "UTF-8"
    }

    processResources {
        // Подставляем версию плагина из Gradle в plugin.yml — чтобы не дублировать её вручную.
        val props = mapOf("version" to project.version)
        inputs.properties(props)
        filesMatching("plugin.yml") {
            expand(props)
        }
    }
}
