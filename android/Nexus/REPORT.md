# NexusChat — Полный отчёт по проекту

## 1. Проделанная работа (20 циклов)

### Цикл 1 — V2RayService: удаление симуляции
- Удалён `simulateV2Ray()`, добавлен auto-download через `BinaryDownloader` + проверка порта SOCKS5
- Реальный запуск V2Ray-процесса с генерацией config.json

### Цикл 2 — XRaySubprocess: реальный запуск
- Удалён silent no-op, добавлен AutoDownload + port verification
- Поддержка VLESS, VMESS, Shadowsocks, Trojan протоколов

### Цикл 3 — TorService: embedded libtor.so
- Удалён silent fallback, auto-download бинарника, fail если порты не слушают
- Full torrc конфиг: ExitNodes, StrictNodes, CircuitBuildTimeout, HiddenService

### Цикл 4 — TransportManager: fail-closed
- Удалён DIRECT_TCP fallback — `IllegalStateException` если нет транспортов
- Multi-transport polling (Tor, Snowflake, DomainFront, WireGuard) с auto-failover

### Цикл 5 — DoubleRatchet: реальный X25519
- `generateDH()` через BouncyCastle X25519 вместо random bytes
- Полная имплементация: KDF цепочки, AES-GCM шифрование, skip message keys, header encryption

### Цикл 6 — DnsOverTor: DNS через Tor SOCKS5
- Локальный DNS-сервер на 5354, резолвинг через Tor SOCKS5 вместо UDP 1.1.1.1
- Кэширование + парсинг DNS-пакетов вручную

### Цикл 7 — Obfs4Transport: AES/CTR вместо XOR
- obfuscateBlock() через AES/CTR с 32-байтным ключом вместо тривиального XOR
- Полный handshake (168 байт) + pipe obfuscation

### Цикл 8 — SmpServerService: бинарный SMP протокол
- Магия `0x53 0x4D 0x50 0x03` + DataInputStream framing
- Поддержка JSON и binary режимов, очереди, подписки, ключи

### Цикл 9 — WireGuardConfig: VpnService tunnel
- Полный lifecycle: wg-quick up/down, X25519 keygen, конфиг для wg-quick

### Цикл 10 — BinaryDownloader: auto-download по ABI
- Tor, V2Ray, XRay, obfs4proxy; определение ABI; скачивание .so и .zip

### Цикл 11-14 — TransportIntegrationTest: 15 тестов
- DNS, Obfs4, ChainProxy, DoubleRatchet, WireGuard, SMP framing, TrafficPadding

### Цикл 15 — CoverTrafficScheduler
- Генерация dummy-трафика с имитацией HTTPS/DNS/QUIC/NTP/HTTP2

### Цикл 16 — ErrorRecoveryManager
- Exponential backoff + retry для всех 8 ServiceType (Tor, V2Ray, XRay, SMP, Snowflake, ChainProxy, WireGuard, DNS)

### Цикл 17 — TorBridgeConfig
- Динамический поиск плагинов (filesDir/bin, /system/bin, nativeLibDir)
- Генерация torrc с UseBridges 1

### Цикл 18 — TrafficPadding: фикс padToCell
- System.arraycopy вместо rng.nextBytes для корректного padding

### Цикл 19 — assembleDebug SUCCESS
- Первая успешная сборка (71.3 MB APK)

### Цикл 20 — Git push
- `DarkPushkin/NexusChat-Android` (private)

### Цикл 21 — UI: добавлены все недостающие функции app.js
- 25+ JS функций: openModal, toggleLayer, clearLog, openQR, setRetention, triggerPanic
- BinaryDownload overlay + BinaryProgressBridge
- fix: пустые catch-блоки, мёртвые CDN (cbor-js, lz-string)
- Comments: архитектурные блок-комментарии в ключевые файлы

### Цикл 22 — BIP39 кошелёк (Bip39Wallet.kt)
- Полная имплементация: BIP39 mnemonic, PBKDF2 seed, BIP32 master key, BIP44 (BTC/ETH), SLIP-0010 Ed25519, X25519, Ed25519 sign/verify
- WalletBridge + JS интерфейс (AndroidWallet)
- UI вкладка Wallet в index.html с генерацией/восстановлением/подписью

### Цикл 23 — BinaryDownloader: реальные URL + hash verification + progress
- Tor URL: guardianproject/tor-android (реальные бинарники)
- V2Ray: v2fly-core (существующие релизы)
- XRay: XTLS (существующие релизы)
- obfs4proxy: gitlab.torproject.org
- SHA256 верификация загруженных файлов
- Progress callback для BinaryProgressBridge

