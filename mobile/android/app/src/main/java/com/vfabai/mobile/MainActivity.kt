package com.vfabai.mobile

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.SslErrorHandler
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.webkit.WebSettings
import android.net.http.SslError
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast

class MainActivity : Activity() {
    private val preferences by lazy { getSharedPreferences("vfab_workspace", MODE_PRIVATE) }
    private var webView: WebView? = null
    private var fileChooser: ValueCallback<Array<Uri>>? = null
    private var allowedOrigin: String? = null
    private val filePickerRequest = 7312

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Color.rgb(17, 24, 39)
        window.navigationBarColor = Color.rgb(17, 24, 39)
        val saved = preferences.getString("dashboard_url", "") ?: ""
        if (saved.isNotBlank()) connect(saved) else showConnectScreen("")
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private fun text(value: String, size: Float, color: Int, bold: Boolean = false): TextView {
        return TextView(this).apply {
            text = value
            textSize = size
            setTextColor(color)
            if (bold) setTypeface(typeface, android.graphics.Typeface.BOLD)
        }
    }

    private fun showConnectScreen(prefill: String) {
        webView?.stopLoading()
        webView?.destroy()
        webView = null
        allowedOrigin = null

        val page = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(24), dp(24), dp(24), dp(24))
            setBackgroundColor(Color.rgb(248, 249, 252))
        }

        page.addView(
            text("VFAB AI", 30f, Color.rgb(17, 24, 39), true),
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        )
        page.addView(
            text("Your AI clipping workspace", 16f, Color.rgb(92, 99, 115)),
            marginParams(top = 4, bottom = 22)
        )
        page.addView(
            text("Paste the private dashboard link printed by your laptop or cloud server.", 15f, Color.rgb(45, 52, 66)),
            marginParams(bottom = 12)
        )

        val input = EditText(this).apply {
            hint = "http://192.168.1.10:8765/?token=..."
            setText(prefill)
            singleLine = true
            textSize = 14f
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
            setPadding(dp(14), dp(12), dp(14), dp(12))
            setBackgroundColor(Color.WHITE)
        }
        page.addView(input, marginParams(bottom = 14))

        val connect = Button(this).apply {
            text = "Connect to VFAB AI"
            isAllCaps = false
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.rgb(101, 88, 232))
            setOnClickListener { connect(input.text.toString().trim()) }
        }
        page.addView(connect, marginParams(bottom = 18))

        page.addView(
            text(
                "Local mode: keep your laptop and phone on the same Wi-Fi, then start VFAB with the phone/LAN launcher on Windows.\n\nCloud mode: paste your private HTTPS workspace link. Keep that link secret because it contains your access token.",
                13f,
                Color.rgb(92, 99, 115)
            ),
            marginParams()
        )

        setContentView(page)
    }

    private fun marginParams(top: Int = 0, bottom: Int = 0): LinearLayout.LayoutParams {
        return LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply {
            topMargin = dp(top)
            bottomMargin = dp(bottom)
        }
    }

    private fun originOf(uri: Uri): String {
        val scheme = (uri.scheme ?: "").lowercase()
        val host = (uri.host ?: "").lowercase()
        val port = if (uri.port >= 0) uri.port else if (scheme == "https") 443 else 80
        return "$scheme://$host:$port"
    }

    private fun isPrivateHttpHost(host: String): Boolean {
        val value = host.lowercase()
        if (value == "localhost" || value.endsWith(".local")) return true
        val parts = value.split(".")
        if (parts.size != 4) return false
        val octets = parts.map { it.toIntOrNull() ?: return false }
        if (octets.any { it !in 0..255 }) return false
        return octets[0] == 10 ||
            (octets[0] == 172 && octets[1] in 16..31) ||
            (octets[0] == 192 && octets[1] == 168) ||
            octets[0] == 127 ||
            (octets[0] == 169 && octets[1] == 254)
    }

    private fun connect(raw: String) {
        val uri = try { Uri.parse(raw) } catch (_: Exception) { null }
        if (uri == null || uri.host.isNullOrBlank()) {
            Toast.makeText(this, "Enter a valid http or https dashboard link.", Toast.LENGTH_LONG).show()
            return
        }
        val scheme = (uri.scheme ?: "").lowercase()
        if (scheme != "http" && scheme != "https") {
            Toast.makeText(this, "Only http and https dashboard links are supported.", Toast.LENGTH_LONG).show()
            return
        }
        if (scheme == "http" && !isPrivateHttpHost(uri.host!!)) {
            Toast.makeText(this, "For safety, HTTP is allowed only for private/local network addresses. Use HTTPS for cloud.", Toast.LENGTH_LONG).show()
            return
        }
        if (uri.getQueryParameter("token").isNullOrBlank()) {
            Toast.makeText(this, "Paste the full private link containing ?token=...", Toast.LENGTH_LONG).show()
            return
        }

        val savedUrl = uri.toString()
        preferences.edit().putString("dashboard_url", savedUrl).apply()
        allowedOrigin = originOf(uri)

        val shell = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.WHITE)
        }
        val toolbar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(14), dp(6), dp(8), dp(6))
            setBackgroundColor(Color.rgb(17, 24, 39))
        }
        toolbar.addView(
            text("VFAB AI", 18f, Color.WHITE, true),
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        )
        val change = Button(this).apply {
            text = "Change"
            isAllCaps = false
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.rgb(55, 65, 81))
            setOnClickListener { showConnectScreen(preferences.getString("dashboard_url", "") ?: "") }
        }
        toolbar.addView(change, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(42)))
        shell.addView(toolbar, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(54)))

        val browser = WebView(this)
        webView = browser
        browser.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = true
            allowFileAccess = false
            allowContentAccess = true
            mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
            mediaPlaybackRequiresUserGesture = false
            javaScriptCanOpenWindowsAutomatically = false
            setSupportMultipleWindows(false)
        }
        CookieManager.getInstance().setAcceptCookie(true)
        CookieManager.getInstance().setAcceptThirdPartyCookies(browser, false)

        browser.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                val destination = request?.url ?: return false
                return routeNavigation(destination)
            }

            override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean {
                if (url.isNullOrBlank()) return false
                return routeNavigation(Uri.parse(url))
            }

            override fun onReceivedSslError(view: WebView?, handler: SslErrorHandler?, error: SslError?) {
                handler?.cancel()
                Toast.makeText(this@MainActivity, "Secure connection failed. Certificate errors are not bypassed.", Toast.LENGTH_LONG).show()
            }

            override fun onReceivedError(
                view: WebView?,
                request: WebResourceRequest?,
                error: android.webkit.WebResourceError?
            ) {
                if (request?.isForMainFrame == true) {
                    Toast.makeText(this@MainActivity, "Cannot reach the VFAB server. Check that it is running and reachable.", Toast.LENGTH_LONG).show()
                }
            }
        }

        browser.webChromeClient = object : WebChromeClient() {
            override fun onShowFileChooser(
                webView: WebView?,
                callback: ValueCallback<Array<Uri>>?,
                params: FileChooserParams?
            ): Boolean {
                fileChooser?.onReceiveValue(null)
                fileChooser = callback
                val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                    addCategory(Intent.CATEGORY_OPENABLE)
                    type = "*/*"
                    putExtra(Intent.EXTRA_MIME_TYPES, arrayOf("video/*", "image/*"))
                    putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
                }
                return try {
                    startActivityForResult(intent, filePickerRequest)
                    true
                } catch (_: Exception) {
                    fileChooser?.onReceiveValue(null)
                    fileChooser = null
                    false
                }
            }
        }

        shell.addView(browser, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        setContentView(shell)
        browser.loadUrl(savedUrl)
    }

    private fun routeNavigation(uri: Uri): Boolean {
        val scheme = (uri.scheme ?: "").lowercase()
        if (scheme != "http" && scheme != "https") return true
        if (originOf(uri) == allowedOrigin) return false
        return try {
            startActivity(Intent(Intent.ACTION_VIEW, uri))
            true
        } catch (_: Exception) {
            true
        }
    }

    @Deprecated("Deprecated in Android API; retained for broad device compatibility")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == filePickerRequest) {
            val callback = fileChooser
            fileChooser = null
            callback?.onReceiveValue(WebChromeClient.FileChooserParams.parseResult(resultCode, data))
        }
    }

    @Deprecated("Deprecated in Android API; WebView history is handled here")
    override fun onBackPressed() {
        val current = webView
        if (current != null && current.canGoBack()) {
            current.goBack()
        } else if (current != null) {
            showConnectScreen(preferences.getString("dashboard_url", "") ?: "")
        } else {
            super.onBackPressed()
        }
    }

    override fun onDestroy() {
        fileChooser?.onReceiveValue(null)
        fileChooser = null
        webView?.apply {
            stopLoading()
            destroy()
        }
        webView = null
        super.onDestroy()
    }
}
