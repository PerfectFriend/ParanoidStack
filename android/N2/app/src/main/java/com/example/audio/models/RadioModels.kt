package com.example.audio.models

data class LocalTrack(
    val id: String,
    val name: String,
    val uriString: String,
    val isSelected: Boolean = true
)

data class TelegramTrack(
    val id: String,
    val title: String,
    val artist: String,
    val url: String
)
