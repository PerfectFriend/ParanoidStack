# N2 Evolution Plan — Phase 6: Hardening & Test Expansion

**Date:** July 2026
**Project:** ~210 Kotlin files, ~30,448 LOC
**Current state:** 164 tests pass, BUILD SUCCESSFUL, APK exported

---

## Completed (Phases 1–5, 7–8)

| Phase | What | Result |
|-------|------|--------|
| 1 — Navigation | AppNavHost, 25+ routes, MainActivity refactor | 1 file created, 3 modified |
| 2 — Services | MyApplication service management | ~20 lines added |
| 3 — Decomposition | GameDialogs (1765→1), Language (944→12), RadioManager (960→823) | 10 new files, ~2883 lines removed |
| 4 — Code Quality | Fixed 20 `!!`, 47 printStackTrace→Log.e, lint config | 8 files modified |
| 5 — Tests | Ran 164 tests, all pass | Verified |
| 7 — Settings Hub | 8-category settings, push toggle, privacy wiring | ~150 lines |
| 8 — Onboarding | First-run detection, OnboardingScreen wired | ~20 lines |

---

## Remaining Issues (by severity)

### 🔴 Critical (crash/ANR risk)

| # | Issue | Count | Impact |
|---|-------|-------|--------|
| C1 | `runBlocking` in production code | 15 calls in 7 files | ANR if on main thread |
| C2 | Unsafe `as` casts | 20 occurrences | ClassCastException |
| C3 | Empty `catch` blocks swallowing exceptions | 41 occurrences | Silent data loss |
| C4 | `GlobalScope` leak | 1 (SecurityEnhancements.kt:64) | Memory leak |

### 🟠 High (structure/quality debt)

| # | Issue | Details |
|---|-------|---------|
| H1 | SimpleXChatScreen.kt | 3,748 lines — largest monolith |
| H2 | GameViewModel.kt | 3,519 lines — god ViewModel |
| H3 | GameScreen.kt | 1,923 lines — monolithic composable |
| H4 | GameEngine.kt | 856 lines — core engine needs splitting |
| H5 | GameSettingsDialog.kt | 783 lines — massive dialog |
| H6 | GameWelcomeScreen.kt, GameCryptoWizard.kt, MatrixNoGameScreen.kt | 634–719 lines each |
| H7 | RadioManager.kt | Still 753 lines after partial split |
| H8 | GameDicePanel.kt | 516 lines |

### 🟡 Medium (test coverage gaps)

| # | Gap | Details |
|---|-----|---------|
| M1 | 27/35 packages have ZERO tests | audio, protocols, security, navigation, 22+ screens |
| M2 | 3 largest files have no tests | SimpleXChatScreen, GameViewModel, GameScreen |
| M3 | No UI/compose interaction tests | Only 1 screenshot test exists |
| M4 | Only 1 integration/E2E test | SMPEndToEndTest |

### 🟢 Low (architectural debt)

| # | Issue | Details |
|---|-------|---------|
| L1 | No DI framework (Hilt/Koin) | Massive manual `GameViewModel` construction |
| L2 | No SavedStateHandle | Process death = state loss |
| L3 | Plaintext SharedPreferences | Sensitive data unencrypted |
| L4 | Wildcard imports (100+) | Obscures dependency tracking |
| L5 | Hardcoded URLs (60+) | Radio streams, Tor bridges, API endpoints |

---

## Proposed Plan: 6 Sub-Phases

### Phase 6A — Crash Prevention (🔴 Critical)

**Goal:** Eliminate all ANR and crash risks.

| Task | Files | Effort |
|------|-------|--------|
| 6A.1 Replace `runBlocking` with proper coroutines | FoxrayVpnManager (5), NetworkOrchestrator (3), ArchiveCloud (3), TorEmbeddedController, TorProxyClient, FoxrayVpnService, VoiceMessageManager | **7 files, ~2h** |
| 6A.2 Convert `as` to `as?` safe casts | RadioManager, FoxrayVpnManager, TelegramReporter, TorProxyClient, WebDavBackup, FcmPushService, NotificationChannels, QuietHoursManager, SmpNotificationService, SimpleXChatScreen, DebugPanel, GameViewModel, ArchiveCloud, DoubleRatchet | **14 files, ~1h** |
| 6A.3 Add logging to empty catch blocks | AudioPlayer, OnionStreamBridge, RadioManager, VoiceRecorder, PerformanceOptimizer, ProfileManager, SMPClient, TorEmbeddedController, TorP2PManager, TransportAppStore, TransportProvider, V2RayEmbeddedController, XFTPClient, NodeMeshManager, ArchiveCloud, CrashLogHandler, FoxrayVpnService, PushNotificationForegroundService | **18 files, ~1h** |
| 6A.4 Replace GlobalScope with lifecycle-bound scope | SecurityEnhancements.kt | **1 file, ~10min** |