### Цикл 24 — MainActivity: полный lifecycle всех сервисов
- Запуск TorService, V2RayService, SmpServerService
- Регистрация ErrorRecoveryManager для всех 8 ServiceType
- ErrorRecovery handler: автоматический перезапуск сервисов
- CoverTrafficScheduler с реальными HTTP запросами через TransportManager
- DnsOverTor автозапуск
- WalletBridge регистрация

### Цикл 25 — NexusVpnService: VPN tunnel без root (non-root)
- VpnService extends android.net.VpnService (API)
- TUN интерфейс 10.100.1.2/24, маршрутизация через Tor SOCKS5
- Foreground service с нотификацией
- Manifest: объявлен как BIND_VPN_SERVICE
- WireGuardConfig: удалён wg-quick (root), работает через VpnService

### Цикл 26 — TransportManager: TrafficObfuscator с реальным паддингом
- Случайный User-Agent (5 вариантов)
- Случайный Accept-Language
- X-Forwarded-For со случайным IP
- Реальный body padding (32-288 байт случайных данных)
- Timing jitter через TrafficPadding.getJitteredDelay()
- TrafficPadding.getInstance() интегрирован
- ProtocolObfuscator.getInstance() инициализирован

### Цикл 27 — TorControlConnection удалён (дубликат)
- TorService имеет собственный inline control port (AUTHENTICATE, NEWNYM, GETINFO)
- TorControlConnection.kt — 72 строки дублирующегося кода → удалён

### Цикл 28 — BootReceiver: запуск всех сервисов
- TorService, V2RayService, SmpServerService на boot
- ErrorRecoveryManager, ChainProxy, DnsOverTor, CoverTrafficScheduler

### Цикл 29 — Сборка: assembleDebug SUCCESS (0 ошибок)
- Исправлено 11 ошибок компиляции
- PKCS5S2ParametersGenerator.init() API fix
- Bip39Wallet: instance methods instead of companion
- Hardened index Int overflow fix (0x80000000)
- VpnService: merged companion objects
- TransportManager: RequestBody extension fix

---

## 2. Структура проекта

```
nexuschat/
├── app/
│   ├── build.gradle              # API 34, Gradle 8.6, JDK 17, minSdk 26
│   ├── src/
│   │   ├── main/
│   │   │   ├── AndroidManifest.xml      # Permissions: INTERNET, AUDIO, FOREGROUND_SERVICE, POST_NOTIFICATIONS
│   │   │   ├── assets/public/
│   │   │   │   ├── index.html           # WebView UI (1431 строк) — полный интерфейс
│   │   │   │   ├── app.js               # Frontend логика (1096 строк) — крипто, SMP, WebRTC, UI
│   │   │   │   ├── sw.js                # Service Worker (72 строки) — PWA кэширование
│   │   │   │   └── manifest.json        # PWA манифест
│   │   │   ├── java/com/nexuschat/app/
│   │   │   │   ├── MainActivity.kt      # (283 строки) — WebView + JS bridges + service lifecycle
│   │   │   │   ├── NexusChatApp.kt      # (90 строк) — Application: Conscrypt, Tink, EncryptedPrefs
│   │   │   │   ├── bridges/
│   │   │   │   │   ├── Bridges.kt       # (377 строк) — TorBridge, TailscaleBridge, KeystoreBridge,
│   │   │   │   │   │                          BiometricBridge, NotificationBridge, FileBridge,
│   │   │   │   │   │                          ClipboardBridge, SystemBridge, SnowflakeBridge,
│   │   │   │   │   │                          BinaryProgressBridge
│   │   │   │   │   └── WebRtcBridge.kt  # (288 строк) — WebRTC: P2P audio calls, STUN/TURN
│   │   │   │   ├── crypto/
│   │   │   │   │   ├── CryptoManager.kt # (173 строки) — AES-256-GCM через Google Tink
│   │   │   │   │   ├── DoubleRatchet.kt # (218 строк) — X3DH + Double Ratchet (X25519, AES-GCM, HMAC-SHA256)
│   │   │   │   │   └── SmpProtocol.kt   # (222 строки) — SimpleX SMP v3 frame builder/parser
│   │   │   │   ├── receivers/
│   │   │   │   │   └── BootReceiver.kt  # (18 строк) — Автозапуск Tor + SMP при загрузке
│   │   │   │   └── services/
│   │   │   │       ├── TorService.kt           # (287 строк) — Embedded Tor daemon
│   │   │   │       ├── SmpServerService.kt     # (484 строки) — SMP queue server
│   │   │   │       ├── V2RayService.kt         # (313 строк) — V2Ray proxy
│   │   │   │       ├── XRaySubprocess.kt       # (172 строки) — XRay-core proxy
│   │   │   │       ├── TransportManager.kt     # (238 строк) — Multi-transport router
│   │   │   │       ├── AudioRelay.kt           # (218 строк) — PCM audio capture/playback over WS
│   │   │   │       ├── BinaryDownloader.kt     # (152 строки) — Auto-download binaries
│   │   │   │       ├── SnowflakeTransport.kt   # (271 строка) — Snowflake WebRTC pluggable transport
│   │   │   │       ├── WireGuardConfig.kt      # (176 строк) — WG tunnel lifecycle
│   │   │   │       ├── DnsOverTor.kt           # (269 строк) — DNS resolver через Tor
│   │   │   │       ├── ChainProxy.kt           # (214 строк) — Multi-hop proxy chains
│   │   │   │       ├── TransportChainBuilder.kt# (169 строк) — Transport chain builder
│   │   │   │       ├── TorBridgeConfig.kt      # (149 строк) — Tor bridge config
│   │   │   │       ├── TorControlConnection.kt # (72 строки) — Tor control protocol
│   │   │   │       ├── Obfs4Transport.kt       # (172 строки) — obfs4 pluggable transport
│   │   │   │       ├── MeekTransport.kt        # (186 строк) — meek transport
│   │   │   │       ├── DomainFronting.kt       # (126 строк) — CDN domain fronting
│   │   │   │       ├── CoverTrafficScheduler.kt# (135 строк) — Dummy traffic generator
│   │   │   │       ├── TrafficPadding.kt       # (127 строк) — Traffic flow obfuscation
│   │   │   │       ├── ProtocolObfuscator.kt   # (135 строк) — TLS fingerprint mimicry
│   │   │   │       └── ErrorRecoveryManager.kt # (135 строк) — Exponential backoff recovery
│   │   │   └── res/                    # Ресурсы: themes, strings, colors, drawable icons
│   │   └── test/                       # Unit-тесты (5 файлов, 631 строка)
│   └── androidTest/                    # Интеграционные тесты (1 файл, 156 строк)
├── build.gradle                        # Root build.gradle
├── settings.gradle
└── gradle/
```

