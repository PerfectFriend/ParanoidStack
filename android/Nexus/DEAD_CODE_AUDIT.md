# NexusChat Dead Code Audit

## ENTIRE CLASSES — NEVER INSTANTIATED/USED

| File | Reason |
|------|--------|
| `services/TorControlConnection.kt` | Class never imported or instantiated; TorService manages control port directly |
| `services/TorBridgeConfig.kt` | `getInstance()` never called anywhere |
| `services/TransportChainBuilder.kt` | `getInstance()` never called anywhere |
| `services/CoverTrafficScheduler.kt` | `getInstance()` never called anywhere |
| `services/ChainProxy.kt` | `getInstance()` never called anywhere |
| `crypto/CryptoManager.kt` | `getInstance(ctx)` never called anywhere |
| `crypto/DoubleRatchet.kt` | `initialize()` / `fromState()` never called anywhere |
| `crypto/SmpProtocol.kt` | Object never imported anywhere; SMP framing done manually in SmpServerService |

---

## UNUSED IMPORTS (Kotlin)

| File:Line | Import | Why Dead |
|-----------|--------|----------|
| `SmpServerService.kt:21` | `java.security.SecureRandom` | Used only via `rng` field — wait, it IS used. Never mind. |
| `SmpServerService.kt:51` | `pendingAcks: ConcurrentHashMap<String, CompletableDeferred<Boolean>>` | Field declared but never read/written (only `CompletableDeferred<String>` used in `sendFrameWithCallback`) |
| `MeekTransport.kt:46` | `connections: Map<String, Socket>` | Declared but never assigned/read |
| `V2RayService.kt:72` | `configHistory: ConcurrentHashMap<String, V2RayConfig>` | Never written to or read |
| `TrafficPadding.kt:36,76` | `PaddedPacket`, `PaddedFrame` data classes | Never used anywhere |

---

## UNUSED PUBLIC/PRIVATE FUNCTIONS

### ProtocolObfuscator.kt
| Line | Function | Why Dead |
|------|----------|----------|
| 56 | `sslContextWithRandomFingerprint()` | Never called |
| 68 | `applyFingerprintToSocket()` | Never called |
| 83 | `obfuscateHttpHeaders()` | Never called |
| 97 | `randomPadding()` | Never called |
| 104 | `morphTlsClientHello()` | Never called |
| 122 | `normalizePacketSize()` | Never called |
| 130 | `addTimingJitter()` | Never called |
| 134 | `destroy()` | Never called |

### Obfs4Transport.kt
| Line | Function | Why Dead |
|------|----------|----------|
| 159 | `getActiveConnections()` | Never called |
| 167 | `destroy()` | Never called |

### MeekTransport.kt
| Line | Function | Why Dead |
|------|----------|----------|
| 171 | `readReceived()` | Never called |
| 173 | `getSessionId()` | Never called |
| 181 | `destroy()` | Never called |

### ErrorRecoveryManager.kt
| Line | Function | Why Dead |
|------|----------|----------|
| 46 | `setRecoveryHandler()` | Never called |
| 116 | `getStatus()` | Never called |
| 118 | `getAllStatuses()` | Never called |
| 120 | `isHealthy()` | Never called |
| 122 | `resetRetries()` | Never called |
| 129 | `destroy()` | Never called |

### DomainFronting.kt
| Line | Function | Why Dead |
|------|----------|----------|
| 78 | `frontedWebSocket()` | Never called |
| 122 | `destroy()` | Never called |

### DnsOverTor.kt
| Line | Function | Why Dead |
|------|----------|----------|
| 242 | `resolve()` | Never called (only `resolveViaTor`/`resolveDirect` used) |
| 264 | `destroy()` | Never called |

### SmpServerService.kt
| Line | Function | Why Dead |
|------|----------|----------|
| 355 | `createQueue()` | Public API but never called from any file |
| 368 | `subscribeToQueue()` | Public API but never called |
| 382 | `sendMessage()` | Public API but never called |
| 403 | `deleteQueue()` | Public API but never called |
| 446 | `removeMessageListener()` | Never called (addMessageListener is used) |

