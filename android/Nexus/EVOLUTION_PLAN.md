# PLAN ЭВОЛЮЦИИ NEXUSCHAT — ЗАМЕНА СИМУЛЯЦИЙ НА PRODUCTION КОД

## 🎯 ЦЕЛЬ
Полностью убрать все `simulated/placeholder/mock` реализации и заменить их на работающий production-ready код с тестами и CI.

---

## 📦 ЭТАП 1: TOR DAEMON (приоритет 1)
**Файлы:** `TorService.kt`, `TorControlConnection.kt`, `app/build.gradle`

### 1.1 Подключить реальный tor-android AAR
- [ ] Добавить зависимость `info.guardianproject:tor-android:0.4.9.11` (последняя стабильная)
- [ ] Настроить `jniLibs` для arm64-v8a, armeabi-v7a, x86_64
- [ ] Использовать `TorServiceConnection.connect()` вместо заглушки

### 1.2 Реализовать запуск встроенного Tor
```kotlin
// В TorService.startTor():
torConn = TorServiceConnection(this)
torConn.connect { success ->
    if (success) {
        isRunning = true
        // Читаем hostname из hidden_service/hostname
    }
}
```

### 1.3 Control Port + NEWNYM
- [ ] Cookie authentication из `tor_data/control_auth_cookie`
- [ ] Асинхронный `signal("NEWNYM")` с callback
- [ ] Мониторинг цепей через `GETINFO circuit-status`

### 1.4 Orbot fallback
- [ ] `OrbotHelper.isOrbotInstalled()` + `requestStartTor()`
- [ ] Broadcast receiver для `org.torproject.android.intent.action.TOR_STARTED`

### 1.5 Тесты
- [ ] Unit: `TorControlConnection` — auth, signal, getinfo
- [ ] Integration: запуск TorService, проверка SOCKS5:9050, hidden service

---

## 📦 ЭТАП 2: NATIVE WEBRTC (приоритет 2)
**Файлы:** `WebRtcBridge.kt`, `app/build.gradle`, `app/src/main/jniLibs/`

### 2.1 Подключить libwebrtc AAR
- [ ] `implementation 'io.github.webrtc-sdk:android:114.5735.14'` (или свежая M-версия)
- [ ] Проверить наличие `JavaAudioDeviceModule`, `PeerConnectionFactory`

### 2.2 Реализовать полный Observer
```kotlin
override fun onDataChannel(d: DataChannel) { /* для data channels */ }
override fun onAddTrack(r: RtpReceiver, streams: Array<MediaStream>) { /* remote audio */ }
override fun onIceConnectionReceivingChange(receiving: Boolean) { }
```

### 2.3 ICE/TURN через Tor
- [ ] Настроить `PortAllocator` для Tor SOCKS5
- [ ] Добавить TURN сервер как `.onion` (опционально)

### 2.4 Тесты
- [ ] Unit: SDP munging, stats parsing
- [ ] Integration: звонок между двумя эмуляторами через Tor

---

## 📦 ЭТАП 3: TAILSCALE INTEGRATION (приоритет 3)
**Файлы:** `TailscaleBridge.kt`, `app/build.gradle`

### 3.1 Подключить tailscale-android SDK
- [ ] Репозиторий: `maven { url 'https://pkgs.tailscale.com/android' }`
- [ ] `implementation 'com.tailscale.ipn:tailscale-android:1.66.0'`

### 3.2 Реализовать VPN tunnel
- [ ] `TailscaleVpnService` extends `VpnService`
- [ ] `IPN.start()` + `IPN.setConfig()` с auth key
- [ ] Получение IP: `IPN.getStatus().self?.ipAddresses?.first()`

### 3.3 Тесты
- [ ] Unit: API calls через Tor
- [ ] Integration: поднятие туннеля, пинг 100.x.x.x

---

## 📦 ЭТАП 4: SMP SERVER (приоритет 4)
**Файлы:** `SmpServerService.kt`, `app/src/main/jniLibs/`

### 4.1 Встроить SMP сервер бинарник
- [ ] Скачать `simplexmq` release для Android (arm64/armv7/x86_64)
- [ ] Положить в `jniLibs/` или assets + extract при первом запуске

### 4.2 Запуск как subprocess
```kotlin
val pb = ProcessBuilder("./simplexmq", "server", "--port", "5223")
pb.directory(filesDir).redirectErrorStream(true).start()
```

