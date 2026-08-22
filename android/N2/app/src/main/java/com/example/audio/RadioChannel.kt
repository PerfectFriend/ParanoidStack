/**
 * Represents a radio station channel that can be streamed in the radio player.
 * Default channels are defined in [RadioChannel.Companion.DEFAULT_CHANNELS]; users can
 * add custom channels via [RadioManager.addCustomChannel].
 */
package com.example.audio

/**
 * A single radio channel with an ID, display name, stream URL, and metadata.
 * @property id unique channel identifier (e.g. "ru_record", "custom_12345").
 * @property name human-readable display name.
 * @property streamUrl HTTP/HTTPS stream URL (or .onion URL for Tor).
 * @property isOnline whether this channel is an internet stream (vs. local file).
 * @property genre genre label for categorisation.
 */
data class RadioChannel(
    val id: String,
    val name: String,
    val streamUrl: String,
    val isOnline: Boolean = true,
    val genre: String = "Mixed"
) {
    companion object {
        val DEFAULT_CHANNELS = listOf(
            RadioChannel("armageddon", "☢ Radio Armageddon FM", "https://icecast.armageddon.example/stream.mp3"),
            RadioChannel("jazz", "🎷 Jazz Noir", "https://icecast.jazz.example/stream.mp3"),
            RadioChannel("ambient", "🌌 Ambient Space", "https://icecast.ambient.example/stream.mp3"),
            RadioChannel("onion_stream", "🧅 [ONION] SimpleX Secure Radio", "http://fv3pfzxih5sjf33jmusfbskmd2i3lywaaaysh6tijc7df7k6sijq3yyd.onion/radio/stream.mp3"),
            RadioChannel("classical", "🎼 Classical Republic", "https://icecast.classical.example/stream.mp3"),
            RadioChannel("talk", "🎙 TalkNet", "https://icecast.talk.example/stream.mp3"),
            RadioChannel("electronic", "🔊 Electronic Pulse", "https://icecast.electronic.example/stream.mp3"),
            RadioChannel("folk", "🪕 Folk & Roots", "https://icecast.folk.example/stream.mp3")
        )
    }
}
