package com.raio.browser

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.pm.PackageManager
import android.os.Build
import android.webkit.GeolocationPermissions
import android.webkit.PermissionRequest

class SitePermissions(private val activity: Activity, private val currentUrl: () -> String) {
    private var pending: PermissionRequest? = null
    private var origin = ""
    private var finishRequest: ((Boolean) -> Unit)? = null
    private var runtimePending = false
    private var dialog: AlertDialog? = null

    fun media(r: PermissionRequest) {
        val kinds = r.resources.toList()
        val allowed = setOf(PermissionRequest.RESOURCE_VIDEO_CAPTURE, PermissionRequest.RESOURCE_AUDIO_CAPTURE)
        if (runtimePending || kinds.isEmpty() || kinds.any { it !in allowed } ||
            !SitePolicy.secure(r.origin.toString()) || !SitePolicy.sameOrigin(r.origin.toString(), currentUrl()) ||
            kinds.any { Prefs.permissionBlocked(r.origin.toString(), if (it == PermissionRequest.RESOURCE_VIDEO_CAPTURE) "camera" else "mic") }) {
            r.deny(); return
        }
        cancel()
        pending = r
        val permissions = kinds.map { if (it == PermissionRequest.RESOURCE_VIDEO_CAPTURE) Manifest.permission.CAMERA else Manifest.permission.RECORD_AUDIO }
        ask(r.origin.toString(), permissions, kinds.joinToString(" e ") { if (it == PermissionRequest.RESOURCE_VIDEO_CAPTURE) "câmera" else "microfone" }) { ok ->
            if (pending === r) {
                pending = null
                if (ok) r.grant(kinds.toTypedArray()) else r.deny()
            }
        }
    }
    fun location(requestOrigin: String, callback: GeolocationPermissions.Callback) {
        if (runtimePending || !SitePolicy.secure(requestOrigin) || !SitePolicy.sameOrigin(requestOrigin, currentUrl()) || Prefs.permissionBlocked(requestOrigin, "location")) {
            callback.invoke(requestOrigin, false, false); return
        }
        cancel()
        ask(requestOrigin, listOf(Manifest.permission.ACCESS_COARSE_LOCATION), "localização aproximada") { ok -> callback.invoke(requestOrigin, ok, false) }
    }
    private fun ask(requestOrigin: String, permissions: List<String>, label: String, result: (Boolean) -> Unit) {
        origin = requestOrigin
        finishRequest = result
        dialog = AlertDialog.Builder(activity).setTitle("Permissão do site")
            .setMessage("${SitePolicy.origin(requestOrigin)} quer usar $label. Permitir apenas esta solicitação?")
            .setPositiveButton("Permitir desta vez") { _, _ ->
                if (!SitePolicy.sameOrigin(origin, currentUrl())) { complete(false); return@setPositiveButton }
                if (Build.VERSION.SDK_INT >= 23) {
                    val missing = permissions.filter { activity.checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED }
                    if (missing.isNotEmpty()) { runtimePending = true; activity.requestPermissions(missing.toTypedArray(), 55) } else complete(true)
                } else complete(true)
            }.setNegativeButton("Bloquear") { _, _ -> complete(false) }
            .setOnCancelListener { complete(false) }.show()
    }
    fun runtimeResult(results: IntArray) {
        if (!runtimePending) return
        runtimePending = false
        complete(results.isNotEmpty() && results.all { it == PackageManager.PERMISSION_GRANTED })
    }
    private fun complete(allowed: Boolean) {
        val fn = finishRequest
        finishRequest = null
        fn?.invoke(allowed && SitePolicy.sameOrigin(origin, currentUrl()))
    }
    fun cancelled(r: PermissionRequest) { if (pending === r) cancel() }
    fun cancel() {
        dialog?.dismiss(); dialog = null
        complete(false)
        pending = null
    }
}
