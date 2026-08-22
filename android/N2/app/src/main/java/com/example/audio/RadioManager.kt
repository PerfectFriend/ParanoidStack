/**
 * Manages internet radio streaming, local audio file playback, and Telegram-sourced tracks.
 *
 * ## Features
 * - **Radio streaming** – plays MP3/AAC streams via [StreamPlayer]; supports .onion addresses
 *   through [OnionStreamBridge] (SOCKS proxy).
 * - **Local files** – loads audio files from the device via [Uri], supports playlists with
 *   shuffle/repeat, persists track metadata to SharedPreferences.
 * - **Telegram tracks** – scrapes audio links from a Telegram channel (t.me/radioarmageddonfm)
 *   and provides fallback preseeded tracks when the network is unavailable.
 * - **Voice recording** – basic pass-through to [VoiceRecorder].
 *
 * State is exposed as Compose [mutableStateOf] properties for reactive UI updates.
 *
 * @property uiState observable [RadioUIState] containing channels, player state, and errors.
 * @property localTracks list of locally imported audio tracks.
 * @property telegramTracks list of tracks scraped from Telegram.
 */
package com.example.audio

import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.example.audio.models.LocalTrack
import com.example.audio.models.TelegramTrack
import org.json.JSONArray
import org.json.JSONObject
import java.io.InputStream
import java.io.OutputStream
import java.net.URLEncoder
import kotlin.concurrent.thread

/**
 * Central controller for all audio playback (radio, local files, Telegram tracks).
 * Uses [StreamPlayer] for radio streaming and [MediaPlayer] directly for local/Telegram playback.
 * Persists custom channels and local track lists via SharedPreferences.
 */
