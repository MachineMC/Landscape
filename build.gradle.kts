plugins {
    java
    `maven-publish`
}

group = "org.machinemc"
version = "1.0.4"

repositories {
    mavenCentral()
    maven {
        url = uri("https://repo.machinemc.org/releases")
    }
}

dependencies {
    implementation("org.machinemc:nbt-core:2.0.0")
    implementation("io.netty:netty-buffer:4.1.89.Final")
    compileOnly("org.jetbrains:annotations:23.0.0")
    testImplementation("org.junit.jupiter:junit-jupiter-api:5.6.0")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine")
}

java {
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
    withSourcesJar()
}

publishing {
    repositories {
        maven {
            name = "machine"
            url = uri("https://repo.machinemc.org/releases")
            credentials(PasswordCredentials::class)
            authentication {
                create<BasicAuthentication>("basic")
            }
        }
    }
    publications {
        create<MavenPublication>("maven") {
            groupId = "org.machinemc"
            artifactId = "landscape"
            version = "1.0.4"
            from(components["java"])
        }
    }
}

tasks {
    test {
        useJUnitPlatform()
    }
}