plugins {
    java
    `maven-publish`
}

group = "org.machinemc"
version = "1.0.3"

repositories {
    mavenCentral()
    maven {
        url = uri("http://www.machinemc.org/releases")
        isAllowInsecureProtocol = true
    }
}

dependencies {
    implementation("org.machinemc:nbt:1.1.0")
    implementation("io.netty:netty-buffer:4.1.89.Final")
    compileOnly("org.jetbrains:annotations:23.0.0")
    testImplementation("org.junit.jupiter:junit-jupiter-api:5.6.0")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine")
}

publishing {
    repositories {
        maven {
            name = "machine"
            url = uri("http://www.machinemc.org/releases")
            credentials(PasswordCredentials::class)
            authentication {
                create<BasicAuthentication>("basic")
            }
            isAllowInsecureProtocol = true
        }
    }
    publications {
        create<MavenPublication>("maven") {
            groupId = "org.machinemc"
            artifactId = "landscape"
            version = "1.0.2"
            from(components["java"])
        }
    }
}

tasks {
    test {
        useJUnitPlatform()
    }
}