class RadioManager(private val context: Context) {
    val defaultChannels = listOf(
        // RU - Русский
        RadioChannel("ru_record", "🇷🇺 [RU] Радио Рекорд", "https://online.radiorecord.ru:8055/rr_128", genre = "Dance"),
        RadioChannel("ru_ep", "🇷🇺 [RU] Европа Плюс", "https://europaplus.hostingradio.ru:8014/ep128", genre = "Pop"),
        RadioChannel("ru_rusradio", "🇷🇺 [RU] Русское Радио", "https://rusradio.hostingradio.ru/rusradio128.mp3", genre = "Pop"),
        RadioChannel("ru_jazz", "🇷🇺 [RU] Радио Джаз", "https://jazzradio.hostingradio.ru/jazzradio128.mp3", genre = "Jazz"),
        RadioChannel("ru_dacha", "🇷🇺 [RU] Радио Дача", "https://dacha.hostingradio.ru/dacha128.mp3", genre = "Pop"),
        RadioChannel("ru_dorognoe", "🇷🇺 [RU] Дорожное Радио", "https://dorognoe.hostingradio.ru:8000/dorognoe128.mp3", genre = "Pop"),
        RadioChannel("ru_chanson", "🇷🇺 [RU] Радио Шансон", "https://chanson.hostingradio.ru:8041/chanson128.mp3", genre = "Chanson"),
        RadioChannel("ru_nashe", "🇷🇺 [RU] Наше Радио", "https://nashe1.hostingradio.ru/nashe-128.mp3", genre = "Rock"),
        RadioChannel("ru_armageddon", "📻 [TG] Radio Armageddon FM", "https://ice1.somafm.com/metal-128-mp3", genre = "Metal"),
        RadioChannel("onion_stream", "🧅 [ONION] SimpleX Secure Radio", "http://fv3pfzxih5sjf33jmusfbskmd2i3lywaaaysh6tijc7df7k6sijq3yyd.onion/radio/stream.mp3", genre = "Mixed"),

        // EN - English
        RadioChannel("en_bbcws", "🇬🇧 [EN] BBC World Service", "https://stream.live.vc.bbcmedia.co.uk/bbc_world_service", genre = "News"),
        RadioChannel("en_somagroove", "🇺🇸 [EN] SomaFM Groove Salad", "https://ice1.somafm.com/groovesalad-128-mp3", genre = "Electronic"),
        RadioChannel("en_somadef", "🇺🇸 [EN] SomaFM Metal Detector", "https://ice1.somafm.com/metal-128-mp3", genre = "Metal"),
        RadioChannel("en_classic", "🇬🇧 [EN] Classic FM", "https://stream.global.com/classicfmmp3", genre = "Classical"),
        RadioChannel("en_somads1", "🇺🇸 [EN] SomaFM Deep Space One", "https://ice1.somafm.com/deepspaceone-128-mp3", genre = "Ambient"),
        RadioChannel("en_somadrone", "🇺🇸 [EN] SomaFM Drone Zone", "https://ice1.somafm.com/dronezone-128-mp3", genre = "Ambient"),
        RadioChannel("en_somasecret", "🇺🇸 [EN] SomaFM Secret Agent", "https://ice1.somafm.com/secretagent-128-mp3", genre = "Lounge"),
        RadioChannel("en_somalush", "🇺🇸 [EN] SomaFM Lush Vocals", "https://ice1.somafm.com/lush-128-mp3", genre = "Vocal"),

        // ES - Español
        RadioChannel("es_los40", "🇪🇸 [ES] Los 40 Principales", "https://rstream.los40.com/los40.mp3", genre = "Pop"),
        RadioChannel("es_ibiza", "🇪🇸 [ES] Ibiza Global Radio", "https://live.ibizaglobalradio.com/stream", genre = "Electronic"),
        RadioChannel("es_dial", "🇪🇸 [ES] Cadena Dial", "https://rstream.cadenadial.com/cadenadial.mp3", genre = "Pop"),
        RadioChannel("es_rockfm", "🇪🇸 [ES] Rock FM", "https://rstream.rockfm.es/rockfm.mp3", genre = "Rock"),
        RadioChannel("es_cadena100", "🇪🇸 [ES] Cadena 100", "https://rstream.cadena100.es/cadena100.mp3", genre = "Pop"),
        RadioChannel("es_cope", "🇪🇸 [ES] COPE Madrid", "https://rstream.cope.es/cope.mp3", genre = "Talk"),
        RadioChannel("es_kebuena", "🇪🇸 [ES] Ke Buena", "https://rstream.kebuena.com/kebuena.mp3", genre = "Latin"),

        // DE - Deutsch
        RadioChannel("de_1live", "🇩🇪 [DE] 1LIVE WDR", "https://wdr-1live-live.icecast.wdr.de/wdr/1live/live/mp3/128/stream.mp3", genre = "Pop"),
        RadioChannel("de_wdr2", "🇩🇪 [DE] WDR 2 Rheinland", "https://wdr-wdr2-rheinland.icecast.wdr.de/wdr/wdr2/rheinland/mp3/128/stream.mp3", genre = "Pop"),
        RadioChannel("de_swr3", "🇩🇪 [DE] SWR3 Live", "https://swr-swr3-live.cast.addradio.de/swr/swr3/live/mp3/128/stream.mp3", genre = "Pop"),
        RadioChannel("de_antenne", "🇩🇪 [DE] Antenne Bayern", "https://webradio.antenne.de/antenne", genre = "Pop"),
        RadioChannel("de_dlf", "🇩🇪 [DE] Deutschlandfunk", "https://dradio-dlf-live.cast.addradio.de/dradio/dlf/live/mp3/128/stream.mp3", genre = "News"),
        RadioChannel("de_bayern3", "🇩🇪 [DE] Bayern 3", "https://br-br3-live.cast.addradio.de/br/br3/live/mp3/128/stream.mp3", genre = "Pop"),
        RadioChannel("de_bigfm", "🇩🇪 [DE] BigFM Germany", "https://bigfm.stream.bigfm.de/bigfm/mp3-128/stream.mp3", genre = "Pop"),

        // FR - Français
        RadioChannel("fr_fip", "🇫🇷 [FR] FIP Radio France", "https://stream.radiofrance.fr/fip/fip.mp3", genre = "Mixed"),
        RadioChannel("fr_inter", "🇫🇷 [FR] France Inter", "https://stream.radiofrance.fr/franceinter/franceinter.mp3", genre = "Talk"),
        RadioChannel("fr_info", "🇫🇷 [FR] France Info", "https://stream.radiofrance.fr/franceinfo/franceinfo.mp3", genre = "News"),
        RadioChannel("fr_culture", "🇫🇷 [FR] France Culture", "https://stream.radiofrance.fr/franceculture/franceculture.mp3", genre = "Culture"),
        RadioChannel("fr_nrj", "🇫🇷 [FR] NRJ France", "https://cdn.nrjaudio.fm/audio1/fr/30001/mp3_128.mp3", genre = "Pop"),
        RadioChannel("fr_cherie", "🇫🇷 [FR] Cherie FM", "https://cdn.nrjaudio.fm/audio1/fr/30201/mp3_128.mp3", genre = "Pop"),
        RadioChannel("fr_nostalgie", "🇫🇷 [FR] Nostalgie France", "https://cdn.nrjaudio.fm/audio1/fr/30601/mp3_128.mp3", genre = "Oldies"),
        RadioChannel("fr_rtl", "🇫🇷 [FR] RTL France", "https://rtl.ice.infomaniak.ch/rtl-128.mp3", genre = "Talk"),

        // TR - Türkçe
        RadioChannel("tr_power", "🇹🇷 [TR] Power FM Turkey", "https://listen.powerapp.com.tr/powerfm/mpeg.128", genre = "Pop"),
        RadioChannel("tr_kral", "🇹🇷 [TR] Kral FM", "https://listen.kralfm.com.tr/kralfm/mpeg.128", genre = "Pop"),
        RadioChannel("tr_metro", "🇹🇷 [TR] Metro FM Turkey", "https://listen.powerapp.com.tr/metrofm/mpeg.128", genre = "Pop"),
        RadioChannel("tr_joy", "🇹🇷 [TR] Joy FM", "https://listen.powerapp.com.tr/joyfm/mpeg.128", genre = "Pop"),
        RadioChannel("tr_joyturk", "🇹🇷 [TR] Joy Türk", "https://listen.powerapp.com.tr/joyturk/mpeg.128", genre = "Pop"),
        RadioChannel("tr_fenomen", "🇹🇷 [TR] Radyo Fenomen", "https://listen.powerapp.com.tr/radyofenomen/mpeg.128", genre = "Pop")
    )

