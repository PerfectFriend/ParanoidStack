# ParanoidStack UI/Functional Audit Report
## Симуляции, Затычки, Хардкод и Неработающий Функционал

**Дата аудита:** 2026-08-22  
**Проект:** ParanoidStack (Nexus, N3, N2, keyboard-ime, store, guard, sdk)  
**Методология:** Полный разбор всех `.kt`, `.xml`, `.json` файлов в `src/main`, поиск `TODO`, `FIXME`, `stub`, `mock`, `hardcode`, `placeholder` (не UI), захардкоженных IP/ports/фп/URLs, и нереализованных методов.

---

## 📊 СВОДНАЯ ТАБЛИЦА

| Категория | Найдено | Критичность |
|-----------|---------|-------------|
| **Хардкод IP/портов/URL** | 47+ вхождений | 🔴 КРИТИЧНО |
| **Хардкод отпечатков/сертификатов** | 12+ уникальных значений | 🔴 КРИТИЧНО |
| **Затычки/STUB в логике** | 3 явных случая | 🟡 ВЫСОКАЯ |
| **Mock/placeholder реализации** | 2 (WebRTC speakerphone, TrafficPadding) | 🟡 ВЫСОКАЯ |
| **Не реализованные UI экраны** | 0 (все WebView-based) | 🟢 OK |
| **Отсутствующие конфиги** | Bridges, Turn, Snowflake, DNS | 🔴 КРИТИЧНО |
| **UI placeholder тексты** | Много (но это нормально для Compose) | 🟢 OK |

---

## 🔴 КРИТИЧЕСКИЕ НАХОЖДЕНИЯ: ХАРДКОД

### 1. Хардкод IP-адресов и хостов (47+ мест)

| Файл | Значение | Тип |
|------|----------|-----|
| `android/Nexus/app/src/main/java/com/nexuschat/app/MainActivity.kt:149` | `85.31.186.98:443` | obfs4 bridge |
| `android/Nexus/app/src/main/java/com/nexuschat/app/MainActivity.kt:157` | `meek.azureedge.net:443` | meek bridge |
| `android/Nexus/app/src/main/java/com/nexuschat/app/MainActivity.kt:163` | `www.google.com:443` | domain fronting |
| `android/Nexus/app/src/main/java/com/nexuschat/app/MainActivity.kt:179` | `https://check.torproject.org/api/ip` | health check |
| `android/Nexus/app/src/main/java/com/nexuschat/app/services/TorBridgeConfig.kt:43-50` | 4 obfs4 bridges с IP/фп/сертификатами | bridge pool |
| `android/Nexus/app/src/main/java/com/nexuschat/app/services/TorBridgeConfig.kt:50` | `meek.azureedge.net:443` | meek bridge |
| `android/Nexus/app/src/main/java/com/nexuschat/app/services/DomainFronting.kt:38,55` | `www.google.com` | front domain |
| `android/Nexus/app/src/main/java/com/nexuschat/app/services/TransportManager.kt:45` | `https://check.torproject.org/api/ip` | health check |
| `android/Nexus/app/src/main/java/com/nexuschat/app/services/TransportManager.kt:46` | `https://snowflake-broker.torproject.net/` | snowflake broker |
| `android/Nexus/app/src/main/java/com/nexuschat/app/services/TransportChainBuilder.kt` | `https://check.torproject.org/`, `https://www.google.com/` | health checks |
| `android/Nexus/app/src/main/java/com/nexuschat/app/services/SnowflakeTransport.kt` | `https://snowflake-broker.torproject.net/`, STUN серверы | broker + STUN |
| `android/Nexus/app/src/main/java/com/nexuschat/app/bridges/WebRtcBridge.kt:94-96` | `stun.l.google.com:19302`, `stun1.l.google.com:19302`, `stun.cloudflare.com:3478` | STUN |
| `android/Nexus/app/src/main/java/com/nexuschat/app/bridges/Bridges.kt:486` | `https://snowflake-broker.torproject.net/` | fallback |
| `android/N3/app/src/main/java/com/n3/app/bridge/BridgeOrchestrator.kt` | `https://www.google.com/generate_204` | connectivity test |
| `android/N3/app/src/main/java/com/n3/app/MainActivity.kt:161` | `http://smp.simplex.chat:5223` | SMP connectivity |
| `android/N3/app/src/main/java/com/n3/app/bridges/Bridges.kt:68` | `https://check.torproject.org/` | IP check |
| `android/N2/app/src/main/java/com/example/data/TorEmbeddedController.kt` | 3 bridge lines с IP/фп | embedded Tor |
| `android/N2/app/src/main/java/com/example/ui/viewmodels/ProtocolViewModel.kt` | `stun:stun.l.google.com:19302` | STUN default |
| `android/N2/app/src/main/java/com/example/ui/GameViewModel.kt` | `stun:stun.l.google.com:19302` | STUN default |
| `android/N2/app/src/main/java/com/example/protocols/ProtocolRegistry.kt` | `stun.l.google.com:19302` | STUN default |
| `sdk/transport-sdk/src/main/java/com/paranoidx/sdk/transport/TransportObfuscator.kt` | `www.google.com`, `www.cloudflare.com` | TLS SNI |
| `sdk/transport-sdk/src/main/java/com/paranoidx/sdk/transport/TransportSelector.kt` | `www.cloudflare.com`, `www.google.com` | TLS SNI |
| `transport/docker/v2ray/config.json` | 3 trojan outbounds с IP/password, Reality dest `www.cloudflare.com` | V2Ray config |

