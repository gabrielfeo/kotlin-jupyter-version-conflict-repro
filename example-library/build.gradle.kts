plugins {
    kotlin("jvm") version "2.1.20"
    application
    `maven-publish`
}

group = "com.example"
version = "SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    api("com.squareup.okhttp3:okhttp:5.1.0")
    api("com.squareup.moshi:moshi:1.15.2")
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(8)
    }
}

publishing {
    publications {
        create<MavenPublication>("library") {
            from(components["java"])
        }
    }
}
