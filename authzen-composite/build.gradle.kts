dependencies {
    api(project(":authzen-api"))
    testImplementation("io.kotest:kotest-runner-junit5:6.0.0.M1")
    testImplementation("io.kotest:kotest-assertions-core:6.0.0.M1")
}

tasks.withType<Test> {
    useJUnitPlatform()
}
