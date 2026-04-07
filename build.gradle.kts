plugins {
    kotlin("jvm") version "2.1.20" apply false
    id("com.google.protobuf") version "0.9.6" apply false
    id("maven-publish")
    id("signing")
    id("com.vanniktech.maven.publish") version "0.32.0" apply false
}

allprojects {
    group = "com.dataversation.authzen"
    version = "0.2.0-SNAPSHOT"

    repositories {
        mavenCentral()
    }
}

subprojects {
    apply(plugin = "org.jetbrains.kotlin.jvm")
    apply(plugin = "java-library")
    apply(plugin = "com.vanniktech.maven.publish")

    configure<JavaPluginExtension> {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_21)
        }
    }

    configure<com.vanniktech.maven.publish.MavenPublishBaseExtension> {
        publishToMavenCentral(com.vanniktech.maven.publish.SonatypeHost.CENTRAL_PORTAL, automaticRelease = true)
        signAllPublications()

        pom {
            name.set(project.name)
            description.set("AuthZEN Authorization API 1.0 client for Kotlin/JVM")
            url.set("https://github.com/dataversation/authzen-client")

            licenses {
                license {
                    name.set("European Union Public License 1.2")
                    url.set("https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12")
                }
            }

            developers {
                developer {
                    id.set("mtrimpe")
                    name.set("Milo Trimpe")
                    organization.set("Dataversation")
                }
            }

            scm {
                connection.set("scm:git:git://github.com/dataversation/authzen-client.git")
                developerConnection.set("scm:git:ssh://github.com/dataversation/authzen-client.git")
                url.set("https://github.com/dataversation/authzen-client")
            }
        }
    }
}
