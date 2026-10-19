package com.ahmadi.vpn

import android.app.Activity
import android.content.Intent
import android.net.VpnService
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.*
import android.graphics.Color
import android.graphics.Typeface
import com.wireguard.android.backend.GoBackend
import com.wireguard.android.backend.Tunnel
import com.wireguard.config.Config
import java.io.BufferedReader
import java.io.StringReader
import java.util.concurrent.Executors

class MainActivity : Activity() {
    private lateinit var status: TextView
    private lateinit var configInfo: TextView
    private lateinit var connectButton: Button
    private var configText: String? = null
    private var connected = false
    private val executor = Executors.newSingleThreadExecutor()
    private lateinit var backend: GoBackend

    private val tunnel = object : Tunnel {
        override fun getName(): String = "AHMADI-VPN"
        override fun onStateChange(newState: Tunnel.State) {
            runOnUiThread {
                connected = newState == Tunnel.State.UP
                updateUi(if (connected) "متصل (WireGuard)" else "قطع")
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        backend = GoBackend(applicationContext)
        window.statusBarColor = Color.rgb(9, 23, 45)
        window.navigationBarColor = Color.rgb(9, 23, 45)
        window.decorView.layoutDirection = View.LAYOUT_DIRECTION_RTL

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 28, 24, 24)
            setBackgroundColor(Color.rgb(9, 23, 45))
        }
        root.addView(TextView(this).apply {
            text = "AHMADI VPN"
            textSize = 28f
            setTextColor(Color.WHITE)
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
        })
        root.addView(TextView(this).apply {
            text = "اتصال مستقیم WireGuard داخل برنامه"
            textSize = 15f
            setTextColor(Color.rgb(185, 204, 230))
            gravity = Gravity.CENTER
            setPadding(0, 8, 0, 22)
        })
        status = TextView(this).apply {
            text = "وضعیت: قطع"
            textSize = 16f
            setTextColor(Color.WHITE)
            setPadding(14, 16, 14, 16)
            setBackgroundColor(Color.rgb(24, 43, 69))
        }
        root.addView(status, LinearLayout.LayoutParams(-1, -2))
        configInfo = TextView(this).apply {
            text = "اول فایل WireGuard با پسوند .conf را وارد کن."
            textSize = 14f
            setTextColor(Color.rgb(202, 216, 235))
            setPadding(4, 18, 4, 18)
        }
        root.addView(configInfo)
        addButton(root, "انتخاب فایل WireGuard (.conf)", Color.rgb(0, 133, 112)) { chooseFile() }
        addButton(root, "چسپاندن متن کانفیگ WireGuard", Color.rgb(40, 94, 160)) { pasteConfig() }
        connectButton = addButton(root, "اتصال VPN", Color.rgb(0, 133, 112)) { toggleConnection() }
        root.addView(TextView(this).apply {
            text = "موتور WireGuard داخل برنامه است. برای اتصال به کانفیگ معتبر WireGuard نیاز داری؛ فایل‌های OpenVPN (.ovpn) و لینک‌های VLESS/Xray در این نسخه هنوز پشتیبانی مستقیم ندارند."
            textSize = 12f
            setTextColor(Color.rgb(255, 211, 130))
            setPadding(4, 18, 4, 0)
        })
        setContentView(ScrollView(this).apply { addView(root) })
    }

    private fun addButton(parent: LinearLayout, label: String, color: Int, action: () -> Unit): Button {
        val button = Button(this).apply {
            text = label
            isAllCaps = false
            textSize = 15f
            setTextColor(Color.WHITE)
            setBackgroundColor(color)
            setOnClickListener { action() }
        }
        parent.addView(button, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = 10 })
        return button
    }

    private fun chooseFile() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "text/*"
        }
        startActivityForResult(intent, 1001)
    }

    @Deprecated("Legacy activity result used for compatibility")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 1001 && resultCode == RESULT_OK) {
            val uri = data?.data ?: return
            try {
                val text = contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                    ?: throw IllegalArgumentException("فایل خوانده نشد")
                setConfig(text)
            } catch (e: Exception) {
                showError("خواندن فایل ناموفق بود: ${e.message ?: "خطا"}")
            }
        } else if (requestCode == 1002) {
            if (resultCode == RESULT_OK) startTunnel() else showError("اجازهٔ اتصال VPN داده نشد")
        }
    }

    private fun pasteConfig() {
        val input = EditText(this).apply {
            hint = "[Interface] و [Peer] کانفیگ WireGuard را اینجا بچسپان"
            setTextColor(Color.BLACK)
            textSize = 13f
            minLines = 8
            gravity = Gravity.TOP
        }
        android.app.AlertDialog.Builder(this)
            .setTitle("کانفیگ WireGuard")
            .setView(input)
            .setNegativeButton("لغو", null)
            .setPositiveButton("ذخیره") { _, _ -> setConfig(input.text.toString()) }
            .show()
    }

    private fun setConfig(text: String) {
        try {
            if (!text.contains("[Interface]", ignoreCase = true) ||
                !text.contains("[Peer]", ignoreCase = true)) {
                throw IllegalArgumentException("این فایل کانفیگ WireGuard نیست؛ باید [Interface] و [Peer] داشته باشد.")
            }
            // Parse before accepting the config, so malformed files are rejected early.
            Config.parse(BufferedReader(StringReader(text)))
            configText = text
            configInfo.text = "کانفیگ WireGuard معتبر از نظر قالب وارد شد. سرور و کلیدها هنگام اتصال بررسی می‌شوند."
            status.text = "وضعیت: کانفیگ آماده"
            Toast.makeText(this, "کانفیگ وارد شد", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            showError("کانفیگ معتبر نیست: ${e.message ?: "خطای قالب"}")
        }
    }

    private fun toggleConnection() {
        if (connected) {
            status.text = "وضعیت: در حال قطع..."
            executor.execute {
                try {
                    backend.setState(tunnel, Tunnel.State.DOWN, null)
                    runOnUiThread { updateUi("قطع") }
                } catch (e: Exception) {
                    runOnUiThread { showError("قطع اتصال ناموفق بود: ${e.message ?: "خطا"}") }
                }
            }
            return
        }
        if (configText.isNullOrBlank()) {
            showError("اول کانفیگ WireGuard را وارد کن")
            return
        }
        val permissionIntent = VpnService.prepare(this)
        if (permissionIntent != null) startActivityForResult(permissionIntent, 1002) else startTunnel()
    }

    private fun startTunnel() {
        val raw = configText ?: return
        status.text = "وضعیت: در حال اتصال..."
        connectButton.isEnabled = false
        executor.execute {
            try {
                val config = Config.parse(BufferedReader(StringReader(raw)))
                backend.setState(tunnel, Tunnel.State.UP, config)
                runOnUiThread { updateUi("درخواست اتصال فرستاده شد") }
            } catch (e: Exception) {
                runOnUiThread {
                    connected = false
                    updateUi("خطای اتصال")
                    showError("اتصال ناموفق بود: ${e.message ?: "سرور یا کانفیگ را بررسی کن"}")
                }
            }
        }
    }

    private fun updateUi(message: String) {
        status.text = "وضعیت: $message"
        connectButton.text = if (connected) "قطع VPN" else "اتصال VPN"
        connectButton.isEnabled = true
    }

    private fun showError(message: String) {
        android.app.AlertDialog.Builder(this)
            .setTitle("AHMADI VPN")
            .setMessage(message)
            .setPositiveButton("باشه", null)
            .show()
    }

    override fun onDestroy() {
        super.onDestroy()
        executor.shutdown()
    }
}
