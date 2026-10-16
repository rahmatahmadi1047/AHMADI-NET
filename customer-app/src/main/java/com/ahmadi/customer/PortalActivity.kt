package com.ahmadi.customer

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView

/**
 * Opens the official SAS RADIUS portal in the device browser.
 * This avoids weakening WebView TLS checks; the app never handles customer credentials.
 */
class PortalActivity : Activity() {
    private val portalUrl = "https://169.58.110.86/user/#/login"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Color.rgb(10, 54, 50)
        window.navigationBarColor = Color.rgb(10, 54, 50)
        window.decorView.layoutDirection = View.LAYOUT_DIRECTION_RTL

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(28, 28, 28, 28)
            setBackgroundColor(Color.WHITE)
        }

        val title = TextView(this).apply {
            text = "ورود امن به پنل رسمی شرکت"
            textSize = 21f
            setTextColor(Color.rgb(10, 54, 50))
            gravity = Gravity.CENTER
        }
        root.addView(title, LinearLayout.LayoutParams(-1, -2))

        val description = TextView(this).apply {
            text = "برای محافظت از رمز شما، پنل SAS RADIUS در مرورگر موبایل باز می‌شود. رمز خود را فقط در صفحه رسمی شرکت وارد کنید."
            textSize = 16f
            setTextColor(Color.rgb(45, 55, 65))
            gravity = Gravity.CENTER
            setPadding(0, 20, 0, 24)
        }
        root.addView(description, LinearLayout.LayoutParams(-1, -2))

        val openButton = Button(this).apply {
            text = "بازکردن پنل رسمی در مرورگر"
            setOnClickListener { openPortal() }
        }
        root.addView(openButton, LinearLayout.LayoutParams(-1, -2))

        val note = TextView(this).apply {
            text = "اگر مرورگر هشدار گواهی امنیتی نشان داد، هشدار را نادیده نگیرید و با شرکت ارائه‌دهنده اینترنت تماس بگیرید."
            textSize = 13f
            setTextColor(Color.rgb(130, 65, 20))
            gravity = Gravity.CENTER
            setPadding(0, 20, 0, 0)
        }
        root.addView(note, LinearLayout.LayoutParams(-1, -2))

        setContentView(root)
    }

    private fun openPortal() {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(portalUrl))
        try {
            startActivity(intent)
        } catch (_: ActivityNotFoundException) {
            // Keep the page visible so the user can see the instruction if no browser exists.
        }
    }
}
