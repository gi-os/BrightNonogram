plugins {
    kotlin("jvm") version "2.0.21"
    application
}

repositories { mavenCentral() }

dependencies {
    testImplementation(kotlin("test"))
}

kotlin { jvmToolchain(17) }

application {
    mainClass.set("nonogram.MainKt")
}

tasks.test {
    useJUnitPlatform()
    testLogging { events("passed", "failed", "skipped") }
}

/** Regenerate every pack. Wire this into CI so packs are never hand-edited. */
tasks.register<JavaExec>("buildPacks") {
    group = "picross"
    description = "Regenerate all puzzle packs into ./packs"
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("nonogram.MainKt")
    args = listOf("random", "--size", "15", "--count", "150", "--seed", "1", "--pack-id", "core-15")
}
