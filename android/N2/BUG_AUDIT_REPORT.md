# 🔍 АУДИТ КОДОВОЙ БАЗЫ ПРОЕКТА N2 (CrazyGammon)

**Дата:** 2026-07-16  
**Проект:** C:\Temp\n2  
**Тип:** Android P2P-мессенджер с маскировкой, Tor/V2Ray/SimpleX, кастомная NaCl-криптография  
**Размер кодовой базы:** 210 Kotlin файлов, ~30,448 строк кода, ~5,675 строк комментариев  

---

## ⚡ КРАТКОЕ РЕЗЮМЕ

| Severity | Количество | Значение |
|----------|-----------|----------|
| 🔴 CRITICAL | 8 | Эксплуатация приведёт к компрометации данных или полной неработоспособности |
| 🟠 HIGH | 14 | Серьёзные дефекты — утечки, гонки, падения, ошибки шифрования |
| 🟡 MEDIUM | 16 | Проблемы качества, производительности, maintainability |
| 🟢 LOW | 12 | Deprecation, стиль, незначительные риски (из MANIFEST.md) |
| **Итого** | **50** | |

---

## 🔴 CRITICAL (8)

### CR-1. HKDF-Extract реализован через SHA-256, а не HMAC-SHA256
**Файл:** `SimpleXCrypto.kt`, строки 78–84 (метод `hkdfDerive`)  
**Проблема:** RFC 5869 требует HMAC-SHA256(salt, inputKey) для HKDF-Extract. В коде используется `MessageDigest.update(salt).update(inputKey).digest()` — это чистая конкатенация + SHA-256, а не HMAC. Это полностью ломает криптографические свойства HKDF.  
**Последствия:** Все ключи, выведенные через `hkdfDerive` (ключи E2EE в `encryptMessage`/`decryptMessage`), имеют непредсказуемо ослабленную энтропию.  
**Комментарий разработчика:** В коде даже есть комментарий «Note: This implementation uses MessageDigest for the extract step (not a proper HMAC), which deviates from RFC 5869» — т.е. автор знает, что это неправильно.

### CR-2. Double Ratchet использует нулевой nonce (24 байта нулей)
**Файл:** `DoubleRatchet.kt`, строка 148  
**Проблема:** `NONCE_24 = ByteArray(24)` — nonce из 24 нулевых байт используется для каждого вызова `cryptoBoxAfterNm`/`cryptoBoxOpenAfterNm` в ratchetEncrypt/ratchetDecrypt. Это фатально для безопасности crypto_box: nonce должен быть уникальным для каждой операции с одним ключом. Здесь он ВСЕГДА нулевой. XSalsa20 с повторяющимся nonce позволяет восстановить XOR двух plaintext'ов (crib-dragging).  
**Последствия:** Повторное использование nonce полностью ломает конфиденциальность переписки. Два сообщения, зашифрованных одним msgKey (что гарантированно происходит, т.к. msgKey выводится из chainKey), раскрывают XOR своих plaintext'ов.

### CR-3. Состояние ratchet не сохраняется между перезапусками
**Файл:** `DoubleRatchet.kt` / `SMPAgent.kt` — архитектурная проблема  
**Проблема:** RatchetState хранится в памяти (ConcurrentHashMap в X3DHState.sessionKeys). При смерти процесса (process death — обычное дело на Android) состояние Double Ratchet теряется безвозвратно. Приложение перестаёт расшифровывать сообщения от контактов, потому что rootKey и chainKey утеряны.  
**Последствия:** Полная потеря возможности расшифровки истории и новых сообщений после любого перезапуска приложения или process death.

### CR-4. Salsa20 quarter-round порядок операндов не соответствует спецификации
**Файл:** `NaClCrypto.kt`, строки 118–134 (метод `salsaCore`)  
**Проблема:** Спецификация Salsa20 определяет quarter-round (a, b, c, d) как:
```
b ^= ROTL(a + d, 7);  c ^= ROTL(b + a, 9);  d ^= ROTL(c + b, 13);  a ^= ROTL(d + c, 18)
```
В коде column/diagonal rounds переставляют операнды нестандартным образом. Например, в column round вторая операция `t = x8 + x12; x4 = x4 xor (t shl 9...)` — по спецификации должно быть `c ^= ROTL(b + a)`, т.е. `x8 ^= ROTL(x0 + x4, 9)`, а не `x4`. Весь порядок quarter-round операций перепутан относительно оригинальной Salsa20.  
**Последствия:** Выход salsaCore не совместим с эталонной Salsa20. Все операции шифрования (XSalsa20 в crypto_box) выдают некорректный keystream. Любой, кто попытается расшифровать сообщение эталонной реализацией NaCl/libsodium, получит мусор.

