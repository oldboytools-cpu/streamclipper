package com.vfabai.mobile

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.FrameLayout
import android.os.Handler
import android.os.Looper
import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener
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
    private var rootFrame: FrameLayout? = null
    private var nativePanel: View? = null
    private var jobsBox: LinearLayout? = null
    private var sourceInput: EditText? = null
    private var modePicker: Spinner? = null
    private val uiHandler = Handler(Looper.getMainLooper())
    private var polling = false
    private val pollJobs = object : Runnable {
        override fun run() {
            if (!polling) return
            if (nativePanel?.visibility == View.VISIBLE) refreshNativeJobs()
            uiHandler.postDelayed(this, 8000)
        }
    }

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
        stopNativePolling()
        rootFrame = null
        nativePanel = null
        jobsBox = null
        sourceInput = null
        modePicker = null
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
            setSingleLine(true)
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
        val nativeHome = Button(this).apply {
            text = "Projects"
            isAllCaps = false
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.rgb(55, 65, 81))
            setOnClickListener { showNativeHome() }
        }
        toolbar.addView(nativeHome, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(42)))
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
            override fun onPageFinished(view: WebView?, url: String?) {
                if (nativePanel?.visibility == View.VISIBLE) refreshNativeJobs()
            }

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
        val frame = FrameLayout(this)
        frame.addView(shell, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        rootFrame = frame
        setContentView(frame)
        browser.loadUrl(savedUrl)
    }


    private fun showNativeHome() {
        val frame = rootFrame ?: return
        val panel = nativePanel ?: buildNativeHomePanel().also {
            nativePanel = it
            frame.addView(it, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        }
        panel.visibility = View.VISIBLE
        startNativePolling()
        refreshNativeJobs()
    }

    private fun openFullDashboard(jobId: String? = null) {
        stopNativePolling()
        nativePanel?.visibility = View.GONE
        val browser = webView ?: return
        browser.visibility = View.VISIBLE
        if (jobId != null) {
            val encoded = JSONObject.quote(jobId)
            browser.evaluateJavascript("location.hash = '#/project/' + encodeURIComponent($encoded)", null)
        }
    }

    private fun buildNativeHomePanel(): View {
        val page = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(14), dp(18), dp(16))
            setBackgroundColor(Color.rgb(247, 248, 252))
        }
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        header.addView(
            text("VFAB AI", 25f, Color.rgb(22, 28, 45), true),
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        )
        val dashboard = Button(this).apply {
            text = "Full editor"
            isAllCaps = false
            setOnClickListener { openFullDashboard() }
        }
        header.addView(dashboard)
        page.addView(header)
        page.addView(
            text("Native project control · Turbo AI · all qualifying clips ranked best first", 13f, Color.rgb(83, 91, 108)),
            marginParams(top = 4, bottom = 14)
        )

        val source = EditText(this).apply {
            hint = "Paste a YouTube, Twitch or Kick video URL"
            setSingleLine(true)
            textSize = 15f
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
            setPadding(dp(12), dp(11), dp(12), dp(11))
            setBackgroundColor(Color.WHITE)
        }
        sourceInput = source
        page.addView(source, marginParams(bottom = 8))

        val mode = Spinner(this).apply {
            adapter = ArrayAdapter(
                this@MainActivity,
                android.R.layout.simple_spinner_dropdown_item,
                listOf("Recorded video / VOD", "Live stream")
            )
        }
        modePicker = mode
        page.addView(mode, marginParams(bottom = 8))

        val start = Button(this).apply {
            text = "Find and rank clips"
            isAllCaps = false
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.rgb(91, 77, 220))
            setOnClickListener { submitNativeJob() }
        }
        page.addView(start, marginParams(bottom = 10))

        val actions = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        actions.addView(text("Projects", 19f, Color.rgb(22, 28, 45), true), LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        val refresh = Button(this).apply {
            text = "Refresh"
            isAllCaps = false
            setOnClickListener { refreshNativeJobs() }
        }
        actions.addView(refresh)
        page.addView(actions)

        val scroll = ScrollView(this)
        val list = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, dp(8), 0, dp(12))
        }
        jobsBox = list
        scroll.addView(list)
        page.addView(scroll, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        page.addView(
            text("For clip preview, trimming, captions and export, open a project in the full editor.", 12f, Color.rgb(83, 91, 108)),
            marginParams(top = 8)
        )
        return page
    }

    private fun submitNativeJob() {
        val source = sourceInput?.text?.toString()?.trim().orEmpty()
        if (source.isBlank()) {
            Toast.makeText(this, "Paste a video link first.", Toast.LENGTH_LONG).show()
            return
        }
        val live = modePicker?.selectedItemPosition == 1
        val body = JSONObject()
            .put("mode", if (live) "live" else "vod")
            .put("source", source)
            .put("max_clips", 0)
            .put("min_score", 50)
            .put("asr", "turbo")
            .put("render", true)
            .put("drafts", false)
            .put("ratio", "9:16")
            .put("llm", "none")
            .put("caption_style", "auto")
            .put("hook_style", "bold-box")
        if (live) {
            body.put("stop_after", 60)
            body.put("max_clips_per_hour", 12)
        }
        postNativeApi("/api/jobs", body) { ok, response ->
            if (ok) {
                sourceInput?.setText("")
                Toast.makeText(this, "Project queued. All qualifying clips will be ranked.", Toast.LENGTH_LONG).show()
                refreshNativeJobs()
            } else {
                Toast.makeText(this, response.ifBlank { "Could not start the project." }, Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun postNativeApi(path: String, body: JSONObject, done: (Boolean, String) -> Unit) {
        val browser = webView ?: return done(false, "Dashboard is not connected.")
        val pathLiteral = JSONObject.quote(path)
        val bodyLiteral = JSONObject.quote(body.toString())
        val script = "(async()=>{try{const csrf=document.querySelector('meta[name=csrf]')?.content||'';" +
            "const r=await fetch($pathLiteral,{method:'POST',headers:{'Content-Type':'application/json','X-CSRF-Token':csrf}," +
            "body:$bodyLiteral});const d=await r.json().catch(()=>({}));" +
            "return JSON.stringify({ok:r.ok,status:r.status,data:d});" +
            "}catch(e){return JSON.stringify({ok:false,error:String(e)});}})()"
        browser.evaluateJavascript(script) { raw ->
            val value = decodeJavascriptString(raw)
            try {
                val obj = JSONObject(value)
                val ok = obj.optBoolean("ok")
                val data = obj.optJSONObject("data")
                done(ok, if (ok) "" else data?.optString("error", "") ?: obj.optString("error", "Request failed."))
            } catch (_: Exception) {
                done(false, "The server returned an unreadable response.")
            }
        }
    }

    private fun decodeJavascriptString(raw: String?): String {
        if (raw.isNullOrBlank()) return ""
        return try {
            JSONTokener(raw).nextValue() as? String ?: raw
        } catch (_: Exception) {
            raw
        }
    }

    private fun refreshNativeJobs() {
        val browser = webView ?: return
        val script = "(async()=>{try{const r=await fetch('/api/jobs',{cache:'no-store'});" +
            "return JSON.stringify({ok:r.ok,data:await r.json()});" +
            "}catch(e){return JSON.stringify({ok:false,error:String(e)});}})()"
        browser.evaluateJavascript(script) { raw ->
            val value = decodeJavascriptString(raw)
            try {
                val obj = JSONObject(value)
                if (!obj.optBoolean("ok")) {
                    showNativeMessage("Cannot load projects. Check the laptop connection.")
                    return@evaluateJavascript
                }
                renderNativeJobs(obj.optJSONObject("data")?.optJSONArray("jobs") ?: JSONArray())
            } catch (_: Exception) {
                showNativeMessage("Waiting for the VFAB dashboard to finish connecting.")
            }
        }
    }

    private fun renderNativeJobs(jobs: JSONArray) {
        val list = jobsBox ?: return
        list.removeAllViews()
        if (jobs.length() == 0) {
            list.addView(
                text("No projects yet. Paste a video link above to start.", 14f, Color.rgb(83, 91, 108)),
                marginParams(top = 14)
            )
            return
        }
        for (i in 0 until jobs.length()) {
            val job = jobs.optJSONObject(i) ?: continue
            val id = job.optString("id")
            val titleText = job.optString("label", "Video project")
            val status = job.optString("status", "unknown")
            val progress = job.optJSONObject("progress")
            val detail = progress?.optString("detail", "")?.takeIf { it.isNotBlank() }
                ?: job.optString("stage", "")
            val card = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(13), dp(12), dp(13), dp(12))
                setBackgroundColor(Color.WHITE)
            }
            card.addView(text(titleText, 14f, Color.rgb(22, 28, 45), true))
            card.addView(
                text("$status  ·  ${job.optInt("clip_count", 0)} clips", 13f, Color.rgb(91, 77, 220)),
                marginParams(top = 4)
            )
            if (detail.isNotBlank()) card.addView(text(detail, 12f, Color.rgb(83, 91, 108)), marginParams(top = 3))
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.END
            }
            val open = Button(this).apply {
                text = "Open clips"
                isAllCaps = false
                setOnClickListener { openFullDashboard(id) }
            }
            row.addView(open)
            if (status in listOf("failed", "cancelled", "interrupted", "stopped")) {
                val resume = Button(this).apply {
                    text = "Resume"
                    isAllCaps = false
                    setOnClickListener { postNativeApi("/api/jobs/$id/resume", JSONObject()) { ok, msg ->
                        Toast.makeText(this@MainActivity, if (ok) "Project resumed." else msg, Toast.LENGTH_LONG).show()
                        refreshNativeJobs()
                    } }
                }
                row.addView(resume)
            } else if (status in listOf("running", "queued")) {
                val cancel = Button(this).apply {
                    text = "Cancel"
                    isAllCaps = false
                    setOnClickListener { postNativeApi("/api/jobs/$id/cancel", JSONObject()) { ok, msg ->
                        Toast.makeText(this@MainActivity, if (ok) "Project cancelled." else msg, Toast.LENGTH_LONG).show()
                        refreshNativeJobs()
                    } }
                }
                row.addView(cancel)
            }
            card.addView(row, marginParams(top = 4))
            list.addView(card, marginParams(top = 8))
        }
    }

    private fun showNativeMessage(message: String) {
        val list = jobsBox ?: return
        list.removeAllViews()
        list.addView(text(message, 14f, Color.rgb(83, 91, 108)), marginParams(top = 14))
    }

    private fun startNativePolling() {
        if (polling) return
        polling = true
        uiHandler.removeCallbacks(pollJobs)
        uiHandler.postDelayed(pollJobs, 8000)
    }

    private fun stopNativePolling() {
        polling = false
        uiHandler.removeCallbacks(pollJobs)
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
        if (nativePanel?.visibility == View.VISIBLE) {
            openFullDashboard()
            return
        }
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
        stopNativePolling()
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
