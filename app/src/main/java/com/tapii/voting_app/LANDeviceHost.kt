package com.tapii.voting_app

import Message
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.core.view.setPadding
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
import kotlin.collections.component1
import kotlin.collections.component2
import kotlin.collections.iterator

class LANDeviceHost : ComponentActivity() {
    private val clients: MutableCollection<DefaultWebSocketServerSession?> =
        Collections.synchronizedList(mutableListOf())
    private var acceptNewConnections = true

    private lateinit var clientCountText: TextView
    private lateinit var continueButton: Button
    private var server: EmbeddedServer<NettyApplicationEngine, NettyApplicationEngine.Configuration>? = null
    private lateinit var resultDisplayer: LinearLayout

    private lateinit var votes: MutableMap<String, Int>


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.lan_voting_host)

        clientCountText = findViewById(R.id.ready_to_vote_count)
        continueButton = findViewById(R.id.start_vote_button)
        resultDisplayer = findViewById(R.id.result_displayer)

        val subject = intent.getStringExtra("subject") ?: ""
        val options = intent.getStringArrayListExtra("options") ?: arrayListOf()
        val checkboxes = intent.getBooleanExtra("allowMultiple", false)
        val voting = Message(subject, options, checkboxes)
        val jsonVoting = Json.encodeToString(voting)
        var voterCount = 0
        votes = mutableMapOf(*options.map{it to 0}.toTypedArray())
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
                                    votes[options[i]] = votes[options[i]]?.plus(1) as Int
                                }
                            }

                            if (voterCount >= clients.size) {
                                lifecycleScope.launch(Dispatchers.Main) {
                                    endVoting()
                                }
                            }

                            showResults()
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

    private fun showResults() {
        lifecycleScope.launch(Dispatchers.Main) {
            clientCountText.visibility = View.INVISIBLE
            continueButton.visibility = View.INVISIBLE
            val builder = StringBuilder("Voting Finished!\n\nResults:\n")
            for ((opt, count) in votes) {
                builder.append("$opt: $count\n")
            }
            Toast.makeText(this@LANDeviceHost, builder.toString(), Toast.LENGTH_LONG).show()

            resultDisplayer.removeAllViews()
            for ((opt, count) in votes) {
                val tv = TextView(this@LANDeviceHost).apply {
                    text = "$opt: $count"
                    textSize = 18f
                    setPadding(16)
                }
                resultDisplayer.addView(tv)
            }
        }
    }

    private suspend fun endVoting() {
        broadcast("Voting Finished!")

        clients.forEach { session ->
            session?.close(CloseReason(CloseReason.Codes.NORMAL, "Voting ended"))
        }

        clients.clear()

        server?.stop(200, 1000, java.util.concurrent.TimeUnit.MILLISECONDS)
    }


}
