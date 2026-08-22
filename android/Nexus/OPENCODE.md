# NexusChat — opencode task list

Проект готов к сборке. JDK, SDK, Gradle уже настроены.
Ниже — конкретные места в коде, которые нужно доделать перед сборкой APK,
и точные инструкции что именно написать.

---

## СБОРКА (запускать после всех правок)

```bash
cd nexuschat-android
./gradlew assembleDebug                         # debug APK для теста
./gradlew assembleRelease \
  -PKEYSTORE_PATH=./nexuschat.keystore \
  -PKEYSTORE_PASS=PASS \
  -PKEY_ALIAS=nexuschat \
  -PKEY_PASS=PASS                              # release APK
```

APK: `app/build/outputs/apk/debug/app-debug.apk`

---

## ЗАДАЧА 1 — Исправить дублирующееся поле webView в MainActivity.kt

**Файл:** `app/src/main/java/com/nexuschat/app/MainActivity.kt`

**Проблема:** В конце `addJavaScriptBridges()` добавлена вторая строка:
```kotlin
internal val webView get() = findViewById<android.webkit.WebView>(R.id.webView)
```
Это конфликтует с `private lateinit var webView: WebView` в начале класса.

**Что сделать:** Удалить эту строку. Она единственная с `internal val webView`.

Затем в `addJavaScriptBridges()` передать `this` в `WebRtcBridge`, чтобы он мог
звонить `evaluateJavascript`. Заменить строку:
```kotlin
webView.addJavascriptInterface(
    com.nexuschat.app.bridges.WebRtcBridge(this), "AndroidWebRTC")
```
на:
```kotlin
val rtcBridge = com.nexuschat.app.bridges.WebRtcBridge(this) { js ->
    runOnUiThread { webView.evaluateJavascript(js, null) }
}
webView.addJavascriptInterface(rtcBridge, "AndroidWebRTC")
```

---

## ЗАДАЧА 2 — WebRtcBridge: сделать callJs рабочим

**Файл:** `app/src/main/java/com/nexuschat/app/bridges/WebRtcBridge.kt`

**Проблема:** `callJs()` — заглушка, только пишет в Log, JS не вызывается.

**Что сделать:**

1. Изменить конструктор класса — добавить лямбду:
```kotlin
class WebRtcBridge(
    private val ctx: Context,
    private val evalJs: (String) -> Unit   // ← добавить этот параметр
) {
```

2. Заменить тело `callJs`:
```kotlin
private fun callJs(js: String) {
    evalJs(js)
}
```

3. То же для `callJs` в observer-колбэках — убедиться что везде вызывается `callJs(...)`,
   а не прямой `Log`.

---

## ЗАДАЧА 3 — TorService: заменить заглушку на реальный запуск tor-android

**Файл:** `app/src/main/java/com/nexuschat/app/services/TorService.kt`

**Проблема:** Закомментированный вызов `torConn?.startWithRepresentativeConfigFile(torrcFile)`
и `delay(3000)` — Tor не запускается по-настоящему.

**Что сделать:** Заменить блок с комментарием (строки ~88–97) на:

```kotlin
// Запустить tor-android через OrbotHelper
// Если Orbot установлен на устройстве — использовать его SOCKS5
val orbotInstalled = OrbotHelper.isOrbotInstalled(this@TorService)
if (orbotInstalled) {
    OrbotHelper.requestStartTor(this@TorService)
    // Ждём запуска Orbot (он сигнализирует через broadcast)
    // SOCKS5 будет доступен на 127.0.0.1:9050 автоматически
    delay(4000)
    isRunning = true
    onionAddress = "via-orbot.onion"   // реальный адрес читается отдельно
} else {
    // Встроенный Tor через TorServiceConnection (tor-android 0.4.7.17)
    // API: https://github.com/guardianproject/tor-android
    torConn = TorServiceConnection(this@TorService)
    torConn?.connect()   // блокирует до готовности
    // После connect() SOCKS5 :9050 готов, hidden service создаётся асинхронно
    isRunning = torConn != null
    delay(2000)
}
```

