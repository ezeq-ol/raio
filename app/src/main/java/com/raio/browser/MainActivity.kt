package com.raio.browser

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.app.DownloadManager
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import android.net.http.SslError
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.text.Editable
import android.text.InputType
import android.text.TextUtils
import android.text.TextWatcher
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.webkit.CookieManager
import android.webkit.DownloadListener
import android.webkit.JsResult
import android.webkit.PermissionRequest
import android.webkit.SslErrorHandler
import android.webkit.URLUtil
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.EditText
import android.widget.AutoCompleteTextView
import android.widget.ArrayAdapter
import android.app.ActivityManager
import android.os.SystemClock
import java.util.concurrent.atomic.AtomicInteger
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import java.io.ByteArrayInputStream

class Tab(@Volatile var url: String, var title: String) {
    var view: WebView? = null
    var state: Bundle? = null
    var x = 0; var y = 0
    var restoreScroll = false
    var used = 0L
    var config = ""
    val blocked = AtomicInteger()
}

private data class TabSnapshot(val url: String, val title: String, val state: Bundle?, val x: Int, val y: Int)
private data class BrowserSnapshot(val tabs: List<TabSnapshot>, val current: Int)

@Suppress("DEPRECATION")
open class MainActivity : Activity() {
    private val MATCH = ViewGroup.LayoutParams.MATCH_PARENT
    private val MAX_TABS = 8
    private val DESKTOP_UA =
        "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"

    private lateinit var pal: Pal
    private lateinit var web: WebView
    private lateinit var root: LinearLayout
    private lateinit var topBar: LinearLayout
    private lateinit var bottomBar: LinearLayout
    private lateinit var findBar: LinearLayout
    private lateinit var findBox: EditText
    private lateinit var urlBox: AutoCompleteTextView
    private lateinit var progress: ProgressBar
    private lateinit var content: FrameLayout
    private lateinit var full: FrameLayout
    private lateinit var btnBack: Ic
    private lateinit var btnFwd: Ic
    private lateinit var btnTabs: Ic
    private lateinit var shieldButton: TextView
    private lateinit var sitePermissions: SitePermissions
    private var lastShortcuts = ""
    private lateinit var btnReload: Ic

    private val tabs = mutableListOf<Tab>()
    private var cur = 0
    private var tabClock = 0L
    private var fileRequestCode = 1000
    private var activeFileRequestCode: Int? = null
    private var mobileUA = ""
    private var appliedDesktop = false
    private var loading = false
    private var customView: View? = null
    private var customCb: WebChromeClient.CustomViewCallback? = null
    private var fileCb: ValueCallback<Array<Uri>>? = null
    private var previousSystemUi = 0
    private var previousCutoutMode = 0
    private var previousWindowFlags = 0
    @Volatile private var pageUrl = ""
    private var appliedShield = ""
    private var pendingDl: Array<String>? = null

    // ---------------------------------------------------------------- ciclo de vida

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        if ((this is PrivateMainActivity && Build.VERSION.SDK_INT < 28) || intent.getBooleanExtra("exitPrivate", false)) { finish(); return }
        val started = SystemClock.elapsedRealtime()
        Prefs.init(this)
        if (Prefs.incognito) window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        pal = Pal(isDark(this))
        buildUi()
        sitePermissions = SitePermissions(this) { if (::web.isInitialized) web.url ?: pageUrl else "" }

