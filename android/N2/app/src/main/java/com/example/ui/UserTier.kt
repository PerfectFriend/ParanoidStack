package com.example.ui

enum class UserTier {
    FREE, PREMIUM, ROYAL;

    fun label(lang: String): String {
        return when (this) {
            FREE -> if (lang == "RU") "Free (6с, 1 стэк)" else "Free (6s, 1 stack)"
            PREMIUM -> if (lang == "RU") "Premium (20с, 5 стэков)" else "Premium (20s, 5 stacks)"
            ROYAL -> if (lang == "RU") "Royal (120с, безлимит)" else "Royal (120s, unlimited)"
        }
    }
}
