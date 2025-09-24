package com.tapii.voting_app

import android.os.Bundle
import androidx.activity.ComponentActivity
import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.websocket.*
import io.ktor.websocket.*
import kotlinx.coroutines.*
import android.content.Context
import android.net.wifi.WifiManager
import java.net.InetAddress

class LANDeviceClient : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val client = HttpClient(CIO) {
            install(WebSockets)
        }

        runBlocking {
            client.webSocket(host = getDefaultGateway(this@LANDeviceClient), port = 8080,
                            path = "/voting") {
                send("Hello, WebSocket!")
                for (frame in incoming) {
                    when (frame) {
                        is Frame.Text -> println("Received text: ${frame.readText()}")
                        is Frame.Close -> println("Connection closed by server.")
                        else -> println("Other frame received.")
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