### Архитектурные слои

```
┌─────────────────────────────────────────────┐
│            WebView UI (index.html)           │
│           app.js (крипто, SMP, UI)          │
├─────────────────────────────────────────────┤
│          JavaScript Bridges (@JavascriptInterface)
│  AndroidTor AndroidTailscale AndroidKeystore │
│  AndroidBiometric AndroidNotifications      │
│  AndroidFiles AndroidClipboard AndroidSystem│
│  AndroidSnowflake AndroidWebRTC            │
│  AndroidBinary                              │
├─────────────────────────────────────────────┤
│          Kotlin Services (Сервисы)          │
│  TorService  SmpServerService  V2RayService  │
│  TransportManager  AudioRelay  WireGuard     │
├─────────────────────────────────────────────┤
│       Crypto / Network / Transport Layer     │
│  DoubleRatchet  SmpProtocol  DnsOverTor      │
│  Obfs4Transport  MeekTransport  Snowflake    │
│  DomainFronting  ChainProxy  CoverTraffic    │
│  TrafficPadding  ProtocolObfuscator          │
├─────────────────────────────────────────────┤
│          BinaryDownloader / ErrorRecovery    │
│          TorBridgeConfig / TorControlConn    │
├─────────────────────────────────────────────┤
│        Android OS Layer                      │
│  Keystore  Biometric  VpnService  Notif      │
│  ForegroundService  Permissions              │
└─────────────────────────────────────────────┘
```

---

## 3. Краткое руководство пользователя

### Установка
1. Скомпилировать: `./gradlew assembleDebug`
2. APK: `app/build/outputs/apk/debug/app-debug.apk`
3. Установить на Android 8.0+ (API 26+) с включёнными разрешениями

### Первый запуск
1. Введите PIN (по умолчанию: `123456`) для разблокировки
2. Дождитесь загрузки бинарников (Tor, V2Ray, XRay, obfs4proxy) — прогресс-бар
3. После инициализации откроется Dashboard

### Основные экраны

**DASH (Главная)**
- Статус: Tor, Tailscale, .onion, SMP сервер
- Traffic chart (трафик за 24ч)
- Server log (консоль)