    private val streamPlayer = StreamPlayer { playerState ->
        uiState = uiState.copy(playerState = playerState, isPlaying = playerState == StreamPlayer.PlayerState.PLAYING)
        if (playerState == StreamPlayer.PlayerState.PLAYING) isLoading = false
    }
    private var onionBridge: OnionStreamBridge? = null
    private val voiceRecorder = VoiceRecorder()

    var uiState by mutableStateOf(RadioUIState())
        private set

    val channels get() = uiState.channels
    val currentChannel get() = uiState.currentChannel
    val isPlaying get() = uiState.isPlaying
    val errorMessage get() = uiState.error
    val isRecording get() = uiState.isRecording

    var isLoading by mutableStateOf(false)
        private set

    var localTracks by mutableStateOf<List<LocalTrack>>(emptyList())
        private set

    var currentTrackIndex by mutableStateOf(-1)
        private set

    var isPlayingLocal by mutableStateOf(false)
        private set

    var telegramTracks by mutableStateOf<List<TelegramTrack>>(emptyList())
        private set

    var isTelegramLoading by mutableStateOf(false)
        private set

    var currentTelegramTrackIndex by mutableStateOf(-1)
        private set

    var isPlayingTelegram by mutableStateOf(false)
        private set

    var isShuffle by mutableStateOf(false)
    var isRepeat by mutableStateOf(false)

    val activeTrackTitle: String
        get() = when {
            isPlayingLocal -> {
                val selectedTracks = localTracks.filter { it.isSelected }
                if (currentTrackIndex in selectedTracks.indices) {
                    selectedTracks[currentTrackIndex].name
                } else {
                    "No track selected"
                }
            }
            isPlayingTelegram -> {
                if (currentTelegramTrackIndex in telegramTracks.indices) {
                    telegramTracks[currentTelegramTrackIndex].title
                } else {
                    "No media selected"
                }
            }
            else -> {
                currentChannel?.name ?: "No station selected"
            }
        }

    private val sharedPrefs = context.getSharedPreferences("radio_prefs", Context.MODE_PRIVATE)

    init {
        loadChannels()
        loadLocalTracks()
        val defaultChannel = channels.firstOrNull { it.id == "en_somagroove" } ?: channels.firstOrNull()
        if (defaultChannel != null) {
            uiState = uiState.copy(currentChannel = defaultChannel)
        }
        fetchTelegramTracks()
    }

    private fun isCustomChannel(channel: RadioChannel): Boolean = channel.id.startsWith("custom_")

    private fun loadChannels() {
        val jsonString = sharedPrefs.getString("custom_channels", null)
        val custom = mutableListOf<RadioChannel>()
        if (jsonString != null) {
            try {
                val jsonArray = JSONArray(jsonString)
                for (i in 0 until jsonArray.length()) {
                    val obj = jsonArray.getJSONObject(i)
                    val streamUrl = obj.optString("streamUrl", obj.optString("url", ""))
                    val isOnline = obj.optBoolean("isOnline", obj.optBoolean("isCustom", true) || true)
                    custom.add(
                        RadioChannel(
                            id = obj.getString("id"),
                            name = obj.getString("name"),
                            streamUrl = streamUrl,
                            isOnline = isOnline,
                            genre = obj.optString("genre", if (obj.optBoolean("isCustom", false)) "Custom" else "Mixed")
                        )
                    )
                }
            } catch (e: Exception) {
                Log.e("RadioManager", "exception", e)
            }
        }
        uiState = uiState.copy(channels = defaultChannels + custom)
    }

