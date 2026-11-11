plugins {
    id("org.jetbrains.kotlin.jvm")
    `java-library`
}

dependencies {
    implementation("com.squareup.okhttp3:okhttp:4.12.0")

    testImplementation("junit:junit:4.13.2")
}