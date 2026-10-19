package com.rahmat.vpn

import android.app.Activity
import android.content.Intent
import android.net.VpnService
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
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
import java.util.Locale
import java.util.concurrent.Executors

class MainActivity : Activity() {
    private lateinit var backend: GoBackend
    private lateinit var status: TextView
    private lateinit var configInput: EditText
    private lateinit var connectButton: Button
    private lateinit var downloadSpeedView: TextView
    private lateinit var uploadSpeedView: TextView
    private lateinit var downloadedView: TextView
    private lateinit var uploadedView: TextView
    private lateinit var durationView: TextView
    private val executor = Executors.newSingleThreadExecutor()
    private val mainHandler = Handler(Looper.getMainLooper())

    @Volatile private var connected = false
    private var sessionStartedAt = 0L
    private var lastRxBytes = 0L
    private var lastTxBytes = 0L
    private val requestVpnPermission = 501

    private val tunnel = object : Tunnel {
        override fun getName(): String = "vpn"
        override fun onStateChange(newState: Tunnel.State) {
            runOnUiThread {
                connected = newState == Tunnel.State.UP
                status.text = if (connected) "وضعیت: متصل" else "وضعیت: قطع"
                connectButton.text = if (connected) "قطع اتصال" else "اتصال"
                if (!connected) {
                    mainHandler.removeCallbacks(statsRunnable)
                    downloadSpeedView.text = "سرعت دانلود: 0 B/s"
                    uploadSpeedView.text = "سرعت آپلود: 0 B/s"
                }
            }
        }
    }