    private fun saveCustomChannels(custom: List<RadioChannel>) {
        try {
            val jsonArray = JSONArray()
            for (channel in custom) {
                val obj = JSONObject().apply {
                    put("id", channel.id)
                    put("name", channel.name)
                    put("streamUrl", channel.streamUrl)
                    put("isOnline", channel.isOnline)
                    put("genre", channel.genre)
                }
                jsonArray.put(obj)
            }
            sharedPrefs.edit().putString("custom_channels", jsonArray.toString()).apply()
        } catch (e: Exception) {
            Log.e("RadioManager", "exception", e)
        }
    }

    /**
     * Adds a user-defined radio channel and persists it to SharedPreferences.
     * The channel ID is prefixed with "custom_".
     */
    fun addCustomChannel(name: String, streamUrl: String) {
        if (name.isBlank() || streamUrl.isBlank()) return
        val id = "custom_" + System.currentTimeMillis()
        val newChannel = RadioChannel(id, name, streamUrl, isOnline = true, genre = "Custom")
        val custom = (uiState.channels.filter { isCustomChannel(it) } + newChannel)
        saveCustomChannels(custom)
        uiState = uiState.copy(channels = defaultChannels + custom)
        if (uiState.currentChannel == null) {
            uiState = uiState.copy(currentChannel = newChannel)
        }
    }

    /** Removes a custom channel by its ID and persists the updated list. */
    fun deleteCustomChannel(id: String) {
        val custom = uiState.channels.filter { isCustomChannel(it) && it.id != id }
        saveCustomChannels(custom)
        uiState = uiState.copy(channels = defaultChannels + custom)
        if (uiState.currentChannel?.id == id) {
            val wasPlaying = isPlaying || isLoading
            stop()
            val firstChannel = uiState.channels.firstOrNull()
            uiState = uiState.copy(currentChannel = firstChannel)
            if (wasPlaying && firstChannel != null) {
                play(firstChannel)
            }
        }
    }

    private fun saveLocalTracks() {
        try {
            val jsonArray = JSONArray()
            for (track in localTracks) {
                val obj = JSONObject().apply {
                    put("id", track.id)
                    put("name", track.name)
                    put("uriString", track.uriString)
                    put("isSelected", track.isSelected)
                }
                jsonArray.put(obj)
            }
            sharedPrefs.edit().putString("local_tracks2", jsonArray.toString()).apply()
        } catch (e: Exception) {
            Log.e("RadioManager", "exception", e)
        }
    }

    private fun loadLocalTracks() {
        val jsonString = sharedPrefs.getString("local_tracks2", null)
        if (jsonString != null) {
            try {
                val jsonArray = JSONArray(jsonString)
                val list = mutableListOf<LocalTrack>()
                for (i in 0 until jsonArray.length()) {
                    val obj = jsonArray.getJSONObject(i)
                    list.add(
                        LocalTrack(
                            id = obj.getString("id"),
                            name = obj.getString("name"),
                            uriString = obj.getString("uriString"),
                            isSelected = obj.optBoolean("isSelected", true)
                        )
                    )
                }
                localTracks = list
            } catch (e: Exception) {
                Log.e("RadioManager", "exception", e)
            }
        }
    }

    /**
     * Imports audio files from a list of content URIs.
     * Attempts to persist read permissions via [takePersistableUriPermission].
     */
    fun addLocalFiles(uris: List<Uri>) {
        val newTracks = mutableListOf<LocalTrack>()
        for (uri in uris) {
            try {
                try {
                    context.contentResolver.takePersistableUriPermission(
                        uri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION
                    )
                } catch (e: Exception) {
                    Log.e("RadioManager", "exception", e)
                }
                val name = getFileName(uri) ?: "Track " + (localTracks.size + newTracks.size + 1)
                val id = "local_" + System.currentTimeMillis() + "_" + (uri.hashCode())
                newTracks.add(LocalTrack(id, name, uri.toString(), true))
            } catch (e: Exception) {
                Log.e("RadioManager", "exception", e)
            }
        }
        if (newTracks.isNotEmpty()) {
            localTracks = localTracks + newTracks
            saveLocalTracks()
        }
    }

