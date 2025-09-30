package com.tapii.voting_app

import Message
import android.os.Bundle
import android.view.View
import android.widget.*
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
import kotlinx.serialization.json.Json
import java.util.Collections
import kotlin.time.DurationUnit
import kotlin.time.toDuration

class LANDeviceHost : ComponentActivity() {

    private val clients: MutableCollection<DefaultWebSocketServerSession?> =
        Collections.synchronizedList(mutableListOf())
    private var acceptNewConnections = true

    private lateinit var subjectText: TextView
    private lateinit var optionsContainer: LinearLayout
    private lateinit var castVoteButton: Button
    private lateinit var clientCountText: TextView
    private lateinit var continueButton: Button
    private lateinit var resultDisplayer: LinearLayout

    private var votes: MutableMap<String, Int> = mutableMapOf()

    private lateinit var options: ArrayList<String>
    private var allowMultiple: Boolean = false

    private var server: EmbeddedServer<NettyApplicationEngine, NettyApplicationEngine.Configuration>? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.lan_voting_host)

        subjectText = findViewById(R.id.local_subject)
        optionsContainer = findViewById(R.id.local_options_container)
        castVoteButton = findViewById(R.id.cast_vote_button)
        clientCountText = findViewById(R.id.ready_to_vote_count)
        continueButton = findViewById(R.id.start_vote_button)
        resultDisplayer = findViewById(R.id.result_displayer)

        val subject = intent.getStringExtra("subject") ?: ""
        options = intent.getStringArrayListExtra("options") ?: arrayListOf()
        allowMultiple = intent.getBooleanExtra("allowMultiple", false)

        val voting = Message(subject, options, allowMultiple)
        val jsonVoting = Json.encodeToString(voting)
        votes = mutableMapOf(*options.map { it to 0 }.toTypedArray())

        subjectText.text = subject
        clientCountText.text = "Connected clients: 0"

        renderOptions()
        optionsContainer.visibility = View.GONE
        castVoteButton.visibility = View.GONE

        continueButton.setOnClickListener {
            lifecycleScope.launch {
                broadcast(jsonVoting)
                acceptNewConnections = false

                optionsContainer.visibility = View.VISIBLE
                castVoteButton.visibility = View.VISIBLE
                continueButton.visibility = View.GONE
                clientCountText.visibility = View.GONE
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

                            val receivedText = frame.readText()
                            val selections: List<Boolean> =
                                Json.decodeFromString(ListSerializer(Boolean.serializer()), receivedText)

                            selections.forEachIndexed { i, selected ->
                                if (selected) {
                                    votes[options[i]] = votes[options[i]]?.plus(1)!!
                                }
                            }

                            if (votes.values.sum() >= clients.size + 1) {
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

    private fun renderOptions() {
        optionsContainer.removeAllViews()
        castVoteButton.isEnabled = false

        if (allowMultiple) {
            for (opt in options) {
                val cb = CheckBox(this).apply {
                    text = opt
                    textSize = 18f
                    setPadding(16)
                    setOnCheckedChangeListener { _, _ ->
                        updateButtonState()
                    }
                }
                optionsContainer.addView(cb)
            }
        } else {
            val rg = RadioGroup(this).apply {
                orientation = RadioGroup.VERTICAL
                setOnCheckedChangeListener { _, _ ->
                    updateButtonState()
                }
            }
            for (opt in options) {
                val rb = RadioButton(this).apply {
                    text = opt
                    textSize = 18f
                    setPadding(16)
                }
                rg.addView(rb)
            }
            optionsContainer.addView(rg)
        }

        castVoteButton.setOnClickListener {
            submitHostVote()
            optionsContainer.visibility = View.GONE
            castVoteButton.visibility = View.GONE
        }
    }

    private fun updateButtonState() {
        castVoteButton.isEnabled = if (allowMultiple) {
            (0 until optionsContainer.childCount)
                .map { optionsContainer.getChildAt(it) as CheckBox }
                .any { it.isChecked }
        } else {
            val rg = optionsContainer.getChildAt(0) as RadioGroup
            rg.checkedRadioButtonId != -1
        }
    }

    private fun submitHostVote() {
        if (allowMultiple) {
            for (i in 0 until optionsContainer.childCount) {
                val cb = optionsContainer.getChildAt(i) as CheckBox
                if (cb.isChecked) votes[options[i]] = votes[options[i]]!! + 1
            }
        } else {
            val rg = optionsContainer.getChildAt(0) as RadioGroup
            val selectedIndex = rg.indexOfChild(findViewById(rg.checkedRadioButtonId))
            if (selectedIndex >= 0) votes[options[selectedIndex]] = votes[options[selectedIndex]]!! + 1
        }

        if (votes.values.sum() >= clients.size + 1) {
            lifecycleScope.launch(Dispatchers.Main) {
                endVoting()
            }
        } else {
            showResults()
        }
    }

    private fun showResults() {
        lifecycleScope.launch(Dispatchers.Main) {
            resultDisplayer.removeAllViews()
            val builder = StringBuilder("Voting Results:\n\n")
            for ((opt, count) in votes) {
                builder.append("$opt: $count\n")
                val tv = TextView(this@LANDeviceHost).apply {
                    text = "$opt: $count"
                    textSize = 18f
                    setPadding(16)
                }
                resultDisplayer.addView(tv)
            }
            Toast.makeText(this@LANDeviceHost, builder.toString(), Toast.LENGTH_LONG).show()
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
