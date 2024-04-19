plugins {
    `java-library`
    `maven-publish`
}

val moduleName = "lldevs"

dependencies {
    compileOnly(project(":common"))
    compileOnly("dev.arbjerg:lavaplayer:2.1.1")
}
