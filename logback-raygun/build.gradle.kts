import org.jetbrains.kotlin.fir.expressions.buildArraySetArgumentList
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    id("org.jetbrains.kotlin.jvm") version "1.5.21"
    `java-library`
}

repositories {
    mavenCentral()
}

group = "com.tutuka"
version = "3.0.0"

java.sourceCompatibility = JavaVersion.VERSION_11

tasks.withType<Test> {
    useJUnitPlatform()
}

tasks.withType<KotlinCompile> {
    kotlinOptions {
        freeCompilerArgs = listOf("-Xjsr305=strict")
        jvmTarget = "11"
    }
}

dependencies {
    implementation(platform("org.jetbrains.kotlin:kotlin-bom"))
    implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk8")
    implementation("ch.qos.logback:logback-classic:1.2.5")
    implementation("com.mindscapehq:core:3.0.0")
    testImplementation("org.junit.jupiter:junit-jupiter:5.7.2")
    testImplementation("org.jetbrains.kotlin:kotlin-test")
}
