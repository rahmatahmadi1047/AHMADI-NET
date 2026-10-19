package com.ahmadi.vpn

import android.app.Activity
import android.content.Intent
import android.net.VpnService
import android.net.Uri
import android.os.Bundle
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.widget.*
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
    @Volatile private var connected = false
    private val executor = Executors.newSingleThreadExecutor()
    private lateinit var backend: GoBackend

    private val tunnel = object : Tunnel {
        override fun getName(): String = "AHMADI-VPN"
        override fun onStateChange(newState: Tunnel.State) {
            runOnUiThread {
                connected = newState == Tunnel.State.UP
                updateUi(if (connected) "متصل هستید" else "اتصال قطع است")
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        backend = GoBackend(applicationContext)
        window.statusBarColor = Color.rgb(8, 16, 34)
        window.navigationBarColor = Color.rgb(8, 16, 34)
        window.decorView.layoutDirection = View.LAYOUT_DIRECTION_RTL

        val navy = Color.rgb(8, 16, 34)
        val panel = Color.rgb(19, 31, 56)
        val mint = Color.rgb(40, 220, 174)
        val muted = Color.rgb(164, 181, 208)
        val telegramBlue = Color.rgb(42, 171, 238)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(24), dp(28), dp(24), dp(24))
            setBackgroundColor(navy)
        }

        val topSpace = View(this)
        root.addView(topSpace, LinearLayout.LayoutParams(1, dp(12)))

        // Custom AHMADI VPN badge: brand letter plus VPN shield-style check mark.
        val logo = TextView(this).apply {
            text = "A✓"
            textSize = 30f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setTextColor(navy)
            background = rounded(mint, 24)
            elevation = dp(5).toFloat()
            contentDescription = "لوگوی AHMADI VPN"
        }
        root.addView(logo, LinearLayout.LayoutParams(dp(82), dp(82)))

        root.addView(TextView(this).apply {
            text = "AHMADI VPN"
            textSize = 27f
            typeface = Typeface.DEFAULT_BOLD
            letterSpacing = 0.08f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            setPadding(0, dp(18), 0, dp(4))
        })

        root.addView(TextView(this).apply {
            text = "اتصال با WireGuard"
            textSize = 15f
            setTextColor(muted)
            gravity = Gravity.CENTER
        })

        val spacer = View(this)
        root.addView(spacer, LinearLayout.LayoutParams(1, dp(28)))

        val statusCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            background = rounded(panel, 22)
            setPadding(dp(18), dp(22), dp(18), dp(22))
        }
        status = TextView(this).apply {
            text = "●  اتصال قطع است"
            textSize = 18f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setTextColor(Color.rgb(255, 190, 100))
        }
        statusCard.addView(status)
        statusCard.addView(TextView(this).apply {
            text = "برای شروع، کانفیگ WireGuard خود را وارد کنید"
            textSize = 12f
            setTextColor(muted)
            gravity = Gravity.CENTER
            setPadding(0, dp(10), 0, 0)
        })
        root.addView(statusCard, LinearLayout.LayoutParams(-1, -2))

        val configPanel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = rounded(Color.rgb(14, 25, 47), 18)
            setPadding(dp(18), dp(16), dp(18), dp(16))
            isClickable = true
            isFocusable = true
            setOnClickListener { showConfigOptions() }
        }
        configPanel.addView(TextView(this).apply {
            text = "کانفیگ WireGuard  ›"
            textSize = 15f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.WHITE)
        })
        configInfo = TextView(this).apply {
            text = "برای واردکردن فایل .conf اینجا بزنید"
            textSize = 12f
            setTextColor(muted)
            setPadding(0, dp(8), 0, 0)
        }
        configPanel.addView(configInfo)
        root.addView(configPanel, LinearLayout.LayoutParams(-1, -2).apply {
            topMargin = dp(18)
        })

        connectButton = Button(this).apply {
            text = "اتصال VPN"
            textSize = 18f
            isAllCaps = false
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(navy)
            background = rounded(mint, 18)
            setPadding(dp(12), dp(10), dp(12), dp(10))
            setOnClickListener { toggleConnection() }
        }
        root.addView(connectButton, LinearLayout.LayoutParams(-1, dp(62)).apply {
            topMargin = dp(22)
        })

        // Prominent, clickable Telegram invitation for the user's channel.
        val telegramCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            background = rounded(Color.rgb(16, 38, 62), 20)
            setPadding(dp(16), dp(16), dp(16), dp(16))
            isClickable = true
            isFocusable = true
            contentDescription = "بازکردن کانال تلگرام احمدی"
            setOnClickListener { openTelegram() }
        }
        telegramCard.addView(TextView(this).apply {
            text = "✈  کانال تلگرام احمدی"
            textSize = 17f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(telegramBlue)
            gravity = Gravity.CENTER
        })
        telegramCard.addView(TextView(this).apply {
            text = "برای خبرها و به‌روزرسانی‌ها عضو شوید"
            textSize = 12f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            setPadding(0, dp(7), 0, dp(5))
        })
        telegramCard.addView(TextView(this).apply {
            text = "t.me/ahmadi_up"
            textSize = 13f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(muted)
            gravity = Gravity.CENTER
        })
        root.addView(telegramCard, LinearLayout.LayoutParams(-1, -2).apply {
            topMargin = dp(22)
        })

        root.addView(TextView(this).apply {
            text = "فقط WireGuard  •  ساده و کاربردی"
            textSize = 12f
            setTextColor(muted)
            gravity = Gravity.CENTER
            setPadding(0, dp(20), 0, 0)
        })

        setContentView(ScrollView(this).apply {
            isFillViewport = true
            setBackgroundColor(navy)
            addView(root)
        })
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private fun rounded(color: Int, radiusDp: Int): GradientDrawable =
        GradientDrawable().apply {
            setColor(color)
            cornerRadius = dp(radiusDp).toFloat()
        }

    private fun openTelegram() {
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://t.me/ahmadi_up")))
        } catch (e: Exception) {
            showError("بازکردن تلگرام ممکن نشد. این لینک را باز کنید: https://t.me/ahmadi_up")
        }
    }

    private fun showConfigOptions() {
        val options = arrayOf("انتخاب فایل کانفیگ (.conf)", "چسپاندن متن کانفیگ")
        android.app.AlertDialog.Builder(this)
            .setTitle("واردکردن WireGuard")
            .setItems(options) { _, which ->
                if (which == 0) chooseFile() else pasteConfig()
            }
            .setNegativeButton("لغو", null)
            .show()
    }

    private fun chooseFile() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*"
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
            if (resultCode == RESULT_OK) startTunnel()
            else showError("اجازه اتصال VPN داده نشد")
        }
    }

    private fun pasteConfig() {
        val input = EditText(this).apply {
            hint = "[Interface] و [Peer] کانفیگ را اینجا بچسپانید"
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
                throw IllegalArgumentException("فایل باید بخش‌های [Interface] و [Peer] داشته باشد.")
            }
            Config.parse(BufferedReader(StringReader(text)))
            configText = text
            configInfo.text = "کانفیگ وارد شده است و آماده اتصال می‌باشد."
            status.text = if (connected) "●  متصل هستید" else "●  کانفیگ آماده است"
            status.setTextColor(if (connected) Color.rgb(40, 220, 174) else Color.rgb(255, 190, 100))
            Toast.makeText(this, "کانفیگ WireGuard ذخیره شد", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            showError("کانفیگ معتبر نیست: ${e.message ?: "خطای قالب"}")
        }
    }

    private fun toggleConnection() {
        if (connected) {
            status.text = "●  در حال قطع اتصال..."
            executor.execute {
                try {
                    backend.setState(tunnel, Tunnel.State.DOWN, null)
                    runOnUiThread { updateUi("اتصال قطع است") }
                } catch (e: Exception) {
                    runOnUiThread { showError("قطع اتصال ناموفق بود: ${e.message ?: "خطا"}") }
                }
            }
            return
        }
        if (configText.isNullOrBlank()) {
            showError("ابتدا از بخش کانفیگ، فایل WireGuard را وارد کنید.")
            return
        }
        val permissionIntent = VpnService.prepare(this)
        if (permissionIntent != null) startActivityForResult(permissionIntent, 1002) else startTunnel()
    }

    private fun startTunnel() {
        val raw = configText ?: return
        status.text = "●  در حال اتصال..."
        connectButton.isEnabled = false
        executor.execute {
            try {
                val config = Config.parse(BufferedReader(StringReader(raw)))
                backend.setState(tunnel, Tunnel.State.UP, config)
                runOnUiThread { updateUi("درخواست اتصال ارسال شد") }
            } catch (e: Exception) {
                runOnUiThread {
                    connected = false
                    updateUi("اتصال قطع است")
                    showError("اتصال ناموفق بود: ${e.message ?: "سرور یا کانفیگ را بررسی کنید"}")
                }
            }
        }
    }

    private fun updateUi(message: String) {
        status.text = if (connected) "●  متصل هستید" else "●  $message"
        status.setTextColor(if (connected) Color.rgb(40, 220, 174) else Color.rgb(255, 190, 100))
        connectButton.text = if (connected) "قطع اتصال VPN" else "اتصال VPN"
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
