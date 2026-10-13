package com.ahmadi.net

import android.app.Activity
import android.content.Context
import android.os.Bundle
import android.print.PrintAttributes
import android.print.PrintManager
import android.webkit.WebView
import android.webkit.WebViewClient
import android.graphics.Color
import android.view.Gravity
import android.view.View
import android.widget.*
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.random.Random

class MainActivity : Activity() {
    private val prefs by lazy { getSharedPreferences("ahmadi_net_data", Context.MODE_PRIVATE) }
    private val cards = mutableListOf<JSONObject>()
    private val customers = mutableListOf<JSONObject>()
    private lateinit var root: LinearLayout
    private lateinit var body: LinearLayout
    private val fa = Locale("fa", "AF")
    @Volatile private var routerApi: RouterApi? = null
    private val uiHandler by lazy { android.os.Handler(mainLooper) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Color.rgb(12, 48, 84)
        window.navigationBarColor = Color.rgb(12, 48, 84)
        loadData()
        buildShell()
        showHome()
    }

    private fun loadData() {
        try {
            val a = JSONArray(prefs.getString("cards", "[]"))
            for (i in 0 until a.length()) cards.add(a.getJSONObject(i))
            val c = JSONArray(prefs.getString("customers", "[]"))
            for (i in 0 until c.length()) customers.add(c.getJSONObject(i))
        } catch (_: Exception) { }
    }

    private fun saveData() {
        val a = JSONArray(); cards.forEach { a.put(it) }
        val c = JSONArray(); customers.forEach { c.put(it) }
        prefs.edit().putString("cards", a.toString()).putString("customers", c.toString()).apply()
    }

