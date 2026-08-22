# Not Gammon (N2) — Техническая спецификация продукта

> **Версия:** 1.1.0  
> **Пакет:** `com.notgammon.app`  
> **Минимальный API:** Android 8.0 (API 26)  
> **Целевой API:** Android 14 (API 34)  
> **Сборка:** 23 июля 2026  
> **APK:** `C:\ApkExport\NotNode.apk` (99 MB)  
> **Стек:** Kotlin 2.0.21 + Jetpack Compose + AGP 9.1.1

---

## 1. Архитектура приложения

### 1.1 Общая структура

```
com.example/
├── MainActivity.kt                 — точка входа, оркестрация старта
├── MyApplication.kt                — Application, инициализация глобальных сервисов
├── DecoyCalculatorActivity.kt      — активность-калькулятор (маскировка)
├── data/                           — данные, сеть, криптография
├── security/                       — безопасность, PIN, экран
├── service/                        — фоновые сервисы
├── tor/                            — Tor embedded controller
├── v2ray/                          — V2Ray (Xray) embedded controller
├── vpn/                            — TUN/VPN сервис
├── smp/                            — SMP протокол (SimpleX Messaging Protocol)
├── xftp/                           — XFTP файловый транспорт
├── crypto/                         — рукописный криптостек
├── ui/
│   ├── screens/                    — экраны (boot, permissions, auth, terminal, ...)
│   ├── navigation/                 — NavHost, NavRoutes
│   ├── viewmodels/                 — ProtocolViewModel, NetworkViewModel, etc.
│   ├── gameprofile/                — система смены игр-маскировок
│   ├── components/                 — переиспользуемые UI-компоненты
│   └── theme/                      — тема оформления
└── evolution/                      — отчёты эволюционных циклов
```

### 1.2 Паттерны

| Паттерн | Где используется |
|---------|-----------------|
| **MVVM** | Все ViewModel, разделение состояния и UI |
| **State Machine** | StartupOrchestrator (12 состояний запуска) |
| **Repository** | SecureStorage, NetworkDefaults |
| **Service Locator** | MyApplication — singletons (bandwidthMonitor, proxyManager) |
| **Chain of Responsibility** | Socks5Chain (цепочка прокси: App → V2Ray → Tor) |
| **Strategy** | GameProfileLoader — подключаемые игры-маскировки |
| **Observer** | StateFlow в StartupOrchestrator, LiveData в ViewModel |
| **Singleton** | DuressPinManager, SecureStorage, Bip39Helper |

### 1.3 Навигация

`NavRoutes.kt` — 13 маршрутов:
- `Boot` → `Permissions` → `Onboarding`/`AppLock` → `TerminalSetup` → `Dashboard`
- Внутри Dashboard: `Chat`, `Game`, `Evolve`, `Settings`, `NetworkDiagnostics`, `FilePreview`, `Debug`

### 1.4 DI (Dependency Injection)

Ручной DI через `ViewModelProvider` + `MyApplication`-singletons. Gradle KSP используется для кодогенерации (Moshi).

---

## 2. Стартовый пайплайн

### 2.1 BootScreen (MATRIX splash)

| Параметр | Значение |
|----------|----------|
| Длительность | 3 секунды |
| Стиль | MATRIX digital rain + CRT scanlines |
| Анимация | Цифровой дождь (катакана + hex), пульсирующее свечение |
| Арт | Пиксельный силуэт админа перед консолью в серверной |
| Детали | Стойки серверов с мигающими LED, кабели между ними |
| Текст | «BOOT» (56sp, CRT-свечение), v1.1.0 · N2 protocol |
| Индикатор | Прогресс-бар в нижней части |

**Файл:** `ui/screens/BootScreen.kt`

### 2.2 PermissionsScreen

Запрашивает runtime-разрешения **до** их использования (Android 13+ compliance):

| Разрешение | Тип | Назначение |
|-----------|-----|-----------|
| `CAMERA` | Dangerous | Видеозвонки, фото |
| `RECORD_AUDIO` | Dangerous | Аудиозвонки, voice messages |
| `POST_NOTIFICATIONS` | Dangerous (API 33+) | Push-уведомления |
| `ACCESS_FINE_LOCATION` | Dangerous | WiFi-сканирование для сети |
| `ACCESS_COARSE_LOCATION` | Dangerous | WiFi-сканирование для сети |
| `ACCESS_WIFI_STATE` | Normal | Сканирование сетей |
| `CHANGE_WIFI_STATE` | Normal | Переключение WiFi |