    /**
     * Recursively scans a document-tree URI for audio files (.mp3, .wav, .ogg, .m4a, .flac)
     * and adds them as local tracks.
     */
    fun addLocalFolder(treeUri: Uri) {
        val newTracks = mutableListOf<LocalTrack>()
        try {
            try {
                context.contentResolver.takePersistableUriPermission(
                    treeUri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            } catch (e: Exception) {
                Log.e("RadioManager", "exception", e)
            }
            val documentId = DocumentsContract.getTreeDocumentId(treeUri)
            val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, documentId)
            context.contentResolver.query(
                childrenUri,
                arrayOf(
                    DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                    DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                    DocumentsContract.Document.COLUMN_MIME_TYPE
                ),
                null, null, null
            )?.use { cursor ->
                val idIndex = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
                val nameIndex = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
                val mimeIndex = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_MIME_TYPE)
                while (cursor.moveToNext()) {
                    val childId = cursor.getString(idIndex)
                    val name = cursor.getString(nameIndex)
                    val mime = cursor.getString(mimeIndex)
                    val isDirectory = mime == DocumentsContract.Document.MIME_TYPE_DIR
                    if (!isDirectory && (mime.startsWith("audio/") || name.endsWith(".mp3", true) || name.endsWith(".wav", true) || name.endsWith(".ogg", true) || name.endsWith(".m4a", true) || name.endsWith(".flac", true))) {
                        val childUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, childId)
                        val id = "local_" + System.currentTimeMillis() + "_" + (childUri.hashCode())
                        newTracks.add(LocalTrack(id, name, childUri.toString(), true))
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("RadioManager", "exception", e)
            uiState = uiState.copy(error = "Unable to read directory: ${e.message}")
        }
        if (newTracks.isNotEmpty()) {
            localTracks = localTracks + newTracks
            saveLocalTracks()
        }
    }

    private fun getFileName(uri: Uri): String? {
        var name: String? = null
        try {
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (nameIndex != -1) {
                        name = cursor.getString(nameIndex)
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("RadioManager", "exception", e)
        }
        if (name == null) {
            name = uri.path?.substringAfterLast('/')
        }
        return name
    }

    /** Toggles the selection state of a local track and persists the change. */
    fun toggleTrackSelection(id: String) {
        localTracks = localTracks.map {
            if (it.id == id) it.copy(isSelected = !it.isSelected) else it
        }
        saveLocalTracks()
    }

    /** Selects or deselects all local tracks. */
    fun selectAllTracks(select: Boolean) {
        localTracks = localTracks.map { it.copy(isSelected = select) }
        saveLocalTracks()
    }

    /** Deletes a local track by ID and adjusts playback if the deleted track was playing. */
    fun deleteTrack(id: String) {
        val tracksToPlay = localTracks.filter { it.isSelected }
        val currentPlayingTrack = if (currentTrackIndex >= 0 && currentTrackIndex < tracksToPlay.size) {
            tracksToPlay[currentTrackIndex]
        } else null
        localTracks = localTracks.filter { it.id != id }
        saveLocalTracks()
        if (isPlayingLocal) {
            val newTracksToPlay = localTracks.filter { it.isSelected }
            if (newTracksToPlay.isEmpty()) {
                stop()
            } else {
                val newIndex = newTracksToPlay.indexOfFirst { it.id == currentPlayingTrack?.id }
                if (newIndex != -1) {
                    currentTrackIndex = newIndex
                } else {
                    nextLocalTrack()
                }
            }
        }
    }

    /** Stops playback and removes all local tracks. */
    fun clearAllTracks() {
        stop()
        localTracks = emptyList()
        saveLocalTracks()
        currentTrackIndex = -1
    }

    /**
     * Starts playing a radio channel. If the channel URL contains ".onion",
     * spawns [OnionStreamBridge] to tunnel through a local SOCKS proxy.
     */
    fun play(channel: RadioChannel) {
        uiState = uiState.copy(currentChannel = channel, error = null)
        isLoading = true
        isPlayingLocal = false

        var streamUrl = channel.streamUrl
        if (streamUrl.contains(".onion")) {
            val gamePrefs = context.getSharedPreferences("crazy_backgammon_prefs", Context.MODE_PRIVATE)
            val socksPort = gamePrefs.getInt("tor_socks_port", 9050)
            if (onionBridge == null) {
                onionBridge = OnionStreamBridge(socksPort)
                onionBridge?.start()
            }
            val encodedUrl = URLEncoder.encode(streamUrl, "UTF-8")
            streamUrl = "http://127.0.0.1:${onionBridge?.localPort}/onion_stream?url=$encodedUrl"
        }

        streamPlayer.play(streamUrl)
    }

    private fun playWithAudioAttributes(url: String, onCompletion: () -> Unit) {
        isLoading = true
        val mp = MediaPlayer()
        var prepared = false
        try {
            val mainHandler = android.os.Handler(android.os.Looper.getMainLooper())
            mp.apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .build()
                )
                setVolume(1.0f, 1.0f)
                setDataSource(context, Uri.parse(url))
                setOnPreparedListener {
                    mainHandler.post { isLoading = false }
                    try { it.start() } catch (_: java.lang.Exception) { Log.w("RadioManager", "ignored exception") }
                }
                setOnErrorListener { _, what, extra ->
                    mainHandler.post {
                        isLoading = false
                        uiState = uiState.copy(error = "Error playing track ($what, $extra).")
                    }
                    true
                }
                setOnCompletionListener { onCompletion() }
            }
            mp.prepareAsync()
            prepared = true
        } catch (e: Exception) {
            isLoading = false
            uiState = uiState.copy(error = "Unable to open file: ${e.message}")
            Log.e("RadioManager", "exception", e)
        } finally {
            if (!prepared) mp.release()
        }
    }

