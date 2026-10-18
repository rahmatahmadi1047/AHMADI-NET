package com.ahmadi.customer

import android.app.Activity
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale

class CustomerActivity : Activity() {
    private val navy = Color.rgb(18, 42, 77)
    private val blue = Color.rgb(28, 105, 190)
    private val muted = Color.rgb(95, 108, 125)
    private val baseUrl = "https://my-ahmadinet.workers.dev"

    private var sessionToken: String? = null
    private lateinit var usernameInput: EditText
    private lateinit var passwordInput: EditText
    private lateinit var checkButton: Button
    private lateinit var resultView: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = navy
        window.navigationBarColor = navy
        window.decorView.layoutDirection = View.LAYOUT_DIRECTION_RTL

        val page = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.TOP
            setPadding(dp(24), dp(28), dp(24), dp(24))
            setBackgroundColor(Color.rgb(246, 248, 252))
        }

        val title = TextView(this).apply {
            text = "احمدی‌نت"
            textSize = 30f
            setTextColor(navy)
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
        }
        page.addView(title, matchWrap())

        val subtitle = TextView(this).apply {
            text = "مشاهده آنلاین حساب اینترنت"
            textSize = 17f
            setTextColor(muted)
            gravity = Gravity.CENTER
            setPadding(0, dp(8), 0, dp(24))
        }
        page.addView(subtitle, matchWrap())

        page.addView(label("نام کاربری Hotspot"), matchWrap())
        usernameInput = EditText(this).apply {
            hint = "نام کاربری اینترنت"
            textSize = 16f
            isSingleLine = true
            setPadding(dp(14), dp(10), dp(14), dp(10))
            layoutDirection = View.LAYOUT_DIRECTION_LTR
            setBackgroundColor(Color.WHITE)
        }
        page.addView(usernameInput, inputParams())

        page.addView(label("رمز اینترنت"), matchWrap())
        passwordInput = EditText(this).apply {
            hint = "رمز حساب Hotspot"
            textSize = 16f
            isSingleLine = true
            inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
            setPadding(dp(14), dp(10), dp(14), dp(10))
            layoutDirection = View.LAYOUT_DIRECTION_LTR
            setBackgroundColor(Color.WHITE)
        }
        page.addView(passwordInput, inputParams())

        checkButton = Button(this).apply {
            text = "ورود و بررسی حساب"
            textSize = 16f
            setTextColor(Color.WHITE)
            setBackgroundColor(blue)
        }
        page.addView(checkButton, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, dp(52)
        ))

        resultView = TextView(this).apply {
            text = "برای مشاهده اطلاعات واقعی، با نام کاربری و رمز Hotspot خود وارد شوید."
            textSize = 15f
            setTextColor(navy)
            setPadding(dp(16), dp(16), dp(16), dp(16))
            setBackgroundColor(Color.rgb(232, 240, 250))
            gravity = Gravity.CENTER
        }
        page.addView(resultView, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = dp(18) })

        val adSlot = TextView(this).apply {
            text = "تبلیغات احمدی‌نت"
            textSize = 13f
            setTextColor(muted)
            gravity = Gravity.CENTER
            setPadding(dp(10), dp(14), dp(10), dp(14))
            setBackgroundColor(Color.rgb(238, 240, 244))
        }
        page.addView(adSlot, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = dp(18) })

        val footer = TextView(this).apply {
            text = "پشتیبانی: 0700985955"
            textSize = 13f
            setTextColor(muted)
            gravity = Gravity.CENTER
            setPadding(0, dp(20), 0, 0)
        }
        page.addView(footer, matchWrap())

        checkButton.setOnClickListener {
            val token = sessionToken
            if (token != null) {
                refreshAccount(token)
            } else {
                val username = usernameInput.text.toString().trim()
                val password = passwordInput.text.toString()
                if (username.isBlank() || password.isBlank()) {
                    resultView.text = "نام کاربری و رمز اینترنت را وارد کنید."
                } else {
                    login(username, password)
                }
            }
        }

        val scroll = ScrollView(this).apply {
            isFillViewport = true
            addView(page)
        }
        setContentView(scroll)
    }

    private fun login(username: String, password: String) {
        setBusy(true, "در حال دریافت اطلاعات واقعی حساب...")
        Thread {
            try {
                val body = JSONObject().put("username", username).put("password", password)
                val response = apiCall("/api/login", "POST", body, null)
                val token = response.optString("token")
                val account = response.optJSONObject("account")
                    ?: throw ApiException("سرور اطلاعات حساب را برنگرداند.")
                if (token.isBlank()) throw ApiException("نشست امن از سرور دریافت نشد.")
                runOnUiThread {
                    sessionToken = token
                    passwordInput.text.clear()
                    checkButton.text = "تازه‌سازی حساب"
                    showAccount(account)
                    setBusy(false, null)
                }
            } catch (error: Exception) {
                runOnUiThread {
                    if (error is ApiException && error.httpCode == 401) {
                        sessionToken = null
                        checkButton.text = "ورود و بررسی حساب"
                    }
                    setBusy(false, error.message ?: "اتصال برقرار نشد.")
                }
            }
        }.start()
    }

    private fun refreshAccount(token: String) {
        setBusy(true, "در حال تازه‌سازی اطلاعات...")
        Thread {
            try {
                val response = apiCall("/api/account", "GET", null, token)
                val account = response.optJSONObject("account")
                    ?: throw ApiException("سرور اطلاعات حساب را برنگرداند.")
                runOnUiThread {
                    showAccount(account)
                    setBusy(false, null)
                }
            } catch (error: Exception) {
                runOnUiThread {
                    if (error is ApiException && error.httpCode == 401) {
                        sessionToken = null
                        checkButton.text = "ورود و بررسی حساب"
                    }
                    setBusy(false, error.message ?: "اتصال برقرار نشد.")
                }
            }
        }.start()
    }

    private fun apiCall(path: String, method: String, body: JSONObject?, token: String?): JSONObject {
        val connection = (URL(baseUrl + path).openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = 12000
            readTimeout = 15000
            setRequestProperty("Accept", "application/json")
            if (body != null) {
                doOutput = true
                setRequestProperty("Content-Type", "application/json; charset=utf-8")
            }
            if (!token.isNullOrBlank()) setRequestProperty("Authorization", "Bearer $token")
        }
        try {
            if (body != null) connection.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
            val status = connection.responseCode
            val stream = if (status in 200..299) connection.inputStream else connection.errorStream
            val text = stream?.bufferedReader(Charsets.UTF_8)?.use(BufferedReader::readText).orEmpty()
            val json = try { JSONObject(text) } catch (_: Exception) { JSONObject() }
            if (status !in 200..299 || !json.optBoolean("ok", false)) {
                throw ApiException(json.optString("error").ifBlank { "خطای سرور ($status)" }, status)
            }
            return json
        } finally {
            connection.disconnect()
        }
    }

    private fun showAccount(account: JSONObject) {
        val connected = account.optBoolean("connected", false)
        val status = if (connected) "متصل به اینترنت" else "حساب فعال؛ اکنون متصل نیست"
        val download = formatBytes(account.optLong("downloadBytes", 0))
        val upload = formatBytes(account.optLong("uploadBytes", 0))
        val used = formatBytes(account.optLong("usedBytes", 0))
        val lines = StringBuilder()
            .append("نام کاربری: ").append(account.optString("username", "—")).append("\n")
            .append("وضعیت: ").append(status).append("\n")
            .append("دانلود مصرف‌شده: ").append(download).append("\n")
            .append("آپلود مصرف‌شده: ").append(upload).append("\n")
            .append("مجموع مصرف: ").append(used).append("\n")
            .append("مدت اتصال: ").append(account.optString("uptime", "—"))
        if (!account.isNull("totalLimitBytes")) {
            lines.append("\nحجم کل بسته: ").append(formatBytes(account.optLong("totalLimitBytes")))
            if (!account.isNull("remainingBytes")) {
                lines.append("\nحجم باقی‌مانده: ").append(formatBytes(account.optLong("remainingBytes")))
            }
        } else {
            lines.append("\nحجم باقی‌مانده: برای این حساب محدودیت کل حجم تنظیم نشده است.")
        }
        val profile = account.optString("profile", "")
        if (profile.isNotBlank()) lines.append("\nپروفایل: ").append(profile)
        resultView.text = lines.toString()
    }

    private fun formatBytes(value: Long): String {
        val bytes = value.coerceAtLeast(0L).toDouble()
        if (bytes < 1024) return String.format(Locale.US, "%.0f B", bytes)
        val units = arrayOf("KB", "MB", "GB", "TB")
        var amount = bytes / 1024.0
        var index = 0
        while (amount >= 1024 && index < units.lastIndex) {
            amount /= 1024.0
            index++
        }
        return String.format(Locale.US, "%.2f %s", amount, units[index])
    }

    private fun setBusy(busy: Boolean, message: String?) {
        checkButton.isEnabled = !busy
        checkButton.alpha = if (busy) 0.65f else 1f
        if (message != null) resultView.text = message
    }

    private fun label(value: String) = TextView(this).apply {
        text = value
        textSize = 16f
        setTextColor(navy)
    }

    private fun inputParams() = LinearLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT, dp(54)
    ).apply { topMargin = dp(8); bottomMargin = dp(14) }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private fun matchWrap() = LinearLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
    )

    private class ApiException(message: String, val httpCode: Int = 0) : Exception(message)
}