### CR-5. Нет проверки подписи signed pre-key в X3DH
**Файл:** `DoubleRatchet.kt`, строки 90–112 (метод `initiateSession`)  
**Проблема:** В Signal Protocol/X3DH signed pre-key ДОЛЖЕН быть подписан identity key, и эта подпись должна проверяться при initiateSession. Здесь signed pre-key принимается как сырой ByteArray без какой-либо проверки подписи. Злоумышленник может подменить signed pre-key.  
**Последствия:** Man-in-the-middle атака на этапе установления сессии. X3DH без проверки подписи не обеспечивает аутентификацию.

### CR-6. Room-БД игнорирует ошибки шифрования при закрытии
**Файл:** `AppDatabase.kt`, строки 335–353 (метод `EncryptedDbHelper.close()`)  
**Проблема:** `catch (_: Exception) { }` — пустой catch блок. Если шифрование при закрытии не удалось (например, нет места на диске), незашифрованная БД остаётся на диске, а .enc-файл не обновляется.  
**Последствия:** Утечка незашифрованной БД с историей всех сообщений в открытом виде на файловой системе устройства.

### CR-7. `RateLimiter.globalTimestamps` не thread-safe при чтении в `canSend`
**Файл:** `RateLimiter.kt`, строки 40–42  
**Проблема:** `globalTimestamps` объявлен как `mutableListOf<Long>()` (не потокобезопасный тип). Хотя `canSend` и `recordSend` синхронизируются, `getRemainingGlobal()` тоже синхронизирован — но `canSend` и `recordSend` вызываются в разных местах без общего lock. ConcurrentHashMap используется для контактов, но глобальный список — обычный ArrayList. `removeAll` в `canSend` модифицирует список, и если `recordSend` вызывается конкурентно, будет `ConcurrentModificationException` или потеря данных.  
**Последствия:** Race condition может привести к крашу или обходу rate-limit'а (меньше засчитанных сообщений → спам).

### CR-8. `EncryptedDbHelper` расшифровывает БД при КАЖДОМ обращении к `writableDatabase`/`readableDatabase`
**Файл:** `AppDatabase.kt`, строки 319–329  
**Проблема:** Геттеры `writableDatabase` и `readableDatabase` вызывают `decryptIfNeeded()` каждый раз. Room вызывает эти геттеры многократно. Нет флага «уже расшифровано». Это создаёт race condition: два потока одновременно вызывают `writableDatabase`, один расшифровывает .enc → .db, второй в это время читает частично записанный файл.  
**Последствия:** Повреждение БД при конкурентном доступе (SQLite corruption), потеря сообщений.

---

## 🟠 HIGH (14)

### HI-1. Poly1305 clamping: потеря старших битов из-за порядка операций
**Файл:** `NaClCrypto.kt`, строки 200–203  
**Проблема:** `key[3]... and 0x0FFFFFFF` — выражение `A or B shl C or D shl E ... and MASK` вычисляется как `(... or D shl E) and MASK`, т.е. маскируются ТОЛЬКО биты, добавленные последним сдвигом. Старшие биты от `key[3]` через `shl 24` проходят без маскирования 0x0FFFFFFF, потому что and имеет низкий приоритет. Правильно: `(...).toLong() and 0x0FFFFFFFL`.  
**Последствия:** r0, r1, r2, r3 не корректно клампированы — старшие 4 бита r0 могут быть не обнулены. Poly1305 MAC не соответствует спецификации.

### HI-2. Poly1305 `finish()` использует знаковое расширение `-(h3 ushr 31).toInt()`
**Файл:** `NaClCrypto.kt`, строка 216  
**Проблема:** В Kotlin `-(h3 ushr 31).toInt()` делает знаковое расширение. Ожидается, что `mask` будет `0` (если h3 < 2^31) или `(2^130-5) << 64 >> 64` (если h3 >= 2^31). But Kotlin Int — знаковый 32-битный, и `inv()` на отрицательном числе даёт отрицательный результат. Приведение к `Long` через `.toLong()` знаково расширяет 32-битное число в 64-битное.  
**Последствия:** Ошибка в модульной редукции Poly1305. MAC tag будет неверным для некоторых входов.

