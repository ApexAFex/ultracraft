import java.util.zip.ZipFile

plugins {
    kotlin("jvm")
    application
}

kotlin {
    jvmToolchain(21)
}

val lwjglVersion = "3.3.3"
val lwjglNatives = "natives-windows"

dependencies {
    implementation(project(":net"))
    implementation(project(":auth"))
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")

    implementation(platform("org.lwjgl:lwjgl-bom:$lwjglVersion"))
    implementation("org.lwjgl:lwjgl")
    implementation("org.lwjgl:lwjgl-glfw")
    implementation("org.lwjgl:lwjgl-opengl")
    implementation("org.lwjgl:lwjgl-openal")
    implementation("org.lwjgl:lwjgl-stb")
    runtimeOnly("org.lwjgl:lwjgl:$lwjglVersion:$lwjglNatives")
    runtimeOnly("org.lwjgl:lwjgl-glfw:$lwjglVersion:$lwjglNatives")
    runtimeOnly("org.lwjgl:lwjgl-opengl:$lwjglVersion:$lwjglNatives")
    runtimeOnly("org.lwjgl:lwjgl-openal:$lwjglVersion:$lwjglNatives")
    runtimeOnly("org.lwjgl:lwjgl-stb:$lwjglVersion:$lwjglNatives")

    // The mesher is pure geometry and must be testable without a GPU.
    testImplementation(kotlin("test"))
    testImplementation("org.junit.jupiter:junit-jupiter:5.11.4")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.test {
    useJUnitPlatform()
}

application {
    mainClass.set("mcplus.app.MainKt")
}

tasks.named<JavaExec>("run") {
    // LWJGL needs the natives on the library path when launched through Gradle.
    jvmArgs("-Djava.awt.headless=false")
}

/**
 * Single runnable jar with every dependency, natives included.
 *
 * LWJGL extracts its DLLs from the classpath into a temp directory at runtime, so
 * `java -jar ultracraft.jar` works without copying natives next to the jar.
 */
val fatJar = tasks.register<Jar>("fatJar") {
    group = "distribution"
    description = "Собирает один исполняемый jar со всеми зависимостями"

    archiveBaseName.set("ultracraft")
    archiveVersion.set("")
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE

    manifest {
        attributes["Main-Class"] = "mcplus.app.MainKt"
    }

    from(sourceSets.main.get().output)
    dependsOn(configurations.runtimeClasspath)
    from({
        configurations.runtimeClasspath.get()
            .filter { it.name.endsWith(".jar") }
            .map { zipTree(it) }
    })

    exclude("META-INF/*.SF", "META-INF/*.DSA", "META-INF/*.RSA", "module-info.class")
}

/** Windows application image with a bundled runtime: dist/ultracraft/ultracraft.exe */
val packageExe = tasks.register<Exec>("packageExe") {
    group = "distribution"
    description = "Собирает ultracraft.exe через jpackage"

    dependsOn(fatJar)
    workingDir = rootProject.projectDir

    val jpackage = File(System.getProperty("java.home"), "bin/jpackage.exe").absolutePath
    val libs = layout.buildDirectory.dir("libs").get().asFile.absolutePath

    doFirst {
        delete(File(rootProject.projectDir, "dist/ultracraft"))
    }

    // jpackage does not look inside jars for native libraries, so the DLLs are unpacked
    // next to the executable where the launcher's java.library.path points.
    doLast {
        val jar = File(libs, "ultracraft.jar")
        val target = File(rootProject.projectDir, "dist/ultracraft/app").apply { mkdirs() }
        ZipFile(jar).use { zip ->
            for (entry in zip.entries()) {
                if (!entry.name.startsWith("windows/x64/") || !entry.name.endsWith(".dll")) continue
                val out = File(target, entry.name.substringAfterLast('/'))
                zip.getInputStream(entry).use { input -> out.outputStream().use { input.copyTo(it) } }
            }
        }
    }

    commandLine(
        jpackage,
        "--type", "app-image",
        "--name", "ultracraft",
        "--app-version", "0.1.0",
        "--input", libs,
        "--main-jar", "ultracraft.jar",
        "--main-class", "mcplus.app.MainKt",
        "--dest", File(rootProject.projectDir, "dist").absolutePath,
        "--java-options", "-Xmx3G",
        "--win-console",
    )
}




