import com.github.jengelman.gradle.plugins.shadow.transformers.Log4j2PluginsCacheFileTransformer

description = "Proxy pass allows developers to MITM a vanilla client and server without modifying them."

plugins {
    id("java")
    id("application")
    alias(libs.plugins.shadow)
    id("org.jetbrains.kotlin.jvm") version "1.7.20"
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(17))
    }
}

repositories {
    mavenLocal()
    mavenCentral()
    maven("https://repo.opencollab.dev/maven-snapshots")
    maven("https://repo.opencollab.dev/maven-releases")
}

dependencies {
    compileOnly(libs.lombok)
    annotationProcessor(libs.lombok)
    compileOnly(libs.jsr305)
    implementation(libs.bedrock.connection)
    implementation(libs.jackson.databind)
    implementation(libs.jackson.dataformat.yaml)
    implementation(libs.common)
    implementation(libs.jansi)
    implementation(libs.jline.reader)
    implementation(libs.kotlin.stdlib)
    implementation(libs.ktor.client.content.negotiation.jvm)
    implementation(libs.ktor.client.okhttp.jvm)
    implementation(libs.ktor.serialization.jackson.jvm)
    implementation(libs.bcpkix.jdk15on)
    implementation(libs.jose4j)
}

application {
    mainClass.set("org.cloudburstmc.proxypass.ProxyPass")
}

tasks.shadowJar {
    archiveClassifier.set("")
    archiveVersion.set("")
    transform(Log4j2PluginsCacheFileTransformer())
}

tasks {
    val shadowJar by existing(com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar::class)
    val distZip by existing(Zip::class) // adjust with the actual type of your distZip task
    val distTar by existing(Tar::class) // adjust with the actual type of your distTar task
    val startScripts by existing(CreateStartScripts::class) // adjust with the actual type of your startScripts task

    distZip.configure {
        dependsOn(shadowJar)
    }

    distTar.configure {
        dependsOn(shadowJar)
    }

    startScripts.configure {
        dependsOn(shadowJar)
    }
}

tasks {
    val jar by existing(Jar::class)
    val startShadowScripts by existing(CreateStartScripts::class) // adjust with the actual type of your startShadowScripts task

    startShadowScripts.configure {
        dependsOn(jar)
    }
}

tasks.named<JavaExec>("run") {
    workingDir = projectDir.resolve("run")
    workingDir.mkdir()
}

java.sourceSets["main"].java {
    srcDir("src/main/kotlin")
    srcDir("src/main/java")
}