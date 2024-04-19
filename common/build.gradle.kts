plugins {
    `java-library`
    `maven-publish`
}

val moduleName = "common"

dependencies {
    compileOnly("dev.arbjerg:lavaplayer:1.5.3")
}

