rootProject.name = "youtube-source"

include("v2")
include("common")
include("plugin")

enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

dependencyResolutionManagement {
    versionCatalogs {
        create("libs") {
            version("lavaplayer-v1", "1.5.3")
            version("lavaplayer-v2", "2.1.1")

            library("lavaplayer-v1", "dev.arbjerg", "lavaplayer").versionRef("lavaplayer-v1")
            library("lavaplayer-v2", "dev.arbjerg", "lavaplayer").versionRef("lavaplayer-v2")

            version("lavalink", "4.0.7")
            library("lavalink-server", "dev.arbjerg.lavalink", "Lavalink-Server").versionRef("lavalink")
            library("lavaplayer-ext-youtube-rotator", "dev.arbjerg", "lavaplayer-ext-youtube-rotator").versionRef("lavaplayer-v1")

            library("rhino-engine", "org.mozilla", "rhino-engine").version("1.7.15")
            library("nanojson", "com.grack", "nanojson").version("1.7")
            library("slf4j", "org.slf4j", "slf4j-api").version("1.7.25")
            library("annotations", "org.jetbrains", "annotations").version("24.1.0")

            plugin("lavalink-gradle-plugin", "dev.arbjerg.lavalink.gradle-plugin").version("1.0.15")

            val mavenPublishPlugin = version("maven-publish-plugin", "0.25.3")
            plugin("maven-publish", "com.vanniktech.maven.publish").versionRef(mavenPublishPlugin)
            plugin("maven-publish-base", "com.vanniktech.maven.publish.base").versionRef(mavenPublishPlugin)
        }
    }
}
