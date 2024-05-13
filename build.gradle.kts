import org.apache.tools.ant.taskdefs.condition.Os
import java.io.FileInputStream
import java.io.InputStreamReader
import java.util.*

plugins {
    id("java")
    id("org.jetbrains.kotlin.jvm") version "1.9.20"
    id("org.jetbrains.intellij") version "1.17.3"
}

group = "me.rerere"
version = "1.4.8"

repositories {
    mavenCentral()
}

dependencies {
    implementation("com.github.weisj:jsvg:1.3.0")
}

// Configure Gradle IntelliJ Plugin
// Read more: https://plugins.jetbrains.com/docs/intellij/tools-gradle-intellij-plugin.html
intellij {
    version.set("2024.1")
    type.set("IU") // Target IDE Platform

    plugins.set(listOf("JavaScript"))
}

fun properties(key: String) = project.findProperty(key).toString()

// include the generated source directory
sourceSets["main"].java.srcDirs("src/main/gen")

tasks {
    // Set the JVM compatibility versions
    withType<JavaCompile> {
        sourceCompatibility = "17"
        targetCompatibility = "17"
    }
    withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
        kotlinOptions.jvmTarget = "17"

        dependsOn("processJavaScript")
    }

    patchPluginXml {
        sinceBuild.set("233")
        untilBuild.set("242.*")
    }

    signPlugin {
        certificateChain.set(file("sign/chain.crt").readText())
        privateKey.set(System.getenv(file("sign/private.pem").readText()))
        password.set(System.getenv("PRIVATE_KEY_PASSWORD"))
    }

    publishPlugin {
        token.set(System.getenv("PUBLISH_TOKEN"))
    }

    task("processJavaScript") {
        inputs.dir("src/main/javascript/src")
        inputs.file("src/main/javascript/package.json")
        outputs.dir("${project.projectDir}/unojs")
        outputs.cacheIf { true }

        doFirst {
            exec {
                workingDir("${project.projectDir}/src/main/javascript")
                val npm = if (Os.isFamily(Os.FAMILY_WINDOWS)) "npm.cmd" else "npm"
                commandLine(npm, "run", "build")
            }
        }
    }

    prepareSandbox {
        inputs.dir("unojs")
        doLast {
            copy {
                from("${project.projectDir}/unojs")
                into("${destinationDir.path}/${project.name}/unojs")
            }
        }
    }

    runIde {
        val idePath = getLocalProperty("IDE_PATH") as String?
        idePath?.let {
            ideDir.set(file(idePath))
        }
    }
}

fun getLocalProperty(key: String, file: String = "local.properties"): Any? {
    val properties = Properties()
    val localProperties = File(file)
    if (localProperties.isFile) {
        InputStreamReader(FileInputStream(localProperties), Charsets.UTF_8).use { reader ->
            properties.load(reader)
        }
    } else return null

    return properties.getProperty(key)
}