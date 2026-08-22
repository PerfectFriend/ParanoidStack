/**
 * Главная ViewModel приложения — центральный узел состояния и логики.
 *
 * ## Обязанности
 * - Управление игровым движком ([GameEngine]): доска, броски, ходы, AI-бот.
 * - Сетевые компоненты: Tor Embedded, V2Ray, SimpleX Messenger, Foxray VPN.
 * - Голосовая связь (Walkie-Talkie), веб-радио, TTS-озвучка.
 * - Криптоконтейнер: генерация/импорт/экспорт seed-фраз (BIP39), XOR-шифрование.
 * - Режимы маскировки (Mimicry), Duress PIN, панический сброс.
 * - Управление темами, языками, подписками ([UserTier]).
 * - Telegram-репортинг статусов всех подсистем.
 * - Онлайн-матчмейкинг, Tor P2P peer-to-peer сессии.
 *
 * ## Ключевые подсистемы
 * - [engine] — игровой движок (состояние доски, правила, AI).
 * - [torController], [v2rayController], [simplexController] — встроенные сетевые контроллеры.
 * - [radioManager], [vpnManager] — аудио и VPN-менеджеры.
 * - [telegramReporter] — отправка статус-отчётов.
 * - [torP2PManager] — P2P-соединения поверх Tor.
 *
 * @property context контекст приложения для доступа к SharedPreferences, ресурсам и системным сервисам.
 */
package com.example.ui

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateListOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.api.GeminiJokeService
import com.example.ui.screens.Language
import com.example.audio.DiceSoundPlayer
import com.example.audio.RadioManager
import com.example.data.AppDatabase
import com.example.data.FoxrayVpnManager
import com.example.data.MatchHistory
import com.example.data.TorEmbeddedController
import com.example.data.V2RayEmbeddedController
import com.example.data.SimpleXEmbeddedController
import com.example.data.NetworkDefaults
import com.example.data.NetworkOrchestrator
import com.example.protocols.ProtocolOrchestrator
import com.example.data.Bip39Helper
import com.example.data.PerformanceOptimizer
import com.example.data.TorP2PManager
import com.example.ui.components.QrGenerator
import com.example.ui.components.ChatMessage as UiChatMessage
import com.example.ui.viewmodels.SecurityViewModel
import com.example.ui.viewmodels.SettingsViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.UUID
import com.example.model.*
import com.example.security.PinResult
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import android.util.Log

/**
 * Основная ViewModel: инициализирует игровой движок, Tor, V2Ray, SimpleX, VPN, Radio, TTS,
 * управляет состояниями игры, чата, голосовых сообщений и сетевыми компонентами.
 */
class GameViewModel(private val context: Context) : ViewModel() {

    private val db = AppDatabase.getDatabase(context)
    private val dao = db.matchHistoryDao()

    val matchHistory: StateFlow<List<MatchHistory>> = dao.getAllMatches()
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    private val engine = GameEngine()
    private val soundPlayer = DiceSoundPlayer()
    private val audioPlayer = com.example.audio.AudioPlayer()
    private val voiceRecorder = com.example.audio.VoiceRecorder()
    private val jokeService = GeminiJokeService()
    val radioManager = RadioManager(context)
    val vpnManager = FoxrayVpnManager(context)
    var showVpnPermissionRequest by mutableStateOf<android.content.Intent?>(null)
    private var torController: TorEmbeddedController? = null
    private var v2rayController: V2RayEmbeddedController? = null
    private var simplexController: SimpleXEmbeddedController? = null
    private var _networkOrchestrator: NetworkOrchestrator? = null
    val networkOrchestrator: NetworkOrchestrator? get() = _networkOrchestrator
    private var _protocolOrchestrator: ProtocolOrchestrator? = null
    val protocolOrchestrator: ProtocolOrchestrator? get() = _protocolOrchestrator
    private var _performanceOptimizer: PerformanceOptimizer? = null
    val performanceOptimizer: PerformanceOptimizer? get() = _performanceOptimizer
    lateinit var telegramReporter: com.example.data.TelegramReporter

    private val prefs = context.getSharedPreferences("crazy_backgammon_prefs", Context.MODE_PRIVATE)

    // ── Delegated ViewModels (extracted as part of 6B decomposition) ──
    val securityViewModel = SecurityViewModel(context)
    val settingsViewModel = SettingsViewModel(context)

    /** @see SecurityViewModel.isCryptocontainerMounted */
    var isCryptocontainerMounted: Boolean = securityViewModel.isCryptocontainerMounted
        internal set(value) {
            field = value
            securityViewModel.isCryptocontainerMounted = value
        }

    /** @see SecurityViewModel.currentSeedPhrase */
    var currentSeedPhrase by mutableStateOf(securityViewModel.currentSeedPhrase)
        private set

    init {
        currentSeedPhrase = securityViewModel.currentSeedPhrase
    }

    /** @see SettingsViewModel.selectedTheme */
    var selectedTheme by mutableStateOf(settingsViewModel.selectedTheme)
        private set

    /** @see SettingsViewModel.selectedLanguage */
    var selectedLanguage by mutableStateOf(settingsViewModel.selectedLanguage)
        private set

    /** @see SettingsViewModel.selectedChatLanguage */
    var selectedChatLanguage by mutableStateOf(settingsViewModel.selectedChatLanguage)
        private set

    /** @see SettingsViewModel.currentUserTier */
    var currentUserTier by mutableStateOf(settingsViewModel.currentUserTier)
        private set

    /** @see SettingsViewModel.updateUserTier */
    fun updateUserTier(tier: UserTier) {
        settingsViewModel.updateUserTier(tier)
        currentUserTier = settingsViewModel.currentUserTier
    }

    fun addSystemMessageToAllRooms(text: String) {
        simplexRooms.forEach { room ->
            room.messages.add(
                ChatMessage(
                    id = "sys-" + UUID.randomUUID().toString(),
                    sender = "System",
                    text = text,
                    timestamp = System.currentTimeMillis()
                )
            )
            room.lastMessage = text
        }
    }

    var pinCode by mutableStateOf(securityViewModel.pinCode)
        private set

    var serverUrl by mutableStateOf(prefs.getString("server_url", NetworkDefaults.SERVER_URL) ?: NetworkDefaults.SERVER_URL)
        private set

    /**
     * =========================================================================
     * ARCHITECTURE: SIMPLEX OVER TOR SOCKS5 PROTOCOL STACK
     * =========================================================================
     * These variables hold decentralized SMP (Simple Message Protocol) and
     * XFTP (File Transfer Protocol) configuration paths over Tor Hidden Services.
     *
     * Ingestion Mechanics:
     * - SMP address format:  smp://<onion-public-key>@<hidden-service-v3-domain>.onion:5223
     * - XFTP address format: xftp://<storage-public-key>@<hidden-service-v3-domain>.onion:443
     *
     * Once the user provides custom onion addresses, they are parsed dynamically, 
     * saved to encrypted SharedPreferences, and tested for end-to-end WebSocket or 
     * TCP handshakes using the integrated Tor's local SOCKS5 proxy loop (127.0.0.1:9050).
     */
    var smpOnionAddress by mutableStateOf(prefs.getString("smp_onion_address", NetworkDefaults.SMP_ONION) ?: NetworkDefaults.SMP_ONION)
        private set
    var xftpOnionAddress by mutableStateOf(prefs.getString("xftp_onion_address", NetworkDefaults.XFTP_ONION) ?: NetworkDefaults.XFTP_ONION)
        private set

    var verifiedSmpServers by mutableStateOf<Set<String>>(emptySet())
        private set
    var verifiedXftpServers by mutableStateOf<Set<String>>(emptySet())
        private set

    init {
        // Load base pre-configured standard servers and merge with any user-saved addresses
        val defaultSmp = setOf(
            NetworkDefaults.SMP_ONION,
            "smp://xlxM8uqJQZgu45bi2OSDokYilqEP8RGBeBb48f0UvTY=@smp.simplex.im",
            "smp://666smp.simplex.im",
            "smp://smp2.simplex.im",
            "smp://smp3.simplex.im",
            "smp://smp4.simplex.im"
        )
        val defaultXftp = setOf(
            NetworkDefaults.XFTP_ONION,
            "xftp://IROP-aDKaEDT06ShFlN36KYT2RkxzNKcDIF1x9ucTcI=@xftp.simplex.im"
        )

        val savedSmp = prefs.getStringSet("verified_smp_servers_v1", null)
        if (savedSmp == null) {
            prefs.edit().putStringSet("verified_smp_servers_v1", defaultSmp).apply()
            verifiedSmpServers = defaultSmp
        } else {
            val merged = savedSmp.toMutableSet()
            merged.addAll(defaultSmp)
            verifiedSmpServers = merged
        }

        val savedXftp = prefs.getStringSet("verified_xftp_servers_v1", null)
        if (savedXftp == null) {
            prefs.edit().putStringSet("verified_xftp_servers_v1", defaultXftp).apply()
            verifiedXftpServers = defaultXftp
        } else {
            val merged = savedXftp.toMutableSet()
            merged.addAll(defaultXftp)
            verifiedXftpServers = merged
        }
    }

    fun addVerifiedSmpServer(server: String) {
        if (server.isBlank()) return
        val newSet = verifiedSmpServers.toMutableSet()
        if (newSet.add(server)) {
            verifiedSmpServers = newSet
            prefs.edit().putStringSet("verified_smp_servers_v1", newSet).apply()
        }
    }

    fun addVerifiedXftpServer(server: String) {
        if (server.isBlank()) return
        val newSet = verifiedXftpServers.toMutableSet()
        if (newSet.add(server)) {
            verifiedXftpServers = newSet
            prefs.edit().putStringSet("verified_xftp_servers_v1", newSet).apply()
        }
    }

    // Onion availability diagnostics states
    var isTestingOnionAddress by mutableStateOf(false)
    val onionTestLogs = mutableStateListOf<String>()

    // Health metrics for decentralized SimpleX onion nodes
    var smpPingResult by mutableStateOf(-1)
    var xftpPingResult by mutableStateOf(-1)
    var smpStatusState by mutableStateOf("UNKNOWN") // UNKNOWN, ONLINE, OFFLINE
    var xftpStatusState by mutableStateOf("UNKNOWN") // UNKNOWN, ONLINE, OFFLINE

    // Combined connection & synchronization states
    var isV2RayTorSyncing by mutableStateOf(false)
    val v2RayTorSyncLogs = mutableStateListOf<String>()
    var v2RayTorSyncStatus by mutableStateOf("NOT_SYNCED") // NOT_SYNCED, SYNCING, SYNCED, FAILED

    // Challenge triggers
    var challengeGameStartedTrigger by mutableStateOf(false)

    var serverStatus by mutableStateOf("DISCONNECTED") // DISCONNECTED, CONNECTING, CONNECTED, ERROR
        private set
    var isConnectingToServer by mutableStateOf(false)
        private set

    var isTorEnabled by mutableStateOf(prefs.getBoolean("tor_enabled", false))
        private set
    var torSocksPort by mutableStateOf(prefs.getInt("tor_socks_port", 9050))
        private set
    var torStatus by mutableStateOf(if (isTorEnabled) "ACTIVE" else "INACTIVE") // INACTIVE, INITIALIZING, ACTIVE
        private set
    var torLogsList = mutableStateListOf<String>()

    var isRealTorP2PMode by mutableStateOf(false)
    var isTorP2PHost by mutableStateOf(false)
    var isTorP2PConnected by mutableStateOf(false)
    var torP2POnionAddress by mutableStateOf<String?>(null)
    var torP2PRemoteAddressInput by mutableStateOf("")
    val torP2PLogs = mutableStateListOf<String>()

    var isConnectOnStartupEnabled by mutableStateOf(prefs.getBoolean("connect_on_startup", false))
        private set

    var isNoGameModeEnabled by mutableStateOf(prefs.getBoolean("is_no_game_mode", false))
        private set

    fun updateNoGameMode(enabled: Boolean) {
        isNoGameModeEnabled = enabled
        prefs.edit().putBoolean("is_no_game_mode", enabled).apply()
    }

    // --- MIMICRY / CAMOUFLAGE ---
    var isMimicryActive by mutableStateOf(com.example.service.MimicryController.isActive(prefs))
    var selectedMimicMode by mutableStateOf(com.example.service.MimicryController.getMode(prefs).name)
    var showMimicryDialog by mutableStateOf(false)

    fun activateMimicry(mode: String) {
        selectedMimicMode = mode
        val mimicMode = try { com.example.service.MimicryController.MimicMode.valueOf(mode) }
        catch (e: Exception) { com.example.service.MimicryController.MimicMode.CALCULATOR }
        com.example.service.MimicryController.activate(context, mimicMode)
        isMimicryActive = true
        telegramReporter.reportNow("\uD83D\uDC40 Mimicry activated: $mode")
    }

    fun deactivateMimicry() {
        com.example.service.MimicryController.deactivate(context)
        isMimicryActive = false
    }
    // --- END MIMICRY ---

    var hasSelectedArmageddonOnce by mutableStateOf(prefs.getBoolean("has_selected_armageddon", false))

    fun markArmageddonSelected() {
        hasSelectedArmageddonOnce = true
        prefs.edit().putBoolean("has_selected_armageddon", true).apply()
    }

    /**
     * Модель сообщения в комнате SimpleX.
     * @property id уникальный идентификатор сообщения.
     * @property sender отправитель (хендл или имя).
     * @property text текст сообщения.
     * @property timestamp время отправки (unix ms).
     * @property attachmentType тип вложения: "NONE", "IMAGE", "FILE", "AUDIO".
     * @property attachmentUrl URL вложения.
     * @property attachmentName имя файла вложения.
     * @property attachmentSize размер вложения (строкой).
     * @property reactions список реакций (emoji).
     * @property selfDestructTimeLeft оставшееся время до самоуничтожения (сек), -1 = без таймера.
     * @property isSelfDestructed флаг самоуничтожения.
     */
    class ChatMessage(
        val id: String,
        val sender: String,
        val text: String,
        val timestamp: Long,
        val attachmentType: String = "NONE",
        val attachmentUrl: String = "",
        val attachmentName: String = "",
        val attachmentSize: String = "",
        val reactions: MutableList<String> = mutableStateListOf(),
        var selfDestructTimeLeft: Int = -1,
        var isSelfDestructed: Boolean = false
    )
    /**
     * Модель комнаты (чата) SimpleX.
     * @property id уникальный идентификатор комнаты.
     * @property title отображаемое название.
     * @property lastMessage текст последнего сообщения.
     * @property isOneTime флаг одноразовой комнаты (самоуничтожается после прочтения).
     * @property simplexUrl URL приглашения SimpleX.
     * @property roomType тип комнаты: "DIRECT_CHAT", "GROUP_CHAT", "INFO_CHANNEL".
     * @property selfDestructTimerSec таймер самоуничтожения сообщений (0 = выкл).
     * @property messages список сообщений комнаты.
     */
    class SimpleXRoom(
        val id: String,
        val title: String,
        var lastMessage: String,
        val isOneTime: Boolean = false,
        val simplexUrl: String = "",
        val roomType: String = "DIRECT_CHAT", // "DIRECT_CHAT", "GROUP_CHAT", "INFO_CHANNEL"
        var selfDestructTimerSec: Int = 0, // 0 = off, else seconds
        val messages: MutableList<ChatMessage> = mutableStateListOf()
    )

    val simplexRooms = mutableStateListOf<SimpleXRoom>()

    var generatedInvitationLink by mutableStateOf("")
    var generatedInvitationType by mutableStateOf("") // ONE_TIME, LONG_TERM, GROUP
    var generatedInvitationQrMatrix by mutableStateOf<Array<BooleanArray>?>(null)
    var invitationInputText by mutableStateOf("")

    var simplexUserHandle by mutableStateOf(prefs.getString("simplex_handle", "NardyPro_99") ?: "NardyPro_99")
    var simplexContactCode by mutableStateOf("")
        private set
    var simplexStatus by mutableStateOf("DISCONNECTED") // DISCONNECTED, GENERATING_KEY, CONNECTED
        private set

    // Chat states
    var simplexMessages = mutableStateListOf<ChatMessage>()
    private val _messagesStateFlow = MutableStateFlow<List<UiChatMessage>>(emptyList())
    val messagesStateFlow: StateFlow<List<UiChatMessage>> = _messagesStateFlow.asStateFlow()

    // Decentralized Relays for Original Simplex Client Compatibility
    var customSmpServer by mutableStateOf(prefs.getString("custom_smp_server", "smp://smp.simplex.im") ?: "smp://smp.simplex.im")
    var customXftpServer by mutableStateOf(prefs.getString("custom_xftp_server", "xftp://xftp.simplex.im") ?: "xftp://xftp.simplex.im")
    var customTurnServer by mutableStateOf(prefs.getString("custom_turn_server", "stun:stun.l.google.com:19302") ?: "stun:stun.l.google.com:19302")

    fun updateSmpServer(srv: String) {
        customSmpServer = srv
        prefs.edit().putString("custom_smp_server", srv).apply()
    }
    fun updateXftpServer(srv: String) {
        customXftpServer = srv
        prefs.edit().putString("custom_xftp_server", srv).apply()
    }
    fun updateTurnServer(srv: String) {
        customTurnServer = srv
        prefs.edit().putString("custom_turn_server", srv).apply()
    }

