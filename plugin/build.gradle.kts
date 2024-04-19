plugins {
    `java-library`
    `maven-publish`
}

val moduleName = "plugin"

dependencies {
    compileOnly("dev.arbjerg.lavalink:plugin-api:3.7.11")
    compileOnly("dev.arbjerg:lavaplayer-ext-youtube-rotator:1.5.3")
    compileOnly(project(":common"))
    compileOnly(project(":lldevs"))
}