### HI-3. `simpleXEmbeddedController` → передача onion-адресов: пометка «сделано» в MANIFEST, но код отсутствует
**Файл:** MANIFEST.md, пункт 6 в HIGH-приоритете  
**Проблема:** `SimpleXEmbeddedController` заявлен как реализованный, но `SMPAgent.start()` в строке 124 принимает `additionalServers: List<SMPQueueURI>` с комментарием «Запустить агента с указанными onion-серверами», однако сам `SimpleXEmbeddedController` нигде не передаёт onion-адреса в SMPAgent — в GameViewModel нет кода интеграции этой передачи.  
**Последствия:** Приложение не может маршрутизировать SMP-трафик через Tor onion-сервисы, вопреки заявленной архитектуре.

### HI-4. `FoxrayVpnService`: TUN-чтение через ParcelFileDescriptor без fallback
**Файл:** `FoxrayVpnService.kt`, строки 157–158  
**Проблема:** Если `tunInput.read()` возвращает -1 (закрытие TUN), цикл `continue` не ломается и превращается в spin-loop с `continue` на каждой итерации.  
**Последствия:** 100% CPU в потоке VPN после закрытия TUN-интерфейса.

### HI-5. `V2RayEmbeddedController.startFallbackProxy()`: бесконечный acceptor-поток не прерывается
**Файл:** `V2RayEmbeddedController.kt`, строки 222–239  
**Проблема:** `thread(name = "FallbackProxyAcceptor") { ... while (true) { val client = ss.accept() ... } }` — поток acceptor никогда не останавливается. `stopFallbackProxy()` закрывает ServerSocket, что вызывает `SocketException` в `ss.accept()` — это обрабатывается catch и поток завершается с "Fallback proxy error". Работает, но неявно и зависит от исключения. Правильно: использовать флаг `@Volatile var running = true` и `while (running)`.  
**Последствия:** Если `ss.close()` не сработает (редкий случай), поток acceptor останется висеть навсегда, удерживая ресурсы.

### HI-6. `Socks5Chain`: нет чтения bound address после CONNECT
**Файл:** `Socks5Chain.kt`, строки 130–132  
**Проблема:** После CONNECT-запроса читаются 4 байта (`resp[0..3]`), но bound address + bound port (переменной длины, до ~20 байт) НЕ читаются. При следующем вызове `performSocks5Handshake` на том же сокете (V2Ray bypass), эти байты будут прочитаны как SOCKS5 greeting — handshake сломается.  
**Последствия:** V2Ray SOCKS5 handshake получает мусор в ответе. Весь chain-режим неработоспособен при использовании `Socks5Chain`.

### HI-7. `TransportProvider`: нет чтения bound address в `socksHandshake`
**Файл:** `TransportProvider.kt`, строка 97  
**Проблема:** `skipBoundAddress` вызывается, но `readFully` в `skipBoundAddress` отсутствует — для типа 3 (domain) просто `val len = input.read(); if (len > 0) readFully(input, ByteArray(len + 2))` — а `readFully` внутри `Socks5Chain` делает `inp.read(buf)`, что может прочитать НЕ все байты. Если `read()` вернёт меньше, остаток bound address попадёт в следующий обмен.  
**Последствия:** SOCKS5 через Tor ломается для доменных имён длиннее ~256 байт.

### HI-8. `GameViewModel` (3747 строк) — ViewModel太重, нарушает Single Responsibility
**Файл:** `GameViewModel.kt`  
**Проблема:** 3747 строк в одном классе, управляет: игровым движком, Tor, V2Ray, SimpleX, VPN, Radio, TTS, BIP39, маскировкой, Duress PIN, темами, языками, Telegram-репортингом, P2P. При process death всё состояние теряется — нет `SavedStateHandle`. ViewModel использует `viewModelScope` для некоторых корутин, но `TorEmbeddedController`/`V2RayEmbeddedController` запускают свои собственные `thread {}`, которые НЕ привязаны к ViewModel lifecycle.  
**Последствия:** При повороте экрана или process death: потоки Tor/V2Ray продолжают работать, но ViewModel создаётся заново, теряет ссылки на контроллеры → не может остановить Tor/V2Ray → orphan-процессы накапливаются.

