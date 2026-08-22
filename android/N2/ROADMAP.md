# ROADMAP — ПЛАН ЭВОЛЮЦИИ N2
## Стратегический план развития децентрализованного P2P-стека

---

## ТЕКУЩЕЕ СОСТОЯНИЕ

### ✅ Реализовано

#### Криптографическое ядро
- [x] **NaCl crypto_box** (X25519 ECDH + XSalsa20-Poly1305) — ручная реализация
- [x] **X3DH** — 4-обменный протокол Диффи-Хеллмана
- [x] **Double Ratchet** — цепочки KDF_RK / KDF_CK, skipped messages
- [x] **AES-256-GCM** — шифрование контейнера SimpleX
- [x] **PBKDF2WithHmacSHA256** — защита PIN (10000 итераций)
- [x] **MessageDigest.isEqual** — constant-time MAC сравнение
- [x] **Salsa20 Quarter/Double Rounds** — ручная имплементация
- [x] **Poly1305** — GF(2^130-5) polynomial MAC

#### Транспортный уровень
- [x] **SMPClient** — TLS 1.3 + SPKI pinning, wait/notify reader
- [x] **SMPAgent** — сессии, контакты, группы, E2EE, XFTP
- [x] **XFTPClient** — TLS+SPKI, FNEW/FPUT/FGET/FDEL
- [x] **SMPProtocol** — бинарный wire, transport blocks 16KB
- [x] **TorProxyClient** — ручной SOCKS5, exponential backoff, DoH
- [x] **V2RayEmbeddedController** — Xray subprocess, health-check, auto-restart
- [x] **Socks5Chain** — multi-hop Tor→V2Ray
- [x] **TransportProvider** — P2P singleton, dual SOCKS5 handshake
- [x] **TorP2PManager** — ServerSocket, keep-alive, peer connections
- [x] **FoxrayVpnService** — TUN (MTU 1500), IPv4+IPv6, SOCKS5 CONNECT
- [x] **FoxrayVpnManager** — vmess/vless/ss/trojan import, subscriptions

#### Protocol Stack (новый, июль 2026)
- [x] **ProtocolRegistry** — 25+ протоколов, категории, конфигурация
- [x] **NetworkAutoDetector** — детекция сети, 8 стратегий обхода, benchmark
- [x] **ProtocolOrchestrator** — автопилот DETECT→TRANSPORT→STORAGE→MESH→RUNNING
- [x] **ArchiveCloud** — P2P хранилище, SHA-256, DHT репликация, HTTP API
- [x] **NodeMeshManager** — P2P mesh, Kademlia DHT, broadcast
- [x] **ProtocolSettingsScreen** — Compose UI, 5 вкладок, конфиг-диалоги

#### База данных
- [x] **Room** — 2 таблицы, FTS4, 4 миграции
- [x] **MessageRepository** — Full CRUD + pagination
- [x] **MessageSearchManager** — FTS MATCH search
- [x] **ProfileBackupManager** — AES-256-GCM export/import
- [x] **MessageExporter** — JSON export

#### Безопасность
- [x] **ScreenSecurityManager** — FLAG_SECURE
- [x] **ClipboardGuard** — автоочистка 30 секунд
- [x] **DuressPinManager** — два PIN + PBKDF2
- [x] **MimicryController** — маскировка (калькулятор/плеер/нарды)
- [x] **SecureStorage** — PBKDF2 + AES-256-GCM
- [x] **SecurityAudit** — root/debug/obfuscation/emulator
- [x] **TelegramReporter** — crash-репорты в Telegram

#### UI (31 экран)
- [x] **GameScreen** — нарды + чат + HUD (9508 строк)
- [x] **MatrixNoGameScreen** — PIN, Matrix-rain
- [x] **DashboardScreen** — список чатов
- [x] **Settings** — 6 экранов (Network, Privacy, Notifications, Data, Snooze, Protocol)
- [x] **Onboarding** — 4 шага
- [x] **Auth** — AppLock (PIN + биометрия)
- [x] **Contacts** — ContactDetail, BlockList, ContactExchange (QR)
- [x] **Groups** — CreateGroup, GroupInfo, GroupSetupFlow
- [x] **Chat** — MessageDetail, FilePreview, CallScreen
- [x] **Profile** — ProfileEdit, ProfileSwitcher
- [x] **Debug** — DebugPanel, LogExport, NetworkTest
- [x] **Game** — GameOver, GameStats