**Файл:** `ui/screens/PermissionsScreen.kt`

### 2.3 AppLock (PIN)

| Параметр | Значение |
|----------|----------|
| Первый запуск | Установка PIN (4-6 цифр) |
| Последующие | Ввод PIN для разблокировки |
| Duress PIN | Второй PIN (сигнал тревоги) |
| Кол-во попыток | 5 неудачных → кнопка сброса |
| Ошибки | Показ числа неудачных попыток |
| Поддержка | Биометрия (опционально) |

**Файл:** `ui/screens/auth/AppLockScreen.kt`

### 2.4 StartupOrchestrator

State machine в 12 этапов:

```
1.  WAITING_FOR_PERMISSIONS   — ожидание разрешений
2.  PIN_SETUP                  — установка/ввод PIN
3.  GENERATE_SEED              — BIP39 seed generation
4.  VALIDATE_NETWORK           — DNS + TCP connectivity
5.  START_TOR                  — запуск Tor SOCKS5 :9050
6.  CHECK_TOR_PROXY            — проверка SOCKS5-прокси
7.  START_V2RAY                — запуск V2Ray SOCKS5 :10808
8.  CHECK_V2RAY                — проверка V2Ray-прокси  
9.  BUILD_BRIDGE               — V2Ray → Tor bridge build
10. CHECK_SMP_ONION            — тест .onion SMP ноды
11. CHECK_XFTP_ONION           — тест .onion XFTP ноды
12. READY                      — терминал готов
```

**Состояния терминала:** `LOCKED → UNLOCKING → DIAGNOSTICS → READY → ERROR`

**Файл:** `ui/StartupOrchestrator.kt`

### 2.5 TerminalSetupScreen

Анимированный экран хода диагностики:
- 12 ступеней с иконками (✅/🔄/❌/⏳)
- Cyberpunk-стиль (тёмный фон, зелёный текст, CRT-шум)
- Прогресс-бар
- Кнопка «LAUNCH MESSENGER» после READY
- Кнопка «RETRY DIAGNOSTICS» при ошибке
- Кнопка «RESET» для возврата к PIN

**Файл:** `ui/screens/terminal/TerminalSetupScreen.kt`

---

## 3. Покровный слой: Игра «Нарды» (Backgammon)

### 3.1 GameViewModel

| Компонент | Описание |
|-----------|----------|
| Игровая доска | 24 пункта, 15 шашек на игрока |
| Кости | Two dice, double rolls, animation |
| AI | Minimax с depth=3, alpha-beta pruning |
| Режимы | PvP, AI (easy/medium/hard), P2P онлайн |
| Система ходов | Legal moves, bear-off, doubling cube |
| Score | Match play, Crawford rule |
| Отмена хода | Undo до 3 ходов |
| Сохранение | Auto-save состояния игры |

**Файл:** `ui/GameViewModel.kt`

### 3.2 GameScreen

Compose-рендер доски:
- 3D-перспектива (скин)
- Анимация броска костей
- Подсветка доступных ходов
- Drag-and-drop шашек
- Чат-панель (P2P режим)
- Таймер хода
- Шкала счёта

**Файл:** `ui/screens/GameScreen.kt` (~9,500 строк)

### 3.3 GameCryptoWizard

Cryptographic wizard interface:
- Установка SMP .onion адреса
- Установка XFTP .onion адреса
- Инициализация крипто-контейнера
- Привязка BIP39 seed к profile

**Файл:** `ui/screens/GameCryptoWizard.kt`

### 3.4 GameProfileLoader

Система смены покровных приложений:

```kotlin
data class GameProfile(
    val id: String,
    val name: String,
    val icon: Int,
    val description: String,
    val packageSuffix: String
)
```

| Метод | Описание |
|-------|----------|
| `registerProfile(profile)` | Зарегистрировать новую игру |
| `switchProfile(id)` | Сменить активную игру |
| `getActiveProfile()` | Текущий профиль |
| `getProfiles()` | Список всех профилей |

По умолчанию: **«Backgammon Classic»** (id=`backgammon`).  
Система читает/пишет в `SecureStorage`.

**Файл:** `ui/gameprofile/GameProfileLoader.kt`

---

