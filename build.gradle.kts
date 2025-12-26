import org.gradle.api.tasks.testing.logging.TestExceptionFormat
import org.jreleaser.model.Active
import org.jreleaser.model.Signing.Mode
import java.nio.file.Files
import java.nio.file.Paths
import kotlin.io.path.absolutePathString

plugins {
    `java-library`
    application
    `maven-publish`
    jacoco
    id("org.jreleaser") version "1.21.0"
}

enum class OS {
    MAC, WINDOWS, LINUX
}

val currentOS = when {
    System.getProperty("os.name").lowercase().contains("mac") -> OS.MAC
    System.getProperty("os.name").lowercase().contains("windows") -> OS.WINDOWS
    else -> OS.LINUX
}

group = "tanin.singleinstancedeeplink"
version = "1.0"
val appName = "Single Instance with Deep Link test app"

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
    withSourcesJar()
}

tasks.jacocoTestReport {
    dependsOn(tasks.test) // tests are required to run before generating the report

    reports {
        xml.required = true
        csv.required = false
        html.outputLocation = layout.buildDirectory.dir("jacocoHtml")
    }
}

repositories {
    mavenCentral()
}

dependencies {
    testImplementation("org.junit.jupiter:junit-jupiter:5.7.1")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.named<Test>("test") {
    useJUnitPlatform()

    maxHeapSize = "1G"

    testLogging {
        events("started", "passed", "skipped", "failed")
        showStandardStreams = true
        showStackTraces = true
        showExceptions = true
        showCauses = true
        exceptionFormat = TestExceptionFormat.FULL
    }

}

var mainClassName = "tanin.singleinstancedeeplink.Main"

application {
    mainClass.set(mainClassName)
}


publishing {
    publications {
        create<MavenPublication>("maven") {
            groupId = "io.github.tanin47"
            artifactId = "single-instance-deep-link"
            version = project.version.toString()
            artifact(tasks["sourcesJar"])

            pom {
                name.set("Single instance with deep link")
                description.set("Enforcing a single instance with deep link for Java app")
                url.set("https://github.com/tanin47/single-instance-deep-link")
                inceptionYear.set("2025")
                licenses {
                    license {
                        name.set("MIT")
                        url.set("https://spdx.org/licenses/MIT.html")
                    }
                }
                developers {
                    developer {
                        id.set("tanin47")
                        name.set("Tanin Na Nakorn")
                    }
                }
                scm {
                    connection.set("scm:git:https://github.com/tanin47/single-instance-deep-link.git")
                    developerConnection.set("scm:git:ssh://github.com/tanin47/single-instance-deep-link.git")
                    url.set("http://github.com/tanin47/single-instance-deep-link")
                }
            }
        }
    }

    repositories {
        maven {
            url = uri(layout.buildDirectory.dir("staging-deploy"))
        }
    }
}

jreleaser {
    signing {
        active = Active.ALWAYS
        armored = true
        mode = if (System.getenv("CI") != null) Mode.MEMORY else Mode.COMMAND
        command {
            executable = "/opt/homebrew/bin/gpg"
        }
    }
    deploy {
        maven {
            mavenCentral {
                create("sonatype") {
                    setActive("ALWAYS")
                    url = "https://central.sonatype.com/api/v1/publisher"
                    stagingRepository("build/staging-deploy")
                }
            }
        }
    }
}


private fun runCmd(currentDir: File, env: Map<String, String>, vararg args: String): String {
    println("Executing command: ${args.joinToString(" ")}")

    val output = StringBuilder()
    val builder = ProcessBuilder(*args)
        .directory(currentDir)

    builder.environment().putAll(env)

    val process = builder.start()
    process.inputStream.bufferedReader().use { reader ->
        reader.lines().forEach {
            println("stdout: $it")
            output.appendLine(it)
        }
    }

    // Print stderr
    process.errorStream.bufferedReader().use { reader ->
        reader.lines().forEach { println("stderr: $it") }
    }

    val retVal = process.waitFor()

    if (retVal != 0) {
        throw IllegalStateException("Command execution failed with return value: $retVal")
    }

    return output.toString()
}

private fun runCmd(currentDir: File, vararg args: String): String {
    return runCmd(currentDir, mapOf(), *args)
}

private fun runCmd(vararg args: String): String {
    return runCmd(layout.projectDirectory.asFile, *args)
}

tasks.register("copyDependencies", Copy::class) {
    from(configurations.runtimeClasspath).into(layout.buildDirectory.dir("jmods"))
}

tasks.register("copyJar", Copy::class) {
    from(tasks.jar).into(layout.buildDirectory.dir("jmods"))
}

tasks.register("jlink") {
    dependsOn("assemble", "copyJar", "copyDependencies")
    val jlinkBin = Paths.get(System.getProperty("java.home"), "bin", "jlink")

    inputs.files(tasks.named("copyJar").get().outputs.files)
    outputs.file(layout.buildDirectory.file("jlink"))
    outputs.files.singleFile.deleteRecursively()

    doLast {
        runCmd(
            jlinkBin.absolutePathString(),
            "--ignore-signing-information",
            "--strip-native-commands",
            "--no-header-files",
            "--no-man-pages",
            "--strip-debug",
            "-p",
            inputs.files.singleFile.absolutePath,
            "--module-path",
            "${System.getProperty("java.home")}/jmods;${inputs.files.singleFile.absolutePath}",
            "--add-modules",
            "java.base,java.desktop,java.logging",
            "--output",
            outputs.files.singleFile.absolutePath,
        )
    }
}

tasks.register("prepareInfoPlist") {
    onlyIf { currentOS == OS.MAC }
    doLast {
        val template = layout.projectDirectory.file("mac-resources/Info.plist.template").asFile.readText()
        val content = template
            .replace("{{VERSION}}", version.toString())
            .replace("{{INTERNAL_VERSION}}", "1")
            .replace("{{PACKAGE_IDENTIFIER}}", "tanin.singleinstancedeeplink")
            .replace("{{APP_NAME}}", appName)

        layout.projectDirectory.file("mac-resources/Info.plist").asFile.writeText(content)
    }
}

tasks.register("jpackage") {
    dependsOn("jlink", "prepareInfoPlist")
    val javaHome = System.getProperty("java.home")
    val jpackageBin = Paths.get(javaHome, "bin", "jpackage")

    val runtimeImage = tasks.named("jlink").get().outputs.files.singleFile
    val modulePath = tasks.named("copyJar").get().outputs.files.singleFile

    inputs.files(runtimeImage, modulePath)

    val outputDir = layout.buildDirectory.dir("jpackage")
    val outputFile = if (currentOS == OS.MAC) {
        outputDir.get().asFile.resolve("${appName}-$version.dmg")
    } else if (currentOS == OS.WINDOWS) {
        outputDir.get().asFile.resolve("${appName}-$version.msi")
    } else {
        throw Exception("Unsupported OS: $currentOS")
    }

    outputs.file(outputFile)
    outputDir.get().asFile.deleteRecursively()

    doLast {
        // -XstartOnFirstThread is required for MacOS
        val maybeStartOnFirstThread = if (currentOS == OS.MAC) {
            "-XstartOnFirstThread"
        } else {
            ""
        }

        val javaOptionsArg = listOf(
            "--java-options",
            // -Djava.library.path=$APPDIR/resources is needed because we put the needed dylibs, jnilibs, and dlls there.
            "$maybeStartOnFirstThread -Dbackdoor.packaged=true -Djava.library.path=\$APPDIR/resources --add-exports java.base/sun.security.x509=ALL-UNNAMED --add-exports java.base/sun.security.tools.keytool=ALL-UNNAMED"
        )

        val baseArgs = listOf(
            "--name", appName,
            "--app-version", version.toString(),
            "--main-jar", modulePath.resolve("${project.name}-$version.jar").absolutePath,
            "--main-class", mainClassName,
            "--runtime-image", runtimeImage.absolutePath,
            "--input", modulePath.absolutePath,
            "--dest", outputDir.get().asFile.absolutePath,
            "--vendor", "Tanin Na Nakorn",
            "--copyright", "2025 Tanin Na Nakorn",
        ) + javaOptionsArg

        val platformSpecificArgs = if (currentOS == OS.MAC) {
            listOf(
                "--mac-package-identifier", "tanin.singleinstancedeeplink",
                "--mac-package-name", appName,
                "--resource-dir", layout.projectDirectory.dir("mac-resources").asFile.absolutePath,
            )
        } else if (currentOS == OS.WINDOWS) {
            listOf(
                "--icon", layout.projectDirectory.dir("win-resources").file("Backdoor.ico").asFile.absolutePath,
                "--type", "msi",
                "--win-menu",
                "--win-shortcut"
            )
        } else {
            throw Exception("Unsupported OS: $currentOS")
        }

        runCmd(*((listOf(jpackageBin.absolutePathString()) + baseArgs + platformSpecificArgs).toTypedArray()))
    }
}
