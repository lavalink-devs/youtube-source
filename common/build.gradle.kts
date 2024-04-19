plugins {
    `java-library`
    `maven-publish`
}

val moduleName = "common"

dependencies {
    compileOnly("dev.arbjerg:lavaplayer:1.5.3")
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