### 2. Хардкод отпечатков (fingerprints) и сертификатов (12+ уникальных)

| Значение | Где используется |
|----------|------------------|
| `D2B4E4F9A660B6B5D4B7E1C9A2E3F8D0C1B4A9E8` | MainActivity, TorBridgeConfig (4 bridges), N2 TorEmbeddedController |
| `F5CB4E4F9A660B6B5D4B7E1C9A2E3F8D0C1B4A9E8` | MainActivity cert, TorBridgeConfig cert |
| `A5CB4E4F9A660B6B5D4B7E1C9A2E3F8D0C1B4A9E8` | TorBridgeConfig 2-й bridge cert |
| `B5CB4E4F9A660B6B5D4B7E1C9A2E3F8D0C1B4A9E8` | TorBridgeConfig 3-й bridge cert |
| `C5CB4E4F9A660B6B5D4B7E1C9A2E3F8D0C1B4A9E8` | TorBridgeConfig 4-й bridge cert |
| `B2B4E4F9A660B6B5D4B7E1C9A2E3F8D0C1B4A9E8` | TorBridgeConfig 4-й bridge fingerprint |
| `3D7A2E5C6D8A4F7B912C3E5A8D6F9B0C1A2E4D8` | N2 TorEmbeddedController obfs4 fingerprint |
| `G5tO7VfK8wLxR2Eq3YcNm6Xp9bA4sJ7dF0gH1jK2lZ3xV4cB5nM6aQ7wE8rT9yU0i` | N2 obfs4 cert |
| `813C8F8E4C9C5E1A2B3D4F6G7H8I9J0K1L2M3N4` | N2 meek_lite 1 fingerprint |
| `9A8B7C6D5E4F3A2B1C0D9E8F7A6B5C4D3E2F1G0` | N2 meek_lite 2 fingerprint |
| `0a7a1204-5450-4541-bbf9-54b3a3de6c51` | V2Ray VLESS UUID |
| `-DMH4aSevNhVWXEonqKbMN9eN6Pw5AKFBAK51ilMgFY` | V2Ray Reality private key |

### 3. Хардкод паролей/ключей в Docker конфиге
- `transport/docker/v2ray/config.json:94` — `password: "humanity"` (3 trojan outbounds)
- VLESS UUID: `0a7a1204-5450-4541-bbf9-54b3a3de6c51`
- Reality private key и shortIds

