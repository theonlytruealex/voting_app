package com.tapii.voting_app

import android.os.Bundle
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.core.view.setPadding
import androidx.lifecycle.lifecycleScope
import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.websocket.*
import io.ktor.websocket.*
import kotlinx.coroutines.*
import android.content.Context
import android.net.wifi.WifiManager
import java.net.InetAddress
import kotlin.coroutines.coroutineContext

class LANDeviceClient : ComponentActivity() {

    private lateinit var client: HttpClient
    private lateinit var messagesTextView: TextView
    private lateinit var scrollView: ScrollView
    private var connectionJob: Job? = null
    private var keepTrying = true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Create a simple UI: ScrollView with TextView
        scrollView = ScrollView(this)
        messagesTextView = TextView(this).apply {
            setPadding(16)
        }
        scrollView.addView(messagesTextView)
        setContentView(scrollView)

        client = HttpClient(CIO) {
            install(WebSockets)
        }

        // Start connection in coroutine
        connectionJob = lifecycleScope.launch {
            tryConnect()
        }
    }

    private suspend fun tryConnect() {
        val host = getDefaultGateway(this@LANDeviceClient)
        if (host == null) {
            showMessageOnScreen("No Wi-Fi gateway found. Are you connected to Wi-Fi?")
            return
        }

        while (keepTrying && coroutineContext.isActive) {
            try {
                showMessageOnScreen("Connecting to $host:8080...")
                client.webSocket(host = host, port = 8080, path = "/voting") {
                    showMessageOnScreen("Connected to $host!")

                    send("Hello, WebSocket!")

                    for (frame in incoming) {
                        when (frame) {
                            is Frame.Text -> showMessageOnScreen("Server: ${frame.readText()}")
                            is Frame.Close -> showMessageOnScreen("Connection closed by server.")
                            else -> showMessageOnScreen("Other frame received.")
                        }
                    }
                }
                break // connection closed normally, stop retrying
            } catch (e: Exception) {
                showMessageOnScreen("Connection failed: ${e.message}. Retrying in 2s...")
                delay(2000)
            }
        }
    }

    private fun showMessageOnScreen(message: String) {
        runOnUiThread {
            messagesTextView.append("$message\n")
            scrollView.post { scrollView.fullScroll(ScrollView.FOCUS_DOWN) }
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

    override fun onDestroy() {
        super.onDestroy()
        keepTrying = false
        connectionJob?.cancel()
        client.close()
    }
}