**Verification:** `gradle test --no-daemon`, verify 164+ tests still pass.

---

### Phase 6B — ViewModel Refactoring (🟠 High)

**Goal:** Split GameViewModel.kt (3,519 lines) into focused ViewModels.

**Current state:** GameViewModel manages game engine, Tor, V2Ray, SimpleX, VPN, radio, TTS, crypto, Telegram, P2P, themes, languages all in one class.

| Task | New File | Responsibility |
|------|----------|----------------|
| 6B.1 Extract `ProtocolViewModel` | `ui/viewmodel/ProtocolViewModel.kt` | Tor/V2Ray/SimpleX/ProtocolOrchestrator state + lifecycle |
| 6B.2 Extract `NetworkViewModel` | `ui/viewmodel/NetworkViewModel.kt` | VPN, network detection, bandwidth, proxy chain |
| 6B.3 Extract `AudioViewModel` | `ui/viewmodel/AudioViewModel.kt` | Radio, walkie-talkie, TTS, voice messages |
| 6B.4 Extract `SecurityViewModel` | `ui/viewmodel/SecurityViewModel.kt` | Duress PIN, mimicry, screen security |
| 6B.5 Extract `GameViewModel` (core) | Keep, reduce to ~800 lines | Game engine, match history, leaderboard only |
| 6B.6 Wire via DI pattern | `GameViewModelFactory` updated | Construct sub-ViewModels, pass to screens |

**Strategy:** Non-destructive extraction — new ViewModels will be created alongside the existing one. Old `GameViewModel` methods will delegate to sub-ViewModels via composition. Each extraction is verified to build before moving to the next.

**Verification:** `gradle assembleDebug --no-daemon`, verify all existing screens still function identically.

---

### Phase 6C — Screen Decomposition (🟠 High)

**Goal:** Split the remaining large screen files.

| Task | File | Lines → Target | Strategy |
|------|------|----------------|----------|
| 6C.1 | SimpleXChatScreen.kt | 3,748 → <1,000 | Extract: ChatInputBar, MessageListView, ChatContactList, ChatSettingsPanel into `ui/screens/chat/` sub-components |
| 6C.2 | GameScreen.kt | 1,923 → <800 | Currently wraps 7+ dialogs (already extracted). Extract HUD panel, overlay system, welcome/radio/settings inline sections |
| 6C.3 | GameSettingsDialog.kt | 783 → <400 | Split into: NetworkSettingsTab.kt, CryptoContainerTab.kt, ThemeSettingsTab.kt, MiscSettingsTab.kt |
| 6C.4 | GameWelcomeScreen.kt | 719 → <400 | Extract language selector and player name entry as separate composables |
| 6C.5 | GameCryptoWizard.kt | 698 → <400 | Split seed phrase generate/import/export steps |
| 6C.6 | MatrixNoGameScreen.kt | 634 → <400 | Extract matrix rain animation, PIN pad, panic button |
| 6C.7 | GameEngine.kt | 856 → <500 | Split AI logic into `AIPlayerLogic.kt`, board rules into `GameRules.kt` |
| 6C.8 | GameDicePanel.kt | 516 → <300 | Extract doubling cube and lottery UI |

**Verification:** Build after each split. No functional changes — pure file reorganization.

---

### Phase 6D — Test Expansion (🟡 Medium)

**Goal:** Add tests to the 27 uncovered packages, starting with the most critical.