---

## 🟡 ВЫСОКАЯ: ЗАТЫЧКИ И STUB В ЛОГИКЕ

### 1. `WebRtcBridge.kt:204-206` — **Speakerphone stub**
```kotlin
@JavascriptInterface
fun setSpeakerphone(enabled: Boolean) {
    Log.i(TAG, "Speakerphone ${if (enabled) "on" else "off"} (stub)")
}
```
**Проблема:** Метод существует в JS-интерфейсе, но **ничего не делает**. Нет реального переключения аудио-маршрута (Speakerphone vs Earpiece).

### 2. `TrafficPadding.kt` — **Stub реализация для cover traffic**
```kotlin
fun getJitteredDelay(baseMs: Long = 50): Long { ... } // работает
fun obfuscateLength(originalLen: Int, blockSize: Int = 32): Int { ... } // работает
```
**Проблема:** Класс существует и имеет методы, но **не интегрирован** в транспортный стек. `TransportManager` не вызывает `TrafficPadding` для реального паддинга пакетов. Это мёртвый код.

### 3. `ParanoidBackgammonDemo/src/main/java/com/paranoidx/demo/game/GameEngine.kt:158`
```kotlin
return true // any dice that reaches or passes home in long
```
**Проблема:** Заглушка в игровой логике (не основной проект, но всё же).

### 4. `TorBridgeConfig.kt:76-91` — **fetchBridgesFromServer частично реализовано**
- Парсит только `obfs4` строки
- Не поддерживает `meek`, `snowflake`, `webtunnel` форматы
- Нет кэширования/обновления по расписанию

---

## 🟡 ВЫСОКАЯ: ОТСУТСТВУЮЩИЕ КОНФИГУРАЦИИ

### 1. **Нет внешних конфиг-файлов для bridges**
Все bridges захардкожены в коде:
- `TorBridgeConfig.kt` — `defaultObfs4Bridges`, `defaultMeekBridges`
- `MainActivity.kt` — `BridgeOrchestrator` с захардкоженными адресами
- `N2/TorEmbeddedController.kt` — статический список
- **Требуется:** JSON/TOML/YAML конфиг в `assets/bridges.json` или загружаемый с сервера

### 2. **Нет конфига для STUN/TURN серверов**
- `WebRtcBridge.kt` — 3 захардкоженных STUN
- `ProtocolViewModel`, `GameViewModel`, `ProtocolRegistry` — дублируют `stun.l.google.com:19302`
- **Требуется:** Единый `stun-turn-config.json` с поддержкой TURN (username/password)

### 3. **Нет конфига для Snowflake broker**
- `TransportManager.kt`, `SnowflakeTransport.kt`, `Bridges.kt` — все дублируют URL
- **Требуется:** Единая константа или конфиг

### 4. **Нет конфига для Domain Fronting**
- `DomainFronting.kt`, `MeekTransport.kt`, `TransportChainBuilder.kt`, `TransportSelector.kt` — дублируют `www.google.com` / `www.cloudflare.com`
- **Требуется:** Список front domains с весами/приоритетами

### 5. **Нет конфига для health check endpoints**
- `check.torproject.org` (HTTP + API), `www.google.com/generate_204`, `smp.simplex.chat:5223`
- **Требуется:** Настраиваемый список health check URLs с таймаутами

### 6. **V2Ray конфиг захардкожен в Docker**
- `transport/docker/v2ray/config.json` — конкретные IP, пароли, Reality keys
- **Требуется:** Шаблон с переменными окружения или генерация из конфига

---

## 🟢 НОРМАЛЬНО: UI PLACEHOLDERS (НЕ БАГИ)