**Дополнительно:** Добавить в `AndroidManifest.xml` внутри `<application>`:
```xml
<queries>
    <package android:name="org.torproject.android" />
</queries>
```

---

## ЗАДАЧА 4 — TailscaleBridge: починить callJs

**Файл:** `app/src/main/java/com/nexuschat/app/bridges/Bridges.kt`

**Проблема:** `callJs(ctx, ...)` в `TailscaleBridge` — заглушка, JS не вызывается.

**Что сделать:** Передать `evalJs`-лямбду аналогично WebRtcBridge.
В `MainActivity.kt` в `addJavaScriptBridges()` заменить:
```kotlin
webView.addJavascriptInterface(TailscaleBridge(this), "AndroidTailscale")
```
на:
```kotlin
webView.addJavascriptInterface(
    TailscaleBridge(this) { js -> runOnUiThread { webView.evaluateJavascript(js, null) } },
    "AndroidTailscale"
)
```

В `TailscaleBridge` изменить конструктор:
```kotlin
class TailscaleBridge(
    private val ctx: Context,
    private val evalJs: (String) -> Unit
)
```

И заменить `callJs(ctx, ...)` на `evalJs(...)`:
```kotlin
// было:
callJs(ctx, "$callbackFn(${Gson().toJson(resp)})")
// стало:
evalJs("$callbackFn(${Gson().toJson(resp)})")
```

Удалить старый приватный метод `callJs(ctx: Context, js: String)` из класса.

---

## ЗАДАЧА 5 — BuildInfo: заменить заглушку на реальный BuildConfig

**Файл:** `app/src/main/java/com/nexuschat/app/bridges/Bridges.kt`

**Проблема:** В конце файла:
```kotlin
object BuildInfo {
    const val VERSION_NAME = "1.0.0"
    const val IS_DEBUG     = false
}
```

**Что сделать:** Удалить этот object целиком.
В `SystemBridge` заменить:
```kotlin
fun getVersion(): String = BuildInfo.VERSION_NAME
fun isDebug(): Boolean = BuildInfo.IS_DEBUG
```
на:
```kotlin
fun getVersion(): String = com.nexuschat.app.BuildConfig.VERSION_NAME
fun isDebug(): Boolean = com.nexuschat.app.BuildConfig.DEBUG
```

---

## ЗАДАЧА 6 — themes.xml: добавить xmlns:tools

**Файл:** `app/src/main/res/values/themes.xml`

**Проблема:** Используется `tools:targetApi` без объявления namespace.

**Что сделать:** В тег `<resources>` добавить атрибут:
```xml
<resources xmlns:tools="http://schemas.android.com/tools">
```

---

## ЗАДАЧА 7 — network_security_config.xml: заменить placeholder pins

**Файл:** `app/src/main/res/xml/network_security_config.xml`

**Проблема:** Два placeholder-пина для api.tailscale.com.

**Что сделать:** Получить реальные пины и вставить, **или** удалить весь блок `<pin-set>`
оставив только:
```xml
<domain-config cleartextTrafficPermitted="false">
    <domain includeSubdomains="true">api.tailscale.com</domain>
</domain-config>
```
(без pinning — допустимо для первой версии, pinning добавить позже).

---

## ЗАДАЧА 8 — gradle-wrapper.jar: получить настоящий

**Файл:** `gradle/wrapper/gradle-wrapper.jar`

Текущий файл — невалидная заглушка (112 байт).

**Что сделать** (один из вариантов):

```bash
# Вариант A — если есть Gradle в системе:
gradle wrapper --gradle-version=8.6

# Вариант B — скачать из официального дистрибутива:
curl -L https://services.gradle.org/distributions/gradle-8.6-bin.zip -o /tmp/g.zip
unzip -p /tmp/g.zip "gradle-8.6/lib/plugins/gradle-wrapper-*.jar" > gradle/wrapper/gradle-wrapper.jar
# (путь внутри ZIP может отличаться — проверить: unzip -l /tmp/g.zip | grep wrapper)

# Вариант C — использовать gradlew от другого Android-проекта
```

---

## ЗАДАЧА 9 — app.js: добавить вызовы нативных мостов

