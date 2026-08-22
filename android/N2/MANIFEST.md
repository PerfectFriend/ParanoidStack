# ПОЛНЫЙ МАНИФЕСТ ПРОЕКТА N2 (CRAZYGAMMON)

## 1. КОНЦЕПЦИЯ

Скрытый децентрализованный P2P-мессенджер, замаскированный под игру в нарды (Backgammon). Весь трафик проходит через цепочку Tor → V2Ray/Xray → SMP/SimpleX. Приложение не использует центральных серверов. Маскировка: иконка калькулятора, плеера, точек.

## 2. АРХИТЕКТУРА

```
MainActivity → GameScreen (10.5k строк)
  → SimpleXFullScreenChat / MatrixNoGameScreen
    → SMPAgent → SMPClient → TLS SPKI → SMP-сервер
    → TorEmbeddedController → tor-android-binary (0.4.4.6)
    → V2RayEmbeddedController → xray v1.8.24 / fallback SOCKS5
    → FoxrayVpnService → VpnService + TUN

Стек протоколов:
[App] → [SMP/SimpleX] → [V2Ray SOCKS5 :10808] → [Tor SOCKS5 :9050] → [Internet]
```

### Стек файлов

```
ui/viewmodel/          — GameViewModel (3660 строк), ChatViewModel, NetworkViewModel, ProfileViewModel
ui/navigation/         — AppNavHost, NavRoutes, NavigationActions, ScreenConnector, DeepLinkHandler
ui/screens/            — GameScreen (9508 строк), MatrixNoGameScreen, 31 экран + GameDialogs
ui/components/         — 35 компонентов (Board, Dice, Chat, Keyboard, Emoji...)
ui/theme/              — AppTheme, Color, Type, ShimmerEffect
service/               — FoxrayVpnService, FcmPushService, SmpNotificationService, 4 Worker, Crash, Logger
data/                  — Ядро: 35+ файлов (SMP, Tor, V2Ray, Crypto, VPN, P2P, БД)
security/              — ScreenSecurity, ClipboardGuard, DuressPin, SecurityAudit
audio/                 — OpusEncoder, VoiceRecorder, RadioManager, StreamPlayer
model/                 — GameEngine (164 теста), AIPlayer
api/                   — GeminiJokeService
```

## 3. РЕАЛИЗОВАННЫЕ ФУНКЦИИ

### КРИПТОГРАФИЯ

| Компонент | Файл | Описание |
|-----------|------|----------|
| X25519 ECDH | NaClCrypto.kt:30 | KeyAgreement.getInstance("XDH") |
| Salsa20 | NaClCrypto.kt:72 | Quarter rounds, double rounds |
| HSalsa20 | NaClCrypto.kt:146 | 10 double-rounds, SIGMA constants |
| Poly1305 MAC | NaClCrypto.kt:194 | GF(2^130-5) polynomial, 130-bit clamping |
| crypto_box | NaClCrypto.kt:269 | XSalsa20-Poly1305 encrypt-then-MAC |
| crypto_box_open | NaClCrypto.kt:296 | MAC-then-decrypt |
| cryptoBoxBeforeNm | NaClCrypto.kt:320 | Shared key via X25519 DH + HSalsa20 |
| X3DH | DoubleRatchet.kt:46 | 4 DH exchanges |
| Double Ratchet | DoubleRatchet.kt:120 | KDF_RK (HSalsa20), KDF_CK, skipped messages |
| AES-256-GCM | SimpleXCrypto.kt | Шифрование контейнера |
| PBKDF2 | SecureStorage.kt | Master key derivation |

### СЕТЬ

