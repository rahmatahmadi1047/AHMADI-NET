package com.rahmat.vpn

import android.app.Activity
import android.content.Intent
import android.net.VpnService
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.graphics.Color
import android.graphics.Typeface
import com.wireguard.android.backend.GoBackend
import com.wireguard.android.backend.Tunnel
import com.wireguard.config.Config
import java.io.BufferedReader
import java.io.StringReader
import java.util.concurrent.Executors

class MainActivity : Activity() {
    private lateinit var backend: GoBackend
    private lateinit var status: TextView
    private lateinit var configInput: EditText
    private lateinit var connectButton: Button
    private val executor = Executors.newSingleThreadExecutor()
    private var connected = false
    private val requestVpnPermission = 501

    private val tunnel = object : Tunnel {
        override fun getName(): String = "vpn"
        override fun onStateChange(newState: Tunnel.State) {
            runOnUiThread {
                connected = newState == Tunnel.State.UP
                status.text = if (connected) "وضعیت: متصل" else "وضعیت: قطع"
                connectButton.text = if (connected) "قطع اتصال" else "اتصال"
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        backend = GoBackend(applicationContext)
        buildUi()
    }

    private fun buildUi() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(24, 32, 24, 24)
            setBackgroundColor(Color.rgb(8, 18, 40))
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }
        val scroll = ScrollView(this)
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
        }

        val title = TextView(this).apply {
            text = "VPN"
            textSize = 34f
            setTextColor(Color.WHITE)
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setPadding(0, 10, 0, 8)
        }
        val subtitle = TextView(this).apply {
            text = "اتصال امن با WireGuard"
            textSize = 16f
            setTextColor(Color.rgb(180, 204, 235))
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, 24)
        }
        status = TextView(this).apply {
            text = "وضعیت: آماده اتصال"
            textSize = 18f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            setPadding(0, 12, 0, 16)
        }
        connectButton = Button(this).apply {
            text = "اتصال"
            textSize = 18f
            isAllCaps = false
            setOnClickListener { if (connected) disconnectTunnel() else connectTunnel() }
        }
        val configLabel = TextView(this).apply {
            text = "تنظیمات سرور WireGuard"
            textSize = 16f
            setTextColor(Color.WHITE)
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.RIGHT
            setPadding(0, 24, 0, 8)
        }
        configInput = EditText(this).apply {
            hint = "[Interface] و [Peer] را از مدیر سرور دریافت کن"
            textSize = 13f
            gravity = Gravity.TOP or Gravity.START
            minLines = 8
            setSingleLine(false)
            setTextColor(Color.WHITE)
            setHintTextColor(Color.LTGRAY)
            setPadding(12, 12, 12, 12)
            setBackgroundColor(Color.rgb(23, 38, 65))
            setHint("Paste WireGuard config here")
        }
        val note = TextView(this).apply {
            text = "برای اتصال واقعی باید پیکربندی معتبر سرور WireGuard داشته باشی. فعلاً سرور VPN ساخته یا به این برنامه وصل نشده است. کلید خصوصی خود را با کسی شریک نکن."
            textSize = 13f
            setTextColor(Color.rgb(180, 204, 235))
            gravity = Gravity.RIGHT
            setPadding(0, 14, 0, 16)
        }
        content.addView(title)
        content.addView(subtitle)
        content.addView(status)
        content.addView(connectButton, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ))
        content.addView(configLabel)
        content.addView(configInput, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, 300
        ))
        content.addView(note)
        scroll.addView(content)
        root.addView(scroll, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT
        ))
        setContentView(root)
    }

    private fun connectTunnel() {
        val text = configInput.text.toString().trim()
        if (text.isEmpty()) {
            status.text = "ابتدا تنظیمات سرور را وارد کن"
            return
        }
        val permissionIntent = VpnService.prepare(this)
        if (permissionIntent != null) {
            startActivityForResult(permissionIntent, requestVpnPermission)
        } else {
            startWireGuard(text)
        }
    }

    @Deprecated("Deprecated by Android, retained for broad device compatibility")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == requestVpnPermission) {
            if (resultCode == Activity.RESULT_OK) startWireGuard(configInput.text.toString().trim())
            else status.text = "اجازه VPN داده نشد"
        }
    }

    private fun startWireGuard(text: String) {
        status.text = "در حال اتصال..."
        executor.execute {
            try {
                val config = Config.parse(BufferedReader(StringReader(text)))
                backend.setState(tunnel, Tunnel.State.UP, config)
                runOnUiThread {
                    connected = true
                    status.text = "وضعیت: متصل"
                    connectButton.text = "قطع اتصال"
                }
            } catch (e: Exception) {
                runOnUiThread {
                    connected = false
                    status.text = "اتصال ناموفق: تنظیمات سرور را بررسی کن"
                    connectButton.text = "اتصال"
                }
            }
        }
    }

    private fun disconnectTunnel() {
        status.text = "در حال قطع اتصال..."
        executor.execute {
            try {
                backend.setState(tunnel, Tunnel.State.DOWN, null)
            } catch (_: Exception) {
                // Keep the UI responsive even if the backend is already down.
            }
            runOnUiThread {
                connected = false
                status.text = "وضعیت: قطع"
                connectButton.text = "اتصال"
            }
        }
    }

    override fun onDestroy() {
        executor.shutdownNow()
        super.onDestroy()
    }
}