## 4. Слой мессенджера (P2P)

### 4.1 Чат-система

| Компонент | Ключевые функции |
|-----------|-----------------|
| **ChatScreen** | Список диалогов, поиск, архивация |
| **ChatListScreen** | Все чаты, статусы, непрочитанные |
| **MessageBubble** | Rich text, markdown, replies, reactions |
| **UserProfile** | Аватар, статус, ключи, био |
| **ContactManager** | Добавление, блокировка, экспорт контактов |
| **GroupManager** | Создание групп, управление участниками |

**Файлы:** `ui/screens/chat/*.kt`

### 4.2 SMP Protocol (SimpleX Messaging Protocol)

| Компонент | Описание |
|-----------|----------|
| **SMPProtocol** | Базовый протокол: message queue relay |
| **SMPClient** | Клиент SMP с очередями send/recv |
| **SMPAgent** | Агент: управление очередями, onion-адресами |
| **SMPQueue** | Очередь сообщений (send/recv) |
| SMP .onion | Подключение через Tor к SMP-ноде |

**Файлы:** `smp/*.kt`

### 4.3 XFTP File Transfer

Асинхронная передача файлов через Tor/V2Ray:
- XFTP protocol client
- XFTP .onion ноды
- Прогресс загрузки
- Chunked transfer

**Файлы:** `xftp/*.kt`

### 4.4 Push-уведомления

| Компонент | Описание |
|-----------|----------|
| **PushNotificationForegroundService** | Foreground service для push |
| **NotificationChannels** | 3 канала: chat, calls, system |
| **ChatNotificationManager** | Управление уведомлениями чатов |
| PendingIntent | Открытие чата по тапу |

**Файлы:** `service/PushNotificationForegroundService.kt`, `service/NotificationChannels.kt`

---

## 5. Сетевой стек

### 5.1 Tor (Embedded)

| Компонент | Описание |
|-----------|----------|
| **TorEmbeddedController** | Управление Tor-процессом (start/stop/restart) |
| **TorProxyClient** | SOCKS5-клиент для Tor (:9050) |
| Tor config | Сгенерирована на лету: SocksPort 9050, ControlPort 9051 |
| **Обфускация:** | |
| obfs4 | Randomised обфускация трафика |
| meek | Domain-fronting через Azure/CDN |
| snowflake | WebRTC-мосты (Tor Snowflake) |
| Bridge management | Автоматическое получение мостов |
| `.onion` resolution | Разрешение .onion адресов через Tor |

**Файлы:** `tor/TorEmbeddedController.kt`, `tor/TorProxyClient.kt`

### 5.2 V2Ray (Xray)

| Компонент | Описание |
|-----------|----------|
| **V2RayEmbeddedController** | Управление V2Ray/Xray-процессом |
| SOCKS5 inbound | :10808 |
| VMESS/VLESS | Протоколы подключения |
| Routing | Маршрутизация трафика через Tor |
| Multi-protocol | Поддержка VMESS, VLESS, Trojan, Shadowsocks |

**Файлы:** `v2ray/V2RayEmbeddedController.kt`

### 5.3 Socks5Chain

Цепочка прокси-серверов (основной сетевой механизм):

```
[Приложение] → V2Ray (:10808) → Tor (:9050) → [Интернет]
```

| Компонент | Описание |
|-----------|----------|
| **Socks5Chain** | Многоуровневый прокси-чейн |
| **Socks5Client** | SOCKS5 протокол (connect, auth) |
| Health checks | Ping каждого уровня |
| Auto-reconnect | Переподключение при разрыве |

**Файлы:** `vpn/Socks5Chain.kt`

### 5.4 FoxrayVpnService (TUN)

VPN-сервис на основе TUN:
- tunsafe TUN interface
- Routing всего трафика через Socks5Chain
- Per-app VPN filter
- Always-on VPN support
- killSwitch при разрыве

**Файлы:** `vpn/FoxrayVpnService.kt`

### 5.5 Network Detection

| Компонент | Описание |
|-----------|----------|
| **NetworkViewModel** | Статус сети, WiFi, сотовая, VPN |
| WiFi auto-detection | SSID, сила сигнала, частота |
| Mobile data | APN, тип сети (4G/5G) |
| VPN detection | Активен ли VPN |
| Network quality | Ping, latency, speedtest |
| ConnectivityManager | Обработка смены сети |

