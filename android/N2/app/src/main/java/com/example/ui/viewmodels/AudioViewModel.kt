package com.example.ui.viewmodels

import android.content.Context
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.api.GeminiJokeService
import com.example.audio.DiceSoundPlayer
import com.example.audio.RadioManager
import com.example.ui.UserTier
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.UUID

enum class MatchAudioMode {
    WALKIE_TALKIE,
    RADIO,
    SILENCE
}

class AudioViewModel(private val appContext: Context) : ViewModel() {

    val radioManager = RadioManager(appContext)
    private val audioPlayer = com.example.audio.AudioPlayer()
    private val voiceRecorder = com.example.audio.VoiceRecorder()
    private val soundPlayer = DiceSoundPlayer()
    private val jokeService = GeminiJokeService()
    private var tts: android.speech.tts.TextToSpeech? = null

    init {
        try {
            tts = android.speech.tts.TextToSpeech(appContext) { status ->
                if (status == android.speech.tts.TextToSpeech.SUCCESS) {
                    tts?.language = java.util.Locale.US
                }
            }
        } catch (e: Exception) {
            Log.e("AudioViewModel", "exception", e)
        }
    }

    var matchAudioMode by mutableStateOf(MatchAudioMode.WALKIE_TALKIE)
    var isWalkieTalkieMuted by mutableStateOf(false)

    enum class TalkieState {
        OFF,
        CONNECTING,
        READY
    }

    var talkieState by mutableStateOf(TalkieState.OFF)
    var talkieStepText by mutableStateOf("")
    var talkieSelectedContactName by mutableStateOf("")
    var isVoiceSavingEnabled by mutableStateOf(false)
    var showTalkieErrorDialog by mutableStateOf(false)
    var showContactListDialog by mutableStateOf(false)
    var showDisconnectOptionsDialog by mutableStateOf(false)

    data class SimplexVoiceMessage(
        val id: String = UUID.randomUUID().toString(),
        val sender: String,
        val durationSec: Int,
        val transcript: String,
        val timestamp: Long = System.currentTimeMillis(),
        var isPlaying: Boolean = false,
        var playProgress: Float = 0f,
        var isPlayed: Boolean = false,
        var pcmData: ByteArray? = null
    )

    val voiceMessages = mutableStateListOf<SimplexVoiceMessage>()
    var isRecordingVoice by mutableStateOf(false)
    var recordingDurationSec by mutableStateOf(0)
    var isAutoPlayVoiceEnabled by mutableStateOf(true)

    val isTalkieConnected: Boolean
        get() = talkieState == TalkieState.READY

    val talkieLogs: String
        get() = talkieStepText

    val talkieStatus: TalkieState
        get() = talkieState

    val voiceRecordingDuration: Int
        get() = recordingDurationSec

    val isPlayingVoice: Boolean
        get() = voiceMessages.any { it.isPlaying }

    // External state — sync from GameViewModel before calling audio functions
    var selectedLanguage: String = "EN"
    var currentUserTier: UserTier = UserTier.FREE
    var simplexUserHandle: String = "User"
    var customSmpServer: String = ""
    var customXftpServer: String = ""
    var isOnlinePlayActive: Boolean = false
    var onlineOpponentName: String = ""
    var addSystemMessageToAllRooms: (String) -> Unit = {}
    var botSpeechBubbleCallback: (String) -> Unit = {}
    var isGeminiLoadingJokeCallback: (Boolean) -> Unit = {}

    fun updateTtsLanguage(languageCode: String) {
        try {
            tts?.language = if (languageCode == "RU") java.util.Locale.forLanguageTag("ru") else java.util.Locale.US
        } catch (e: Exception) {
            Log.e("AudioViewModel", "exception", e)
        }
    }

