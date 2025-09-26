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
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import java.net.InetAddress

class LANDeviceClient : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val client = HttpClient(CIO) {
            install(WebSockets)
        }
        var votingStarted = false

        runBlocking {
            client.webSocket(host = getDefaultGateway(this@LANDeviceClient), port = 8080,
                            path = "/voting") {
                send("Hello, WebSocket!")
                for (frame in incoming) {
                    if (frame is Frame.Text) {
                        val receivedText = frame.readText()
                        val jsonText = Json.parseToJsonElement(receivedText)

                        if (jsonText is JsonObject) {
                            val field = jsonText["votingStarted"]?.jsonPrimitive?.booleanOrNull

                            if (field != null) {
                                votingStarted = field
                                continue
                            }

                            val subject = jsonText["subject"]?.jsonPrimitive?.contentOrNull
                            val options = jsonText["options"]?.jsonArray?.map {
                                it.jsonPrimitive.content
                            } ?: emptyList()
                            val checkboxes = jsonText["checkboxes"]?.jsonPrimitive?.booleanOrNull
                        }

                        /** TODO:
                         * Make a new page that tells the client to wait for the host to start the
                         * vote. Each time a client receives a frame, it should check if it contains
                         * a flag for the start of the vote. After that, it should receive the vote
                         * details and be able to vote and send its response to the server.
                         */
                    }
                }
            }
        }

        client.close()
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
}