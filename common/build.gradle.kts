import com.vanniktech.maven.publish.JavaLibrary
import com.vanniktech.maven.publish.JavadocJar
import org.apache.tools.ant.filters.ReplaceTokens

plugins {
    `java-library`
    alias(libs.plugins.maven.publish.base)
}

base {
    archivesName = "youtube-common"
}

dependencies {
    compileOnly(libs.lavaplayer.v1)

    implementation(libs.rhino.engine)
    implementation(libs.nanojson)
    compileOnly(libs.slf4j)
    compileOnly(libs.annotations)

    testImplementation(libs.lavaplayer.v1)
    testImplementation("org.apache.logging.log4j:log4j-core:2.19.0")
    testImplementation("org.apache.logging.log4j:log4j-slf4j2-impl:2.19.0")
}

mavenPublishing {
    configure(JavaLibrary(JavadocJar.Javadoc()))
}

tasks {
    processResources {
        filter<ReplaceTokens>(
            "tokens" to mapOf(
                "version" to project.version
            )
        )
    }
}
