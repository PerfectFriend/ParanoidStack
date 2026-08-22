package com.example.ui.screens.localization

data class LeaderboardEntry(
    val rank: Int,
    val name: String,
    val rating: Int,
    val countryCode: String,
    val winRate: String,
    val status: String
)

val worldLeaderboardList: List<LeaderboardEntry> = listOf(
    LeaderboardEntry(1, "Magnus_Nardy 👑", 2850, "🇳🇴 NO", "68.4%", "Online"),
    LeaderboardEntry(2, "Oleg_Tashkent", 2792, "🇺🇿 UZ", "65.1%", "Online"),
    LeaderboardEntry(3, "Jean_Pierre_Paris", 2740, "🇫🇷 FR", "62.8%", "Offline"),
    LeaderboardEntry(4, "Onion_Master_Tor", 2690, "🧅 TOR", "60.5%", "Online (Tor)"),
    LeaderboardEntry(5, "Fatma_Istanbul", 2655, "🇹🇷 TR", "59.2%", "Offline"),
    LeaderboardEntry(6, "John_Doe_SimpleX", 2620, "🇺🇸 US", "58.7%", "Online (SMP)"),
    LeaderboardEntry(7, "NardyPro_99 (You)", 1500, "🇷🇺 RU", "50.0%", "Online"),
    LeaderboardEntry(8, "Hans_Berlin", 1490, "🇩🇪 DE", "49.3%", "Offline"),
    LeaderboardEntry(9, "Carlos_Madrid", 1420, "🇪🇸 ES", "48.5%", "Offline")
)
