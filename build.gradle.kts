import org.apache.tools.ant.taskdefs.condition.Os
import org.jetbrains.intellij.platform.gradle.IntelliJPlatform
import org.jetbrains.intellij.platform.gradle.IntelliJPlatformType
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import java.io.FileInputStream
import java.io.InputStreamReader
import java.util.*

plugins {
    java
    kotlin("jvm") version "2.1.10"
    id("org.jetbrains.intellij.platform") version "2.4.0"
}

group = "me.rerere"
version = "2.0.0"

repositories {
    mavenCentral()
    intellijPlatform {
        defaultRepositories()
    }
}

dependencies {
    implementation("com.github.weisj:jsvg:1.3.0")

    intellijPlatform {
        intellijIdeaUltimate("251-EAP-SNAPSHOT", useInstaller = false)

        bundledPlugin("com.intellij.css")
        bundledPlugin("JavaScript")
    }
}

// Configure Gradle IntelliJ Plugin
// Read more: https://plugins.jetbrains.com/docs/intellij/tools-intellij-platform-gradle-plugin.html
intellijPlatform {
    pluginConfiguration {
        ideaVersion {
            sinceBuild = "251"
            untilBuild = "251.*"
        }
    }
    pluginVerification {
        ides {
            recommended()
        }
    }
}

// include the generated source directory
sourceSets["main"].java.srcDirs("src/main/gen")

tasks {
    // Set the JVM compatibility versions
    withType<JavaCompile> {
        sourceCompatibility = "21"
        targetCompatibility = "21"
    }
    withType<KotlinCompile> {
        compilerOptions.jvmTarget = JvmTarget.JVM_21

        dependsOn("processJavaScript")
    }

    signPlugin {
        certificateChain = file("sign/chain.crt").readText()
        privateKey = System.getenv(file("sign/private.pem").readText())
        password = System.getenv("PRIVATE_KEY_PASSWORD")
    }

    publishPlugin {
        token = System.getenv("PUBLISH_TOKEN")
    }

    register("processJavaScript", Exec::class) {
        inputs.dir("src/main/javascript/src")
        inputs.file("src/main/javascript/package.json")
        inputs.file("src/main/javascript/babel.config.json")
        inputs.file("src/main/javascript/tsconfig.json")
        inputs.file("src/main/javascript/rollup.config.js")
        outputs.dir("${project.projectDir}/unojs")
        outputs.cacheIf { true }

        doFirst {
            file("${project.projectDir}/unojs")
                .takeIf { it.exists() }
                ?.let {
                    it.listFiles()?.forEach { file ->
                        println("del $file")
                        file.delete()
                    }
                }

            workingDir("${project.projectDir}/src/main/javascript")
            val npm = if (Os.isFamily(Os.FAMILY_WINDOWS)) "npm.cmd" else "npm"
            commandLine(npm, "run", "build")
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
}

val runLocalIde by intellijPlatformTesting.runIde.registering {
    val idePath = getLocalProperty("IDE_PATH") as String?
    idePath?.let {
        // localPath = file(idePath)
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