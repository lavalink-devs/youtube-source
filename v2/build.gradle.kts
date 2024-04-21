import com.vanniktech.maven.publish.JavaLibrary
import com.vanniktech.maven.publish.JavadocJar

plugins {
    `java-library`
    alias(libs.plugins.maven.publish.base)
}

base {
    archivesName = "youtube-v2"
}

dependencies {
    api(projects.common)
    compileOnly(libs.lavaplayer.v2)
    implementation(libs.rhino.engine)
    implementation(libs.nanojson)
    compileOnly(libs.slf4j)
    compileOnly(libs.annotations)
}

mavenPublishing {
    configure(JavaLibrary(JavadocJar.Javadoc()))
}