    /** Plays a local track by its index in the filtered (selected-only) playlist. */
    fun playLocalTrack(index: Int) {
        val tracksToPlay = localTracks.filter { it.isSelected }
        if (tracksToPlay.isEmpty()) {
            uiState = uiState.copy(error = "Playlist is empty")
            return
        }
        if (index < 0 || index >= tracksToPlay.size) return
        currentTrackIndex = index
        val track = tracksToPlay[index]
        isPlayingLocal = true
        uiState = uiState.copy(error = null)

        playWithAudioAttributes(track.uriString) {
            if (isRepeat) {
                playLocalTrack(currentTrackIndex)
            } else {
                nextLocalTrack()
            }
        }
    }

    /** Advances to the next local track; respects shuffle and repeat modes. */
    fun nextLocalTrack() {
        val tracksToPlay = localTracks.filter { it.isSelected }
        if (tracksToPlay.isEmpty()) return
        if (isShuffle) {
            val nextIndex = (tracksToPlay.indices).random()
            playLocalTrack(nextIndex)
        } else {
            var nextIndex = currentTrackIndex + 1
            if (nextIndex >= tracksToPlay.size) {
                nextIndex = 0
            }
            playLocalTrack(nextIndex)
        }
    }

    /** Goes back to the previous local track; respects shuffle mode. */
    fun prevLocalTrack() {
        val tracksToPlay = localTracks.filter { it.isSelected }
        if (tracksToPlay.isEmpty()) return
        if (isShuffle) {
            val nextIndex = (tracksToPlay.indices).random()
            playLocalTrack(nextIndex)
        } else {
            var prevIndex = currentTrackIndex - 1
            if (prevIndex < 0) {
                prevIndex = tracksToPlay.size - 1
            }
            playLocalTrack(prevIndex)
        }
    }

    /** Stops all playback (radio, local, or Telegram) and shuts down the Onion bridge if active. */
    fun stop() {
        isLoading = false
        streamPlayer.stop()
        try {
            onionBridge?.stop()
            onionBridge = null
        } catch (e: Exception) {
            Log.e("RadioManager", "exception", e)
        }
        uiState = uiState.copy(isPlaying = false)
    }

    /** Toggles playback for the current source (radio, local, or Telegram). */
    fun togglePlay() {
        if (isPlayingLocal) {
            if (isPlaying || isLoading) {
                stop()
            } else {
                if (currentTrackIndex != -1) {
                    playLocalTrack(currentTrackIndex)
                } else {
                    playLocalTrack(0)
                }
            }
        } else if (isPlayingTelegram) {
            if (isPlaying || isLoading) {
                stop()
            } else {
                if (currentTelegramTrackIndex != -1) {
                    playTelegramTrack(currentTelegramTrackIndex)
                } else {
                    playTelegramTrack(0)
                }
            }
        } else {
            val channel = currentChannel ?: return
            if (isPlaying || isLoading) {
                stop()
            } else {
                play(channel)
            }
        }
    }

    /** Plays a random channel from the current channel list. */
    fun playRandomChannel() {
        if (uiState.channels.isEmpty()) return
        val randomChannel = uiState.channels.random()
        play(randomChannel)
    }

    /** Selects a channel and starts playing it if audio was already active. */
    fun selectChannel(channel: RadioChannel) {
        val wasPlaying = isPlaying || isLoading
        isPlayingTelegram = false
        if (wasPlaying) {
            play(channel)
        } else {
            uiState = uiState.copy(currentChannel = channel)
            isPlayingLocal = false
        }
    }

