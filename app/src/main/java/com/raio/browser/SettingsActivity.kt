package com.raio.browser

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebStorage
import android.webkit.WebView
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast

/** Configuracoes do navegador, montadas em codigo (sem layouts XML, abre rapido). */
class SettingsActivity : Activity() {
    private lateinit var pal: Pal
    private lateinit var list: LinearLayout

    override fun onCreate(b: Bundle?) {
        Prefs.init(this)
        pal = Pal(isDark(this))
        setTheme(if (pal.dark) R.style.AppDark else R.style.AppLight)
        super.onCreate(b)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(pal.bg)
            fitsSystemWindows = true
        }
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setBackgroundColor(pal.bar)
            setPadding(dp(8), 0, dp(8), 0)
        }
        header.addView(TextView(this).apply {
            text = "‹"
            textSize = 30f
            setTextColor(pal.text)
            gravity = Gravity.CENTER
            setOnClickListener { finish() }
        }, LinearLayout.LayoutParams(dp(48), dp(52)))
        header.addView(TextView(this).apply {
            text = "Configurações"
            textSize = 18f
            setTextColor(pal.text)
            setTypeface(typeface, android.graphics.Typeface.BOLD)
        })
        root.addView(header, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(52)))

        list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        root.addView(ScrollView(this).apply { addView(list) }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        setContentView(root)
        styleBars(pal.bar, pal.dark)
        render()
    }

    private fun dlg() = AlertDialog.Builder(
        this,
        if (pal.dark) android.R.style.Theme_Material_Dialog_Alert else android.R.style.Theme_Material_Light_Dialog_Alert
    )

    // ---------------------------------------------------------------- blocos de tela

    private fun render() {
        list.removeAllViews()

        header("Geral")
        choice("Mecanismo de busca", Prefs.engines.map { it.first }, Prefs.engine) { Prefs.engine = it }
        textRow("Página inicial", if (Prefs.home.isBlank()) "Página inicial do Raio (rápida)" else Prefs.home) { askHome() }
        toggle("Restaurar abas ao abrir", "Continua de onde você parou", Prefs.restoreTabs) { Prefs.restoreTabs = it }
        textRow("Definir como navegador padrão", "Abrir links de outros apps no Raio") {
            try {
                startActivity(Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS))
            } catch (_: Exception) {
                toast("Abra Configurações > Apps > Apps padrão")
            }
        }

        header("Velocidade e dados")
        toggle("Bloquear anúncios e rastreadores", "Páginas mais leves e rápidas", Prefs.adblock) { Prefs.adblock = it }
        toggle("Proteção agressiva", "Filtra URLs de anúncios, rastreadores e espaços de publicidade. Pode afetar alguns sites.", Prefs.aggressive) { Prefs.aggressive = it }
        toggle("Economia de dados (sem imagens)", "Não carrega imagens das páginas", Prefs.blockImages) { Prefs.blockImages = it }
        toggle("JavaScript", "Desligar deixa tudo mais leve, mas alguns sites quebram", Prefs.js) { Prefs.js = it }
        toggle("Versão para computador", "Pede a versão desktop dos sites", Prefs.desktop) { Prefs.desktop = it }
        choice("Tamanho do texto", listOf("Pequeno (80%)", "Normal (100%)", "Grande (120%)", "Muito grande (150%)"),
            listOf(80, 100, 120, 150).indexOf(Prefs.textSize).coerceAtLeast(1)) { Prefs.textSize = listOf(80, 100, 120, 150)[it] }

        header("Aparência")
        choice("Tema", listOf("Automático (sistema)", "Claro", "Escuro"), Prefs.theme) {
            Prefs.theme = it
            recreate()
        }
        if (Build.VERSION.SDK_INT >= 29) {
            toggle("Escurecer sites", "Força modo escuro nas páginas", Prefs.webDark) { Prefs.webDark = it }
        }

        header("Privacidade")
        toggle("Modo anônimo", "Não salva histórico nem abas e limpa tudo ao sair", Prefs.incognito) { Prefs.incognito = it }
        toggle("Enviar \"Não rastrear\"", "Pede aos sites para não rastrear você", Prefs.dnt) { Prefs.dnt = it }
        toggle("Cookies de terceiros", "Desligado é mais privado", Prefs.thirdCookies) { Prefs.thirdCookies = it }
        toggle("Bloquear pop-ups", "Impede janelas que abrem sozinhas", Prefs.blockPopups) { Prefs.blockPopups = it }
        toggle("Limpar dados ao sair", "Apaga histórico, cache e cookies ao fechar", Prefs.clearExit) { Prefs.clearExit = it }
        textRow("Limpar histórico", null) {
            Prefs.saveList("hist", emptyList())
            toast("Histórico apagado")
        }
        textRow("Limpar cache e cookies", "Libera espaço e desconecta dos sites") {
            try {
                WebView(this).apply {
                    clearCache(true)
                    clearFormData()
                    destroy()
                }
                CookieManager.getInstance().removeAllCookies(null)
                WebStorage.getInstance().deleteAllData()
            } catch (_: Exception) {
            }
            toast("Cache e cookies apagados")
        }

        header("Sobre")
        textRow("Raio 1.1", "Navegador ultra leve. Sem bibliotecas extras, abre rápido em qualquer celular.") {}
    }

    private fun header(t: String) {
        list.addView(TextView(this).apply {
            text = t.uppercase()
            textSize = 12f
            setTextColor(pal.accent)
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            setPadding(dp(18), dp(20), dp(18), dp(6))
        })
    }

    private fun row(title: String, sub: String?, trailing: View?, onClick: () -> Unit): LinearLayout {
        val r = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setBackgroundColor(pal.bar)
            setPadding(dp(18), dp(12), dp(14), dp(12))
            setOnClickListener { onClick() }
        }
        val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        col.addView(TextView(this).apply {
            text = title
            textSize = 16f
            setTextColor(pal.text)
        })
        if (sub != null) col.addView(TextView(this).apply {
            text = sub
            textSize = 13f
            setTextColor(pal.sub)
        })
        r.addView(col, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        if (trailing != null) r.addView(trailing)
        list.addView(r, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        list.addView(View(this).apply { setBackgroundColor(pal.line) }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 1))
        return r
    }

    private fun textRow(title: String, sub: String?, onClick: () -> Unit) {
        row(title, sub, null, onClick)
    }

    private fun toggle(title: String, sub: String, value: Boolean, set: (Boolean) -> Unit) {
        val sw = Switch(this).apply {
            isChecked = value
            isClickable = false
        }
        row(title, sub, sw) {
            sw.toggle()
            set(sw.isChecked)
        }
    }

    private fun choice(title: String, options: List<String>, selected: Int, set: (Int) -> Unit) {
        row(title, options.getOrElse(selected) { options[0] }, null) {
            dlg().setTitle(title).setSingleChoiceItems(options.toTypedArray(), selected) { d, w ->
                set(w)
                d.dismiss()
                render()
            }.show()
        }
    }

    private fun askHome() {
        val et = EditText(this).apply {
            setText(Prefs.home)
            hint = "Vazio = página inicial do Raio"
            setSingleLine()
            setTextColor(pal.text)
            setHintTextColor(pal.sub)
        }
        val box = LinearLayout(this).apply {
            setPadding(dp(22), dp(8), dp(22), 0)
            addView(et, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        }
        dlg().setTitle("Página inicial").setView(box)
            .setPositiveButton("Salvar") { _, _ ->
                Prefs.home = et.text.toString().trim()
                render()
            }
            .setNegativeButton("Cancelar", null).show()
    }

    private fun toast(m: String) = Toast.makeText(this, m, Toast.LENGTH_SHORT).show()
}
