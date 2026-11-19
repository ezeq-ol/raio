package com.raio.browser

import org.junit.Assert.*
import org.junit.Test

class AdBlockTest {
    @Test fun normalizesHostNames() {
        assertTrue(AdBlock.blocked("ADS.DOUBLECLICK.NET."))
        assertEquals("youtube.com", AdBlock.host("HTTPS://YouTube.COM./watch?v=abc"))
    }
    @Test fun domainBoundariesDoNotMatchLookalikes() {
        assertFalse(AdBlock.blocked("notdoubleclick.net"))
        assertFalse(AdBlock.blocked("doubleclick.net.example.org"))
    }
    @Test fun blocksSubdomainsAndTrackerVendors() {
        assertTrue(block("https://a.b.doubleclick.net/video", false))
        assertTrue(block("https://connect.facebook.net/en_US/fbevents.js"))
        assertTrue(block("https://cdn.taboola.com/lib.js"))
    }
    @Test fun adPathsAreAggressiveOnly() {
        assertTrue(block("https://example.org/pagead/show"))
        assertTrue(block("https://example.org/js/prebid.min.js"))
        assertFalse(block("https://example.org/pagead/show", false))
    }
    @Test fun ordinaryPathsAndQueryWordsRemainUsable() {
        assertFalse(block("https://example.org/news/advertising-careers"))
        assertFalse(block("https://example.org/search?q=ads"))
        assertFalse(block("https://example.org/downloads/app.apk"))
    }
    @Test fun malformedAndNonNetworkUrlsAreIgnored() {
        for (url in listOf("not a url", "data:text/plain,ads", "blob:https://youtube.com/id", "file:///ads/test")) {
            assertFalse(block(url))
        }
    }
    @Test fun thirdPartyTrackingIsBlockedButSameSiteApiIsKept() {
        assertTrue(block("https://other.example/telemetry/event"))
        assertFalse(block("https://example.org/telemetry/event"))
        assertFalse(block("https://api.example.org/analytics/report"))
    }
    @Test fun youtubeAdEndpointsAreBlocked() {
        assertTrue(block("https://www.youtube.com/api/stats/ads?x=1"))
        assertTrue(block("https://m.youtube.com/pagead/adview"))
        assertTrue(block("https://www.youtube.com/youtubei/v1/log_event"))
    }
    @Test fun youtubePlaybackAndLoginArePreserved() {
        for (url in listOf("https://www.youtube.com/youtubei/v1/player", "https://www.youtube.com/watch?v=ads", "https://r1.googlevideo.com/videoplayback?id=123", "https://accounts.google.com/signin", "https://i.ytimg.com/vi/abc/hqdefault.jpg")) {
            assertFalse(block(url))
        }
    }
    private fun block(url: String, aggressive: Boolean = true) =
        AdBlock.shouldBlock(url, "https://example.org/article", aggressive)
}
