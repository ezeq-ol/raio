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
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import java.io.ByteArrayInputStream

class Tab(var url: String, var title: String)

@Suppress("DEPRECATION")
class MainActivity : Activity() {
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
    private lateinit var urlBox: EditText
    private lateinit var progress: ProgressBar
    private lateinit var content: FrameLayout
    private lateinit var full: FrameLayout
    private lateinit var btnBack: Ic
    private lateinit var btnFwd: Ic
    private lateinit var btnTabs: Ic
    private lateinit var btnReload: Ic

    private val tabs = mutableListOf<Tab>()
    private var cur = 0
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
        Prefs.init(this)
        pal = Pal(isDark(this))
        buildUi()
        setupWeb()

        if (Prefs.restoreTabs && !Prefs.incognito) {
            Prefs.list("tabs").take(MAX_TABS).forEach { tabs.add(Tab(it.url, it.title)) }
        }
        if (tabs.isEmpty()) tabs.add(Tab(homeUrl(), ""))
        cur = Prefs.curTab.coerceIn(0, tabs.size - 1)
        load(tabs[cur].url)
        handleIntent(intent)
    }

    override fun onNewIntent(i: Intent) {
        super.onNewIntent(i)
        setIntent(i)
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
        if (isDark(this) != pal.dark) {
            recreate()
            return
        }
        applyWebSettings()
        if (customView == null) applyBars() else applyImmersive()
        val shield = shieldState()
        if (appliedShield.isNotEmpty() && appliedShield != shield) web.reload()
        appliedShield = shield
        web.onResume()
    }

    override fun onPause() {
        super.onPause()
        web.onPause()
        saveTabs()
    }

    private fun saveTabs() {
        if (Prefs.incognito) return
        if (Prefs.restoreTabs) Prefs.saveList("tabs", tabs.map { Entry(it.title, it.url) })
        Prefs.curTab = cur
    }

    override fun onDestroy() {
        if (isFinishing && (Prefs.incognito || Prefs.clearExit)) clearBrowsingData()
        if (customView != null) hideCustom()
        content.removeView(web)
        web.destroy()
        super.onDestroy()
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
        for (ic in listOf(btnBack, btnFwd, btnTabs, btnReload)) ic.col = iconCol
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
        urlBox = EditText(this).apply {
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

    private fun setupWeb() {
        web = WebView(this)
        web.setBackgroundColor(pal.bg)
        content.addView(web, 0, FrameLayout.LayoutParams(MATCH, MATCH))
        mobileUA = web.settings.userAgentString.replace("; wv", "").replace("Version/4.0 ", "")
        applyWebSettings()

        web.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(v: WebView, r: WebResourceRequest): Boolean {
                val sch = r.url.scheme ?: ""
                if (sch == "http" || sch == "https" || sch == "about" || sch == "data" || sch == "blob" || sch == "javascript") return false
                if (sch == "file") return true
                if (Prefs.blockPopups && (!r.isForMainFrame || !r.hasGesture())) return true
                openExternal(r.url.toString())
                return true
            }

            override fun shouldInterceptRequest(v: WebView, r: WebResourceRequest): WebResourceResponse? {
                if (Prefs.shieldEnabled(pageUrl) && !r.isForMainFrame &&
                    AdBlock.shouldBlock(r.url.toString(), pageUrl, Prefs.aggressive)) {
                    return WebResourceResponse("text/plain", "utf-8", ByteArrayInputStream(ByteArray(0)))
                }
                return null
            }

            override fun onPageStarted(v: WebView, url: String?, f: Bitmap?) {
                pageUrl = url ?: ""
                appliedShield = shieldState()
                loading = true
                btnReload.let { setReloadKind(true) }
                syncUrl(url)
            }

            override fun onPageFinished(v: WebView, url: String?) {
                loading = false
                setReloadKind(false)
                syncUrl(url)
                val u = url ?: return
                val t = tabs[cur]
                if (u != HOME && !u.startsWith("data:") && v.title != StartPage.ERR_TITLE) {
                    t.title = v.title ?: u
                    addHistory(t.title, u)
                }
                injectShield()
                updateNav()
            }

            override fun doUpdateVisitedHistory(v: WebView, url: String?, isReload: Boolean) {
                syncUrl(url)
                pageUrl = url ?: pageUrl
                injectShield()
                updateNav()
            }

            @android.annotation.TargetApi(23)
            override fun onReceivedError(v: WebView, r: WebResourceRequest, e: WebResourceError) {
                if (r.isForMainFrame) showError(r.url.toString(), e.description?.toString() ?: "")
            }

            @Deprecated("Deprecated in Java")
            override fun onReceivedError(v: WebView, code: Int, desc: String?, failing: String?) {
                if (Build.VERSION.SDK_INT < 23 && failing != null) showError(failing, desc ?: "")
            }

            override fun onReceivedSslError(v: WebView, h: SslErrorHandler, e: SslError) {
                h.cancel()
                showError(e.url ?: "", "Conexão não segura (certificado inválido).")
            }
        }

        web.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(v: WebView, p: Int) {
                progress.progress = p
                progress.visibility = if (p < 100) View.VISIBLE else View.GONE
            }

            override fun onReceivedTitle(v: WebView, t: String?) {
                if (t != null && v.url != HOME) tabs[cur].title = t
            }

            override fun onShowCustomView(view: View, cb: CustomViewCallback) {
                if (customView != null) {
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

            override fun onHideCustomView() = hideCustom()

            override fun onShowFileChooser(v: WebView, cb: ValueCallback<Array<Uri>>, p: FileChooserParams): Boolean {
                fileCb?.onReceiveValue(null)
                fileCb = cb
                return try {
                    startActivityForResult(p.createIntent(), 12)
                    true
                } catch (_: Exception) {
                    fileCb = null
                    false
                }
            }

            override fun onPermissionRequest(r: PermissionRequest) = r.deny()

            override fun onGeolocationPermissionsShowPrompt(o: String, cb: android.webkit.GeolocationPermissions.Callback) {
                cb.invoke(o, false, false)
            }

            override fun onJsAlert(v: WebView, url: String?, m: String?, r: JsResult): Boolean {
                dlg().setMessage(m).setPositiveButton("OK") { _, _ -> r.confirm() }
                    .setOnCancelListener { r.cancel() }.show()
                return true
            }

            override fun onJsConfirm(v: WebView, url: String?, m: String?, r: JsResult): Boolean {
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
        s.javaScriptEnabled = Prefs.js
        s.domStorageEnabled = true
        s.allowFileAccess = false
        s.allowContentAccess = false
        s.setGeolocationEnabled(false)
        s.mediaPlaybackRequiresUserGesture = true
        s.javaScriptCanOpenWindowsAutomatically = !Prefs.blockPopups
        // Unhandled popup windows are rejected by WebChromeClient.
        s.setSupportMultipleWindows(Prefs.blockPopups)
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
        CookieManager.getInstance().setAcceptThirdPartyCookies(web, Prefs.thirdCookies)
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
        urlBox.setText(if (u == HOME || u.startsWith("data:")) "" else hostOf(u))
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
    }

    private fun go(input: String) {
        urlBox.clearFocus()
        load(resolve(input))
    }

    private fun load(u: String) {
        tabs[cur].url = u
        pageUrl = u
        appliedShield = shieldState()
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

    private fun showTab(i: Int) {
        cur = i
        load(tabs[i].url)
    }

    private fun newTab(url: String = homeUrl()) {
        if (tabs.size >= MAX_TABS) {
            toast("Limite de $MAX_TABS abas (modo leve)")
            return
        }
        tabs.add(Tab(url, ""))
        cur = tabs.size - 1
        load(url)
    }

    private fun closeTab(i: Int) {
        tabs.removeAt(i)
        if (tabs.isEmpty()) {
            tabs.add(Tab(homeUrl(), ""))
            cur = 0
            load(tabs[0].url)
            return
        }
        if (i < cur) {
            cur--
            updateNav()
        } else if (i == cur) {
            cur = minOf(cur, tabs.size - 1)
            load(tabs[cur].url)
        } else updateNav()
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
                tabs.clear()
                tabs.add(Tab(homeUrl(), ""))
                cur = 0
                load(tabs[0].url)
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
            "Downloads" to {
                try {
                    startActivity(Intent(DownloadManager.ACTION_VIEW_DOWNLOADS))
                } catch (_: Exception) {
                    toast("Abra o app Arquivos para ver seus downloads")
                }
            },
            "Localizar na página" to { openFind() },
            "Compartilhar página" to { sharePage() },
            (if (Prefs.desktop) "Versão para celular" else "Versão para computador") to {
                Prefs.desktop = !Prefs.desktop
                applyWebSettings()
            },
            (if (Prefs.incognito) "Desligar modo anônimo" else "Ligar modo anônimo") to {
                Prefs.incognito = !Prefs.incognito
                applyBars()
                toast(if (Prefs.incognito) "Modo anônimo: nada será salvo" else "Modo anônimo desligado")
                if (tabs[cur].url == HOME) load(HOME)
            },
            "Configurações" to { startActivity(Intent(this, SettingsActivity::class.java)) },
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
        startActivityForResult(Intent(this, LibraryActivity::class.java).putExtra("mode", mode), 11)
    }

    override fun onActivityResult(req: Int, res: Int, data: Intent?) {
        if (req == 11 && res == RESULT_OK && data != null) {
            val u = data.getStringExtra("url") ?: return
            if (data.getBooleanExtra("newtab", false)) newTab(u) else load(u)
        } else if (req == 12) {
            fileCb?.onReceiveValue(WebChromeClient.FileChooserParams.parseResult(res, data))
            fileCb = null
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
            val r = DownloadManager.Request(Uri.parse(url))
            if (!mime.isNullOrEmpty()) r.setMimeType(mime)
            CookieManager.getInstance().getCookie(url)?.let { r.addRequestHeader("Cookie", it) }
            if (!ua.isNullOrEmpty()) r.addRequestHeader("User-Agent", ua)
            r.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            r.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, name)
            (getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager).enqueue(r)
            toast("Baixando: $name")
        } catch (_: Exception) {
            toast("Falha ao iniciar o download")
        }
    }

    override fun onRequestPermissionsResult(code: Int, perms: Array<out String>, res: IntArray) {
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

    private fun shieldState() = "${Prefs.shieldEnabled(pageUrl)}:${Prefs.aggressive}"

    private fun injectShield() {
        if (Prefs.js && Prefs.aggressive && Prefs.shieldEnabled(pageUrl)) {
            web.evaluateJavascript(ShieldScript.source, null)
        }
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