| Компонент | Файл | Строк | Реализация |
|-----------|------|-------|------------|
| SMPProtocol | SMPProtocol.kt | 321 | Бинарный wire, transport blocks 16384B, commands NEW/SEND/SUB/ACK/DEL/PING/KEY/NKEY/NDEL |
| SMPClient | SMPClient.kt | 317 | TLS 1.3, SPKI pinning, wait/notify reader, 15s timeout |
| SMPAgent | SMPAgent.kt | 505 | Сессии, контакты, группы, E2EE, XFTP |
| TorEmbeddedController | TorEmbeddedController.kt | 322 | tor-android-binary, obfs4/meek/snowflake, bootstrap, onion services |
| V2RayEmbeddedController | V2RayEmbeddedController.kt | 405 | xray subprocess, fallback SOCKS5, health-check, auto-restart |
| TorProxyClient | TorProxyClient.kt | 244 | SOCKS5, exponential backoff, DoH |
| Socks5Chain | Socks5Chain.kt | 131 | Multi-hop Tor→V2Ray |
| TransportProvider | TransportProvider.kt | 134 | P2P singleton, dual SOCKS5 handshake |
| TorP2PManager | TorP2PManager.kt | 334 | ServerSocket, keep-alive, peer connections |
| TransportAppStore | TransportAppStore.kt | ~250 | AppEntry, SHA-256 verification, 9 demo apps |
| XFTPClient | XFTPClient.kt | 259 | TLS+SPKI, FNEW/FPUT/FGET/FDEL |
| NetworkOrchestrator | NetworkOrchestrator.kt | 101 | Lifecycle Tor→V2Ray→SimpleX |

### VPN

| Компонент | Файл | Строк | Реализация |
|-----------|------|-------|------------|
| FoxrayVpnService | FoxrayVpnService.kt | 356 | VpnService.Builder, TUN (MTU 1500), IP routing, connection pool, SOCKS5 CONNECT, IPv6 |
| FoxrayVpnManager | FoxrayVpnManager.kt | 457 | vmess/vless/ss/trojan import, subscriptions, Xray JSON parse, ping |

### БАЗА ДАННЫХ

| Компонент | Файл | Описание |
|-----------|------|----------|
| AppDatabase | AppDatabase.kt | Room, version 4, 4 migrations |
| SecureMessageEntity | SecureMessageEntity.kt | 12 columns, FTS4 virtual table |
| MessageRepository | MessageRepository.kt | Full CRUD + pagination |
| MessageSearchManager | MessageSearchManager.kt | FTS MATCH search |
| ProfileBackupManager | ProfileBackupManager.kt | AES-256-GCM export/import |
| MessageExporter | MessageExporter.kt | JSON export |
| EncryptedDbHelper | — | SQLCipher |

### БЕЗОПАСНОСТЬ

| Компонент | Описание |
|-----------|----------|
| ScreenSecurityManager | FLAG_SECURE, скриншоты блокируются |
| ClipboardGuard | Автоочистка через 30 секунд |
| DuressPinManager | Два PIN: основной + под давлением |
| MimicryController | Маскировка: калькулятор/плеер/нарды |
| SecureStorage | PBKDF2 + AES-256-GCM SharedPreferences |
| SecurityAudit | root/debug/obfuscation/emulator checks |
| TelegramReporter | Отчёты о статусе и крашах в Telegram |

### UI ЭКРАНЫ (31)

