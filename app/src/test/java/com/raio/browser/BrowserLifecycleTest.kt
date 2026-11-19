package com.raio.browser

import android.app.AlertDialog
import android.content.Context
import android.net.Uri
import android.view.View
import android.view.ViewGroup
import android.webkit.PermissionRequest
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.widget.Switch
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowAlertDialog

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class BrowserLifecycleTest {
    private var browser: ActivityController<MainActivity>? = null
    private val app get() = RuntimeEnvironment.getApplication()
    @Before fun prepare() {
        setPrivate(false)
        resetPrefs()
        app.getSharedPreferences("raio", Context.MODE_PRIVATE).edit().clear().commit()
        Prefs.init(app); Prefs.autoUpdates = false; Prefs.restoreTabs = false
        RaioApp.residentViews = 0
    }
    @After fun close() {
        setPrivate(false)
        browser?.pause()?.stop()?.destroy()
        resetPrefs()
    }
    private fun resetPrefs() { Prefs::class.java.getDeclaredField("sp").apply { isAccessible = true }.set(null, null) }
    private fun setPrivate(value: Boolean) { RaioApp::class.java.getDeclaredField("privateMode").apply { isAccessible = true }.setBoolean(null, value) }
    private fun open(): MainActivity = Robolectric.buildActivity(MainActivity::class.java).setup().also { browser = it }.get()
    private fun call(a: MainActivity, method: String, arg: Any? = null) {
        val m = MainActivity::class.java.declaredMethods.single { it.name == method }
        m.isAccessible = true
        if (arg == null) m.invoke(a) else m.invoke(a, arg)
    }
    @Suppress("UNCHECKED_CAST") private fun tabs(a: MainActivity) = MainActivity::class.java.getDeclaredField("tabs").apply { isAccessible = true }.get(a) as MutableList<Tab>

    @Test fun switchesTabsWithinResidentBudgetAndReusesCachedPage() {
        val a = open()
        call(a, "newTab", "https://example.org/second")
        val second = tabs(a)[1].view
        call(a, "showTab", 0)
        if (tabs(a)[1].view != null) {
            call(a, "showTab", 1)
            assertSame(second, tabs(a)[1].view)
        }
        call(a, "newTab", "https://example.org/third")
        assertTrue(tabs(a).count { it.view != null } in 1..2)
        a.onTrimMemory(android.content.ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW)
        assertEquals(1, tabs(a).count { it.view != null })
    }
    @Test fun closingAnActiveTabDoesNotReuseDestroyedWebView() {
        val a = open()
        call(a, "newTab", "https://example.org/second")
        call(a, "closeTab", 1)
        assertEquals(1, tabs(a).size)
        assertNotNull(tabs(a)[0].view)
        call(a, "closeTab", 0)
        assertEquals(HOME, tabs(a)[0].url)
        assertEquals(1, RaioApp.residentViews)
    }
    @Test fun fullscreenIsAnOverlayAndRestoresBrowserOnExit() {
        val a = open()
        val root = MainActivity::class.java.getDeclaredField("root").apply { isAccessible = true }.get(a) as View
        val chrome = org.robolectric.Shadows.shadowOf(tabs(a)[0].view!!).webChromeClient
        var hidden = 0
        chrome.onShowCustomView(View(a), WebChromeClient.CustomViewCallback { hidden++ })
        assertEquals(View.GONE, root.visibility)
        val full = MainActivity::class.java.getDeclaredField("full").apply { isAccessible = true }.get(a) as View
        assertSame(a.window.decorView, full.parent)
        chrome.onHideCustomView()
        assertEquals(View.VISIBLE, root.visibility)
        assertNull(full.parent)
        assertEquals(1, hidden)
        chrome.onHideCustomView()
        assertEquals(1, hidden)
    }
    @Test fun privatePreferencesCannotOverwriteNormalHistoryOrSiteCookiesSettings() {
        Prefs.saveList("hist", listOf(Entry("Normal", "https://normal.example.org")))
        Prefs.setShieldException("https://normal.example.org", true)
        setPrivate(true); resetPrefs(); Prefs.init(app)
        assertTrue(Prefs.incognito)
        assertTrue(Prefs.list("hist").isEmpty())
        Prefs.saveList("hist", listOf(Entry("Secret", "https://secret.example.org")))
        Prefs.siteFlag("https://normal.example.org", "siteCookies", true)
        setPrivate(false); resetPrefs(); Prefs.init(app)
        assertEquals("Normal", Prefs.list("hist").single().title)
        assertFalse(Prefs.siteCookies("https://normal.example.org"))
    }
    @Test fun privateActivitiesRunInTheirOwnNonExportedProcess() {
        val info = app.packageManager.getActivityInfo(android.content.ComponentName(app, PrivateMainActivity::class.java), 0)
        assertTrue(info.processName.endsWith(":private"))
        assertFalse(info.exported)
        assertEquals("com.raio.browser.private", info.taskAffinity)
    }
    @Test fun settingsSwitchPersistsWhenUserTapsTheSwitchItself() {
        val controller = Robolectric.buildActivity(SettingsActivity::class.java).setup()
        try {
            fun switches(v: View): List<Switch> = if (v is Switch) listOf(v) else if (v is ViewGroup) (0 until v.childCount).flatMap { switches(v.getChildAt(it)) } else emptyList()
            val switch = switches(controller.get().window.decorView).first()
            val before = Prefs.restoreTabs
            switch.performClick()
            assertEquals(!before, Prefs.restoreTabs)
        } finally { controller.pause().stop().destroy() }
    }
    @Test fun insecureAndCrossOriginMediaRequestsAreDeniedWithoutPrompt() {
        val a = open()
        val gate = SitePermissions(a) { "https://example.org" }
        for (origin in listOf("http://example.org", "https://evil.example.org")) {
            val r = Request(origin)
            gate.media(r)
            assertTrue(r.denied); assertFalse(r.granted)
        }
    }
    @Test fun mediaPermissionCannotBeGrantedAfterNavigation() {
        val a = open()
        var current = "https://example.org"
        val gate = SitePermissions(a) { current }
        val r = Request(current)
        gate.media(r)
        current = "https://other.example.org"
        ShadowAlertDialog.getLatestAlertDialog().getButton(AlertDialog.BUTTON_POSITIVE).performClick()
        org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
        assertTrue(r.denied); assertFalse(r.granted)
    }
    @Test fun staleAndroidPermissionResultCannotAuthorizeANewRequest() {
        val a = open()
        val gate = SitePermissions(a) { "https://example.org" }
        val first = Request("https://example.org")
        gate.media(first)
        ShadowAlertDialog.getLatestAlertDialog().getButton(AlertDialog.BUTTON_POSITIVE).performClick()
        org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
        gate.cancel()
        val replacement = Request("https://example.org")
        gate.media(replacement)
        assertTrue(replacement.denied)
        gate.runtimeResult(intArrayOf(android.content.pm.PackageManager.PERMISSION_GRANTED))
        assertFalse(first.granted)
        assertFalse(replacement.granted)
    }
    @Test fun shortcutsAreEscapedAndCanBeClearedWithoutReturningDefaults() {
        Prefs.saveList("shortcuts", listOf(Entry("<script>alert(1)</script>", "https://example.org/?a=1&b=2")))
        val html = StartPage.html(Pal(false))
        assertFalse(html.contains("<script>alert(1)</script>"))
        assertTrue(html.contains("&lt;script&gt;"))
        Prefs.saveList("shortcuts", emptyList())
        assertTrue(Prefs.shortcuts().isEmpty())
    }
    @Test fun downloadRecordIsCreatedWithoutPublicStoragePermissionForAppUpdates() {
        val id = DownloadRecords.enqueue(app, "https://github.com/ezeq-ol/raio/releases/download/v1.2/Raio.apk", "Raio.apk", null, null, null, publicDirectory = false)
        assertEquals(id.toString(), Prefs.list("downloads").single().url)
    }
    private class Request(origin: String) : PermissionRequest() {
        private val uri = Uri.parse(origin)
        var denied = false; var granted = false
        override fun getOrigin() = uri
        override fun getResources() = arrayOf(RESOURCE_VIDEO_CAPTURE)
        override fun grant(resources: Array<out String>) { granted = true }
        override fun deny() { denied = true }
    }
}
