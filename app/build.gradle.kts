plugins {
    kotlin("jvm")
    application
}

kotlin {
    jvmToolchain(21)
}

dependencies {
    implementation(project(":net"))
}

application {
    mainClass.set("mcplus.app.MainKt")
}
