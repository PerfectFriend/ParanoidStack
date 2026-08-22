# ParanoidStack Evolution Cycles Log

**Started:** 2026-08-22
**Flash Repo:** /run/media/thomas/F2DB-14E5/backup/projects/ParanoidStack
**Telegram Bot:** NodeBot (chat_id: 143293811) - Token: 8694045845:AAFVEsREi40dRQnQ99QDBKqfR6-5dPlOvrI (WORKING ✅)
**Methodology:** Graph Engineering (nodes, edges, pulse, fitness, mutations, extinctions) — from Vault/Учебник
**Reference Projects:** ParanoidX (FLAGSHIP, production), SuperGuard (50-cycle autonomous evolution)

---

## Cycle 0: Baseline (2026-08-22)
**Commit:** a1a1654
**Status:** ✅ COMPLETE

**Completed:**
- px-transport (Go) - VPN1→VPN2→Tor bridge operational
- transport-sdk (Android) - 96 tests passing, AAR built
- N2 (Android app) - MatrixKeyboard Compose UI
- Nexus (Android app) - Companion app
- MatrixKeyboard - Sealed interface keyboard implementation
- Flash repo initialized with full project state

---

## Cycle 1: MatrixKeyboard IME (2026-08-22)
**Commit:** 7e5444b
**Status:** ✅ COMPLETE

**Completed:**
- Created `keyboard-ime/` module with Gradle setup
- Implemented `ParanoidMatrixIME` - `InputMethodService` with Compose UI
- Features: RU/EN languages, symbols mode, shift/caps, haptic feedback
- Settings activity with preferences (haptic, sound, no-logging, incognito)
- AndroidManifest with IME service declaration
- **Build:** `./gradlew :keyboard:assembleRelease` → **BUILD SUCCESSFUL**
- **AAR Artifact:** `keyboard/build/outputs/aar/keyboard-release.aar` (41KB)
- Compatible with Android API 24+

---

## Cycle 2: Evolution Planning & Cleanup (2026-08-22)
**Status:** ✅ COMPLETE
- Created flash Git repo for version control
- Cleaned legacy Island project files
- Defined 10-cycle evolution roadmap
- Established Telegram reporting via NodeBot

---

## Cycle 3: ParanoidGuard (2026-08-22)
**Commit:** 10cdc31
**Status:** ✅ COMPLETE

**Completed:**
- Created `guard/` module with Gradle setup
- Implemented `ParanoidGuardService` - Foreground Service with LifecycleObserver
- Network monitoring: ConnectivityManager.NetworkCallback, VPN/proxy detection, captive portal alerts
- Process monitoring: /proc scanning, suspicious binary detection (su, frida, magisk, gdb, nmap, etc.)
- Filesystem monitoring: SHA-256 integrity of critical directories (/system, /data, /etc, /vendor, /data/local/tmp)
- Alert system: 4 severity levels (LOW/MEDIUM/HIGH/CRITICAL), notifications + broadcast for MatrixKeyboard LED
- BootReceiver for auto-start on boot
- **Build:** `./gradlew :guard:assembleRelease` → **BUILD SUCCESSFUL**
- **AAR Artifact:** `guard/build/outputs/aar/guard-release.aar` (33KB)
- Min SDK 24, Foreground Service (dataSync), BootReceiver

---

## Cycle 4: ParanoidStore (2026-08-22) 🔄 IN PROGRESS
**Target:** Encrypted Keystore & Secure Notes
**Insights from Vault:**
- **StrongBox/TEE** hardware-backed keystore (Textbook §4: AI/ML on AMD 780M → hardware isolation patterns)
- **BiometricPrompt** API with CRYPTO_OBJECT for crypto operations bound to biometric auth
- **AES-256-GCM** with Android Keystore key generation (non-extractable)
- **Auto-lock** on screen off / timeout / biometric failure
- **MatrixKeyboard secure input** — dedicated IME subtype for password/seed entry (no suggestions, no logging, incognito forced)
- **Graph Engineering integration** — Store as node in evolution graph with fitness criteria

**Planned Implementation:**
1. `ParanoidStore` class — Keystore wrapper (AES-256-GCM, StrongBox preferred)
2. `SecureNotes` — Encrypted notes with auto-lock, biometric unlock
3. `BiometricAuth` — BiometricPrompt with CryptoObject, fallback to device credential
4. `SecureInputSubtype` — MatrixKeyboard IME subtype for sensitive input
5. `StoreSettings` — Auto-lock timeout, biometric requirement, StrongBox enforcement
6. **Fitness criteria:** "AES-256-GCM encrypt/decrypt works + BiometricPrompt unlocks + StrongBox detected + Auto-lock triggers on screen off"

---

## Cycle 5: ParanoidIsland (Offline Mesh Comms)
**Target:** BLE/WiFi-Direct mesh, delay-tolerant networking
**Insights from Vault:** ParanoidX SMP/XFTP/Coturn infrastructure → apply to mobile mesh
**Fitness criteria:** "BLE peer discovery + message relay + store-and-forward + Tor bridge optional"

---

## Cycle 6: px-transport v2 (QUIC + Post-Quantum)
**Target:** QUIC transport, Kyber KEM, multi-path routing
**Insights from Vault:** ParanoidX V2Ray/Tor/SimpleX bridge → mobile transport SDK
**Fitness criteria:** "QUIC handshake <100ms + Kyber768 key exchange + multi-path failover <500ms"

---

