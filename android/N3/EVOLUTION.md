# N3 Evolution Plan

## Phase 1 — Foundation (current)
- [x] Single-Activity WebView architecture
- [x] Tor daemon (SOCKS5 + hidden service)
- [x] SMP messaging client (WebSocket over Tor)
- [x] AEAD encryption via Tink (AES256-GCM)
- [x] EncryptedSharedPreferences keystore
- [x] 7 JS bridges (Tor, Crypto, Keystore, Clipboard, System, Biometric, Android)
- [x] Boot receiver for auto-start
- [x] Deep link support (n3://)

## Phase 2 — Identity & Localization (this sprint)
- [ ] Auto-detect system language → apply locale
- [ ] English + Russian localisation (Planned: +ar, +fa, +zh, +es, +fr, +de, +tr, +uk)
- [ ] BIP39-based identity: 5 user words + date → 12-word seed phrase
- [ ] Seed verification: random 3 words check
- [ ] Profile persistence (encrypted in Keystore)
- [ ] Bridge configuration system (OpenVPN, WireGuard, V2Ray, Tor)
- [ ] Bridge import (.ovpn, .conf, vmess/vless links)
- [ ] Bridge tab in UI

## Phase 3 — Boot Sequence
- [ ] Language → Profile → Bridges pipeline
- [ ] Network capabilities detection (IPv4/IPv6, DNS, DPI, censorship)
- [ ] Individual bridge element health checks
- [ ] Automatic optimal chain assembly (probe: direct → v2ray → wireguard → v2ray+tor)
- [ ] End-to-end connectivity verification (SMP reachable)
- [ ] Terminal-ready signal to user

## Phase 4 — Messaging Core
- [ ] Contact management (address book, nicknames)
- [ ] Key exchange (out-of-band or via SMP)
- [ ] Message persistence (local encrypted DB — Room + Tink)
- [ ] Message history with search
- [ ] File/image sharing (tunneled via Tor)
- [ ] Read receipts, typing indicators
- [ ] Multi-device sync (via SMP queues)

## Phase 5 — Security & Privacy
- [ ] Disappearing messages (self-destruct timers)
- [ ] Screen security (FLAG_SECURE already set)
- [ ] Clipboard auto-clear timer
- [ ] Panic mode (wipe session, destroy keys)
- [ ] Audit log (who connected, when, from where)
- [ ] Biometric app lock (already wired)

## Phase 6 — Network Resilience
- [ ] Automatic bridge rotation on failure
- [ ] Obfuscated TLS (WebSocket over HTTPS伪装)
- [ ] Multi-hop: V2Ray→WireGuard→Tor chain builder
- [ ] Bandwidth monitoring + adaptive compression
- [ ] DNS over Tor (already implicit via SOCKS5)
- [ ] Connection health dashboard

## Phase 7 — Polish & UX
- [ ] Material Design 3 UI (replace debug WebView with Jetpack Compose)
- [ ] Push notifications via FCM (wake-up only, no content)
- [ ] Widgets (quick connect, message preview)
- [ ] Notification reply action
- [ ] Accessibility (TalkBack, font scaling)
- [ ] Dark/AMOLED theme (already dark themed)

## Phase 8 — Distribution
- [ ] Gradle build flavors (FOSS vs GMS)
- [ ] ProGuard/R8 hardening (rules exist)
- [ ] APK size optimization
- [ ] F-Droid + GitHub Releases pipeline
- [ ] Auto-update mechanism (own update channel over Tor)

---

## Architecture invariants (will not change)
- WebView UI shell → native services underneath
- All crypto via Tink (no custom crypto)
- Tor as mandatory transport layer
- SMP as messaging protocol
- No Google Play Services dependency
- minSdk 26, targetSdk 34
- Single-Activity, service-oriented
