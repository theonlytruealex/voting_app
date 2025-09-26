package com.tapii.voting_app

import Message
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.routing.*
import io.ktor.server.websocket.*
import io.ktor.websocket.*
import kotlinx.coroutines.launch
import kotlin.time.DurationUnit
import kotlin.time.toDuration
import kotlinx.serialization.json.*
import java.util.Collections

class LANDeviceHost : ComponentActivity() {
    val clients: MutableCollection<DefaultWebSocketServerSession?> =
        Collections.synchronizedList<DefaultWebSocketServerSession?>(mutableListOf())
    var acceptNewConnections = true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val subject = intent.getStringExtra("subject") ?: ""
        val options = intent.getStringArrayListExtra("options") ?: arrayListOf()
        val checkboxes = intent.getBooleanExtra("allowMultiple", false)
        val voting = Message(subject, options, checkboxes)
        val jsonVoting = Json.encodeToString(voting)

        embeddedServer(Netty, port = 8080) {
            install(WebSockets) {
                pingPeriod = 15.toDuration(DurationUnit.SECONDS)
            }
            routing {
                webSocket("/voting") {
                    val userID = "user-${this.hashCode()}"

                    if (!acceptNewConnections) {
                        close(CloseReason(CloseReason.Codes.VIOLATED_POLICY, "Voting started."))
                        return@webSocket
                    }

                    println("$userID connected.")
                    clients += this

                    try {
                        for (frame in incoming) {
                            frame as? Frame.Text ?: continue
                            val receivedText = frame.readText()
                            println("Received from $userID: $receivedText")
                        }
                    } catch (e: Exception) {
                        println("Error with $userID: ${e.localizedMessage}")
                    } finally {
                        clients -= this
                        println("$userID disconnected.")
                    }
                }
            }
        }.start(wait = false)

        /** TODO:
         * After pressing CONTINUE, the flag `acceptNewConnections` should be set to `false` and the
         * broadcast function should be used to notify the clients that the voting has started and
         * send them the poll.
         */

        // broadcasting example
        lifecycleScope.launch {
            broadcast(buildJsonObject { put("votingStarted", true) }.toString())
        }
    }

    private suspend fun broadcast(message: String) {
        val deadSessions = mutableListOf<DefaultWebSocketServerSession>()

        clients.forEach { session ->
            try {
                session?.send(message)
            } catch (e: Exception) {
                deadSessions.add(session!!)
            }
        }

        clients.removeAll(deadSessions)
    }
}
