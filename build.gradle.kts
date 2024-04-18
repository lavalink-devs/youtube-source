plugins {
    java
    `maven-publish`
}

group = "dev.lavalink"

allprojects {
    group = rootProject.group

    repositories {
        mavenLocal()
        mavenCentral()
        maven(url = "https://maven.lavalink.dev/releases")
        maven(url = "https://m2.dv8tion.net/releases")
        maven(url = "https://jitpack.io")
    }

    apply(plugin = "java")
    apply(plugin = "maven-publish")

    java {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }

    dependencies {
        implementation("org.mozilla:rhino-engine:1.7.14")
        implementation("com.grack:nanojson:1.7")
        compileOnly("org.slf4j:slf4j-api:1.7.25")
    }
}