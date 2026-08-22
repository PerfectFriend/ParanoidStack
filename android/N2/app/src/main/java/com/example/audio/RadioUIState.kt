/**
 * Immutable UI state holder for the radio player.
 * Updated reactively by [RadioManager] via Compose [mutableStateOf].
 */
package com.example.audio

/**
 * Observable UI state for the radio/music player.
 * @property channels full list of available channels (defaults + custom).
 * @property currentChannel the currently selected channel (null if none).
 * @property isPlaying whether audio is currently playing.
 * @property isRecording whether a voice recording is in progress.
 * @property playerState lifecycle state of the underlying [StreamPlayer].
 * @property volume current volume level (0.0 – 1.0).
 * @property isArmageddonUnlocked whether the "Armageddon FM" channel has been unlocked.
 * @property error last error message, or null.
 */
data class RadioUIState(
    val channels: List<RadioChannel> = RadioChannel.DEFAULT_CHANNELS,
    val currentChannel: RadioChannel? = null,
    val isPlaying: Boolean = false,
    val isRecording: Boolean = false,
    val playerState: StreamPlayer.PlayerState = StreamPlayer.PlayerState.IDLE,
    val volume: Float = 0.5f,
    val isArmageddonUnlocked: Boolean = false,
    val error: String? = null
)
