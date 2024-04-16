plugins {
    `java-library`
    `maven-publish`
}

val moduleName = "sedmelluq"
version = "1.0.0"

dependencies {
    compileOnly("com.github.devoxin.lavaplayer:lavaplayer:1.8.0")
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
