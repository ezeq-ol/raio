package com.raio.browser

import android.app.Activity
import android.content.Context
import android.content.SharedPreferences
import android.content.res.Configuration
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.view.View
import org.json.JSONArray
import org.json.JSONObject

const val HOME = "about:home"

class Entry(val title: String, val url: String)

/** Preferencias + listas (favoritos, historico, abas) em SharedPreferences. Sem banco de dados. */
object Prefs {
    private lateinit var sp: SharedPreferences

    fun init(c: Context) {
        if (::sp.isInitialized) return
        val normal = c.applicationContext.getSharedPreferences("raio", Context.MODE_PRIVATE)
        if (!RaioApp.privateMode) { sp = normal; return }
        sp = c.applicationContext.getSharedPreferences("raio_private", Context.MODE_PRIVATE)
        val e = sp.edit().clear()
        for ((k, v) in normal.all) {
            if (k in setOf("hist", "tabs", "downloads", "incognito")) continue
            when (v) {
                is String -> e.putString(k, v)
                is Boolean -> e.putBoolean(k, v)
                is Int -> e.putInt(k, v)
                is Long -> e.putLong(k, v)
            }
        }
        e.commit()
    }

    val engines = listOf(
        "Google" to "https://www.google.com/search?q=%s",
        "DuckDuckGo" to "https://duckduckgo.com/?q=%s",
        "Bing" to "https://www.bing.com/search?q=%s",
        "Brave" to "https://search.brave.com/search?q=%s",
        "Ecosia" to "https://www.ecosia.org/search?q=%s"
    )

    private fun b(k: String, d: Boolean) = sp.getBoolean(k, d)
    private fun i(k: String, d: Int) = sp.getInt(k, d)
    private fun s(k: String, d: String) = sp.getString(k, d) ?: d
    private fun put(k: String, v: Any) {
        val e = sp.edit()
        when (v) {
            is Boolean -> e.putBoolean(k, v)
            is Int -> e.putInt(k, v)
            is String -> e.putString(k, v)
            is Long -> e.putLong(k, v)
        }
        e.apply()
    }

    var engine: Int get() = i("engine", 0).coerceIn(0, engines.size - 1); set(v) = put("engine", v)
    var home: String get() = s("home", ""); set(v) = put("home", v)
    var restoreTabs: Boolean get() = b("restoreTabs", true); set(v) = put("restoreTabs", v)
    var adblock: Boolean get() = b("adblock", true); set(v) = put("adblock", v)
    var aggressive: Boolean get() = b("aggressive", true); set(v) = put("aggressive", v)

    fun shieldEnabled(url: String): Boolean = adblock && !b("shieldOff:" + AdBlock.host(url), false)
    fun setShieldException(url: String, disabled: Boolean) = put("shieldOff:" + AdBlock.host(url), disabled)

    var blockImages: Boolean get() = b("blockImages", false); set(v) = put("blockImages", v)
    var js: Boolean get() = b("js", true); set(v) = put("js", v)
    var desktop: Boolean get() = b("desktop", false); set(v) = put("desktop", v)
    var textSize: Int get() = i("textSize", 100); set(v) = put("textSize", v)
    var theme: Int get() = i("theme", 0); set(v) = put("theme", v) // 0 sistema, 1 claro, 2 escuro
    var webDark: Boolean get() = b("webDark", false); set(v) = put("webDark", v)
    val incognito: Boolean get() = RaioApp.privateMode
    var autoUpdates: Boolean get() = b("autoUpdates", true); set(v) = put("autoUpdates", v)
    var updateCheckedAt: Long get() = sp.getLong("updateCheckedAt", 0); set(v) = put("updateCheckedAt", v)
    var filterCheckedAt: Long get() = sp.getLong("filterCheckedAt", 0); set(v) = put("filterCheckedAt", v)
    fun siteJs(url: String) = b("siteJs:" + AdBlock.host(url), js)
    fun siteCookies(url: String) = b("siteCookies:" + AdBlock.host(url), thirdCookies)
    fun sitePopups(url: String) = b("sitePopups:" + AdBlock.host(url), blockPopups)
    fun siteFlag(url: String, key: String, value: Boolean) = put(key + ":" + AdBlock.host(url), value)
    fun permissionBlocked(url: String, key: String) = b("deny:$key:" + SitePolicy.origin(url), false)
    fun setPermissionBlocked(url: String, key: String, value: Boolean) = put("deny:$key:" + SitePolicy.origin(url), value)
    fun shortcuts(): List<Entry> = if (sp.contains("shortcuts")) list("shortcuts") else list("fav").take(8).ifEmpty {
        listOf(Entry("YouTube", "https://m.youtube.com"), Entry("Wikipédia", "https://pt.m.wikipedia.org"), Entry("G1", "https://g1.globo.com"), Entry("Mercado Livre", "https://www.mercadolivre.com.br"))
    }
    var dnt: Boolean get() = b("dnt", true); set(v) = put("dnt", v)
    var thirdCookies: Boolean get() = b("thirdCookies", false); set(v) = put("thirdCookies", v)
    var blockPopups: Boolean get() = b("blockPopups", true); set(v) = put("blockPopups", v)
    var clearExit: Boolean get() = b("clearExit", false); set(v) = put("clearExit", v)
    var curTab: Int get() = i("curTab", 0); set(v) = put("curTab", v)

