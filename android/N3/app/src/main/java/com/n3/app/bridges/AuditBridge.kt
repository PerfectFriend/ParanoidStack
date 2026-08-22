package com.n3.app.bridges

import android.content.Context
import android.webkit.JavascriptInterface
import com.google.gson.Gson
import com.n3.app.audit.AuditLogManager

class AuditBridge(private val ctx: Context) {
    private val audit = AuditLogManager(ctx)
    private val gson = Gson()

    @JavascriptInterface fun getRecent(limit: Int): String = gson.toJson(audit.getRecent(limit))
    @JavascriptInterface fun getAll(): String = gson.toJson(audit.getAll())
    @JavascriptInterface fun getByType(type: String): String = gson.toJson(audit.getByType(type))
    @JavascriptInterface fun record(type: String, source: String, details: String, level: String) {
        audit.record(type, source, details, level)
    }
    @JavascriptInterface fun clear() { audit.clear() }
    @JavascriptInterface fun prune() { audit.prune() }
}
