/**
 * Plays streaming audio URLs via Android's [MediaPlayer] with state-change callbacks.
 *
 * Supports play, pause, resume, and stop operations. Notifies the caller of state transitions
 * through [onStateChange]. Designed for radio streaming (see [RadioManager]) but usable for
 * any HTTP/HTTPS audio stream.
 *
 * ## State machine
 * `IDLE` → `BUFFERING` → `PLAYING` ⇄ `PAUSED` → `IDLE`
 *                              ↘ `ERROR`
 */
package com.example.audio

import android.media.MediaPlayer
import android.util.Log
import java.net.URI

/**
 * Lightweight wrapper around [MediaPlayer] for streaming audio URLs.
 * Reports lifecycle transitions (IDLE, BUFFERING, PLAYING, PAUSED, ERROR) via a callback.
 */
class StreamPlayer(
    private val onStateChange: (PlayerState) -> Unit = {}
) {
    private var mediaPlayer: MediaPlayer? = null
    private val tag = "StreamPlayer"
    
    /** Possible states of the stream player lifecycle. */
    enum class PlayerState { IDLE, BUFFERING, PLAYING, PAUSED, ERROR }
    
    var state: PlayerState = PlayerState.IDLE
        private set
    
    val isPlaying: Boolean get() = state == PlayerState.PLAYING
    
    /**
     * Begins buffering and playing the given streaming URL.
     * Transitions to [PlayerState.BUFFERING] immediately; on success moves to [PlayerState.PLAYING].
     * @param url the HTTP/HTTPS stream URL to play.
     */
    fun play(url: String) {
        stop()
        state = PlayerState.BUFFERING
        onStateChange(state)
        try {
            mediaPlayer = MediaPlayer().apply {
                setDataSource(url)
                setOnPreparedListener { 
                    state = PlayerState.PLAYING
                    onStateChange(state)
                    start()
                }
                setOnErrorListener { _, what, extra ->
                    Log.e(tag, "MediaPlayer error: $what $extra")
                    state = PlayerState.ERROR
                    onStateChange(state)
                    true
                }
                setOnCompletionListener {
                    state = PlayerState.IDLE
                    onStateChange(state)
                }
                prepareAsync()
            }
        } catch (e: Exception) {
            Log.e(tag, "Failed to play $url", e)
            state = PlayerState.ERROR
            onStateChange(state)
        }
    }
    
    /** Pauses playback if currently playing. Transitions to [PlayerState.PAUSED]. */
    fun pause() {
        mediaPlayer?.let {
            if (it.isPlaying) {
                it.pause()
                state = PlayerState.PAUSED
                onStateChange(state)
            }
        }
    }
    
    /** Resumes playback from [PlayerState.PAUSED]. Transitions back to [PlayerState.PLAYING]. */
    fun resume() {
        mediaPlayer?.let {
            if (!it.isPlaying && state == PlayerState.PAUSED) {
                it.start()
                state = PlayerState.PLAYING
                onStateChange(state)
            }
        }
    }
    
    /** Stops playback, releases the underlying [MediaPlayer], and resets to [PlayerState.IDLE]. */
    fun stop() {
        try {
            mediaPlayer?.stop()
            mediaPlayer?.release()
        } catch (e: Exception) { Log.e(tag, "stop error", e) }
        mediaPlayer = null
        state = PlayerState.IDLE
        onStateChange(state)
    }
}
