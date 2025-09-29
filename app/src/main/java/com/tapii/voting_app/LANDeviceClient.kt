package com.tapii.voting_app

import Message
import android.os.Bundle
import androidx.activity.ComponentActivity
import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.websocket.*
import io.ktor.websocket.*
import kotlinx.coroutines.*
import android.content.Context
import android.net.wifi.WifiManager
import android.text.BoringLayout
import android.view.View
import android.widget.Button
import android.widget.CheckBox
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.TextView
import androidx.core.view.setPadding
import androidx.lifecycle.lifecycleScope
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import java.net.InetAddress

class LANDeviceClient : ComponentActivity() {

    private lateinit var subjectText: TextView
    private lateinit var optionsContainer: LinearLayout
    private lateinit var castVoteButton: Button
    private lateinit var voterInfo: TextView
    private lateinit var waitingText: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.lan_voting_client)

        subjectText = findViewById(R.id.lan_subject)
        optionsContainer = findViewById(R.id.lan_options_container)
        castVoteButton = findViewById(R.id.cast_vote_button)
        voterInfo = findViewById(R.id.voter_info)
        waitingText = findViewById(R.id.waiting_txt)

        val client = HttpClient(CIO) {
            install(WebSockets)
        }

        lifecycleScope.launch {
            try {
                client.webSocket(host = getDefaultGateway(this@LANDeviceClient)!!, port = 8080, path = "/voting") {
                    for (frame in incoming) {
                        if (frame is Frame.Text) {
                            val receivedText = frame.readText()
                            val jsonText = Json.parseToJsonElement(receivedText)

                            if (jsonText is JsonObject) {
                                val subject = jsonText["subject"]?.jsonPrimitive?.contentOrNull
                                val options = jsonText["options"]?.jsonArray?.map {
                                    it.jsonPrimitive.content
                                } ?: emptyList()
                                val checkboxes = jsonText["checkboxes"]?.jsonPrimitive?.booleanOrNull

                                // switch to main thread for UI
                                withContext(Dispatchers.Main) {
                                    renderOptions(checkboxes == true, options)
                                    subjectText.text = subject

                                    waitingText.visibility = View.GONE
                                    subjectText.visibility = View.VISIBLE
                                    optionsContainer.visibility = View.VISIBLE
                                    castVoteButton.visibility = View.VISIBLE
                                    voterInfo.visibility = View.VISIBLE

                                    castVoteButton.setOnClickListener {
                                        lifecycleScope.launch {
                                            sendVote(this@webSocket, checkboxes == true)
                                            castVoteButton.isEnabled = false
                                            close(CloseReason(CloseReason.Codes.NORMAL, "Vote sent"))
                                            finish()
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    waitingText.text = "Connection failed: ${e.message}"
                }
            } finally {
                client.close()
            }
        }
    }

    private fun getDefaultGateway(context: Context): String? {
        val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        val dhcpInfo = wifiManager.dhcpInfo ?: return null
        val gatewayInt = dhcpInfo.gateway
        return InetAddress.getByAddress(
            byteArrayOf(
                (gatewayInt and 0xFF).toByte(),
                (gatewayInt shr 8 and 0xFF).toByte(),
                (gatewayInt shr 16 and 0xFF).toByte(),
                (gatewayInt shr 24 and 0xFF).toByte()
            )
        ).hostAddress
    }

    private fun renderOptions(allowMultiple: Boolean, options: List<String>) {
        optionsContainer.removeAllViews()

        castVoteButton.isEnabled = false

        if (allowMultiple) {
            for (opt in options) {
                val cb = CheckBox(this).apply {
                    text = opt
                    textSize = 18f
                    setPadding(16)
                    setOnCheckedChangeListener { _, _ ->
                        updateButtonState(allowMultiple)
                    }
                }
                optionsContainer.addView(cb)
            }
        } else {
            val rg = RadioGroup(this).apply {
                orientation = RadioGroup.VERTICAL
                setOnCheckedChangeListener { _, _ ->
                    updateButtonState(allowMultiple)
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
    }

    private fun updateButtonState(allowMultiple: Boolean) {
        val anySelected = if (allowMultiple) {
            (0 until optionsContainer.childCount).any { i ->
                val view = optionsContainer.getChildAt(i)
                view is CheckBox && view.isChecked
            }
        } else {
            val rg = optionsContainer.getChildAt(0) as RadioGroup
            rg.checkedRadioButtonId != -1
        }

        castVoteButton.isEnabled = anySelected
    }

    private suspend fun sendVote(client: DefaultClientWebSocketSession, allowMultiple: Boolean) {
        val selections = mutableListOf<Boolean>()

        if (allowMultiple) {
            for (i in 0 until optionsContainer.childCount) {
                val view = optionsContainer.getChildAt(i)
                selections.add(view is CheckBox && view.isChecked)
            }
        } else {
            val rg = optionsContainer.getChildAt(0) as RadioGroup
            for (i in 0 until rg.childCount) {
                val rb = rg.getChildAt(i) as RadioButton
                selections.add(rb.isChecked)
            }
        }

        val json = Json.encodeToString(ListSerializer(Boolean.serializer()), selections)

        client.send(Frame.Text(json))
    }
}