package com.raio.browser

/** Pagina inicial e pagina de erro: HTML local minusculo, abre instantaneamente (sem rede). */
object StartPage {
    const val ERR_TITLE = "Falha ao carregar"

    private fun esc(s: String) = s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")
    private fun hex(c: Int) = String.format("#%06X", c and 0xFFFFFF)

    fun html(p: Pal): String {
        val eng = Prefs.engines[Prefs.engine]
        val action = eng.second.substringBefore("?")
        val shortcuts = Prefs.list("fav").take(8).ifEmpty {
            listOf(
                Entry("YouTube", "https://m.youtube.com"),
                Entry("Wikipédia", "https://pt.m.wikipedia.org"),
                Entry("G1", "https://g1.globo.com"),
                Entry("Mercado Livre", "https://www.mercadolivre.com.br")
            )
        }
        val tiles = StringBuilder()
        for (e in shortcuts) {
            val name = if (e.title.isBlank()) hostOf(e.url) else e.title
            val letter = name.trim().firstOrNull()?.uppercaseChar()?.toString() ?: "?"
            tiles.append("<a class=t href=\"").append(esc(e.url)).append("\"><i>").append(esc(letter))
                .append("</i><span>").append(esc(name.take(14))).append("</span></a>")
        }
        val incog = if (Prefs.incognito) "<p class=in>Modo anônimo ligado: nada é salvo.</p>" else ""
        return """<!DOCTYPE html><html><head><meta charset="utf-8">
<meta name="viewport" content="width=device-width,initial-scale=1">
<style>
*{box-sizing:border-box;-webkit-tap-highlight-color:transparent}
body{margin:0;background:${hex(p.bg)};color:${hex(p.text)};font-family:sans-serif}
.c{max-width:480px;margin:0 auto;padding:12vh 20px 20px;text-align:center}
.logo{display:flex;align-items:center;justify-content:center;gap:8px;font-size:34px;font-weight:800;letter-spacing:1px}
.logo svg{width:38px;height:38px}
form{margin:26px 0 30px;display:flex;background:${hex(p.field)};border-radius:28px;overflow:hidden}
input{flex:1;min-width:0;border:0;background:transparent;color:${hex(p.text)};font-size:16px;padding:15px 18px;outline:0}
button{border:0;background:${hex(p.accent)};color:#fff;font-weight:700;font-size:15px;padding:0 20px}
.g{display:grid;grid-template-columns:repeat(4,1fr);gap:14px 6px}
.t{text-decoration:none;color:${hex(p.sub)};font-size:12px;display:flex;flex-direction:column;align-items:center;gap:6px}
.t i{width:52px;height:52px;border-radius:26px;background:${hex(p.field)};color:${hex(p.accent)};font-style:normal;font-weight:800;font-size:22px;display:flex;align-items:center;justify-content:center}
.in{color:${hex(p.sub)};font-size:13px;margin-top:28px}
</style></head><body><div class="c">
<div class="logo"><svg viewBox="0 0 24 24"><path fill="${hex(p.accent)}" d="M13.5 1 4 14h6l-1.5 9L20 9h-6.5z"/></svg>Raio</div>
<form action="${esc(action)}" method="get"><input name="q" placeholder="Pesquisar com ${esc(eng.first)}" autocomplete="off"><button type="submit">Ir</button></form>
<div class="g">$tiles</div>$incog
</div></body></html>"""
    }

    fun error(p: Pal, url: String, msg: String): String = """<!DOCTYPE html><html><head><meta charset="utf-8">
<meta name="viewport" content="width=device-width,initial-scale=1"><title>$ERR_TITLE</title>
<style>body{margin:0;background:${hex(p.bg)};color:${hex(p.text)};font-family:sans-serif;text-align:center;padding:18vh 24px 0}
h2{margin:0 0 10px}p{color:${hex(p.sub)};word-break:break-all;font-size:14px}
a{display:inline-block;margin-top:22px;background:${hex(p.accent)};color:#fff;text-decoration:none;padding:13px 26px;border-radius:26px;font-weight:700}</style></head>
<body><h2>Não foi possível abrir a página</h2><p>${esc(msg)}</p><p>${esc(url)}</p><a href="${esc(url)}">Tentar novamente</a></body></html>"""
}
