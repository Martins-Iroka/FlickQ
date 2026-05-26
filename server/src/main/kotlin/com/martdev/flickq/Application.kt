package com.martdev.flickq

import io.github.cdimascio.dotenv.dotenv
import io.ktor.server.application.Application

fun main(args: Array<String>) {
    dotenv {
        systemProperties = true
    }
    io.ktor.server.netty.EngineMain.main(args)
}

fun Application.module() {

}