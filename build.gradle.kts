plugins {
    kotlin("jvm") version "2.1.0" apply false
    kotlin("plugin.serialization") version "2.1.0" apply false
}

allprojects {
    group = "mcplus"
    version = "0.1.0-SNAPSHOT"
    repositories {
        mavenCentral()
    }
}
