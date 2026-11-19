package com.raio.browser

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.os.SystemClock
import android.widget.Toast
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.io.File
import java.util.concurrent.Executors

object Remote {
    private val executor = Executors.newSingleThreadExecutor { r -> Thread(r, "RaioUpdates").apply { isDaemon = true } }
    fun fetch(url: String, limit: Int): String {
        require(url.startsWith("https://"))
        var next = URL(url)
        repeat(6) {
            val c = next.openConnection() as HttpURLConnection
            try {
                c.instanceFollowRedirects = false
                c.connectTimeout = 12000; c.readTimeout = 12000
                c.setRequestProperty("User-Agent", "Raio/1.2")
                c.setRequestProperty("Accept", "application/vnd.github+json, text/plain")
                val status = c.responseCode
                if (status in 300..399) {
                    next = URL(next, c.getHeaderField("Location") ?: error("Redirecionamento inválido"))
                    require(next.protocol == "https")
                } else {
                    require(status == 200) { "Servidor respondeu $status" }
                    return c.inputStream.use { input ->
                        val out = java.io.ByteArrayOutputStream()
                        val buffer = ByteArray(8192)
                        while (true) {
                            val n = input.read(buffer)
                            if (n < 0) break
                            require(out.size() + n <= limit) { "Resposta grande demais" }
                            out.write(buffer, 0, n)
                        }
                        out.toString("UTF-8")
                    }
                }
            } finally { c.disconnect() }
        }
        error("Redirecionamentos demais")
    }
    fun <T> task(activity: Activity, work: () -> T, done: (Result<T>) -> Unit) {
        executor.execute {
            val result = runCatching(work)
            activity.runOnUiThread {
                if (!activity.isFinishing && !activity.isDestroyed) done(result)
            }
        }
    }
}

object FilterStore {
    private const val SOURCE = "https://raw.githubusercontent.com/ezeq-ol/raio/main/app/src/main/assets/domains.txt"
    fun load(c: Context) {
        val stored = File(c.filesDir, "shield-domains.txt")
        val list = runCatching { SitePolicy.domains(stored.readText()) }.getOrElse {
            runCatching { SitePolicy.domains(c.assets.open("domains.txt").bufferedReader().use { it.readText() }) }.getOrDefault(emptySet())
        }
        AdBlock.replaceExtra(list)
    }
    fun update(activity: Activity, manual: Boolean = true) {
        if (RaioApp.privateMode) return
        if (manual) Toast.makeText(activity, "Atualizando lista do GitHub…", Toast.LENGTH_SHORT).show()
        Remote.task(activity, {
            val text = Remote.fetch(SOURCE, 512 * 1024)
            val parsed = SitePolicy.domains(text)
            val atomic = android.util.AtomicFile(File(activity.filesDir, "shield-domains.txt"))
            var stream: java.io.FileOutputStream? = null
            try {
                stream = atomic.startWrite()
                stream.write(text.toByteArray(Charsets.UTF_8))
                atomic.finishWrite(stream)
            } catch (e: Exception) {
                if (stream != null) atomic.failWrite(stream)
                throw e
            }
            AdBlock.replaceExtra(parsed)
            parsed.size
        }) { result ->
            result.onSuccess {
                Prefs.filterCheckedAt = System.currentTimeMillis()
                if (manual) AlertDialog.Builder(activity).setTitle("Lista atualizada")
                    .setMessage("$it domínios carregados. As novas regras já estão ativas; recarregue a página para reaplicar os filtros.")
                    .setPositiveButton("OK", null).show()
            }.onFailure {
                if (manual) Toast.makeText(activity, "Falha ao atualizar. A lista anterior foi mantida.", Toast.LENGTH_LONG).show()
            }
        }
    }
}

object AppUpdates {
    fun check(activity: Activity, manual: Boolean = true) {
        if (RaioApp.privateMode) return
        if (manual) Toast.makeText(activity, "Consultando versões no GitHub…", Toast.LENGTH_SHORT).show()
        Remote.task(activity, {
            val r = JSONObject(Remote.fetch("https://api.github.com/repos/ezeq-ol/raio/releases/latest", 256 * 1024))
            require(!r.optBoolean("draft") && !r.optBoolean("prerelease"))
            val tag = r.getString("tag_name")
            val installed = activity.packageManager.getPackageInfo(activity.packageName, 0).versionName ?: "0"
            val assets = r.getJSONArray("assets")
            var apk = ""
            for (i in 0 until assets.length()) {
                val a = assets.getJSONObject(i)
                if (a.optString("name") == "Raio.apk" && SitePolicy.releaseAsset(a.optString("browser_download_url"))) {
                    apk = a.getString("browser_download_url")
                }
            }
            require(apk.isNotEmpty())
            Triple(tag, apk, SitePolicy.newer(tag, installed))
        }) { result ->
            result.onSuccess { (tag, url, newer) ->
                Prefs.updateCheckedAt = System.currentTimeMillis()
                if (newer) AlertDialog.Builder(activity).setTitle("Raio $tag disponível")
                    .setMessage("Baixar a nova versão do repositório oficial no GitHub? A instalação será confirmada por você no Android.")
                    .setPositiveButton("Baixar APK") { _, _ ->
                        DownloadRecords.enqueue(activity, url, "Raio-$tag.apk", "application/vnd.android.package-archive", null, null, publicDirectory = false)
                    }.setNegativeButton("Depois", null).show()
                else if (manual) Toast.makeText(activity, "Você já está na versão mais recente.", Toast.LENGTH_LONG).show()
            }.onFailure { if (manual) Toast.makeText(activity, "Não foi possível consultar o GitHub. Tente novamente mais tarde.", Toast.LENGTH_LONG).show() }
        }
    }
    fun automatic(activity: Activity) {
        if (RaioApp.privateMode || !Prefs.autoUpdates) return
        val now = System.currentTimeMillis()
        if (now - Prefs.updateCheckedAt > 86400000L) {
            Prefs.updateCheckedAt = now // throttle unsuccessful attempts too
            check(activity, false)
        }
        if (now - Prefs.filterCheckedAt > 7 * 86400000L) {
            Prefs.filterCheckedAt = now
            FilterStore.update(activity, false)
        }
    }
}
