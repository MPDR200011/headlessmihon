package com.example.source_service

import android.app.Service
import android.content.Intent
import android.os.IBinder
import io.ktor.application.*
import io.ktor.features.*
import io.ktor.gson.*
import io.ktor.routing.*
import io.ktor.http.*
import io.ktor.response.*
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.netty.NettyApplicationEngine

fun Application.module() {
    install(ContentNegotiation) {
        gson()
    }

    routing {
        get("/search/{query}") {
            call.respond(mapOf("query" to call.parameters["query"]))
        }
    }
}

class SourceService: Service() {
    private lateinit var server: NettyApplicationEngine

    override fun onCreate() {
        server = embeddedServer(Netty, port = 8080, module = Application::module)
        super.onCreate()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        server.start(wait = false)
        return START_STICKY
    }

    override fun onDestroy() {
        server.stop(0,0)
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

}