        val retained = lastNonConfigurationInstance as? BrowserSnapshot
        if (retained != null) {
            retained.tabs.take(MAX_TABS).forEach { t ->
                tabs.add(Tab(t.url, t.title).apply { state = t.state; x = t.x; y = t.y })
            }
        } else if (Prefs.restoreTabs && !Prefs.incognito) {
            Prefs.list("tabs").take(MAX_TABS).forEach { tabs.add(Tab(it.url, it.title)) }
        }
        if (tabs.isEmpty()) tabs.add(Tab(homeUrl(), ""))
        cur = (retained?.current ?: Prefs.curTab).coerceIn(0, tabs.size - 1)
        showTab(cur)
        handleIntent(intent)
        RaioApp.screenStartupMs = SystemClock.elapsedRealtime() - started
        AppUpdates.automatic(this)
    }

    override fun onNewIntent(i: Intent) {
        super.onNewIntent(i)
        setIntent(i)
        if (i.getBooleanExtra("exitPrivate", false) && Prefs.incognito) { finishAndRemoveTask(); return }
        handleIntent(i)
    }

    private fun handleIntent(i: Intent?) {
        val u = i?.dataString ?: return
        if (i.action == Intent.ACTION_VIEW) {
            i.data = null
            newTab(u)
        }
    }

    override fun onResume() {
        super.onResume()
        if (!::web.isInitialized) return
        if (isDark(this) != pal.dark) {
            recreate()
            return
        }
        refreshSuggestions()
        val shortcuts = Prefs.shortcuts().joinToString { it.title + it.url }
        if (lastShortcuts != shortcuts && tabs[cur].url == HOME) load(HOME)
        lastShortcuts = shortcuts
        applyWebSettings()
        if (customView == null) applyBars() else applyImmersive()
        val shield = shieldState()
        if (appliedShield.isNotEmpty() && appliedShield != shield) web.reload()
        appliedShield = shield
        web.onResume()
    }

    @Deprecated("Activity retention API")
    override fun onRetainNonConfigurationInstance(): Any {
        tabs.forEach { rememberTab(it) }
        return BrowserSnapshot(tabs.map { TabSnapshot(it.url, it.title, it.state, it.x, it.y) }, cur)
    }

    override fun onPause() {
        super.onPause()
        if (::web.isInitialized) web.onPause()
        if (::web.isInitialized) saveTabs()
    }

    private fun saveTabs() {
        if (Prefs.incognito) return
        if (Prefs.restoreTabs) Prefs.saveList("tabs", tabs.map { Entry(it.title, it.url) })
        Prefs.curTab = cur
    }

    override fun onDestroy() {
        if (::web.isInitialized && isFinishing && (Prefs.incognito || Prefs.clearExit)) clearBrowsingData()
        if (customView != null) hideCustom()
        if (::sitePermissions.isInitialized) sitePermissions.cancel()
        fileCb?.onReceiveValue(null); fileCb = null; activeFileRequestCode = null
        tabs.forEach { releaseTab(it) }
        super.onDestroy()
        if (isFinishing && Prefs.incognito) {
            if (Build.VERSION.SDK_INT >= 24) deleteSharedPreferences("raio_private")
            runCatching { RaioApp.clearPrivateProfiles(this) }
            android.os.Process.killProcess(android.os.Process.myPid())
        }
    }

    private fun clearBrowsingData() {
        try {
            web.clearCache(true)
            web.clearFormData()
            web.clearHistory()
            CookieManager.getInstance().removeAllCookies(null)
            android.webkit.WebStorage.getInstance().deleteAllData()
            Prefs.saveList("hist", emptyList())
            Prefs.saveList("tabs", emptyList())
        } catch (_: Exception) {
        }
    }

    // ---------------------------------------------------------------- interface

    private fun barColor() = if (Prefs.incognito) pal.incog else pal.bar

    private fun applyBars() {
        val incog = Prefs.incognito
        val c = barColor()
        topBar.setBackgroundColor(c)
        bottomBar.setBackgroundColor(c)
        urlBox.background = round(if (incog) 0x33FFFFFF else pal.field, dp(20).toFloat())
        val iconCol = if (incog) Color.WHITE else pal.text
        btnReload.col = iconCol; btnReload.invalidate()
        for (i in 0 until bottomBar.childCount) (bottomBar.getChildAt(i) as? Ic)?.let { it.col = iconCol; it.invalidate() }
        shieldButton.setTextColor(iconCol)
        urlBox.hint = if (incog) "Anônimo · pesquisar ou digitar endereço" else "Pesquisar ou digitar endereço"
        urlBox.setTextColor(iconCol)
        urlBox.setHintTextColor(if (incog) 0xFFBBB0D0.toInt() else pal.sub)
        root.setBackgroundColor(c)
        styleBars(c, pal.dark || incog)
    }

    private fun ripple(v: View) {
        val ta = obtainStyledAttributes(intArrayOf(android.R.attr.selectableItemBackgroundBorderless))
        v.background = ta.getDrawable(0)
        ta.recycle()
    }

    private fun buildUi() {
        root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(pal.bar)
            fitsSystemWindows = true
        }

        // barra superior: endereco + recarregar
        topBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(10), 0, dp(4), 0)
        }
        urlBox = AutoCompleteTextView(this).apply {
            threshold = 1
            hint = "Pesquisar ou digitar endereço"
            textSize = 15f
            setSingleLine()
            ellipsize = TextUtils.TruncateAt.END
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI
            imeOptions = EditorInfo.IME_ACTION_GO
            setPadding(dp(16), 0, dp(16), 0)
            setSelectAllOnFocus(true)
            setOnEditorActionListener { _, a, e ->
                if (a == EditorInfo.IME_ACTION_GO || (e != null && e.keyCode == KeyEvent.KEYCODE_ENTER && e.action == KeyEvent.ACTION_DOWN)) {
                    go(text.toString())
                    true
                } else false
            }
            setOnFocusChangeListener { _, f ->
                if (f) {
                    setText(fullUrl())
                    selectAll()
                } else refreshUrlBox()
            }
        }
        btnReload = Ic(this, Ic.RELOAD, pal.text).apply {
            ripple(this)
            setOnClickListener { if (loading) web.stopLoading() else web.reload() }
        }
        topBar.addView(urlBox, LinearLayout.LayoutParams(0, dp(40), 1f))
        shieldButton = TextView(this).apply {
            text = "● 0"; textSize = 13f; gravity = Gravity.CENTER
            contentDescription = "Proteção e permissões deste site"
            setOnClickListener { showSiteControls() }
        }
        topBar.addView(shieldButton, LinearLayout.LayoutParams(dp(52), dp(46)))
        topBar.addView(btnReload, LinearLayout.LayoutParams(dp(46), dp(46)))
        root.addView(topBar, LinearLayout.LayoutParams(MATCH, dp(52)))

        progress = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            max = 100
            visibility = View.GONE
            progressTintList = ColorStateList.valueOf(pal.accent)
            progressBackgroundTintList = ColorStateList.valueOf(Color.TRANSPARENT)
        }
        root.addView(progress, LinearLayout.LayoutParams(MATCH, dp(3)))

        // barra "localizar na pagina"
        findBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setBackgroundColor(pal.bar)
            setPadding(dp(10), 0, dp(6), 0)
            visibility = View.GONE
        }
        findBox = EditText(this).apply {
            hint = "Localizar na página"
            textSize = 15f
            setSingleLine()
            setTextColor(pal.text)
            setHintTextColor(pal.sub)
            addTextChangedListener(object : TextWatcher {
                override fun afterTextChanged(s: Editable?) {
                    web.findAllAsync(s?.toString() ?: "")
                }
                override fun beforeTextChanged(s: CharSequence?, st: Int, c: Int, a: Int) {}
                override fun onTextChanged(s: CharSequence?, st: Int, b: Int, c: Int) {}
            })
        }
        findBar.addView(findBox, LinearLayout.LayoutParams(0, dp(44), 1f))
        findBar.addView(textBtn("Ant.") { web.findNext(false) })
        findBar.addView(textBtn("Próx.") { web.findNext(true) })
        findBar.addView(textBtn("Fechar") { closeFind() })
        root.addView(findBar, LinearLayout.LayoutParams(MATCH, dp(46)))

        content = FrameLayout(this)
        full = FrameLayout(this).apply {
            setBackgroundColor(Color.BLACK)
            visibility = View.GONE
        }
        root.addView(content, LinearLayout.LayoutParams(MATCH, 0, 1f))

        root.addView(View(this).apply { setBackgroundColor(pal.line) }, LinearLayout.LayoutParams(MATCH, 1))

        // barra inferior: voltar, avancar, inicio, abas, menu
        bottomBar = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        btnBack = Ic(this, Ic.BACK, pal.text).apply { setOnClickListener { if (web.canGoBack()) web.goBack() } }
        btnFwd = Ic(this, Ic.FWD, pal.text).apply { setOnClickListener { if (web.canGoForward()) web.goForward() } }
        val btnHome = Ic(this, Ic.HOME, pal.text).apply { setOnClickListener { load(homeUrl()) } }
        btnTabs = Ic(this, Ic.TABS, pal.text).apply { setOnClickListener { showTabs() } }
        val btnMenu = Ic(this, Ic.MENU, pal.text).apply { setOnClickListener { showMenu() } }
        for (b in listOf(btnBack, btnFwd, btnHome, btnTabs, btnMenu)) {
            ripple(b)
            bottomBar.addView(b, LinearLayout.LayoutParams(0, MATCH, 1f))
        }
        root.addView(bottomBar, LinearLayout.LayoutParams(MATCH, dp(52)))

        setContentView(root)
    }

    private fun textBtn(t: String, onClick: () -> Unit) = TextView(this).apply {
        text = t
        textSize = 14f
        setTextColor(pal.accent)
        gravity = Gravity.CENTER
        setPadding(dp(10), 0, dp(10), 0)
        setOnClickListener { onClick() }
    }

    // ---------------------------------------------------------------- WebView

    private fun setupWeb(owner: Tab) {
        web = WebView(this)
        owner.view = web
        RaioApp.residentViews++
        val tabView = web
        web.setBackgroundColor(pal.bg)
        content.addView(web, 0, FrameLayout.LayoutParams(MATCH, MATCH))
        mobileUA = web.settings.userAgentString.replace("; wv", "").replace("Version/4.0 ", "")
        applyWebSettings()

        web.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(v: WebView, r: WebResourceRequest): Boolean {
                val sch = r.url.scheme ?: ""
                if (sch == "http" || sch == "https" || sch == "about" || sch == "data" || sch == "blob" || sch == "javascript") return false
                if (sch == "file") return true
                if (Prefs.sitePopups(owner.url) && (!r.isForMainFrame || !r.hasGesture())) return true
                openExternal(r.url.toString())
                return true
            }

            override fun shouldInterceptRequest(v: WebView, r: WebResourceRequest): WebResourceResponse? {
                if (Prefs.shieldEnabled(owner.url) && !r.isForMainFrame &&
                    AdBlock.shouldBlock(r.url.toString(), owner.url, Prefs.aggressive)) {
                    val count = owner.blocked.incrementAndGet()
                    if (count <= 3 || count % 10 == 0) runOnUiThread { if (!isFinishing && !isDestroyed && owner.view === tabView && web === tabView) updateNav() }
                    return WebResourceResponse("text/plain", "utf-8", ByteArrayInputStream(ByteArray(0)))
                }
                return null
            }

            override fun onPageStarted(v: WebView, url: String?, f: Bitmap?) {
                if (owner.view !== v) return
                owner.url = url ?: owner.url
                owner.blocked.set(0)
                owner.config = stateFor(owner.url)
                if (v !== web) return
                sitePermissions.cancel()
                pageUrl = owner.url
                applyWebSettings()
                appliedShield = shieldState()
                loading = true
                btnReload.let { setReloadKind(true) }
                syncUrl(url)
            }

            override fun onPageFinished(v: WebView, url: String?) {
                if (owner.view !== v || !tabs.contains(owner)) return
                if (url != null && !url.startsWith("data:")) owner.url = url
                if (owner.restoreScroll) {
                    owner.restoreScroll = false
                    val x = owner.x; val y = owner.y
                    v.post { if (owner.view === v) v.scrollTo(x, y) }
                }
                if (owner.url != HOME && !owner.url.startsWith("data:") && v.title != StartPage.ERR_TITLE) {
                    owner.title = v.title ?: owner.url
                    addHistory(owner.title, owner.url)
                }
                injectShield(v, owner.url)
                if (v !== web) return
                loading = false
                setReloadKind(false)
                syncUrl(url)
                refreshSuggestions()
                updateNav()
            }

            override fun doUpdateVisitedHistory(v: WebView, url: String?, isReload: Boolean) {
                if (owner.view !== v) return
                if (url != null && !url.startsWith("data:")) owner.url = url
                injectShield(v, owner.url)
                if (v !== web) return
                syncUrl(url)
                pageUrl = owner.url
                updateNav()
            }

            @android.annotation.TargetApi(23)
            override fun onReceivedError(v: WebView, r: WebResourceRequest, e: WebResourceError) {
                if (v === web && r.isForMainFrame) showError(r.url.toString(), e.description?.toString() ?: "")
            }

            @Deprecated("Deprecated in Java")
            override fun onReceivedError(v: WebView, code: Int, desc: String?, failing: String?) {
                if (v === web && Build.VERSION.SDK_INT < 23 && failing != null) showError(failing, desc ?: "")
            }

            override fun onReceivedSslError(v: WebView, h: SslErrorHandler, e: SslError) {
                h.cancel()
                if (v === web) showError(e.url ?: "", "Conexão não segura (certificado inválido).")
            }
        }

        web.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(v: WebView, p: Int) {
                if (owner.view !== v || v !== web) return
                progress.progress = p
                progress.visibility = if (p < 100) View.VISIBLE else View.GONE
            }

            override fun onReceivedTitle(v: WebView, t: String?) {
                if (owner.view === v && t != null && v.url != HOME) owner.title = t
            }

            override fun onShowCustomView(view: View, cb: CustomViewCallback) {
                if (tabView !== web || customView != null) {
                    cb.onCustomViewHidden()
                    return
                }
                customView = view
                customCb = cb
                hideKeyboard()
                previousSystemUi = window.decorView.systemUiVisibility
                previousWindowFlags = window.attributes.flags
                if (Build.VERSION.SDK_INT >= 28) previousCutoutMode = window.attributes.layoutInDisplayCutoutMode
                (window.decorView as ViewGroup).addView(full, ViewGroup.LayoutParams(MATCH, MATCH))
                full.addView(view, FrameLayout.LayoutParams(MATCH, MATCH, Gravity.CENTER))
                full.visibility = View.VISIBLE
                setFullscreen(true)
            }

            override fun onHideCustomView() { if (tabView === web) hideCustom() }

            override fun onShowFileChooser(v: WebView, cb: ValueCallback<Array<Uri>>, p: FileChooserParams): Boolean {
                if (tabView !== web) return false
                fileCb?.onReceiveValue(null)
                fileCb = cb
                fileRequestCode = if (fileRequestCode >= 65000) 1000 else fileRequestCode + 1
                activeFileRequestCode = fileRequestCode
                return try {
                    startActivityForResult(p.createIntent(), fileRequestCode)
                    true
                } catch (_: Exception) {
                    fileCb = null
                    false
                }
            }

            override fun onPermissionRequest(r: PermissionRequest) {
                if (owner.view === tabView && tabView === web && !isFinishing) sitePermissions.media(r) else r.deny()
            }
            override fun onPermissionRequestCanceled(r: PermissionRequest) = sitePermissions.cancelled(r)

            override fun onGeolocationPermissionsShowPrompt(o: String, cb: android.webkit.GeolocationPermissions.Callback) {
                if (tabView === web) sitePermissions.location(o, cb) else cb.invoke(o, false, false)
            }

            override fun onJsAlert(v: WebView, url: String?, m: String?, r: JsResult): Boolean {
                if (owner.view !== v || v !== web) { r.cancel(); return true }
                dlg().setMessage(m).setPositiveButton("OK") { _, _ -> r.confirm() }
                    .setOnCancelListener { r.cancel() }.show()
                return true
            }

            override fun onJsConfirm(v: WebView, url: String?, m: String?, r: JsResult): Boolean {
                if (owner.view !== v || v !== web) { r.cancel(); return true }
                dlg().setMessage(m).setPositiveButton("OK") { _, _ -> r.confirm() }
                    .setNegativeButton("Cancelar") { _, _ -> r.cancel() }
                    .setOnCancelListener { r.cancel() }.show()
                return true
            }
        }

        web.setDownloadListener(DownloadListener { url, ua, cd, mime, _ -> download(url, ua, cd, mime) })

        web.setOnLongClickListener {
            val h = web.hitTestResult
            val ex = h.extra
            if (ex == null) return@setOnLongClickListener false
            when (h.type) {
                WebView.HitTestResult.SRC_ANCHOR_TYPE, WebView.HitTestResult.SRC_IMAGE_ANCHOR_TYPE -> {
                    linkMenu(ex); true
                }
                WebView.HitTestResult.IMAGE_TYPE -> {
                    imageMenu(ex); true
                }
                else -> false
            }
        }
    }

    private fun applyWebSettings() {
        val s = web.settings
        s.javaScriptEnabled = Prefs.siteJs(pageUrl)
        s.domStorageEnabled = true
        s.allowFileAccess = false
        s.allowContentAccess = false
        s.setGeolocationEnabled(!Prefs.permissionBlocked(pageUrl, "location"))
        s.mediaPlaybackRequiresUserGesture = true
        s.javaScriptCanOpenWindowsAutomatically = !Prefs.sitePopups(pageUrl)
        // Unhandled popup windows are rejected by WebChromeClient.
        s.setSupportMultipleWindows(Prefs.sitePopups(pageUrl))
        s.blockNetworkImage = Prefs.blockImages
        s.textZoom = Prefs.textSize
        s.setSupportZoom(true)
        s.builtInZoomControls = true
        s.displayZoomControls = false
        s.cacheMode = WebSettings.LOAD_DEFAULT
        s.useWideViewPort = true
        s.loadWithOverviewMode = Prefs.desktop
        s.userAgentString = if (Prefs.desktop) DESKTOP_UA else mobileUA
        if (Build.VERSION.SDK_INT >= 29) {
            s.forceDark = if (Prefs.webDark) WebSettings.FORCE_DARK_ON else WebSettings.FORCE_DARK_OFF
        }
        CookieManager.getInstance().setAcceptCookie(true)
        CookieManager.getInstance().setAcceptThirdPartyCookies(web, Prefs.siteCookies(pageUrl))
        if (appliedDesktop != Prefs.desktop) {
            val first = !appliedDesktop && !Prefs.desktop
            appliedDesktop = Prefs.desktop
            if (!first && web.url != null && web.url != HOME) web.reload()
        }
    }

    // ---------------------------------------------------------------- navegacao e abas

    private fun homeUrl(): String {
        val h = Prefs.home.trim()
        return if (h.isEmpty()) HOME else resolve(h)
    }

    private fun fullUrl(): String {
        val u = tabs[cur].url
        return if (u == HOME || u.startsWith("data:")) "" else u
    }

    private fun refreshUrlBox() {
        if (urlBox.hasFocus()) return
        val u = tabs[cur].url
        urlBox.setText(if (u == HOME || u.startsWith("data:")) "" else (if (u.startsWith("http://")) "HTTP · " else "") + hostOf(u))
    }

    private fun syncUrl(url: String?) {
        if (url == null || url.startsWith("data:")) return
        tabs[cur].url = url
        refreshUrlBox()
    }

    private fun setReloadKind(stop: Boolean) {
        // troca o desenho do botao: parar durante carregamento, recarregar depois
        val parent = btnReload.parent as ViewGroup
        val idx = parent.indexOfChild(btnReload)
        val want = if (stop) Ic.STOP else Ic.RELOAD
        if (btnReload.kind == want) return
        val n = Ic(this, want, btnReload.col)
        ripple(n)
        n.setOnClickListener { if (loading) web.stopLoading() else web.reload() }
        parent.removeViewAt(idx)
        parent.addView(n, idx, LinearLayout.LayoutParams(dp(46), dp(46)))
        btnReload = n
    }

    private fun updateNav() {
        btnBack.isEnabled = web.canGoBack()
        btnFwd.isEnabled = web.canGoForward()
        btnTabs.badge = tabs.size.toString()
        btnTabs.invalidate()
        shieldButton.text = (if (Prefs.shieldEnabled(pageUrl)) "● " else "○ ") + tabs.getOrNull(cur)?.blocked?.get().toString()
    }

    private fun go(input: String) {
        urlBox.clearFocus()
        load(resolve(input))
    }

    private fun load(u: String) {
        tabs[cur].url = u
        pageUrl = u
        appliedShield = shieldState()
        applyWebSettings()
        closeFind()
        if (u == HOME) {
            web.loadDataWithBaseURL(HOME, StartPage.html(if (Prefs.incognito) Pal(true) else pal), "text/html", "utf-8", HOME)
        } else if (Prefs.dnt) {
            web.loadUrl(u, mapOf("DNT" to "1", "Sec-GPC" to "1"))
        } else {
            web.loadUrl(u)
        }
        web.requestFocus()
        hideKeyboard()
        refreshUrlBox()
        updateNav()
    }

    private fun rememberTab(tab: Tab) {
        tab.view?.let { v ->
            tab.x = v.scrollX; tab.y = v.scrollY
            tab.state = Bundle().also { v.saveState(it) }
        }
    }

    private fun releaseTab(tab: Tab) {
        val v = tab.view ?: return
        tab.view = null
        (v.parent as? ViewGroup)?.removeView(v)
        v.stopLoading(); v.onPause(); v.destroy()
        RaioApp.residentViews--
    }

    private fun trimTabs(limit: Int) {
        val inactive = tabs.filter { it.view != null && it !== tabs.getOrNull(cur) }.sortedBy { it.used }
        var resident = tabs.count { it.view != null }
        for (tab in inactive) {
            if (resident <= limit) break
            rememberTab(tab); releaseTab(tab); resident--
        }
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        if (level >= android.content.ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW) trimTabs(1)
    }

    private fun showTab(i: Int) {
        if (customView != null) hideCustom()
        sitePermissions.cancel()
        fileCb?.onReceiveValue(null); fileCb = null; activeFileRequestCode = null
        if (::web.isInitialized && web.parent === content) {
            tabs.getOrNull(cur)?.let { rememberTab(it) }
            web.onPause()
            content.removeView(web)
        }
        cur = i
        val tab = tabs[i]
        pageUrl = tab.url
        tab.used = ++tabClock
        closeFind()
        if (tab.view == null) {
            setupWeb(tab)
            tab.restoreScroll = tab.state != null
            val restored = tab.state?.let { web.restoreState(it) != null } ?: false
            if (!restored || tab.url == HOME) load(tab.url)
        } else {
            web = tab.view!!
            content.addView(web, 0, FrameLayout.LayoutParams(MATCH, MATCH))
            applyWebSettings()
            if (tab.config != stateFor(tab.url)) web.reload()
        }
        web.onResume()
        loading = web.progress < 100
        setReloadKind(loading)
        appliedShield = shieldState()
        refreshUrlBox(); updateNav(); applyBars()
        val lowRam = (getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager).isLowRamDevice
        trimTabs(if (lowRam) 1 else 2)
    }

    private fun newTab(url: String = homeUrl()) {
        if (tabs.size >= MAX_TABS) { toast("Limite de $MAX_TABS abas (modo leve)"); return }
        tabs.add(Tab(url, ""))
        showTab(tabs.lastIndex)
    }

    private fun closeTab(i: Int) {
        val current = i == cur
        releaseTab(tabs[i])
        tabs.removeAt(i)
        if (tabs.isEmpty()) tabs.add(Tab(homeUrl(), ""))
        if (current) { cur = -1; showTab(minOf(i, tabs.lastIndex)) }
        else { if (i < cur) cur--; updateNav() }
    }

    private fun addHistory(title: String, url: String) {
        if (Prefs.incognito) return
        val l = Prefs.list("hist")
        if (l.isNotEmpty() && l[0].url == url) l.removeAt(0)
        l.add(0, Entry(title, url))
        while (l.size > 300) l.removeAt(l.size - 1)
        Prefs.saveList("hist", l)
    }

    private fun showError(url: String, msg: String) {
        web.loadDataWithBaseURL(url, StartPage.error(pal, url, msg), "text/html", "utf-8", url)
    }

    private fun openExternal(u: String) {
        try {
            val i = if (u.startsWith("intent:")) Intent.parseUri(u, Intent.URI_INTENT_SCHEME) else Intent(Intent.ACTION_VIEW, Uri.parse(u))
            i.addCategory(Intent.CATEGORY_BROWSABLE)
            i.component = null
            i.selector = null
            startActivity(i)
        } catch (_: Exception) {
            toast("Nenhum app para abrir este link")
        }
    }

    // ---------------------------------------------------------------- dialogos

    private fun dlg(): AlertDialog.Builder = AlertDialog.Builder(
        this,
        if (pal.dark) android.R.style.Theme_Material_Dialog_Alert else android.R.style.Theme_Material_Light_Dialog_Alert
    )

    private fun showTabs() {
        val ll = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(8), dp(4), dp(8), dp(4))
        }
        val sv = ScrollView(this).apply { addView(ll) }
        lateinit var d: AlertDialog
        tabs.forEachIndexed { i, t ->
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(10), dp(8), dp(4), dp(8))
                if (i == cur) background = round(0x22FF6B1A, dp(10).toFloat())
                setOnClickListener { d.dismiss(); showTab(i) }
            }
            val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
            val title = if (t.url == HOME) "Nova aba" else if (t.title.isBlank()) hostOf(t.url) else t.title
            col.addView(TextView(this).apply {
                text = title
                textSize = 15f
                setSingleLine()
                ellipsize = TextUtils.TruncateAt.END
                setTextColor(if (i == cur) pal.accent else pal.text)
            })
            col.addView(TextView(this).apply {
                text = if (t.url == HOME) "Página inicial" else hostOf(t.url)
                textSize = 12f
                setSingleLine()
                ellipsize = TextUtils.TruncateAt.END
                setTextColor(pal.sub)
            })
            row.addView(col, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            row.addView(Ic(this, Ic.STOP, pal.sub).apply {
                setOnClickListener {
                    d.dismiss()
                    closeTab(i)
                    showTabs()
                }
            }, LinearLayout.LayoutParams(dp(44), dp(44)))
            ll.addView(row)
        }
        d = dlg().setTitle("Abas (${tabs.size})").setView(sv)
            .setPositiveButton("Nova aba") { _, _ -> newTab() }
            .setNeutralButton("Fechar todas") { _, _ ->
                tabs.forEach { releaseTab(it) }
                tabs.clear()
                tabs.add(Tab(homeUrl(), ""))
                cur = -1
                showTab(0)
            }
            .create()
        d.show()
    }

    private fun showMenu() {
        val url = web.url ?: tabs[cur].url
        val isFav = Prefs.list("fav").any { it.url == url }
        val items = listOf<Pair<String, () -> Unit>>(
            "Nova aba" to { newTab() },
            (if (Prefs.shieldEnabled(pageUrl)) "Desativar proteção neste site" else "Ativar proteção neste site") to {
                if (AdBlock.host(pageUrl).isNotEmpty()) {
                    val wasEnabled = Prefs.shieldEnabled(pageUrl)
                    if (!Prefs.adblock) Prefs.adblock = true
                    Prefs.setShieldException(pageUrl, wasEnabled)
                    appliedShield = shieldState()
                    web.reload()
                } else toast("Abra um site para alterar a proteção")
            },
            (if (isFav) "Remover dos favoritos" else "Adicionar aos favoritos") to { toggleFav() },
            "Favoritos" to { openLibrary("fav") },
            "Histórico" to { openLibrary("hist") },
            "Downloads" to { startActivity(Intent(this, if (Prefs.incognito) PrivateDownloadsActivity::class.java else DownloadsActivity::class.java)) },
            "Proteção e permissões deste site" to { showSiteControls() },
            "Editar atalhos" to { startActivity(Intent(this, if (Prefs.incognito) PrivateShortcutsActivity::class.java else ShortcutsActivity::class.java)) },
            "Localizar na página" to { openFind() },
            "Compartilhar página" to { sharePage() },
            (if (Prefs.desktop) "Versão para celular" else "Versão para computador") to {
                Prefs.desktop = !Prefs.desktop
                applyWebSettings()
            },
            (if (Prefs.incognito) "Desligar modo anônimo" else "Ligar modo anônimo") to {
                switchPrivacy(this, !Prefs.incognito)
            },
            "Configurações" to { startActivity(Intent(this, if (Prefs.incognito) PrivateSettingsActivity::class.java else SettingsActivity::class.java)) },
            "Sair" to {
                if (Prefs.incognito || Prefs.clearExit) clearBrowsingData()
                finishAndRemoveTask()
            }
        )
        dlg().setItems(items.map { it.first }.toTypedArray()) { _, i -> items[i].second() }.show()
    }

    private fun linkMenu(u: String) {
        val items = listOf<Pair<String, () -> Unit>>(
            "Abrir em nova aba" to { newTab(u) },
            "Copiar link" to { copy(u) },
            "Compartilhar link" to { share(u) }
        )
        dlg().setTitle(hostOf(u)).setItems(items.map { it.first }.toTypedArray()) { _, i -> items[i].second() }.show()
    }

    private fun imageMenu(u: String) {
        val items = listOf<Pair<String, () -> Unit>>(
            "Baixar imagem" to { download(u, web.settings.userAgentString, null, null) },
            "Abrir imagem em nova aba" to { newTab(u) },
            "Copiar endereço da imagem" to { copy(u) }
        )
        dlg().setItems(items.map { it.first }.toTypedArray()) { _, i -> items[i].second() }.show()
    }

    private fun toggleFav() {
        val u = web.url ?: tabs[cur].url
        if (u == HOME || u.startsWith("data:")) {
            toast("Abra um site para favoritar")
            return
        }
        val l = Prefs.list("fav")
        val ex = l.indexOfFirst { it.url == u }
        if (ex >= 0) {
            l.removeAt(ex)
            toast("Removido dos favoritos")
        } else {
            l.add(0, Entry(web.title ?: hostOf(u), u))
            toast("Favorito adicionado")
        }
        Prefs.saveList("fav", l)
    }

    private fun openLibrary(mode: String) {
        startActivityForResult(Intent(this, if (Prefs.incognito) PrivateLibraryActivity::class.java else LibraryActivity::class.java).putExtra("mode", mode), 11)
    }

    override fun onActivityResult(req: Int, res: Int, data: Intent?) {
        if (req == 11 && res == RESULT_OK && data != null) {
            val u = data.getStringExtra("url") ?: return
            if (data.getBooleanExtra("newtab", false)) newTab(u) else load(u)
        } else if (req in 1000..65000) {
            if (req == activeFileRequestCode) {
                fileCb?.onReceiveValue(WebChromeClient.FileChooserParams.parseResult(res, data))
                fileCb = null; activeFileRequestCode = null
            }
        } else super.onActivityResult(req, res, data)
    }

    private fun openFind() {
        findBar.visibility = View.VISIBLE
        findBox.requestFocus()
        (getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager).showSoftInput(findBox, 0)
    }

    private fun closeFind() {
        if (findBar.visibility != View.VISIBLE) return
        web.clearMatches()
        findBox.setText("")
        findBar.visibility = View.GONE
        hideKeyboard()
    }

    // ---------------------------------------------------------------- utilitarios

    private fun download(url: String, ua: String?, cd: String?, mime: String?) {
        if (url.startsWith("blob:") || url.startsWith("data:")) {
            toast("Este tipo de download não é suportado")
            return
        }
        if (Build.VERSION.SDK_INT in 23..28 && checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            pendingDl = arrayOf(url, ua ?: "", cd ?: "", mime ?: "")
            requestPermissions(arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE), 21)
            return
        }
        try {
            val name = URLUtil.guessFileName(url, cd, mime)
            DownloadRecords.enqueue(this, url, name, mime, ua, CookieManager.getInstance().getCookie(url))
        } catch (_: Exception) {
            toast("Falha ao iniciar o download")
        }
    }

    override fun onRequestPermissionsResult(code: Int, perms: Array<out String>, res: IntArray) {
        if (code == 55) { sitePermissions.runtimeResult(res); return }
        if (code == 21 && res.isNotEmpty() && res[0] == PackageManager.PERMISSION_GRANTED) {
            pendingDl?.let { download(it[0], it[1], it[2], it[3]) }
        }
        pendingDl = null
    }

    private fun copy(t: String) {
        (getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("link", t))
        toast("Copiado")
    }

    private fun share(t: String) {
        startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, t), "Compartilhar"))
    }

    private fun sharePage() {
        val u = web.url ?: return
        if (u == HOME || u.startsWith("data:")) toast("Nada para compartilhar") else share(u)
    }

    private fun toast(m: String) = Toast.makeText(this, m, Toast.LENGTH_SHORT).show()

    private fun hideKeyboard() {
        (getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager).hideSoftInputFromWindow(root.windowToken, 0)
    }

    private fun stateFor(url: String) = listOf(Prefs.shieldEnabled(url), Prefs.aggressive,
        Prefs.siteJs(url), Prefs.siteCookies(url), Prefs.sitePopups(url), Prefs.blockImages,
        Prefs.desktop, Prefs.textSize, Prefs.webDark).joinToString(":")
    private fun shieldState() = stateFor(pageUrl)

    private fun injectShield(view: WebView = web, url: String = pageUrl) {
        if (Prefs.siteJs(url) && Prefs.aggressive && Prefs.shieldEnabled(url)) {
            view.evaluateJavascript(ShieldScript.source, null)
        }
    }

    private fun refreshSuggestions() {
        val entries = (Prefs.list("fav") + if (Prefs.incognito) emptyList() else Prefs.list("hist"))
            .distinctBy { it.url }.take(80)
        val labels = entries.map { (it.title.ifBlank { hostOf(it.url) }) + " — " + it.url }
        val adapter = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, labels)
        urlBox.setAdapter(adapter)
        urlBox.setOnItemClickListener { _, _, position, _ ->
            val label = adapter.getItem(position)
            val entry = entries.getOrNull(labels.indexOf(label)) ?: return@setOnItemClickListener
            urlBox.dismissDropDown()
            go(entry.url)
        }
    }

    private fun showSiteControls() {
        val url = pageUrl
        if (AdBlock.host(url).isEmpty()) { toast("Abra um site primeiro"); return }
        val status = if (SitePolicy.secure(url)) "HTTPS (conexão criptografada)" else "HTTP: conexão sem criptografia"
        val choices = arrayOf(
            "JavaScript: " + if (Prefs.siteJs(url)) "permitido" else "bloqueado",
            "Cookies de terceiros: " + if (Prefs.siteCookies(url)) "permitidos" else "bloqueados",
            "Pop-ups: " + if (Prefs.sitePopups(url)) "bloqueados" else "permitidos",
            "Câmera: " + if (Prefs.permissionBlocked(url, "camera")) "bloqueada" else "perguntar",
            "Microfone: " + if (Prefs.permissionBlocked(url, "mic")) "bloqueado" else "perguntar",
            "Localização: " + if (Prefs.permissionBlocked(url, "location")) "bloqueada" else "perguntar"
        )
        dlg().setTitle("${SitePolicy.origin(url)}\n$status\n${tabs[cur].blocked.get()} requisições bloqueadas")
            .setItems(choices) { _, choice ->
                when (choice) {
                    0 -> Prefs.siteFlag(url, "siteJs", !Prefs.siteJs(url))
                    1 -> Prefs.siteFlag(url, "siteCookies", !Prefs.siteCookies(url))
                    2 -> Prefs.siteFlag(url, "sitePopups", !Prefs.sitePopups(url))
                    else -> { val key = listOf("camera", "mic", "location")[choice - 3]; Prefs.setPermissionBlocked(url, key, !Prefs.permissionBlocked(url, key)) }
                }
                sitePermissions.cancel()
                applyWebSettings(); web.reload()
            }.setPositiveButton("Fechar", null).show()
    }

    private fun applyImmersive() {
        window.statusBarColor = Color.BLACK
        window.navigationBarColor = Color.BLACK
        window.addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN or WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        if (Build.VERSION.SDK_INT >= 28) {
            window.attributes = window.attributes.apply {
                layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
        }
        window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_FULLSCREEN or
            View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or
            View.SYSTEM_UI_FLAG_LAYOUT_STABLE or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
            View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
        if (Build.VERSION.SDK_INT >= 30) {
            window.setDecorFitsSystemWindows(false)
            window.insetsController?.let {
                it.systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                it.hide(WindowInsets.Type.systemBars())
            }
        }
    }

    private fun setFullscreen(on: Boolean) {
        if (on) {
            root.visibility = View.GONE
            applyImmersive()
        } else {
            window.setFlags(previousWindowFlags, WindowManager.LayoutParams.FLAG_FULLSCREEN or WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            if (Build.VERSION.SDK_INT >= 28) {
                window.attributes = window.attributes.apply { layoutInDisplayCutoutMode = previousCutoutMode }
            }
            if (Build.VERSION.SDK_INT >= 30) {
                window.setDecorFitsSystemWindows(true)
                window.insetsController?.show(WindowInsets.Type.systemBars())
            }
            window.decorView.systemUiVisibility = previousSystemUi
            root.visibility = View.VISIBLE
            root.requestApplyInsets()
            applyBars()
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus && customView != null) applyImmersive()
    }

    override fun onConfigurationChanged(newConfig: android.content.res.Configuration) {
        super.onConfigurationChanged(newConfig)
        if (customView != null) applyImmersive()
    }

    private fun hideCustom() {
        val v = customView ?: return
        customView = null
        full.removeView(v)
        (full.parent as? ViewGroup)?.removeView(full)
        full.visibility = View.GONE
        val callback = customCb
        customCb = null
        setFullscreen(false)
        callback?.onCustomViewHidden()
    }

    override fun onBackPressed() {
        when {
            customView != null -> hideCustom()
            findBar.visibility == View.VISIBLE -> closeFind()
            urlBox.hasFocus() -> {
                urlBox.clearFocus()
                web.requestFocus()
            }
            web.canGoBack() -> web.goBack()
            tabs.size > 1 -> closeTab(cur)
            else -> super.onBackPressed()
        }
    }
}