**Файлы:** `ui/viewmodels/NetworkViewModel.kt`

### 5.6 ProtocolViewModel

Оркестратор сетевых протоколов:
- VPN управление (start/stop)
- Tor управление (start/stop)
- Network test suite
- Protocol auto-detection
- Bandwidth monitoring

**Файлы:** `ui/viewmodels/ProtocolViewModel.kt`

---

## 6. Криптография

### 6.1 Hand-written NaCl

| Примитив | Алгоритм | Назначение |
|----------|----------|-----------|
| Key Exchange | **X25519** (Curve25519 ECDH) | Установка общего ключа |
| Encryption | **XSalsa20-Poly1305** | Симметричное шифрование (secretbox) |
| Signing | **Ed25519** | Цифровые подписи |
| Hashing | **SHA-512**, **BLAKE2b** | Хеширование |

**Файлы:** `crypto/NaCl.kt` / `crypto/Curve25519.kt` / `crypto/Salsa20.kt`

### 6.2 X3DH Key Agreement

Extended Triple Diffie-Hellman (Signal Protocol):
- Pre-key bundles (signed pre-key, one-time pre-keys)
- SPK (Signed Pre-Key) rotation
- OTPK (One-Time Pre-Key) consumption
- Triple DH: IK_A + SPK_B, EK_A + IK_B, EK_A + SPK_B
- Session key derivation via HKDF

**Файлы:** `crypto/X3DH.kt`

### 6.3 Double Ratchet

Signal Protocol Double Ratchet:
- Symmetric ratchet (KDF chain: root → sending → receiving)
- DH ratchet (per-message DH key exchange)
- Associated Data (AD) binding
- Message key derivation
- Skipped message keys (out-of-order delivery)
- Header encryption
- Session persistence (serialize/deserialize)

**Файлы:** `crypto/DoubleRatchet.kt`

### 6.4 BIP39 Seed Phrases

| Функция | Описание |
|---------|----------|
| **generateMnemonic()** | Генерация 12/24 слов (BIP-39) |
| **mnemonicToSeed()** | BIP-39 → 512-bit seed |
| **seedToKeypair()** | Seed → X25519 keypair |
| Validation | Проверка валидности seed phrase |
| Language | Английский wordlist (BIP-39) |

**Файл:** `data/Bip39Helper.kt`

### 6.5 Duress PIN System

| Режим | Действие при вводе |
|-------|-------------------|
| **Main PIN** | Нормальная разблокировка |
| **Duress PIN** | Сигнал тревоги: уничтожение seed, чистка контейнера |
| Сброс | Через `onResetApp()` (5+ ошибок) |
| Управление | DuressPinManager: setMainPin, setDuressPin, verifyPin |

**Файл:** `security/DuressPinManager.kt`

### 6.6 SecureStorage

| Механизм | Описание |
|----------|----------|
| **AndroidKeyStore** | Хранение мастер-ключа |
| **AES-256-GCM** | Шифрование данных на диске |
| **EncryptedSharedPrefs** | SharedPreferences + AES |
| File encryption | Шифрование файлов крипто-контейнера |
| Key rotation | Смена ключей по расписанию |

**Файл:** `data/SecureStorage.kt`

---

## 7. Крипто-контейнер

### 7.1 Cryptocontainer

| Функция | Описание |
|---------|----------|
| Экспорт | Полный дамп контейнера (seed + keys) |
| Импорт | Восстановление из дампа |
| Валидация | Целостность + check-sum |
| Sealed box | X25519 + XSalsa20-Poly1305 для хранения |
| Формат | Binary + BIP39 seed phrase fallback |

---

## 8. Безопасность

### 8.1 ScreenSecurityManager

| Функция | Описание |
|---------|----------|
| FLAG_SECURE | Запрет скриншотов/записи экрана |
| enableScreenSecurity() | Включение FLAG_SECURE |
| disableScreenSecurity() | Отключение (для скриншотов с согласия) |

**Файл:** `security/ScreenSecurityManager.kt`

### 8.2 ClipboardGuard

| Функция | Описание |
|---------|----------|
| Auto-clear | Очистка буфера обмена через N секунд |
| Sensitive detection | Определение seed/phrase в буфере |
| Worker | PeriodicWorkRequest (WorkManager) |

**Файл:** `security/ClipboardGuard.kt`

### 8.3 network_security_config.xml