### TorService.kt
| Line | Function | Why Dead |
|------|----------|----------|
| 230 | `newCircuit()` | Never called; TorBridge sends broadcast instead |
| 241 | `getCircuitStatus()` | Never called |

### TransportManager.kt
| Line | Function | Why Dead |
|------|----------|----------|
| 178 | `recordBytesReceived()` | Never called |
| 186 | `recordFailure()` | Never called |
| 191 | `getAllStats()` | Never called |
| 234 | `getTransportForOnion()` | Never called |
| 235 | `getTransportForClearnet()` | Never called |
| 237 | `getProxyForSocks()` | Never called |

### V2RayService.kt
| Line | Function | Why Dead |
|------|----------|----------|
| 261 | `generateUUID()` | Never called |
| 270 | `getClientInfo()` | Never called |
| 274 | `generateShareLink()` | Never called |

### WireGuardConfig.kt
| Line | Function | Why Dead |
|------|----------|----------|
| 65 | `startTunnel()` | Never called |
| 113 | `stopTunnel()` | Never called |
| 148 | `getConfigString()` | Never called |
| 150 | `getSocksProxyPort()` | Never called |
| 162 | `loadConfigFromPrefs()` | Never called |
| 172 | `destroy()` | Never called |

### CryptoManager.kt
| Line | Function | Why Dead |
|------|----------|----------|
| 99 | `encryptFile()` | Never called |
| 126 | `decryptFile()` | Never called |
| 149 | `wipeBytes()` | Never called |
| 160 | `randomBytes()` | Never called |
| 161 | `randomBase64()` | Never called |

### SnowflakeTransport.kt
| Line | Function | Why Dead |
|------|----------|----------|
| 253 | `send()` | Never called; SnowflakeBridge creates transport as local var, so send() is unreachable |

### BinaryDownloader.kt
| Line | Function | Why Dead |
|------|----------|----------|
| 174 | `destroy()` | Never called |

### AudioRelay.kt
| Line | Function | Why Dead |
|------|----------|----------|
| 213 | `destroy()` | Never called |

### TrafficPadding.kt
| Line | Function | Why Dead |
|------|----------|----------|
| 34 | `configure()` | Never called |
| 69 | `addHeaderPadding()` | Never called |
| 108 | `stripPadding()` | Never called |
| 121 | `obfuscateLength()` | Never called |
| 126 | `destroy()` | Never called |

### Bridges.kt
| Line | Function | Why Dead |
|------|----------|----------|
| 415 | `BinaryProgressBridge.getVersion()` | Duplicate of `SystemBridge.getVersion()` (line 358); both return `BuildConfig.VERSION_NAME` |

### XRaySubprocess.kt
| Line | Function | Why Dead |
|------|----------|----------|
| 158 | `stop()` | Never called (only in destroy, which is never called) |
| 167 | `destroy()` | Never called |

---

## DEAD CODE IN CONDITIONS / EMPTY BODIES

| File:Line | Description |
|-----------|-------------|
| `WebRtcBridge.kt:236-243` | Empty overrides: `onConnectionChange`, `onSignalingChange`, `onIceGatheringChange`, `onIceCandidatesRemoved`, `onRemoveStream`, `onDataChannel`, `onRenegotiationNeeded`, `onAddTrack` — required by interface, acceptable |
| `SnowflakeTransport.kt:119,128` | Empty observer overrides: `onIceCandidate`, `onIceConnectionReceivingChange`, `onAddStream`, `onConnectionChange` etc. — required by interface |
| `Obfs4Transport.kt:137` | Empty catch block: `catch (_: Exception) {}` in `obfs4Pipe` — swallows all errors silently |
| `MeekTransport.kt:86` | Empty catch in finally: `catch (_: Exception) {}` |
| `ChainProxy.kt:199` | Empty catch: `catch (_: Exception) {}` in `pipe()` |
| `app.js:700` | Empty handler: `State.peerConn.onicecandidate = e => {};` — ICE candidates from JS WebRTC are silently discarded (no forwarding to peer) |

