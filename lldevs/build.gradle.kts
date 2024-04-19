plugins {
    `java-library`
    `maven-publish`
}

val moduleName = "lldevs"

dependencies {
    compileOnly(project(":common"))
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
