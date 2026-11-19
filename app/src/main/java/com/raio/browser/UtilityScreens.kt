package com.raio.browser

import android.app.Activity
import android.app.AlertDialog
import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast

open class SimpleScreen : Activity() {
    protected lateinit var pal: Pal
    protected lateinit var body: LinearLayout
    protected lateinit var scroll: ScrollView
    protected fun screen(title: String) {
        Prefs.init(this)
        pal = Pal(isDark(this))
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; fitsSystemWindows = true; setBackgroundColor(pal.bg) }
        root.addView(TextView(this).apply {
            text = "‹  $title"; textSize = 20f; setTextColor(pal.text); setPadding(dp(16), dp(18), dp(16), dp(18))
            setOnClickListener { finish() }
        })
        body = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(16), 0, dp(16), dp(20)) }
        scroll = ScrollView(this).apply { addView(body) }
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        setContentView(root); styleBars(pal.bar, pal.dark)
    }
    protected fun row(text: String, action: (() -> Unit)? = null) {
        body.addView(TextView(this).apply {
            this.text = text; textSize = 16f; setTextColor(if (action == null) pal.text else pal.accent)
            setPadding(dp(4), dp(14), dp(4), dp(14))
            if (action != null) setOnClickListener { action() }
        }, LinearLayout.LayoutParams(-1, -2))
    }
}

object DownloadRecords {
    fun enqueue(c: Context, url: String, title: String, mime: String?, ua: String?, cookie: String?, publicDirectory: Boolean = true): Long {
        require(AdBlock.host(url).isNotEmpty())
        val safeName = title.replace(Regex("[\\\\/\\p{Cntrl}]"), "_").take(180).ifBlank { "arquivo" }
        val r = DownloadManager.Request(Uri.parse(url)).setTitle(safeName)
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
        if (publicDirectory) r.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, safeName)
        else r.setDestinationInExternalFilesDir(c, Environment.DIRECTORY_DOWNLOADS, safeName)
        if (!mime.isNullOrEmpty()) r.setMimeType(mime)
        if (!ua.isNullOrEmpty()) r.addRequestHeader("User-Agent", ua)
        if (!cookie.isNullOrEmpty()) r.addRequestHeader("Cookie", cookie)
        val id = (c.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager).enqueue(r)
        val records = Prefs.list("downloads")
        records.add(0, Entry(safeName, id.toString()))
        Prefs.saveList("downloads", records.take(100))
        Toast.makeText(c, "Download iniciado. Veja Menu → Downloads.", Toast.LENGTH_LONG).show()
        return id
    }
}

