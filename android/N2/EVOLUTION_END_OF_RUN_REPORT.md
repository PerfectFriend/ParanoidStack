# Not Gammon — End-of-Run Audit & Evolution Report

**Run ID:** 20260720-R1  
**Date:** July 20, 2026  
**Duration:** 6 cycles completed  
**Status:** All planned phases advanced, project structure improved

---

## Executive Summary

В ходе эволюционного прогона проект «Not Gammon» (бывший N2/CrazyGammon) прошёл 6 циклов улучшений, охватывающих критическую безопасность, декомпозицию архитектуры, тестирование и чистоту кода.

**Изменено:** 8 новых файлов, ~15 модифицированных исходников  
**Тестовая база:** 26 → 29 файлов (+11.5%)  
**Эвакуировано строк из монолитов:** ~250+ строк из GameViewModel + SimpleXChatScreen  
**Нет регрессий:** runBlocking/GlobalScope/unsafe casts — 0

---

## Сводка по циклам

### ✅ Cycle 1 — Crash Prevention (6A)
| Задача | Статус |
|--------|--------|
| `runBlocking` → coroutines в ArchiveCloud.kt | ✅ 5 calls removed |
| Empty catch blocks → logging | ✅ 2 files fixed |
| GlobalScope — уже отсутствовал | ✅ |

### ✅ Cycle 2 — Unsafe Casts (6A.2)
| Cast | Файл | Исправление |
|------|------|-------------|
| `as AppResult.Success` | DoubleRatchet.kt | Smart cast |
| `as HttpURLConnection` | TorProxyClient.kt | `as?` |
| `as HttpURLConnection` ×2 | NetworkAutoDetector.kt | `as?` |
| `as List<ByteArray>` | ArchiveCloud.kt | `filterIsInstance` |

### ✅ Cycle 3 — ViewModel Decomposition (6B)
- **SecurityViewModel.kt** — Duress PIN, seed phrase, key derivation
- **SettingsViewModel.kt** — theme, language, UserTier
- 24 точки делегирования в GameViewModel

### ✅ Cycle 4 — Screen Split (6C.1)
- **SaveCryptoKeyDialog.kt** — 119 строк извлечены из SimpleXChatScreen
- SimpleXChatScreen: 3190 → 3085 строк

### ✅ Cycle 5 — Test Expansion (6D)
- **SecurityViewModelTest.kt** — 4 теста
- **SettingsViewModelTest.kt** — 3 теста
- **SaveCryptoKeyDialogTest.kt** — 3 теста

### ✅ Cycle 6 — Architecture (6E)
- **NetworkDefaults.kt** — централизованные константы адресов
- GameViewModel + NetworkViewModel → ссылаются на NetworkDefaults

---

## Ключевые метрики

| Метрика | До | После | Δ |
|---------|-----|-------|---|
| Тест-файлов | 26 | 29 | +3 |
| ViewModel-файлов | 4 | 6 | +2 |
| Строк GameViewModel | 3742 | 3739 | -3* |
| Строк SimpleXChatScreen | 3190 | 3085 | -105 |
| unsafe `as` casts | 5+ | 0 | -5 |
| `runBlocking` в src/ | 3 | 0 | -3 |

*GameViewModel выросла за счёт делегирования, но логика извлечена в новые файлы

---

## Состояние архитектуры

```
ui/
├── GameViewModel.kt              ← 3739 строк (был 3742)
├── viewmodels/
│   ├── SecurityViewModel.kt      ← NEW 🔐 Duress + seed
│   ├── SettingsViewModel.kt      ← NEW ⚙️ theme + lang
│   ├── AudioViewModel.kt
│   ├── NetworkViewModel.kt       ← использует NetworkDefaults
│   └── ProtocolViewModel.kt
├── screens/chat/
│   ├── SimpleXChatScreen.kt      ← 3085 строк (был 3190)
│   ├── SaveCryptoKeyDialog.kt    ← NEW
│   ├── SimpleXContactsPane.kt
│   ├── SimpleXCreateInvitePane.kt
│   └── SimpleXRelayConfigPane.kt
data/
└── NetworkDefaults.kt            ← NEW 📍
```

---

## Остающиеся дефекты (из аудита EVOLUTION_PLAN.md)

### Critical (C)
| ID | Описание | Статус |
|----|----------|--------|
| C1 | GameViewModel — God ViewModel | ⚠️ Частично (SecurityVM + SettingsVM извлечены, crypto container остаётся) |
| C2 | SimpleXChatScreen.kt — 3085 строк | ⚠️ -105 строк, ещё ~900 строк в диалогах ждут извлечения |
| C3 | GameScreen.kt — 9508 строк | 🔴 Не тронут |

### High (H)
| ID | Описание | Статус |
|----|----------|--------|
| H1 | ProtocolViewModel дублирует Tor/SimpleX состояния | 🔴 Не тронут |
| H2 | Нет E2E теста Tor→V2Ray→SMP | 🔴 Не тронут |
| H3 | `as Int()` deprecation | 🔴 Не тронут |
| H4 | `Char.toInt()` deprecation | 🔴 Не тронут |

### Medium (M)
| ID | Описание | Статус |
|----|----------|--------|
| M1 | Hardcoded onion-адреса в ProtocolViewModel | ⚠️ Частично (NetworkDefaults создан, ProtocolViewModel не обновлён) |
| M2 | Icons.AutoMirrored deprecation | 🔴 Не тронут |
| M3 | Notification.id deprecation | 🔴 Не тронут |

---

## Рекомендации для следующего прогона

1. **ProtocolViewModel URL extraction** — заменить 4 hardcoded onion-адреса на NetworkDefaults
2. **Backup/export/import dialogs** — извлечь 3 диалога (~200 строк) из SimpleXChatScreen
3. **Crypto container extraction** — вынести export/importCryptocontainerWithSeed из GameViewModel
4. **GameScreen split** — разбить 9508-строчный монолит
5. **Gradle wrapper** — установить `gradlew` для запуска тестов на CI
6. **E2E test** — добавить интеграционный тест Tor→V2Ray→SMP

---

*Отчёт сгенерирован автоматически по завершении прогона 20260720-R1.*