Следующие — это **нормальные** Compose placeholder'ы для текстовых полей, **НЕ** затычки логики:
- `SimpleXRelayConfigPane.kt` — `placeholder = { Text("smp://...") }` и др.
- `SimpleXChatScreen.kt` — placeholder для ввода сообщений, onion адресов
- `GameWelcomeScreen.kt` — placeholder для имен игроков
- `GameChatPanel.kt`, `ScheduledMessageComposer.kt`, `ChatSearchPanel.kt` — стандартные UI подсказки
- `AppLockScreen.kt` — `placeholder = { Text("******") }` для PIN ввода
- `Translations.kt` — локализованные placeholder строки (chat_placeholder, passcode_placeholder и т.д.)

**Вердикт:** Это правильный UX, не требует исправления.

---

## 📋 ПОДРОБНЫЙ СПИСОК ПО ФАЙЛАМ

### `android/Nexus/app/src/main/java/com/nexuschat/app/MainActivity.kt`
| Строка | Проблема | Исправление |
|--------|----------|-------------|
| 149 | `address = "85.31.186.98"` | Загружать из `bridges.json` |
| 150 | `fingerprint = "D2B4..."` | Загружать из конфига |
| 151-153 | `cert`, `iat-mode` hardcoded | Конфиг |
| 157 | `address = "meek.azureedge.net"` | Конфиг |
| 163 | `address = "www.google.com"` | Конфиг front domains |
| 179 | `https://check.torproject.org/api/ip` | Настраиваемый health check |

### `android/Nexus/app/src/main/java/com/nexuschat/app/services/TorBridgeConfig.kt`
| Строка | Проблема |
|--------|----------|
| 43-50 | 4 obfs4 bridges + 1 meek — все захардкожены |
| 76-91 | `fetchBridgesFromServer` парсит только obfs4 |
| 128-138 | `findPluginPath` — логика поиска бинарников нормальная |

### `android/Nexus/app/src/main/java/com/nexuschat/app/services/DomainFronting.kt`
| Строка | Проблема |
|--------|----------|
| 38 | `frontDomain = "www.google.com"` hardcoded |
| 55 | `www.google.com` hardcoded в другом месте |

### `android/Nexus/app/src/main/java/com/nexuschat/app/services/TransportManager.kt`
| Строка | Проблема |
|--------|----------|
| 45 | `CHECK_URL = "https://check.torproject.org/api/ip"` |
| 46 | `SNOWFLAKE_BROKER = "https://snowflake-broker.torproject.net/"` |
| 297 | `url("https://check.torproject.org/")` — health check |

### `android/Nexus/app/src/main/java/com/nexuschat/app/services/SnowflakeTransport.kt`
| Строка | Проблема |
|--------|----------|
| 28 | `BROKER_URL = "https://snowflake-broker.torproject.net/"` |
| 67-68 | STUN серверы захардкожены |

### `android/Nexus/app/src/main/java/com/nexuschat/app/bridges/WebRtcBridge.kt`
| Строка | Проблема |
|--------|----------|
| 94-96 | 3 STUN сервера захардкожены |
| 204-206 | **STUB: `setSpeakerphone` ничего не делает** |
| 280 | `TransportManager.getInstance(ctx).activeTransport.name` — зависит от TransportManager |

### `android/N3/app/src/main/java/com/n3/app/bridge/BridgeOrchestrator.kt`
| Строка | Проблема |
|--------|----------|
| ~ | `TEST_URL = "https://www.google.com/generate_204"` |

### `android/N3/app/src/main/java/com/n3/app/MainActivity.kt`
| Строка | Проблема |
|--------|----------|
| 161 | `http://smp.simplex.chat:5223` — SMP health check |

### `android/N3/app/src/main/java/com/n3/app/bridges/Bridges.kt`
| Строка | Проблема |
|--------|----------|
| 68 | `https://check.torproject.org/` — IP check |

### `android/N2/app/src/main/java/com/example/data/TorEmbeddedController.kt`
| Проблема | 3 статических BridgeConfig с полными bridge line строками |

### `transport/docker/v2ray/config.json`
| Проблема | Полностью статический конфиг с реальными IP, паролями, ключами |