open class DownloadsActivity : SimpleScreen() {
    private var pendingInstallId = -1L
    private val handler = Handler(Looper.getMainLooper())
    private val ticker = object : Runnable { override fun run() { render(); handler.postDelayed(this, 1500) } }
    override fun onCreate(b: Bundle?) { super.onCreate(b); pendingInstallId = b?.getLong("pendingInstallId", -1L) ?: -1L; screen("Downloads") }
    override fun onResume() { super.onResume(); handler.post(ticker) }
    override fun onPause() { handler.removeCallbacks(ticker); super.onPause() }
    private fun render() {
        val y = scroll.scrollY
        body.removeAllViews()
        val records = Prefs.list("downloads")
        if (records.isEmpty()) row("Nenhum download registrado nesta sessão.")
        val dm = getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        for (entry in records) {
            val id = entry.url.toLongOrNull() ?: continue
            var status = 0; var bytes = 0L; var total = -1L; var reason = 0
            runCatching {
                dm.query(DownloadManager.Query().setFilterById(id)).use { cursor ->
                    if (cursor.moveToFirst()) {
                        status = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
                        bytes = cursor.getLong(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR))
                        total = cursor.getLong(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES))
                        reason = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_REASON))
                    }
                }
            }
            val message = when (status) {
                DownloadManager.STATUS_SUCCESSFUL -> "Concluído · toque para abrir"
                DownloadManager.STATUS_FAILED -> "Falhou · " + when (reason) {
                    DownloadManager.ERROR_INSUFFICIENT_SPACE -> "espaço insuficiente"
                    DownloadManager.ERROR_FILE_ALREADY_EXISTS -> "arquivo já existe"
                    DownloadManager.ERROR_HTTP_DATA_ERROR -> "falha de conexão"
                    else -> "erro $reason"
                }
                DownloadManager.STATUS_PAUSED -> "Aguardando rede ou retomada pelo Android"
                DownloadManager.STATUS_PENDING -> "Na fila"
                DownloadManager.STATUS_RUNNING -> if (total > 0) "${bytes * 100 / total}% · ${bytes / 1024} / ${total / 1024} KB" else "${bytes / 1024} KB recebidos"
                else -> "Arquivo removido ou indisponível"
            }
            row("${entry.title}\n$message") {
                if (status == DownloadManager.STATUS_SUCCESSFUL) {
                    openDownload(id)
                } else {
                    AlertDialog.Builder(this).setTitle(entry.title).setMessage(message)
                        .setPositiveButton("Remover registro") { _, _ ->
                            Prefs.saveList("downloads", Prefs.list("downloads").filter { it.url != entry.url }); render()
                        }.setNegativeButton("Fechar", null).show()
                }
            }
        }
        row("Os arquivos são salvos em Downloads e continuam no aparelho após sair do modo anônimo. Remover um registro não apaga o arquivo.")
        scroll.post { scroll.scrollTo(0, y) }
    }
    override fun onSaveInstanceState(out: Bundle) { out.putLong("pendingInstallId", pendingInstallId); super.onSaveInstanceState(out) }
    private fun openDownload(id: Long) {
        val dm = getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        runCatching {
            val uri = dm.getUriForDownloadedFile(id) ?: error("Arquivo indisponível")
            val mime = dm.getMimeTypeForDownloadedFile(id) ?: "*/*"
            if (mime == "application/vnd.android.package-archive" && android.os.Build.VERSION.SDK_INT >= 26 && !packageManager.canRequestPackageInstalls()) {
                AlertDialog.Builder(this).setTitle("Instalar atualização")
                    .setMessage("O Android precisa da sua permissão para instalar APKs baixados pelo Raio. Você ainda confirmará cada instalação na tela do sistema.")
                    .setPositiveButton("Abrir configuração") { _, _ ->
                        pendingInstallId = id
                        startActivityForResult(Intent(android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:$packageName")), 77)
                    }.setNegativeButton("Cancelar", null).show()
                return
            }
            startActivity(Intent(Intent.ACTION_VIEW).setDataAndType(uri, mime).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION))
        }.onFailure { Toast.makeText(this, "Não foi possível abrir o arquivo. Confira o aplicativo compatível e as permissões do Android.", Toast.LENGTH_LONG).show() }
    }
    override fun onActivityResult(request: Int, result: Int, data: Intent?) {
        super.onActivityResult(request, result, data)
        if (request == 77 && pendingInstallId >= 0) {
            val id = pendingInstallId; pendingInstallId = -1
            if (android.os.Build.VERSION.SDK_INT >= 26 && packageManager.canRequestPackageInstalls()) openDownload(id)
        }
    }

}

open class ShortcutsActivity : SimpleScreen() {
    override fun onCreate(b: Bundle?) { super.onCreate(b); screen("Editar atalhos"); render() }
    private fun render() {
        body.removeAllViews()
        row("Os atalhos ficam na página inicial. Toque para editar ou reorganizar.")
        val list = Prefs.shortcuts()
        list.forEachIndexed { i, e -> row("${i + 1}. ${e.title}\n${e.url}") {
            AlertDialog.Builder(this).setTitle(e.title).setItems(arrayOf("Editar", "Mover para cima", "Mover para baixo", "Remover")) { _, which ->
                val items = Prefs.shortcuts().toMutableList()
                when (which) {
                    0 -> { edit(i); return@setItems }
                    1 -> if (i > 0) java.util.Collections.swap(items, i, i - 1)
                    2 -> if (i < items.lastIndex) java.util.Collections.swap(items, i, i + 1)
                    3 -> items.removeAt(i)
                }
                Prefs.saveList("shortcuts", items); render()
            }.show()
        } }
        if (list.size < 8) row("+ Adicionar atalho") { edit(null) }
    }
    private fun edit(index: Int?) {
        val old = index?.let { Prefs.shortcuts().getOrNull(it) }
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(20), 0, dp(20), 0) }
        val title = EditText(this).apply { hint = "Nome do atalho"; setSingleLine(); setText(old?.title ?: "") }
        val url = EditText(this).apply { hint = "https://site.com"; setSingleLine(); setText(old?.url ?: ""); inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_URI }
        box.addView(title); box.addView(url)
        val d = AlertDialog.Builder(this).setTitle("Atalho").setView(box).setPositiveButton("Salvar", null).setNegativeButton("Cancelar", null).create()
        d.setOnShowListener {
            d.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val target = resolve(url.text.toString())
                if (AdBlock.host(target).isEmpty() || url.text.isBlank()) { url.error = "Informe um endereço HTTP ou HTTPS"; return@setOnClickListener }
                val items = Prefs.shortcuts().toMutableList()
                val entry = Entry(title.text.toString().trim().ifBlank { hostOf(target) }, target)
                if (index == null) items.add(entry) else items[index] = entry
                Prefs.saveList("shortcuts", items.take(8)); d.dismiss(); render()
            }
        }
        d.show()
    }
}
