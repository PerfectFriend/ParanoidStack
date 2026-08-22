# Проект «Not Gammon» — Полный технический и маркетинговый отчёт

**Дата:** 21 июля 2026  
**Версия:** 1.1.0 (build 2)  
**Артефакт:** `C:\ApkExport\app-debug.apk` (98 MB)

---

## Содержание

1. [Идея проекта и архитектура](#1-идея-проекта-и-архитектура)
2. [Структура кодовой базы](#2-структура-кодовой-базы)
3. [Описание функций](#3-описание-функций)
4. [Технический стек](#4-технический-стек)
5. [Статус эволюции](#5-статус-эволюции)
6. [План дальнейшей эволюции](#6-план-дальнейшей-эволюции)
7. [Маркетинговая оценка](#7-маркетинговая-оценка)
8. [Оценка затрат команды 5 чел. (без AI)](#8-оценка-затрат-команды-5-чел-без-ai)

---

## 1. Идея проекта и архитектура

### 1.1 Концепция

**Not Gammon** — скрытый децентрализованный P2P-мессенджер, замаскированный под игру в нарды (Backgammon). Приложение реализует двухслойную архитектуру:

- **Внешний слой (cover):** полноценная игра в нарды с AI, мультиплеером, турнирами, настройками
- **Внутренний слой (hidden):** защищённый P2P-мессенджер на базе протокола SMP (SimpleX Messaging Protocol) с обязательным цепочечным проксированием через Tor → V2Ray

Вся сетевая активность приложения проходит через зашифрованный туннель, не использует центральных серверов, не требует phone/SIM/email для регистрации. Маскировка дополнительно обеспечивается сменными иконками (калькулятор, плеер, «точки»).

### 1.2 Ключевые принципы

| Принцип | Реализация |
|---------|-----------|
| Zero trust | Нет центральной аутентификации, ключи на клиенте |
| Metadata resistance | SMP (не federated XMPP/Matrix), onion-маршрутизация |
| Plausible deniability | Cover-игра + Duress PIN (аварийное стирание) |
| Censorship circumvention | Tor obfs4/meek/snowflake + V2Ray VMess/VLESS |
| No phone/email | Регистрация по ed25519 ключу, адрес — SMP onion |
| E2E encryption | X3DH + Double Ratchet (аналог Signal Protocol) |

### 1.3 Сеть протоколов

```
[App UI / GameView]
    │
    ├── Game Logic (движок нард, AI, мультиплеер WebSocket)
    │
    └── SimpleX (SMP Client/Agent)
            │
            ├── SMP Protocol (Queue-based, relay-only, no store)
            │       └── TLS SPKI pinning (certificate verification)
            │
            ├── XFTP Protocol (File transfer over SMP)
            │
            ├── TorEmbeddedController (обёртка tor-android-binary 0.4.4.6)
            │       ├── SOCKS5 :9050
            │       ├── obfs4 transport
            │       ├── meek-lite (Amazon S3 domain fronting)
            │       └── snowflake (WebRTC circumvention)
            │
            └── V2RayEmbeddedController (Xray-core v1.8.24 subprocess)
                    ├── VMess / VLESS / Shadowsocks
                    ├── SOCKS5 ingress :10808 → Tor :9050 egress
                    └── Fallback: прямой SOCKS5 через Tor

[Network]
    └── FoxrayVpnService (VpnService + TUN)
            └── System VPN — весь трафик принудительно через Tor
```

### 1.4 Криптоядро (hand-written, без готовых библиотек)

| Компонент | Назначение |
|-----------|-----------|
| `NaClCrypto` | X25519 DH + XSalsa20-Poly1305 AEAD |
| `X3DHState` | X3DH key agreement (Signal X3DH) |
| `DoubleRatchet` | Double Ratchet (Signal DR) |
| `SimpleXCrypto` | SMP-specific envelope encryption |
| `Bip39Helper` | BIP39 seed phrase (12 слов) |
| `DuressPinManager` | Duress PIN — аварийное стирание ключей |
| `SasCalculator` | Short Authentication String (zRTP CPA) |

---

## 2. Структура кодовой базы

### 2.1 Метрики

| Метрика | Значение |
|---------|----------|
| Всего `.kt` файлов | 234 |
| Строк production-кода | **44 117** |
| Строк тестов | **2 112** |
| Соотношение test/prod | **4.8%** |
| Компонентов UI (Compose) | 35 |
| Экранов | 30+ |
| Сервисов Android | 5 |
| Воркеров | 4 |
| Файлов ресурсов (строки) | 102 (RU) + 37 (EN) |
| Размер APK (debug) | 98 MB |
| Размер APK (сжатие) | ~35-40 MB (release + ProGuard) |

### 2.2 Иерархия директорий

```
app/src/main/java/com/example/
├── api/                        # REST/WebSocket клиенты (matchmaking, обновления)
├── audio/                      # Аудиодвижок
│   ├── models/                 # AudioTrack, проигрыватели
│   └── RadioManager.kt        # TTS, синтез речи (глас Бога)
├── data/                       # ✦ ЯДРО (35 файлов, ~12 000 строк)
│   ├── Bip39Helper.kt         # BIP39 seed phrase
│   ├── NaClCrypto.kt          # X25519 + XSalsa20-Poly1305
│   ├── DoubleRatchet.kt       # Double Ratchet DR
│   ├── X3DHState.kt           # X3DH key agreement
│   ├── SimpleXCrypto.kt       # SMP envelope шифрование
│   ├── SMPAgent.kt            # SMP протокол-клиент (544 строки)
│   ├── SMPClient.kt           # Сетевое подключение к SMP relay
│   ├── TorEmbeddedController.kt # Tor (377 строк)
│   ├── V2RayEmbeddedController.kt # Xray (439 строк)
│   ├── FoxrayVpnManager.kt    # VPN-менеджер (449 строк)
│   ├── AppDatabase.kt         # Room DB (387 строк)
│   ├── DuressPinManager.kt    # Аварийный PIN
│   ├── NetworkDefaults.kt     # ✦ NEW — централизованные адреса
│   └── ... (35 файлов)
├── model/                      # Модели данных
│   ├── GameEngine.kt          # Движок нард (956 строк)
│   └── ... (ChatMessage, Contact, UserProfile, Room, UserTier...)
├── protocols/                  # Протокольный слой
│   ├── ProtocolOrchestrator.kt # Оркестрация Tor → V2Ray → SMP
│   ├── NetworkAutoDetector.kt # Обнаружение сетевых изменений
│   ├── ProtocolRegistry.kt    # Реестр протоколов
│   ├── mesh/NodeMeshManager.kt # P2P mesh-сеть (465 строк)
│   └── storage/ArchiveCloud.kt# WebDAV/Cloud-бэкап
├── security/                   # Безопасность
│   └── DuressPinManager.kt    # ✦ Duress PIN
├── service/                    # Android-сервисы
│   ├── FoxrayVpnService.kt    # VpnService (371 строка)
│   ├── SmpNotificationService.kt
│   ├── PushNotificationForegroundService.kt
│   ├── ProtocolOrchestratorService.kt
│   ├── BootReceiver.kt
│   └── CrashLogHandler.kt
├── ui/                         # ✦ UI (Compose, ~25 000 строк)
│   ├── GameViewModel.kt       # God ViewModel (3740 строк)
│   ├── viewmodels/            # 5 ViewModel (SecurityVM, SettingsVM, AudioVM, NetworkVM, ProtocolVM)
│   ├── screens/               # 30+ экранов
│   │   ├── game/              # GameScreen (2005 строк), доска, кости
│   │   ├── chat/              # SimpleXChatScreen (3085 строк), 5 субкомпонентов
│   │   ├── auth/              # AuthScreen, PinSetup, DuressWarning
│   │   ├── settings/          # SettingsScreen, UserTierScreen
│   │   ├── dialogs/           # JrebiyDialog, RadioDialog, GameDialogs
│   │   ├── ... (onboarding, profile, calls, files, groups, localization)
│   ├── components/            # 35 Compose-компонентов
│   ├── theme/                 # Material3 тема (5 цветовых схем)
│   └── navigation/            # NavHost, NavRoutes, DeepLinkHandler
```

### 2.3 Крупнейшие файлы (monoliths)

| Файл | Строк | Доля кодовой базы |
|------|-------|-------------------|
| `GameViewModel.kt` | 3 740 | 8.5% |
| `SimpleXChatScreen.kt` | 3 085 | 7.0% |
| `GameScreen.kt` | 2 005 | 4.5% |
| `ProtocolViewModel.kt` | 1 689 | 3.8% |
| `GameEngine.kt` | 956 | 2.2% |
| `RadioManager.kt` | 814 | 1.8% |
| `GameSettingsDialog.kt` | 819 | 1.9% |
| **Сумма топ-7** | **13 108** | **29.7%** |

---

## 3. Описание функций

### 3.1 Cover-функции (игра в нарды)

| Функция | Статус | Описание |
|---------|--------|----------|
| Движок нард (GameEngine) | ✅ | Полные правила, счёт, даве (удвоение), марс/кокс |
| AI противник | ✅ | 3 уровня сложности |
| PvP мультиплеер | ✅ | Matchmaking через WebSocket + onion |
| Анимация броска костей | ✅ | 3D-стиль с физикой |
| Турнирная таблица | ✅ | ELO, статистика |
| Настройки игры | ✅ | Время хода, правила, звуки |
| 5 тем оформления | ✅ | warm, dark, neon, ocean, forest |
| Локализация (6 языков) | ✅ | RU, EN, DE, ES, FR, TR |

### 3.2 Hidden-функции (P2P-мессенджер)

| Функция | Статус | Описание |
|---------|--------|----------|
| SMP-чат (SimpleX) | ✅ | Децентрализованный P2P обмен сообщениями через SMP relay |
| X3DH + Double Ratchet | ✅ | E2E шифрование, perfect forward secrecy |
| Файлообмен (XFTP) | ✅ | P2P передача файлов через XFTP relay |
| Tor-маршрутизация | ✅ | Весь трафик через Tor SOCKS5 :9050 |
| V2Ray-мост | ✅ | Xray subprocess, цепочка Tor → V2Ray |
| VPN-форсирование | ✅ | FoxrayVpnService — весь трафик системы через Tor |
| Duress PIN | ✅ | Аварийный PIN — мгновенное стирание ключей |
| Аудиозвонки (TTS) | ✅ | Голосовые сообщения через синтез речи |
| Push-уведомления | ⚠️ | FCM-заглушка (только локальные нотификации) |
| WebDAV-бэкап | ✅ | Шифрованный бэкап ключей в облако |
| Mesh-сеть | ✅ | P2P mesh (NodeMeshManager) — обход блокировок |
| Telegram-репортер | ✅ | Отправка crash-reports через Telegram Bot API |

### 3.3 Системные функции

| Функция | Статус | Описание |
|---------|--------|----------|
| Сменная иконка | ✅ | Calculator / Music / Dots & Boxes |
| Duress PIN | ✅ | При вводе аварийного PIN — очистка контейнера |
| Seed-фраза BIP39 | ✅ | 12 слов для восстановления ключей |
| Экспорт/импорт криптоконтейнера | ✅ | Перенос ключей между устройствами |
| Onboarding | ✅ | Первый запуск — мастер seed + PIN |
| Material 3 Theme | ✅ | Dynamic color, Dark mode |

---

## 4. Технический стек

### 4.1 Базовый стек

| Компонент | Версия | Назначение |
|-----------|--------|------------|
| Kotlin | 2.0.21 | Основной язык |
| Jetpack Compose | BOM 2024.09.00 | UI |
| Android Gradle Plugin | 9.1.1 | Сборка |
| Gradle | 9.5.1 | Система сборки |
| KSP | 2.0.21-1.0.26 | Code generation |
| Room | 2.7.0 | Локальная БД |
| Navigation Compose | 2.8.9 | Навигация |
| Coil | 2.7.0 | Загрузка изображений |
| Retrofit + Moshi | 2.12.0 / 1.15.2 | HTTP-клиент |
| OkHttp + Logging | 4.10.0 | HTTP |

### 4.2 Специализированные зависимости

| Компонент | Назначение |
|-----------|-----------|
| tor-android-binary 0.4.4.6 | Встроенный Tor (SOCKS5, obfs4, meek, snowflake) |
| Xray-core 1.8.24 | V2Ray (VMess, VLESS, Shadowsocks) |
| SimpleX SMP protocol | Messaging protocol (self-contained в `data/SMP*.kt`) |
| X3DH + Double Ratchet | E2E encryption (ручная реализация) |
| FoxrayVpnService | VpnService + TUN |
| WorkManager 2.9.1 | Фоновые задачи |
| Google Secrets Plugin | Управление API-ключами |
| Roborazzi 1.59.0 | Скриншот-тесты Compose |
| Robolectric 4.16.1 | Android unit tests |

### 4.3 Размеры компонент в APK

| Компонент | Приблизительный размер |
|-----------|----------------------|
| Tor binary (armeabi-v7a, arm64-v8a, x86_64) | ~40 MB |
| Xray binary (3 архитектуры) | ~25 MB |
| DEX (Kotlin-код) | ~12 MB |
| Ресурсы (Compose, строки, темы) | ~5 MB |
| Native libs (OpenSSL, и т.д.) | ~16 MB |
| **Итого** | **~98 MB** |

---

## 5. Статус эволюции

### 5.1 Выполнено (Run 20260720-R1, 6 циклов)

| Цикл | Фаза | Изменения | Статус |
|------|------|-----------|--------|
| 0 | Bootstrap | E VOLUTION/ артефакты, план | ✅ |
| 1 | 6A Crash Prevention | ArchiveCloud: runBlocking → coroutines; V2Ray/NetworkDetector: пустые catch → logging | ✅ |
| 2 | 6A.2 Unsafe Casts | DoubleRatchet, TorProxyClient, NetworkAutoDetector, ArchiveCloud: `as` → `as?` | ✅ |
| 3 | 6B VM Decomposition | SecurityViewModel.kt, SettingsViewModel.kt; 24 точки делегирования | ✅ |
| 4 | 6C.1 Screen Split | SaveCryptoKeyDialog.kt; SimpleXChatScreen 3190→3085 строк | ✅ |
| 5 | 6D Tests | 3 новых тестовых файла (29 total), 10 тестов | ✅ |
| 6 | 6E Architecture | NetworkDefaults.kt; GameViewModel + NetworkViewModel → константы | ✅ |

### 5.2 Новые файлы (8)

| Файл | Строк | Назначение |
|------|-------|-----------|
| `SecurityViewModel.kt` | 94 | Duress PIN, seed, key derivation |
| `SettingsViewModel.kt` | 67 | Theme, language, UserTier |
| `SaveCryptoKeyDialog.kt` | 149 | Диалог экспорта криптоключей |
| `NetworkDefaults.kt` | 28 | Централизованные onion-адреса |
| `SecurityViewModelTest.kt` | 63 | 4 теста |
| `SettingsViewModelTest.kt` | 56 | 3 теста |
| `SaveCryptoKeyDialogTest.kt` | 55 | 3 теста |
| `EVOLUTION_END_OF_RUN_REPORT.md` | 135 | Финальный отчёт |

### 5.3 Исправлено дефектов

| Тип | Найдено | Исправлено |
|-----|---------|-----------|
| `runBlocking` в production | 3 | 3 (100%) |
| `GlobalScope` | 0 | 0 |
| Unsafe `as` casts | 5 | 5 (100%) |
| Empty catch blocks | 2 | 2 (100%) |
| Hardcoded onion addresses | 10+ | 10+ (100%) в GameViewModel/NetworkViewModel |
| Deprecated `stopForeground(Boolean)` | 0 | Уже на современном API |

---

## 6. План дальнейшей эволюции

### 6.1 Критические (🔴)

| № | Задача | Приоритет | Оценка (чел.-дней) |
|---|--------|-----------|-------------------|
| 1 | **ProtocolViewModel** — устранить дублирование Tor/SMP состояний | Critical | 2 |
| 2 | **GameScreen.kt** — разбить 9508 строк на 5-8 подкомпонентов | Critical | 5 |
| 3 | **export/import Cryptocontainer** — вынести из GameViewModel | High | 1 |
| 4 | **Backup dialogs** — извлечь 3 диалога (~200 строк) из SimpleXChatScreen | High | 1 |

### 6.2 Высокие (🟠)

| № | Задача | Оценка |
|---|--------|--------|
| 5 | E2E тест Tor → V2Ray → SMP | 3 |
| 6 | FCM push notification (реальная регистрация) | 2 |
| 7 | `as Int()` и `Char.toInt()` deprecation warnings | 1 |
| 8 | Gradle wrapper (`gradlew`) для CI | 0.5 |
| 9 | ProGuard/R8 конфигурация для минификации APK | 1 |
| 10 | Исправить TorProxyClient.kt:192 — `Nothing?` вместо `String` | 0.5 |

### 6.3 Средние (🟡)

| № | Задача | Оценка |
|---|--------|--------|
| 11 | Icons.AutoMirrored / Notification.id deprecation | 1 |
| 12 | Material 3 Dynamic Color — полная адаптация | 1 |
| 13 | Русская локализация — добить оставшиеся untranslated строки | 0.5 |
| 14 | Отказоустойчивость — retry при падении Tor/Xray | 2 |

### 6.4 Долгосрочные (🟢)

| № | Задача | Оценка |
|---|--------|--------|
| 15 | Многопользовательские групповые чаты (SMP groups) | 5 |
| 16 | Сквозное шифрование медиафайлов | 2 |
| 17 | Автоматический выбор транспорта (Tor ↔ V2Ray ↔ direct) | 3 |
| 18 | Matrix протокол как второй P2P-слой (уже есть MatrixNoGameScreen) | 10 |
| 19 | iOS порт (KMP) | 30+ |

---

## 7. Маркетинговая оценка

### 7.1 Рынок

**Сегмент:** Приватные коммуникации (Privacy-first messaging)  
**Адресуемый рынок (TAM):** ~$25 млрд (2026, рынок защищённых мессенджеров)  
**SOM (Serviceable Obtainable Market):** $50-100 млн (нишевый продукт для журналистов, диссидентов, crypto-энтузиастов)

**Конкуренты:**

| Продукт | Сильные стороны | Слабые стороны |
|---------|----------------|----------------|
| Signal | Простота, reputation | Требует phone, центральные серверы |
| Telegram | 900M+ users | Нет E2E по умолчанию, metadata leak |
| Session | Нет phone/email | Медленный Loki network |
| Briar | Offline mesh, Tor-native | Спартанский UI |
| SimpleX Chat | Нет ID, relay-only | Мало пользователей |
| **Not Gammon** | **Двойной слой + игра, Duress, Tor+V2Ray** | **98 MB APK, баги, недоделанный UI** |

### 7.2 Целевая аудитория

| Сегмент | Размер | Готовность платить |
|---------|--------|-------------------|
| Журналисты (risk environments) | ~500K worldwide | Высокая |
| Полит-активисты | ~2M | Средняя |
| Crypto community | ~10M | Высокая |
| Privacy-conscious users | ~50M | Средняя |
| Gamer segment (cover) | ~50M | Низкая (игра бесплатна) |

### 7.3 Монетизация

| Модель | Доход | Сложность |
|--------|-------|-----------|
| **Freemium UserTier** (сейчас: FREE/PREMIUM/ROYAL) | $5-15/мес | 🟢 Low |
| **Donation/crypto** (Monero, BTC) | Community-driven | 🟢 Low |
| **SMP relay hosting** (свой relay за $) | $3-10/мес | 🟡 Medium |
| **VPN-бандл** (Tor + V2Ray как услуга) | $10-20/мес | 🟠 Medium |
| **Enterprise audit** (для НКО, медиа) | $1000-5000 | 🔴 High |

### 7.4 Каналы распространения

| Канал | Доступность | Сложность |
|-------|------------|-----------|
| GitHub Releases (APK) | 🟢 Immediate | 🟢 Low |
| F-Droid | 🟢 Privacy-friendly store | 🟡 Medium |
| Собственный сайт + прямой APK | 🟢 Full control | 🟢 Low |
| Telegram / X (Twitter) community | 🟢 Free | 🟢 Low |
| Google Play | 🔴 Restricted (dual-layer может быть забанен) | 🔴 High |
| Apple App Store | 🔴 Нет iOS-версии | 🔴 Very High |

### 7.5 Пользовательские метрики (оценка)

| Метрика | Цель (1 год) |
|---------|-------------|
| DAU | 500-2 000 |
| MAU | 10 000-50 000 |
| Конверсия в Premium | 5-10% |
| Retention D1/D7/D30 | 40%/20%/10% |
| Средний revenue на пользователя (ARPU) | $2-5/мес |

---

## 8. Оценка затрат команды 5 чел. (без AI)

### 8.1 Состав команды

| Роль | Уровень | Ставка (мес.) | Занятость |
|------|---------|--------------|-----------|
| Android-разработчик (Team Lead) | Senior | $8 000-10 000 | Full-time |
| Kotlin/Protocol-разработчик | Middle | $5 000-7 000 | Full-time |
| Бэкенд-разработчик (SMP/relay) | Middle | $5 000-7 000 | Full-time |
| Android-разработчик | Junior | $2 500-3 500 | Full-time |
| QA-инженер / SecOps | Middle | $4 000-5 000 | Full-time |
| **Итого ФОТ** | | **$24 500-32 500/мес** | |

### 8.2 Временные затраты на текущее состояние

| Фаза | Время | Кто |
|------|-------|-----|
| **1. Core protocol (NaCl, X3DH, Double Ratchet, SMP)** | 6-8 мес. | Senior + Middle protocol |
| **2. Tor + V2Ray интеграция** | 3-4 мес. | 2 Middle |
| **3. Game engine (нарды)** | 3-4 мес. | Middle + Junior |
| **4. Compose UI (30 экранов)** | 4-5 мес. | Senior + Junior |
| **5. VPN Service + TUN** | 2-3 мес. | Middle (security) |
| **6. Тестирование (unit + integration + E2E)** | непрерывно | QA |
| **7. Оптимизация (ProGuard, баги, perf)** | 2-3 мес. | All |
| **Итого до текущего состояния** | **18-24 мес.** | |

### 8.3 Оборудование и инфраструктура

| Статья | Ед. | Стоимость (разовая) |
|--------|-----|--------------------|
| MacBook Pro M4 (для сборки) | 2 шт. | $7.000 |
| Android-девайсы (тестирование, 5 шт.) | 5 шт. | $3.000 |
| Pixel 8/9, Samsung, эмуляция | — | — |
| CI/CD сервер (self-hosted или GitHub Actions) | 1 | $200/мес. |
| SMP relay VPS (Tor-enabled) | 2 шт. | $60/мес. |
| Xray/V2Ray сервер | 1 шт. | $30/мес. |
| WebDAV backup server | 1 шт. | $20/мес. |
| Подписка на инструменты (JetBrains All Products) | 5 лицензий | $1.500/год |

### 8.4 Полная смета до текущего состояния

| Статья | Расчёт | Сумма |
|--------|--------|-------|
| **ФОТ** | $28 500/мес × 21 мес. | **$598 500** |
| **Оборудование** | MacBook ×2 + девайсы | $10 000 |
| **Инфраструктура** | $310/мес × 21 | $6 510 |
| **ПО (JetBrains и т.д.)** | $1 500/год × 2 года | $3 000 |
| **Юридическое (Privacy Policy, ToS, открытие компании)** | Разово | $5 000 |
| **Маркетинг (сайт, relay, community)** | $2 000/мес × 6 | $12 000 |
| **Резерв (20%)** | | ~$127 000 |
| **ИТОГО** | | **~$762 000** |

### 8.5 Затраты на завершение (оставшиеся эволюционные задачи)

| Статья | Оценка |
|--------|--------|
| ProtocolViewModel + GameScreen декомпозиция | 1-2 мес. |
| E2E тесты + CI | 1 мес. |
| FCM реальный push | 0.5 мес. |
| ProGuard + уменьшение APK (98→40 MB) | 1 мес. |
| Устранение deprecation warnings | 0.5 мес. |
| **Доведение до production-ready** | **~3-4 мес. команды** |
| **Дополнительные затраты** | **$85 000 - $130 000** |

### 8.6 Итоговая оценка

| Параметр | Значение |
|----------|----------|
| **Трудоёмкость до текущего состояния** | **18-24 человеко-месяцев** (105-140 чел.-мес. суммарно) |
| **Стоимость разработки (без AI)** | **$762 000 - $1 000 000** |
| **Стоимость доведения до production** | +$85 000 - $130 000 |
| **Время с нуля до текущего состояния** | **1.5 - 2 года (команда 5 чел.)** |
| **Время до production-ready** | **+3-4 мес.** |
| **Ежемесячные операционные расходы** | **$25 000 - $33 000** |

### 8.7 Ключевые выводы по затратам

1. **Самая затратная часть** — hand-written криптоядро (NaCl, X3DH, Double Ratchet) и протокол SMP. Коммерчески оправдано, т.к. даёт полный аудит кода.
2. **Tor + Xray интеграция** — вторая по сложности часть из-за embedded native binaries и обработки Android VPN.
3. **Compose UI** — объёмная (30+ экранов, 25K строк), но технически простая (требует больше middle/junior).
4. **APK 98 MB** — проблема для распространения (лимит Google Play 150 MB, но пользователи не скачивают 100 MB мессенджер без доверия).
5. **Экономия от AI** — текущая эволюция (6 циклов за ~2 дня) заменила бы ~1-2 месяца работы команды.

---

*Отчёт сгенерирован 21 июля 2026 на основе анализа кодовой базы Not Gammon (44 117 строк Kotlin, 234 файла).*