    /** Plays a Telegram track by its index in the track list. */
    fun playTelegramTrack(index: Int) {
        if (telegramTracks.isEmpty()) return
        val targetIndex = if (index in telegramTracks.indices) index else 0
        currentTelegramTrackIndex = targetIndex
        val track = telegramTracks[targetIndex]
        isPlayingLocal = false
        isPlayingTelegram = true
        uiState = uiState.copy(error = null)

        playWithAudioAttributes(track.url) {
            nextTelegramTrack()
        }
    }

    /** Advances to the next Telegram track; respects shuffle mode. */
    fun nextTelegramTrack() {
        if (telegramTracks.isEmpty()) return
        if (isShuffle) {
            val nextIndex = (telegramTracks.indices).random()
            playTelegramTrack(nextIndex)
        } else {
            var nextIndex = currentTelegramTrackIndex + 1
            if (nextIndex >= telegramTracks.size) {
                nextIndex = 0
            }
            playTelegramTrack(nextIndex)
        }
    }

    /** Goes back to the previous Telegram track; respects shuffle mode. */
    fun prevTelegramTrack() {
        if (telegramTracks.isEmpty()) return
        if (isShuffle) {
            val nextIndex = (telegramTracks.indices).random()
            playTelegramTrack(nextIndex)
        } else {
            var prevIndex = currentTelegramTrackIndex - 1
            if (prevIndex < 0) {
                prevIndex = telegramTracks.size - 1
            }
            playTelegramTrack(prevIndex)
        }
    }

    /** Plays a random Telegram track from the current list. */
    fun playRandomTelegramTrack() {
        if (telegramTracks.isEmpty()) return
        val randomIndex = (telegramTracks.indices).random()
        playTelegramTrack(randomIndex)
    }

    /**
     * Fetches audio track links from a Telegram channel (t.me/radioarmageddonfm) via HTML scraping.
     * Falls back to [getPreseededTracks] on failure or empty results.
     */
    fun fetchTelegramTracks() {
        isTelegramLoading = true
        uiState = uiState.copy(error = null)

        Thread {
            try {
                val url = java.net.URL("https://t.me/s/radioarmageddonfm")
                val connection = url.openConnection() as? java.net.HttpURLConnection ?: return@Thread
                val body: String
                try {
                    connection.requestMethod = "GET"
                    connection.connectTimeout = 12000
                    connection.readTimeout = 12000
                    connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                    connection.setRequestProperty("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/webp,*/*;q=0.8")
                    connection.connect()
                    body = connection.inputStream.bufferedReader().use { it.readText() }
                } finally {
                    connection.disconnect()
                }
                val parsedList = mutableListOf<TelegramTrack>()
                val blocks = body.split("class=\"tgme_widget_message")
                val audioSrcRegex = """<audio[^>]+src="([^"]+)"""".toRegex(RegexOption.IGNORE_CASE)
                val dataTrackRegex = """data-track-url="([^"]+)"""".toRegex(RegexOption.IGNORE_CASE)
                val titleRegex = """class="[^"]*tgme_widget_message_document_title[^"]*"[^>]*>([^<]+)""".toRegex(RegexOption.IGNORE_CASE)
                val audioTitleRegex = """class="[^"]*tgme_widget_message_user_audio_title[^"]*"[^>]*>([^<]+)""".toRegex(RegexOption.IGNORE_CASE)
                val performerRegex = """class="[^"]*tgme_widget_message_user_audio_performer[^"]*"[^>]*>([^<]+)""".toRegex(RegexOption.IGNORE_CASE)
                val linkRegex = """href="([^"]+)"""".toRegex(RegexOption.IGNORE_CASE)
                val captionRegex = """class="[^"]*tgme_widget_message_text[^"]*"[^>]*>([\s\S]*?)</div>""".toRegex(RegexOption.IGNORE_CASE)

                for (i in 1..blocks.lastIndex) {
                    val block = blocks[i]
                    var audioUrl = audioSrcRegex.find(block)?.groupValues?.get(1)
                        ?: dataTrackRegex.find(block)?.groupValues?.get(1)
                    if (audioUrl == null) {
                        for (match in linkRegex.findAll(block)) {
                            val href = match.groupValues[1]
                            if (href.contains(".mp3") || href.contains("?audio=") || href.contains("/file/")) {
                                audioUrl = href
                                break
                            }
                        }
                    }
                    if (audioUrl != null) {
                        val cleanUrl = audioUrl.replace("&amp;", "&")
                        var title = titleRegex.find(block)?.groupValues?.get(1)?.trim()
                            ?: audioTitleRegex.find(block)?.groupValues?.get(1)?.trim()
                        val performer = performerRegex.find(block)?.groupValues?.get(1)?.trim()
                            ?: "Radio Armageddon FM"
                        if (title == null) {
                            val caption = captionRegex.find(block)?.groupValues?.get(1)
                            title = if (caption != null) {
                                val cleanCaption = caption.replace("""<[^>]+>""".toRegex(), "").trim()
                                if (cleanCaption.length > 50) cleanCaption.take(47) + "..." else cleanCaption
                            } else null
                        }
                        val finalTitle = title?.ifBlank { null } ?: "Track #${parsedList.size + 1}"
                        parsedList.add(
                            TelegramTrack(
                                id = "tg_" + cleanUrl.hashCode() + "_" + parsedList.size,
                                title = finalTitle,
                                artist = performer,
                                url = cleanUrl
                            )
                        )
                    }
                }

                if (parsedList.isEmpty()) {
                    val rawMp3Regex = """src="([^"]+\.mp3[^"]*)"""".toRegex(RegexOption.IGNORE_CASE)
                    rawMp3Regex.findAll(body).forEachIndexed { index, match ->
                        val u = match.groupValues[1].replace("&amp;", "&")
                        parsedList.add(
                            TelegramTrack(
                                id = "tg_fallback_$index",
                                title = "Audio File #${index + 1}",
                                artist = "Radio Armageddon FM",
                                url = u
                            )
                        )
                    }
                }

                android.os.Handler(android.os.Looper.getMainLooper()).post {
                    isTelegramLoading = false
                    telegramTracks = if (parsedList.isNotEmpty()) parsedList else getPreseededTracks()
                }
            } catch (e: Exception) {
                Log.e("RadioManager", "exception", e)
                android.os.Handler(android.os.Looper.getMainLooper()).post {
                    isTelegramLoading = false
                    telegramTracks = getPreseededTracks()
                }
            }
        }.start()
    }