    fun updateMatchAudioMode(mode: MatchAudioMode) {
        matchAudioMode = mode
        when (mode) {
            MatchAudioMode.WALKIE_TALKIE -> {
                radioManager.stop()
                isWalkieTalkieMuted = false
            }
            MatchAudioMode.RADIO -> {
                isWalkieTalkieMuted = true
                val channel = radioManager.currentChannel ?: radioManager.channels.firstOrNull()
                if (channel != null && !radioManager.isPlaying) {
                    radioManager.play(channel)
                }
            }
            MatchAudioMode.SILENCE -> {
                radioManager.stop()
                isWalkieTalkieMuted = true
            }
        }
    }

    fun startTalkieConnection() {
        if (talkieState != TalkieState.OFF) return
        talkieState = TalkieState.CONNECTING
        talkieStepText = if (selectedLanguage == "RU") "Инициализация VPN..." else "Initializing VPN..."
        viewModelScope.launch {
            delay(1000)
            talkieStepText = if (selectedLanguage == "RU") "Маршрутизация через TOR (SOCKS5)..." else "Routing via TOR (SOCKS5)..."
            delay(1000)
            talkieStepText = if (selectedLanguage == "RU") "SimpleX ID: Регистрация..." else "SimpleX ID: Registering..."
            delay(1000)
            talkieStepText = if (selectedLanguage == "RU") "Тест SMP и XFTP серверов..." else "Testing SMP & XFTP servers..."
            delay(1000)
            talkieState = TalkieState.READY
            talkieStepText = ""
        }
    }

    fun triggerTalkieError1488() {
        try {
            val tg = android.media.ToneGenerator(android.media.AudioManager.STREAM_NOTIFICATION, 80)
            tg.startTone(android.media.ToneGenerator.TONE_PROP_BEEP, 150)
        } catch (e: Exception) {
            Log.e("AudioViewModel", "exception", e)
        }
        showTalkieErrorDialog = true
        talkieState = TalkieState.OFF
        isRecordingVoice = false
    }

    fun playVoiceMessage(msg: SimplexVoiceMessage) {
        if (msg.isPlayed && isVoiceSavingEnabled) return
        if (isWalkieTalkieMuted) return

        voiceMessages.forEachIndexed { idx, m ->
            if (m.isPlaying) {
                voiceMessages[idx] = m.copy(isPlaying = false, playProgress = 0f)
            }
        }

        val index = voiceMessages.indexOfFirst { it.id == msg.id }
        if (index == -1) return

        voiceMessages[index] = msg.copy(isPlaying = true)

        val pcm = msg.pcmData
        if (pcm != null) {
            audioPlayer.play(pcm)
            viewModelScope.launch {
                audioPlayer.position.collect { progress ->
                    val idx = voiceMessages.indexOfFirst { it.id == msg.id }
                    if (idx != -1 && voiceMessages[idx].isPlaying) {
                        voiceMessages[idx] = voiceMessages[idx].copy(playProgress = progress)
                    }
                }
            }
            viewModelScope.launch {
                audioPlayer.isPlaying.collect { playing ->
                    if (!playing) {
                        val idx = voiceMessages.indexOfFirst { it.id == msg.id }
                        if (idx != -1) {
                            if (!isVoiceSavingEnabled) {
                                voiceMessages.removeAt(idx)
                            } else {
                                voiceMessages[idx] = voiceMessages[idx].copy(isPlaying = false, isPlayed = true, playProgress = 1.0f)
                            }
                        }
                    }
                }
            }
        } else {
            tts?.language = if (selectedLanguage == "RU") java.util.Locale.forLanguageTag("ru") else java.util.Locale.US
            tts?.speak(msg.transcript, android.speech.tts.TextToSpeech.QUEUE_FLUSH, null, msg.id)

            viewModelScope.launch {
                val totalMs = msg.durationSec * 1000L
                val startTime = System.currentTimeMillis()
                while (System.currentTimeMillis() - startTime < totalMs) {
                    val elapsed = System.currentTimeMillis() - startTime
                    val progress = (elapsed.toFloat() / totalMs).coerceIn(0f, 1f)
                    val idx = voiceMessages.indexOfFirst { it.id == msg.id }
                    if (idx != -1 && voiceMessages[idx].isPlaying) {
                        voiceMessages[idx] = voiceMessages[idx].copy(playProgress = progress)
                    } else {
                        break
                    }
                    delay(100)
                }
                val idx = voiceMessages.indexOfFirst { it.id == msg.id }
                if (idx != -1 && voiceMessages[idx].isPlaying) {
                    if (!isVoiceSavingEnabled) {
                        voiceMessages.removeAt(idx)
                    } else {
                        voiceMessages[idx] = voiceMessages[idx].copy(isPlaying = false, isPlayed = true, playProgress = 1.0f)
                    }
                }
            }
        }
    }

