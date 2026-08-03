plugins {
    id("java")
    id("xyz.jpenilla.run-paper") version "3.0.2"
}

group = "net.ucucraft"
version = "1.0.0"

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:26.2.build.87-stable")
    compileOnly(files("libs/countries-1.0.0.jar"))
}

java {
    toolchain.languageVersion = JavaLanguageVersion.of(25)
}

tasks {
    compileJava {
        options.encoding = "UTF-8"
    }

    processResources {
        filesMatching("paper-plugin.yml") {
            expand("version" to project.version)
        }
    }

    runServer {
        minecraftVersion("26.2")
    }
}