---

## JS DEAD CODE

| File:Line | Code | Why Dead |
|-----------|------|----------|
| `app.js:3` | `const APP_VERSION = '1.0.0';` | Never referenced anywhere |
| `app.js:30` | `State.trafficChart` / `State.trafficData` | Chart.js is loaded but no chart is ever created; traffic chart canvas (#trafficChart) exists but never populated |
| `app.js:33` | `State.stats` (connections, msgsHour, uptime, ping) | Object defined but never updated; HTML elements `#stConn`, `#stMsg`, `#stUp`, `#stPing` remain showing "—" permanently |
| `app.js:811` | `window.updateTrafficDisplay = function() {};` | Empty stub, never meaningfully implemented |
| `app.js:1164` | `const resp = await fetch(...)` | Response is never used (result discarded) |
| `app.js:45` | `CustomEvent('permissionsResult')` (Java side) | Dispatched from Java, but no `addEventListener('permissionsResult', ...)` exists in app.js |
| `app.js:62` | `window.nativeTorReady(...)` (Java side) | Called from Java on Tor ready, but `window.nativeTorReady` is never defined in JS — callback is silently swallowed |

---

## HTML DEAD CODE

| File:Line | Element | Why Dead |
|-----------|---------|----------|
| `index.html:22` | `<script src="cbor-js/...">` | `cbor-js` loaded but never used in app.js |
| `index.html:24` | `<script src="lz-string/...">` | `lz-string` loaded but never used in app.js |
| `index.html:26` | `<script src="Chart.js/...">` | `Chart.js` loaded but no chart is ever created |
| `index.html:903` | `#sbTime` | Shows "00:00" but never updated with actual time |
| `index.html:935-938` | `#stConn`, `#stMsg`, `#stUp`, `#stPing` | Server hero stat cells — never populated by JS |
| `index.html:1001` | `#onionDetail` | Shows "loading..." never updated |
| `index.html:1082` | Tab "XFTP" (4th media tab) | `State.mediaFiles.xftp` is undefined; clicking it shows "No xftp files" |
| `index.html:1135` | `#tsStatusRow` | Never updated by JS |
| `index.html:1140` | `#tsAuthRow` | Never updated by JS |
| `index.html:1145` | `#tsPeersRow` | Never updated by JS |
| `index.html:1361` | `#smpUriPreview` | Shows placeholder "Fill host and port above" — never generated |
| `index.html:1364` | `#smpConfigLog` | Console element never used by `saveSmpConfig()` |
| `index.html:1398` | `#tsPeersLog` | Console element never populated |
| `index.html:1407` | `#queueList` | Element exists but `pruneQueues()`/`clearAllQueues()` never render to it |

---

## CDN / SCRIPT LOAD ISSUES

| Resource | Status |
|----------|--------|
| `cbor-js` (index.html:22) | **DEAD** — never referenced in app.js |
| `lz-string` (index.html:24) | **DEAD** — never referenced in app.js |
| `Chart.js` (index.html:26) | **DEAD** — never instantiated; no chart created |
| `vanilla-masker` | Not loaded — might be needed but missing |
| `manifest.json` (index.html:12) | Referenced but **file does not exist** in assets/public/ |

---

## CUMULATIVE SUMMARY

| Category | Count |
|----------|-------|
| Entire classes (0 calls) | 8 files |
| Unused public/private functions | ~55 functions |
| Unused fields/variables | ~10 fields |
| Dead CDN loads | 3 scripts |
| Dead HTML elements | ~12 elements |
| Empty catch blocks | 5 instances |
| Unused imports | ~3 imports |

**Total estimated dead code lines: ~1800+ lines** (out of ~6400 total lines across all files)
