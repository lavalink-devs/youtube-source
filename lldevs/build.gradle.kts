plugins {
    `java-library`
    `maven-publish`
}

val moduleName = "lldevs"
version = "1.0.0"

dependencies {
    compileOnly("dev.arbjerg:lavaplayer:2.1.1")
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
