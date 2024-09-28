import org.ajoberstar.grgit.Grgit

plugins {
    java
    id("org.ajoberstar.grgit") version "5.2.0"
    alias(libs.plugins.maven.publish.base) apply false
}

val (gitVersion, release) = versionFromGit()
logger.lifecycle("Version: $gitVersion (release: $release)")

allprojects {
    group = "dev.lavalink.youtube"
    // The plugin project is the only one that should not have a snapshot version since lavalink expects the jar name to be specific
    version = if (project.name == "plugin") {
        gitVersion.removeSuffix("-SNAPSHOT")
    } else {
        gitVersion
    }


    repositories {
        mavenLocal()
        mavenCentral()
        maven(url = "https://maven.lavalink.dev/releases")
        maven(url = "https://jitpack.io")
    }
}

subprojects {
    apply<JavaPlugin>()
    apply<MavenPublishPlugin>()

    configure<JavaPluginExtension> {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }

     configure<PublishingExtension> {
        if (findProperty("MAVEN_PASSWORD") != null && findProperty("MAVEN_USERNAME") != null) {
            repositories {
                val snapshots = "https://maven.kikkia.dev/snapshots"
                val releases = "https://maven.kikkia.dev/releases"

                maven(if (release) releases else snapshots) {
                    credentials {
                        password = findProperty("MAVEN_PASSWORD") as String?
                        username = findProperty("MAVEN_USERNAME") as String?
                    }
                }
            }
        } else {
            logger.lifecycle("Not publishing to maven.lavalink.dev because credentials are not set")
        }
    }
}

@SuppressWarnings("GrMethodMayBeStatic")
fun versionFromGit(): Pair<String, Boolean> {
    Grgit.open(mapOf("currentDir" to project.rootDir)).use { git ->
        val headTag = git.tag
            .list()
            .find { it.commit.id == git.head().id }

        val clean = git.status().isClean || System.getenv("CI") != null
        if (!clean) {
            logger.lifecycle("Git state is dirty, version is a snapshot.")
        }

        return if (headTag != null && clean) headTag.name to true else "${git.head().id}-SNAPSHOT" to false
    }
}