- Cleartext запрещён в целом
- Исключения для TUN/VPN: 10.0.0.0/8, 172.16.0.0/12, 192.168.0.0/16
- User CA для .onion сертификатов

**Файл:** `res/xml/network_security_config.xml`

---

## 9. Фоновые сервисы

| Сервис | Функция | Foreground |
|--------|---------|-----------|
| PushNotificationForegroundService | Push-уведомления, держит процесс | ✅ |
| NotificationChannels | 3 канала: chat, calls, system | — |
| ClipGuardWorker | Очистка буфера обмена | ❌ |
| BandwidthMonitor | Мониторинг скорости сети | ❌ |
| RadioManager | Управление радио (потоковое аудио) | ❌ |

**Файлы:** `service/*.kt`

---

## 10. UI/UX

### 10.1 Тема

Cyberpunk-стиль:
- Тёмный фон (`#0D0B1A`)
- Акцентный цвет: циан (`#00FFCC`)
- Дополнительный: розовый (`#FF007F`)
- CRT scanlines, Matrix rain
- Моноширинный шрифт для терминала

**Файлы:** `ui/theme/*.kt`

### 10.2 Онбординг

`OnboardingScreen.kt` — 3-экранный тур:
1. Welcome (приватность, сеть)
2. Features (шифрование, P2P)
3. Ready (start button)

**Файл:** `ui/screens/onboarding/OnboardingScreen.kt`

### 10.3 Dashboard

Центральный экран управления:
- Быстрые переключатели: Tor, V2Ray, VPN, Bridge
- Статус-панель: сеть, онбординг, кол-во сообщений
- Кнопки: Play Game, Chats, Network, Settings

**Файл:** `ui/screens/DashboardScreen.kt`

### 10.4 Settings

`SettingsScreen.kt`:
- Сеть (Tor, V2Ray, Bridge config)
- Безопасность (PIN, duress, screen security)
- Уведомления (push, sound, vibration)
- Профиль (имя, аватар, seed)
- Очистка данных
- О приложении

### 10.5 PermissionsScreen

Тёмный экран с 6 переключателями:
- Camera, Microphone, Notifications, WiFi, Location, Network
- Cyberpunk grid layout
- Animated status indicators

**Файл:** `ui/screens/PermissionsScreen.kt`

---

## 11. Маскировка (decoy layer)

### 11.1 Activity Aliases

| Алиас | Включён | Имя в системе | Иконка |
|-------|---------|--------------|--------|
| MainActivityAlias | ✅ | Not Gammon | Иконка игры |
| DecoyCalculatorAlias | ❌ | Calculator | Иконка калькулятора |
| DecoyMusicAlias | ❌ | Music Player | Иконка плеера |
| DecoyNotesAlias | ❌ | Notes | Иконка заметок |
| DecoyWeatherAlias | ❌ | Weather | Иконка погоды |

Runtime-переключение иконок через `PackageManager.setComponentEnabledSetting()`.

### 11.2 DecoyCalculatorActivity

Простой калькулятор (маскировка):
- 4 операции: +, −, ×, ÷
- История вычислений
- Защищённый режим (переход в основное приложение)

**Файл:** `DecoyCalculatorActivity.kt`

### 11.3 GameProfileLoader

Плагинная система смены покровных игр:
- Регистрация новых профилей
- Переключение активного профиля
- Сохранение в SecureStorage
- UI для выбора игры

**Файл:** `ui/gameprofile/GameProfileLoader.kt`

---

## 12. Эволюционные метрики

| Метрика | Значение |
|---------|----------|
| Всего .kt файлов | 238 |
| Всего строк кода | 47,359 |
| Production строк | ~44,000 |
| Тестовые строки | ~2,100 |
| UI файлы | ~40 Compose screens/components |
| Крипто-файлы | 8 (NaCl, X3DH, Double Ratchet, BIP39) |
| Сетевые файлы | ~20 (Tor, V2Ray, SOCKS5, VPN, SMP, XFTP) |
| Эволюционных циклов | 6 (Run 20260720-R1) |

---

## 13. Требования к сборке

| Компонент | Версия |
|-----------|--------|
| JDK | Eclipse Adoptium 17.0.19 |
| Android SDK | 34 (platforms/android-34) |
| Gradle | 8.11+ |
| AGP | 9.1.1 |
| Kotlin | 2.0.21 |
| Compose BOM | 2024.12.01 |
| Compose Compiler | 2.0.21 |

