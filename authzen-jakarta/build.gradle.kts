plugins {
    id("org.jetbrains.kotlin.plugin.allopen") version "2.1.20"
    id("org.jetbrains.kotlin.plugin.noarg") version "2.1.20"
}

allOpen {
    annotation("jakarta.enterprise.context.ApplicationScoped")
    annotation("jakarta.interceptor.Interceptor")
}

noArg {
    annotation("jakarta.enterprise.context.ApplicationScoped")
    annotation("jakarta.interceptor.Interceptor")
}

dependencies {
    api(project(":authzen-api"))
    compileOnly("jakarta.enterprise:jakarta.enterprise.cdi-api:4.1.0")
    compileOnly("jakarta.interceptor:jakarta.interceptor-api:2.2.0")

    testImplementation("org.jboss.weld.se:weld-se-core:6.0.1.Final")
    testImplementation("io.kotest:kotest-runner-junit5:6.0.0.M1")
    testImplementation("io.kotest:kotest-assertions-core:6.0.0.M1")
}

tasks.withType<Test> {
    useJUnitPlatform()
}
