import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("jvm") version "1.6.0"
}

group = "bot.exchange"
version = "2.0-SNAPSHOT"

repositories {
    mavenCentral()
    mavenLocal()
}

dependencies {
    implementation("org.jetbrains.kotlin:kotlin-stdlib")

    implementation("org.apache.commons:commons-lang3:3.12.0")
    implementation("com.squareup.okhttp3:okhttp:4.10.0")
    implementation("com.typesafe:config:1.4.2")
    implementation("com.google.code.gson:gson:2.10")
    implementation("com.squareup.retrofit2:converter-jackson:2.9.0")
    implementation("org.telegram:telegrambots-spring-boot-starter:6.1.0")
    implementation("com.neovisionaries:nv-websocket-client:2.14")
    implementation("org.apache.poi:poi:5.2.2") // exel table

    // exchanges
    implementation("org.knowm.xchange:xchange-core:5.0.13")
    implementation("org.knowm.xchange:xchange-binance:5.0.13")
    implementation("org.knowm.xchange:xchange-stream-binance:5.0.13")
    implementation("org.knowm.xchange:xchange-huobi:5.0.13")
    implementation("org.knowm.xchange:xchange-gateio:5.0.13")
//    implementation("org.knowm.xchange:xchange-bybit:5.0.12")
    implementation("org.knowm.xchange:xchange-stream-huobi:5.0.13")

    implementation("org.slf4j:slf4j-api:2.0.4")

    implementation("io.github.microutils:kotlin-logging-jvm:2.1.23")


    // todo  Instruction https://gist.github.com/rppowell-lasfs/f0e3b2d18c3be03ada38a3e367eaf1b8
    // https://mvnrepository.com/artifact/org.xerial/sqlite-jdbc
    implementation("org.xerial:sqlite-jdbc:3.39.4.1")

    // logging
    implementation("org.slf4j:slf4j-log4j12:2.0.4")
    implementation("org.apache.logging.log4j:log4j-to-slf4j:2.19.0")
}

tasks.withType<KotlinCompile> {
    kotlinOptions.jvmTarget = "14"
}

tasks.withType<Jar> {
    manifest {
        attributes("Main-Class" to "bot.telegram.notificator.MainKt")
    }
    duplicatesStrategy = DuplicatesStrategy.INCLUDE

    from (configurations.runtimeClasspath.get().map { if (it.isDirectory) it else zipTree(it) })
}