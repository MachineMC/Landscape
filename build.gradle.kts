plugins {
    java
}

group = "me.pesekjak"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    implementation(files("libs/nbt.jar"))
    compileOnly("org.jetbrains:annotations:23.0.0")
    testImplementation("org.junit.jupiter:junit-jupiter-api:5.6.0")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine")
}

tasks {
    test {
        useJUnitPlatform()
    }
}