    fun list(key: String): MutableList<Entry> {
        val out = mutableListOf<Entry>()
        try {
            val a = JSONArray(s(key, "[]"))
            for (n in 0 until a.length()) {
                val o = a.getJSONObject(n)
                out.add(Entry(o.optString("t"), o.optString("u")))
            }
        } catch (_: Exception) {
        }
        return out
    }

    fun saveList(key: String, l: List<Entry>) {
        if (incognito && (key == "hist" || key == "tabs")) return
        val a = JSONArray()
        for (e in l) a.put(JSONObject().put("t", e.title).put("u", e.url))
        put(key, a.toString())
    }
}

/** Transforma o que foi digitado em URL ou busca. */
fun resolve(input: String): String {
    val s = input.trim()
    if (s.isEmpty() || s == HOME) return HOME
    val low = s.lowercase()
    if (low.startsWith("javascript:")) return HOME
    if (Regex("^[a-z][a-z0-9+.-]*://").containsMatchIn(low)) return s
    if (!s.contains(' ') && (s.contains('.') || low.startsWith("localhost"))) {
        val local = low.startsWith("localhost") || Regex("^\\d{1,3}(\\.\\d{1,3}){3}(:\\d+)?(/.*)?$").matches(s)
        return (if (local) "http://" else "https://") + s
    }
    return Prefs.engines[Prefs.engine].second.replace("%s", Uri.encode(s))
}

fun hostOf(u: String): String = try {
    Uri.parse(u).host?.removePrefix("www.") ?: u
} catch (_: Exception) {
    u
}

fun isDark(c: Context): Boolean = when (Prefs.theme) {
    1 -> false
    2 -> true
    else -> (c.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
}

class Pal(val dark: Boolean) {
    val bg = if (dark) 0xFF101215.toInt() else 0xFFF3F5F7.toInt()
    val bar = if (dark) 0xFF1B1E23.toInt() else 0xFFFFFFFF.toInt()
    val field = if (dark) 0xFF2A2E35.toInt() else 0xFFE8ECF0.toInt()
    val text = if (dark) 0xFFECEFF3.toInt() else 0xFF1A1D21.toInt()
    val sub = if (dark) 0xFF98A2AD.toInt() else 0xFF66707B.toInt()
    val line = if (dark) 0xFF2C3036.toInt() else 0xFFDDE2E7.toInt()
    val accent = 0xFFFF6B1A.toInt()
    val incog = 0xFF2B2140.toInt()
}

fun Context.dp(v: Int): Int = (v * resources.displayMetrics.density + 0.5f).toInt()

fun round(color: Int, r: Float): GradientDrawable = GradientDrawable().apply {
    setColor(color)
    cornerRadius = r
}

@Suppress("DEPRECATION")
fun Activity.styleBars(color: Int, darkBar: Boolean) {
    window.statusBarColor = color
    window.navigationBarColor = color
    if (Build.VERSION.SDK_INT >= 23) {
        var f = window.decorView.systemUiVisibility
        f = if (darkBar) f and View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR.inv() else f or View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
        if (Build.VERSION.SDK_INT >= 26) {
            f = if (darkBar) f and View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR.inv() else f or View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
        }
        window.decorView.systemUiVisibility = f
    }
}