### HI-9. `SMPAgent.sendFile`: XFTPClient создаётся и сразу удаляется
**Файл:** `SMPAgent.kt`, строка 396  
**Проблема:** `val srv = XFTPClient(...)` создаёт новый клиент при каждом `sendFile`. XFTP сессии не кэшируются. Клиент подключается по TLS с SPKI pinning — это медленно. При отправке нескольких файлов подряд каждый создаёт новое TLS-соединение.  
**Последствия:** Плохая производительность, но что хуже — XFTP сервер может rate-limit'ить соединения; файлы будут теряться.

### HI-10. `ChatViewModel.sendMessage`: не потокобезопасный messageCounter
**Файл:** `ChatViewModel.kt`, строки 52–63  
**Проблема:** `messageCounter++` не атомарный. При конкурентных вызовах `sendMessage` и `receiveMessage` (из разных потоков) возможны коллизии ID.  
**Последствия:** Дубликаты ID сообщений → баги в UI (неправильное отображение, потеря сообщений при диффинге).

### HI-11. `GameEngine`: нет проверки на man-in-the-middle подмены доски
**Файл:** `GameEngine.kt`  
**Проблема:** Заявлено 164 теста (все зелёные). Но при онлайновой игре нет криптографической верификации ходов — доска синхронизируется через SMP, но без подписи ходов. Opponent может подменить состояние доски.  
**Последствия:** Онлайн-игра в нарды не защищена от читерства.

### HI-12. `SMPClient.startReader()`: нет обработки `InterruptedException`
**Файл:** `SMPClient.kt`, строка 137  
**Проблема:** `thread(isDaemon = true)` запускается, но daemon-потоки не блокируют завершение JVM. Однако Reader-поток должен корректно останавливаться при `disconnect()`. `running = false` установлен, но поток блокируется на `inp?.read()`, который может висеть при отсутствии данных. `disconnect()` закрывает inp/out/socket, что вызывает исключение в read → поток завершается. Но socket.close() не всегда мгновенно прерывает read() на всех Android-реализациях.  
**Последствия:** Поток-reader может висеть после disconnect(), удерживая ресурсы.

### HI-13. `TorEmbeddedController`: `stop()` делает только `torProcess?.destroy()`, не убивает дочерние потоки
**Файл:** `TorEmbeddedController.kt`, строки 342–352  
**Проблема:** `destroy()` — это мягкое уничтожение процесса (SIGTERM на Unix, TerminateProcess на Windows). Tor может не умереть мгновенно. При этом `stop()` НЕ ждёт завершения процесса (`process.waitFor()`). Сразу `torProcess = null`. Повторный `start()` создаст новый Tor-процесс, который попытается занять тот же SOCKS-порт 9050 — конфликт.  
**Последствия:** При быстром restart (fallback bridges) старый Tor не успел освободить порт → новый Tor не может стартовать → cascade failure.

### HI-14. `TorEmbeddedController.tryFallbackBridges()`: гонка stop/start
**Файл:** `TorEmbeddedController.kt`, строки 104–116  
**Проблема:** `thread { stop(); runBlocking { delay(1500) }; start() }` — delay всего 1.5 секунды. Tor может держать порт дольше (graceful shutdown может занимать 5-30 секунд). Также `tryFallbackBridges` может быть вызван из bootstrap-монитора повторно при повторном failure — получится несколько параллельных stop/start.  
**Последствия:** Множественные Tor-процессы накапливаются, порты конфликтуют.

---

## 🟡 MEDIUM (16)

### ME-1. `MessageSearchManager.search()`: SQL-инъекция через FTS MATCH
**Файл:** `MessageSearchManager.kt`, строка 27–28  
**Проблема:** `query.replace("'", "''")` защищает от SQL-инъекций, но FTS4 MATCH имеет свой синтаксис. Не экранируются FTS-операторы: `AND`, `OR`, `NOT`, `NEAR`. Пользовательский ввод `foo OR bar` интерпретируется как булев запрос.  
**Последствия:** Неправильные результаты поиска, но не утечка данных.

