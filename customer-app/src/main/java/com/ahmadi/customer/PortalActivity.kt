package com.ahmadi.customer

import android.app.Activity
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.View
import android.webkit.SslErrorHandler
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.net.http.SslError
import android.content.Intent
import android.widget.LinearLayout
import android.widget.TextView
import android.view.Gravity
import android.view.ViewGroup

/**
 * Loads the official SAS RADIUS customer portal.
 * Never ignore certificate errors; credentials must only be entered over valid HTTPS.
 */
class PortalActivity : Activity() {
    private val portalHost = "169.58.110.86"
    private lateinit var webView: WebView
    private lateinit var status: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Color.rgb(10, 54, 50)
        window.navigationBarColor = Color.rgb(10, 54, 50)
        window.decorView.layoutDirection = View.LAYOUT_DIRECTION_RTL

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.WHITE)
        }
        status = TextView(this).apply {
            text = "در حال بازکردن پنل رسمی شرکت..."
            textSize = 14f
            setTextColor(Color.rgb(45, 55, 65))
            gravity = Gravity.CENTER
            setPadding(16, 12, 16, 12)
        }
        root.addView(status, LinearLayout.LayoutParams(-1, -2))

        webView = WebView(this)
        root.addView(webView, LinearLayout.LayoutParams(-1, 0, 1f))
        setContentView(root)

        webView.settings.javaScriptEnabled = true // Required by the SAS RADIUS web app.
        webView.settings.domStorageEnabled = true
        webView.settings.allowFileAccess = false
        webView.settings.allowContentAccess = false
        webView.settings.javaScriptCanOpenWindowsAutomatically = false
        webView.settings.setSupportMultipleWindows(false)
        webView.settings.mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_NEVER_ALLOW
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            webView.settings.safeBrowsingEnabled = true
        }
        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                return handleNavigation(request.url.toString())
            }

            @Suppress("DEPRECATION")
            override fun shouldOverrideUrlLoading(view: WebView, url: String): Boolean {
                return handleNavigation(url)
            }

            override fun onPageStarted(view: WebView, url: String, favicon: android.graphics.Bitmap?) {
                status.text = "پنل رسمی SAS RADIUS"
            }

            override fun onReceivedSslError(view: WebView, handler: SslErrorHandler, error: SslError) {
                handler.cancel()
                status.text = "خطای گواهی امنیتی HTTPS. برای محافظت از رمز شما، صفحه باز نشد. تاریخ و ساعت موبایل و گواهی سایت باید بررسی شود."
            }

            override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
                if (request.isForMainFrame) {
                    status.text = "صفحه باز نشد. اینترنت و دسترسی به پنل شرکت را بررسی کنید."
                }
            }
        }
        webView.webChromeClient = WebChromeClient()
        webView.loadUrl("https://169.58.110.86/user/#/login")
    }

    private fun handleNavigation(rawUrl: String): Boolean {
        val uri = runCatching { Uri.parse(rawUrl) }.getOrNull() ?: return true
        if (uri.scheme != "https") {
            status.text = "برای امنیت، فقط اتصال HTTPS مجاز است."
            return true
        }
        if (uri.host.equals(portalHost, ignoreCase = true)) return false

        // Other HTTPS origins open outside this app, so the app does not collect their credentials.
        return try {
            startActivity(Intent(Intent.ACTION_VIEW, uri))
            true
        } catch (_: Exception) {
            status.text = "این پیوند امن در موبایل باز نشد."
            true
        }
    }

    @Deprecated("Deprecated in Android 13")
    override fun onBackPressed() {
        if (::webView.isInitialized && webView.canGoBack()) webView.goBack()
        else super.onBackPressed()
    }

    override fun onDestroy() {
        if (::webView.isInitialized) {
            webView.stopLoading()
            webView.destroy()
        }
        super.onDestroy()
    }
}