## Cycle 7: MatrixKeyboard v2 (Smart Input)
**Target:** Swipe, on-device ML predictions, themes, per-app profiles
**Insights from Vault:** Textbook §4 (DirectML/ROCm optimization) → on-device inference patterns
**Fitness criteria:** "Swipe trail rendering 60fps + prediction accuracy >80% + theme engine + per-app layout"

---

## Cycle 8: ParanoidMesh (P2P over Tor Onion)
**Target:** IPFS (kubo) over Tor, Briar/Cwtch/Jami integration, BitTorrent over VPN→Tor
**Insights from Vault:** ParanoidX Phase 6 (P2P Mesh & Federation) → mobile implementation
**Fitness criteria:** "IPFS daemon starts + Tor hidden service + Briar/Cwtch message send/receive"

---

## Cycle 9: N2 v2 (Secure Messenger Integration)
**Target:** Signal Protocol + ParanoidStack transport (IME + Guard + Store + Mesh)
**Insights from Vault:** ParanoidX 283+ API economy + SimpleX bridge → full messenger stack
**Fitness criteria:** "Signal Protocol double ratchet + ParanoidStack transport layer + secure input + guard monitoring"

---

## Cycle 10: Release Prep (CI/CD + Audit + Distribution)
**Target:** Reproducible builds, F-Droid, security audit, documentation
**Insights from Vault:** SuperGuard 50-cycle checkpoints + Graph Engineering extinction protocol
**Fitness criteria:** "Reproducible build verified + F-Droid PR submitted + 3rd party audit passed + zero CVE"

---

## 🧬 Graph Engineering Integration (from Vault/Учебник)

### Evolution Graph Nodes (ParanoidStack)
```yaml
nodes:
  - id: matrixkeyboard_ime
    type: MODULE
    role: INPUT
    genome: "InputMethodService + Compose UI + RU/EN + haptic"
    state: "ALIVE"
    fitness: "IME selects + types + switches layouts + haptic works"
    
  - id: paranoidguard
    type: MODULE
    role: MONITOR
    genome: "ForegroundService + NetworkCallback + /proc scanner + SHA256 FS"
    state: "ALIVE"
    fitness: "VPN detected + suspicious proc alerted + FS hash mismatch alerted"
    
  - id: paranoidstore
    type: MODULE
    role: STORE
    genome: "AES-256-GCM + StrongBox + BiometricPrompt + SecureNotes + SecureIME"
    state: "BUILDING"
    fitness: "Encrypt/decrypt + biometric unlock + StrongBox + auto-lock on screen off"
    
  - id: paranoidisland
    type: MODULE
    role: MESH
    genome: "BLE/WiFi-Direct + delay-tolerant + Tor bridge"
    state: "PLANNED"
    fitness: "Peer discovery + relay + store-forward"
    
  - id: transport_v2
    type: MODULE
    role: TRANSPORT
    genome: "QUIC + Kyber + multi-path"
    state: "PLANNED"
    fitness: "QUIC handshake + Kyber KEM + failover"
    
  - id: matrixkeyboard_v2
    type: MODULE
    role: INPUT
    genome: "Swipe + ML predictions + themes + per-app"
    state: "PLANNED"
    fitness: "Swipe 60fps + prediction >80% + themes"
    
  - id: paranoidmesh
    type: MODULE
    role: P2P
    genome: "IPFS/Tor + Briar/Cwtch + BitTorrent"
    state: "PLANNED"
    fitness: "IPFS daemon + Tor HS + Briar msg"
    
  - id: n2_messenger
    type: APP
    role: MESSENGER
    genome: "Signal Protocol + ParanoidStack transport"
    state: "PLANNED"
    fitness: "Double ratchet + full stack integration"
    
  - id: release
    type: PIPELINE
    role: DISTRIBUTION
    genome: "CI/CD + reproducible + F-Droid + audit"
    state: "PLANNED"
    fitness: "Reproducible + F-Droid PR + audit clean"
```

### Pulse/Health Check (from SuperGuard pulse.py)
```python
# ParanoidStack Pulse - runs via cron every hour
# Exit 0 = all alive (silence = health)
# Exit 1 = dead nodes detected
def run_paranoidstack_pulse():
    dead = []
    # Check each module AAR exists and loads
    # Check IME service responds
    # Check Guard service runs
    # Check Store encrypt/decrypt
    # Return 0 if all healthy, 1 with list of dead nodes
```

### Mutation Engine (from Gardener)
- **Mutation:** Add new capability to module (e.g., Store adds secure notes)
- **Fitness Test:** Build + unit tests + integration test
- **Selection:** If fitness passes → commit, else → revert (extinction)
- **Archive:** Full genome of extinct mutations saved to archive.md

### Cron Schedule (from Учебник)
- **Every 30 min:** Textbook learning (random 🔴 topic → study → 🟢)
- **Every hour:** ParanoidStack pulse (health check)
- **Every cycle:** Evolution step + Telegram report + Git commit

---

## 📊 Current Status Summary

| Cycle | Module | Status | AAR Size | Fitness |
|-------|--------|--------|----------|---------|
| 1 | keyboard-ime | ✅ ALIVE | 41KB | ✅ PASS |
| 3 | guard | ✅ ALIVE | 33KB | ✅ PASS |
| 4 | store | 🔄 BUILDING | — | ⏳ PENDING |
| 5 | island | 📋 PLANNED | — | — |
| 6 | transport_v2 | 📋 PLANNED | — | — |
| 7 | keyboard_v2 | 📋 PLANNED | — | — |
| 8 | mesh | 📋 PLANNED | — | — |
| 9 | n2_messenger | 📋 PLANNED | — | — |
| 10 | release | 📋 PLANNED | — | — |

**Next Action:** Begin Cycle 4 — ParanoidStore implementation