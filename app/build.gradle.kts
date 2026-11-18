plugins {
    kotlin("jvm")
    application
}

kotlin {
    jvmToolchain(21)
}

dependencies {
    implementation(project(":net"))
    implementation(project(":auth"))
}

application {
    mainClass.set("mcplus.app.MainKt")
}
