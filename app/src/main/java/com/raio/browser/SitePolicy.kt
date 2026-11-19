package com.raio.browser

import java.net.URI

object SitePolicy {
    fun origin(url: String): String = try {
        val u = URI(url)
        val h = AdBlock.host(url)
        if (h.isEmpty()) "" else {
            val scheme = u.scheme.lowercase(java.util.Locale.ROOT)
            val port = if (u.port < 0 || (scheme == "https" && u.port == 443) || (scheme == "http" && u.port == 80)) "" else ":${u.port}"
            "$scheme://$h$port"
        }
    } catch (_: Exception) { "" }

    fun secure(url: String) = origin(url).startsWith("https://")
    fun sameOrigin(a: String, b: String) = origin(a).isNotEmpty() && origin(a) == origin(b)

    fun newer(candidate: String, installed: String): Boolean {
        fun parts(v: String): List<Int>? = v.removePrefix("v").takeIf { it.matches(Regex("\\d+(\\.\\d+){0,3}")) }
            ?.split('.')?.map { it.toIntOrNull() ?: return null }
        val a = parts(candidate) ?: return false
        val b = parts(installed) ?: return false
        for (i in 0 until maxOf(a.size, b.size)) {
            val d = a.getOrElse(i) { 0 }.compareTo(b.getOrElse(i) { 0 })
            if (d != 0) return d > 0
        }
        return false
    }

    fun releaseAsset(url: String): Boolean = try {
        val u = URI(url)
        u.scheme == "https" && u.host == "github.com" && u.userInfo == null && u.port == -1 &&
            u.query == null && u.fragment == null &&
            u.rawPath.matches(Regex("/ezeq-ol/raio/releases/download/v?\\d+(?:\\.\\d+){0,3}/Raio\\.apk"))
    } catch (_: Exception) { false }

    fun domains(text: String): Set<String> {
        require(text.length <= 512 * 1024) { "Lista grande demais" }
        val domains = linkedSetOf<String>()
        for (raw in text.lineSequence()) {
            val s = raw.trim().lowercase(java.util.Locale.ROOT)
            if (s.isEmpty() || s.startsWith('#') || s.startsWith('!')) continue
            require(s.length <= 253 && s.split('.').size >= 2 && s.split('.').all {
                it.length in 1..63 && it.matches(Regex("[a-z0-9](?:[a-z0-9-]*[a-z0-9])?"))
            } && !s.matches(Regex("[0-9.]+"))) { "Domínio inválido na lista" }
            domains.add(s)
            require(domains.size <= 20000) { "Muitas regras" }
        }
        require(domains.size >= 10) { "Lista vazia ou incompleta" }
        return domains
    }
}
