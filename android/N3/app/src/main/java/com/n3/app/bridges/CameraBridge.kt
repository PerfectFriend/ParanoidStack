package com.n3.app.bridges

import android.webkit.JavascriptInterface
import com.google.gson.Gson

class CameraBridge(private val scanner: (String) -> Unit) {
    private val gson = Gson()

    @JavascriptInterface fun scanQR(callback: String) {
        scanner(callback)
    }

    @JavascriptInterface fun isAvailable(): Boolean = true
}
