package com.n3.app.bridges

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.webkit.JavascriptInterface
import com.n3.app.security.SecurityManager

class ClipboardBridge(private val ctx: Context) {
    private val sm = SecurityManager(ctx)

    @JavascriptInterface fun copy(text: String) {
        (ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager)
            .setPrimaryClip(ClipData.newPlainText("N3", text))
        sm.scheduleClipboardClear(text)
    }

    @JavascriptInterface fun paste(): String =
        ((ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager)
            .primaryClip?.getItemAt(0)?.text?.toString() ?: "")

    @JavascriptInterface fun copyNoAutoClear(text: String) {
        sm.cancelClipboardClear()
        (ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager)
            .setPrimaryClip(ClipData.newPlainText("N3", text))
    }
}
