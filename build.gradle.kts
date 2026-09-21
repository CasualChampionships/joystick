plugins {
    `kotlin-dsl`
    `java-gradle-plugin`
    alias(libs.plugins.plugin.publish)
}

group = "net.casualchampionships"
version = providers.gradleProperty("plugin_version").get()

repositories {
    mavenCentral()
}

dependencies {
    implementation(libs.gson)

    testImplementation(libs.junit.jupiter)
    testImplementation(gradleTestKit())
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(libs.versions.java.get()))
    }
}

kotlin {
    explicitApi()
}

gradlePlugin {
    website.set("https://github.com/CasualChampionships/joystick")
    vcsUrl.set("https://github.com/CasualChampionships/joystick")

    plugins {
        create("joystick") {
            id = "net.casualchampionships.joystick"
            implementationClass = "net.casual.joystick.JoystickPlugin"
            displayName = "Joystick"
            description = "Utility for depending and bundling arcade modules"
            tags.set(listOf("minecraft", "fabric", "arcade"))
        }
    }
}

tasks.test {
    useJUnitPlatform()
}

publishing {
    repositories {
        val mavenUrl = providers.environmentVariable("MAVEN_URL")
        if (mavenUrl.isPresent) {
            maven {
                url = uri(mavenUrl.get())
                val mavenUsername = providers.environmentVariable("MAVEN_USERNAME")
                val mavenPassword = providers.environmentVariable("MAVEN_PASSWORD")
                if (mavenUsername.isPresent && mavenPassword.isPresent) {
                    credentials {
                        username = mavenUsername.get()
                        password = mavenPassword.get()
                    }
                }
            }
        }
    }
}

tasks.register("release") {
    group = "publishing"
    description = "Publishes the plugin to the Gradle Plugin Portal and to the remote Maven repository"
    dependsOn(tasks.named("publishPlugins"))
    dependsOn(tasks.named("publish"))
}