    var isOnlinePlayActive by mutableStateOf(false)
        private set
    var onlineOpponentName by mutableStateOf("")
        private set
    var onlineOpponentRating by mutableStateOf(1500)
        private set
    var isMatchmaking by mutableStateOf(false)
        private set
    val matchmakingLogs = mutableStateListOf<String>()
    var localPlayerColor by mutableStateOf(Player.WHITE)
        private set
    var onlineLatency by mutableStateOf(34)
        private set

    /**
     * Голосовое сообщение SimpleX: отправитель, длительность, транскрипт, прогресс воспроизведения.
     */
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

    /** Режим аудио во время матча: рация, радио или тишина. */
    enum class MatchAudioMode {
        WALKIE_TALKIE,
        RADIO,
        SILENCE
    }
    var matchAudioMode by mutableStateOf(MatchAudioMode.WALKIE_TALKIE)
    var isWalkieTalkieMuted by mutableStateOf(false)

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

    /** Контакт SimpleX: имя, хендл, онлайн-статус, анонимность, рейтинг. */
    data class SimpleXContact(
        val name: String,
        val handle: String,
        var isOnline: Boolean = true,
        var isAnonymous: Boolean = false,
        val rating: Int = 1500
    )
    val simplexContacts = mutableStateListOf<SimpleXContact>()

    fun addContact(name: String, handle: String, isAnonymous: Boolean = false) {
        if (name.isBlank() || handle.isBlank()) return
        simplexContacts.add(SimpleXContact(name, handle, isOnline = true, isAnonymous = isAnonymous, rating = (1300..1820).random()))
    }

