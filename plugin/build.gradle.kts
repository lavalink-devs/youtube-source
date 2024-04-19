plugins {
    `java-library`
    `maven-publish`
}

val moduleName = "plugin"

dependencies {
    implementation(project(":common"))
    implementation(project(":lldevs"))
    compileOnly("dev.arbjerg.lavalink:plugin-api:3.7.11")
    compileOnly("dev.arbjerg.lavalink:Lavalink-Server:3.7.11")
    compileOnly("dev.arbjerg:lavaplayer-ext-youtube-rotator:1.5.3")
}

java {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
}

val sourcesJar by tasks.registering(Jar::class) {
    archiveClassifier.set("sources")
    from(sourceSets["main"].allSource)
}

publishing {
    publications {
        create<MavenPublication>("mavenJava") {
            from(components["java"])
            artifactId = moduleName
            artifact(sourcesJar)
        }
    }
}
