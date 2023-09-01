import org.apache.tools.ant.taskdefs.condition.Os

plugins {
    id("java")
    id("org.jetbrains.kotlin.jvm") version "1.9.0"
    id("org.jetbrains.intellij") version "1.15.0"
}

group = "me.rerere"
version = "1.2.3"

repositories {
    mavenCentral()
}

dependencies {
    implementation("com.github.weisj:jsvg:1.0.0")
}

// Configure Gradle IntelliJ Plugin
// Read more: https://plugins.jetbrains.com/docs/intellij/tools-gradle-intellij-plugin.html
intellij {
    version.set("2023.1.3")
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
        sinceBuild.set("231")
        untilBuild.set("232.*")
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
                val npm = if(Os.isFamily(Os.FAMILY_WINDOWS)) "npm.cmd" else "npm"
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
        // ideDir.set(file("/Users/re/Library/Application Support/JetBrains/Toolbox/apps/WebStorm/ch-0/231.9161.29/WebStorm.app/Contents"))
    }
}