    fun pauseVoiceMessage(msg: SimplexVoiceMessage) {
        val index = voiceMessages.indexOfFirst { it.id == msg.id }
        if (index != -1) {
            voiceMessages[index] = voiceMessages[index].copy(isPlaying = false)
            audioPlayer.stop()
            try {
                tts?.stop()
            } catch (e: Exception) {
                Log.e("AudioViewModel", "exception", e)
            }
        }
    }

    fun startVoiceRecording() {
        if (isRecordingVoice) return

        val limit = when (currentUserTier) {
            UserTier.FREE -> 1
            UserTier.PREMIUM -> 5
            UserTier.ROYAL -> Int.MAX_VALUE
        }
        if (voiceMessages.size >= limit) {
            val errMsg = if (selectedLanguage == "RU") {
                "⚠️ [Сбой] Очередь рации полна! Предел пакетов Вашего аккаунта [${currentUserTier.label("RU")}]: $limit"
            } else {
                "⚠️ [Fail] Walkie-Talkie Queue Full! Account limit [${currentUserTier.label("EN")}]: $limit"
            }
            addSystemMessageToAllRooms(errMsg)
            return
        }

        isRecordingVoice = true
        recordingDurationSec = 0
        voiceRecorder.startRecording()

        viewModelScope.launch {
            while (isRecordingVoice) {
                delay(1000)
                recordingDurationSec++

                val maxDuration = when (currentUserTier) {
                    UserTier.FREE -> 6
                    UserTier.PREMIUM -> 20
                    UserTier.ROYAL -> 120
                }

                if (recordingDurationSec >= maxDuration) {
                    stopAndSendVoiceRecording()
                    break
                }
            }
        }
    }

    fun stopAndSendVoiceRecording() {
        if (!isRecordingVoice) return
        isRecordingVoice = false

        val pcmData = voiceRecorder.stopRecording()
        val duration = if (recordingDurationSec > 0) recordingDurationSec else 3

        val userPhrasesRu = listOf(
            "Глянь на мои нервы из стали! Кубики ложатся как надо!",
            "Слушай, SimpleX работает отлично! Твой ход, не задерживай эфир!",
            "Хороший бросок! Зарик Бот, тебе пора на вибро-калибровку!",
            "Проверяю защищенную рацию. Все пакеты доставлены!",
            "Моя тактика безупречна. Советую тебе сдаться!"
        )
        val userPhrasesEn = listOf(
            "Check out my steel nerves! The dice are perfect!",
            "SimpleX node is super stable! Make your move quickly!",
            "Great roll! Zarik Bot, time to calibrate your generator!",
            "Testing E2EE walkie-talkie. All voice packets delivered!",
            "My tactics are flawless. I suggest you resign!"
        )
        val phrase = if (selectedLanguage == "RU") userPhrasesRu.random() else userPhrasesEn.random()

        val myMsg = SimplexVoiceMessage(
            sender = simplexUserHandle,
            durationSec = duration,
            transcript = phrase,
            isPlayed = true,
            pcmData = pcmData.takeIf { it.isNotEmpty() }
        )
        voiceMessages.add(myMsg)

        val encryptMsg = if (selectedLanguage == "RU") {
            "🎙️ [Передача] Отправка голосового пакета (${duration}с) на сервер SMP/XFTP: $customSmpServer / $customXftpServer"
        } else {
            "🎙️ [Transmit] Outgoing audio packet (${duration}s) sent to relays: $customSmpServer / $customXftpServer"
        }
        addSystemMessageToAllRooms(encryptMsg)

        viewModelScope.launch {
            delay(2500)
            if (!isVoiceSavingEnabled) {
                voiceMessages.remove(myMsg)
            }
            queueIncomingVoiceReply()
        }
    }