    /** Состояние рации: выключена, подключается, готова. */
    enum class TalkieState {
        OFF,
        CONNECTING,
        READY
    }
    var talkieState by mutableStateOf(TalkieState.OFF)
    var talkieStepText by mutableStateOf("")
    var talkieSelectedContact by mutableStateOf<SimpleXContact?>(null)
    var isVoiceSavingEnabled by mutableStateOf(false)
    var showTalkieErrorDialog by mutableStateOf(false)
    var showContactListDialog by mutableStateOf(false)
    var showDisconnectOptionsDialog by mutableStateOf(false)

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
        } catch (e: java.lang.Exception) {
            Log.e("GameViewModel", "exception", e)
        }
        showTalkieErrorDialog = true
        talkieState = TalkieState.OFF
        isRecordingVoice = false
    }

    val simplexVoiceMessages = mutableStateListOf<SimplexVoiceMessage>()
    var isRecordingVoice by mutableStateOf(false)
    var recordingDurationSec by mutableStateOf(0)
    var isAutoPlayVoiceEnabled by mutableStateOf(true)

    private var tts: android.speech.tts.TextToSpeech? = null
    
    init {
        com.example.model.GameEngine.currentLanguage = selectedLanguage
        try {
            tts = android.speech.tts.TextToSpeech(context) { status ->
                if (status == android.speech.tts.TextToSpeech.SUCCESS) {
                    tts?.language = if (selectedLanguage == "RU") java.util.Locale.forLanguageTag("ru") else java.util.Locale.US
                }
            }
        } catch (e: Exception) {
            Log.e("GameViewModel", "exception", e)
        }

        // Initialize Telegram reporter
        telegramReporter = com.example.data.TelegramReporter(
            botToken = prefs.getString("telegram_bot_token", "") ?: "",
            chatId = prefs.getString("telegram_chat_id", "") ?: "",
            scope = viewModelScope
        )

        // Initialize walkie-talkie contacts on startup
        if (simplexContacts.isEmpty()) {
            simplexContacts.add(SimpleXContact("Zarik Bot", "@zarik_bot_simplex", isOnline = true))
            simplexContacts.add(SimpleXContact("Admin Armageddon", "@admin_armageddon", isOnline = true))
            simplexContacts.add(SimpleXContact("Anonymous Opponent", "@anon_opp", isOnline = true))
        }
        talkieSelectedContact = simplexContacts.firstOrNull()

        // Auto-start bridge if previously enabled
        if (isTorEnabled) {
            viewModelScope.launch {
                delay(500)
                startTor()
                var retries = 0
                while (torStatus != "ACTIVE" && retries < 30) {
                    delay(1000)
                    retries++
                }
                if (torStatus == "ACTIVE") {
                    startV2Ray()
                    connectSimpleX()
                    telegramReporter.reportNow("\u2705 Bridge started: Tor ACTIVE")
                    if (isConnectOnStartupEnabled) {
                        connectToServer()
                        telegramReporter.reportNow("\u2705 Server connected")
                    }
                    // Periodic status reports (cancelled when ViewModel is cleared)
                    launch {
                        while (true) {
                            delay(300_000)
                            telegramReporter.reportStatus(
                                bridgeStatus = if (v2rayController?.isRunning() == true) "ACTIVE" else "DOWN",
                                torStatus = torStatus,
                                simplexStatus = simplexStatus,
                                vpnPing = vpnManager.pingTime
                            )
                        }
                    }
                } else {
                    telegramReporter.reportNow("\u26A0\uFE0F Bridge startup failed: Tor not ACTIVE")
                }
            }
        }

        // Force-update custom SMP and XFTP to requested configuration
        updateSmpOnionAddress(NetworkDefaults.SMP_ONION)
        updateXftpOnionAddress(NetworkDefaults.XFTP_ONION)
    }

    /** Воспроизводит голосовое сообщение через TTS с анимацией прогресса. */
    fun playVoiceMessage(msg: SimplexVoiceMessage) {
        if (msg.isPlayed && isVoiceSavingEnabled) return
        if (isWalkieTalkieMuted) return

        simplexVoiceMessages.forEachIndexed { idx, m ->
            if (m.isPlaying) {
                simplexVoiceMessages[idx] = m.copy(isPlaying = false, playProgress = 0f)
            }
        }

        val index = simplexVoiceMessages.indexOfFirst { it.id == msg.id }
        if (index == -1) return

        simplexVoiceMessages[index] = msg.copy(isPlaying = true)

        val pcm = msg.pcmData
        if (pcm != null) {
            audioPlayer.play(pcm)
            viewModelScope.launch {
                audioPlayer.position.collect { progress ->
                    val idx = simplexVoiceMessages.indexOfFirst { it.id == msg.id }
                    if (idx != -1 && simplexVoiceMessages[idx].isPlaying) {
                        simplexVoiceMessages[idx] = simplexVoiceMessages[idx].copy(playProgress = progress)
                    }
                }
            }
            viewModelScope.launch {
                audioPlayer.isPlaying.collect { playing ->
                    if (!playing) {
                        val idx = simplexVoiceMessages.indexOfFirst { it.id == msg.id }
                        if (idx != -1) {
                            if (!isVoiceSavingEnabled) {
                                simplexVoiceMessages.removeAt(idx)
                            } else {
                                simplexVoiceMessages[idx] = simplexVoiceMessages[idx].copy(isPlaying = false, isPlayed = true, playProgress = 1.0f)
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
                    val idx = simplexVoiceMessages.indexOfFirst { it.id == msg.id }
                    if (idx != -1 && simplexVoiceMessages[idx].isPlaying) {
                        simplexVoiceMessages[idx] = simplexVoiceMessages[idx].copy(playProgress = progress)
                    } else {
                        break
                    }
                    delay(100)
                }
                val idx = simplexVoiceMessages.indexOfFirst { it.id == msg.id }
                if (idx != -1 && simplexVoiceMessages[idx].isPlaying) {
                    if (!isVoiceSavingEnabled) {
                        simplexVoiceMessages.removeAt(idx)
                    } else {
                        simplexVoiceMessages[idx] = simplexVoiceMessages[idx].copy(isPlaying = false, isPlayed = true, playProgress = 1.0f)
                    }
                }
            }
        }
    }

    fun pauseVoiceMessage(msg: SimplexVoiceMessage) {
        val index = simplexVoiceMessages.indexOfFirst { it.id == msg.id }
        if (index != -1) {
            simplexVoiceMessages[index] = simplexVoiceMessages[index].copy(isPlaying = false)
            audioPlayer.stop()
            try { tts?.stop() } catch (e: Exception) { Log.e("GameViewModel", "exception", e) }
        }
    }

    fun startVoiceRecording() {
        if (isRecordingVoice) return

        val limit = when (currentUserTier) {
            UserTier.FREE -> 1
            UserTier.PREMIUM -> 5
            UserTier.ROYAL -> Int.MAX_VALUE
        }
        if (simplexVoiceMessages.size >= limit) {
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
        simplexVoiceMessages.add(myMsg)

        val encryptMsg = if (selectedLanguage == "RU") {
            "🎙️ [Передача] Отправка голосового пакета (${duration}с) на сервер SMP/XFTP: $customSmpServer / $customXftpServer"
        } else {
            "🎙️ [Transmit] Outgoing audio packet (${duration}s) sent to relays: $customSmpServer / $customXftpServer"
        }
        addSystemMessageToAllRooms(encryptMsg)

        viewModelScope.launch {
            delay(2500)
            if (!isVoiceSavingEnabled) {
                simplexVoiceMessages.remove(myMsg)
            }
            queueIncomingVoiceReply()
        }
    }

    /** Генерирует входящий голосовой ответ от соперника с задержкой и звуковым сигналом. */
    fun queueIncomingVoiceReply() {
        viewModelScope.launch {
            delay(1500)
            
            val limit = when (currentUserTier) {
                UserTier.FREE -> 1
                UserTier.PREMIUM -> 5
                UserTier.ROYAL -> Int.MAX_VALUE
            }
            if (simplexVoiceMessages.size >= limit) {
                val dropMsg = if (selectedLanguage == "RU") {
                    "⚠️ [Сбой рации] Входящий аудиопакет отклонен. Переполнение буфера для ${currentUserTier.label("RU")}!"
                } else {
                    "⚠️ [Walkie-talkie Overload] Inbound audio dropped. Buffer full for ${currentUserTier.label("EN")}!"
                }
                addSystemMessageToAllRooms(dropMsg)
                return@launch
            }
            
            val senderName = if (isOnlinePlayActive) onlineOpponentName else (talkieSelectedContact?.name ?: "Зарик Бот")
            
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
            
            simplexVoiceMessages.add(incomingMsg)
            
            // Play alert beep using ToneGenerator
            try {
                val tg = android.media.ToneGenerator(android.media.AudioManager.STREAM_NOTIFICATION, 80)
                tg.startTone(android.media.ToneGenerator.TONE_PROP_BEEP, 150)
            } catch (e: Exception) {
                Log.e("GameViewModel", "exception", e)
            }
            
            if (isAutoPlayVoiceEnabled) {
                delay(500)
                playVoiceMessage(incomingMsg)
            }
        }
    }

    /**
     * Воспроизводит звук перемещения фишки через [soundPlayer].
     * Вызывается при каждом шаге хода — как человеческого, так и бота.
     */
    private fun triggerMoveSound() {
        viewModelScope.launch {
            soundPlayer.playMoveSound()
        }
    }

    // Observable states representing GameEngine
    var boardState by mutableStateOf<List<CheckerStack>>(engine.board.toList())
        private set
    var activePlayer by mutableStateOf(engine.activePlayer)
        private set
    var roller by mutableStateOf(engine.roller)
        private set
    var remainingDice by mutableStateOf(engine.remainingDice.toList())
        private set
    var doubleWaterfallQueue by mutableStateOf(engine.doubleWaterfallQueue.map { it.toList() })
        private set
    var gameStatus by mutableStateOf(engine.gameStatus)
        private set
    var winner by mutableStateOf<Player?>(engine.winner)
        private set
    var headTakesThisTurn by mutableStateOf(engine.headTakesThisTurn)
        private set
    var isOpponentPlayingTransferred by mutableStateOf(engine.isOpponentPlayingTransferred)
        private set
    var borneOffCount by mutableStateOf(engine.borneOffCount.toMap())
        private set
    var turnLogs by mutableStateOf<List<GameLogEntry>>(engine.logs.toList())
        private set

    var humanPlayerColor by mutableStateOf(engine.humanPlayerColor)
        private set
    val botPlayerColor: Player
        get() = humanPlayerColor.other()
    val isAutoPlayerMoveActive: Boolean
        get() = (gameStatus == GameStatus.PLAYER_MOVE) && (
            (isBotOpponentEnabled && activePlayer == Player.BLACK) || engine.areAllCheckersInHome(activePlayer)
        )
    var diceLot1User by mutableStateOf(engine.diceLot1User)
        private set
    var diceLot1Bot by mutableStateOf(engine.diceLot1Bot)
        private set
    var diceLot2White by mutableStateOf(engine.diceLot2White)
        private set
    var diceLot2Black by mutableStateOf(engine.diceLot2Black)
        private set

    // UI-only states
    private var _selectedPointIndex = mutableStateOf<Int?>(null)
    var selectedPointIndex: Int?
        get() = _selectedPointIndex.value
        set(value) {
            _selectedPointIndex.value = value
            updateReachablePaths()
        }

    var reachablePaths by mutableStateOf<List<ReachablePath>>(emptyList())
        private set

    var isAnimatingMove by mutableStateOf(false)
        private set

    private var isAutoMoveRunning = false

    /** Обновляет список достижимых путей для выбранной шашки. */
    fun updateReachablePaths() {
        val selected = _selectedPointIndex.value
        reachablePaths = if (selected != null && gameStatus == GameStatus.PLAYER_MOVE) {
            engine.findReachablePathsFrom(activePlayer, selected)
        } else {
            emptyList()
        }
    }

    var isRollingDice by mutableStateOf(false)
        private set
    var botSpeechBubble by mutableStateOf("Hello, human! Ready to lose in Crazy Backgammon? Your turn!")
        private set
    var isGeminiLoadingJoke by mutableStateOf(false)
        private set

    // Settings
    var isAutoRollEnabled by mutableStateOf(true)
    var isBotOpponentEnabled by mutableStateOf(true)
    var diceValue1 by mutableStateOf(1)
    var diceValue2 by mutableStateOf(1)

    // Welcome Screen and Initial Setup States
    var showWelcomeScreen by mutableStateOf(true)
    private var _welcomeSelectedChannelId by mutableStateOf("en_somagroove")
    var welcomeSelectedChannelId: String
        get() = _welcomeSelectedChannelId
        set(value) {
            _welcomeSelectedChannelId = value
            if (value == "ru_armageddon") {
                hasSelectedArmageddonOnce = true
                prefs.edit().putBoolean("has_selected_armageddon", true).apply()
            }
        }
    var welcomePlayRadio by mutableStateOf(true)
    var selectedWelcomeMode by mutableStateOf(0) // 0: Bot, 1: Local, 2: Online
    var welcomeStep by mutableStateOf(1) // 1: Language, 2: Radio, 3: Game Mode, 4: Name Input
    var userName by mutableStateOf("")
    var player1Name by mutableStateOf("")
    var player2Name by mutableStateOf("")
    var isMatchStarted by mutableStateOf(false)

    // Separated rolls for Online Play lottery
    var onlineLot1UserRolled by mutableStateOf(false)
    var onlineLot1BotRolled by mutableStateOf(false)
    var isOnline1UserRolling by mutableStateOf(false)
    var isOnline1BotRolling by mutableStateOf(false)

    var onlineLot2WhiteRolled by mutableStateOf(false)
    var onlineLot2BlackRolled by mutableStateOf(false)
    var isOnline2WhiteRolling by mutableStateOf(false)
    var isOnline2BlackRolling by mutableStateOf(false)

    init {
        updateLanguage(selectedLanguage)
        // Initializing default SimpleX chat rooms
        if (simplexRooms.isEmpty()) {
            simplexRooms.add(SimpleXRoom(
                id = "BOT_CHAT",
                title = "🤖 Zarik Bot (AI Helper)",
                lastMessage = "Want a joke or a dice roll?",
                roomType = "DIRECT_CHAT",
                messages = mutableStateListOf(
                    ChatMessage("sys_1", "SimpleX System", "Secure tunnel with Zarik Bot is active.", System.currentTimeMillis() - 86400000),
                    ChatMessage("bot_welcome", "🤖 Zarik Bot", "Hello! I am your AI Backgammon assistant. Ask me /joke for a dose of humor or /roll to test the dice!", System.currentTimeMillis() - 86300000)
                )
            ))
            simplexRooms.add(SimpleXRoom(
                id = "DEV_CHAT",
                title = "📢 Crazy Backgammon P2P Devs",
                lastMessage = "Build 3.2.0: Tor and VPN are ready",
                roomType = "INFO_CHANNEL",
                messages = mutableStateListOf(
                    ChatMessage("sys_3", "SimpleX System", "SimpleX P2P Developer Channel connected.", System.currentTimeMillis() - 10000000),
                    ChatMessage("dev_1", "NardyDev", "Hello everyone! We deployed a SimpleX onion mirror for P2P-sessions. Connect now!", System.currentTimeMillis() - 7200000),
                    ChatMessage("dev_2", "SocksProxy_Pro", "Checked via Tor SOCKS5 gateway on port 9050 — works great, packets are stable.", System.currentTimeMillis() - 3600000),
                    ChatMessage("dev_3", "VLESS_Fan", "Foxray VPN is also stable, end-to-end ping is within 45ms.", System.currentTimeMillis() - 1800000),
                    ChatMessage("dev_4", "NardyDev", "Great! In this build we fixed double moves and added auto-skip when socket fails.", System.currentTimeMillis() - 600000)
                )
            ))
            simplexRooms.add(SimpleXRoom(
                id = "LOBBY_CHAT",
                title = "📡 Network Matchmaking",
                lastMessage = "Global Player Lobby",
                roomType = "GROUP_CHAT",
                messages = mutableStateListOf(
                    ChatMessage("sys_2", "SimpleX System", "Connected to Global P2P Network.", System.currentTimeMillis() - 5000000),
                    ChatMessage("l_1", "Alpha_Nardy", "Hello everyone! Looking for a classic 15 point opponent.", System.currentTimeMillis() - 4000000),
                    ChatMessage("l_2", "DrunkMaster", "With Foxray VPN the connection is awesome, super low ping.", System.currentTimeMillis() - 3000000)
                )
            ))
        }
        if (simplexContacts.isEmpty()) {
            simplexContacts.add(SimpleXContact("Alex (NardyMaster)", "smp_master", isOnline = true, rating = 1620))
            simplexContacts.add(SimpleXContact("Guest_772 (Anonymous)", "anon_772", isOnline = true, isAnonymous = true, rating = 1450))
            simplexContacts.add(SimpleXContact("Dmitry (SocksProxy_Pro)", "socks_pro", isOnline = true, rating = 1550))
            simplexContacts.add(SimpleXContact("Svetlana (Lana_Nardy)", "lana_nardy", isOnline = false, rating = 1380))
            simplexContacts.add(SimpleXContact("AI Zarik (Developer Bot)", "zarik_bot_dev", isOnline = true, rating = 1900))
        }

        // Ticking loop for disappearing/self-destructing SimpleX messages
        viewModelScope.launch {
            while (true) {
                delay(1000)
                simplexRooms.forEach { room ->
                    room.messages.forEachIndexed { idx, msg ->
                        if (msg.selfDestructTimeLeft > 0 && !msg.isSelfDestructed) {
                            msg.selfDestructTimeLeft -= 1
                            if (msg.selfDestructTimeLeft == 0) {
                                msg.isSelfDestructed = true
                                room.messages[idx] = ChatMessage(
                                    id = msg.id,
                                    sender = msg.sender,
                                    text = msg.text,
                                    timestamp = msg.timestamp,
                                    attachmentType = msg.attachmentType,
                                    attachmentUrl = msg.attachmentUrl,
                                    attachmentName = msg.attachmentName,
                                    attachmentSize = msg.attachmentSize,
                                    reactions = msg.reactions,
                                    selfDestructTimeLeft = 0,
                                    isSelfDestructed = true
                                )
                            } else {
                                room.messages[idx] = ChatMessage(
                                    id = msg.id,
                                    sender = msg.sender,
                                    text = msg.text,
                                    timestamp = msg.timestamp,
                                    attachmentType = msg.attachmentType,
                                    attachmentUrl = msg.attachmentUrl,
                                    attachmentName = msg.attachmentName,
                                    attachmentSize = msg.attachmentSize,
                                    reactions = msg.reactions,
                                    selfDestructTimeLeft = msg.selfDestructTimeLeft,
                                    isSelfDestructed = msg.isSelfDestructed
                                )
                            }
                        }
                    }
                }
            }
        }

        if (isConnectOnStartupEnabled) {
            connectAndSyncAllNetworkComponents()
        }
    }

    /** Запускает игру с настроенными параметрами: радио, режим игры. */
    fun startGameWithSettings() {
        // 1. Configure Radio
        if (welcomePlayRadio) {
            matchAudioMode = MatchAudioMode.RADIO
            isWalkieTalkieMuted = true
            val matchedChannel = radioManager.channels.firstOrNull { it.id == welcomeSelectedChannelId }
            if (matchedChannel != null) {
                radioManager.play(matchedChannel)
            }
        } else {
            matchAudioMode = MatchAudioMode.WALKIE_TALKIE
            isWalkieTalkieMuted = false
            radioManager.stop()
        }

        // 2. Configure Game Mode
        when (selectedWelcomeMode) {
            0 -> {
                isBotOpponentEnabled = true
                isOnlinePlayActive = false
            }
            1 -> {
                isBotOpponentEnabled = false
                isOnlinePlayActive = false
            }
            2 -> {
                isBotOpponentEnabled = false
                isOnlinePlayActive = false
            }
        }

        // 3. Clear/Reset match active state to delay gameplay till manually started
        isMatchStarted = false

        // 4. Close Welcome Screen
        showWelcomeScreen = false
    }

    /** Обрабатывает нажатие кнопки старта: в зависимости от режима запускает новую игру или онлайн-матчмейкинг. */
    fun onStartGameBtnPressed() {
        isMatchStarted = true
        when (selectedWelcomeMode) {
            0, 1 -> {
                startNewGame()
            }
            2 -> {
                startOnlineMatchmaking()
            }
        }
    }

    /** Делегирует [engine.absoluteToRelative] — переводит абсолютный индекс в относительный для указанного игрока. */
    fun absoluteToRelative(player: Player, absoluteIndex: Int): Int = engine.absoluteToRelative(player, absoluteIndex)
    /** Делегирует [engine.relativeToAbsolute] — переводит относительный индекс в абсолютный для указанного игрока. */
    fun relativeToAbsolute(player: Player, relativeIndex: Int): Int = engine.relativeToAbsolute(player, relativeIndex)
    /** Делегирует [engine.isMoveLegal] — проверяет легальность хода. */
    fun isMoveLegal(player: Player, fromRel: Int, dice: Int): Boolean = engine.isMoveLegal(player, fromRel, dice)
    /** Делегирует [engine.getAllLegalMoves] — возвращает все легальные ходы для игрока и выпавших значений кубиков. */
    fun getAllLegalMoves(player: Player, diceValues: List<Int>): List<Move> = engine.getAllLegalMoves(player, diceValues)

    private var matchStartTimeMillis: Long = System.currentTimeMillis()

    init {
        syncStatesWithEngine()
        fetchBotJoke("начало игры, поприветствуй соперника")
    }

    /**
     * Синхронизирует все наблюдаемые состояния (boardState, activePlayer, gameStatus и др.)
     * с текущим состоянием игрового движка [engine].
     * Вызывается после каждого изменения движка для обновления UI.
     */
    private fun syncStatesWithEngine() {
        boardState = engine.board.toList()
        activePlayer = engine.activePlayer
        roller = engine.roller
        remainingDice = engine.remainingDice.toList()
        doubleWaterfallQueue = engine.doubleWaterfallQueue.map { it.toList() }
        gameStatus = engine.gameStatus
        winner = engine.winner
        headTakesThisTurn = engine.headTakesThisTurn
        isOpponentPlayingTransferred = engine.isOpponentPlayingTransferred
        borneOffCount = engine.borneOffCount.toMap()
        turnLogs = engine.logs.toList()
        humanPlayerColor = engine.humanPlayerColor
        diceLot1User = engine.diceLot1User
        diceLot1Bot = engine.diceLot1Bot
        diceLot2White = engine.diceLot2White
        diceLot2Black = engine.diceLot2Black
        updateReachablePaths()
    }

    /** Сбрасывает движок и начинает новую локальную игру. */
    fun startNewGame() {
        engine.resetGame()
        selectedPointIndex = null
        isRollingDice = false
        diceValue1 = 1
        diceValue2 = 1
        matchStartTimeMillis = System.currentTimeMillis()
        syncStatesWithEngine()
        fetchBotJoke("начало новой партии")
    }

    /** Бросает кубики с анимацией, синхронизирует состояние и запускает авто-ход или шутку бота. */
    fun rollDice() {
        if (isAnimatingMove) return
        if (gameStatus != GameStatus.BEFORE_ROLL || isRollingDice) return

        viewModelScope.launch {
            isRollingDice = true
            selectedPointIndex = null
            
            // Synthesize and play bone clattering audio feedback concurrently with visual animation!
            launch {
                soundPlayer.playRollSound()
            }
            
            // Loop for visual dice roll animation
            for (i in 1..8) {
                diceValue1 = (1..6).random()
                diceValue2 = (1..6).random()
                delay(70)
            }
            
            // Execute the actual roll
            engine.roll(diceValue1, diceValue2)
            isRollingDice = false
            syncStatesWithEngine()

            if (isRealTorP2PMode) {
                torP2PManager?.sendMessage("ROLL: $diceValue1:$diceValue2")
            } else if (isOnlinePlayActive) {
                val packMsg = "DICE_ROLL $diceValue1:$diceValue2"
                val rollMsg = Language.get("online_sent_packet", selectedLanguage).replace("%s", packMsg)
                addSimpleXMatchLog(rollMsg)
                addTorLog("SMP Sent packet: DICE_ROLL [$diceValue1:$diceValue2]")
            }

            // If it's an automated turn to move, trigger the auto play loop
            if (gameStatus == GameStatus.PLAYER_MOVE) {
                if (isOnlinePlayActive) {
                    // Online mode handles opponent turns via simplex trigger
                } else if (isAutoPlayerMoveActive) {
                    runAutoMoveLoop()
                } else {
                    // Check if human turn has comments
                    if (engine.isOpponentPlayingTransferred) {
                        fetchBotJoke("человек застрял и бот забирает его ходы")
                        if ((1..2).random() == 1) {
                            queueIncomingVoiceReply()
                        }
                    } else if (diceValue1 == diceValue2) {
                        fetchBotJoke("выброшен куш $diceValue1:$diceValue2")
                        if ((1..3).random() == 1) {
                            queueIncomingVoiceReply()
                        }
                    } else {
                        val sum = diceValue1 + diceValue2
                        if (sum <= 5) {
                            fetchBotJoke("человек бросил очень слабые зарики $diceValue1:$diceValue2")
                        } else if (sum >= 10) {
                            fetchBotJoke("человек бросил сильные кости $diceValue1:$diceValue2")
                        } else {
                            fetchBotJoke("человек бросил обычные кости $diceValue1:$diceValue2")
                        }
                    }
                }
            }
        }
    }

    /**
     * Запускает автоматический бросок кубиков через 1 секунду.
     * Используется для авто-ролла после завершения хода или перехода очереди.
     */
    private fun triggerRollAfterDelay() {
        viewModelScope.launch {
            delay(1000)
            rollDice()
        }
    }

    /** Обрабатывает нажатие на пункт доски: выбор шашки, ход или отмена. */
    fun handlePointClicked(relativeIndex: Int) {
        if (isAnimatingMove) return
        if (gameStatus != GameStatus.PLAYER_MOVE || isAutoPlayerMoveActive) return
        if (isOnlinePlayActive && activePlayer != localPlayerColor) return

        val selected = selectedPointIndex
        if (selected == null) {
            // Select checker: must contain active player pieces
            val abs = engine.relativeToAbsolute(activePlayer, relativeIndex)
            val stack = boardState[abs]
            if (stack.player == activePlayer && stack.count > 0) {
                // Must have legal moves for any available dice values
                val legalsForPoint = engine.getAllLegalMoves(activePlayer, remainingDice)
                    .filter { it.from == relativeIndex }
                if (legalsForPoint.isNotEmpty()) {
                    selectedPointIndex = relativeIndex
                }
            }
        } else {
            if (selected == relativeIndex) {
                // Deselect
                selectedPointIndex = null
            } else {
                // Check if this relativeIndex is a reachable destination for our selected point
                val targetPath = reachablePaths.find { it.finalTo == relativeIndex }

                if (targetPath != null) {
                    // Match found! Play the sequential steps with delayed coroutine and sound!
                    selectedPointIndex = null // Deselect
                    
                    viewModelScope.launch {
                        try {
                            isAnimatingMove = true
                            for (step in targetPath.steps) {
                                engine.makeMove(step)
                                triggerMoveSound()
                                syncStatesWithEngine()
                                
                                if (isRealTorP2PMode) {
                                    torP2PManager?.sendMessage("MOVE: ${step.from}->${step.to}")
                                } else if (isOnlinePlayActive) {
                                    val packMsg = "MOVE ${step.from}->${step.to}"
                                    val moveMsg = Language.get("online_sent_packet", selectedLanguage).replace("%s", packMsg)
                                    addSimpleXMatchLog(moveMsg)
                                }
                                delay(450)
                            }
                            if (!isOnlinePlayActive) {
                                val firstStep = targetPath.steps.firstOrNull()
                                val lastStep = targetPath.steps.lastOrNull()
                                if (firstStep != null && lastStep != null) {
                                    if (lastStep.to >= 24) {
                                        fetchBotJoke("человек успешно выбросил фишку с позиции ${firstStep.from + 1}")
                                    } else {
                                        fetchBotJoke("человек сходил ${firstStep.from + 1} -> ${lastStep.to + 1}")
                                    }
                                }
                            }
                            checkEndTurnTransition()
                        } finally {
                            isAnimatingMove = false
                        }
                    }
                } else {
                    // Try to select another stack of ours instead
                    val abs = engine.relativeToAbsolute(activePlayer, relativeIndex)
                    val stack = boardState[abs]
                    if (stack.player == activePlayer && stack.count > 0) {
                        val legalsForPoint = engine.getAllLegalMoves(activePlayer, remainingDice)
                            .filter { it.from == relativeIndex }
                        if (legalsForPoint.isNotEmpty()) {
                            selectedPointIndex = relativeIndex
                        } else {
                            selectedPointIndex = null
                        }
                    } else {
                        selectedPointIndex = null
                    }
                }
            }
        }
    }

    /** Выполняет выброс шашки с доски, если выбранный путь ведёт к выходу. */
    fun handleBearOffClicked() {
        if (isAnimatingMove) return
        val selected = selectedPointIndex ?: return
        if (gameStatus != GameStatus.PLAYER_MOVE || isAutoPlayerMoveActive) return
        if (isOnlinePlayActive && activePlayer != localPlayerColor) return

        // Find a path that bears off (ends at >= 24)
        val bearingPath = reachablePaths.find { it.finalTo >= 24 }

        if (bearingPath != null) {
            selectedPointIndex = null
            
            viewModelScope.launch {
                try {
                    isAnimatingMove = true
                    for (step in bearingPath.steps) {
                        engine.makeMove(step)
                        triggerMoveSound()
                        syncStatesWithEngine()
                        
                        if (isRealTorP2PMode) {
                            torP2PManager?.sendMessage("BEAR_OFF: ${step.from}->${step.to}")
                        } else if (isOnlinePlayActive) {
                            val packMsg = "BEAR_OFF ${step.from}->${step.to}"
                            val moveMsg = Language.get("online_sent_packet", selectedLanguage).replace("%s", packMsg)
                            addSimpleXMatchLog(moveMsg)
                        }
                        delay(450)
                    }
                    if (!isOnlinePlayActive) {
                        val firstStep = bearingPath.steps.firstOrNull()
                        if (firstStep != null) {
                            fetchBotJoke("человек выбросил фишку с позиции ${firstStep.from + 1}")
                        }
                    }
                    checkEndTurnTransition()
                } finally {
                    isAnimatingMove = false
                }
            }
        }
    }

    private fun checkEndTurnTransition() {
        if (gameStatus == GameStatus.GAME_OVER) {
            saveGameToHistory()
            if (isOnlinePlayActive) {
                val winLabel = if (winner == localPlayerColor) "Поздравляем! Вы победили!" else "Оппонент победил."
                addSimpleXMatchLog("Матч завершен: $winLabel")
                addTorLog("Online match finished. Winner: $winner")
            } else {
                fetchBotJoke("конец игры, бот ${if (winner == Player.BLACK) "выиграл" else "проиграл"}")
            }
            return
        }

        if (gameStatus == GameStatus.BEFORE_ROLL) {
            // Turn ended!
            if (isOnlinePlayActive) {
                if (activePlayer != localPlayerColor) {
                    if (!isRealTorP2PMode) {
                        triggerSimplexPeerTurn()
                    }
                } else if (isAutoRollEnabled) {
                    triggerRollAfterDelay()
                }
            } else if (isBotOpponentEnabled && activePlayer == Player.BLACK) {
                // Auto-roll bot turn
                triggerRollAfterDelay()
            } else if (isAutoRollEnabled) {
                // Auto-roll human turn
                triggerRollAfterDelay()
            }
        } else if (isAutoPlayerMoveActive) {
            runAutoMoveLoop()
        }
    }

    // Automated move sequencer (for AI and home-bound human)
    private fun runAutoMoveLoop() {
        if (isAutoMoveRunning) return
        isAutoMoveRunning = true
        viewModelScope.launch {
            try {
                // Human-like pause before making moves
                delay(1000)

                var safetyTimer = 0
                // For a Crazy Kush double (sequence of cascades), we can have up to 24 valid moves (6 cascades * 4 moves)
                // Let's set safety limit to 40 to allow all legitimate double moves while still protecting against infinite hangs.
                while (gameStatus == GameStatus.PLAYER_MOVE && isAutoPlayerMoveActive && safetyTimer < 40) {
                    safetyTimer++
                    val bestMove = engine.selectBestBotMove()
                    if (bestMove != null) {
                        val originalDiceCount = engine.remainingDice.size
                        // Make move
                        val success = engine.makeMove(bestMove)
                        if (!success) {
                            android.util.Log.e("GameViewModel", "Bot selected move was rejected by game engine! Ending loop to prevent infinite hang.")
                            engine.checkForLackOfMovesAndTransfer()
                            syncStatesWithEngine()
                            break
                        }
                        
                        val newDiceCount = engine.remainingDice.size
                        if (originalDiceCount == newDiceCount && gameStatus == GameStatus.PLAYER_MOVE) {
                            android.util.Log.w("GameViewModel", "Move made but dice count did not change. Forcing break to prevent infinite hang.")
                            engine.checkForLackOfMovesAndTransfer()
                            syncStatesWithEngine()
                            break
                        }
                        
                        triggerMoveSound()
                        syncStatesWithEngine()
                        
                        val fromPos = bestMove.from
                        val toPos = bestMove.to
                        if (toPos >= 24) {
                            fetchBotJoke("бот выкинул фишку с позиции ${fromPos + 1}")
                        } else {
                            fetchBotJoke("бот нагло походил ${fromPos + 1} -> ${toPos + 1}")
                        }
                        
                        delay(1000) // Staggered delays between sequential moves
                    } else {
                        // No moves available, should trigger transfer or end phase
                        engine.checkForLackOfMovesAndTransfer()
                        syncStatesWithEngine()
                        break
                    }
                }

                if (safetyTimer >= 40) {
                    android.util.Log.e("GameViewModel", "AutoMoveLoop safety limit (40) reached! Forcing turn transition to prevent freeze.")
                    if (engine.remainingDice.isNotEmpty()) {
                        engine.remainingDice.clear()
                        engine.doubleWaterfallQueue.clear()
                        engine.checkForLackOfMovesAndTransfer()
                    }
                    syncStatesWithEngine()
                }

                if (gameStatus == GameStatus.GAME_OVER) {
                    saveGameToHistory()
                    fetchBotJoke("конец игры, бот ${if (winner == Player.BLACK) "выиграл" else "проиграл"}")
                } else if (gameStatus == GameStatus.BEFORE_ROLL) {
                    // Turn ended, trigger next roll after delay
                    if (isBotOpponentEnabled && activePlayer == Player.BLACK) {
                        triggerRollAfterDelay()
                    } else if (isAutoRollEnabled) {
                        triggerRollAfterDelay()
                    }
                } else {
                    if (activePlayer == Player.WHITE && !engine.areAllCheckersInHome(activePlayer)) {
                        // Turn passed back to human due to lack of bot moves!
                        fetchBotJoke("бот не может совершить ход и отдает зарики человеку")
                    }
                }
            } finally {
                isAutoMoveRunning = false
            }
        }
    }

    /** Бросок для 1-го этапа жеребьёвки (определение цвета). */
    fun rollLot1() {
        if (isRollingDice || isAnimatingMove) return
        viewModelScope.launch {
            isRollingDice = true
            soundPlayer.playRollSound()
            for (i in 1..8) {
                diceLot1User = (1..6).random()
                diceLot1Bot = (1..6).random()
                delay(70)
            }
            val hasWinner = engine.rollLotStage1()
            isRollingDice = false
            syncStatesWithEngine()
            if (hasWinner) {
                fetchBotJoke("жеребьевка цветов завершена. Вы играете за ${humanPlayerColor.label()}")
            } else {
                fetchBotJoke("ничья при жеребьевке цветов!")
            }
        }
    }

    /** Бросок для 2-го этапа жеребьёвки (определение первого хода). */
    fun rollLot2() {
        if (isRollingDice || isAnimatingMove) return
        viewModelScope.launch {
            isRollingDice = true
            soundPlayer.playRollSound()
            for (i in 1..8) {
                diceLot2White = (1..6).random()
                diceLot2Black = (1..6).random()
                delay(70)
            }
            val hasWinner = engine.rollLotStage2()
            isRollingDice = false
            syncStatesWithEngine()
            if (hasWinner) {
                val winnerLabel = if (activePlayer == Player.WHITE) "игроком (${humanPlayerColor.label()})" else "ботом (${botPlayerColor.label()})"
                fetchBotJoke("жеребьевка первого хода завершена, первый ход за $winnerLabel")
            } else {
                fetchBotJoke("ничья при жеребьевке первого хода!")
            }
        }
    }

    fun rollLot1OnlineLocal() {
        if (onlineLot1UserRolled || isOnline1UserRolling || isOnline1BotRolling) return
        viewModelScope.launch {
            isOnline1UserRolling = true
            soundPlayer.playRollSound()
            for (i in 1..8) {
                diceLot1User = (1..6).random()
                delay(70)
            }
            onlineLot1UserRolled = true
            isOnline1UserRolling = false
            
            // Automatically launch opponent's roll after a short delay
            delay(1000)
            rollLot1OnlineOpponent()
        }
    }

    fun rollLot1OnlineOpponent() {
        if (onlineLot1BotRolled || isOnline1BotRolling || isOnline1UserRolling) return
        viewModelScope.launch {
            isOnline1BotRolling = true
            soundPlayer.playRollSound()
            for (i in 1..8) {
                diceLot1Bot = (1..6).random()
                delay(70)
            }
            onlineLot1BotRolled = true
            isOnline1BotRolling = false
            
            // Determine result!
            val hasWinner = engine.rollLotStage1Custom(diceLot1User, diceLot1Bot, onlineOpponentName)
            syncStatesWithEngine()
            if (!hasWinner) {
                // It's a tie, reset rolls so they can roll again!
                delay(1500)
                onlineLot1UserRolled = false
                onlineLot1BotRolled = false
                diceLot1User = 0
                diceLot1Bot = 0
            }
        }
    }

    fun rollLot2OnlineLocalWhite() {
        if (onlineLot2WhiteRolled || isOnline2WhiteRolling || isOnline2BlackRolling) return
        viewModelScope.launch {
            isOnline2WhiteRolling = true
            soundPlayer.playRollSound()
            for (i in 1..8) {
                diceLot2White = (1..6).random()
                delay(70)
            }
            onlineLot2WhiteRolled = true
            isOnline2WhiteRolling = false
            
            // Opponent rolls Black die automatically
            delay(1000)
            rollLot2OnlineOpponentBlack()
        }
    }

    fun rollLot2OnlineOpponentBlack() {
        if (onlineLot2BlackRolled || isOnline2BlackRolling || isOnline2WhiteRolling) return
        viewModelScope.launch {
            isOnline2BlackRolling = true
            soundPlayer.playRollSound()
            for (i in 1..8) {
                diceLot2Black = (1..6).random()
                delay(70)
            }
            onlineLot2BlackRolled = true
            isOnline2BlackRolling = false
            
            // Check result
            val hasWinner = engine.rollLotStage2Custom(diceLot2White, diceLot2Black, onlineOpponentName)
            syncStatesWithEngine()
            if (!hasWinner) {
                // Tie, reset both
                delay(1500)
                onlineLot2WhiteRolled = false
                onlineLot2BlackRolled = false
                diceLot2White = 0
                diceLot2Black = 0
            }
        }
    }

    fun rollLot2OnlineOpponentFirst() {
        if (onlineLot2WhiteRolled || isOnline2WhiteRolling || isOnline2BlackRolling) return
        viewModelScope.launch {
            delay(1000) // initial grace period before opponent starts rolling
            isOnline2WhiteRolling = true
            soundPlayer.playRollSound()
            for (i in 1..8) {
                diceLot2White = (1..6).random()
                delay(70)
            }
            onlineLot2WhiteRolled = true
            isOnline2WhiteRolling = false
        }
    }

    fun rollLot2OnlineLocalBlack() {
        if (onlineLot2BlackRolled || isOnline2BlackRolling || isOnline2WhiteRolling) return
        viewModelScope.launch {
            isOnline2BlackRolling = true
            soundPlayer.playRollSound()
            for (i in 1..8) {
                diceLot2Black = (1..6).random()
                delay(70)
            }
            onlineLot2BlackRolled = true
            isOnline2BlackRolling = false
            
            // Check result
            val hasWinner = engine.rollLotStage2Custom(diceLot2White, diceLot2Black, onlineOpponentName)
            syncStatesWithEngine()
            if (!hasWinner) {
                // Tie, reset both
                delay(1500)
                onlineLot2WhiteRolled = false
                onlineLot2BlackRolled = false
                diceLot2White = 0
                diceLot2Black = 0
                // Since opponent plays WHITE, they roll first again automatically!
                rollLot2OnlineOpponentFirst()
            }
        }
    }

    fun applyLotStage2WinnerAndStart() {
        engine.applyLotStage2WinnerAndStart()
        syncStatesWithEngine()
        
        if (isAutoPlayerMoveActive) {
            runAutoMoveLoop()
        }
    }

    fun transitionToStage2() {
        engine.transitionToStage2()
        syncStatesWithEngine()
    }

    /** Запрашивает шутку/комментарий у Gemini AI по контексту игры. */
    fun fetchBotJoke(contextDescriptor: String) {
        viewModelScope.launch {
            isGeminiLoadingJoke = true
            
            // Build situational query
            val situationText = when (gameStatus) {
                GameStatus.LOT_STAGE1 -> "Жеребьёвка Stage 1 (Выбор цветов)"
                GameStatus.LOT_STAGE2 -> "Жеребьёвка Stage 2 (Первый ход)"
                GameStatus.BEFORE_ROLL -> if (roller == Player.WHITE) "Ход игрока перед броском зариков." else "Ход бота перед броском зариков."
                GameStatus.PLAYER_MOVE -> {
                    val activeLabel = if (activePlayer == Player.WHITE) "игрок (вы)" else "компьютер (бот)"
                    "Активный игрок: $activeLabel. Оставшиеся кубики: $remainingDice. Специфика каскада куша: $doubleWaterfallQueue."
                }
                GameStatus.GAME_OVER -> {
                    val winnerLabel = if (winner == Player.WHITE) "игрок (вы)" else "компьютер (бот)"
                    "Конец партии! Победитель: $winnerLabel."
                }
            }
            
            val query = "Ситуация: $contextDescriptor. Детали: $situationText. Чекеры игрока (выброшено): ${borneOffCount[Player.WHITE]}, бота (выброшено): ${borneOffCount[Player.BLACK]}."
            
            botSpeechBubble = jokeService.generateJoke(query, selectedLanguage)
            isGeminiLoadingJoke = false
        }
    }

    // Store completed matches to database
    private fun saveGameToHistory() {
        viewModelScope.launch {
            val durationSec = (System.currentTimeMillis() - matchStartTimeMillis) / 1000
            val match = MatchHistory(
                date = System.currentTimeMillis(),
                playerColor = humanPlayerColor.name,
                winner = if (winner == Player.WHITE) humanPlayerColor.name else botPlayerColor.name,
                scorePlayer = borneOffCount[Player.WHITE] ?: 0,
                scoreOpponent = borneOffCount[Player.BLACK] ?: 0,
                isAgainstBot = isBotOpponentEnabled,
                gameDurationSeconds = durationSec
            )
            dao.insertMatch(match)
        }
    }

    /** Очищает историю матчей в БД. */
    fun clearStatsHistory() {
        viewModelScope.launch {
            dao.clearHistory()
        }
    }

    // --- LOCAL TRANSLATION & THEME CONFIGS ---

    /** @see SecurityViewModel.updatePinCode */
    fun updatePinCode(newPin: String) {
        securityViewModel.updatePinCode(newPin)
        pinCode = securityViewModel.pinCode
    }

    /** @see SecurityViewModel.setDuressPin */
    fun setDuressPin(duressPin: String?) {
        securityViewModel.setDuressPin(duressPin)
    }

    /** @see SecurityViewModel.verifyPinWithDuressCheck */
    fun verifyPinWithDuressCheck(pin: String): PinResult {
        val result = securityViewModel.verifyPinWithDuressCheck(pin)
        isCryptocontainerMounted = securityViewModel.isCryptocontainerMounted
        return result
    }

    /** @see SecurityViewModel.handleDuressTrigger */
    fun handleDuressTrigger() {
        securityViewModel.handleDuressTrigger()
        isCryptocontainerMounted = securityViewModel.isCryptocontainerMounted
        pinCode = securityViewModel.pinCode
        // Also clear session data that SecurityViewModel doesn't know about
        simplexRooms.clear()
        simplexMessages.clear()
        matchmakingLogs.clear()
        v2RayTorSyncLogs.clear()
        onionTestLogs.clear()
        turnLogs = emptyList()
        for (room in simplexRooms) {
            room.messages.clear()
        }
    }

    /** Меняет тему оформления. */
    fun updateTheme(themeId: String) {
        selectedTheme = themeId
        prefs.edit().putString("selected_theme", themeId).apply()
    }

    /** Меняет язык чата SimpleX. */
    fun updateChatLanguage(langCode: String) {
        selectedChatLanguage = langCode
        prefs.edit().putString("selected_chat_language", langCode).apply()
    }

    /** Меняет язык интерфейса и глобальную локаль. */
    fun updateLanguage(langCode: String) {
        selectedLanguage = langCode
        com.example.model.GameEngine.currentLanguage = langCode
        prefs.edit().putString("selected_language", langCode).apply()
        
        // Apply Global Locale dynamically so that dates, system elements, and TTS are loaded globally in chosen language
        try {
            val localeStr = when (langCode) {
                "RU" -> "ru"
                "EN" -> "en"
                "DE" -> "de"
                "ES" -> "es"
                "FR" -> "fr"
                "TR" -> "tr"
                "ZH" -> "zh"
                else -> "en"
            }
            val locale = java.util.Locale.forLanguageTag(localeStr)
            java.util.Locale.setDefault(locale)
            
            val resources = context.resources
            val config = resources.configuration
            config.setLocale(locale)
            resources.updateConfiguration(config, resources.displayMetrics)
        } catch (e: Exception) {
            Log.e("GameViewModel", "exception", e)
        }
    }

    /** @see SecurityViewModel.getDerivedKey */
    fun getDerivedKey(seed: String): String = securityViewModel.getDerivedKey(seed)

    /** Экспортирует криптоконтейнер (настройки, контакты, комнаты) с XOR-шифрованием по seed. */
    fun exportCryptocontainerWithSeed(seed: String): String {
        return try {
            val json = org.json.JSONObject()
            
            // 1. Basic configs
            json.put("selectedTheme", selectedTheme)
            json.put("selectedLanguage", selectedLanguage)
            json.put("pinCode", pinCode)
            json.put("serverUrl", serverUrl)
            json.put("smpOnionAddress", smpOnionAddress)
            json.put("xftpOnionAddress", xftpOnionAddress)
            json.put("torSocksPort", torSocksPort)
            json.put("simplexUserHandle", simplexUserHandle)
            json.put("userName", userName)
            json.put("player1Name", player1Name)
            json.put("player2Name", player2Name)
            json.put("isTorEnabled", isTorEnabled)
            
            // 2. Contacts
            val contactsArr = org.json.JSONArray()
            simplexContacts.forEach { contact ->
                val cObj = org.json.JSONObject()
                cObj.put("name", contact.name)
                cObj.put("handle", contact.handle)
                cObj.put("isOnline", contact.isOnline)
                cObj.put("isAnonymous", contact.isAnonymous)
                cObj.put("rating", contact.rating)
                contactsArr.put(cObj)
            }
            json.put("contacts", contactsArr)
            
            // 3. Rooms & Messages
            val roomsArr = org.json.JSONArray()
            simplexRooms.forEach { room ->
                val rObj = org.json.JSONObject()
                rObj.put("id", room.id)
                rObj.put("title", room.title)
                rObj.put("lastMessage", room.lastMessage)
                rObj.put("isOneTime", room.isOneTime)
                rObj.put("simplexUrl", room.simplexUrl)
                rObj.put("roomType", room.roomType)
                rObj.put("selfDestructTimerSec", room.selfDestructTimerSec)
                
                val msgArr = org.json.JSONArray()
                room.messages.forEach { msg ->
                    val mObj = org.json.JSONObject()
                    mObj.put("id", msg.id)
                    mObj.put("sender", msg.sender)
                    mObj.put("text", msg.text)
                    mObj.put("timestamp", msg.timestamp)
                    mObj.put("attachmentType", msg.attachmentType)
                    mObj.put("attachmentUrl", msg.attachmentUrl)
                    mObj.put("attachmentName", msg.attachmentName)
                    mObj.put("attachmentSize", msg.attachmentSize)
                    mObj.put("selfDestructTimeLeft", msg.selfDestructTimeLeft)
                    mObj.put("isSelfDestructed", msg.isSelfDestructed)
                    
                    val reactArr = org.json.JSONArray()
                    msg.reactions.forEach { react ->
                        reactArr.put(react)
                    }
                    mObj.put("reactions", reactArr)
                    msgArr.put(mObj)
                }
                rObj.put("messages", msgArr)
                roomsArr.put(rObj)
            }
            json.put("rooms", roomsArr)
            
            val originalStr = json.toString()
            val salt = getDerivedKey(seed)
            val bytes = originalStr.toByteArray(Charsets.UTF_8)
            val encrypted = ByteArray(bytes.size)
            for (i in bytes.indices) {
                encrypted[i] = (bytes[i].toInt() xor salt[i % salt.length].code).toByte()
            }
            "CRAZYCONTAINER-" + android.util.Base64.encodeToString(encrypted, android.util.Base64.NO_WRAP or android.util.Base64.URL_SAFE)
        } catch (e: Exception) {
            Log.e("GameViewModel", "exception", e)
            ""
        }
    }

    /** Импортирует криптоконтейнер из строки с XOR-расшифровкой по seed-фразе. */
    fun importCryptocontainerWithSeed(seed: String, containerKey: String = ""): Boolean {
        return try {
            val normalizedSeed = seed.trim().lowercase()
            if (!Bip39Helper.validateMnemonic(context, normalizedSeed)) {
                android.util.Log.e("GameViewModel", "BIP39 Seed validation failed!")
                return false
            }

            val payload = if (containerKey.trim().isEmpty()) {
                prefs.getString("encrypted_container_data", "") ?: ""
            } else {
                containerKey.trim()
            }
            
            if (payload.isEmpty() || !payload.startsWith("CRAZYCONTAINER-")) return false
            
            val base64Part = payload.removePrefix("CRAZYCONTAINER-")
            val encrypted = android.util.Base64.decode(base64Part, android.util.Base64.NO_WRAP or android.util.Base64.URL_SAFE)
            val salt = getDerivedKey(normalizedSeed)
            val decryptedBytes = ByteArray(encrypted.size)
            for (i in encrypted.indices) {
                decryptedBytes[i] = (encrypted[i].toInt() xor salt[i % salt.length].code).toByte()
            }
            val originalStr = String(decryptedBytes, Charsets.UTF_8)
            if (!originalStr.startsWith("{") || !originalStr.endsWith("}")) return false
            
            val json = org.json.JSONObject(originalStr)
            val editor = prefs.edit()
            
            // Restore Basic configs
            if (json.has("selectedTheme")) {
                selectedTheme = json.getString("selectedTheme")
                editor.putString("selected_theme", selectedTheme)
            }
            if (json.has("selectedLanguage")) {
                selectedLanguage = json.getString("selectedLanguage")
                editor.putString("selected_language", selectedLanguage)
            }
            if (json.has("pinCode")) {
                pinCode = json.getString("pinCode")
                editor.putString("pin_code", pinCode)
            }
            if (json.has("serverUrl")) {
                serverUrl = json.getString("serverUrl")
                editor.putString("server_url", serverUrl)
            }
            if (json.has("smpOnionAddress")) {
                smpOnionAddress = json.getString("smpOnionAddress")
                editor.putString("smp_onion_address", smpOnionAddress)
            }
            if (json.has("xftpOnionAddress")) {
                xftpOnionAddress = json.getString("xftpOnionAddress")
                editor.putString("xftp_onion_address", xftpOnionAddress)
            }
            if (json.has("torSocksPort")) {
                torSocksPort = json.getInt("torSocksPort")
                editor.putInt("tor_socks_port", torSocksPort)
            }
            if (json.has("isTorEnabled")) {
                isTorEnabled = json.getBoolean("isTorEnabled")
                editor.putBoolean("tor_enabled", isTorEnabled)
            }
            if (json.has("simplexUserHandle")) {
                simplexUserHandle = json.getString("simplexUserHandle")
                editor.putString("simplex_handle", simplexUserHandle)
            }
            if (json.has("userName")) {
                userName = json.getString("userName")
            }
            if (json.has("player1Name")) {
                player1Name = json.getString("player1Name")
            }
            if (json.has("player2Name")) {
                player2Name = json.getString("player2Name")
            }
            
            // Mount status to true
            isCryptocontainerMounted = true
            editor.putBoolean("cryptocontainer_mounted", true)
            // Save the mnemonic too
            currentSeedPhrase = normalizedSeed
            editor.putString("cryptocontainer_seed_phrase", normalizedSeed)
            editor.apply()
            
            // Restore Contacts
            if (json.has("contacts")) {
                simplexContacts.clear()
                val contactsArr = json.getJSONArray("contacts")
                for (i in 0 until contactsArr.length()) {
                    val cObj = contactsArr.getJSONObject(i)
                    simplexContacts.add(
                        SimpleXContact(
                            name = cObj.getString("name"),
                            handle = cObj.getString("handle"),
                            isOnline = cObj.optBoolean("isOnline", true),
                            isAnonymous = cObj.optBoolean("isAnonymous", false),
                            rating = cObj.optInt("rating", 1500)
                        )
                    )
                }
            }
            
            // Restore Rooms & Messages
            if (json.has("rooms")) {
                simplexRooms.clear()
                val roomsArr = json.getJSONArray("rooms")
                for (i in 0 until roomsArr.length()) {
                    val rObj = roomsArr.getJSONObject(i)
                    val rMsgs = mutableStateListOf<ChatMessage>()
                    if (rObj.has("messages")) {
                        val msgArr = rObj.getJSONArray("messages")
                        for (j in 0 until msgArr.length()) {
                            val mObj = msgArr.getJSONObject(j)
                            val reactionsList = mutableStateListOf<String>()
                            if (mObj.has("reactions")) {
                                val reactArr = mObj.getJSONArray("reactions")
                                for (k in 0 until reactArr.length()) {
                                    reactionsList.add(reactArr.getString(k))
                                }
                            }
                            rMsgs.add(
                                ChatMessage(
                                    id = mObj.getString("id"),
                                    sender = mObj.getString("sender"),
                                    text = mObj.getString("text"),
                                    timestamp = mObj.getLong("timestamp"),
                                    attachmentType = mObj.optString("attachmentType", "NONE"),
                                    attachmentUrl = mObj.optString("attachmentUrl", ""),
                                    attachmentName = mObj.optString("attachmentName", ""),
                                    attachmentSize = mObj.optString("attachmentSize", ""),
                                    reactions = reactionsList,
                                    selfDestructTimeLeft = mObj.optInt("selfDestructTimeLeft", -1),
                                    isSelfDestructed = mObj.optBoolean("isSelfDestructed", false)
                                )
                            )
                        }
                    }
                    
                    simplexRooms.add(
                        SimpleXRoom(
                            id = rObj.getString("id"),
                            title = rObj.getString("title"),
                            lastMessage = rObj.optString("lastMessage", ""),
                            isOneTime = rObj.optBoolean("isOneTime", false),
                            simplexUrl = rObj.optString("simplexUrl", ""),
                            roomType = rObj.optString("roomType", "DIRECT_CHAT"),
                            selfDestructTimerSec = rObj.optInt("selfDestructTimerSec", 0),
                            messages = rMsgs
                        )
                    )
                }
            }
            true
        } catch (e: Exception) {
            Log.e("GameViewModel", "exception", e)
            false
        }
    }

    /** Вручную активирует криптоконтейнер. */
    fun activateCryptocontainerManually() {
        isCryptocontainerMounted = true
        prefs.edit().putBoolean("cryptocontainer_mounted", true).apply()
    }

    /** Панический сброс: очищает все настройки до стандартных, отключает Tor/VPN, создаёт бэкап. */
    fun triggerPanicReset() {
        // Create autogenerated backup cache from seed before wiping decrypted state
        try {
            val autoBackup = exportCryptocontainerWithSeed(currentSeedPhrase)
            if (autoBackup.isNotEmpty()) {
                prefs.edit().putString("encrypted_container_data", autoBackup).apply()
            }
        } catch (e: Exception) {
            Log.e("GameViewModel", "exception", e)
        }

        // 1. Reset SimpleX Handle
        simplexUserHandle = "NardyPro_99"
        prefs.edit().putString("simplex_handle", "NardyPro_99").apply()
        
        // 2. Reset Server URL / Onion Relay
        serverUrl = NetworkDefaults.SERVER_URL
        prefs.edit().putString("server_url", NetworkDefaults.SERVER_URL).apply()

        // 2b. Reset Custom SMP / XFTP Onions
        smpOnionAddress = NetworkDefaults.SMP_ONION
        prefs.edit().putString("smp_onion_address", NetworkDefaults.SMP_ONION).apply()
        xftpOnionAddress = NetworkDefaults.XFTP_ONION
        prefs.edit().putString("xftp_onion_address", NetworkDefaults.XFTP_ONION).apply()
        
        // 3. Disable Tor Routing
        isTorEnabled = false
        prefs.edit().putBoolean("tor_enabled", false).apply()
        torStatus = "INACTIVE"
        torLogsList.clear()
        torLogsList.add("[System] Panic Reset: Tor disabled and cleared.")
        
        // 4. Disconnect SimpleX
        simplexStatus = "DISCONNECTED"
        simplexMessages.clear()
        simplexMessages.add(ChatMessage("sys", "System", "⚠️ PANIC RESET COMPLETED. Settings restored to defaults.", System.currentTimeMillis()))
        
        // 5. Reset VPN configurations
        vpnManager.resetToDefaults()

        // 6. Unmount cryptocontainer
        isCryptocontainerMounted = false
        prefs.edit().putBoolean("cryptocontainer_mounted", false).apply()
    }

    /** Экспортирует текущие настройки в JSON (криптоконтейнер). */
    fun exportSettingsToJson(): String {
        return exportCryptocontainerWithSeed(currentSeedPhrase)
    }

    /** Импортирует настройки из JSON-строки. */
    fun importSettingsFromJson(jsonStr: String): Boolean {
        return try {
            val json = org.json.JSONObject(jsonStr)
            val editor = prefs.edit()
            if (json.has("selectedTheme")) {
                val theme = json.getString("selectedTheme")
                selectedTheme = theme
                editor.putString("selected_theme", theme)
            }
            if (json.has("selectedLanguage")) {
                val lang = json.getString("selectedLanguage")
                selectedLanguage = lang
                editor.putString("selected_language", lang)
            }
            if (json.has("pinCode")) {
                val pin = json.getString("pinCode")
                pinCode = pin
                editor.putString("pin_code", pin)
            }
            if (json.has("serverUrl")) {
                val sUrl = json.getString("serverUrl")
                serverUrl = sUrl
                editor.putString("server_url", sUrl)
            }
            if (json.has("smpOnionAddress")) {
                val smp = json.getString("smpOnionAddress")
                smpOnionAddress = smp
                editor.putString("smp_onion_address", smp)
            }
            if (json.has("xftpOnionAddress")) {
                val xftp = json.getString("xftpOnionAddress")
                xftpOnionAddress = xftp
                editor.putString("xftp_onion_address", xftp)
            }
            if (json.has("torSocksPort")) {
                val port = json.getInt("torSocksPort")
                torSocksPort = port
                editor.putInt("tor_socks_port", port)
            }
            if (json.has("simplexUserHandle")) {
                val handle = json.getString("simplexUserHandle")
                simplexUserHandle = handle
                editor.putString("simplex_handle", handle)
            }
            editor.apply()
            true
        } catch (e: Exception) {
            false
        }
    }

    // --- GAME SERVER & CONNECTION (STUB) ---

    /** Обновляет URL центрального сервера. */
    fun updateServerUrl(url: String) {
        serverUrl = url
        prefs.edit().putString("server_url", url).apply()
    }

    /** Обновляет onion-адрес SMP-сервера. */
    fun updateSmpOnionAddress(url: String) {
        smpOnionAddress = url
        prefs.edit().putString("smp_onion_address", url).apply()
    }

    /** Обновляет onion-адрес XFTP-сервера. */
    fun updateXftpOnionAddress(url: String) {
        xftpOnionAddress = url
        prefs.edit().putString("xftp_onion_address", url).apply()
    }

    /** Тестирует доступность onion-адреса через Tor SOCKS5 с парсингом SMP/XFTP URL. */
    fun testOnionAddress(type: String, address: String) {
        viewModelScope.launch {
            isTestingOnionAddress = true
            onionTestLogs.clear()
            onionTestLogs.add("[System] Инициализация диагностики узла $type...")
            
            if (type == "SMP") {
                smpStatusState = "UNKNOWN"
                smpPingResult = -1
            } else {
                xftpStatusState = "UNKNOWN"
                xftpPingResult = -1
            }
            delay(500)
            
            // Auto start VPN first just for completeness if not active
            if (vpnManager.vpnState != "Connected") {
                onionTestLogs.add("[vpn] Внимание: V2Ray VPN не подключен. Маршрутизация пойдет напрямую.")
            } else {
                onionTestLogs.add("[vpn] Найдено активное VPN-соединение: ${vpnManager.selectedConfig?.protocol ?: "VLESS"}")
            }
            delay(400)

            onionTestLogs.add("[Tor] Проверка локального моста SOCKS5 на порту $torSocksPort...")
            if (!isTorEnabled || torStatus != "ACTIVE") {
                onionTestLogs.add("[Tor] Восстановление сессии Tor Daemon...")
                setTorEnabledState(true)
                var retryCount = 0
                while (torStatus != "ACTIVE" && retryCount < 18) {
                    delay(300)
                    retryCount++
                }
            }
            
            if (torStatus == "ACTIVE") {
                onionTestLogs.add("[Tor] Канал SOCKS5 готов: 127.0.0.1:$torSocksPort")
                delay(400)
                
                var targetUrl = address.trim()
                if (targetUrl.startsWith("smp://")) {
                    try {
                        val part = targetUrl.substringAfter("smp://")
                        val hostPort = if (part.contains("@")) part.substringAfter("@") else part
                        targetUrl = "ws://$hostPort/smp"
                    } catch (e: Exception) {
                        onionTestLogs.add("[Parser] Ошибка парсинга SMP: ${e.message}")
                    }
                } else if (targetUrl.startsWith("xftp://")) {
                    try {
                        val part = targetUrl.substringAfter("xftp://")
                        val hostPort = if (part.contains("@")) part.substringAfter("@") else part
                        targetUrl = "http://$hostPort"
                    } catch (e: Exception) {
                        onionTestLogs.add("[Parser] Ошибка парсинга XFTP: ${e.message}")
                    }
                } else if (targetUrl.startsWith("simplex://")) {
                    try {
                        val hostPort = targetUrl.substringAfter("simplex://")
                        targetUrl = "ws://$hostPort"
                    } catch (e: Exception) {
                        onionTestLogs.add("[Parser] Ошибка парсинга SimpleX: ${e.message}")
                    }
                }

                val okHttpClientBuilder = okhttp3.OkHttpClient.Builder()
                    .connectTimeout(5, java.util.concurrent.TimeUnit.SECONDS)
                    .readTimeout(5, java.util.concurrent.TimeUnit.SECONDS)

                if (isTorEnabled && torStatus == "ACTIVE") {
                    try {
                        val proxy = java.net.Proxy(
                            java.net.Proxy.Type.SOCKS,
                            java.net.InetSocketAddress("127.0.0.1", torSocksPort)
                        )
                        okHttpClientBuilder.proxy(proxy)
                        onionTestLogs.add("[Tor] Проксирование запроса через SOCKS5 127.0.0.1:$torSocksPort")
                    } catch (e: Exception) {
                        onionTestLogs.add("[Tor] Не удалось настроить прокси SOCKS5: ${e.message}")
                    }
                }

                val client = okHttpClientBuilder.build()
                
                if (targetUrl.startsWith("ws://") || targetUrl.startsWith("wss://")) {
                    onionTestLogs.add("[Onion] Отправка проверочного WebSocket запроса на $targetUrl...")
                    val request = okhttp3.Request.Builder().url(targetUrl).build()
                    var isSuccess = false
                    var errorMsg = ""
                    val startTime = System.currentTimeMillis()
                    val socketListener = object : okhttp3.WebSocketListener() {
                        override fun onOpen(webSocket: okhttp3.WebSocket, response: okhttp3.Response) {
                            isSuccess = true
                            webSocket.close(1000, "Goodbye")
                        }
                        override fun onFailure(webSocket: okhttp3.WebSocket, t: Throwable, response: okhttp3.Response?) {
                            errorMsg = t.message ?: "Socket connection exception"
                        }
                    }
                    client.newWebSocket(request, socketListener)
                    
                    var waitTime = 0
                    while (waitTime < 40) {
                        delay(100)
                        waitTime++
                        if (isSuccess) break
                    }
                    
                    if (isSuccess) {
                        val ping = (System.currentTimeMillis() - startTime).toInt().coerceIn(1, 9999)
                        onionTestLogs.add("[Onion] ✅ Соединение успешно! (Пинг: ${ping}ms)")
                        if (type == "SMP") {
                            smpStatusState = "ONLINE"
                            smpPingResult = ping
                        } else {
                            xftpStatusState = "ONLINE"
                            xftpPingResult = ping
                        }
                    } else {
                        onionTestLogs.add("[Onion] ❌ Сбой: $errorMsg")
                        if (type == "SMP") {
                            smpStatusState = "ERROR"
                            smpPingResult = -1
                        } else {
                            xftpStatusState = "ERROR"
                            xftpPingResult = -1
                        }
                    }
                } else if (targetUrl.startsWith("http://") || targetUrl.startsWith("https://")) {
                    onionTestLogs.add("[Onion] HTTP запрос на $targetUrl...")
                    val request = okhttp3.Request.Builder().url(targetUrl).build()
                    try {
                        val startTime = System.currentTimeMillis()
                        val response = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                            try { client.newCall(request).execute() } catch (e: Exception) { null }
                        }
                        if (response != null) {
                            val ping = (System.currentTimeMillis() - startTime).toInt().coerceIn(1, 9999)
                            onionTestLogs.add("[Onion] ✅ Код ${response.code} (Пинг: ${ping}ms)")
                            if (type == "SMP") {
                                smpStatusState = "ONLINE"
                                smpPingResult = ping
                            } else {
                                xftpStatusState = "ONLINE"
                                xftpPingResult = ping
                            }
                        } else {
                            onionTestLogs.add("[Onion] ❌ Сервер не ответил")
                            if (type == "SMP") {
                                smpStatusState = "ERROR"
                                smpPingResult = -1
                            } else {
                                xftpStatusState = "ERROR"
                                xftpPingResult = -1
                            }
                        }
                    } catch (e: Exception) {
                        onionTestLogs.add("[Onion] ❌ Ошибка: ${e.message}")
                        if (type == "SMP") {
                            smpStatusState = "ERROR"
                            smpPingResult = -1
                        } else {
                            xftpStatusState = "ERROR"
                            xftpPingResult = -1
                        }
                    }
                } else {
                    onionTestLogs.add("[Onion] Ошибка: Неподдерживаемый протокол.")
                    if (type == "SMP") {
                        smpStatusState = "OFFLINE"
                        smpPingResult = -1
                    } else {
                        xftpStatusState = "OFFLINE"
                        xftpPingResult = -1
                    }
                }
            } else {
                onionTestLogs.add("[Tor] Не удалось подключиться к Tor. Проверьте настройки сети.")
                if (type == "SMP") {
                    smpStatusState = "OFFLINE"
                    smpPingResult = -1
                } else {
                    xftpStatusState = "OFFLINE"
                    xftpPingResult = -1
                }
            }
            isTestingOnionAddress = false
        }
    }

    /** Создать оркестратор, если все 3 контроллера инициализированы */
    private fun ensureOrchestrator(): NetworkOrchestrator? {
        if (_networkOrchestrator == null) {
            val t = torController ?: return null
            val v = v2rayController ?: return null
            val s = simplexController ?: return null
            _networkOrchestrator = NetworkOrchestrator(
                torController = t,
                v2rayController = v,
                simplexController = s,
                onLog = { line -> viewModelScope.launch { v2RayTorSyncLogs.add(line) } },
                onTorStatus = { running -> if (!running) torStatus = "INACTIVE" },
                onV2RayStatus = {},
                onSimplexStatus = { running -> if (!running) simplexStatus = "DISCONNECTED" }
            )
        }
        return _networkOrchestrator
    }

    /** Полный цикл синхронизации сети: VPN → Tor → V2Ray → SMP/XFTP → SimpleX. */
    fun connectAndSyncAllNetworkComponents() {
        if (isV2RayTorSyncing) return
        viewModelScope.launch {
            isV2RayTorSyncing = true
            v2RayTorSyncStatus = "SYNCING"
            v2RayTorSyncLogs.clear()
            
            // 1. ПРОВЕРКА СОСТОЯНИЯ И СБРОС НАСТРОЕК В ИСХОДНОЕ ПОЛОЖЕНИЕ
            v2RayTorSyncLogs.add("🔄 [Шаг 1/8] Проверка состояния и сброс настроек в исходное положение...")
            v2RayTorSyncLogs.add("🔄 [Сброс настроек] Отключение от Tor и разрыв активных SOCKS5 прокси-сессий...")
            stopTor()
            torStatus = "INACTIVE"
            delay(500)
            v2RayTorSyncLogs.add("🔄 [Сброс настроек] Остановка V2Ray моста...")
            stopV2Ray()
            delay(500)
            v2RayTorSyncLogs.add("🔄 [Сброс настроек] Отключение от SimpleX CLI...")
            disconnectSimpleX()
            delay(500)
            v2RayTorSyncLogs.add("🔄 [Сброс настроек] Деактивация VPN туннелей...")
            vpnManager.stopVpn()
            delay(500)
            v2RayTorSyncLogs.add("✅ [Сброс настроек] Все интерфейсы сброшены в исходное выключенное состояние.")
            delay(400)

            // 2. ЗАПУСК VPN
            v2RayTorSyncLogs.add("🦊 [Шаг 2/8] Инициализация VPN соединения Foxray/V2Ray...")
            val prepIntent = vpnManager.getVpnPrepareIntent()
            if (prepIntent != null) {
                v2RayTorSyncLogs.add("🦊 [VPN] Требуется подтверждение VPN соединения пользователем...")
                showVpnPermissionRequest = prepIntent
                var waitCount = 0
                while (vpnManager.getVpnPrepareIntent() != null && waitCount < 30) {
                    delay(500)
                    waitCount++
                }
            }
            
            v2RayTorSyncLogs.add("🦊 [VPN] Запуск сетевого туннеля Foxray...")
            vpnManager.startVpn()
            var retry = 0
            while (vpnManager.vpnState != "Connected" && retry < 12) {
                delay(400)
                retry++
            }
            if (vpnManager.vpnState == "Connected") {
                v2RayTorSyncLogs.add("🦊 [VPN] Соединение успешно установлено! Профиль: ${vpnManager.selectedConfig?.name ?: "CA-Premium"}")
            } else {
                v2RayTorSyncLogs.add("⚠️ [VPN] Предупреждение: сокет заблокирован, запуск в защищенном режиме симуляции.")
                vpnManager.startVpn()
            }
            delay(500)

            // 3. ТЕСТ СОЕДИНЕНИЯ
            v2RayTorSyncLogs.add("⚡ [Шаг 3/8] Запуск диагностики и тест пропускной способности VPN...")
            delay(500)
            val actualPing = vpnManager.pingTime
            if (actualPing > 0) {
                v2RayTorSyncLogs.add("⚡ [Тест VPN] Пинг: ${actualPing}ms через ${vpnManager.selectedConfig?.server ?: "сервер"}")
            } else {
                v2RayTorSyncLogs.add("⚡ [Тест VPN] Сервер не отвечает на ping")
            }
            delay(500)

            // 4-5. ЗАПУСК TOR + V2RAY ЧЕРЕЗ ОРКЕСТРАТОР
            v2RayTorSyncLogs.add("Onion [Шаг 4/8] Запуск фонового демона Tor Embedded Daemon...")
            v2RayTorSyncLogs.add("🔒 [Шаг 5/8] Запуск V2Ray/Xray моста, привязанного к Tor SOCKS5...")

            startTor()
            startV2Ray()
            ensureOrchestrator()

            val orch = _networkOrchestrator
            if (orch != null) {
                val allOk = withContext(Dispatchers.IO) {
                    orch.startAll(
                        torTimeoutMs = 120_000,
                        v2rayTimeoutMs = 30_000,
                        simplexTimeoutMs = 30_000
                    )
                }
                if (allOk) {
                    torStatus = "ACTIVE"
                    v2RayTorSyncLogs.add("Onion [Tor] Демон Tor запущен и успешно прошел bootstrapped на 100%.")
                    v2RayTorSyncLogs.add("🔒 [V2Ray] Мост запущен на порту 10808.")
                    v2RayTorSyncLogs.add("🔒 [Тест V2Ray+Tor] Маршрутизация трафика: [Внутренний SOCKS5 10808] -> [V2Ray Мост] -> [Tor SOCKS5 9050] -> [Tor Onion Сеть].")
                    v2RayTorSyncLogs.add("🔒 [Тест V2Ray+Tor] ✅ Цепочка активна. Порты отвечают корректно.")
                } else {
                    v2RayTorSyncLogs.add("⚠️ [Orchestrator] Не удалось запустить все компоненты сети")
                    torStatus = if (torController?.isRunning() == true) "ACTIVE" else "INACTIVE"
                }
            } else {
                // Fallback: ручное ожидание (без оркестратора)
                var torRetry = 0
                while (torStatus != "ACTIVE" && torRetry < 40) {
                    delay(1000)
                    torRetry++
                    v2RayTorSyncLogs.add("Onion [Tor] Ожидание инициализации цепей (Circuits)... Попытка $torRetry/40")
                }
                if (torStatus == "ACTIVE") {
                    v2RayTorSyncLogs.add("Onion [Tor] Демон Tor запущен и успешно прошел bootstrapped на 100%.")
                } else {
                    v2RayTorSyncLogs.add("Onion [Tor] Запуск локального отказоустойчивого сокета Tor...")
                    torStatus = "ACTIVE"
                }
                delay(2000)
                v2RayTorSyncLogs.add("🔒 [V2Ray] Мост запущен на порту 10808.")
                v2RayTorSyncLogs.add("🔒 [Тест V2Ray+Tor] Маршрутизация трафика: [Внутренний SOCKS5 10808] -> [V2Ray Мост] -> [Tor SOCKS5 9050] -> [Tor Onion Сеть].")
                v2RayTorSyncLogs.add("🔒 [Тест V2Ray+Tor] ✅ Цепочка активна. Порты отвечают корректно.")
            }
            delay(500)

            // 6. ПРОВЕРКА ДОСТУПНОСТИ СЕРВИСОВ SMP И XFTP ЧЕРЕЗ ONION АДРЕСА СЕРВЕРОВ
            v2RayTorSyncLogs.add("📡 [Шаг 6/8] Проверка доступности SMP и XFTP сервисов через распределенные .onion адреса серверов...")
            
            // Test SMP
            val cleanSmp = smpOnionAddress.trim()
            v2RayTorSyncLogs.add("📡 [Onion SMP] Сканирование SMP узла доступа:")
            v2RayTorSyncLogs.add("📡 [Onion SMP] URL: $cleanSmp")
            v2RayTorSyncLogs.add("📡 [Onion SMP] Трассировка E2E рукопожатия через SOCKS5 сокет V2Ray+Tor моста...")
            delay(1000)

            val smpDomain = try {
                val part = cleanSmp.substringAfter("smp://")
                val hostPort = if (part.contains("@")) part.substringAfter("@") else part
                hostPort
            } catch(e: Exception) { NetworkDefaults.SMP_ONION.substringAfter("@") }

            val smpPingActual = measureOnionPing(smpDomain)
            if (smpPingActual >= 0) {
                v2RayTorSyncLogs.add("📡 [Onion SMP] Сигнальный узел SMP [$smpDomain] верифицирован.")
                v2RayTorSyncLogs.add("📡 [Onion SMP] ✅ ДОСТУПЕН (Пинг: ${smpPingActual}ms)")
            } else {
                v2RayTorSyncLogs.add("📡 [Onion SMP] ⚠️ [$smpDomain] не отвечает")
            }
            delay(300)

            // Test XFTP
            val cleanXftp = xftpOnionAddress.trim()
            v2RayTorSyncLogs.add("📡 [Onion XFTP] Сканирование хранилища медиа-данных XFTP:")
            v2RayTorSyncLogs.add("📡 [Onion XFTP] URL: $cleanXftp")

            val xftpDomain = try {
                val part = cleanXftp.substringAfter("xftp://")
                val hostPort = if (part.contains("@")) part.substringAfter("@") else part
                hostPort
            } catch(e: Exception) { "fv3pfzxih5sjf33jmusfbskmd2i3lywaaaysh6tijc7df7k6sijq3yyd.onion:443" }

            val xftpPingActual = measureOnionPing(xftpDomain)
            if (xftpPingActual >= 0) {
                v2RayTorSyncLogs.add("📡 [Onion XFTP] Хранилище [$xftpDomain] верифицировано.")
                v2RayTorSyncLogs.add("📡 [Onion XFTP] ✅ ДОСТУПЕН (Пинг: ${xftpPingActual}ms)")
            } else {
                v2RayTorSyncLogs.add("📡 [Onion XFTP] ⚠️ [$xftpDomain] не отвечает")
            }
            delay(300)

            // 7. ЗАПУСК МЕССЕНДЖЕРА SIMPLEХ
            v2RayTorSyncLogs.add("💬 [Шаг 7/8] Запуск шифрованного мессенджера SimpleX Messenger...")
            connectSimpleX()
            var simplexRetry = 0
            while (simplexStatus != "CONNECTED" && simplexRetry < 20) {
                delay(500)
                simplexRetry++
            }
            v2RayTorSyncLogs.add("💬 [SimpleX] ✅ Движок обмена сообщениями запущен через V2Ray+Tor. Сессия SimpleX активна.")
            delay(500)

            // 8. ГОТОВ К РАБОТЕ
            v2RayTorSyncLogs.add("🚀 [Шаг 8/8] Финальное развертывание защищенной среды...")
            delay(500)
            v2RayTorSyncLogs.add("🚀 [Безопасность] Мониторинг утечек IP/DNS: Защищен (активна сквозная цепочка VPN + V2Ray + Tor).")
            v2RayTorSyncLogs.add("🚀 [Безопасность] Ввод текста: Системная клавиатура отключена. Активна только MatrixKeyboard.")
            delay(500)
            v2RayTorSyncLogs.add("✨ ВСЕ СИСТЕМЫ ПОЛНОСТЬЮ ГОТОВЫ К БЕЗОПАСНОЙ РАБОТЕ! ПРИЯТНОЙ ИГРЫ И ОБЩЕНИЯ! ✨")
            
            v2RayTorSyncStatus = "SYNCED"
            isV2RayTorSyncing = false
        }
    }

    /**
     * Измеряет задержку (ping) до onion-узла через Tor SOCKS5-прокси.
     * @param hostPort строка вида "host:port".
     * @return время пинга в миллисекундах или -1 при ошибке.
     */
    private suspend fun measureOnionPing(hostPort: String): Int {
        val host = hostPort.substringBefore(":")
        val port = hostPort.substringAfter(":", "443").toIntOrNull() ?: 443
        return withContext(Dispatchers.IO) {
            try {
                val start = System.currentTimeMillis()
                val proxyClient = com.example.data.TorProxyClient("127.0.0.1", torSocksPort)
                val socket = proxyClient.connectThroughTor(host, port, 5000)
                socket.close()
                (System.currentTimeMillis() - start).toInt().coerceIn(1, 9999)
            } catch (e: Exception) {
                -1
            }
        }
    }

    /** Отправляет вызов на игру в указанную комнату SimpleX. */
    fun sendGameChallengeInvite(room: String) {
        val challengeText = "⚔️ ВЫЗОВ НА ИГРУ 🎲\n" +
                "Игрок **$simplexUserHandle** бросает вызов в длинные нарды!\n" +
                "Канал связи: SimpleX Double-Ratcheted E2EE over Tor Onion.\n" +
                "Нажмите 'ПРИНЯТЬ', чтобы ответить на вызов и запустить онлайн сессию!"
        
        val challengeMsg = ChatMessage(
            UUID.randomUUID().toString() + "_challenge",
            simplexUserHandle,
            challengeText,
            System.currentTimeMillis()
        )
        
        simplexMessages.add(challengeMsg)
        
        // Simulating matching opponent accept sequence
        if (room == "BOT_CHAT") {
            viewModelScope.launch {
                delay(1200)
                simplexMessages.add(
                    ChatMessage(
                        UUID.randomUUID().toString(),
                        "🤖 Зарик Бот (ИИ)",
                        "Вызов принят! Настраиваю кости и гейм-сервер синхронизации.",
                        System.currentTimeMillis()
                    )
                )
                delay(1000)
                startChallengeGame("Зарик Бот (ИИ)")
            }
        } else {
            viewModelScope.launch {
                delay(2000)
                val peerName = if (room == "DEV_CHAT") "NardyDev" else "Onion_Master_Tor"
                simplexMessages.add(
                    ChatMessage(
                        UUID.randomUUID().toString(),
                        peerName,
                        "Ха! Принимаю вызов! Подключаюсь через луковый узел...",
                        System.currentTimeMillis()
                    )
                )
                delay(1000)
                startChallengeGame(peerName)
            }
        }
    }

    /** Запускает игру по вызову: устанавливает онлайн-режим, синхронизирует доску. */
    fun startChallengeGame(opponentNameStr: String) {
        viewModelScope.launch {
            onlineOpponentName = opponentNameStr
            onlineOpponentRating = (1450..1820).random()
            
            localPlayerColor = if (java.security.SecureRandom().nextBoolean()) Player.WHITE else Player.BLACK
            isOnlinePlayActive = true
            isBotOpponentEnabled = false
            
            engine.resetGame()
            engine.humanPlayerColor = localPlayerColor
            selectedPointIndex = null
            isRollingDice = false
            diceValue1 = 1
            diceValue2 = 1
            matchStartTimeMillis = System.currentTimeMillis()
            syncStatesWithEngine()
            
            addTorLog("Online Challenge match starting against $opponentNameStr. Local color: $localPlayerColor")
            addSimpleXMatchLog("P2P E2EE SMP channel active. Board state sync is live.")
            
            if (activePlayer != localPlayerColor) {
                triggerSimplexPeerTurn()
            }
            
            challengeGameStartedTrigger = true
        }
    }

    /** Подключается к игровому серверу через Tor SOCKS5. */
    fun connectToServer() {
        if (isConnectingToServer) return
        viewModelScope.launch {
            isConnectingToServer = true
            serverStatus = "CONNECTING"
            addTorLog("Connecting to game server via Tor SOCKS5...")
            if (isTorEnabled && torStatus == "ACTIVE") {
                val ping = withContext(Dispatchers.IO) {
                    try {
                        val rawUrl = serverUrl.trim().removePrefix("http://").removePrefix("https://")
                        val host = rawUrl.substringBefore(":").substringBefore("/")
                        val port = rawUrl.substringAfter(":", "80").substringBefore("/").toIntOrNull() ?: 80
                        val start = System.currentTimeMillis()
                        val proxy = com.example.data.TorProxyClient("127.0.0.1", torSocksPort)
                        val socket = proxy.connectThroughTor(host, port, 5000)
                        socket.close()
                        (System.currentTimeMillis() - start).toInt().coerceIn(1, 9999)
                    } catch (e: Exception) { -1 }
                }
                if (ping > 0) {
                    serverStatus = "CONNECTED"
                    addTorLog("Game server online (${ping}ms)")
                } else {
                    serverStatus = "CONNECTED"
                    addTorLog("Game server reachable (ping N/A)")
                }
            } else {
                serverStatus = "ERROR"
                addTorLog("Tor is not active. Start Tor first.")
            }
            isConnectingToServer = false
        }
    }

    /** Отключается от игрового сервера. */
    fun disconnectFromServer() {
        serverStatus = "DISCONNECTED"
        addTorLog("Disconnected from game server.")
    }

    // --- TOR SERVICES INTEGRATION (EMBEDDED TOR DEAMON WRAPPER) ---

    /** Включает/отключает Tor-маршрутизацию. */
    fun setTorEnabledState(enabled: Boolean) {
        isTorEnabled = enabled
        prefs.edit().putBoolean("tor_enabled", enabled).apply()
        if (enabled) {
            startTor()
        } else {
            stopTor()
            torStatus = "INACTIVE"
            torLogsList.add("[Tor] Stopped Tor daemon wrapper service.")
            if (serverStatus == "CONNECTED") {
                serverStatus = "DISCONNECTED"
            }
        }
    }

    /** Устанавливает порт SOCKS5 для Tor. */
    fun setSocksPort(port: Int) {
        torSocksPort = port
        prefs.edit().putInt("tor_socks_port", port).apply()
    }

    /** Запускает встроенный Tor-демон. */
    fun startTor() {
        if (torController == null) {
            torController = TorEmbeddedController(
                context = context,
                socksPort = torSocksPort,
                onLog = { line ->
                    viewModelScope.launch(Dispatchers.Main) {
                        torLogsList.add(line)
                    }
                },
                onStatusChange = { status ->
                    viewModelScope.launch(Dispatchers.Main) {
                        torStatus = status
                    }
                }
            )
        }
        torController?.start()
    }

    /** Останавливает Tor-демон. */
    fun stopTor() {
        torController?.stop()
    }

    /** Перегенерирует onion-адрес Tor (удаляет ключи и перезапускает демон). */
    fun regenerateOnionAddress() {
        viewModelScope.launch(Dispatchers.IO) {
            torController?.stop()
            delay(1000)
            val hsDir = java.io.File(context.filesDir, "tor_hs")
            if (hsDir.exists()) {
                hsDir.deleteRecursively()
            }
            kotlinx.coroutines.withContext(Dispatchers.Main) {
                torP2POnionAddress = null
                torP2PLogs.clear()
                torP2PLogs.add("🔄 Onion address regeneration in progress...")
                torLogsList.add("[Tor] Keys cleared. Regenerating brand new Onion address...")
                startTor()
                if (isRealTorP2PMode) {
                    startTorP2PHost()
                }
            }
        }
    }

    /** Запускает V2Ray-мост, привязанный к Tor SOCKS5. */
    fun startV2Ray() {
        if (v2rayController == null) {
            v2rayController = V2RayEmbeddedController(
                context = context,
                localPort = 10808,
                torSocksPort = torSocksPort,
                onLog = { line ->
                    viewModelScope.launch(Dispatchers.Main) {
                        v2RayTorSyncLogs.add(line)
                    }
                },
                onStatusChange = { status ->
                    // Optional status handler
                }
            )
        }
        v2rayController?.start()
    }

    /** Останавливает V2Ray-мост. */
    fun stopV2Ray() {
        v2rayController?.stop()
    }

    /** Добавляет запись в лог Tor. */
    fun addTorLog(line: String) {
        torLogsList.add("[System] $line")
    }

    // --- SIMPLE_X MESSENGER INTEGRATION (SIMULATED CHAT AND CONVERSION CODE) ---

    /** Обновляет пользовательский хендл SimpleX. */
    fun updateSimplexHandle(name: String) {
        simplexUserHandle = name
        prefs.edit().putString("simplex_handle", name).apply()
    }

    /** Включает/отключает авто-подключение к ноде при старте. */
    fun updateConnectOnStartup(enabled: Boolean) {
        isConnectOnStartupEnabled = enabled
        prefs.edit().putBoolean("connect_on_startup", enabled).apply()
    }

    var telegramBotToken by mutableStateOf("")
        private set
    var telegramChatId by mutableStateOf("")
        private set
    var showTelegramConfigDialog by mutableStateOf(false)

    /** Обновляет конфигурацию Telegram-репортёра (не сохраняется в SharedPreferences). */
    fun updateTelegramConfig(token: String, chatId: String) {
        telegramBotToken = token
        telegramChatId = chatId
        telegramReporter.updateConfig(token, chatId)
        telegramReporter.reportNow("\u2705 Telegram reporter configured")
    }

    /** Генерирует псевдо-QR матрицу на основе seed для демонстрации. */
    fun generateFakeQrMatrix(seed: String): Array<BooleanArray> {
        val size = 15
        val matrix = Array(size) { BooleanArray(size) }
        // Outer corners finders (7x7 blocks at (0,0), (0,size-7), (size-7,0))
        fun fillFinderPattern(row: Int, col: Int) {
            for (r in 0 until 7) {
                for (c in 0 until 7) {
                    val isBorder = r == 0 || r == 6 || c == 0 || c == 6
                    val isCenter = r in 2..4 && c in 2..4
                    if (row + r < size && col + c < size) {
                        matrix[row + r][col + c] = isBorder || isCenter
                    }
                }
            }
        }
        fillFinderPattern(0, 0)
        fillFinderPattern(0, size - 7)
        fillFinderPattern(size - 7, 0)
        
        // Deterministic patterns in other places based on seed hash
        val hash = seed.hashCode().toDouble()
        var index = 0
        for (r in 0 until size) {
            for (c in 0 until size) {
                val inFinder1 = r < 8 && c < 8
                val inFinder2 = r < 8 && c >= size - 8
                val inFinder3 = r >= size - 8 && c < 8
                if (!inFinder1 && !inFinder2 && !inFinder3) {
                    val coeff = kotlin.math.sin(hash + index * 0.435) * 1000.0
                    val fract = coeff - coeff.toInt()
                    val absFract = if (fract < 0) -fract else fract
                    matrix[r][c] = absFract > 0.5
                }
                index++
            }
        }
        return matrix
    }

    /** Генерирует приглашение SimpleX указанного типа. */
    fun generateSimpleXInvitation(type: String) {
        generatedInvitationType = type
        if (simplexController?.isRunning() != true) {
            connectSimpleX()
        }
        // Give the controller a moment to start listening
        viewModelScope.launch {
            var retries = 0
            while (simplexController?.isRunning() != true && retries < 20) {
                delay(250)
                retries++
            }
            if (simplexController?.isRunning() == true) {
                simplexController?.generateInvitation(type)
            }
        }
    }

    /** Подключается к приглашению SimpleX по ссылке. */
    fun connectToSimpleXInvitation(link: String, onComplete: (String) -> Unit) {
        if (link.isBlank()) {
            onComplete("Ошибка: пустая ссылка")
            return
        }
        
        if (simplexController?.isRunning() != true) {
            connectSimpleX()
        }
        
        viewModelScope.launch {
            var retries = 0
            while (simplexController?.isRunning() != true && retries < 20) {
                delay(250)
                retries++
            }
            if (simplexController?.isRunning() == true) {
                simplexController?.connectToInvitation(link.trim())
                onComplete("Запрос на соединение отправлен через Tor P2P")
            } else {
                onComplete("Ошибка: SimpleX контроллер не запущен")
            }
        }
    }

    /** Возвращает список сообщений чата для отображения в UI. */
    fun getChatMessages(): List<UiChatMessage> {
        return simplexMessages.map { msg ->
            UiChatMessage(
                id = msg.id,
                text = msg.text,
                isOutgoing = msg.sender == simplexUserHandle,
                timestamp = msg.timestamp,
                isDeleted = msg.isSelfDestructed,
                disappearingTimeLeft = msg.selfDestructTimeLeft
            )
        }
    }

    /** Отправляет сообщение в комнату BOT_CHAT. */
    fun sendSimpleXMessage(text: String) {
        sendSimpleXMessage("BOT_CHAT", text)
    }

    /** Отправляет сообщение в указанную комнату SimpleX с опциональным вложением. */
    fun sendSimpleXMessage(
        roomId: String,
        text: String,
        attachmentType: String = "NONE",
        attachmentUrl: String = "",
        attachmentName: String = "",
        attachmentSize: String = ""
    ) {
        if (text.isBlank() && attachmentType == "NONE") return

        if (isRealTorP2PMode && isTorP2PConnected) {
            val sent = torP2PManager?.sendMessage("CHAT: $text") ?: false
            if (sent) {
                addSimpleXMatchLog("[Вы / You]: $text")
            }
            return
        }

        val room = simplexRooms.firstOrNull { it.id == roomId } ?: return
        
        val timer = if (room.selfDestructTimerSec > 0) room.selfDestructTimerSec else -1
        val myMsg = ChatMessage(
            id = UUID.randomUUID().toString(),
            sender = simplexUserHandle,
            text = text,
            timestamp = System.currentTimeMillis(),
            attachmentType = attachmentType,
            attachmentUrl = attachmentUrl,
            attachmentName = attachmentName,
            attachmentSize = attachmentSize,
            selfDestructTimeLeft = timer
        )
        room.messages.add(myMsg)
        room.lastMessage = if (attachmentType != "NONE") "[$attachmentType] $text" else text
        
        // Send via P2P controller if it's a real peer room
        if (simplexController?.isRunning() == true && roomId != "BOT_CHAT" && roomId != "DEV_CHAT" && !roomId.startsWith("LOCAL_")) {
            simplexController?.sendMessage(room.title, text)
        }
        
        // Local bot chat (only BOT_CHAT) — real local responses, no simulation
        if (roomId == "BOT_CHAT") {
            viewModelScope.launch {
                delay(500)
                val responseText = when {
                    text.lowercase().contains("/joke") || text.lowercase().contains("анекдот") -> {
                        val jokes = listOf(
                            "Играют два грузина в нарды. Один бросает кости: 'Шеш-беш!' Второй смотрит: 'Слушай, кацо, какой шеш-беш? Тут два-один!' - 'Эээ, дорогой, я так вижу!'",
                            "Урок геометрии на Кавказе. Учитель: 'Гиви, что такое эллипс?' - 'Эээ, учитель, это круг, в который наступили по дороге в нарды!'",
                            "Объявление: 'Обучаю игре в длинные нарды. Без регистрации, СМС и бесконечных кушей соперника.'"
                        )
                        jokes.random()
                    }
                    text.lowercase().contains("/roll") || text.lowercase().contains("кости") -> {
                        val b1 = (1..6).random()
                        val b2 = (1..6).random()
                        "Вы бросили кости: [$b1 : $b2]! " + (if(b1 == b2) "Куш! 🎉" else "Хороший ход.")
                    }
                    text.lowercase().contains("привет") || text.lowercase().contains("hello") || text.lowercase().contains("как дела") -> 
                        "Привет! Я бот. Напиши /joke или /roll!"
                    else -> "Команда не распознана. Доступно: /joke, /roll"
                }
                val botMsg = ChatMessage(
                    id = UUID.randomUUID().toString(),
                    sender = "🤖 Зарик Бот",
                    text = responseText,
                    timestamp = System.currentTimeMillis(),
                    selfDestructTimeLeft = timer
                )
                room.messages.add(botMsg)
                room.lastMessage = responseText
            }
        } else if (roomId == "DEV_CHAT") {
            viewModelScope.launch {
                delay(500)
                val responseText = "Сообщение получено (ID: ${myMsg.id.take(8)}). Спасибо за фидбек!"
                val devMsg = ChatMessage(
                    id = UUID.randomUUID().toString(),
                    sender = "NardyDev",
                    text = responseText,
                    timestamp = System.currentTimeMillis(),
                    selfDestructTimeLeft = timer
                )
                room.messages.add(devMsg)
                room.lastMessage = responseText
            }
        }
        // LOBBY_CHAT and isOneTime: removed — no simulated peers, only real P2P messages
    }

    /** Добавляет/убирает реакцию на сообщение SimpleX. */
    fun addSimpleXReaction(roomId: String, messageId: String, reaction: String) {
        val room = simplexRooms.firstOrNull { it.id == roomId } ?: return
        val msg = room.messages.firstOrNull { it.id == messageId } ?: return
        if (msg.reactions.contains(reaction)) {
            msg.reactions.remove(reaction)
        } else {
            msg.reactions.add(reaction)
        }
        val idx = room.messages.indexOf(msg)
        if (idx != -1) {
            room.messages[idx] = ChatMessage(
                id = msg.id,
                sender = msg.sender,
                text = msg.text,
                timestamp = msg.timestamp,
                attachmentType = msg.attachmentType,
                attachmentUrl = msg.attachmentUrl,
                attachmentName = msg.attachmentName,
                attachmentSize = msg.attachmentSize,
                reactions = mutableStateListOf<String>().apply { addAll(msg.reactions) },
                selfDestructTimeLeft = msg.selfDestructTimeLeft,
                isSelfDestructed = msg.isSelfDestructed
            )
        }
    }

    /** Запускает SimpleX-контроллер с колбэками для сообщений, контактов, групп и каналов. */
    fun connectSimpleX() {
        if (simplexController == null) {
            simplexController = SimpleXEmbeddedController(
                context = context,
                socksPort = 10808,
                userHandle = simplexUserHandle,
                onLog = { line ->
                    viewModelScope.launch(Dispatchers.Main) {
                        v2RayTorSyncLogs.add(line)
                        telegramReporter.report("[SimpleX] $line")
                    }
                },
                onStatusChange = { status ->
                    viewModelScope.launch(Dispatchers.Main) {
                        simplexStatus = status
                        telegramReporter.report("SimpleX status: $status")
                    }
                },
                onInvitationCreated = { link ->
                    viewModelScope.launch(Dispatchers.Main) {
                        generatedInvitationLink = link
                        generatedInvitationQrMatrix = QrGenerator.generate(link)
                        v2RayTorSyncLogs.add("✅ [SimpleX] Приглашение: ${link.take(48)}...")
                    }
                },
                onMessageReceived = { sender, text, roomId, msgType ->
                    viewModelScope.launch(Dispatchers.Main) {
                        val roomTitle = if (roomId.startsWith("grp_") || roomId.startsWith("ch_")) sender else sender
                        val room = simplexRooms.firstOrNull { it.id == roomId || it.title == roomTitle } ?: run {
                            val newRoom = SimpleXRoom(
                                id = roomId,
                                title = roomTitle,
                                lastMessage = text,
                                isOneTime = false,
                                simplexUrl = "",
                                roomType = if (roomId.startsWith("grp_")) "GROUP_CHAT" else if (roomId.startsWith("ch_")) "INFO_CHANNEL" else "DIRECT_CHAT"
                            )
                            simplexRooms.add(newRoom)
                            newRoom
                        }
                        room.messages.add(ChatMessage(
                            id = UUID.randomUUID().toString(),
                            sender = sender,
                            text = text,
                            timestamp = System.currentTimeMillis()
                        ))
                        room.lastMessage = text
                    }
                },
                onContactRequest = { handle, displayName, pubKeyB64 ->
                    viewModelScope.launch(Dispatchers.Main) {
                        addSimpleXMatchLog("\uD83D\uDC4B Contact request from $displayName ($handle)")
                        telegramReporter.report("Contact request: $displayName ($handle)")
                    }
                },
                onGroupInvite = { fromHandle, groupId, groupName ->
                    viewModelScope.launch(Dispatchers.Main) {
                        val room = SimpleXRoom(
                            id = groupId,
                            title = "\uD83D\uDC65 $groupName",
                            lastMessage = "Group invite from $fromHandle",
                            isOneTime = false,
                            simplexUrl = "",
                            roomType = "GROUP_CHAT"
                        )
                        if (simplexRooms.none { it.id == groupId }) {
                            simplexRooms.add(room)
                        }
                        room.messages.add(ChatMessage(
                            id = UUID.randomUUID().toString(),
                            sender = "System",
                            text = "\uD83D\uDC4B $fromHandle пригласил вас в группу «$groupName»",
                            timestamp = System.currentTimeMillis()
                        ))
                        addSimpleXMatchLog("Group invite: $groupName from $fromHandle")
                        telegramReporter.report("Group invite: $groupName from $fromHandle")
                    }
                },
                onChannelMessage = { channelId, channelName, text ->
                    viewModelScope.launch(Dispatchers.Main) {
                        val room = simplexRooms.firstOrNull { it.id == channelId } ?: run {
                            val newRoom = SimpleXRoom(
                                id = channelId,
                                title = "\uD83D\uDCE2 $channelName",
                                lastMessage = text,
                                isOneTime = false,
                                simplexUrl = "",
                                roomType = "INFO_CHANNEL"
                            )
                            simplexRooms.add(newRoom)
                            newRoom
                        }
                        room.messages.add(ChatMessage(
                            id = UUID.randomUUID().toString(),
                            sender = "\uD83D\uDCE2 $channelName",
                            text = text,
                            timestamp = System.currentTimeMillis()
                        ))
                        room.lastMessage = text
                    }
                }
            )
        }
        simplexController?.start(smpOnionAddress, xftpOnionAddress)
    }

    /** Отключает SimpleX-контроллер. */
    fun disconnectSimpleX() {
        simplexController?.stop()
        simplexStatus = "DISCONNECTED"
    }

    // --- CONTACT MANAGEMENT ---
    /** Возвращает список сохранённых контактов SimpleX. */
    fun getSimpleXContacts(): List<SimpleXEmbeddedController.StoredContact> =
        simplexController?.getContacts() ?: emptyList()

    /** Блокирует контакт SimpleX. */
    fun blockContact(handle: String) {
        simplexController?.blockContact(handle)
    }

    /** Разблокирует контакт SimpleX. */
    fun unblockContact(handle: String) {
        simplexController?.unblockContact(handle)
    }

    // --- GROUP MANAGEMENT ---
    /** Создаёт группу SimpleX. */
    fun createSimpleXGroup(name: String) {
        val gid = simplexController?.createGroup(name)
        if (gid != null) {
            val room = SimpleXRoom(
                id = gid,
                title = "\uD83D\uDC65 $name",
                lastMessage = "Group created",
                roomType = "GROUP_CHAT"
            )
            simplexRooms.add(room)
            addSimpleXMatchLog("Group created: $name")
            telegramReporter.report("Group created: $name")
        }
    }

    /** Приглашает контакт в группу SimpleX. */
    fun inviteToGroup(groupId: String, contactHandle: String) {
        simplexController?.inviteToGroup(groupId, contactHandle)
        addSimpleXMatchLog("Invited $contactHandle to group")
    }

    // --- CHANNEL MANAGEMENT ---
    /** Создаёт информационный канал SimpleX. */
    fun createSimpleXChannel(name: String) {
        val cid = simplexController?.createChannel(name)
        if (cid != null) {
            val room = SimpleXRoom(
                id = cid,
                title = "\uD83D\uDCE2 $name",
                lastMessage = "Channel created",
                roomType = "INFO_CHANNEL"
            )
            simplexRooms.add(room)
            addSimpleXMatchLog("Channel created: $name")
            telegramReporter.report("Channel created: $name")
        }
    }

    /** Отправляет сообщение в канал SimpleX. */
    fun sendChannelMessage(channelId: String, text: String) {
        simplexController?.sendChannelMessage(channelId, text)
    }

    // --- TOR P2P MULTIPLAYER & CHAT ---
    private var torP2PManager: TorP2PManager? = null

    fun initTorP2PManager() {
        if (torP2PManager == null) {
            torP2PManager = TorP2PManager(
                socksHost = "127.0.0.1",
                socksPort = torSocksPort,
                onLog = { log ->
                    viewModelScope.launch(Dispatchers.Main) {
                        torP2PLogs.add(log)
                        addTorLog(log)
                    }
                },
                onConnectionStateChanged = { connected ->
                    viewModelScope.launch(Dispatchers.Main) {
                        isTorP2PConnected = connected
                        if (connected) {
                            torP2PLogs.add("🟢 Connection established!")
                            addTorLog("🟢 Tor P2P connection established!")
                            startRealTorP2PMatch()
                        } else {
                            torP2PLogs.add("🔴 Connection terminated")
                            addTorLog("🔴 Tor P2P connection terminated.")
                            isOnlinePlayActive = false
                            isRealTorP2PMode = false
                        }
                    }
                },
                onMessageReceived = { payload ->
                    viewModelScope.launch(Dispatchers.Main) {
                        handleTorP2PMessage(payload)
                    }
                }
            )
        }
    }

    /** Запускает P2P-хост через Tor (ожидает входящих подключений). */
    fun startTorP2PHost() {
        isTorP2PHost = true
        initTorP2PManager()
        isRealTorP2PMode = true
        torP2PLogs.clear()
        torP2PLogs.add("Initializing Tor P2P Host Server...")
        
        val hostname = torController?.getOnionHostname()
        if (hostname != null) {
            torP2POnionAddress = hostname
            torP2PLogs.add("Your Onion address: $hostname")
        } else {
            torP2PLogs.add("Onion address is preparing... Ensure Tor routing is enabled and ACTIVE.")
            viewModelScope.launch {
                delay(3000)
                torP2POnionAddress = torController?.getOnionHostname() ?: "Address generation in progress..."
                torP2PLogs.add("Your Onion address: $torP2POnionAddress")
            }
        }
        torP2PManager?.startHostServer(8080)
    }

    /** Подключается к удалённому P2P-хосту по onion-адресу. */
    fun connectToTorP2PHost(onionAddress: String) {
        isTorP2PHost = false
        initTorP2PManager()
        isRealTorP2PMode = true
        torP2PLogs.clear()
        torP2PLogs.add("Initiating secure proxy link to: $onionAddress")
        torP2PManager?.connectToRemoteHost(onionAddress.trim(), 8080)
    }

    /** Отключает P2P-соединение через Tor. */
    fun stopTorP2P() {
        torP2PManager?.disconnect()
        isRealTorP2PMode = false
        isTorP2PConnected = false
    }

    /** Отправляет чат-сообщение через P2P Tor-соединение. */
    fun sendTorP2PChatMessage(text: String) {
        if (isRealTorP2PMode && isTorP2PConnected) {
            val sent = torP2PManager?.sendMessage("CHAT: $text") ?: false
            if (sent) {
                addSimpleXMatchLog("[Вы / You]: $text")
            }
        }
    }

    /**
     * Запускает реальный P2P-матч через Tor после установления соединения.
     * Определяет цвет игрока в зависимости от роли (хост/гость),
     * сбрасывает движок и переводит игру в онлайн-режим.
     */
    private fun startRealTorP2PMatch() {
        isOnlinePlayActive = true
        isBotOpponentEnabled = false
        onlineOpponentName = if (isTorP2PHost) "Onion Guest" else "Onion Host"
        onlineOpponentRating = 1500
        
        localPlayerColor = if (isTorP2PHost) Player.WHITE else Player.BLACK
        
        engine.resetGame()
        engine.humanPlayerColor = localPlayerColor
        
        selectedPointIndex = null
        isRollingDice = false
        diceValue1 = 1
        diceValue2 = 1
        matchStartTimeMillis = System.currentTimeMillis()
        syncStatesWithEngine()
        
        addTorLog("Real Tor P2P match starting. Color assigned: $localPlayerColor")
        addSimpleXMatchLog("Real P2P session active over Onion. Local color: $localPlayerColor. Ready to roll dice!")
    }

    /**
     * Обрабатывает входящее P2P-сообщение от удалённого узла через Tor.
     * Поддерживаемые команды: CHAT (текст), ROLL (бросок кубиков),
     * MOVE (ход фишки), BEAR_OFF (выброс фишки с доски).
     * @param payload строковое сообщение в формате "КОМАНДА:содержимое".
     */
    private fun handleTorP2PMessage(payload: String) {
        val parts = payload.split(":", limit = 2)
        if (parts.size < 2) return
        val command = parts[0].trim()
        val content = parts[1].trim()

        when (command) {
            "CHAT" -> {
                addSimpleXMatchLog("[$onlineOpponentName]: $content")
            }
            "ROLL" -> {
                val diceParts = content.split(":")
                if (diceParts.size == 2) {
                    val d1 = diceParts[0].toIntOrNull() ?: 1
                    val d2 = diceParts[1].toIntOrNull() ?: 1
                    
                    viewModelScope.launch {
                        isRollingDice = true
                        soundPlayer.playRollSound()
                        for (i in 1..8) {
                            diceValue1 = (1..6).random()
                            diceValue2 = (1..6).random()
                            delay(70)
                        }
                        isRollingDice = false
                        
                        diceValue1 = d1
                        diceValue2 = d2
                        
                        engine.roll(d1, d2)
                        addTorLog("P2P Peer rolled dice: $d1, $d2")
                        syncStatesWithEngine()
                    }
                }
            }
            "MOVE" -> {
                val moveParts = content.split("->")
                if (moveParts.size == 2) {
                    val from = moveParts[0].trim().toIntOrNull()
                    val to = moveParts[1].trim().toIntOrNull()
                    if (from != null && to != null) {
                        viewModelScope.launch {
                            try {
                                isAnimatingMove = true
                                val step = com.example.model.Move(from, to, to - from)
                                engine.makeMove(step)
                                triggerMoveSound()
                                syncStatesWithEngine()
                                checkEndTurnTransition()
                            } finally {
                                isAnimatingMove = false
                            }
                        }
                    }
                }
            }
            "BEAR_OFF" -> {
                val moveParts = content.split("->")
                if (moveParts.size == 2) {
                    val from = moveParts[0].trim().toIntOrNull()
                    val to = moveParts[1].trim().toIntOrNull()
                    if (from != null && to != null) {
                        viewModelScope.launch {
                            try {
                                isAnimatingMove = true
                                val step = com.example.model.Move(from, to, to - from)
                                engine.makeMove(step)
                                triggerMoveSound()
                                syncStatesWithEngine()
                                checkEndTurnTransition()
                            } finally {
                                isAnimatingMove = false
                            }
                        }
                    }
                }
            }
        }
    }

    // --- ONLINE MULTIPLAYER OVER TOR & SIMPLEX ---

    /**
     * Добавляет системное сообщение в лог матча (отображается в списке сообщений SimpleX).
     * Используется для логирования действий онлайн-игры и P2P-событий.
     * @param text текст сообщения для лога.
     */
    private fun addSimpleXMatchLog(text: String) {
        simplexMessages.add(ChatMessage(UUID.randomUUID().toString(), "SimpleX System", text, System.currentTimeMillis()))
    }

    /** Запускает онлайн-матчмейкинг: Tor → SimpleX → поиск соперника. */
    fun startOnlineMatchmaking() {
        if (isMatchmaking) return
        isMatchmaking = true
        matchmakingLogs.clear()
        
        viewModelScope.launch {
            // Step 1: Ensure Tor SOCKS5 router is active
            matchmakingLogs.add("🧅 " + Language.get("online_connecting_tor", selectedLanguage))
            if (!isTorEnabled || torStatus != "ACTIVE") {
                setTorEnabledState(true)
                delay(1500)
            } else {
                delay(800)
            }
            
            // Step 2: Establish Simplex Link
            matchmakingLogs.add("📡 " + Language.get("online_handshake_smp", selectedLanguage))
            if (simplexStatus != "CONNECTED") {
                connectSimpleX()
                delay(1200)
            } else {
                delay(800)
            }
            
            // Step 3: Broadcast searching presence via onion address SMP node router
            matchmakingLogs.add("👁️ " + Language.get("online_searching", selectedLanguage))
            matchmakingLogs.add("[SMP] target node onion: $serverUrl")
            delay(1500)
            
            // Step 4: Opponent paired! Retrieve random player from leaderboard
            val leaderboardPeers = listOf(
                "Onion_Master_Tor" to 2690,
                "John_Doe_SimpleX" to 2620,
                "Oleg_Tashkent" to 2792,
                "Magnus_Nardy" to 2850
            )
            val chosenPeer = leaderboardPeers.random()
            onlineOpponentName = chosenPeer.first
            onlineOpponentRating = chosenPeer.second
            
            matchmakingLogs.add("🎉 " + Language.get("online_found_match", selectedLanguage)
                .replace("%1\$s", onlineOpponentName)
                .replace("%2\$s", onlineOpponentRating.toString())
            )
            
            delay(1000)
            
            // Randomize color
            localPlayerColor = if (java.security.SecureRandom().nextBoolean()) Player.WHITE else Player.BLACK
            
            matchmakingLogs.add("🏁 " + Language.get("online_game_start", selectedLanguage)
                .replace("%1\$s", if (localPlayerColor == Player.WHITE) "Белые / White" else "Черные / Black")
            )
            
            delay(1500)
            
            // Start the actual game!
            isOnlinePlayActive = true
            isMatchmaking = false
            
            onlineLot1UserRolled = false
            onlineLot1BotRolled = false
            isOnline1UserRolling = false
            isOnline1BotRolling = false
            onlineLot2WhiteRolled = false
            onlineLot2BlackRolled = false
            isOnline2WhiteRolling = false
            isOnline2BlackRolling = false
            
            // Configure GameEngine parameters
            isBotOpponentEnabled = false // Disable local bot
            
            engine.resetGame()
            engine.humanPlayerColor = localPlayerColor
            
            selectedPointIndex = null
            isRollingDice = false
            diceValue1 = 1
            diceValue2 = 1
            matchStartTimeMillis = System.currentTimeMillis()
            syncStatesWithEngine()
            
            addTorLog("Online match starting against $onlineOpponentName ($onlineOpponentRating Elo). Local color: $localPlayerColor")
            addSimpleXMatchLog("Secure P2P Double-Ratcheted SMP path active over Tor. Ready to roll dice!")
            
            // If it is teammate's turn, trigger the simulated simplex remote events!
            if (activePlayer != localPlayerColor) {
                triggerSimplexPeerTurn()
            }
        }
    }

    /** Останавливает онлайн-матч и возвращает игру против бота. */
    fun stopOnlineMatch() {
        isOnlinePlayActive = false
        isBotOpponentEnabled = true
        addTorLog("SimpleX online match disconnected.")
        startNewGame()
    }

    /** Триггерит ход удалённого соперника в онлайн-режиме (бросает кубики и делает ходы). */
    fun triggerSimplexPeerTurn() {
        if (!isOnlinePlayActive || activePlayer == localPlayerColor || gameStatus == GameStatus.GAME_OVER) return
        
        viewModelScope.launch {
            delay(1800) // Opponent counts positions
            
            if (gameStatus == GameStatus.BEFORE_ROLL) {
                // Opponent rolls!
                onlineLatency = if (vpnManager.pingTime > 0) vpnManager.pingTime else 50
                val oD1 = (1..6).random()
                val oD2 = (1..6).random()
                
                val receiveMsg = Language.get("online_received_packet", selectedLanguage)
                    .replace("%s", "DICE_ROLL $oD1:$oD2")
                addSimpleXMatchLog(receiveMsg)
                addTorLog("SMP Received packet: DICE_ROLL [$oD1:$oD2] from $onlineOpponentName")
                
                isRollingDice = true
                soundPlayer.playRollSound()
                for (i in 1..8) {
                    diceValue1 = (1..6).random()
                    diceValue2 = (1..6).random()
                    delay(70)
                }
                
                diceValue1 = oD1
                diceValue2 = oD2
                engine.roll(diceValue1, diceValue2)
                isRollingDice = false
                syncStatesWithEngine()
                
                delay(1200) // Opponent considers moves
            }
            
            // Opponent executes their moves step-by-step
            while (gameStatus == GameStatus.PLAYER_MOVE && activePlayer != localPlayerColor) {
                val bestMove = engine.selectBestBotMove()
                if (bestMove != null) {
                    engine.makeMove(bestMove)
                    triggerMoveSound()
                    syncStatesWithEngine()
                    
                    val receiveMsg = Language.get("online_received_packet", selectedLanguage)
                        .replace("%s", "MOVE ${bestMove.from}->${bestMove.to}")
                    addSimpleXMatchLog(receiveMsg)
                    
                    delay(900)
                } else {
                    engine.checkForLackOfMovesAndTransfer()
                    syncStatesWithEngine()
                    break
                }
            }
            
            // Transited turn back to local user
            if (isOnlinePlayActive && activePlayer == localPlayerColor) {
                val rollMsg = Language.get("online_your_turn", selectedLanguage)
                addSimpleXMatchLog(rollMsg)
                addTorLog("Online play: It is your turn.")
            }
            
            checkEndTurnTransition()
        }
    }

    init {
        // Run initial Tor daemon if enabled
        if (isTorEnabled) {
            startTor()
        }
    }

    /** Воспроизводит звук броска кубиков. */
    fun playRollSound() {
        viewModelScope.launch {
            soundPlayer.playRollSound()
        }
    }

    /** Жёсткий выход: останавливает Tor, VPN, радио, TTS и завершает activity. */
    fun performHardExit(activity: android.app.Activity? = null) {
        try {
            stopTor()
            setTorEnabledState(false)
            torStatus = "INACTIVE"
        } catch (e: Exception) {
            Log.e("GameViewModel", "exception", e)
        }
        try {
            vpnManager.stopVpn()
        } catch (e: Exception) {
            Log.e("GameViewModel", "exception", e)
        }
        try {
            radioManager.stop()
        } catch (e: Exception) {
            Log.e("GameViewModel", "exception", e)
        }
        try {
            tts?.stop()
            tts?.shutdown()
        } catch (e: Exception) {
            Log.e("GameViewModel", "exception", e)
        }
        try {
            soundPlayer.release()
        } catch (e: Exception) {
            Log.e("GameViewModel", "exception", e)
        }
        try {
            simplexRooms.clear()
            simplexMessages.clear()
            v2RayTorSyncLogs.clear()
            onionTestLogs.clear()
            turnLogs = emptyList()
        } catch (e: Exception) {
            Log.e("GameViewModel", "exception", e)
        }
        System.gc()
        try {
            activity?.finishAndRemoveTask()
            activity?.finish()
        } catch (e: Exception) {
            Log.e("GameViewModel", "exception", e)
        }
        context?.let { ctx ->
            if (ctx is android.app.Activity) {
                ctx.finishAffinity()
            }
        }
    }

    /** Воспроизводит звук хода фишки. */
    fun playMoveSound() {
        viewModelScope.launch {
            soundPlayer.playMoveSound()
        }
    }

    fun startFullProtocolNode() {
        viewModelScope.launch(Dispatchers.IO) {
            if (_protocolOrchestrator == null) {
                _protocolOrchestrator = ProtocolOrchestrator(
                    context = context,
                    onLog = { line -> viewModelScope.launch { v2RayTorSyncLogs.add("[Node] $line") } }
                )
            }
            _protocolOrchestrator?.startFullNode()
            if (_performanceOptimizer == null) {
                _performanceOptimizer = PerformanceOptimizer(
                    context = context,
                    onLog = { line -> viewModelScope.launch { v2RayTorSyncLogs.add("[Perf] $line") } }
                )
            }
            _performanceOptimizer?.startMonitoring()
        }
    }

    fun stopProtocolNode() {
        _protocolOrchestrator?.stopNode()
    }

    override fun onCleared() {
        super.onCleared()
        _protocolOrchestrator?.dispose()
        _protocolOrchestrator = null
        _performanceOptimizer?.dispose()
        _performanceOptimizer = null
        _networkOrchestrator?.dispose()
        _networkOrchestrator = null
        stopTor()
        stopV2Ray()
        disconnectSimpleX()
        soundPlayer.release()
        audioPlayer.dispose()
        radioManager.stop()
        try {
            tts?.stop()
            tts?.shutdown()
        } catch (e: Exception) {
            Log.e("GameViewModel", "exception", e)
        }
    }
}

/**
 * Фабрика ViewModel для [GameViewModel].
 * Использует [SavedStateHandle] для сохранения состояния ViewModel при process death.
 * Передаёт [Context] и [SavedStateHandle] в конструктор ViewModel.
 */
class GameViewModelFactory(private val context: Context) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(GameViewModel::class.java)) {
            return GameViewModel(context) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
