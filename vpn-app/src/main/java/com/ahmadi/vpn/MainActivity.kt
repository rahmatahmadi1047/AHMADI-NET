package com.ahmadi.vpn

import android.app.Activity
import android.os.Bundle
import android.content.Intent
import android.net.Uri
import android.view.Gravity
import android.view.View
import android.widget.*
import android.graphics.Color
import android.graphics.Typeface

class MainActivity : Activity() {
    private lateinit var status: TextView
    private lateinit var details: TextView
    private var selectedUri: Uri? = null
    private var selectedType = "نامشخص"
    private val pickFile = 1001

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Color.rgb(9, 23, 45)
        window.navigationBarColor = Color.rgb(9, 23, 45)
        window.decorView.layoutDirection = View.LAYOUT_DIRECTION_RTL

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 28, 24, 24)
            setBackgroundColor(Color.rgb(9, 23, 45))
        }
        root.addView(TextView(this).apply {
            text = "AHMADI VPN"; textSize = 28f; setTextColor(Color.WHITE)
            typeface = Typeface.DEFAULT_BOLD; gravity = Gravity.CENTER
        })
        root.addView(TextView(this).apply {
            text = "مدیریت کانفیگ‌های VPN"; textSize = 15f
            setTextColor(Color.rgb(185, 204, 230)); gravity = Gravity.CENTER
            setPadding(0, 8, 0, 22)
        })
        status = TextView(this).apply {
            text = "وضعیت: هنوز کانفیگی انتخاب نشده"; textSize = 16f
            setTextColor(Color.WHITE); setPadding(14, 16, 14, 16)
            setBackgroundColor(Color.rgb(24, 43, 69))
        }
        root.addView(status, LinearLayout.LayoutParams(-1, -2))
        details = TextView(this).apply {
            text = "فرمت‌ها: OpenVPN (.ovpn)، WireGuard (.conf)، VLESS/V2Ray، Trojan و Shadowsocks"
            textSize = 14f; setTextColor(Color.rgb(202, 216, 235)); setPadding(4, 18, 4, 18)
        }
        root.addView(details)
        addButton(root, "انتخاب فایل کانفیگ", Color.rgb(0, 133, 112)) { chooseFile() }
        addButton(root, "واردکردن لینک کانفیگ", Color.rgb(40, 94, 160)) { pasteLink() }
        addButton(root, "بازکردن با برنامه VPN سازگار", Color.rgb(75, 83, 110)) { handoff() }
        addButton(root, "راهنمای اتصال", Color.rgb(75, 83, 110)) { showEngines() }
        root.addView(TextView(this).apply {
            text = "توجه: این نسخه کانفیگ را به برنامهٔ موتور همان پروتکل می‌سپارد. موتورهای اتصال هنوز داخل خود AHMADI VPN ادغام نشده‌اند؛ برای اتصال باید کلاینت سازگار نصب باشد."
            textSize = 12f; setTextColor(Color.rgb(255, 211, 130)); setPadding(4, 18, 4, 0)
        })
        setContentView(ScrollView(this).apply { addView(root) })
    }

    private fun addButton(parent: LinearLayout, label: String, color: Int, action: () -> Unit) {
        val b = Button(this).apply {
            text = label; isAllCaps = false; textSize = 15f
            setTextColor(Color.WHITE); setBackgroundColor(color); setOnClickListener { action() }
        }
        parent.addView(b, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = 10 })
    }

    private fun chooseFile() {
        val i = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE); type = "*/*"
        }
        startActivityForResult(i, pickFile)
    }

    @Deprecated("Legacy activity result used for compatibility with minSdk 23")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == pickFile && resultCode == RESULT_OK) {
            val uri = data?.data ?: return
            selectedUri = uri
            try { contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) } catch (_: Exception) {}
            val name = uri.lastPathSegment ?: "فایل انتخاب‌شده"
            selectedType = detect(name, "")
            status.text = "کانفیگ انتخاب شد: $selectedType"
            details.text = "$name\nبرای اتصال، دکمهٔ بازکردن با برنامه سازگار را بزن."
        }
    }

    private fun detect(name: String, value: String): String {
        val s = (name + " " + value).lowercase()
        return when {
            s.contains(".ovpn") -> "OpenVPN"
            s.contains(".conf") -> "WireGuard یا کانفیگ متنی؛ محتوای فایل باید بررسی شود"
            s.contains("vless://") || s.contains("vmess://") || s.contains("trojan://") || s.contains("ss://") -> "V2Ray/Xray یا Shadowsocks"
            else -> "فرمت نامشخص؛ اتصال تضمین نیست"
        }
    }

    private fun pasteLink() {
        val input = EditText(this).apply {
            hint = "vless:// یا vmess:// یا trojan:// یا ss://"; setTextColor(Color.BLACK); textSize = 14f
        }
        android.app.AlertDialog.Builder(this).setTitle("لینک کانفیگ").setView(input)
            .setNegativeButton("لغو", null)
            .setPositiveButton("ثبت") { _, _ ->
                val value = input.text.toString().trim()
                if (value.isBlank()) return@setPositiveButton
                selectedUri = Uri.parse(value)
                selectedType = detect("", value)
                details.text = value.take(220)
                status.text = "نوع کانفیگ: $selectedType"
            }.show()
    }

    private fun handoff() {
        val uri = selectedUri
        if (uri == null) {
            Toast.makeText(this, "اول یک فایل یا لینک کانفیگ انتخاب کن", Toast.LENGTH_LONG).show()
            return
        }
        val isLink = uri.scheme?.lowercase() in listOf("vless", "vmess", "trojan", "ss")
        val intent = if (isLink) Intent(Intent.ACTION_VIEW, uri)
                     else Intent(Intent.ACTION_VIEW).setDataAndType(uri, "*/*")
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        try { startActivity(Intent.createChooser(intent, "انتخاب برنامهٔ VPN برای $selectedType")) }
        catch (_: Exception) {
            Toast.makeText(this, "برنامهٔ سازگار پیدا نشد؛ موتور مناسب را نصب کن.", Toast.LENGTH_LONG).show()
        }
    }

    private fun showEngines() {
        android.app.AlertDialog.Builder(this).setTitle("برنامه‌های موردنیاز")
            .setMessage("OpenVPN (.ovpn): OpenVPN Connect یا کلاینت سازگار\nWireGuard (.conf): برنامهٔ رسمی WireGuard\nVLESS/V2Ray: کلاینت سازگار با Xray/V2Ray مثل v2rayNG\n\nپس از نصب کلاینت، کانفیگ را به آن بسپار و داخل کلاینت روی اتصال بزن.")
            .setPositiveButton("فهمیدم", null).show()
    }
}
