import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar

plugins {
    application
    kotlin("jvm") version "1.3.60"
    id("com.github.johnrengelman.shadow") version "5.1.0"
}

application {
    mainClassName = "task.MainKt"
}


dependencies {
    compile(kotlin("stdlib"))
    compile("io.arrow-kt", "arrow-core", "0.10.4")
    compile("com.h2database", "h2", "1.4.200")
    compile("me.liuwj.ktorm", "ktorm-core", "2.6")
    compile("com.mchange", "c3p0", "0.9.5.5")
    compile("io.ktor", "ktor-server-core", "1.3.0")
    compile("io.ktor", "ktor-server-netty", "1.3.0")
    compile("io.ktor", "ktor-jackson", "1.3.0")
    compile("com.fasterxml.jackson.datatype", "jackson-datatype-jsr310", "2.10.2")

    testImplementation("io.kotlintest:kotlintest-runner-junit5:3.3.0")

}


tasks.withType<ShadowJar>() {
    manifest {
        attributes["Main-Class"] = "task.MainKt"
    }
}

tasks.withType<Test> {
    useJUnitPlatform()
}

repositories {
    jcenter()
}
