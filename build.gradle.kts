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

    // Тесты хранилища истории идут без сервера: нужен только JUnit и драйвер SQLite.
    // В рантайме драйвер даёт сам сервер, поэтому в JAR плагина он не попадает.
    testImplementation(platform("org.junit:junit-bom:5.11.4"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    testRuntimeOnly("org.xerial:sqlite-jdbc:3.47.1.0")
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

    compileTestJava {
        options.encoding = "UTF-8"
    }

    test {
        useJUnitPlatform()
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
