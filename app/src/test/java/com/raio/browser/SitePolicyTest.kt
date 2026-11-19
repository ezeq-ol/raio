package com.raio.browser

import org.junit.Assert.*
import org.junit.Test

class SitePolicyTest {
    @Test fun originsRespectSchemeAndPort() {
        assertTrue(SitePolicy.sameOrigin("https://EXAMPLE.org:443/page", "https://example.org/"))
        assertFalse(SitePolicy.sameOrigin("https://example.org", "http://example.org"))
        assertFalse(SitePolicy.sameOrigin("https://example.org:8443", "https://example.org"))
        assertFalse(SitePolicy.sameOrigin("https://example.org.evil.com", "https://example.org"))
        assertFalse(SitePolicy.sameOrigin("about:blank", "about:blank"))
    }
    @Test fun versionComparisonIsNumericAndRejectsUnstableTags() {
        assertTrue(SitePolicy.newer("v1.10", "1.9"))
        assertTrue(SitePolicy.newer("2", "1.99"))
        assertFalse(SitePolicy.newer("v1.2.0", "1.2"))
        assertFalse(SitePolicy.newer("1.1", "1.2"))
        assertFalse(SitePolicy.newer("v1.3-beta", "1.2"))
        assertFalse(SitePolicy.newer("9999999999999999999999", "1.2"))
    }
    @Test fun downloadsOnlyComeFromOurReleasePaths() {
        assertTrue(SitePolicy.releaseAsset("https://github.com/ezeq-ol/raio/releases/download/v1.3/Raio.apk"))
        for (url in listOf("http://github.com/ezeq-ol/raio/releases/download/v1.3/Raio.apk", "https://github.com.evil.org/ezeq-ol/raio/releases/download/v1.3/Raio.apk", "https://github.com/other/raio/releases/download/v1.3/Raio.apk", "https://github.com/ezeq-ol/raio/releases/download/v1.3/Raio.apk?redirect=evil", "https://github.com/ezeq-ol/raio/releases/download/v1.3/../Raio.apk")) assertFalse(SitePolicy.releaseAsset(url))
    }
    @Test fun updatedDomainListsRejectScriptsWildcardsIpsAndTruncatedResponses() {
        val base = (1..10).joinToString("\n") { "ads$it.example.org" }
        assertEquals(10, SitePolicy.domains("# header\n$base\nads1.example.org").size)
        for (bad in listOf("<html>error</html>", "*.example.org", "https://example.org/path", "127.0.0.1", "a..example.org", "-ads.example.org")) {
            try { SitePolicy.domains("$base\n$bad"); fail("Must reject $bad") } catch (_: IllegalArgumentException) { }
        }
        try { SitePolicy.domains("one.example.org"); fail("Reject incomplete list") } catch (_: IllegalArgumentException) { }
    }
    @Test fun downloadedRulesCanRemoveOldFalsePositives() {
        try {
            AdBlock.replaceExtra(SitePolicy.domains((1..10).joinToString("\n") { "ads$it.example.org" }))
            assertTrue(AdBlock.blocked("cdn.ads1.example.org"))
            assertFalse(AdBlock.blocked("doubleclick.net"))
        } finally { AdBlock.replaceExtra(emptySet()) }
    }
}