    private val statsRunnable: Runnable = object : Runnable {
        override fun run() {
            if (!connected) return
            executor.execute {
                try {
                    val stats = backend.getStatistics(tunnel)
                    val rx = stats.totalRx()
                    val tx = stats.totalTx()
                    runOnUiThread {
                        if (!connected) return@runOnUiThread
                        val rxRate = (rx - lastRxBytes).coerceAtLeast(0L)
                        val txRate = (tx - lastTxBytes).coerceAtLeast(0L)
                        lastRxBytes = rx
                        lastTxBytes = tx
                        downloadSpeedView.text = "سرعت دانلود: ${formatRate(rxRate)}"
                        uploadSpeedView.text = "سرعت آپلود: ${formatRate(txRate)}"
                        downloadedView.text = "حجم دانلود: ${formatBytes(rx)}"
                        uploadedView.text = "حجم آپلود: ${formatBytes(tx)}"
                        val elapsed = if (sessionStartedAt > 0L) {
                            ((SystemClock.elapsedRealtime() - sessionStartedAt) / 1000L).coerceAtLeast(0L)
                        } else 0L
                        durationView.text = "مدت اتصال: ${formatDuration(elapsed)}"
                        mainHandler.postDelayed(this, 1000L)
                    }
                } catch (_: Exception) {
                    runOnUiThread {
                        if (connected) mainHandler.postDelayed(this, 1000L)
                    }
                }
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
            text = "AHMADI VPN"
            textSize = 30f
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
            setOnClickListener {
                isEnabled = false
                if (connected) disconnectTunnel() else connectTunnel()
                postDelayed({ isEnabled = true }, 1200L)
            }
        }

        val statsTitle = TextView(this).apply {
            text = "آمار واقعی اتصال"
            textSize = 18f
            setTextColor(Color.rgb(91, 222, 166))
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.RIGHT
            setPadding(0, 24, 0, 10)
        }
        fun statLine(initial: String): TextView = TextView(this).apply {
            text = initial
            textSize = 15f
            setTextColor(Color.WHITE)
            gravity = Gravity.RIGHT
            setPadding(0, 5, 0, 5)
        }
        downloadSpeedView = statLine("سرعت دانلود: 0 B/s")
        uploadSpeedView = statLine("سرعت آپلود: 0 B/s")
        downloadedView = statLine("حجم دانلود: 0 B")
        uploadedView = statLine("حجم آپلود: 0 B")
        durationView = statLine("مدت اتصال: 00:00:00")

        val configLabel = TextView(this).apply {
            text = "تنظیمات سرور WireGuard"
            textSize = 16f
            setTextColor(Color.WHITE)
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.RIGHT
            setPadding(0, 24, 0, 8)
        }
        configInput = EditText(this).apply {
            textSize = 13f
            gravity = Gravity.TOP or Gravity.START
            minLines = 8
            setSingleLine(false)
            setTextColor(Color.WHITE)
            setHintTextColor(Color.LTGRAY)
            setPadding(12, 12, 12, 12)
            setBackgroundColor(Color.rgb(23, 38, 65))
            hint = "Paste WireGuard config here"
        }
        val note = TextView(this).apply {
            text = "برای اتصال واقعی، پیکربندی معتبر سرور WireGuard لازم است. آمار از خود تونل WireGuard خوانده می‌شود؛ سرعت، میانگین تقریبی در فاصله به‌روزرسانی یک‌ثانیه‌ای است. کلید خصوصی خود را با کسی شریک نکن."
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
        content.addView(statsTitle)
        content.addView(downloadSpeedView)
        content.addView(uploadSpeedView)
        content.addView(downloadedView)
        content.addView(uploadedView)
        content.addView(durationView)
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
                val initialStats = backend.getStatistics(tunnel)
                runOnUiThread {
                    connected = true
                    sessionStartedAt = SystemClock.elapsedRealtime()
                    lastRxBytes = 0L
                    lastTxBytes = 0L
                    status.text = "وضعیت: متصل"
                    connectButton.text = "قطع اتصال"
                    // Use the backend counters as the initial baseline; they are actual tunnel bytes.
                    downloadedView.text = "حجم دانلود: ${formatBytes(initialStats.totalRx())}"
                    uploadedView.text = "حجم آپلود: ${formatBytes(initialStats.totalTx())}"
                    lastRxBytes = initialStats.totalRx()
                    lastTxBytes = initialStats.totalTx()
                    downloadSpeedView.text = "سرعت دانلود: 0 B/s"
                    uploadSpeedView.text = "سرعت آپلود: 0 B/s"
                    mainHandler.removeCallbacks(statsRunnable)
                    mainHandler.post(statsRunnable)
                }
            } catch (_: Exception) {
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
        mainHandler.removeCallbacks(statsRunnable)
        executor.execute {
            try {
                backend.setState(tunnel, Tunnel.State.DOWN, null)
            } catch (_: Exception) {
                // The tunnel may already be down.
            }
            runOnUiThread {
                connected = false
                status.text = "وضعیت: قطع"
                connectButton.text = "اتصال"
                downloadSpeedView.text = "سرعت دانلود: 0 B/s"
                uploadSpeedView.text = "سرعت آپلود: 0 B/s"
            }
        }
    }

    private fun formatBytes(bytes: Long): String {
        if (bytes < 1024L) return "$bytes B"
        val units = arrayOf("KB", "MB", "GB", "TB")
        var value = bytes.toDouble()
        var unit = -1
        do {
            value /= 1024.0
            unit++
        } while (value >= 1024.0 && unit < units.lastIndex)
        return String.format(Locale.US, "%.2f %s", value, units[unit])
    }

    private fun formatRate(bytesPerSecond: Long): String = "${formatBytes(bytesPerSecond)}/s"

    private fun formatDuration(seconds: Long): String {
        val hours = seconds / 3600L
        val minutes = (seconds % 3600L) / 60L
        val secs = seconds % 60L
        return String.format(Locale.US, "%02d:%02d:%02d", hours, minutes, secs)
    }

    override fun onDestroy() {
        mainHandler.removeCallbacks(statsRunnable)
        executor.shutdownNow()
        super.onDestroy()
    }
}
