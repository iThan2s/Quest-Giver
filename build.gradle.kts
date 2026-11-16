plugins {
    java
    id("com.gradleup.shadow") version "9.6.1"
}

group = "io.github.ithan2s"
// The version comes from gradle.properties; CI overrides it with -Pversion=<tag>.

java {
    toolchain {
        // Paper 26.3 requires Java 25.
        languageVersion = JavaLanguageVersion.of(25)
    }
}

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
    maven("https://jitpack.io") {
        content {
            includeGroup("com.github.MilkBowl")
            includeGroup("com.github.decentsoftware-eu")
        }
    }
}

val paperApi = "io.papermc.paper:paper-api:26.3.build.253-stable"

dependencies {
    compileOnly(paperApi)
    // Paper 26.3 bundles org.xerial:sqlite-jdbc 3.49.1.0 at runtime, so nothing is shaded for storage.
    compileOnly("com.github.MilkBowl:VaultAPI:1.7.1") {
        isTransitive = false
    }
    compileOnly("com.github.decentsoftware-eu:decentholograms:2.10.2") {
        isTransitive = false
    }

    testImplementation(platform("org.junit:junit-bom:6.1.3"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks {
    withType<JavaCompile>().configureEach {
        options.encoding = "UTF-8"
        options.release = 25
        options.compilerArgs.addAll(listOf("-Xlint:deprecation", "-Xlint:unchecked"))
    }

    processResources {
        val props = mapOf("version" to project.version)
        inputs.properties(props)
        filesMatching("plugin.yml") {
            expand(props)
        }
    }

    test {
        useJUnitPlatform()
    }

    jar {
        // The shaded jar is the only deliverable.
        enabled = false
    }

    shadowJar {
        archiveBaseName = "QuestGiver"
        archiveClassifier = ""
    }

    build {
        dependsOn(shadowJar)
    }
}
