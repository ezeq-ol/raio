package com.raio.browser

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.TextView

/** Lista de favoritos ou historico (mesma tela, sem bibliotecas). */
open class LibraryActivity : Activity() {
    private lateinit var pal: Pal
    private lateinit var mode: String
    private lateinit var items: MutableList<Entry>
    private lateinit var adapter: BaseAdapter
    private lateinit var empty: TextView

    override fun onCreate(b: Bundle?) {
        Prefs.init(this)
        pal = Pal(isDark(this))
        setTheme(if (pal.dark) R.style.AppDark else R.style.AppLight)
        super.onCreate(b)
        mode = intent.getStringExtra("mode") ?: "fav"
        items = Prefs.list(mode)

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
            text = if (mode == "fav") "Favoritos" else "Histórico"
            textSize = 18f
            setTextColor(pal.text)
            setTypeface(typeface, android.graphics.Typeface.BOLD)
        }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        header.addView(TextView(this).apply {
            text = "Limpar tudo"
            textSize = 14f
            setTextColor(pal.accent)
            gravity = Gravity.CENTER
            setPadding(dp(12), 0, dp(12), 0)
            setOnClickListener { confirmClear() }
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(52)))
        root.addView(header, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(52)))

        empty = TextView(this).apply {
            text = if (mode == "fav") "Nenhum favorito ainda.\nUse o menu ⋮ > Adicionar aos favoritos." else "Histórico vazio."
            setTextColor(pal.sub)
            gravity = Gravity.CENTER
            setPadding(dp(24), dp(60), dp(24), 0)
        }
        root.addView(empty)

        adapter = object : BaseAdapter() {
            override fun getCount() = items.size
            override fun getItem(p: Int) = items[p]
            override fun getItemId(p: Int) = p.toLong()
            override fun getView(p: Int, cv: View?, parent: ViewGroup?): View {
                val e = items[p]
                val col = LinearLayout(this@LibraryActivity).apply {
                    orientation = LinearLayout.VERTICAL
                    setPadding(dp(16), dp(10), dp(16), dp(10))
                }
                col.addView(TextView(this@LibraryActivity).apply {
                    text = if (e.title.isBlank()) hostOf(e.url) else e.title
                    textSize = 15f
                    setSingleLine()
                    ellipsize = TextUtils.TruncateAt.END
                    setTextColor(pal.text)
                })
                col.addView(TextView(this@LibraryActivity).apply {
                    text = e.url
                    textSize = 12f
                    setSingleLine()
                    ellipsize = TextUtils.TruncateAt.END
                    setTextColor(pal.sub)
                })
                return col
            }
        }
        val lv = ListView(this).apply {
            adapter = this@LibraryActivity.adapter
            divider = null
            setOnItemClickListener { _, _, p, _ -> pick(items[p].url, false) }
            setOnItemLongClickListener { _, _, p, _ ->
                menu(p)
                true
            }
        }
        root.addView(lv, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        setContentView(root)
        styleBars(pal.bar, pal.dark)
        refresh()
    }

    private fun refresh() {
        adapter.notifyDataSetChanged()
        empty.visibility = if (items.isEmpty()) View.VISIBLE else View.GONE
    }

    private fun pick(url: String, newTab: Boolean) {
        setResult(RESULT_OK, Intent().putExtra("url", url).putExtra("newtab", newTab))
        finish()
    }

    private fun dlg() = AlertDialog.Builder(
        this,
        if (pal.dark) android.R.style.Theme_Material_Dialog_Alert else android.R.style.Theme_Material_Light_Dialog_Alert
    )

    private fun menu(p: Int) {
        dlg().setItems(arrayOf("Abrir em nova aba", "Remover")) { _, w ->
            if (w == 0) pick(items[p].url, true) else {
                items.removeAt(p)
                Prefs.saveList(mode, items)
                refresh()
            }
        }.show()
    }

    private fun confirmClear() {
        if (items.isEmpty()) return
        dlg().setMessage("Apagar tudo desta lista?")
            .setPositiveButton("Apagar") { _, _ ->
                items.clear()
                Prefs.saveList(mode, items)
                refresh()
            }.setNegativeButton("Cancelar", null).show()
    }
}