**Файл:** `app/src/main/assets/public/app.js`

JS сейчас использует только browser API. Нужно добавить ветки для нативных мостов.

### 9.1 — initLock: использовать Android Keystore для PIN

Найти функцию `initLock()`. В самом начале добавить:
```javascript
// Загрузить PIN из Android Keystore если доступен
if (window.AndroidKeystore) {
    const stored = AndroidKeystore.getSecret('nc_pin');
    if (stored) localStorage.setItem('nc_pin', stored);
}
```

В функции `changePIN()` после `localStorage.setItem(PIN_KEY, newP)` добавить:
```javascript
if (window.AndroidKeystore) AndroidKeystore.storeSecret('nc_pin', newP);
```

### 9.2 — triggerPanic: добавить нативный wipe

В функции `triggerPanic()`, в шаге с id `pi5` (wiping localStorage), после `localStorage.clear()` добавить:
```javascript
if (window.AndroidKeystore) AndroidKeystore.clearAll();
```

### 9.3 — copyOnion: использовать нативный буфер обмена

Заменить тело функции `copyOnion()`:
```javascript
function copyOnion() {
    const addr = State.onionAddress || document.getElementById('settingsOnion').textContent;
    if (window.AndroidClipboard) {
        AndroidClipboard.copy(addr);
        showToast('✓ .onion адрес скопирован');
        return;
    }
    navigator.clipboard?.writeText(addr)
        .then(() => showToast('✓ Скопировано'))
        .catch(() => showToast('Адрес: ' + addr.slice(0, 30) + '…'));
}
```

### 9.4 — receiveMessage: системные уведомления

В функции `receiveMessage()`, после строки `if (State.currentTab === 'Chats')`, добавить:
```javascript
if (window.AndroidNotifications && (document.hidden || State.locked)) {
    AndroidNotifications.show(
        chat.name,
        text.slice(0, 80),
        'msg_' + chat.id
    );
}
```

### 9.5 — openCallScreen: использовать нативный WebRTC

В начале функции `openCallScreen()`, перед `try {`, добавить:
```javascript
// Приоритет: нативный WebRTC через AndroidWebRTC bridge
if (window.AndroidWebRTC) {
    AndroidWebRTC.initFactory();
    // Колбэки из нативного кода:
    window.onRtcOffer = (sdp) => {
        // Отправить SDP offer через SMP очередь контакта
        if (State.currentChat?.queueId) {
            sendSMPMessage(State.currentChat.queueId,
                JSON.stringify({ type: 'offer', sdp }), null);
        }
        sysLog('ok', 'WebRTC SDP offer → SMP');
    };
    window.onRtcAnswer = (sdp) => {
        AndroidWebRTC.setRemoteSdp(JSON.stringify({ type: 'answer', sdp }));
    };
    window.onRtcIceCandidate = (json) => {
        if (State.currentChat?.queueId) {
            sendSMPMessage(State.currentChat.queueId,
                JSON.stringify({ type: 'ice', candidate: json }), null);
        }
    };
    window.onRtcConnected = () => {
        document.getElementById('callStatus').textContent = '00:00';
        startCallTimer();
        sysLog('ok', 'WebRTC connected (native)');
    };
    window.onRtcDisconnected = () => endCall();
    window.onRtcStats = (stats) => {
        const el = document.getElementById('callQuality');
        if (el) el.textContent =
            `RTT: ${stats.rtt >= 0 ? stats.rtt : '—'}ms · ${stats.codec} · Loss: ${stats.packetsLost}pkts`;
    };
    AndroidWebRTC.initCall();
    return; // нативный WebRTC запущен, дальше не идти
}
// fallback: browser WebRTC (текущий код остаётся ниже)
```

В функции `endCall()` перед `clearInterval(State.callTimer)` добавить:
```javascript
if (window.AndroidWebRTC) { AndroidWebRTC.endCall(); }
```

В функции `toggleMute()` добавить:
```javascript
if (window.AndroidWebRTC) { AndroidWebRTC.setMuted(State.callMuted); return; }
```