| Экран | Файл | Описание |
|-------|------|----------|
| GameScreen | GameScreen.kt (9508 строк) | Доска нард + чат + HUD |
| MatrixNoGameScreen | MatrixNoGameScreen.kt (665 строк) | PIN, Matrix-стиль, дождь |
| SimpleXFullScreenChat | GameScreen.kt (часть) | Полноэкранный чат |
| DashboardScreen | DashboardScreen.kt | Список чатов |
| OnboardingScreen | OnboardingScreen.kt | 4 шага |
| AppLockScreen | auth/AppLockScreen.kt | PIN + биометрия |
| SettingsScreen | settings/SettingsScreen.kt | Настройки |
| NetworkSettingsScreen | settings/NetworkSettingsScreen.kt | Tor/VPN/V2Ray |
| PrivacySettingsScreen | settings/PrivacySettingsScreen.kt | Duress PIN |
| NotificationSettingsScreen | settings/NotificationSettingsScreen.kt | Уведомления |
| DataUsageScreen | settings/DataUsageScreen.kt | Трафик |
| SnoozeSettingsScreen | settings/SnoozeSettingsScreen.kt | Тихие часы |
| ContactDetailScreen | contacts/ContactDetailScreen.kt | mute/block/clear |
| BlockListScreen | contacts/BlockListScreen.kt | Заблокированные |
| ContactExchangeScreen | contacts/ContactExchangeScreen.kt | QR-обмен |
| MessageDetailScreen | messages/MessageDetailScreen.kt | Статус доставки |
| CreateGroupScreen | groups/CreateGroupScreen.kt | Создать группу |
| GroupInfoScreen | groups/GroupInfoScreen.kt | Инфо о группе |
| GroupSetupFlow | groups/GroupSetupFlow.kt | Настройка группы |
| ProfileEditScreen | profile/ProfileEditScreen.kt | Редактирование |
| ProfileSwitcherScreen | ProfileSwitcherScreen.kt | Слайд-панель |
| CallScreen | calls/CallScreen.kt | Звонок |
| AboutScreen | about/AboutScreen.kt | О приложении |
| PrivacyPolicyScreen | about/PrivacyPolicyScreen.kt | Политика |
| DebugPanel | debug/DebugPanel.kt | Диагностика |
| LogExportScreen | debug/LogExportScreen.kt | Экспорт логов |
| NetworkTestScreen | diagnostics/NetworkTestScreen.kt | Тест сети |
| FilePreviewScreen | files/FilePreviewScreen.kt | Превью файлов |
| GameOverScreen | game/GameOverScreen.kt | Конец игры |
| GameStatsScreen | game/GameStatsScreen.kt | Статистика |
| Language | Language.kt | 6 языков (RU/EN/DE/ES/FR/TR) |

### UI КОМПОНЕНТЫ (35)

ChatMessageBubble, LazyChatList, MessageThread, MatrixKeyboard, EmojiPicker (600 emoji, 6 категорий),
VoiceMessagePlayer, VoiceRecorderPanel, DisappearingTimerConfig, ScheduledMessageComposer,
ReactionPicker, TypingIndicatorView, FileAttachmentButton, ImagePickerButton,
QrGenerator, QrCodeScannerView, ProfileQrShare, AppRatingPrompt, SearchPanel, ChatSearchPanel,
GameBoardPanel, GameDicePanel, GameScorePanel, GameChatPanel, GameControlsPanel,
GameNetworkPanel, GameSettingsPanel, GameThemeToggle, PlayerInfoPanel,
PendingMessageBanner, NotificationSnoozePicker, MessageSearchBar, AccessibilityManager

### АУДИО (8 файлов)

OpusEncoder (DCT), VoiceRecorder, VoiceMessageManager, StreamPlayer, RadioManager,
RadioChannel, RadioUIState, DiceSoundPlayer

### СЕРВИСЫ (12)

FoxrayVpnService (VpnService), FcmPushService (non-FCM), SmpNotificationService,
MessageSyncWorker, MessageExpiryWorker, MessageScheduler, CrashLogHandler,
CrashReporter, NotificationChannels, QuietHoursManager, MimicryController,
AppLogger, LogExporter, AppShortcutManager

### ТЕСТЫ (27 файлов, 16+ компонентов)

GameEngineTest (164 теста, все зелёные), SMPProtocolTest, AppResultTest,
ContactExchangeManagerTest, DoubleRatchetTest, NaClCryptoTest, SimpleXCryptoTest,
FileTransferManagerTest, EmojiPickerTest, CrashLogHandlerTest, NetworkPanelTest,
ChatPanelTest, SMPAgentTest, SMPEndToEndTest, V2RayEmbeddedControllerTest,
TorEmbeddedControllerTest, XFTPClientTest, FoxrayVpnManagerTest,
WebDavBackupTest, TelegramReporterTest, ExampleRobolectricTest, ExampleUnitTest,
GreetingScreenshotTest, GameBoardPanelTest, GameDicePanelTest, GameSettingsPanelTest

## 4. НЕ РЕАЛИЗОВАНО (ПЛАН)