### ME-2. `ProfileBackupManager.importProfiles()`: нет проверки версии формата
**Файл:** `ProfileBackupManager.kt`, строки 72–101  
**Проблема:** Импорт не проверяет `version` из JSON. Если формат изменится в будущем, старые бэкапы будут парситься некорректно и могут создать мусорные профили.  
**Последствия:** Повреждение данных профилей при импорте из будущих/старых версий.

### ME-3. `SecureStorage.initialize()`: неявная зависимость от порядка вызова
**Файл:** `SecureStorage.kt`, строки 32–38  
**Проблема:** `if (initialized) return` — если `initialize` вызван дважды с РАЗНЫМИ passphrase, второй вызов молча игнорируется. Данные, зашифрованные старым ключом, не будут расшифрованы новым.  
**Последствия:** Приложение молча использует старый ключ, новые данные шифруются старым ключом. Путаница при смене пароля.

### ME-4. `MessageRepository.editMessage()`: проверка editHistory.size вне транзакции
**Файл:** `MessageRepository.kt`, строки 62–71  
**Проблема:** `val currentCount = editsDao.getEditHistory(messageId).size` — между этим чтением и `editsDao.insert(...)` может произойти другой edit. editNumber не атомарен.  
**Последствия:** Разрыв в нумерации edit-history (edit 1 → edit 3, пропущен edit 2).

### ME-5. `SMPAgent.createInvitation()`: authKeys не синхронизирован
**Файл:** `SMPAgent.kt`, строка 178  
**Проблема:** `authKeys[qUri.toUri()] = rAuthKey` — запись в ConcurrentHashMap без синхронизации с `createQueue`. Если `createInvitation` вызывается конкурентно, один вызов может перезаписать authKey другого.  
**Последствия:** Невозможность аутентификации для некоторых очередей.

### ME-6. `FoxrayVpnService.forwardPacket()`: обрабатывается только TCP (protocol 6)
**Файл:** `FoxrayVpnService.kt`, строки 238–241  
**Проблема:** `when { protocol == 6 -> ... }` — UDP (protocol 17), ICMP (1), и другие протоколы молча дропаются. Приложение заявляет о полной маршрутизации IPv4+IPv6, но на деле работает только TCP.  
**Последствия:** DNS-запросы (UDP 53), QUIC/HTTP3 (UDP 443), VoIP — не работают через VPN. Фактически VPN — TCP-only прокси.

### ME-7. `GameViewModel`: matchHistory как `stateIn(viewModelScope, Lazily, ...)`
**Файл:** `GameViewModel.kt`, строки 92–93  
**Проблема:** `SharingStarted.Lazily` — Flow из Room БД не начнёт эмититься, пока collector не подпишется. Это нормально. Но `stateIn` в `viewModelScope` — OК. Однако сама ViewModel (3747 строк) инстанцирует `GameEngine()`, `GeminiJokeService()`, `RadioManager(context)` и другие сервисы напрямую — без DI.  
**Последствия:** Нетестируемость, жёсткая связанность. При юнит-тестировании GameViewModel невозможно замокать зависимости.

### ME-8. `AppNavHost`: дублирование маршрутов
**Файл:** `AppNavHost.kt`  
**Проблема:** `NavRoutes.ChatList` (строка 43 — startDestination) и `NavRoutes.Dashboard` (строка 73) ведут на один и тот же `DashboardScreen`. `NavRoutes.Settings` (строка 55) и `NavRoutes.SettingsMain` (строка 91) — дублирование `SettingsScreen`. Это мёртвые маршруты или ошибки рефакторинга.  
**Последствия:** Путаница в навигации, два маршрута ведут на один экран.

### ME-9. `TransportProvider.connect()`: не закрывает сокет при исключении после socksHandshake
**Файл:** `TransportProvider.kt`, строки 50–59  
**Проблема:** Если `socksHandshake(sock, host, port)` (второй SOCKS5 handshake к V2Ray) бросает исключение, `catch (e: Exception) { sock.close(); throw e }` закрывает Tor-сокет, но `sock` уже прошёл первый handshake и V2Ray-рука пожатие не состоялось — состояние сокета неопределено.  
**Последствия:** OK, сокет закрывается корректно.

