package com.ahmadi.customer

import android.app.Activity
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

/**
 * AHMADI-NET customer app first implementation.
 *
 * This screen deliberately does not transmit credentials: the company/RADIUS
 * authentication endpoint has not yet been configured. Do not replace this
 * guard with direct client-to-router API access.
 */
class CustomerActivity : Activity() {
    private val navy = Color.rgb(10, 54, 50)
    private val green = Color.rgb(13, 148, 136)
    private val blue = Color.rgb(28, 105, 190)
    private val ink = Color.rgb(28, 42, 55)
    private val muted = Color.rgb(100, 116, 139)
    private val pageColor = Color.rgb(244, 248, 247)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = navy
        window.navigationBarColor = navy
        window.decorView.layoutDirection = View.LAYOUT_DIRECTION_RTL

        val scroll = ScrollView(this).apply {
            setBackgroundColor(pageColor)
            isFillViewport = true
        }
        val page = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutDirection = View.LAYOUT_DIRECTION_RTL
            setPadding(dp(20), dp(22), dp(20), dp(28))
        }
        scroll.addView(page)

        val header = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            background = rounded(navy, 22)
            setPadding(dp(18), dp(22), dp(18), dp(22))
        }
        header.addView(TextView(this).apply {
            text = "◉  احمدی‌نت"
            textSize = 29f
            setTextColor(Color.WHITE)
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
        }, matchWrap())
        header.addView(TextView(this).apply {
            text = "پنل مشتریان اینترنت"
            textSize = 15f
            setTextColor(Color.rgb(210, 244, 236))
            gravity = Gravity.CENTER
            setPadding(0, dp(7), 0, 0)
        }, matchWrap())
        page.addView(header, matchWrap())

        val ad = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            background = GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                intArrayOf(Color.rgb(10, 110, 101), Color.rgb(13, 148, 136), Color.rgb(23, 78, 91))
            ).apply { cornerRadius = dp(20).toFloat() }
            setPadding(dp(18), dp(18), dp(18), dp(18))
        }
        ad.addView(TextView(this).apply {
            text = "📶  اینترنت رحمت احمدی"
            textSize = 20f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
        }, matchWrap())
        ad.addView(TextView(this).apply {
            text = "اتصال بهتر، خدمات مطمئن‌تر"
            textSize = 15f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            setPadding(0, dp(9), 0, dp(5))
        }, matchWrap())
        ad.addView(TextView(this).apply {
            text = "برای بسته‌ها و پیشنهادهای تازه با احمدی‌نت در تماس باشید"
            textSize = 12f
            setTextColor(Color.rgb(226, 255, 249))
            gravity = Gravity.CENTER
        }, matchWrap())
        page.addView(ad, LinearLayout.LayoutParams(-1, dp(148)).apply {
            topMargin = dp(16)
            bottomMargin = dp(18)
        })

        val form = panel()
        form.addView(TextView(this).apply {
            text = "ورود به حساب"
            textSize = 22f
            setTextColor(navy)
            typeface = Typeface.DEFAULT_BOLD
        }, matchWrap())
        form.addView(TextView(this).apply {
            text = "نام کاربری و رمز حساب اینترنت خود را وارد کنید."
            textSize = 13f
            setTextColor(muted)
            setPadding(0, dp(6), 0, dp(16))
        }, matchWrap())

        form.addView(fieldLabel("نام کاربری اینترنت"))
        val username = input("نام کاربری خود را وارد کنید")
        form.addView(username, inputParams())

        form.addView(fieldLabel("رمز حساب اینترنت"))
        val password = input("رمز حساب خود را وارد کنید").apply {
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        }
        form.addView(password, inputParams())

        val result = TextView(this).apply {
            text = "اتصال امن هنوز تنظیم نشده است. فعلاً اطلاعات ورود شما ارسال یا ذخیره نمی‌شود."
            textSize = 14f
            setTextColor(Color.rgb(120, 74, 10))
            background = rounded(Color.rgb(255, 247, 224), 12)
            setPadding(dp(13), dp(13), dp(13), dp(13))
            gravity = Gravity.RIGHT
        }
        form.addView(result, LinearLayout.LayoutParams(-1, -2).apply {
            topMargin = dp(6)
            bottomMargin = dp(12)
        })

        val login = Button(this).apply {
            text = "ورود و مشاهده حساب"
            textSize = 16f
            isAllCaps = false
            setTextColor(Color.WHITE)
            background = rounded(blue, 14)
            setOnClickListener {
                val user = username.text.toString().trim()
                val pass = password.text.toString()
                result.text = when {
                    user.isBlank() -> "لطفاً نام کاربری اینترنت را وارد کنید."
                    pass.isBlank() -> "لطفاً رمز حساب اینترنت را وارد کنید."
                    else -> "برای امنیت حساب شما، ورود تا زمان آماده‌شدن سرویس امن غیرفعال است. اطلاعات واردشده ارسال یا ذخیره نشد. لطفاً فعلاً رمز واقعی خود را وارد نکنید."
                }
            }
        }
        form.addView(login, LinearLayout.LayoutParams(-1, dp(52)))
        page.addView(form, matchWrap())

        val info = panel()
        info.addView(TextView(this).apply {
            text = "در حساب خود چه خواهید دید؟"
            textSize = 17f
            setTextColor(navy)
            typeface = Typeface.DEFAULT_BOLD
        }, matchWrap())
        listOf(
            "حجم مصرف‌شده و باقی‌مانده",
            "اعتبار و نوع بسته اینترنت",
            "وضعیت فعال یا غیرفعال بودن حساب",
            "بنرها و اطلاعیه‌های جدید احمدی‌نت"
        ).forEach { line ->
            info.addView(TextView(this).apply {
                text = "✓  $line"
                textSize = 14f
                setTextColor(ink)
                setPadding(0, dp(9), 0, dp(2))
            }, matchWrap())
        }
        page.addView(info, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(14) })

        page.addView(TextView(this).apply {
            text = "AHMADI-NET  •  خدمات اینترنتی رحمت احمدی\nنسخه اولیه مشتریان"
            textSize = 12f
            setTextColor(muted)
            gravity = Gravity.CENTER
            setPadding(0, dp(20), 0, 0)
        }, matchWrap())

        setContentView(scroll)
    }

    private fun panel() = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        layoutDirection = View.LAYOUT_DIRECTION_RTL
        background = rounded(Color.WHITE, 18)
        setPadding(dp(16), dp(17), dp(16), dp(17))
        elevation = dp(2).toFloat()
    }

    private fun fieldLabel(value: String) = TextView(this).apply {
        text = value
        textSize = 14f
        setTextColor(ink)
        setPadding(0, dp(4), 0, dp(6))
    }

    private fun input(hintText: String) = EditText(this).apply {
        hint = hintText
        textSize = 15f
        isSingleLine = true
        layoutDirection = View.LAYOUT_DIRECTION_LTR
        textDirection = View.TEXT_DIRECTION_LTR
        setTextColor(ink)
        setHintTextColor(muted)
        setPadding(dp(12), 0, dp(12), 0)
        background = rounded(Color.rgb(246, 249, 250), 12)
    }

    private fun inputParams() = LinearLayout.LayoutParams(-1, dp(52)).apply {
        bottomMargin = dp(12)
    }

    private fun rounded(color: Int, radiusDp: Int) = GradientDrawable().apply {
        setColor(color)
        cornerRadius = dp(radiusDp).toFloat()
    }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()

    private fun matchWrap() = LinearLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT,
        ViewGroup.LayoutParams.WRAP_CONTENT
    )
}
