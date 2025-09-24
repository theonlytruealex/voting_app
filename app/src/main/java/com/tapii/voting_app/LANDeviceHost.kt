package com.tapii.voting_app

import android.os.Bundle
import androidx.activity.ComponentActivity
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.routing.*
import io.ktor.server.websocket.*
import io.ktor.websocket.*
import kotlin.time.DurationUnit
import kotlin.time.toDuration

class LANDeviceHost : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        embeddedServer(Netty, port = 8080) {
            install(WebSockets) {
                pingPeriod = 15.toDuration(DurationUnit.SECONDS)
            }
            routing {
                webSocket("/voting") {
                    val userID = "user-${this.hashCode()}"
                    println("$userID connected.")

                    try {
                        for (frame in incoming) {
                            frame as? Frame.Text ?: continue
                            val receivedText = frame.readText()
                            println("Received from $userID: $receivedText")
                            send("Server received: $receivedText")
                        }
                    } catch (e: Exception) {
                        println("Error with $userID: ${e.localizedMessage}")
                    } finally {
                        println("$userID disconnected.")
                    }
                }
            }
        }.start(wait = false)
    }
}