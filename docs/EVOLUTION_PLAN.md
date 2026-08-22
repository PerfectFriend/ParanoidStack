# ParanoidStack — Master Evolution Plan

> **Mission:** Build a complete zero-trust P2P stack for Android that makes Apple users jealous.
> **Philosophy:** Pirate code — no masters, no telemetry, no Google Play, no compromise.
> **Hardware:** Built on e-waste (old Android phones), free API keys, broken netbook.
> **Target:** $50/mo all-access subscription → $8.4M ARR at 10k users.

---

## 📦 Current Inventory (Monorepo: `/home/thomas/XAudit/ParanoidStack`)

| Component | Language | Files | Status | Role |
|---|---|---|---|---|
| **transport/** | Go | 84 | ✅ `go build/vet/test` green | Server backbone: VPN1→VPN2→Tor, ChainOrchestrator, Docker sidecars, HTTP API |
| **sdk/** | Kotlin | 23 + tests | ✅ 96 tests pass | Android Client SDK: SMP, Double Ratchet, X3DH, BIP-39, Invite Links, SOCKS5 |
| **keyboard/** | Kotlin | 1 | ⚠️ Compose UI only | MatrixKeyboard.kt — visual keyboard component, NOT system IME |
| **android/N2/** | Kotlin | 258 | ✅ Playable | CrazyGammon + stealth chat, Tor/V2Ray panel, Opus voice, Double Ratchet |
| **android/N3/** | Kotlin | 29 | ✅ Core ready | Mobile node: BIP-39 identity, HKDF, BridgeOrchestrator, bridges |
| **android/Nexus/** | Kotlin | 38 | ✅ Feature-rich | Full chat: Tink AES-256-GCM, ChainProxy (SOCKS5 chaining), Tor/V2Ray/SMP services |
| **android/ParanoidBackgammonDemo/** | Kotlin | 8 | ✅ Demo | SDK demo with P2P game + chat |

**Total:** 84 Go + 357 Kotlin files = **441 source files**

---

## 🎯 End-State Vision: ParanoidStack v1.0 "Black Flag"

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                        PARANOIDSTACK v1.0                                   │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                             │
│  ┌─────────────────┐  ┌─────────────────┐  ┌─────────────────┐            │
│  │  ParanoidChat   │  │  ParanoidGame   │  │  ParanoidNode   │   APPS     │
│  │    (Nexus+)     │  │    (N2+)        │  │    (N3+)        │            │
│  └────────┬────────┘  └────────┬────────┘  └────────┬────────┘            │
│           │                    │                    │                      │
│           └────────────────────┼────────────────────┘                      │
│                                ▼                                           │
│  ┌─────────────────────────────────────────────────────────────────────┐   │
│  │                    ParanoidKeyboard (System IME)                    │   │
│  │  • AES-256-GCM per keystroke  • Double Ratchet integration          │   │
│  │  • Anti-keylogger  • Secure clipboard  • PGP sign/verify            │   │
│  └────────────────────────────────┬────────────────────────────────────┘   │
│                                   │                                        │
│  ┌────────────────────────────────┼────────────────────────────────────┐   │
│  │              ParanoidSDK (Core Kotlin Library)                      │   │
│  │  Double Ratchet • X3DH • BIP-39 • Invite Links • SMP • SOCKS5       │   │
│  └────────────────────────────────┬────────────────────────────────────┘   │
│                                   │ SOCKS5 / WebSocket                     │
│                                   ▼                                        │
│  ┌─────────────────────────────────────────────────────────────────────┐   │
│  │                    px-transport (Go Server)                         │   │
│  │  VPN1 (WireGuard) → VPN2 (Xray/VLESS) → Tor → SimpleX Bridge       │   │
│  │  ChainOrchestrator • Docker sidecars • HTTP API • WebSocket Hub    │   │
│  └─────────────────────────────────────────────────────────────────────┘   │
│                                                                             │
│  ┌─────────────────────────────────────────────────────────────────────┐   │
│  │                    ParanoidGuard (Device Hardening)                 │   │
│  │  Disable GMS/Play/Firebase • DNS blocking • Per-app firewall       │   │
│  │  SELinux policies • ADB lock • Permission auditor                  │   │
│  └─────────────────────────────────────────────────────────────────────┘   │
│                                                                             │
│  ┌─────────────────────────────────────────────────────────────────────┐   │
│  │                    ParanoidStore (App Store)                        │   │
│  │  F-Droid fork + custom repo • BTC/LN/XMR payments • Tor updates    │   │
│  │  Curated privacy-first apps • 30% cut (vs Google 30%)              │   │
│  └─────────────────────────────────────────────────────────────────────┘   │
│                                                                             │
└─────────────────────────────────────────────────────────────────────────────┘
```

---

## 📋 Phase Breakdown

| Phase | Focus | Duration | Deliverable |
|---|---|---|---|
| **Phase 0** | Foundation & Cleanup | 2 days | Clean monorepo, CI, docs, transport v1.0 release |
| **Phase 1** | Android SDK Integration | 3 days | SDK as `.aar`, integrated into N2/N3/Nexus |
| **Phase 2** | ParanoidKeyboard (IME) | 5 days | System keyboard with crypto, integrated in all apps |
| **Phase 3** | ParanoidGuard (Hardening) | 3 days | Install-time hardening script + runtime auditor |
| **Phase 4** | ParanoidStore (App Store) | 5 days | F-Droid fork + crypto payments + Tor updates |
| **Phase 5** | App Polish & Demo | 4 days | 3 production APKs, QR invite flow, demo video |
| **Phase 6** | Island Integration | 3 days | Isle project as premium tier for iOS users |
| **Phase 7** | Launch & Scale | Ongoing | Landing, beta program, marketing, support |

**Total: ~25 days to v1.0 launch**

---

## 🔬 Phase 0: Foundation & Transport v1.0 Release (Days 1-2)

### 0.1 Monorepo Hygiene (4h)
- [ ] **0.1.1** Remove embedded `.git` dirs, flatten history ✅ DONE
- [ ] **0.1.2** Add `.gitignore` for all build artifacts ✅ DONE
- [ ] **0.1.3** Create `docs/` structure: `ARCHITECTURE.md`, `API_REFERENCE.md`, `BUILD.md`, `DEPLOY.md`
- [ ] **0.1.4** Add `Makefile` with targets: `build`, `test`, `lint`, `docker-build`, `android-build`, `release`
- [ ] **0.1.5** Add GitHub Actions CI: Go build/test, Android lint/assemble, SDK test

### 0.2 Transport Core Hardening (8h)
- [ ] **0.2.1** Audit `go.mod` — pin versions, remove unused deps
- [ ] **0.2.2** Add structured logging (slog) with levels, JSON output
- [ ] **0.2.3** Add health endpoints: `/healthz`, `/readyz`, `/metrics` (Prometheus)
- [ ] **0.2.4** Implement graceful shutdown (signal handling, connection draining)
- [ ] **0.2.5** Add request ID propagation, structured audit logging
- [ ] **0.2.6** Rate limiting: token bucket per API key (already partial in `transport.go`)
- [ ] **0.2.7** API versioning: `/api/v1/...` with OpenAPI spec generation
- [ ] **0.2.8** TLS support: auto-generate certs, ACME/Let's Encrypt option

### 0.3 ChainOrchestrator Completion (8h)
- [ ] **0.3.1** WireGuard config generation + key management
- [ ] **0.3.2** Xray/VLESS config generation + cert rotation
- [ ] **0.3.3** Tor hidden service management (create, rotate, backup keys)
- [ ] **0.3.4** SimpleX Chat bridge: SMP client → transport hub
- [ ] **0.3.5** Docker Compose: production-ready with healthchecks, restart policies
- [ ] **0.3.6** Chain status API: `/api/v1/chain/status` with real-time updates (WebSocket)
- [ ] **0.3.7** Auto-recovery: restart failed sidecars, re-establish chains

### 0.4 Transport Client API (4h)
- [ ] **0.4.1** Define client-facing API in `internal/transport/api.go`:
  ```go
  type TransportClient interface {
      Dial(ctx context.Context, addr string) (Conn, error)
      Listen(addr string) (Listener, error)
      Identity() *Identity
      Close() error
  }
  ```
- [ ] **0.4.2** WebSocket client implementation with auto-reconnect
- [ ] **0.4.3** Authentication: API key + reconnect token
- [ ] **0.4.4** Message framing: JSON + binary (protobuf optional)

### 0.5 Transport v1.0 Release (4h)
- [ ] **0.5.1** Tag `v1.0.0` with changelog
- [ ] **0.5.2** Docker image: `ghcr.io/paranoidstack/px-transport:v1.0.0`
- [ ] **0.5.3** Release notes: `RELEASE_v1.0.0.md`
- [ ] **0.5.4** Quickstart guide: `docs/QUICKSTART_TRANSPORT.md`

---

## 📱 Phase 1: Android SDK Integration (Days 3-5)

### 1.1 SDK as Publishable Library (8h)
- [ ] **1.1.1** Create `sdk/build.gradle.kts` with:
  - `com.android.library` plugin
  - `maven-publish` for `.aar` publishing
  - Kotlin 2.0+, Android Gradle Plugin 8.5+
  - Min SDK 24, Target SDK 35
- [ ] **1.1.2** Configure ProGuard/R8 rules for SDK consumers
- [ ] **1.1.3** Build release `.aar`: `./gradlew :sdk:assembleRelease`
- [ ] **1.1.4** Publish to local Maven: `./gradlew :sdk:publishToMavenLocal`
- [ ] **1.1.5** Create `sdk/README.md` with integration guide

### 1.2 SDK API Surface Cleanup (4h)
- [ ] **1.2.1** Define public API in `sdk/src/main/java/com/paranoidx/sdk/ParanoidSDK.kt`:
  ```kotlin
  interface ParanoidSDK {
      fun createIdentity(mnemonic: String? = null): Identity
      fun restoreIdentity(mnemonic: String): Identity
      fun createSession(peerInvite: InviteLink): Session
      fun dial(uri: String): TransportConn
      fun listen(uri: String): TransportListener
      fun inviteQR(identity: Identity): Bitmap
  }
  ```
- [ ] **1.2.2** Hide internal classes, expose only interfaces + data classes
- [ ] **1.2.3** Add `@JvmStatic` / `@JvmOverloads` for Java interop
- [ ] **1.2.4** Document all public APIs with KDoc

### 1.3 N3 Integration (8h)
- [ ] **1.3.1** Add SDK dependency in `android/N3/app/build.gradle.kts`
- [ ] **1.3.2** Replace `BridgeOrchestrator` → `ParanoidSDK.dial()/listen()`
- [ ] **1.3.3** Replace `Bip39Wallet` → `ParanoidSDK.createIdentity()`
- [ ] **1.3.4** Replace `DoubleRatchet` → `ParanoidSDK.createSession()`
- [ ] **1.3.5** Update `ChainProxy` → SDK `Socks5ProxyClient` + `TransportSelector`
- [ ] **1.3.6** Test: build APK, verify Tor/V2Ray/SMP bridges work via SDK

### 1.4 N2 Integration (8h)
- [ ] **1.4.1** Add SDK dependency in `android/N2/app/build.gradle.kts`
- [ ] **1.4.2** Replace `GameNetworkPanel` network logic → SDK `TransportSelector`
- [ ] **1.4.3** Replace chat crypto → SDK `SmpE2E` / `DoubleRatchet`
- [ ] **1.4.4** Add QR invite generation: `SDK.inviteQR(identity)`
- [ ] **1.4.5** Add lobby/invite flow: scan QR → auto-connect → game + chat
- [ ] **1.4.6** Test: 2 devices, QR invite → game + encrypted chat

### 1.5 Nexus Integration (8h)
- [ ] **1.5.1** Add SDK dependency in `android/Nexus/app/build.gradle.kts`
- [ ] **1.5.2** Replace `ChainProxy` → SDK `Socks5ProxyClient` + `TransportSelector`
- [ ] **1.5.3** Replace `DoubleRatchet` / `SmpProtocol` → SDK equivalents
- [ ] **1.5.4** Replace `Bip39Wallet` → SDK `Bip39Utils`
- [ ] **1.5.5** Keep Nexus-specific: Tink AES-256-GCM for files, WebRTC bridge
- [ ] **1.5.6** Test: full chat flow with file/voice/image sharing via SDK transport

### 1.6 SDK Test Suite (4h)
- [ ] **1.6.1** Unit tests: all crypto primitives (already 96 tests)
- [ ] **1.6.2** Integration test: SDK ↔ px-transport (local Docker)
- [ ] **1.6.3** E2E test: N2 ↔ N3 ↔ Nexus message exchange
- [ ] **1.6.4** Performance benchmark: latency, throughput, battery

---

## ⌨️ Phase 2: ParanoidKeyboard — System IME (Days 6-10)

### 2.1 IME Service Scaffold (4h)
- [ ] **2.1.1** Create `keyboard/` as Android module: `android/keyboard/`
- [ ] **2.1.2** `ParanoidInputMethodService extends InputMethodService`
- [ ] **2.1.3** `res/xml/method.xml` — IME metadata (subtype: EN, RU, DE, ES)
- [ ] **2.1.4** `AndroidManifest.xml` — `BIND_INPUT_METHOD` permission, service declaration
- [ ] **2.1.5** Keyboard layout: QWERTY, QWERTZ, AZERTY, JCUKEN (RU)
- [ ] **2.1.6** Compose UI for keyboard view (reuse `MatrixKeyboard.kt` as base)

### 2.2 Crypto Integration (8h)
- [ ] **2.2.1** Integrate `paranoidx-sdk` → `ParanoidSDK` instance per session
- [ ] **2.2.2** Per-keystroke encryption: `DoubleRatchet.encrypt(keyChar)` → send via transport
- [ ] **2.2.3** Key derivation: session key per conversation (HKDF from shared secret)
- [ ] **2.2.4** Local cache: encrypted keystroke buffer (AES-256-GCM, auto-flush)
- [ ] **2.2.5** Secure clipboard: intercept copy/paste, encrypt in transit

### 2.3 Anti-Surveillance Features (8h)
- [ ] **2.3.1** Anti-keylogger: randomize key positions per session (optional)
- [ ] **2.3.2** Secure pasteboard: auto-clear after 30s, encrypt content
- [ ] **2.3.3** Disable: learning, personalization, cloud sync, suggestions
- [ ] **2.3.4** No network permission in IME — all via SDK transport
- [ ] **2.3.5** Screen security: `FLAG_SECURE` on keyboard window

### 2.4 PGP/OTR Integration (4h)
- [ ] **2.4.1** PGP sign/verify per message (OpenPGP.js compatible)
- [ ] **2.4.2** OTR-style deniable authentication (via Double Ratchet)
- [ ] **2.4.3** Key verification: QR code fingerprint compare

### 2.5 Integration with Apps (8h)
- [ ] **2.5.1** N2: Use ParanoidKeyboard for in-game chat
- [ ] **2.5.2** N3: Use ParanoidKeyboard for all text input
- [ ] **2.5.3** Nexus: Use ParanoidKeyboard for all messaging
- [ ] **2.5.4** Shared preference: "Force ParanoidKeyboard" toggle

### 2.6 Keyboard Build & Test (4h)
- [ ] **2.6.1** Build IME APK: `./gradlew :keyboard:assembleRelease`
- [ ] **2.6.2** Test on 3 devices: Pixel, Samsung, Xiaomi (different OEM IME quirks)
- [ ] **2.6.3** Battery/performance profiling
- [ ] **2.6.4** Accessibility service compatibility (TalkBack, etc.)

---

## 🛡 Phase 3: ParanoidGuard — Device Hardening (Days 11-13)

### 3.1 Install-Time Hardening Script (8h)
- [ ] **3.1.1** `guard/harden.sh` — runs on first app launch (root optional):
  ```bash
  # Disable GMS/Play Services components
  pm disable-user --user 0 com.google.android.gms
  pm disable-user --user 0 com.google.android.gsf
  # Block Google DNS
  settings put global private_dns_mode hostname
  settings put global private_dns_specifier "dns.quad9.net"
  # Firewall rules (requires root/AFWall+)
  iptables -A OUTPUT -d 172.217.0.0/16 -j DROP  # Google
  iptables -A OUTPUT -d 157.240.0.0/16 -j DROP  # Meta
  ```
- [ ] **3.1.2** Non-root fallback: VPN-based firewall (NetGuard style)
- [ ] **3.1.3** SELinux policy module (if rooted): `paranoid_guard.te`
- [ ] **3.1.4** ADB lock: `settings put global adb_enabled 0` (requires device admin)
- [ ] **3.1.5** Bootloader lock check + warning

### 3.2 Runtime Permission Auditor (4h)
- [ ] **3.2.1** Scan all installed apps for dangerous permissions
- [ ] **3.2.2** Auto-revoke unused permissions (Android 11+)
- [ ] **3.2.3** Alert on new app install with suspicious perms
- [ ] **3.2.4** Per-app network allowlist (UI in ParanoidGuard app)

### 3.3 Network Monitor (4h)
- [ ] **3.3.1** `VpnService` implementation: capture all traffic
- [ ] **3.3.2** Detect: Google Play Services, Firebase, Analytics, Crashlytics
- [ ] **3.3.3** Block/alert on telemetry endpoints
- [ ] **3.3.4** Dashboard: real-time connection map (country, org, purpose)

### 3.4 Integration (4h)
- [ ] **3.4.1** ParanoidGuard as standalone app + library
- [ ] **3.4.2** Auto-run on first launch of any ParanoidStack app
- [ ] **3.4.3** Settings sync across apps (encrypted SharedPreferences)

---

## 🏪 Phase 4: ParanoidStore — App Store (Days 14-18)

### 4.1 F-Droid Fork (8h)
- [ ] **4.1.1** Fork F-Droid client: `store/client/`
- [ ] **4.1.2** Custom repo index format: `repo/index-v1.json`
- [ ] **4.1.3** Remove: Google Play Services dependency, crash reporting, analytics
- [ ] **4.1.4** Add: Tor onion repo mirror, signature verification (minisign)

### 4.2 Crypto Payments (8h)
- [ ] **4.2.1** BTC/Lightning: `lightningj` library, Bolt11 invoice generation
- [ ] **4.2.2** Monero: `monero-java` library, subaddress per user
- [ ] **4.2.3** Subscription logic: $5/mo per app, $50/mo all-access
- [ ] **4.2.4** Receipt verification: local + server-side (px-transport API)

### 4.3 App Curation (4h)
- [ ] **4.3.1** Curated list: ParanoidChat, ParanoidGame, ParanoidNode, ParanoidKeyboard
- [ ] **4.3.2** Third-party privacy apps: Signal (fork), Briar, Cwtch, Jami, etc.
- [ ] **4.3.3** Build verification: reproducible builds, signature pinning

### 4.4 Tor Hidden Service Updates (4h)
- [ ] **4.4.1** App updates via `.onion` repo (no clearnet)
- [ ] **4.4.2** Delta updates (bsdiff) for bandwidth efficiency
- [ ] **4.4.3** Auto-update background service (user configurable)

### 4.5 Store App Polish (4h)
- [ ] **4.5.1** UI: dark theme, Material 3, categories, search
- [ ] **4.5.2** Account: BIP-39 identity (same as apps), no email
- [ ] **4.5.3** Subscription management: view, cancel, restore
- [ ] **4.5.4** Build & sign release APK

---

## ✨ Phase 5: App Polish & Demo (Days 19-22)

### 5.1 ParanoidChat (Nexus+) (8h)
- [ ] **5.1.1** UI polish: conversations, contacts, settings
- [ ] **5.1.2** Features: disappearing messages, screenshot detection, PIN lock
- [ ] **5.1.3** File sharing: Tink AES-256-GCM, chunked upload via transport
- [ ] **5.1.4** Voice messages: Opus codec (already in N2)
- [ ] **5.1.5** Group chat: MLS protocol (future) or pairwise Double Ratchet

### 5.2 ParanoidGame (N2+) (8h)
- [ ] **5.2.1** Onboarding: QR invite → auto-match → game + chat
- [ ] **5.2.2** Lobby: public/private games, spectator mode
- [ ] **5.2.3** AI opponent: local engine (no network)
- [ ] **5.2.4** Tournaments: bracket, ELO rating (local only)
- [ ] **5.2.5** Themes: classic, matrix, pirate, cyberpunk

### 5.3 ParanoidNode (N3+) (4h)
- [ ] **5.3.1** Dashboard: chain status, peers, bandwidth, earnings
- [ ] **5.3.2** Node operator mode: relay traffic for others (incentivized)
- [ ] **5.3.3** Battery optimization: Doze mode awareness
- [ ] **5.3.4** Remote management: SSH over Tor hidden service

### 5.4 QR Invite Flow (4h)
- [ ] **5.4.1** Standard format: `paranoid://invite/<base64(InviteLink)>`
- [ ] **5.4.2** QR generation: `SDK.inviteQR(identity)` → Bitmap
- [ ] **5.4.3** QR scanning: camera intent → parse → auto-connect
- [ ] **5.4.4** Deep link handling: `AndroidManifest.xml` intent filter

### 5.5 Demo Video & Landing (4h)
- [ ] **5.5.1** Record: 3 phones, 1 game + chat, 1 node, 1 messenger
- [ ] **5.5.2** Landing page: `docs/landing/index.html` (static, deploy to IPFS/Tor)
- [ ] **5.5.3** Demo script: "From e-waste to privacy empire in 3 minutes"

---

## 🏝 Phase 6: Island Integration (Days 23-25)

### 6.1 Isle Project Revival (8h)
- [ ] **6.1.1** Create `island/` module in monorepo
- [ ] **6.1.2** Core concept: "ParanoidStack for iOS" via WebAssembly + WebRTC
- [ ] **6.1.3** Compile `px-transport` + `paranoidx-sdk` → WASM (Go/TinyGo, Kotlin/WASM)
- [ ] **6.1.4** Web UI: React/TypeScript + ParanoidStack WASM core
- [ ] **6.1.5** iOS distribution: PWA + TestFlight (enterprise cert) + AltStore

### 6.2 Cross-Platform Compatibility (8h)
- [ ] **6.2.1** Shared protocol spec: `docs/PROTOCOL_SPEC.md`
- [ ] **6.2.2** Android ↔ iOS interop test: chat, game, file transfer
- [ ] **6.2.3** Unified identity: BIP-39 mnemonic works on both platforms
- [ ] **6.2.4** Unified invite links: same QR works everywhere

### 6.3 Premium Tier (4h)
- [ ] **6.3.1** Island = premium tier ($100/mo) for iOS users
- [ ] **6.3.2** Features: iMessage-style UI, iCloud backup (encrypted), Shortcuts integration
- [ ] **6.3.3** "Pirate flag" badge for Island users in Android apps

---

## 🚀 Phase 7: Launch & Scale (Day 26+)

### 7.1 Public Launch (4h)
- [ ] **7.1.1** Website: `paranoidstack.org` (static, IPFS, Tor)
- [ ] **7.1.2** Blog post: "How we built a pirate Android stack on e-waste"
- [ ] **7.1.3** Hacker News / Reddit / Lobste.rs launch
- [ ] **7.1.4** Press kit: screenshots, demo video, architecture diagram

### 7.2 Beta Program (Ongoing)
- [ ] **7.2.1** 50 beta testers: Telegram group + feedback form
- [ ] **7.2.2** Bug bounty: $100 per critical, $50 per major (paid in BTC/XMR)
- [ ] **7.2.3** Weekly builds via ParanoidStore

### 7.3 Growth (Ongoing)
- [ ] **7.3.1** Affiliate: 20% lifetime commission for referrals
- [ ] **7.3.2** Node operator rewards: $5/mo per active relay node
- [ ] **7.3.3** Enterprise: self-hosted ParanoidStack for teams ($500/mo)

---

## 🎯 Immediate Next Actions (START NOW)

### Day 1 — Transport v1.0
```bash
cd /home/thomas/XAudit/ParanoidStack
# 1. Create docs/ structure
mkdir -p docs/{architecture,api,build,deploy}
# 2. Add Makefile
cat > Makefile << 'EOF'
.PHONY: build test lint docker-build android-build release

build:
	cd transport && go build -o bin/pxnode ./cmd/pxnode
	cd sdk && ./gradlew assembleRelease
	cd android/N2 && ./gradlew assembleRelease
	cd android/N3 && ./gradlew assembleRelease
	cd android/Nexus && ./gradlew assembleRelease

test:
	cd transport && go test ./...
	cd sdk && ./gradlew test

lint:
	cd transport && golangci-lint run
	cd sdk && ./gradlew lint

docker-build:
	docker build -t ghcr.io/paranoidstack/px-transport:latest transport/

android-build:
	cd android/N2 && ./gradlew assembleRelease
	cd android/N3 && ./gradlew assembleRelease
	cd android/Nexus && ./gradlew assembleRelease

release: build test lint docker-build android-build
	git tag -a v1.0.0 -m "ParanoidStack v1.0.0"
	git push origin v1.0.0
EOF
# 3. Run transport tests
cd transport && go test ./...
# 4. Build Docker image
docker build -t px-transport:local transport/
```

### Day 2 — SDK .aar + N3 Integration
```bash
# 1. Build SDK .aar
cd /home/thomas/XAudit/ParanoidStack/sdk
./gradlew assembleRelease publishToMavenLocal

# 2. Add to N3
cd ../android/N3
# Edit app/build.gradle.kts -> add implementation("com.paranoidx:sdk:1.0.0")
# Replace BridgeOrchestrator -> SDK
./gradlew assembleRelease
```

---

## 📊 Success Metrics

| Metric | Target |
|---|---|
| Transport `go build/vet/test` | ✅ Green |
| SDK tests | ✅ 96 pass |
| N2/N3/Nexus build | ✅ APK generated |
| ParanoidKeyboard IME | ✅ System keyboard works |
| ParanoidGuard hardening | ✅ GMS disabled, DNS blocked |
| ParanoidStore payments | ✅ BTC/LN/XMR invoice → unlock |
| QR invite flow | ✅ 2 devices connect in <10s |
| Battery drain (idle) | < 2%/hour |
| Latency (Tor) | < 2s first message |
| Demo video | ✅ 3 min, compelling |

---

## 🏴‍☠️ Pirate Principles (Non-Negotiable)

1. **No Google Play** — ever. F-Droid + ParanoidStore only.
2. **No telemetry** — no analytics, no crash reporting to third parties.
3. **No root required** — works on stock Android, enhanced with root.
4. **Open source** — AGPLv3 for server, MIT for client SDK.
5. **Crypto payments only** — BTC/LN/XMR. No Stripe, no PayPal.
6. **Tor by default** — all traffic onion-routed unless user chooses direct.
7. **Self-custody** — keys on device, mnemonic in user's brain/paper.
8. **E-waste first** — optimize for 4-6 year old devices.

---

## 📝 Notes for Future Agents

- All context saved in `/home/thomas/XAudit/ParanoidStack/` git history
- Session memory: user prefers Russian informal, high autonomy, concise
- Telegram reports: chat_id -1004431090317, topic 252
- Hardware: Beelink SER9 (Ryzen 9 7940HS, Radeon 780M, 24GB RAM)
- Local LLM: deepseek-coder-v2:16b on llama.cpp (Vulkan, 160k ctx)
- Port 8080 FORBIDDEN in PXNode context (used by SuperGuard)
- Working ONLY in `/home/thomas/SuperGuard/` and `/home/thomas/XAudit/ParanoidStack/`

---

**Generated:** $(date -u +"%Y-%m-%d %H:%M:%S UTC")
**Commit:** $(cd /home/thomas/XAudit/ParanoidStack && git rev-parse HEAD)
**Author:** ox-alpha + Human (Pirate Captain)