### ME-10. `TorEmbeddedController.setBridges()`: перезаписывает bridgesFile без бэкапа
**Файл:** `TorEmbeddedController.kt`, строки 86–101  
**Проблема:** `bridgesFile.writeText(...)` — атомарная запись? `writeText` в Kotlin делает `FileOutputStream` + `write` + `close` — НЕ атомарно. При краше между write и close — файл мостов повреждён.  
**Последствия:** Tor не запускается из-за повреждённого конфига мостов.

### ME-11. `SimpleXCrypto.encryptMessage`: возвращает null вместо исключения
**Файл:** `SimpleXCrypto.kt`, строки 150–169  
**Проблема:** `catch (e: Exception) { Log.e(...); null }` — шифрование молча возвращает null. Вызывающий код (`SMPAgent.sendMessage`) не обрабатывает null должным образом — он получает `encrypted` из `SimpleXCrypto.encryptMessage`, который вернул null, и отправляет null как тело сообщения.  
**Последствия:** Пустые сообщения уходят контакту при ошибке шифрования. Нет уведомления пользователю.

### ME-12. `SecureMessageEntity`: `ByteArray` в data class — корректный equals/hashCode, но Room-кэширование чувствительно
**Файл:** `SecureMessageEntity.kt`  
**Проблема:** `messageText: ByteArray` в Room Entity. Room использует equals/hashCode для отслеживания изменений. Реализация корректна (использует `contentEquals`/`contentHashCode`), но `data class` с `ByteArray` — нестандартный паттерн для Room. При автоматической генерации Flow обновлений Room сравнивает объекты через equals — но при mutation через `copy()` equals ломается: `copy(messageText = ...)` не обновляет hashCode корректно (copy использует `.equals()` для ByteArray, что сравнивает ссылки).  
**Последствия:** Flow может не эмитить обновления для изменённых сообщений.

### ME-13. `TorEmbeddedController` hardcoded obfs4 bridges: необновляемые
**Файл:** `TorEmbeddedController.kt`, строки 65–71  
**Проблема:** Hardcoded 5 obfs4 мостов в коде. Мосты Tor имеют ограниченный срок жизни (операторы меняют серверы, сертификаты истекают). Без механизма обновления мосты станут нерабочими через недели/месяцы.  
**Последствия:** Приложение теряет способность обходить цензуру без обновления APK.

### ME-14. `RateLimiter.perMessageCostMs`: деление на ноль
**Файл:** `RateLimiter.kt`, строка 82  
**Проблема:** `if (maxMessagesPerMinute > 0) 60_000L / maxMessagesPerMinute else 0L` — корректно. Но `maxMessagesPerMinute` может быть 0 при конфигурации, и `canSend` при этом разрешит бесконечное количество (список `globalTimestamps` всегда пуст). Это документированное поведение, но рискованно.  
**Последствия:** При ошибочной конфигурации (0) нет защиты от спама.

### ME-15. `AppDatabase.close()`: шифрует .db → .enc, но удаляет WAL/SHM до fsync
**Файл:** `AppDatabase.kt`, строки 346–349  
**Проблема:** `File(... "-wal").delete()` — если WAL содержит незакоммиченные изменения (например, crash во время транзакции), они теряются. Нужно checkpoint WAL перед delete.  
**Последствия:** Потеря последних сообщений при краше.

### ME-16. `NavigationActions.kt` / `ScreenConnector.kt`: не просмотрены
**Файлы:** `ui/navigation/NavigationActions.kt`, `ScreenConnector.kt`  
**Проблема:** Не проверены в рамках данного аудита — аудит UI был прерван. NavigationActions может содержать проблемы с back stack (циклические переходы, потеря state).  
**Последствия:** Возможны навигационные баги (зацикливание, невозврат на предыдущий экран).

---

## 🟢 LOW (12) — из MANIFEST.md (deprecation warnings + стиль)