---

## 🎯 ПЛАН ИСПРАВЛЕНИЯ (ПРИОРИТЕТЫ)

### P0 — Критично (блокирует production)
1. **Создать `assets/bridges.json`** — единый источник правды для всех bridges (obfs4, meek, snowflake, webtunnel)
2. **Создать `assets/stun-turn.json`** — STUN/TURN серверы с credentials для TURN
3. **Создать `assets/front-domains.json`** — список domain fronting хостов с приоритетами
4. **Создать `assets/health-checks.json`** — настраиваемые эндпоинты проверки
5. **Убрать ВСЕ хардкод IP/фп/сертификатов/URL** из Kotlin кода
6. **Реализовать `setSpeakerphone` в WebRtcBridge** — реальное переключение AudioManager
7. **Интегрировать `TrafficPadding` в `TransportManager`** — реальный паддинг пакетов

### P1 — Высоко (нужно для устойчивости)
8. **Реализовать парсинг всех типов bridges в `fetchBridgesFromServer`** (meek, snowflake, webtunnel)
9. **Добавить автоматическое обновление bridges** по расписанию (каждые 24ч)
10. **Внедрить конфиг-систему** (загрузка JSON из assets + fallback на встроенные defaults)
11. **Сделать V2Ray конфиг шаблонизируемым** (переменные окружения / JSON шаблон)

### P2 — Средне (качество кода)
12. **Унифицировать константы** — убрать дублирование `stun.l.google.com` в 6+ местах
13. **Добавить валидацию конфигов** при загрузке (schema check)
14. **Логировать источники конфига** (builtin / assets / remote)

---

## 🔧 ТЕХНИЧЕСКОЕ РЕШЕНИЕ: АРХИТЕКТУРА КОНФИГОВ

```
assets/
├── bridges.json           # { obfs4: [...], meek: [...], snowflake: {...} }
├── stun-turn.json         # { stun: [...], turn: [{url, username, credential}] }
├── front-domains.json     # [ {domain: "www.google.com", weight: 10}, ... ]
├── health-checks.json     # [ {url: "...", timeout: 5000, expected: "..."} ]
├── snowflake.json         # { broker: "...", stun: [...] }
└── transport-defaults.json# Общие дефолты для всех модулей
```

**Loader pattern:**
```kotlin
object TransportConfig {
    private val bridges = loadJson<BridgesConfig>("bridges.json") 
        ?: BuiltinBridges.default()
    private val stunTurn = loadJson<StunTurnConfig>("stun-turn.json")
        ?: BuiltinStunTurn.default()
    // ...
}
```

---

## 📝 ЗАКЛЮЧЕНИЕ

**ParanoidStack имеет мощную архитектуру, но НЕ ГОТОВ к production без устранения хардкода.**

### Главные выводы:
1. **Весь транспортный стек привязан к конкретным операторам/хостам** (Google, Cloudflare, TorProject, конкретные obfs4 ноды)
2. **Нет никакой конфигурируемости** — смена bridge требует пересборки APK
3. **WebRTC speakerphone — заглушка** (голосовые звонки не переключают динамик)
4. **TrafficPadding — мёртвый код** (не используется в TransportManager)
4. **V2Ray Docker конфиг содержит реальные секреты** — нельзя коммитить в git

### Следующие шаги:
1. Создать конфиг-файлы в `assets/`
2. Рефакторить `TorBridgeConfig`, `TransportManager`, `WebRtcBridge`, `DomainFronting`, `BridgeOrchestrator`
3. Реализовать `setSpeakerphone` через `AudioManager.setSpeakerphoneOn()`
4. Подключить `TrafficPadding` к `TransportManager.send()` / `receive()`
5. Убрать секреты из `transport/docker/v2ray/config.json` (использовать env vars)

---

*Отчёт сгенерирован автоматически при аудите кодовой базы ParanoidStack. Все найденные проблемы требуют исправления перед релизом.*