#### UI Компоненты (35+)
- [x] **MatrixKeyboard** — изолированная клавиатура
- [x] **ChatMessageBubble** — пузырьки сообщений
- [x] **EmojiPicker** — 600 emoji, 6 категорий
- [x] **VoiceMessagePlayer/Recorder** — Opus аудио
- [x] **QrGenerator/Scanner** — QR-обмен контактами
- [x] **GameBoard/Dice/Score/Controls/Chat/Network** — игровые панели

#### Аудио (8 файлов)
- [x] **OpusEncoder** — DCT-сжатие
- [x] **VoiceRecorder/Player/Manager** — голосовые сообщения
- [x] **RadioManager/Channel** — радио-потоки (включая Armageddon FM)
- [x] **DiceSoundPlayer** — звук броска кубиков
- [x] **StreamPlayer** — потоковый аудио-плеер

#### Сервисы (12)
- [x] **FoxrayVpnService** — TUN интерфейс
- [x] **FcmPushService** — push-уведомления (non-FCM)
- [x] **MessageSyncWorker, MessageExpiryWorker, MessageScheduler** — фоновые задачи
- [x] **CrashLogHandler, CrashReporter** — обработка ошибок
- [x] **NotificationChannels** — каналы уведомлений
- [x] **MimicryController** — смена маскировки

#### Тесты (27 файлов, 164+ тестов)
- [x] **GameEngineTest** — 164 теста (все зелёные)
- [x] **SMPProtocolTest, NaClCryptoTest, DoubleRatchetTest**
- [x] **SMPAgentTest, SMPEndToEndTest**
- [x] **TorEmbeddedControllerTest, V2RayEmbeddedControllerTest**
- [x] **XFTPClientTest, FoxrayVpnManagerTest, WebDavBackupTest**
- [x] **TelegramReporterTest, UI screenshot tests**