    fun queueIncomingVoiceReply() {
        viewModelScope.launch {
            delay(1500)

            val limit = when (currentUserTier) {
                UserTier.FREE -> 1
                UserTier.PREMIUM -> 5
                UserTier.ROYAL -> Int.MAX_VALUE
            }
            if (voiceMessages.size >= limit) {
                val dropMsg = if (selectedLanguage == "RU") {
                    "⚠️ [Сбой рации] Входящий аудиопакет отклонен. Переполнение буфера для ${currentUserTier.label("RU")}!"
                } else {
                    "⚠️ [Walkie-talkie Overload] Inbound audio dropped. Buffer full for ${currentUserTier.label("EN")}!"
                }
                addSystemMessageToAllRooms(dropMsg)
                return@launch
            }

            val senderName = if (isOnlinePlayActive) onlineOpponentName else (talkieSelectedContactName.ifEmpty { "Зарик Бот" })

            val repliesRu = listOf(
                "Пакеты пришли, но твоя тактика оставляет желать лучшего!",
                "Эта рация защищена, а вот твой домашний квадрат — нет! Готовься к атаке!",
                "Твой голос звучит слишком самоуверенно. Лови встречный дубль!",
                "Кубики у тебя подкрученные! Я отправляю жалобу разработчику SimpleX!",
                "Зарегистрировано шифрованное аудио. Мой алгоритм предсказывает твой проигрыш!"
            )
            val repliesEn = listOf(
                "Voice packets received, but your tactics could be much better!",
                "This wave is secure, but your checkers certainly aren't! Prepare to be eaten!",
                "You sound way too confident. Taste my double-sixes!",
                "Your dice are rigged! I'm sending a complaint to SimpleX developers!",
                "Encrypted audio registered. My algorithm predicts your imminent defeat!"
            )

            val replyText = if (selectedLanguage == "RU") repliesRu.random() else repliesEn.random()
            val incomingMsg = SimplexVoiceMessage(
                sender = senderName,
                durationSec = 4,
                transcript = replyText
            )

            voiceMessages.add(incomingMsg)

            try {
                val tg = android.media.ToneGenerator(android.media.AudioManager.STREAM_NOTIFICATION, 80)
                tg.startTone(android.media.ToneGenerator.TONE_PROP_BEEP, 150)
            } catch (e: Exception) {
                Log.e("AudioViewModel", "exception", e)
            }

            if (isAutoPlayVoiceEnabled) {
                delay(500)
                playVoiceMessage(incomingMsg)
            }
        }
    }

    fun fetchBotJoke(contextDescriptor: String) {
        viewModelScope.launch {
            isGeminiLoadingJokeCallback(true)
            botSpeechBubbleCallback(jokeService.generateJoke(contextDescriptor, selectedLanguage))
            isGeminiLoadingJokeCallback(false)
        }
    }

    fun playRollSound() {
        viewModelScope.launch {
            soundPlayer.playRollSound()
        }
    }

    fun playMoveSound() {
        viewModelScope.launch {
            soundPlayer.playMoveSound()
        }
    }

    override fun onCleared() {
        super.onCleared()
        soundPlayer.release()
        audioPlayer.dispose()
        radioManager.stop()
        try {
            tts?.stop()
            tts?.shutdown()
        } catch (e: Exception) {
            Log.e("AudioViewModel", "exception", e)
        }
    }
}

class AudioViewModelFactory(private val context: Context) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(AudioViewModel::class.java)) {
            return AudioViewModel(context) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}