**Команда сборки:**
```bash
export JAVA_HOME="C:\Program Files\Eclipse Adoptium\jdk-17.0.19.10-hotspot"
export ANDROID_HOME="C:\Users\<user>\AppData\Local\Android\Sdk"
cd /c/Temp/n2 && gradle assembleDebug
```

**Выход:** `app/build/outputs/apk/debug/app-debug.apk` → `C:\ApkExport\NotNode.apk`

---

## 14. Стартовый флоу (полный цикл)

```
[Launch]
    ↓
[BootScreen] ──── 3s MATRIX анимация ────→  
    ↓
[PermissionsScreen] ──── 6 разрешений ────→  
    ↓
[AppLockScreen] ──── PIN / Duress PIN / Setup ────→  
    ↓
[StartupOrchestrator] ──── 12 этапов ────→  
    │ 1. BIP39 seed generation/load
    │ 2. DNS/TCP check
    │ 3. Start Tor (:9050)
    │ 4. Check Tor proxy
    │ 5. Start V2Ray (:10808)
    │ 6. Check V2Ray
    │ 7. Build Bridge V2Ray→Tor
    │ 8-9. Check SMP + XFTP .onion
    │ 10. READY
    ↓
[TerminalSetupScreen] ──── ✅ LAUNCH ────→  
    ↓
[AppNavHost]
    ├── Dashboard (Tor/V2Ray/VPN toggle, status)
    ├── GameScreen (нарды PvP/AI/P2P)
    ├── ChatScreen (SMP messenger)
    ├── Settings (безопасность, сеть, профиль)
    └── NetworkDiagnostics
```

---

## 15. Разрешения (AndroidManifest)

| Разрешение | Уровень | API |
|-----------|---------|-----|
| INTERNET | Normal | — |
| ACCESS_NETWORK_STATE | Normal | — |
| CAMERA | Dangerous | — |
| RECORD_AUDIO | Dangerous | — |
| POST_NOTIFICATIONS | Dangerous | 33+ |
| ACCESS_FINE_LOCATION | Dangerous | — |
| ACCESS_COARSE_LOCATION | Dangerous | — |
| ACCESS_WIFI_STATE | Normal | — |
| CHANGE_WIFI_STATE | Normal | — |
| FOREGROUND_SERVICE | Normal | — |
| FOREGROUND_SERVICE_SYSTEM_EXEMPTED | Normal | 33 max |
| FOREGROUND_SERVICE_SPECIAL_USE | Normal | 33 max |
| FOREGROUND_SERVICE_DATA_SYNC | Normal | — |
| VIBRATE | Normal | — |
| MODIFY_AUDIO_SETTINGS | Normal | 33 max |
| RECEIVE_BOOT_COMPLETED | Normal | — |

---

## 16. Мониторинг и качество

### 16.1 Network Diagnostics (`NetworkTestScreen`)

| Тест | Описание |
|------|----------|
| DNS resolution | Разрешение onion/surge доменов |
| TCP connectivity | Прямое TCP-соединение |
| Tor proxy health | SOCKS5 :9050 отклик |
| V2Ray proxy health | SOCKS5 :10808 отклик |
| End-to-end onion | Подключение через V2Ray→Tor к .onion |
| Bandwidth | Скорость загрузки (bandwidth monitor) |
| Latency | Ping до нод |
| SMP queue status | Очередь сообщений |

### 16.2 AudioViewModel

- Радио-поток (интернет-радио)
- Плеер (медиа-файлы)
- Управление громкостью
- Audio focus handling

**Файл:** `ui/viewmodels/AudioViewModel.kt`

---

## 17. Целевая аудитория и маркетинг

| Параметр | Значение |
|----------|----------|
| TAM | $25 млрд (private messaging + iGaming) |
| SAM | $1.5 млрд (privacy-conscious gamers) |
| SOM | $50–100 млн (год 1-2) |
| Монетизация | Freemium: базовый P2P бесплатно |
| | Premium: XFTP high-speed, Bridge builder, Multi-account |
| | Game: реклама, турниры, NFT-скины |
| Конкуренты | Signal (без игр), Telegram (без шифрования P2P), |
| | Wire (без маскировки), Session (без TUN/VPN) |

---

*Документ сгенерирован 23 июля 2026. Версия 1.1.0.*