#### Инфраструктура
- [x] Gradle 9.5.1, Kotlin 2.0.21, AGP 9.1.1
- [x] minSdk 24, targetSdk 34, compileSdk 36
- [x] 35 библиотек (Compose, Room, Navigation, Hilt, Ktor, etc.)
- [x] ProGuard/R8
- [x] Deep Link Handler (simplex://)
- [x] 6 языков (RU, EN, DE, ES, FR, TR)

---

## ЭТАПЫ РАЗВИТИЯ

### [ФАЗА 1] ПРОИЗВОДСТВЕННАЯ СБОРКА И РЕЛИЗ
**Приоритет: КРИТИЧЕСКИЙ**

| Задача | Описание | Файлы |
|--------|----------|-------|
| **1.1** Подготовка бинарников | Автоматизация загрузки Tor/Xray/SimpleX бинарников для arm64 + x86_64 | `assets/bin/` |
| **1.2** Release подпись | Настройка signing config для release-сборки | `build.gradle.kts` |
| **1.3** ProGuard/R8 | Полная обфускация, минификация, оптимизация | `proguard-rules.pro` |
| **1.4** APK split | Разделение по ABI (arm64-v8a, armeabi-v7a, x86_64) | `build.gradle.kts` |
| **1.5** Android App Bundle | Переход на AAB для Google Play / FDroid | `build.gradle.kts` |
| **1.6** CI/CD | GitHub Actions: сборка, тесты, подпись, deploy | `.github/workflows/` |
| **1.7** Code signing | Подпись APK/AAB, настройка хранилища ключей | `keystore/` |

**Результат:** Подписанный релизный APK/AAB, готовый к публикации.

---

### [ФАЗА 2] ЗАВЕРШЕНИЕ СЕТЕВОГО СТЕКА
**Приоритет: ВЫСОКИЙ**

| Задача | Описание | Файлы |
|--------|----------|-------|
| **2.1** Orbot SDK | Интеграция tor-android-binary через Orbot SDK вместо ProcessBuilder | `TorEmbeddedController.kt` |
| **2.2** Native Xray JNI | JNI-биндинги Xray core для уменьшения накладных расходов | `V2RayEmbeddedController.kt` |
| **2.3** SimpleX CLI IPC | Полноценная интеграция с SimpleX Chat через stdin/stdout IPC | `SimpleXEmbeddedController.kt` |
| **2.4** E2E интеграционный тест | Сквозной тест: Tor→V2Ray→SMP send+recv между двумя устройствами | `test/` |
| **2.5** Multi-hop цепочки | Динамическая маршрутизация через N прокси (Tor→V2Ray→I2P→...) | `Socks5Chain.kt` |
| **2.6** Auto-failover | Автоматическое переключение между транспортами при сбое | `NetworkOrchestrator.kt` |
| **2.7** Onion балансировка | Поддержка нескольких onion-адресов, round-robin | `TorP2PManager.kt` |
| **2.8** NetworkAutoDetector → продакшн | Асинхронная фоновая детекция с кэшированием, WebSocket/DoH, GPS-контекст | `NetworkAutoDetector.kt` |
| **2.9** ProtocolOrchestrator → foreground service | Запуск как Android Foreground Service с постоянным уведомлением | `ProtocolOrchestrator.kt` |

**Результат:** Полноценный работающий P2P-стек с реальными бинарниками, интеграционными тестами, failover.

---

### [ФАЗА 3] ДЕЦЕНТРАЛИЗОВАННЫЕ ПРОТОКОЛЫ
**Приоритет: ВЫСОКИЙ**

| Задача | Описание | Файлы |
|--------|----------|-------|
| **3.1** Protocol backend workers | Фоновые службы для каждого протокола (TorWorker, I2PWorker, WireGuardWorker) | `protocols/ workers/` |
| **3.2** ArchiveCloud → production | Реальная DHT-репликация, pinning-сервис, WebUI | `ArchiveCloud.kt` |
| **3.3** NodeMesh → Kademlia full | Полноценная Kademlia: bucket split, lookup iterative, RPC | `NodeMeshManager.kt` |
| **3.4** IPFS gateway | Встроенный IPFS gateway (чтение/запись через IPFS) | `protocols/storage/` |
| **3.5** Matrix bridge | Подключение к Matrix HS через MXID, синхронизация контактов | `protocols/messaging/` |
| **3.6** Tox core | JNI-биндинги toxcore для P2P-звонков и сообщений | `protocols/messaging/` |
| **3.7** Nostr relay client | Подписка на релеи, публикация заметок, DMs | `protocols/messaging/` |
| **3.8** BitTorrent DHT client | Поиск пиров через Mainline DHT, magnet-ссылки | `protocols/storage/` |

**Результат:** Работающие P2P-протоколы с UI-управлением и фоновыми службами.

---

### [ФАЗА 4] UI/UX РЕФАКТОРИНГ
**Приоритет: ВЫСОКИЙ**

| Задача | Описание | Файлы |
|--------|----------|-------|
| **4.1** Декомпозиция GameScreen | 9508 строк → модули по 200-500 строк | `GameScreen.kt` |
| **4.2** Material 3 Dynamic Colors | Полная поддержка Material You / Monet | `Theme.kt` |
| **4.3** Темная тема | Полноценная темная тема + переключение | `Theme.kt` |
| **4.4** AccessibilityManager | Content descriptions, TalkBack, фокус | `AccessibilityManager.kt` |
| **4.5** BandwidthMonitor UI | Графики трафика в реальном времени | `ui/components/` |
| **4.6** TransportAppStore UI | Магазин транспортных приложений | `screens/` |
| **4.7** Анимации | Lottie-анимации для переходов, загрузки, статусов | `res/` |
| **4.8** NotificationSnoozePicker UI | Кастомный пикер тихих часов | `ui/components/` |
| **4.9** Adaptive layout | Планшетная ориентация (landscape, split-screen) | все экраны |
| **4.10** PerformanceOptimizer | Lazy list prefetch, image cache, memory profiler | `PerformanceOptimizer.kt` |

**Результат:** Современный адаптивный UI, декомпозированный код, Material You.

---

### [ФАЗА 5] СООБЩЕНИЯ И МЕДИА
**Приоритет: СРЕДНИЙ**

| Задача | Описание | Файлы |
|--------|----------|-------|
| **5.1** Редактирование сообщений | Edit message в истории (с меткой edited) | `MessageRepository.kt` |
| **5.2** Удаление сообщений | Delete для всех / для себя | `MessageRepository.kt` |
| **5.3** Исчезающие сообщения | Таймеры 5s/30s/1m/1h/1d, фоновая очистка Worker | `DisappearingConfig.kt` |
| **5.4** ScheduledMessageComposer → Worker | Интеграция композера с MessageScheduler | `ScheduledMessageComposer.kt` |
| **5.5** VoiceMessagePlayer → VoiceRecorder | Интеграция плеера с реальным рекордером | `VoiceMessagePlayer.kt` |
| **5.6** WebRTC звонки | Audio/video звонки через Matrix VoIP | `CallScreen.kt` |
| **5.7** Opus аппаратное кодирование | Использование MediaCodec для Opus вместо DCT | `OpusEncoder.kt` |
| **5.8** Push-уведомления | Реальная регистрация FCM/GCM, foreground socket | `FcmPushService.kt` |
| **5.9** DecentralizedGroup | Полная peer-to-peer группа без сервера | `DecentralizedGroup.kt` |
| **5.10** E2E search | Поиск по зашифрованным сообщениям (FTS4 на клиенте) | `E2EESearchManager.kt` |

**Результат:** Паритет с коммерческими мессенджерами (Telegram, Signal) по функциям чата.

---

### [ФАЗА 6] БАЗА ДАННЫХ И ХРАНЕНИЕ
**Приоритет: СРЕДНИЙ**

| Задача | Описание | Файлы |
|--------|----------|-------|
| **6.1** SQLCipher | Полное шифрование всех БД SQLite через SQLCipher | `AppDatabase.kt` |
| **6.2** Room миграция v4→v5 | Новые индексы, метаданные, FTS5 | `AppDatabase.kt` |
| **6.3** Multi-profile БД | Изолированные БД для каждого профиля | `ProfileManager.kt` |
| **6.4** Encrypted SharedPreferences | Замена SharedPreferences на EncryptedSharedPreferences | `SecureStorage.kt` |
| **6.5** Auto-backup | Автоматический бэкап БД по расписанию + перед обновлением | `ProfileBackupManager.kt` |
| **6.6** Cloud backup | WebDav Backup → Google Drive / NextCloud | `WebDavBackup.kt` |

**Результат:** Полностью зашифрованная БД с поддержкой мультипрофилей.

---

### [ФАЗА 7] БЕЗОПАСНОСТЬ (ПРОДВИНУТАЯ)
**Приоритет: СРЕДНИЙ**

| Задача | Описание | Файлы |
|--------|----------|-------|
| **7.1** Android Keystore | Хранение ключей в аппаратном Keystore (TEE) | `SecureStorage.kt` |
| **7.2** Biometric auth | Fingerprint / Face unlock для доступа | `AppLockScreen.kt` |
| **7.3** Remote wipe | Удалённая очистка устройства через Telegram-команду | `TelegramReporter.kt` |
| **7.4** Panic button | Кнопка аварийной очистки на главном экране | `GameScreen.kt` |
| **7.5** Integrity check | Проверка целостности APK (APK signature + hash) | `SecurityAudit.kt` |
| **7.6** SSL pinning | Certificate pinning для SMP серверов | `SMPClient.kt` |
| **7.7** Traffic padding | Фиктивный трафик для маскировки (padding) | `SMPProtocol.kt` |
| **7.8** Fake cover traffic | Фоновый трафик имитации игры/радио при простое | `RadomManager.kt` |

**Результат:** Аппаратная защита ключей, биометрия, remote wipe.

---

### [ФАЗА 8] ИНФРАСТРУКТУРА И ТЕСТИРОВАНИЕ
**Приоритет: НИЗКИЙ**

| Задача | Описание | Файлы |
|--------|----------|-------|
| **8.1** E2E тесты (Robolectric + Espresso) | UI-тесты для всех 31 экрана | `test/ androidTest/` |
| **8.2** Performance benchmark | JMH-тесты для криптографии, БД, сети | `test/` |
| **8.3** Security pen-test | frida, objection, нативный аудит | — |
| **8.4** Сеть: stress-test | Симуляция обрыва, таймаутов, DPI | `test/` |
| **8.5** FDroid публикация | Сборка для FDroid (без проприетарных зависимостей) | `.fdroid/` |
| **8.6** Документация | API-документация, Javadoc, архитектурные ADR | `docs/` |
| **8.7** Localization | Crowdin-интеграция, 12 языков | `res/` |

**Результат:** Enterprise-grade тестирование, CI/CD, FDroid.

---

### [ФАЗА 9] ДОЛГОСРОЧНЫЕ ИССЛЕДОВАНИЯ
**Приоритет: НИЗКИЙ (R&D)**

| Задача | Описание |
|--------|----------|
| **9.1** I2P ZeroNet интеграция | P2P-сайты внутри приложения |
| **9.2** Ethereum / libp2p | Децентрализованные ID (DID) |
| **9.3** AI-агент | Gemini AI для авто-ответов, шифрования, анализа угроз |
| **9.4** Децентрализованный рейтинг | Система репутации пиров в NodeMesh |
| **9.5** Квантово-устойчивая криптография | Post-quantum (Kyber, Dilithium) |
| **9.6** Bluetooth / WiFi Direct | Оффлайн P2P-связь без интернета |
| **9.7** Мультиплатформа | iOS (KMP), Desktop (Compose Desktop) |

**Результат:** Исследовательские прототипы, форки, proof-of-concept.

---

## ДОРОЖНАЯ КАРТА (ВРЕМЕННАЯ ШКАЛА)

```
ФАЗА 1: Производственная сборка      ████████░░░░░░  2-3 недели
ФАЗА 2: Сетевой стек (production)     ████████░░░░░░  3-4 недели
ФАЗА 3: Децентрализованные протоколы  ██████░░░░░░░░  4-6 недель
ФАЗА 4: UI/UX рефакторинг             ████████░░░░░░  3-4 недели
ФАЗА 5: Сообщения и медиа             ██████░░░░░░░░  4-6 недель
ФАЗА 6: База данных и хранение        ████░░░░░░░░░░  2-3 недели
ФАЗА 7: Безопасность (продвинутая)    ████░░░░░░░░░░  3-4 недели
ФАЗА 8: Инфраструктура и тесты        ██████░░░░░░░░  4-6 недель
ФАЗА 9: R&D (долгосрочные)            ██░░░░░░░░░░░░  постоянно
                                       0    5    10   15 недель
```

---

## КЛЮЧЕВЫЕ МЕТРИКИ УСПЕХА

| Метрика | Текущее | Цель |
|---------|---------|------|
| Размер APK | 102 MB | <40 MB (release, split) |
| Время старта сети | N/A (нет бинарников) | <10 сек (Tor bootstrap) |
| Пропускная способность чата | N/A | >100 msg/sec через Tor |
| Покрытие тестами | 164 теста | >500 тестов |
| UI тесты | 5 screenshot | 31 экран (Espresso) |
| Языки | 6 | 12 |
| Протоколы | 25+ registered | >30 active |
| Профили | 1 | Multi-profile sandboxing |
| CI/CD | None | Full pipeline (lint+test+build+sign+deploy) |

---

*План актуален на июль 2026 года. 48+ циклов разработки завершено, 164+ тестов зелёные, 130+ файлов, 31 экран.*
