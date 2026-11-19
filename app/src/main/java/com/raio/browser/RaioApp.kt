package com.raio.browser

import android.app.Application
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Build
import android.webkit.WebView
import android.widget.Toast
import java.io.File

class RaioApp : Application() {
    companion object {
        var privateMode = false; private set
        var screenStartupMs = 0L
        var residentViews = 0
        fun clearPrivateProfiles(c: Context) {
            File(c.applicationInfo.dataDir).listFiles()?.filter {
                it.name.startsWith("app_webview_raio_private_") && it.isDirectory
            }?.forEach { check(it.deleteRecursively()) { "Não foi possível limpar a sessão privada anterior" } }
        }
    }
    override fun onCreate() {
        super.onCreate()
        privateMode = Build.VERSION.SDK_INT >= 28 && getProcessName().endsWith(":private")
        if (privateMode && Build.VERSION.SDK_INT >= 28) {
            // Each private process starts with a fresh WebView profile. A killed process
            // cannot accidentally restore an earlier private login on its next start.
            clearPrivateProfiles(this)
            deleteSharedPreferences("raio_private")
            WebView.setDataDirectorySuffix("raio_private_" + java.util.UUID.randomUUID().toString())
        }
        Prefs.init(this)
        FilterStore.load(this)
    }
}

fun switchPrivacy(activity: Activity, enable: Boolean) {
    if (enable) {
        if (Build.VERSION.SDK_INT < 28) {
            Toast.makeText(activity, "O modo anônimo isolado requer Android 9 ou superior.", Toast.LENGTH_LONG).show()
            return
        }
        activity.startActivity(Intent(activity, PrivateMainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } else {
        // PrivateMainActivity owns the entire private task and finishes it on return.
        activity.startActivity(Intent(activity, PrivateMainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            .putExtra("exitPrivate", true))
        if (activity !is PrivateMainActivity) activity.finish()
    }
}

class PrivateMainActivity : MainActivity()
class PrivateSettingsActivity : SettingsActivity()
class PrivateLibraryActivity : LibraryActivity()
class PrivateDownloadsActivity : DownloadsActivity()
class PrivateShortcutsActivity : ShortcutsActivity()
