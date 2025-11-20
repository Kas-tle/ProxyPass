import com.github.jengelman.gradle.plugins.shadow.transformers.Log4j2PluginsCacheFileTransformer
import groovy.util.Node
import org.openjfx.gradle.JavaFXPlatform

description = "Proxy pass allows developers to MITM a vanilla client and server without modifying them."

plugins {
    id("eclipse")
    id("java")
    id("application")
    alias(libs.plugins.shadow)
    alias(libs.plugins.javafxplugin)
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(25))
    }
}

javafx {
    version = "25.0.1"
    modules("javafx.controls", "javafx.fxml")
}

repositories {
    //mavenLocal()
    mavenCentral()
    maven("https://repo.opencollab.dev/maven-snapshots")
    maven("https://repo.opencollab.dev/maven-releases")
}

dependencies {
    compileOnly(libs.lombok)
    annotationProcessor(libs.lombok)
    implementation(libs.bedrock.codec)
    implementation(libs.bedrock.common)
    implementation(libs.bedrock.connection)
    implementation(libs.jackson.dataformat.yaml)
    implementation(libs.log4j)
    implementation(libs.minecraftauth)
    implementation(libs.richtextfx)
    implementation(libs.atlantafx)
    implementation(libs.checker.qual)
}

application {
    mainClass.set("org.cloudburstmc.proxypass.ProxyPass")
}

tasks.shadowJar {
    archiveClassifier.set("")
    archiveVersion.set("")
    transform(Log4j2PluginsCacheFileTransformer())
    filesMatching("META-INF/org/apache/logging/log4j/core/config/plugins/Log4j2Plugins.dat") {
        duplicatesStrategy = DuplicatesStrategy.INCLUDE
    }
}

tasks.named<JavaExec>("run") {
    workingDir = projectDir.resolve("run")
    workingDir.mkdir()
}

listOf("distZip", "distTar", "startScripts").forEach { taskName ->
    tasks.named(taskName) {
        dependsOn("shadowJar")
    }
}

tasks.named("startShadowScripts") {
    dependsOn("jar")
}