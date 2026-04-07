val okhttpVersion = "4.12.0"
val jacksonVersion = "2.18.4"

dependencies {
    api(project(":authzen-api"))
    implementation("com.squareup.okhttp3:okhttp:$okhttpVersion")
    implementation("com.fasterxml.jackson.core:jackson-databind:$jacksonVersion")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin:$jacksonVersion")
}