### 9.6 — checkTorIP: читать из нативного Tor bridge

Заменить начало функции `checkTorIP()`:
```javascript
async function checkTorIP() {
    if (window.AndroidTor) {
        const running = AndroidTor.isRunning();
        const port    = AndroidTor.getSocksPort();
        const onion   = AndroidTor.getOnionAddress();
        document.getElementById('torExitIP').textContent =
            running ? `SOCKS5 :${port} · active` : 'Tor not running';
        if (onion && onion.length > 5) {
            State.onionAddress =
                `smp://${(State.keys?.dh.publicKey || '').slice(0,52)}@${onion}:${State.config.smp.port || 5223}`;
            document.getElementById('dashOnion').textContent    = State.onionAddress;
            document.getElementById('settingsOnion').textContent = State.onionAddress;
        }
        sysLog(running ? 'ok' : 'warn', `Tor native: port=${port} onion=${onion || 'pending'}`);
        return;
    }
    // fallback: текущая fetch-реализация остаётся ниже
```

### 9.7 — refreshTorCircuit: нативный NEWNYM

В начале функции `refreshTorCircuit()`:
```javascript
async function refreshTorCircuit() {
    if (window.AndroidTor) {
        const ok = AndroidTor.newCircuit();
        showToast(ok ? '✓ Новый Tor-circuit запрошен' : 'Ошибка: проверьте Tor');
        sysLog(ok ? 'ok' : 'err', 'Tor NEWNYM: ' + (ok ? 'sent' : 'failed'));
        return;
    }
    // fallback ниже
```

### 9.8 — handleDeepLink: добавить глобальную функцию

В конце файла app.js, после `window.addEventListener('DOMContentLoaded', boot)`, добавить:
```javascript
// Вызывается нативным кодом при simplex:// deep link
window.handleDeepLink = function(uri) {
    sysLog('info', 'Deep link: ' + uri);
    if (uri.startsWith('simplex:')) {
        document.getElementById('newContactAddr').value = uri;
        openModal('newChatModal');
        goTab('Chats');
    }
};

// Вызывается AndroidWebRTC при входящем SMP-сообщении с SDP
window.handleIncomingSdp = function(json) {
    try {
        const msg = JSON.parse(json);
        if (msg.type === 'offer' && window.AndroidWebRTC) {
            AndroidWebRTC.setRemoteSdp(json);
        } else if (msg.type === 'ice' && window.AndroidWebRTC) {
            AndroidWebRTC.addIceCandidate(JSON.stringify(msg.candidate));
        }
    } catch(e) { sysLog('err', 'handleIncomingSdp: ' + e.message); }
};
```

---

## ЗАДАЧА 10 — SmpServerService: вызывать handleIncomingSdp при WebRTC сообщениях

**Файл:** `app/src/main/java/com/nexuschat/app/services/SmpServerService.kt`

В конструктор добавить параметр evalJs (аналогично WebRtcBridge).

В `MainActivity.kt` при старте SmpServerService добавить listener:
```kotlin
smpService?.addMessageListener { json ->
    runOnUiThread {
        // Переслать все входящие SMP-фреймы в JS
        webView.evaluateJavascript("window.onSmpMessage && window.onSmpMessage('${
            json.replace("'", "\\'").replace("\n", "\\n")
        }')", null)
    }
}
```

В app.js добавить обработчик:
```javascript
window.onSmpMessage = function(json) {
    try {
        const frame = JSON.parse(json);
        if (frame.cmd === 'MSG') {
            receiveMessage(frame.queueId, frame.body || {});
        }
        // Входящий WebRTC сигнал
        if (frame.cmd === 'MSG' && frame.body?.encrypted) {
            const text = frame.body.text || '';
            if (text.startsWith('{') && (text.includes('"offer"') || text.includes('"ice"'))) {
                window.handleIncomingSdp && window.handleIncomingSdp(text);
            }
        }
    } catch(e) {}
};
```

---

## ЗАДАЧА 11 — Keystore для SmpServerService в MainActivity

**Файл:** `app/src/main/java/com/nexuschat/app/MainActivity.kt`

В `startSmpService()` передать host/port из сохранённого конфига:
```kotlin
private fun startSmpService() {
    val prefs = NexusChatApp.securePrefs
    val host  = prefs.getString("smp_host", "") ?: ""
    val port  = prefs.getString("smp_port", "5223")?.toIntOrNull() ?: 5223
    val intent = Intent(this, SmpServerService::class.java).apply {
        putExtra("host", host)
        putExtra("port", port)
    }
    startForegroundService(intent)
    bindService(intent, smpConnection, BIND_AUTO_CREATE)
}
```

В app.js в `saveSmpConfig()` после `saveConfig()` добавить:
```javascript
if (window.AndroidKeystore) {
    AndroidKeystore.storeSecret('smp_host', State.config.smp.host);
    AndroidKeystore.storeSecret('smp_port', String(State.config.smp.port));
}
```

---

## ЗАДАЧА 12 — Убрать лишние файлы перед сборкой

Удалить:
- `gradle/wrapper/GET_GRADLE_WRAPPER.txt` — мусор, вызовет warning
- `setup.sh` — не нужен opencode

```bash
rm gradle/wrapper/GET_GRADLE_WRAPPER.txt
rm setup.sh 2>/dev/null || true
```

---

## ЗАДАЧА 13 — Keystore для релиза (создать один раз)

```bash
keytool -genkey -v \
  -keystore nexuschat.keystore \
  -alias nexuschat \
  -keyalg RSA -keysize 4096 -validity 10000 \
  -storepass ПРИДУМАТЬ_ПАРОЛЬ \
  -keypass ПРИДУМАТЬ_ПАРОЛЬ \
  -dname "CN=NexusChat, OU=Dev, O=NexusChat, L=Unknown, ST=Unknown, C=US"
```

---

## КРАТКАЯ КАРТА ФАЙЛОВ

```
nexuschat-android/
├── app/build.gradle              ← все зависимости (не трогать)
├── app/src/main/
│   ├── AndroidManifest.xml       ← ЗАДАЧА 3: добавить <queries>
│   ├── assets/public/
│   │   ├── index.html            ← UI — не трогать
│   │   └── app.js                ← ЗАДАЧИ 9.1–9.8: добавить bridge-вызовы
│   ├── java/com/nexuschat/app/
│   │   ├── MainActivity.kt       ← ЗАДАЧИ 1, 10, 11: webView, listeners, SMP
│   │   ├── bridges/
│   │   │   ├── Bridges.kt        ← ЗАДАЧИ 4, 5: TailscaleBridge.evalJs, BuildConfig
│   │   │   └── WebRtcBridge.kt   ← ЗАДАЧА 2: callJs → evalJs лямбда
│   │   ├── crypto/               ← готово, не трогать
│   │   └── services/
│   │       ├── TorService.kt     ← ЗАДАЧА 3: реальный запуск Tor
│   │       └── SmpServerService.kt ← ЗАДАЧА 10: forward SMP frames to JS
│   └── res/
│       ├── values/themes.xml     ← ЗАДАЧА 6: xmlns:tools
│       └── xml/network_security_config.xml ← ЗАДАЧА 7: убрать placeholder pins
└── gradle/wrapper/
    └── gradle-wrapper.jar        ← ЗАДАЧА 8: получить настоящий JAR
```

---

## ПОРЯДОК ВЫПОЛНЕНИЯ

1. Задача 8 — получить gradle-wrapper.jar (иначе ничего не соберётся)
2. Задача 12 — удалить мусор
3. Задача 6 — themes.xml (иначе compile error)
4. Задача 7 — убрать placeholder pins (иначе compile error)
5. Задача 5 — BuildConfig (иначе compile error)
6. Задача 1 — удалить дубль webView (иначе compile error)
7. Задачи 2, 4 — evalJs лямбды (compile)
8. Задача 3 — TorService реальный запуск
9. Задачи 9, 10, 11 — bridge-вызовы в app.js и MainActivity
10. Задача 13 — keystore
11. `./gradlew assembleDebug` → тест
12. `./gradlew assembleRelease` → финальный APK
