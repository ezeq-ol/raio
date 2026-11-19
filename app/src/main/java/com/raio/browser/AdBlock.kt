package com.raio.browser

import java.net.URI
import java.util.Locale

/** Local, deterministic network rules. No proxy, remote requests or browsing telemetry. */
object AdBlock {
    private val hosts = hashSetOf(
        "doubleclick.net", "googlesyndication.com", "googleadservices.com", "google-analytics.com",
        "googletagservices.com", "adservice.google.com", "adsystem.com", "amazon-adsystem.com",
        "adnxs.com", "adsrvr.org", "taboola.com", "outbrain.com", "criteo.com", "criteo.net",
        "rubiconproject.com", "pubmatic.com", "openx.net", "casalemedia.com", "scorecardresearch.com",
        "quantserve.com", "moatads.com", "adform.net", "smartadserver.com", "advertising.com",
        "adcolony.com", "applovin.com", "unityads.unity3d.com", "mopub.com", "inmobi.com",
        "chartboost.com", "vungle.com", "ironsrc.com", "hotjar.com", "mixpanel.com", "segment.io",
        "popads.net", "propellerads.com", "exoclick.com", "juicyads.com", "trafficjunky.com",
        "revcontent.com", "mgid.com", "zedo.com", "yieldmo.com", "teads.tv", "33across.com",
        "bidswitch.net", "sharethrough.com", "media.net", "bat.bing.com", "clarity.ms",
        "ads.yahoo.com", "adsafeprotected.com", "doubleverify.com", "krxd.net", "bluekai.com",
        "demdex.net", "everesttech.net", "adtech.com", "adcash.com", "hilltopads.net", "pop.ads", "googletagmanager.com", "analytics.google.com", "stats.g.doubleclick.net",
        "analytics.tiktok.com", "ads.tiktok.com", "ads.twitter.com", "analytics.twitter.com",
        "ads.linkedin.com", "px.ads.linkedin.com", "snap.licdn.com", "tr.snapchat.com",
        "sc-static.net", "connect.facebook.net", "an.facebook.com", "pixel.facebook.com",
        "ads.reddit.com", "events.reddit.com", "alb.reddit.com", "adobedtm.com", "omtrdc.net",
        "2mdn.net", "imasdk.googleapis.com", "s0.2mdn.net", "tpc.googlesyndication.com",
        "serving-sys.com", "sizmek.com", "flashtalking.com", "innovid.com", "spotxchange.com",
        "spotx.tv", "tremorhub.com", "unrulymedia.com", "vidoomy.com", "seedtag.com",
        "undertone.com", "sovrn.com", "lijit.com", "gumgum.com", "triplelift.com", "kargo.com",
        "indexww.com", "contextweb.com", "yieldlab.net", "yieldlove.com", "adnami.io",
        "onetag-sys.com", "rtbhouse.com", "adroll.com", "bidswitch.com", "bidr.io",
        "smartclip.net", "smartclip.com", "freewheel.tv", "fwmrm.net", "ads.stickyadstv.com",
        "trafficstars.com", "clickadu.com", "onclickads.net", "onclickalgo.com", "clickaine.com",
        "popcash.net", "popunder.net", "adsterra.com", "monetag.com", "richads.com",
        "bidvertiser.com", "a-ads.com", "cointraffic.io", "coinzilla.com", "ad-maven.com",
        "histats.com", "statcounter.com", "clicky.com", "mouseflow.com", "luckyorange.com",
        "fullstory.com", "logrocket.io", "inspectlet.com", "crazyegg.com", "woopra.com",
        "amplitude.com", "api.segment.io", "segment.com", "heapanalytics.com", "kissmetrics.com",
        "newrelic.com", "nr-data.net", "bam.nr-data.net", "sentry.io", "bugsnag.com"
    )

    fun host(url: String): String = try {
        val uri = URI(url)
        if (uri.scheme.equals("http", true) || uri.scheme.equals("https", true)) {
            uri.host?.lowercase(Locale.ROOT)?.trimEnd('.') ?: ""
        } else ""
    } catch (_: Exception) { "" }

    private fun within(host: String, domain: String) = host == domain || host.endsWith(".$domain")

    fun blocked(host: String?): Boolean {
        var h = host?.lowercase(Locale.ROOT)?.trimEnd('.') ?: return false
        while (h.isNotEmpty()) {
            if (hosts.contains(h)) return true
            val i = h.indexOf('.')
            if (i < 0) return false
            h = h.substring(i + 1)
        }
        return false
    }

    private val adPaths = Regex("(^|/)(pagead|ads?|adserver|adservice|ad-serving|ad-banners|doubleclick|gampad|prebid)(/|[._-]|$)")
    private val trackerPaths = Regex("(^|/)(analytics|tracking|telemetry|beacon|pixel)(/|[._-]|$)")

    fun shouldBlock(url: String, pageUrl: String, aggressive: Boolean): Boolean {
        val h = host(url)
        if (h.isEmpty()) return false
        if (blocked(h)) return true
        if (!aggressive) return false
        val path = try { URI(url).path?.lowercase(Locale.ROOT) ?: "" } catch (_: Exception) { return false }
        // Keep login, ordinary playback and the googlevideo media CDN intact.
        if (within(h, "youtube.com") || within(h, "youtube-nocookie.com")) {
            return path.startsWith("/api/stats/ads") || path.startsWith("/pagead/") ||
                path.startsWith("/ptracking") || path.startsWith("/youtubei/v1/log_event")
        }
        if (within(h, "googlevideo.com") || h == "accounts.google.com") return false
        if (adPaths.containsMatchIn(path)) return true
        val pageHost = host(pageUrl)
        val related = h == pageHost || h.endsWith(".$pageHost") || pageHost.endsWith(".$h")
        return pageHost.isNotEmpty() && !related && trackerPaths.containsMatchIn(path)
    }
}
