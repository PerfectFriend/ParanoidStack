package com.n3.app.bridges

import android.content.Context
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.net.Uri
import android.util.Base64
import android.util.Log
import android.webkit.JavascriptInterface
import com.google.gson.Gson
import java.io.ByteArrayOutputStream
import java.io.File

class VoiceBridge(private val ctx: Context) {
    companion object { private const val TAG = "N3/Voice" }

    private var recorder: MediaRecorder? = null
    private var player: MediaPlayer? = null
    private var recordFile: File? = null
    private val gson = Gson()

    @JavascriptInterface fun startRecording(): String {
        return try {
            recordFile = File(ctx.cacheDir, "voice_${System.currentTimeMillis()}.3gp")
            recorder = MediaRecorder().apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.THREE_GPP)
                setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB)
                setOutputFile(recordFile!!.absolutePath)
                prepare()
                start()
            }
            gson.toJson(mapOf("ok" to true, "file" to recordFile!!.absolutePath))
        } catch (e: Exception) {
            Log.e(TAG, "Start record: ${e.message}")
            gson.toJson(mapOf("ok" to false, "error" to (e.message ?: "unknown")))
        }
    }

    @JavascriptInterface fun stopRecording(): String {
        return try {
            recorder?.apply {
                try { stop() } catch (e: Exception) { Log.w(TAG, "Stop: ${e.message}") }
                release()
            }
            recorder = null
            val file = recordFile ?: return gson.toJson(mapOf("ok" to false, "error" to "no_file"))
            val bytes = file.readBytes()
            val b64 = Base64.encodeToString(bytes, Base64.NO_WRAP)
            val durationMs = try {
                val mp = MediaPlayer().apply { setDataSource(file.absolutePath); prepare() }
                val d = mp.duration
                mp.release(); d
            } catch (e: Exception) { 0 }
            file.delete()
            gson.toJson(mapOf("ok" to true, "data" to b64, "duration" to durationMs,
                "mime" to "audio/3gpp", "size" to bytes.size))
        } catch (e: Exception) {
            Log.e(TAG, "Stop record: ${e.message}")
            gson.toJson(mapOf("ok" to false, "error" to (e.message ?: "unknown")))
        }
    }

    @JavascriptInterface fun playRecording(b64: String): String {
        return try {
            stopPlayback()
            val bytes = Base64.decode(b64, Base64.NO_WRAP)
            val tmp = File(ctx.cacheDir, "voice_play.3gp")
            tmp.writeBytes(bytes)
            player = MediaPlayer().apply {
                setDataSource(tmp.absolutePath)
                setOnCompletionListener { stopPlayback(); tmp.delete() }
                setOnErrorListener { _, _, _ -> stopPlayback(); tmp.delete(); true }
                prepare()
                start()
            }
            gson.toJson(mapOf("ok" to true))
        } catch (e: Exception) {
            Log.e(TAG, "Play: ${e.message}")
            gson.toJson(mapOf("ok" to false, "error" to (e.message ?: "unknown")))
        }
    }

    @JavascriptInterface fun stopPlayback() {
        try {
            player?.apply { if (isPlaying) { stop(); release() } }
        } catch (e: Exception) {}
        player = null
    }

    @JavascriptInterface fun isRecording(): Boolean = recorder != null
}
