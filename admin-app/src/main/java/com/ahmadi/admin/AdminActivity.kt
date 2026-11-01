package com.ahmadi.admin

import android.app.Activity
import android.app.AlertDialog
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.*
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

class AdminActivity : Activity() {
    private val baseUrl = "https://ahmadinet.rahmat-ahmadi1047.workers.dev"
    private val navy = Color.rgb(18, 35, 61)
    private val purple = Color.rgb(105, 91, 220)
    private val muted = Color.rgb(95, 108, 125)
    private var token = ""
    private var connected = false
    private lateinit var tokenInput: EditText
    private lateinit var usernameInput: EditText
    private lateinit var passwordInput: EditText
    private lateinit var profileSpinner: Spinner
    private lateinit var targetSpinner: Spinner
    private lateinit var messageView: TextView
    private lateinit var usersBox: LinearLayout
    private lateinit var connectButton: Button
    private lateinit var createButton: Button

    private val targetType: String
        get() = if (targetSpinner.selectedItemPosition == 1) "usermanager" else "hotspot"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = navy
        window.navigationBarColor = navy
        window.decorView.layoutDirection = View.LAYOUT_DIRECTION_RTL

        val page = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(22), dp(18), dp(28))
            setBackgroundColor(Color.rgb(244, 246, 251))
        }

        val title = TextView(this).apply {
            text = "AHMADI-NET MANAGER"
            textSize = 23f
            setTextColor(navy)
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
        }
        page.addView(title, matchWrap())

        val subtitle = TextView(this).apply {
            text = "مدیریت محدود کاربران روتر RB951"
            textSize = 15f
            setTextColor(muted)
            gravity = Gravity.CENTER
            setPadding(0, dp(8), 0, dp(18))
        }
        page.addView(subtitle, matchWrap())

        page.addView(label("رمز دسترسی فروشنده"), matchWrap())
        tokenInput = edit("ADMIN_TOKEN را وارد کنید", password = true)
        page.addView(tokenInput, inputParams())

        connectButton = button("اتصال به RB951")
        page.addView(connectButton, buttonParams())

        page.addView(label("نوع حساب"), matchWrap())
        targetSpinner = Spinner(this).apply {
            adapter = ArrayAdapter(this@AdminActivity, android.R.layout.simple_spinner_dropdown_item,
                listOf("Hotspot Users", "User Manager"))
        }
        page.addView(targetSpinner, inputParams())

        page.addView(label("نام کاربری جدید"), matchWrap())
        usernameInput = edit("مثلاً 930001")
        page.addView(usernameInput, inputParams())

        page.addView(label("رمز کاربر"), matchWrap())
        passwordInput = edit("رمز مشتری", password = true)
        page.addView(passwordInput, inputParams())

        page.addView(label("پروفایل موجود"), matchWrap())
        profileSpinner = Spinner(this)
        page.addView(profileSpinner, inputParams())

        createButton = button("ساخت کاربر")
        page.addView(createButton, buttonParams())

        val refreshButton = button("تازه‌سازی فهرست کاربران").apply {
            setBackgroundColor(Color.rgb(55, 75, 105))
        }
        page.addView(refreshButton, buttonParams())

        messageView = TextView(this).apply {
            textSize = 14f
            setTextColor(navy)
            setPadding(dp(12), dp(12), dp(12), dp(12))
            setBackgroundColor(Color.rgb(229, 235, 246))
            gravity = Gravity.CENTER
            text = "برای شروع، رمز دسترسی فروشنده را وارد و اتصال را بزنید."
        }
        page.addView(messageView, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = dp(14) })

        val usersTitle = TextView(this).apply {
            text = "فهرست کاربران"
            textSize = 18f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(navy)
            setPadding(0, dp(20), 0, dp(8))
        }
        page.addView(usersTitle, matchWrap())
        usersBox = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(10), dp(8), dp(10), dp(8))
            setBackgroundColor(Color.WHITE)
        }
        page.addView(usersBox, matchWrap())

        connectButton.setOnClickListener {
            token = tokenInput.text.toString().trim()
            if (token.length < 32) {
                showMessage("رمز دسترسی باید حداقل ۳۲ نویسه باشد.", false)
            } else {
                connect()
            }
        }

        targetSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onNothingSelected(parent: AdapterView<*>?) = Unit
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                if (connected) loadData()
            }
        }

        createButton.setOnClickListener { createUser() }
        refreshButton.setOnClickListener { if (connected) loadData() else showMessage("ابتدا متصل شوید.", false) }

        val scroll = ScrollView(this).apply {
            isFillViewport = true
            addView(page)
        }
        setContentView(scroll)
        setBusy(false)
    }

    private fun connect() {
        setBusy(true)
        Thread {
            try {
                api("/api/admin/profiles?type=hotspot")
                runOnUiThread {
                    connected = true
                    showMessage("اتصال موفق بود.", true)
                    setBusy(false)
                    loadData()
                }
            } catch (e: Exception) {
                runOnUiThread {
                    connected = false
                    showMessage(e.message ?: "اتصال برقرار نشد.", false)
                    setBusy(false)
                }
            }
        }.start()
    }

    private fun loadData() {
        if (!connected) return
        setBusy(true)
        val type = targetType
        Thread {
            try {
                val profiles = api("/api/admin/profiles?type=$type").optJSONArray("profiles") ?: JSONArray()
                val users = api("/api/admin/users?type=$type").optJSONArray("users") ?: JSONArray()
                runOnUiThread {
                    val names = mutableListOf<String>()
                    for (i in 0 until profiles.length()) {
                        val name = profiles.optJSONObject(i)?.optString("name").orEmpty()
                        if (name.isNotBlank()) names.add(name)
                    }
                    profileSpinner.adapter = ArrayAdapter(
                        this, android.R.layout.simple_spinner_dropdown_item, names
                    )
                    renderUsers(users, type)
                    showMessage("اطلاعات دریافت شد. تعداد کاربران: ${users.length()}", true)
                    setBusy(false)
                }
            } catch (e: Exception) {
                runOnUiThread {
                    showMessage(e.message ?: "دریافت اطلاعات انجام نشد.", false)
                    setBusy(false)
                }
            }
        }.start()
    }

    private fun createUser() {
        if (!connected) {
            showMessage("ابتدا به روتر متصل شوید.", false)
            return
        }
        val username = usernameInput.text.toString().trim()
        val password = passwordInput.text.toString()
        val profile = profileSpinner.selectedItem?.toString().orEmpty()
        if (username.isBlank() || password.isBlank() || profile.isBlank()) {
            showMessage("نام کاربری، رمز و پروفایل را کامل کنید.", false)
            return
        }
        setBusy(true)
        val type = targetType
        Thread {
            try {
                val body = JSONObject()
                    .put("type", type)
                    .put("username", username)
                    .put("password", password)
                    .put("profile", profile)
                api("/api/admin/users", "POST", body)
                runOnUiThread {
                    usernameInput.text.clear()
                    passwordInput.text.clear()
                    showMessage("کاربر $username ساخته شد.", true)
                    setBusy(false)
                    loadData()
                }
            } catch (e: Exception) {
                runOnUiThread {
                    showMessage(e.message ?: "ساخت کاربر انجام نشد.", false)
                    setBusy(false)
                }
            }
        }.start()
    }

    private fun renderUsers(users: JSONArray, type: String) {
        usersBox.removeAllViews()
        if (users.length() == 0) {
            usersBox.addView(TextView(this).apply {
                text = "کاربری ثبت نشده است."
                setTextColor(muted)
                setPadding(dp(8), dp(12), dp(8), dp(12))
            })
            return
        }
        for (i in 0 until users.length()) {
            val item = users.optJSONObject(i) ?: continue
            val username = item.optString("username")
            val detail = if (type == "hotspot") {
                "پروفایل: " + item.optString("profile", "—")
            } else {
                val assignments = item.optJSONArray("profiles") ?: JSONArray()
                val names = (0 until assignments.length()).mapNotNull { index ->
                    assignments.optJSONObject(index)?.optString("profile")
                }
                "پروفایل: " + (names.joinToString("، ").ifBlank { "بدون پروفایل" })
            }
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(6), dp(10), dp(6), dp(10))
            }
            val info = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
            info.addView(TextView(this).apply {
                text = username
                textSize = 16f
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(navy)
            }, matchWrap())
            info.addView(TextView(this).apply {
                text = detail
                textSize = 12f
                setTextColor(muted)
            }, matchWrap())
            row.addView(info, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            val delete = button("حذف").apply {
                setBackgroundColor(Color.rgb(178, 55, 69))
                textSize = 13f
            }
            row.addView(delete, LinearLayout.LayoutParams(dp(76), dp(42)))
            delete.setOnClickListener {
                AlertDialog.Builder(this)
                    .setTitle("حذف کاربر")
                    .setMessage("آیا کاربر $username حذف شود؟ این عملیات برگشت‌پذیر نیست.")
                    .setNegativeButton("انصراف", null)
                    .setPositiveButton("حذف") { _, _ -> deleteUser(username) }
                    .show()
            }
            usersBox.addView(row, matchWrap())
            val divider = View(this).apply { setBackgroundColor(Color.rgb(225, 230, 239)) }
            usersBox.addView(divider, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(1)
            ))
        }
    }

    private fun deleteUser(username: String) {
        setBusy(true)
        val type = targetType
        Thread {
            try {
                val encoded = URLEncoder.encode(username, "UTF-8")
                api("/api/admin/users?type=$type&username=$encoded", "DELETE")
                runOnUiThread {
                    showMessage("کاربر $username حذف شد.", true)
                    setBusy(false)
                    loadData()
                }
            } catch (e: Exception) {
                runOnUiThread {
                    showMessage(e.message ?: "حذف کاربر انجام نشد.", false)
                    setBusy(false)
                }
            }
        }.start()
    }

    private fun api(path: String, method: String = "GET", body: JSONObject? = null): JSONObject {
        val connection = (URL(baseUrl + path).openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = 12000
            readTimeout = 18000
            setRequestProperty("Accept", "application/json")
            setRequestProperty("Authorization", "Bearer $token")
            if (body != null) {
                doOutput = true
                setRequestProperty("Content-Type", "application/json; charset=utf-8")
            }
        }
        try {
            if (body != null) connection.outputStream.use {
                it.write(body.toString().toByteArray(Charsets.UTF_8))
            }
            val status = connection.responseCode
            val stream = if (status in 200..299) connection.inputStream else connection.errorStream
            val raw = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
            val result = try { JSONObject(raw) } catch (_: Exception) {
                throw IOException("پاسخ سرور قابل خواندن نیست.")
            }
            if (status !in 200..299 || !result.optBoolean("ok")) {
                throw IOException(result.optString("error").ifBlank { "درخواست ناموفق بود (HTTP $status)." })
            }
            return result
        } finally {
            connection.disconnect()
        }
    }

    private fun showMessage(text: String, success: Boolean) {
        messageView.text = text
        messageView.setBackgroundColor(
            if (success) Color.rgb(218, 242, 230) else Color.rgb(250, 226, 228)
        )
        messageView.setTextColor(if (success) Color.rgb(20, 100, 65) else Color.rgb(150, 35, 48))
    }

    private fun setBusy(busy: Boolean) {
        connectButton.isEnabled = !busy
        createButton.isEnabled = !busy
        targetSpinner.isEnabled = !busy
    }

    private fun label(text: String) = TextView(this).apply {
        this.text = text
        textSize = 14f
        setTextColor(navy)
        typeface = Typeface.DEFAULT_BOLD
        setPadding(0, dp(12), 0, dp(5))
    }

    private fun edit(hintText: String, password: Boolean = false) = EditText(this).apply {
        hint = hintText
        textSize = 15f
        isSingleLine = true
        inputType = if (password) InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        else InputType.TYPE_CLASS_TEXT
        layoutDirection = View.LAYOUT_DIRECTION_LTR
        setPadding(dp(12), dp(9), dp(12), dp(9))
        setBackgroundColor(Color.WHITE)
    }

    private fun button(text: String) = Button(this).apply {
        this.text = text
        textSize = 15f
        setTextColor(Color.WHITE)
        setBackgroundColor(purple)
    }

    private fun matchWrap() = LinearLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
    )

    private fun inputParams() = LinearLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT, dp(50)
    ).apply { bottomMargin = dp(4) }

    private fun buttonParams() = LinearLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT, dp(50)
    ).apply { topMargin = dp(9); bottomMargin = dp(4) }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}