| Priority | Package | Current Tests | Target | Key Files to Test |
|----------|---------|---------------|--------|-------------------|
| P0 | `security/` | 0 | 15+ | DuressPinManager, ClipboardGuard, ScreenSecurityManager, SecurityAudit, EncryptedStorage |
| P0 | `protocols/` | 0 | 20+ | NetworkAutoDetector, ProtocolRegistry, ProtocolOrchestrator, NodeMeshManager |
| P1 | `audio/` | 0 | 15+ | RadioManager state/transitions, OnionStreamBridge, OpusEncoder, VoiceRecorder |
| P1 | `navigation/` | 0 | 10+ | NavRoutes, DeepLinkHandler, NavigationActions |
| P2 | `ui/screens/settings/` | 0 | 10+ | ProtocolSettingsScreen, NetworkSettingsScreen, PrivacySettingsScreen |
| P2 | `ui/screens/chat/` (besides data) | 2 | 8+ | ChatMessage data, message formatting |
| P3 | `ui/screens/dialogs/` | 0 | 7+ | Each dialog renders and dismisses correctly |
| P3 | `service/` | 1 | 8+ | PushNotificationForegroundService, QuietHoursManager, BandwidthMonitor |

**Testing strategy:**
- Data/model classes → JUnit + Robolectric (fast, existing pattern)
- Compose UI → Compose UI Test + Robolectric (screenshot + interaction)
- ViewModels → Turbine for Flow testing + fake repositories
- Protocols → Mock network layer + verify state transitions

**Verification:** Target 200+ total tests, `gradle test --no-daemon` green.

---

### Phase 6E — Architecture Improvements (🟢 Low)

**Goal:** Fix architectural debt without breaking existing functionality.

| Task | Description | Approach |
|------|-------------|----------|
| 6E.1 Replace SharedPreferences with DataStore | Migrate `service_prefs`, `app_prefs` to Jetpack DataStore | Add dependency, create migration layer, swap calls |
| 6E.2 Encrypt sensitive prefs | PIN config, server addresses, tokens | Route through existing SecureStorage or EncryptedSharedPreferences |
| 6E.3 Replace wildcard imports | 100+ `import .*` across codebase | Configure ktlint `no-wildcard-imports` rule, auto-fix |
| 6E.4 Extract hardcoded URLs | 60+ URLs in source code | Create `NetworkConfig.kt` object with all endpoints |
| 6E.5 Add SavedStateHandle to ViewModels | GameViewModel state survives process death | `AbstractSavedStateViewModelFactory`, `SavedStateHandle` |

**Verification:** Build must pass, no functional regressions.

---

### Phase 6F — Feature Parity (v2.2 target)

**Goal:** Close feature gaps vs original SimpleX Chat reference.

| Feature | Priority | Current | Target |
|---------|----------|---------|--------|
| Contact QR scanning | High | Partial (QR scanner exists, not wired to contact exchange) | Full: scan invite → parse → connect |
| Message reactions | High | Only emoji reactions, no persistence | Persist reactions in Room, sync via SMP |
| Group admin tools | Medium | Basic create/join | Admin controls: add/remove members, change name, promote admins |
| Message search UI | Medium | FTS backend exists, no UI | Search bar in chat list, results screen |
| File preview | Low | Basic FilePreviewScreen | Image zoom, audio waveform, document viewer |
| Notification actions | Low | Receive-only | Reply/archive actions from notification |
| Contact blocking sync | Low | Local only | Sync blocklist across devices via SMP |
| End-to-end test | High | 1 test (SMPEndToEnd) | Full flow: connect → message → receive → ratchet rotation |

---

## Timeline Estimate

| Phase | Tasks | Estimated effort |
|-------|-------|-----------------|
| 6A — Crash Prevention | 4 tasks, 40 files | **4–6 hours** |
| 6B — ViewModel Refactor | 6 tasks | **6–8 hours** |
| 6C — Screen Decomposition | 8 tasks | **8–10 hours** |
| 6D — Test Expansion | 15+ tasks | **10–12 hours** |
| 6E — Architecture | 5 tasks | **4–6 hours** |
| 6F — Feature Parity | 9 tasks | **12–16 hours** |
| **Total** | **47 tasks** | **44–58 hours** |

---

## Execution Order

```
Phase 6A (CRASH FIXES) → 6B (VIEWMODEL) → 6C (SCREENS) → 6D (TESTS) → 6E (ARCHITECTURE) → 6F (FEATURES)
```

**Rationale:** Fix crashes first (user-facing). Then restructure code (developer-facing). Then test (quality). Then polish (maintainability). Then features (value).

Each phase builds on the previous. After 6A–6C, the codebase will be structurally sound enough to test effectively (6D) and refactor safely (6E). Feature work (6F) is last because it depends on a stable, tested foundation.