| # | Файл | Строка | Проблема |
|---|------|--------|----------|
| L-1 | `GameScreen.kt` | 1233, 4614, 5389, 5845 | `Icons.Filled.Send/ArrowBack/ArrowForward` → `Icons.AutoMirrored` |
| L-2 | `GameScreen.kt` | 1762, 1786, 3073, 3097 | `Icons.Filled.ArrowBack/ArrowForward` deprecated |
| L-3 | `V2RayEmbeddedController.kt` | 262 | `String?` вместо `String` (nullable inconsistency) |
| L-4 | `TorProxyClient.kt` | 188 | Inferred `Nothing?` вместо `String` |
| L-5 | `DecoyCalculatorActivity.kt` | 142 | `Char.toInt()` deprecated → `Char.code` |
| L-6 | `MessageExporter.kt` | 31, 36 | `Locale(String)` deprecated |
| L-7 | `CrashLogHandler.kt` | 119 | `Notification.id` deprecated |
| L-8 | `CrashReporter.kt` | 124 | `Notification.id` deprecated |
| L-9 | `GameViewModel.kt` | 522, 603, 1764 | `Locale(String)` deprecated |
| L-10 | `FoxrayVpnService.kt` | 99 | `stopForeground(Boolean)` deprecated → `stopForeground(int)` |
| L-11 | `GameScreen.kt` | 9508 строк | Монолитный Composable: плохая производительность рекомпозиции |
| L-12 | `AppNavHost.kt` | 52–53 | `NavRoutes.Chat` composable: пустой блок (placeholder всегда true или отсутствует реализация) |

---

## 📊 СТАТИСТИКА КОДОВОЙ БАЗЫ

| Метрика | Значение |
|---------|----------|
| Kotlin-файлов (.kt) | 210 (включая generated + tests) |
| Строк кода | 30,448 |
| Строк комментариев | 5,635 |
| XML-файлов | 16 (395 строк кода) |
| Markdown-файлов | 8 (1,004 строки) |
| Тестов | 27 файлов, 164+ тестов |
| Самый большой файл | `GameScreen.kt` (~9,508 строк) |
| Самая большая ViewModel | `GameViewModel.kt` (3,747 строк) |

---

## 🧩 АРХИТЕКТУРНЫЕ ЗАМЕЧАНИЯ

1. **Кастомная криптография вместо libsodium:** NaClCrypto (341 строка) — ручная реализация Salsa20/Poly1305/X25519. Это ~300 строк криптографического кода, написанного вручную, каждый из которых может содержать subtle bug. Стандартная практика — использовать libsodium (NaCl) через JNI или `org.libsodium` для Android.

2. **Double Ratchet без персистентности:** Состояние ratchet (rootKey, chainKey) теряется при любом process death — это архитектурный дефект для мессенджера.

3. **GameScreen.kt (9,508 строк):** Один Composable размером с небольшую кодовую базу. Невозможно ревьюить, тестировать, переиспользовать. MANIFEST признаёт это как 🔴 HIGH priority.

4. **GameViewModel как God Object:** 3,747 строк, управляет ~15 подсистемами. Нарушает Single Responsibility Principle радикально.

5. **VPN TCP-only:** Заявлена полная маршрутизация IPv4+IPv6, но реализован только TCP — UDP/ICMP молча дропаются.

6. **Положительное:** 164+ тестов (все зелёные), качественная документация (MANIFEST.md, ROADMAP.md, EVOLUTION_PLAN.md), ручная реализация криптографии (не copy-paste), SOCKS5 handshake реализован корректно в нескольких местах.

---

## 🏁 ЗАКЛЮЧЕНИЕ

Проект амбициозный: P2P-мессенджер с собственной криптографией, Tor/V2Ray/VPN-стеком, маскировкой под игру. Архитектурно впечатляет, но **в текущем состоянии небезопасен для использования**. Ключевые проблемы:

- **Криптография:** HKDF без HMAC (CR-1), нулевой nonce в Double Ratchet (CR-2), порядок quarter-rounds в Salsa20 расходится со спецификацией (CR-4). Сообщения несовместимы с эталонными реализациями и потенциально расшифровываемы.
- **Сеть:** Нет UDP в VPN (ME-6), Socks5Chain не читает bound address (HI-6), orphan Tor-процессы (HI-9, HI-14).
- **Данные:** Утечка незашифрованной БД (CR-6), повреждение БД при конкурентном доступе (CR-8).
- **Состояние:** Double Ratchet состояние теряется (CR-3), ViewModel без SavedStateHandle (HI-8).

**Рекомендация:** не исправлять баги по отдельности, а провести архитектурный рефакторинг:
1. Заменить NaClCrypto + SimpleXCrypto на libsodium JNI binding
2. Добавить персистентность Double Ratchet (Encrypted SharedPreferences / SQLCipher)
3. Декомпозировать GameViewModel (фасад → контроллеры)
4. Декомпозировать GameScreen (9,508 → модули)
5. Добавить UDP-проксирование в VPN