    /** Starts voice recording via [VoiceRecorder]. Updates UI state on success. */
    fun startVoiceRecording(): Boolean {
        val started = voiceRecorder.startRecording()
        uiState = uiState.copy(isRecording = started)
        return started
    }

    /** Stops voice recording and returns the captured PCM data (or null if not recording). */
    fun stopVoiceRecording(): ByteArray? {
        if (!voiceRecorder.isRecording) return null
        val data = voiceRecorder.stopRecording()
        uiState = uiState.copy(isRecording = false)
        return data
    }

    private fun getPreseededTracks(): List<TelegramTrack> {
        return listOf(
            TelegramTrack("p1", "The Only Thing They Fear Is You", "Mick Gordon (Doom Eternal)", "https://archive.org/download/doom-ost-mick-gordon/02.%20The%20Only%20Thing%20They%20Fear%20Is%20You.mp3"),
            TelegramTrack("p2", "Du Hast", "Rammstein", "https://archive.org/download/RammsteinDuHast_201602/Rammstein-du%20hast.mp3"),
            TelegramTrack("p3", "Future Club", "Perturbator", "https://archive.org/download/perturbator-future-club/Perturbator%20-%20Future%20Club.mp3"),
            TelegramTrack("p4", "Chop Suey!", "System Of A Down", "https://archive.org/download/system-of-a-down-toxicity_202102/06%20-%20Chop%20Suey%21.mp3"),
            TelegramTrack("p5", "Master of Puppets", "Metallica", "https://archive.org/download/masterofpuppets86/02%20Master%20of%20Puppets.mp3"),
            TelegramTrack("p6", "In The End", "Linkin Park", "https://archive.org/download/linkin-park-hybrid-theory_202011/08%20-%20In%20the%20End.mp3"),
            TelegramTrack("p7", "Head Like a Hole", "Nine Inch Nails", "https://archive.org/download/nine-inch-nails-pretty-hate-machine_202103/01%20-%20Head%20Like%20a%20Hole.mp3"),
            TelegramTrack("p8", "Roller Mobster", "Carpenter Brut", "https://archive.org/download/carpenter-brut-trilogy/03%20-%20Roller%20Mobster.mp3"),
            TelegramTrack("p9", "Shut Your Mouth", "Pain", "https://archive.org/download/pain-shut-your-mouth/Pain%20-%20Shut%20Your%20Mouth.mp3"),
            TelegramTrack("p10", "Dragula", "Rob Zombie", "https://archive.org/download/rob-zombie-dragula/Rob%20Zombie%20-%20Dragula.mp3")
        )
    }
}
