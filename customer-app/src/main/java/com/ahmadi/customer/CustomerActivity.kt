package com.ahmadi.customer

import android.app.Activity
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView

class CustomerActivity : Activity() {
    private val navy = Color.rgb(18, 42, 77)
    private val blue = Color.rgb(28, 105, 190)
    private val muted = Color.rgb(95, 108, 125)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = navy
        window.navigationBarColor = navy
        window.decorView.layoutDirection = android.view.View.LAYOUT_DIRECTION_RTL

        val page = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.TOP
            setPadding(dp(24), dp(34), dp(24), dp(24))
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
            text = "پنل مشتریان اینترنت"
            textSize = 17f
            setTextColor(muted)
            gravity = Gravity.CENTER
            setPadding(0, dp(8), 0, dp(26))
        }
        page.addView(subtitle, matchWrap())

        val label = TextView(this).apply {
            text = "نام کاربری اینترنت"
            textSize = 16f
            setTextColor(navy)
        }
        page.addView(label, matchWrap())

        val username = EditText(this).apply {
            hint = "نام کاربری خود را وارد کنید"
            textSize = 16f
            isSingleLine = true
            setPadding(dp(14), dp(10), dp(14), dp(10))
            layoutDirection = android.view.View.LAYOUT_DIRECTION_LTR
            setBackgroundColor(Color.WHITE)
        }
        val inputParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, dp(54)
        ).apply { topMargin = dp(8); bottomMargin = dp(16) }
        page.addView(username, inputParams)

        val check = Button(this).apply {
            text = "بررسی مصرف و اعتبار"
            textSize = 16f
            setTextColor(Color.WHITE)
            setBackgroundColor(blue)
        }
        page.addView(check, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, dp(52)
        ))

        val result = TextView(this).apply {
            text = "برای حفاظت از اطلاعات شما، این نسخه هنوز به سامانه شرکت اینترنتی وصل نیست. تا زمان فعال‌شدن اتصال امن، اطلاعات ورود خود را وارد نکنید."
            textSize = 15f
            setTextColor(navy)
            setPadding(dp(16), dp(16), dp(16), dp(16))
            setBackgroundColor(Color.rgb(232, 240, 250))
            gravity = Gravity.CENTER
        }
        val resultParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = dp(20) }
        page.addView(result, resultParams)

        check.setOnClickListener {
            val name = username.text.toString().trim()
            result.text = if (name.isBlank()) {
                "لطفاً نام کاربری اینترنت خود را وارد کنید."
            } else {
                "اتصال امن شرکت هنوز تنظیم نشده است؛ اطلاعات مصرف و اعتبار فعلاً قابل دریافت نیست. نام کاربری شما در این نسخه ارسال یا ذخیره نمی‌شود."
            }
        }

        val footer = TextView(this).apply {
            text = "نسخه مشتریان • احمدی‌نت"
            textSize = 12f
            setTextColor(muted)
            gravity = Gravity.CENTER
            setPadding(0, dp(28), 0, 0)
        }
        page.addView(footer, matchWrap())
        setContentView(page)
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private fun matchWrap() = LinearLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
    )
}