### 4.3 Протокол SMP
- [ ] Реализовать framing: `NEW`, `SUB`, `SEND`, `ACK`, `MSG`, `PING`
- [ ] Шифрование: X25519 + XSalsa20-Poly1305 (совпадает с JS)

### 4.4 Тесты
- [ ] Unit: framing, crypto
- [ ] Integration: обмен сообщениями между двумя клиентами через локальный SMP

---

## 📦 ЭТАП 5: SECURITY HARDENING (приоритет 5)

### 5.1 Keystore / Biometric
- [ ] StrongBox preference, fallback на software
- [ ] BiometricPrompt с `setInvalidatedByBiometricEnrollment(true)`

### 5.2 Network Security
- [ ] Certificate pinning для Tailscale API (real pins)
- [ ] `network_security_config.xml` — только system CAs

### 5.3 Panic Mode
- [ ] `AndroidKeystore.clearAll()` — реальное удаление ключей
- [ ] `TorService.destroy()` — `controlConn?.signal("SHUTDOWN")`
- [ ] `SmpServerService.destroy()` — закрытие WebSocket + subprocess

### 5.4 Тесты
- [ ] Unit: panic wipe проверка (Keystore пуст, IndexedDB пуст)
- [ ] Integration: полный цикл panic → relock

---

## 📦 ЭТАП 6: CI/CD & TESTING (приоритет 6)

### 6.1 GitHub Actions
```yaml
jobs:
  build:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4 (jdk: '17')
      - run: ./gradlew assembleDebug test
      - uses: actions/upload-artifact@v4 (app-debug.apk)
```

### 6.2 Тестовый набор
- [ ] Unit: `./gradlew test` (JUnit + Robolectric)
- [ ] Instrumented: `./gradlew connectedAndroidTest` (эмулятор API 34)
- [ ] Lint: `./gradlew lint`

### 6.3 Авто-отчёты
- [ ] JUnit XML → GitHub Actions summary
- [ ] Coverage: JaCoCo → Codecov
- [ ] APK size tracking

---

## 📦 ЭТАП 7: RELEASE PIPELINE (приоритет 7)

### 7.1 Keystore
- [ ] Генерация `nexuschat.keystore` (RSA-4096, 10 лет)
- [ ] GitHub Secrets: `KEYSTORE_BASE64`, `STORE_PASS`, `KEY_PASS`, `ALIAS`

### 7.2 Подписание
```gradle
signingConfigs {
    release { storeFile file(KEYSTORE_PATH) ... }
}
```

### 7.3 ProGuard/R8
- [ ] `proguard-rules.pro` для WebRTC, Tor, Tink, libsodium
- [ ] `-keep` для JS bridges (`@JavascriptInterface`)

### 7.4 Публикация
- [ ] GitHub Releases + `app-release.apk`
- [ ] F-Droid metadata (опционально)

---

## 🚀 ПОРЯДОК ВЫПОЛНЕНИЯ (асинхронно где возможно)

```
[1] TOR DAEMON          ← СРАЗУ (блокирует сеть)
[2] NATIVE WEBRTC       ← ПАРАЛЛЕЛЬНО с Tor
[3] TAILSCALE           ← ПОСЛЕ Tor (нужен SOCKS5)
[4] SMP SERVER          ← ПОСЛЕ Tor + WebRTC
[5] SECURITY            ← ВСЕГДА
[6] CI/CD               ← ПОСЛЕ стабильных тестов
[7] RELEASE             ← В КОНЦЕ
```

---

## 📊 КРИТЕРИИ ГОТОВНОСТИ К РЕЛИЗУ

| Метрика | Цель |
|---------|------|
| APK size | < 50 MB |
| Cold start | < 3 сек |
| Tor bootstrap | < 15 сек |
| Call setup | < 5 сек |
| Panic wipe | < 2 сек |
| Unit coverage | > 80% |
| Zero critical lint | ✅ |
| Zero ProGuard crashes | ✅ |

---

## 🔄 АВТОНОМНЫЙ ЦИКЛ (для каждого этапа)

1. **Планирование** → создаю TODO список в `TASKS.md`
2. **Реализация** → правлю код, добавляю зависимости
3. **Юнит-тесты** → `./gradlew test` + проверка coverage
4. **Интеграционные тесты** → запуск на эмуляторе/устройстве
4. **Дебаг** → logcat, breakpoints, фиксы
5. **Бэкап** → `git commit -m "feat: ..." + tag`
6. **Отчёт** → обновляю `REPORT.md` с результатами
7. **Следующий этап**

---

**СТАРТ: ЭТАП 1 — TOR DAEMON** 🚀