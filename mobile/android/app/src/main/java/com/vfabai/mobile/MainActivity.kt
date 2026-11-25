package com.vfabai.mobile

import android.annotation.SuppressLint
import android.app.Activity
import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.os.Build
import android.os.Environment
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.FrameLayout
import android.widget.MediaController
import android.widget.VideoView
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
    private var clipsPanel: View? = null
    private var clipsBox: LinearLayout? = null
    private var previewPanel: View? = null
    private var activeClipJobId = ""
    private var pendingShareDownloadId: Long? = null
    private var downloadReceiverRegistered = false
    private val downloadDoneReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val id = intent?.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1L) ?: return
            val manager = getSystemService(DOWNLOAD_SERVICE) as DownloadManager
            val cursor = manager.query(DownloadManager.Query().setFilterById(id))
            var status = -1
            if (cursor != null) {
                if (cursor.moveToFirst()) {
                    status = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
                }
                cursor.close()
            }
            if (id == pendingShareDownloadId) {
                pendingShareDownloadId = null
                if (status == DownloadManager.STATUS_SUCCESSFUL) {
                    val uri = manager.getUriForDownloadedFile(id)
                    if (uri != null) shareClipUri(uri)
                    else Toast.makeText(this@MainActivity, "The clip downloaded, but Android did not return its file URI.", Toast.LENGTH_LONG).show()
                } else {
                    Toast.makeText(this@MainActivity, "Clip download failed. Check the connection and try again.", Toast.LENGTH_LONG).show()
                }
            } else if (status == DownloadManager.STATUS_SUCCESSFUL) {
                Toast.makeText(this@MainActivity, "Clip downloaded to VFAB AI app storage.", Toast.LENGTH_LONG).show()
            }
        }
    }
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
        registerDownloadReceiver()
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
        clipsPanel = null
        clipsBox = null
        previewPanel = null
        activeClipJobId = ""
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
                else if (clipsPanel?.visibility == View.VISIBLE && activeClipJobId.isNotBlank()) refreshNativeClips(activeClipJobId)
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
        clipsPanel?.visibility = View.GONE
        previewPanel?.visibility = View.GONE
        webView?.visibility = View.GONE
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
        clipsPanel?.visibility = View.GONE
        previewPanel?.visibility = View.GONE
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
                setOnClickListener { showNativeClips(id) }
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


    private fun showNativeClips(jobId: String) {
        val frame = rootFrame ?: return
        activeClipJobId = jobId
        stopNativePolling()
        nativePanel?.visibility = View.GONE
        previewPanel?.visibility = View.GONE
        webView?.visibility = View.GONE
        val panel = clipsPanel ?: buildNativeClipsPanel().also {
            clipsPanel = it
            frame.addView(it, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        }
        panel.visibility = View.VISIBLE
        refreshNativeClips(jobId)
    }

    private fun buildNativeClipsPanel(): View {
        val page = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(12), dp(16), dp(16))
            setBackgroundColor(Color.rgb(247, 248, 252))
        }
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        header.addView(text("Ranked clips", 21f, Color.rgb(22, 28, 45), true),
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        val projects = Button(this).apply {
            text = "Projects"
            isAllCaps = false
            setOnClickListener { showNativeHome() }
        }
        header.addView(projects)
        val editor = Button(this).apply {
            text = "Full editor"
            isAllCaps = false
            setOnClickListener { openFullDashboard(activeClipJobId) }
        }
        header.addView(editor)
        page.addView(header)
        page.addView(
            text("Best-scoring qualifying moments first · preview, download or share", 12f, Color.rgb(83, 91, 108)),
            marginParams(top = 3, bottom = 10)
        )
        val refresh = Button(this).apply {
            text = "Refresh clips"
            isAllCaps = false
            setOnClickListener { if (activeClipJobId.isNotBlank()) refreshNativeClips(activeClipJobId) }
        }
        page.addView(refresh)
        val scroll = ScrollView(this)
        val list = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, dp(8), 0, dp(10))
        }
        clipsBox = list
        scroll.addView(list)
        page.addView(scroll, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        return page
    }

    private fun refreshNativeClips(jobId: String) {
        val browser = webView ?: return
        val idLiteral = JSONObject.quote(jobId)
        val script = "(async()=>{try{const id=$idLiteral;" +
            "const r=await fetch('/api/jobs/'+encodeURIComponent(id)+'/clips',{cache:'no-store'});" +
            "return JSON.stringify({ok:r.ok,data:await r.json()});" +
            "}catch(e){return JSON.stringify({ok:false,error:String(e)});}})()"
        browser.evaluateJavascript(script) { raw ->
            val value = decodeJavascriptString(raw)
            try {
                val obj = JSONObject(value)
                if (!obj.optBoolean("ok")) {
                    showNativeClipsMessage("Cannot load clips. Check that the laptop workspace is online.")
                    return@evaluateJavascript
                }
                val data = obj.optJSONObject("data")
                renderNativeClips(data?.optJSONArray("clips") ?: JSONArray())
            } catch (_: Exception) {
                showNativeClipsMessage("Waiting for the clips list.")
            }
        }
    }

    private fun renderNativeClips(clips: JSONArray) {
        val list = clipsBox ?: return
        list.removeAllViews()
        if (clips.length() == 0) {
            list.addView(
                text("No rendered clips yet. Refresh after analysis finishes.", 14f, Color.rgb(83, 91, 108)),
                marginParams(top = 14)
            )
            return
        }
        for (i in 0 until clips.length()) {
            val clip = clips.optJSONObject(i) ?: continue
            val rank = clip.optInt("rank", i + 1)
            val score = clip.optDouble("score", 0.0)
            val titleText = clip.optString("title").ifBlank { "Highlight $rank" }
            val category = clip.optString("category").ifBlank { "Highlight" }
            val start = clip.optDouble("start", 0.0)
            val end = clip.optDouble("end", 0.0)
            val mp4 = clip.optString("mp4_url")
            val reason = clip.optString("reason")
            val card = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(13), dp(12), dp(13), dp(12))
                setBackgroundColor(Color.WHITE)
            }
            card.addView(text("#$rank  ·  ${score.toInt()}/100  ·  $category", 13f, Color.rgb(91, 77, 220), true))
            card.addView(text(titleText, 15f, Color.rgb(22, 28, 45), true), marginParams(top = 4))
            card.addView(
                text("${formatClipTime(start)} – ${formatClipTime(end)}", 12f, Color.rgb(83, 91, 108)),
                marginParams(top = 3)
            )
            if (reason.isNotBlank()) card.addView(text(reason, 12f, Color.rgb(83, 91, 108)), marginParams(top = 4))
            val actions = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.END
            }
            val preview = Button(this).apply {
                text = "Preview"
                isAllCaps = false
                isEnabled = mp4.isNotBlank()
                setOnClickListener { showNativePreview(clip) }
            }
            actions.addView(preview)
            val download = Button(this).apply {
                text = "Download"
                isAllCaps = false
                isEnabled = mp4.isNotBlank()
                setOnClickListener { enqueueClipDownload(clip, false) }
            }
            actions.addView(download)
            val share = Button(this).apply {
                text = "Share"
                isAllCaps = false
                isEnabled = mp4.isNotBlank()
                setOnClickListener { enqueueClipDownload(clip, true) }
            }
            actions.addView(share)
            card.addView(actions, marginParams(top = 6))
            list.addView(card, marginParams(top = 8))
        }
    }

    private fun formatClipTime(value: Double): String {
        val total = value.toInt().coerceAtLeast(0)
        val hours = total / 3600
        val minutes = (total % 3600) / 60
        val seconds = total % 60
        return if (hours > 0) String.format("%d:%02d:%02d", hours, minutes, seconds)
        else String.format("%02d:%02d", minutes, seconds)
    }

    private fun showNativeClipsMessage(message: String) {
        val list = clipsBox ?: return
        list.removeAllViews()
        list.addView(text(message, 14f, Color.rgb(83, 91, 108)), marginParams(top = 14))
    }

    private fun showNativePreview(clip: JSONObject) {
        val frame = rootFrame ?: return
        val relative = clip.optString("mp4_url")
        val base = allowedOrigin ?: return
        if (relative.isBlank()) {
            Toast.makeText(this, "This highlight has no rendered MP4 yet.", Toast.LENGTH_LONG).show()
            return
        }
        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(14), dp(16), dp(16))
            setBackgroundColor(Color.rgb(17, 24, 39))
        }
        val top = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val back = Button(this).apply {
            text = "Back to clips"
            isAllCaps = false
            setOnClickListener {
                (previewPanel as? LinearLayout)?.let { root ->
                    (root.getChildAt(1) as? VideoView)?.stopPlayback()
                }
                previewPanel?.visibility = View.GONE
                clipsPanel?.visibility = View.VISIBLE
            }
        }
        top.addView(back)
        top.addView(
            text(clip.optString("title").ifBlank { "VFAB clip" }, 14f, Color.WHITE, true),
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        )
        panel.addView(top)
        val video = VideoView(this)
        panel.addView(video, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        val media = MediaController(this)
        media.setAnchorView(video)
        video.setMediaController(media)
        val cookie = CookieManager.getInstance().getCookie(base).orEmpty()
        val headers = if (cookie.isBlank()) emptyMap() else mapOf("Cookie" to cookie)
        val clipUri = Uri.parse(base.trimEnd('/') + relative)
        video.setVideoURI(clipUri, headers)
        video.setOnPreparedListener { video.start() }
        video.setOnErrorListener { _, _, _ ->
            Toast.makeText(this, "Preview could not load. Try downloading the clip.", Toast.LENGTH_LONG).show()
            true
        }
        clipsPanel?.visibility = View.GONE
        nativePanel?.visibility = View.GONE
        webView?.visibility = View.GONE
        previewPanel?.let { frame.removeView(it) }
        previewPanel = panel
        frame.addView(panel, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
    }

    private fun enqueueClipDownload(clip: JSONObject, shareAfter: Boolean) {
        val base = allowedOrigin ?: return
        val relative = clip.optString("mp4_url")
        if (relative.isBlank()) {
            Toast.makeText(this, "This highlight has no rendered MP4 yet.", Toast.LENGTH_LONG).show()
            return
        }
        val rank = clip.optInt("rank", 1)
        val titleText = clip.optString("title").ifBlank { "Highlight $rank" }
        val safeJob = activeClipJobId.replace(Regex("[^A-Za-z0-9_-]"), "_")
        val fileName = "VFAB_${safeJob}_clip_${rank}.mp4"
        val uri = Uri.parse(base.trimEnd('/') + relative)
        val request = DownloadManager.Request(uri)
            .setTitle(titleText)
            .setDescription("Downloading VFAB AI clip #$rank")
            .setMimeType("video/mp4")
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setAllowedNetworkTypes(DownloadManager.Request.NETWORK_WIFI or DownloadManager.Request.NETWORK_MOBILE)
            .setDestinationInExternalFilesDir(this, Environment.DIRECTORY_MOVIES, fileName)
        val cookie = CookieManager.getInstance().getCookie(base)
        if (!cookie.isNullOrBlank()) request.addRequestHeader("Cookie", cookie)
        val manager = getSystemService(DOWNLOAD_SERVICE) as DownloadManager
        val id = manager.enqueue(request)
        if (shareAfter) pendingShareDownloadId = id
        Toast.makeText(this, if (shareAfter) "Downloading clip before sharing…" else "Clip download started.", Toast.LENGTH_LONG).show()
    }

    private fun shareClipUri(uri: Uri) {
        val share = Intent(Intent.ACTION_SEND).apply {
            type = "video/mp4"
            putExtra(Intent.EXTRA_STREAM, uri)
            clipData = ClipData.newUri(contentResolver, "VFAB AI clip", uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        try {
            startActivity(Intent.createChooser(share, "Share VFAB AI clip"))
        } catch (_: Exception) {
            Toast.makeText(this, "No compatible app is available to share this clip.", Toast.LENGTH_LONG).show()
        }
    }

    private fun registerDownloadReceiver() {
        if (downloadReceiverRegistered) return
        val filter = IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE)
        if (Build.VERSION.SDK_INT >= 33) {
            registerReceiver(downloadDoneReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(downloadDoneReceiver, filter)
        }
        downloadReceiverRegistered = true
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
        if (previewPanel?.visibility == View.VISIBLE) {
            (previewPanel as? LinearLayout)?.let { root ->
                (root.getChildAt(1) as? VideoView)?.stopPlayback()
            }
            previewPanel?.visibility = View.GONE
            clipsPanel?.visibility = View.VISIBLE
            return
        }
        if (clipsPanel?.visibility == View.VISIBLE) {
            showNativeHome()
            return
        }
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
        if (downloadReceiverRegistered) {
            unregisterReceiver(downloadDoneReceiver)
            downloadReceiverRegistered = false
        }
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