### 🔴 Высокий приоритет
1. Декомпозиция `GameScreen.kt` (9508 → модули по 200-500 строк)
2. Push-уведомления: реальная регистрация FCM/GCM
3. `VoiceMessagePlayer` → интеграция с `VoiceRecorder`
4. Редактирование/удаление отправленных сообщений
5. Интеграционный E2E-тест: Tor→V2Ray→SMP send+recv
6. `SimpleXEmbeddedController` → передача onion-адресов в SMPAgent (сделано)

### 🟡 Средний приоритет
7. `ScheduledMessageComposer` → интеграция с `MessageScheduler`
8. Темы: динамическая смена (светлая/тёмная)
9. Мультимедиа: видео-плеер, аудио-звонки (WebRTC)
10. `DecentralizedGroup` → полная mesh-маршрутизация без центрального сервера
11. Автономный режим: полная офлайн-синхронизация
12. Миграция БД: версия 4 → 5 (новые индексы, метаданные)
13. GitHub Actions CI/CD
14. Telegram Bot: настройка реальной обратной связи

### 🟢 Низкий приоритет
15. `TransportAppStore` UI — экран магазина приложений
16. `AccessibilityManager` — полная поддержка
17. `PerformanceOptimizer` — профайлинг
18. `BandwidthMonitor` UI
19. `WebDavBackup` — реальное облачное хранилище
20. Исправление deprecation warnings (`Icons.AutoMirrored`, `Locale`, `Char.toInt()`)
21. Robolectric тесты: SDK 36 совместимость

## 5. НАЙДЕННЫЕ ДЕФЕКТЫ (АУДИТ)

| Файл | Строка | Проблема |
|------|--------|----------|
| GameScreen.kt | 1233, 4614, 5389, 5845 | `Icons.Filled.Send/ArrowBack/ArrowForward` → `Icons.AutoMirrored.Filled.*` |
| GameScreen.kt | 1762, 1786, 3073, 3097 | `Icons.Filled.ArrowBack/ArrowForward` deprecated |
| V2RayEmbeddedController.kt | 262 | `String?` вместо `String` (nullable) |
| TorProxyClient.kt | 188 | Inferred `Nothing?` вместо `String` |
| DecoyCalculatorActivity.kt | 142 | `Char.toInt()` deprecated → `Char.code` |
| MessageExporter.kt | 31, 36 | `Locale(String)` deprecated |
| CrashLogHandler.kt | 119 | `Notification.id` deprecated |
| CrashReporter.kt | 124 | `Notification.id` deprecated |
| GameViewModel.kt | 522, 603, 1764 | `Locale(String)` deprecated |
| FoxrayVpnService.kt | 99 | `stopForeground(Boolean)` deprecated |

## 6. СТАТИСТИКА

| Метрика | Значение |
|---------|----------|
| Всего .kt файлов | ~130 |
| Самая большая строка | 9508 (GameScreen.kt) |
| Тестов | 27 файлов, 164+ тестов |
| Сборка | Gradle 9.5.1, Kotlin 2.0.21, AGP 9.1.1 |
| API | minSdk 24, targetSdk 34, compileSdk 36 |
| Размер APK | 102.6 MB (debug) |
| applicationId | com.aistudio.crazybackgammon.vymkwq |
| Циклов разработки | 48+ |
| Зависимости | 35 библиотек |
| БД | Room, 2 таблицы, FTS4, 4 миграции |
| Криптография | NaCl, X3DH, Double Ratchet, AES-256-GCM, PBKDF2 |
| Сеть | SMP, Tor, V2Ray/Xray, SOCKS5, DoH, XFTP |
| Языки UI | RU, EN, DE, ES, FR, TR (6) |

## 7. ЗАКЛЮЧЕНИЕ

✅ BUILD SUCCESSFUL — compileDebugKotlin + test без ошибок  
✅ Все 164+ тестов зелёные  
✅ Сеть: 95% реальный код (Salsa20/Poly1305 написаны вручную, SMP — по спецификации, Tor/V2Ray запускаются как subprocess)  
✅ VPN: TUN-интерфейс с полной маршрутизацией IPv4+IPv6  
🔜 Ожидает: E2E-интеграции, доработок UI/UX, развёртывания CI/CD
