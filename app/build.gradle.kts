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

    implementation(platform("org.lwjgl:lwjgl-bom:$lwjglVersion"))
    implementation("org.lwjgl:lwjgl")
    implementation("org.lwjgl:lwjgl-glfw")
    implementation("org.lwjgl:lwjgl-opengl")
    runtimeOnly("org.lwjgl:lwjgl::$lwjglNatives")
    runtimeOnly("org.lwjgl:lwjgl-glfw::$lwjglNatives")
    runtimeOnly("org.lwjgl:lwjgl-opengl::$lwjglNatives")
}

application {
    mainClass.set("mcplus.app.MainKt")
}

tasks.named<JavaExec>("run") {
    // LWJGL needs the natives on the library path when launched through Gradle.
    jvmArgs("-Djava.awt.headless=false")
}
