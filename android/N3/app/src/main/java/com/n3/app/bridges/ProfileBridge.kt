package com.n3.app.bridges

import android.webkit.JavascriptInterface
import com.google.gson.Gson
import com.n3.app.MainActivity
import com.n3.app.profile.ProfileManager

class ProfileBridge(private val activity: MainActivity) {
    private val pm = ProfileManager(activity)
    private val gson = Gson()

    @JavascriptInterface fun hasProfile(): Boolean = pm.hasProfile()
    @JavascriptInterface fun isVerified(): Boolean = pm.isVerified()

    @JavascriptInterface fun createProfile(wordsJson: String): String {
        val words = gson.fromJson(wordsJson, Array<String>::class.java).toList()
        if (words.size != 5) return "[]"
        if (words.any { it.length > 16 }) return "[]"
        val phrase = pm.createProfile(words)
        return gson.toJson(phrase)
    }

    @JavascriptInterface fun getVerificationChallenge(): String {
        return gson.toJson(pm.getVerificationChallenge())
    }

    @JavascriptInterface fun checkVerificationWord(index: Int, word: String): Boolean {
        val correct = pm.verifyWord(listOf(index))[index]
        return correct == word.lowercase()
    }

    @JavascriptInterface fun markVerified() {
        pm.markVerified()
        activity.startBoot()
    }

    @JavascriptInterface fun getCreatedAt(): Long = pm.getCreatedAt()
}