    private fun buildShell() {
        root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutDirection = View.LAYOUT_DIRECTION_RTL
            setBackgroundColor(Color.rgb(244, 247, 251))
        }
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(18), dp(20), dp(18))
            setBackgroundColor(Color.rgb(12, 48, 84))
        }
        header.addView(TextView(this).apply {
            text = "احمدی نت"
            textSize = 27f
            setTextColor(Color.WHITE)
            gravity = Gravity.RIGHT
            typeface = android.graphics.Typeface.DEFAULT_BOLD
        })
        header.addView(TextView(this).apply {
            text = "مدیریت مشتری، کارت اینترنت و فروش"
            textSize = 14f
            setTextColor(Color.rgb(215, 230, 245))
            gravity = Gravity.RIGHT
        })
        root.addView(header)
        val nav = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(dp(8), dp(8), dp(8), dp(8))
            setBackgroundColor(Color.WHITE)
        }
        addNav(nav, "خانه") { showHome() }
        addNav(nav, "مشتری") { showCustomerForm() }
        addNav(nav, "ساخت کارت") { showCardForm() }
        addNav(nav, "گزارش") { showReports() }
        addNav(nav, "روتر") { showRouter() }
        root.addView(nav)
        val scroll = ScrollView(this)
        body = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutDirection = View.LAYOUT_DIRECTION_RTL
            setPadding(dp(16), dp(12), dp(16), dp(24))
        }
        scroll.addView(body)
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        setContentView(root)
    }

    private fun addNav(parent: LinearLayout, label: String, action: () -> Unit) {
        val b = Button(this).apply {
            text = label
            textSize = 12f
            isAllCaps = false
            setOnClickListener { action() }
        }
        parent.addView(b, LinearLayout.LayoutParams(0, dp(48), 1f))
    }

    private fun clear(title: String) {
        body.removeAllViews()
        body.addView(TextView(this).apply {
            text = title
            textSize = 22f
            setTextColor(Color.rgb(12, 48, 84))
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            gravity = Gravity.RIGHT
            setPadding(0, dp(8), 0, dp(14))
        })
    }

    private fun cardBox(): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutDirection = View.LAYOUT_DIRECTION_RTL
            setPadding(dp(14), dp(14), dp(14), dp(14))
            setBackgroundColor(Color.WHITE)
            elevation = dp(2).toFloat()
        }.also { body.addView(it, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(12) }) }
    }

    private fun label(text: String, parent: LinearLayout = body) {
        parent.addView(TextView(this).apply {
            this.text = text
            textSize = 15f
            setTextColor(Color.rgb(40, 54, 70))
            gravity = Gravity.RIGHT
            setPadding(0, dp(7), 0, dp(4))
        })
    }

    private fun field(hint: String, parent: LinearLayout = body, numeric: Boolean = false): EditText {
        val e = EditText(this).apply {
            this.hint = hint
            textSize = 15f
            setSingleLine(true)
            gravity = Gravity.RIGHT or Gravity.CENTER_VERTICAL
            layoutDirection = View.LAYOUT_DIRECTION_RTL
            if (numeric) inputType = android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL
            setPadding(dp(12), 0, dp(12), 0)
            setBackgroundColor(Color.rgb(246, 248, 251))
        }
        parent.addView(e, LinearLayout.LayoutParams(-1, dp(50)).apply { bottomMargin = dp(8) })
        return e
    }

    private fun button(text: String, parent: LinearLayout = body, action: () -> Unit) {
        parent.addView(Button(this).apply {
            this.text = text
            isAllCaps = false
            textSize = 15f
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.rgb(18, 105, 170))
            setOnClickListener { action() }
        }, LinearLayout.LayoutParams(-1, dp(50)).apply { topMargin = dp(8); bottomMargin = dp(8) })
    }


    private fun showRouter() {
        clear("مدیریت مستقیم MikroTik")
        val box = cardBox()
        label("آدرس IP روتر (مثلاً 172.16.1.1)", box)
        val host = field("IP روتر یا IP از طریق VPN", box)
        label("پورت API (پیش‌فرض 8728)", box)
        val port = field("8728", box, true).apply { setText("8728") }
        label("نام کاربری MikroTik", box)
        val username = field("نام کاربری مدیر یا حساب محدود", box)
        label("رمز روتر", box)
        val password = field("رمز API روتر", box).apply { inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD }
        val status = TextView(this).apply {
            text = "وضعیت: وصل نیست"
            textSize = 15f; setTextColor(Color.rgb(40,54,70)); gravity = Gravity.RIGHT
        }
        box.addView(status)
        button("اتصال و بررسی روتر", box) {
            val h = host.text.toString().trim()
            val p = port.text.toString().toIntOrNull() ?: 8728
            val u = username.text.toString().trim()
            val pw = password.text.toString()
            if (h.isBlank() || u.isBlank() || pw.isBlank()) { toast("آدرس، نام کاربری و رمز را وارد کنید"); return@button }
            status.text = "در حال اتصال..."
            Thread {
                try {
                    val api = RouterApi(h, p, u, pw)
                    val ident = api.identity()
                    val res = api.resource()
                    val active = api.active()
                    routerApi = api
                    uiHandler.post {
                        status.text = "وصل شد: ${ident["name"] ?: h}\nRouterOS: ${res["version"] ?: "نامشخص"}\nمدل/برد: ${res["board-name"] ?: "—"}\nمدت روشن‌بودن: ${res["uptime"] ?: "—"}\nکاربران آنلاین هات‌اسپات: ${active.size}"
                        toast("اتصال روتر موفق بود")
                    }
                } catch (e: Exception) {
                    routerApi = null
                    uiHandler.post { status.text = "اتصال ناموفق: ${e.message ?: "خطای ناشناخته"}" }
                }
            }.start()
        }
        val manage = cardBox()
        label("عملیات هات‌اسپات", manage)
        label("ساخت یک کاربر یا تولید گروهی رمزها روی روتر واقعی.", manage)
        label("نام کاربری جدید", manage); val newUser = field("مثلاً AN123456", manage)
        label("رمز کاربر", manage); val newPass = field("رمز ورود هات‌اسپات", manage)
        button("ساخت کاربر روی روتر", manage) {
            val api = routerApi
            val n = newUser.text.toString().trim()
            val pw = newPass.text.toString().trim()
            if (api == null) { toast("اول به روتر وصل شوید"); return@button }
            if (n.isBlank() || pw.isBlank()) { toast("نام کاربری و رمز را وارد کنید"); return@button }
            Thread {
                try {
                    api.addUser(n, pw)
                    uiHandler.post { toast("کاربر $n روی روتر ساخته شد") }
                } catch (e: Exception) { uiHandler.post { toast("خطا: ${e.message}") } }
            }.start()
        }
        label("پیشوند نام کاربری گروهی", manage); val prefix = field("AN", manage).apply { setText("AN") }
        label("تعداد رمزها (حداکثر 100)", manage); val count = field("تعداد", manage, true).apply { setText("10") }
        button("ساخت گروهی رمزها روی روتر", manage) {
            val api = routerApi
            val pref = prefix.text.toString().trim().ifBlank { "AN" }
            val amount = (count.text.toString().toIntOrNull() ?: 0).coerceIn(0, 100)
            if (api == null) { toast("اول به روتر وصل شوید"); return@button }
            if (amount == 0) { toast("تعداد باید بین 1 تا 100 باشد"); return@button }
            Thread {
                var made = 0
                var lastError = ""
                repeat(amount) {
                    val n = pref + Random.nextInt(100000, 999999)
                    val pw = Random.nextInt(100000, 999999).toString()
                    try { api.addUser(n, pw); made++ } catch (e: Exception) { lastError = e.message ?: "خطا" }
                }
                uiHandler.post { toast("ساخته شد: $made از $amount" + if (lastError.isNotBlank()) "؛ خطا: $lastError" else "") }
            }.start()
        }
        button("نمایش کاربران آنلاین و مصرف جلسه", manage) {
            val api = routerApi
            if (api == null) { toast("اول به روتر وصل شوید"); return@button }
            status.text = "در حال خواندن کاربران..."
            Thread {
                try {
                    val active = api.active()
                    val users = api.users()
                    uiHandler.post {
                        val out = StringBuilder("کاربران آنلاین: ${active.size}\n\n")
                        active.forEach { a ->
                            out.append("کاربر: ${a["user"] ?: a["name"] ?: "—"}\n")
                            out.append("IP: ${a["address"] ?: "—"}\n")
                            out.append("دریافت: ${a["bytes-in"] ?: "0"} بایت | ارسال: ${a["bytes-out"] ?: "0"} بایت\n")
                            out.append("زمان اتصال: ${a["uptime"] ?: "—"}\n\n")
                        }
                        out.append("کل کاربران ثبت‌شده: ${users.size}")
                        status.text = out.toString()
                    }
                } catch (e: Exception) { uiHandler.post { status.text = "خطا: ${e.message}" } }
            }.start()
        }
        label("مدیریت حساب موجود", manage)
        val targetUser = field("نام کاربری برای جستجو یا قطع اتصال", manage)
        button("غیرفعال‌کردن حساب کاربر", manage) {
            val api = routerApi
            val n = targetUser.text.toString().trim()
            if (api == null) { toast("اول به روتر وصل شوید"); return@button }
            if (n.isBlank()) { toast("نام کاربری را وارد کنید"); return@button }
            Thread {
                try {
                    val found = api.users().firstOrNull { it["name"] == n }
                    if (found == null) uiHandler.post { toast("این کاربر در روتر پیدا نشد") }
                    else {
                        val id = found[".id"] ?: throw IllegalStateException("شناسه کاربر دریافت نشد")
                        api.disableUser(id)
                        uiHandler.post { toast("حساب $n غیرفعال شد") }
                    }
                } catch (e: Exception) { uiHandler.post { toast("خطا: ${e.message}") } }
            }.start()
        }
        button("قطع اتصال فعال این کاربر", manage) {
            val api = routerApi
            val n = targetUser.text.toString().trim()
            if (api == null) { toast("اول به روتر وصل شوید"); return@button }
            if (n.isBlank()) { toast("نام کاربری را وارد کنید"); return@button }
            Thread {
                try {
                    val found = api.active().filter { (it["user"] ?: it["name"]) == n }
                    if (found.isEmpty()) uiHandler.post { toast("این کاربر آنلاین نیست") }
                    else {
                        found.forEach { row ->
                            val id = row[".id"] ?: throw IllegalStateException("شناسه نشست دریافت نشد")
                            api.removeActive(id)
                        }
                        uiHandler.post { toast("اتصال $n قطع شد") }
                    }
                } catch (e: Exception) { uiHandler.post { toast("خطا: ${e.message}") } }
            }.start()
        }
        val warning = cardBox()
        label("نکته امنیتی مهم", warning)
        label("این نسخه از API معمولی روتر استفاده می‌کند. فقط در شبکه محلی یا VPN امن وصل شوید؛ پورت 8728 را در اینترنت باز نکنید. برای استفاده از راه دور، VPN مثل WireGuard و یک کاربر MikroTik با دسترسی محدود بسازید. رمز روتر در برنامه ذخیره نمی‌شود.", warning)
    }

    private fun showHome() {
        clear("داشبورد")
        val soldToday = cards.filter { it.optString("date") == today() }
        val revenueToday = soldToday.sumOf { it.optDouble("paid", 0.0) }
        val revenueMonth = cards.filter { it.optString("date").startsWith(month()) }.sumOf { it.optDouble("paid", 0.0) }
        val box = cardBox()
        label("خوش آمدید به احمدی نت", box)
        label("مشتریان ثبت‌شده: ${customers.size}", box)
        label("کارت‌های ثبت‌شده: ${cards.size}", box)
        label("فروش امروز: ${money(revenueToday)}", box)
        label("فروش این ماه: ${money(revenueMonth)}", box)
        val b = cardBox()
        label("دسترسی سریع", b)
        button("ثبت مشتری جدید", b) { showCustomerForm() }
        button("ساخت کارت اینترنت", b) { showCardForm() }
        button("گزارش فروش", b) { showReports() }
        button("چاپ کارت‌ها", b) { printCards() }
        button("فهرست کارت‌ها", b) { showCards() }
        val note = cardBox()
        label("یادداشت", note)
        label("اطلاعات این نسخه روی همین گوشی ذخیره می‌شود. از اطلاعات خود نسخه پشتیبان تهیه کنید.", note)
        label("برای اتصال به روتر، بخش «روتر» را باز کنید.", note)
    }

    private fun showCustomerForm() {
        clear("ثبت مشتری")
        val box = cardBox()
        label("نام مشتری", box); val name = field("مثلاً احمد", box)
        label("شماره تماس (اختیاری)", box); val phone = field("شماره تماس", box, true)
        label("یادداشت (اختیاری)", box); val note = field("توضیحات", box)
        button("ذخیره مشتری", box) {
            val n = name.text.toString().trim()
            if (n.isBlank()) { toast("نام مشتری را وارد کنید"); return@button }
            customers.add(JSONObject().put("name", n).put("phone", phone.text.toString()).put("note", note.text.toString()).put("date", today()))
            saveData(); toast("مشتری ثبت شد"); showHome()
        }
        val list = cardBox()
        label("مشتریان اخیر", list)
        customers.takeLast(10).asReversed().forEach { label("• ${it.optString("name")}  |  ${it.optString("phone")}", list) }
    }

    private fun showCardForm() {
        clear("ساخت کارت اینترنت")
        val box = cardBox()
        label("نام مشتری (اختیاری)", box); val customer = field("نام مشتری", box)
        label("بسته اینترنت", box)
        val pack = Spinner(this)
        val packs = listOf("1GB", "5GB", "10GB")
        pack.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, packs)
        box.addView(pack, LinearLayout.LayoutParams(-1, dp(48)))
        label("قیمت کل", box); val price = field("قیمت به افغانی", box, true)
        label("مبلغ پرداخت‌شده", box); val paid = field("مبلغ دریافتی", box, true)
        label("نام کاربری (خالی بماند تا خودکار ساخته شود)", box); val user = field("نام کاربری", box)
        label("رمز کارت (خالی بماند تا خودکار ساخته شود)", box); val pass = field("رمز کارت", box)
        button("ساخت و ثبت کارت", box) {
            val p = price.text.toString().toDoubleOrNull()
            val pay = paid.text.toString().toDoubleOrNull()
            if (p == null || p < 0 || pay == null || pay < 0 || pay > p) {
                toast("قیمت و مبلغ پرداختی را درست وارد کنید"); return@button
            }
            val username = user.text.toString().trim().ifBlank { "AN" + Random.nextInt(100000, 999999) }
            val password = pass.text.toString().trim().ifBlank { Random.nextInt(100000, 999999).toString() }
            if (cards.any { it.optString("username") == username }) { toast("این نام کاربری قبلاً ثبت شده است"); return@button }
            cards.add(JSONObject()
                .put("customer", customer.text.toString().trim())
                .put("package", packs[pack.selectedItemPosition])
                .put("price", p).put("paid", pay).put("debt", p-pay)
                .put("username", username).put("password", password)
                .put("date", today()).put("created", System.currentTimeMillis()))
            saveData()
            toast("کارت ثبت شد")
            showCardResult(username, password, packs[pack.selectedItemPosition], p, pay)
        }
    }

    private fun showCardResult(username: String, password: String, pack: String, price: Double, paid: Double) {
        clear("کارت ساخته شد")
        val box = cardBox()
        label("اینترنت رحمت احمدی", box)
        label("بسته: $pack", box)
        label("نام کاربری: $username", box)
        label("رمز کارت: $password", box)
        label("قیمت: ${money(price)} افغانی", box)
        label("پرداخت: ${money(paid)} | باقی‌مانده: ${money(price-paid)}", box)
        button("چاپ کارت‌ها (۵×۱ سانتی‌متر)", box) { printCards() }
        button("ساخت کارت دیگر", box) { showCardForm() }
        button("بازگشت به خانه", box) { showHome() }
    }

    private fun showCards() {
        clear("فهرست کارت‌ها")
        val box = cardBox()
        if (cards.isEmpty()) label("هنوز کارتی ثبت نشده است.", box)
        cards.asReversed().take(100).forEach { c ->
            label("${c.optString("package")} | ${c.optString("username")} | رمز: ${c.optString("password")}", box)
            label("مشتری: ${c.optString("customer", "—")} | پرداخت: ${money(c.optDouble("paid"))} | بدهی: ${money(c.optDouble("debt"))}", box)
            label("تاریخ: ${c.optString("date")}", box)
            val sep = View(this).apply { setBackgroundColor(Color.LTGRAY) }
            box.addView(sep, LinearLayout.LayoutParams(-1, dp(1)).apply { topMargin = dp(5); bottomMargin = dp(5) })
        }
        button("چاپ همه کارت‌ها", box) { printCards() }
    }

    private fun showReports() {
        clear("گزارش فروش")
        val box = cardBox()
        val todayCards = cards.filter { it.optString("date") == today() }
        val monthCards = cards.filter { it.optString("date").startsWith(month()) }
        label("گزارش روزانه — ${today()}", box)
        label("تعداد کارت: ${todayCards.size}", box)
        label("فروش ثبت‌شده: ${money(todayCards.sumOf { it.optDouble("price") })} افغانی", box)
        label("دریافتی: ${money(todayCards.sumOf { it.optDouble("paid") })} افغانی", box)
        label("بدهی: ${money(todayCards.sumOf { it.optDouble("debt") })} افغانی", box)
        val m = cardBox()
        label("گزارش ماهانه — ${month()}", m)
        label("تعداد کارت: ${monthCards.size}", m)
        label("فروش ثبت‌شده: ${money(monthCards.sumOf { it.optDouble("price") })} افغانی", m)
        label("دریافتی: ${money(monthCards.sumOf { it.optDouble("paid") })} افغانی", m)
        label("بدهی: ${money(monthCards.sumOf { it.optDouble("debt") })} افغانی", m)
        val packs = cardBox()
        label("تفکیک بسته‌ها در این ماه", packs)
        listOf("1GB", "5GB", "10GB").forEach { p ->
            val list = monthCards.filter { it.optString("package") == p }
            label("$p: ${list.size} کارت — ${money(list.sumOf { it.optDouble("paid") })} افغانی دریافتی", packs)
        }
        button("فهرست کارت‌ها", packs) { showCards() }
    }

    private fun printCards() {
        if (cards.isEmpty()) { toast("برای چاپ ابتدا یک کارت بسازید"); return }
        val html = buildString {
            append("<html><head><meta charset='utf-8'><style>")
            append("@page{size:auto;margin:4mm}body{font-family:sans-serif;direction:rtl;margin:0}")
            append(".card{width:50mm;height:10mm;box-sizing:border-box;border:0.3mm solid #333;")
            append("display:inline-flex;flex-direction:column;justify-content:center;align-items:center;")
            append("overflow:hidden;margin:1mm;text-align:center;page-break-inside:avoid;font-size:7pt;line-height:1.15}")
            append(".name{font-weight:bold;font-size:8pt}.small{font-size:6.5pt}</style></head><body>")
            cards.forEach { c ->
                append("<div class='card'><div class='name'>اینترنت رحمت احمدی • ${esc(c.optString("package"))}</div>")
                append("<div class='small'>کاربر: ${esc(c.optString("username"))} | رمز: ${esc(c.optString("password"))}</div></div>")
            }
            append("</body></html>")
        }
        val web = WebView(this)
        web.settings.javaScriptEnabled = false
        web.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView, url: String) {
                val pm = getSystemService(Context.PRINT_SERVICE) as PrintManager
                pm.print("کارت‌های اینترنت احمدی نت", view.createPrintDocumentAdapter("AHMADI-NET-cards"),
                    PrintAttributes.Builder()
                        .setMediaSize(PrintAttributes.MediaSize.ISO_A4)
                        .setResolution(android.print.PrintAttributes.Resolution("ahmadi", "Default", 300, 300))
                        .setMinMargins(PrintAttributes.Margins.NO_MARGINS).build())
            }
        }
        web.loadDataWithBaseURL(null, html, "text/html", "UTF-8", null)
    }

    private fun esc(s: String): String = s.replace("&","&amp;").replace("<","&lt;").replace(">","&gt;").replace("\"","&quot;")
    private fun today(): String = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
    private fun month(): String = SimpleDateFormat("yyyy-MM", Locale.US).format(Date())
    private fun money(n: Double): String = if (n % 1.0 == 0.0) n.toLong().toString() else String.format(Locale.US, "%.2f", n)
    private fun dp(n: Int): Int = (n * resources.displayMetrics.density).toInt()
    private fun toast(s: String) = Toast.makeText(this, s, Toast.LENGTH_SHORT).show()
}
