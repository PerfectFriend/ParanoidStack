package com.example.ui.screens

import com.example.ui.screens.localization.LeaderboardEntry
import com.example.ui.screens.localization.uiTranslations
import com.example.ui.screens.localization.worldLeaderboardList

object Language {
    val worldLeaderboardList: List<LeaderboardEntry> = com.example.ui.screens.localization.worldLeaderboardList

    fun get(key: String, lang: String): String {
        val dict = uiTranslations[lang] ?: uiTranslations["EN"] ?: return key
        return dict[key] ?: uiTranslations["EN"]?.get(key) ?: key
    }

    private val uiTranslations: Map<String, Map<String, String>> = com.example.ui.screens.localization.uiTranslations
}