**CHATS (Чаты)**
- Список контактов с E2E шифрованием
- Отправка сообщений через SMP протокол
- Голосовые звонки через WebRTC (DTLS-SRTP)
- Файлы (XFTP encrypted chunked transfer)

**VAULT (Медиа)**
- Вкладки: Images, Docs, Audio, XFTP
- Зашифрованное файловое хранилище

**CONFIG (Настройки)**
- Tor: SOCKS5, Control Port, Bridges, Exit nodes
- Tailscale: Auth key, Peers, WireGuard
- SMP: Host, Port, Fingerprint, Auth token
- XFTP: Storage path, Max size, Chunk size
- Security: PIN, Keys (X25519 + Ed25519), Panic mode

### Panic Mode
Кнопка ☢ в AppBar — мгновенное уничтожение всех ключей, сообщений, очередей, конфига с прогресс-индикацией.

---

## 4. План дальнейшей эволюции

### Фаза 1: Ядро (приоритет: высокий)

**1.1 Интеграция криптооблака (CryptoCloud)**
- E2E encrypted backup ключей и конфига на S3/IPFS/Storj
- Восстановление по seed-фразе (BIP39)
- Синхронизация .onion identity между устройствами
- Техника: AES-256-GCM master key, wrapped by BIP39 mnemonic

**1.2 BIP39 криптокошелёк**
- Генерация mnemonic (12/24 слова) из энтропии SecureRandom
- Деривация ключей: BIP32 (X25519) + BIP44 (Bitcoin/ETH) + Tor identity
- Адрес для пожертвований в чате
- Подписание сообщений Monero-style (RingCT lite?)
- Техника: bitcoinj или bouncycastle BIP39

### Фаза 2: Сеть (приоритет: средний)

**2.1 P2P Маркетплейс**
- Децентрализованный маркет на базе SMP очередей
- Категории: цифровые товары, услуги, контент
- Эскроу через мультиподпись (2-of-3: продавец/покупатель/арбитр)
- Рейтинговая система через SMP подписки
- Web-of-trust граф доверия

**2.2 Группы и Каналы**
- SMP broadcast queues (один-ко-многим)
- Администрирование: mute, ban, moderator roles
- Каналы (read-only broadcast)
- Подписка на каналы по .onion адресу

**2.3 AI Боты для модерации**
- Локальный TinyLLM (llama.cpp/mistral-7b GGUF) на устройстве
- Функции:
  - Авто-модерация: фильтрация спама, скама, NSFW в группах
  - Анти-фишинг: детекция подозрительных ссылок
  - Авто-ответы: FAQ бот в каналах
  - Анализ тональности: определение агрессии в чатах
- ONNX Runtime / MediaPipe для on-device ML

### Фаза 3: Приватность (приоритет: средний)

**3.1 i2p integration**
- I2P резолвинг через SAM API
- .i2p скрытые сервисы как альтернатива .onion
- Multi-hop: Tor → I2P → Snowflake

**3.2 Cover Traffic 2.0**
- Реальный трафик (HTTP/2, WebSocket ping/pong) вместо dummy bytes
- Интеграция с реальными CDN: Cloudflare Workers прокси
- Traffic morphing: подстройка под шаблоны YouTube/WhatsApp/Zoom

**3.3 Децентрализованный DNS**
- .bit (Namecoin) или ENS (Ethereum) резолвинг
- SMP адреса вида `user.nexus` вместо `smp://...@...onion`
- On-chain реестр серверов

### Фаза 4: Монетизация (приоритет: низкий)

**4.1 Токен грантов**
- Nostr zaps / Lightning tips
- Микро-платежи за relay bandwidth
- Торговля SMP queue слотами

**4.2 DAO управления**
- Голосование за развитие протокола
- Стейкинг токенов за права модерации в группах
- Децентрализованный арбитраж споров

### Технический долг

- [ ] Интегрировать ErrorRecoveryManager во все сервисы
- [ ] Подключить DnsOverTor к init-цепи (сейчас не запускается)
- [ ] Wire up ChainProxy через TransportManager
- [ ] CoverTrafficScheduler → интеграция с AudioRelay
- [ ] V2RayService не стартуется из MainActivity (добавить startService)
- [ ] Удалить дублирующийся код TorControlConnection (TorService делает то же самое inline)
- [ ] Перевести все синглтоны на Hilt/Dagger для тестируемости
- [ ] Добавить UI для настройки прокси-цепочек
- [ ] Real-time stats polling: traffic chart, uptime, connections
- [ ] WireGuard через Android VpnService (сейчас wg-quick требует root)
