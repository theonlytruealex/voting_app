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


import android.widget.TextView
import android.widget.ScrollView
import androidx.lifecycle.lifecycleScope
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.routing.*
import io.ktor.server.websocket.*
import io.ktor.websocket.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.time.toDuration

class LANDeviceHost : ComponentActivity() {

    private val clients = mutableSetOf<DefaultWebSocketServerSession>()
    private val clientsMutex = Mutex()
    private lateinit var logTextView: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Simple ScrollView + TextView to show logs
        val scrollView = ScrollView(this)
        logTextView = TextView(this).apply {
            textSize = 16f
        }
        scrollView.addView(logTextView)
        setContentView(scrollView)

        lifecycleScope.launch(Dispatchers.IO) {
            val server = embeddedServer(Netty, port = 8080) {
                install(WebSockets) {
                    pingPeriod = 15.toDuration(DurationUnit.SECONDS)
                }
                routing {
                    webSocket("/voting") {
                        val userID = "user-${this.hashCode()}"

                        // Add client to set
                        clientsMutex.withLock { clients.add(this) }

                        logOnMain("$userID connected.")

                        try {
                            for (frame in incoming) {
                                frame as? Frame.Text ?: continue
                                val receivedText = frame.readText()

                                logOnMain("Received from $userID: $receivedText")

                                // Broadcast to all clients
                                clientsMutex.withLock {
                                    clients.forEach { client ->
                                        try {
                                            client.send("$userID says: $receivedText")
                                        } catch (_: Exception) {
                                        }
                                    }
                                }

                                logOnMain("Broadcasted: $receivedText")
                            }
                        } catch (e: Exception) {
                            logOnMain("Error with $userID: ${e.localizedMessage}")
                        } finally {
                            clientsMutex.withLock { clients.remove(this) }
                            logOnMain("$userID disconnected.")
                        }
                    }
                }
            }

            server.start(wait = true)
        }
    }

    private fun logOnMain(message: String) {
        runOnUiThread {
            logTextView.append("$message\n")
        }
    }
}
