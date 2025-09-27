package com.tapii.voting_app

import Message
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.routing.*
import io.ktor.server.websocket.*
import io.ktor.websocket.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlin.time.DurationUnit
import kotlin.time.toDuration
import kotlinx.serialization.json.*
import java.util.Collections

class LANDeviceHost : ComponentActivity() {
    private val clients: MutableCollection<DefaultWebSocketServerSession?> =
        Collections.synchronizedList(mutableListOf())
    private var acceptNewConnections = true

    private lateinit var clientCountText: TextView
    private lateinit var continueButton: Button
    private var server: EmbeddedServer<NettyApplicationEngine, NettyApplicationEngine.Configuration>? = null


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.lan_voting_host)

        clientCountText = findViewById(R.id.ready_to_vote_count)
        continueButton = findViewById(R.id.start_vote_button)

        val subject = intent.getStringExtra("subject") ?: ""
        val options = intent.getStringArrayListExtra("options") ?: arrayListOf()
        val checkboxes = intent.getBooleanExtra("allowMultiple", false)
        val voting = Message(subject, options, checkboxes)
        val jsonVoting = Json.encodeToString(voting)
        val results = MutableList<Int>(options.size) { 0 }
        var voterCount = 0

        clientCountText.text = "Connected clients: 0"

        continueButton.setOnClickListener {
            lifecycleScope.launch {
                broadcast(jsonVoting)
                acceptNewConnections = false
            }
        }

        server = embeddedServer(Netty, port = 8080) {
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
                    updateClientCount()

                    try {
                        for (frame in incoming) {
                            frame as? Frame.Text ?: continue
                            voterCount++

                            val receivedText = frame.readText()
                            val selections: List<Boolean> = Json.decodeFromString(ListSerializer(Boolean.serializer()), receivedText)

                            selections.forEachIndexed { i, selected ->
                                if (selected) {
                                    results[i]++
                                }
                            }

                            if (voterCount >= clients.size) {
                                lifecycleScope.launch(Dispatchers.Main) {
                                    server?.stop(200, 1000, java.util.concurrent.TimeUnit.MILLISECONDS)
                                    clients.clear()
                                }
                            }

                            // TODO: Display the results
                        }
                    } catch (e: Exception) {
                        println("Error with $userID: ${e.localizedMessage}")
                    } finally {
                        clients -= this
                        updateClientCount()
                        println("$userID disconnected.")
                    }
                }
            }
        }.start(wait = false)
    }

    private fun updateClientCount() {
        lifecycleScope.launch(Dispatchers.Main) {
            clientCountText.text = "Connected clients: ${clients.size}"
        }
    }

    private suspend fun broadcast(message: String) {
        val deadSessions = mutableListOf<DefaultWebSocketServerSession>()
        clients.forEach { session ->
            try {
                session?.send(message)
            } catch (e: Exception) {
                if (session != null) deadSessions.add(session)
            }
        }
        clients.removeAll(deadSessions)
    }

    override fun onDestroy() {
        super.onDestroy()
        server?.stop(200, 1000, java.util.concurrent.TimeUnit.MILLISECONDS)
        clients.clear()
    }
}
