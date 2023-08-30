plugins {
    id("org.springframework.boot") version "3.1.0"
    kotlin("jvm") version "1.9.0"
    id("org.jetbrains.kotlin.plugin.spring") version "1.9.0"
    application
}

application {
    mainClass.set("bot.Main")
}

group = "bot.exchange"
version = "2.0-SNAPSHOT"
java.sourceCompatibility = JavaVersion.VERSION_18

repositories {
    mavenCentral()
    mavenLocal()
}

dependencies {
    implementation("org.jetbrains.kotlin:kotlin-stdlib")
    implementation("org.jetbrains.kotlin:kotlin-reflect")
    implementation("org.springframework.boot:spring-boot-starter-web:3.1.2") {
        exclude(group = "ch.qos.logback", module = "logback-classic")
        exclude(group = "com.fasterxml.jackson.core", module = "jackson-databind")
    }
    implementation("org.springframework.boot:spring-boot-starter-data-jpa:3.1.2") {
        exclude(group = "ch.qos.logback", module = "logback-classic")
        exclude(group = "com.fasterxml.jackson.core", module = "jackson-databind")
    }
    implementation("com.h2database:h2:2.2.220")
    implementation("org.apache.commons:commons-lang3:3.12.0")
    implementation("com.squareup.okhttp3:okhttp:4.10.0")
    implementation("com.typesafe:config:1.4.2")
    implementation("com.google.code.gson:gson:2.10.1")
    implementation("com.squareup.retrofit2:converter-jackson:2.9.0")
    implementation("org.telegram:telegrambots-spring-boot-starter:6.5.0")
    implementation("com.neovisionaries:nv-websocket-client:2.14")
    implementation("org.apache.poi:poi:5.2.2") // exel table

    // exchanges
    implementation("org.knowm.xchange:xchange-core:5.1.1-SNAPSHOT")
    implementation("org.knowm.xchange:xchange-binance:5.1.1-SNAPSHOT")
    implementation("org.knowm.xchange:xchange-stream-binance:5.1.1-SNAPSHOT")
    implementation("org.knowm.xchange:xchange-huobi:5.1.1-SNAPSHOT")
    implementation("org.knowm.xchange:xchange-gateio:5.1.1-SNAPSHOT")
//    implementation("org.knowm.xchange:xchange-bybit:5.0.12")
    implementation("org.knowm.xchange:xchange-stream-huobi:5.1.1-SNAPSHOT")

    implementation("org.slf4j:slf4j-api:2.0.5")
    implementation("io.github.microutils:kotlin-logging-jvm:3.0.5")


    // todo  Instruction https://gist.github.com/rppowell-lasfs/f0e3b2d18c3be03ada38a3e367eaf1b8
    // https://mvnrepository.com/artifact/org.xerial/sqlite-jdbc
    implementation("org.xerial:sqlite-jdbc:3.40.1.0")

    // logging
    implementation("org.slf4j:slf4j-log4j12:2.0.5")
    implementation("org.apache.logging.log4j:log4j-to-slf4j:2.20.0")
}