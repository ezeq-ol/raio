package com.raio.browser

import android.content.Context
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [21, 35])
class CompatibilitySmokeTest {
    @Test fun browserAndUtilityScreensOpenOnOldAndModernAndroid() {
        val app = RuntimeEnvironment.getApplication()
        Prefs::class.java.getDeclaredField("sp").apply { isAccessible = true }.set(null, null)
        app.getSharedPreferences("raio", Context.MODE_PRIVATE).edit().clear().commit()
        Prefs.init(app); Prefs.autoUpdates = false
        val main = Robolectric.buildActivity(MainActivity::class.java).setup()
        val settings = Robolectric.buildActivity(SettingsActivity::class.java).setup()
        settings.pause().stop().destroy()
        val downloads = Robolectric.buildActivity(DownloadsActivity::class.java).setup()
        downloads.pause().stop().destroy()
        val shortcuts = Robolectric.buildActivity(ShortcutsActivity::class.java).setup()
        shortcuts.pause().stop().destroy()
        main.pause().stop().destroy()
    }
}
