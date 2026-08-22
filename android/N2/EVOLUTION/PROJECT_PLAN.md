# Not Gammon — Project Plan

## Vision

Dual-layer Android-приложение: полноценная игра в нарды (Backgammon), под которой скрыт децентрализованный P2P-мессенджер с анонимной маршрутизацией (Tor → V2Ray → SimpleX/SMP).

## Phase Execution Order

```
Phase 6A (CRASH FIXES) → 6B (VIEWMODEL) → 6C (SCREENS) → 6D (TESTS) → 6E (ARCHITECTURE) → 6F (FEATURES)
```

## Phase 6A — Crash Prevention (🔴 Critical)

| # | Task | Files | Effort | Status |
|---|------|-------|--------|--------|
| 6A.1 | Replace `runBlocking` with proper coroutines | ArchiveCloud.kt | S | ✅ |
| 6A.2 | Convert `as` to `as?` safe casts | DoubleRatchet, TorProxyClient, NetworkAutoDetector, ArchiveCloud | S | ✅ |
| 6A.3 | Add logging to empty catch blocks | V2RayEmbeddedController, NetworkAutoDetector | S | ✅ |
| 6A.4 | Replace GlobalScope with lifecycle-bound scope | — (already clean) | S | ✅ |

## Phase 6B — ViewModel Decomposition (🟠 High)

| # | Task | New File | Status |
|---|------|----------|--------|
| 6B.1 | Extract ProtocolViewModel | `ui/viewmodel/ProtocolViewModel.kt` | 🔲 |
| 6B.2 | Extract NetworkViewModel | Already exists — needs audit | 🔲 |
| 6B.3 | Extract AudioViewModel | Already exists — needs audit | 🔲 |
| 6B.4 | Extract SecurityViewModel | `ui/viewmodel/SecurityViewModel.kt` | 🔲 |
| 6B.5 | Reduce GameViewModel | Keep core, target ~800 lines | 🔲 |
| 6B.6 | Wire via DI pattern | Update GameViewModelFactory | 🔲 |

## Phase 6C — Screen Decomposition (🟠 High)

| # | Task | File | Lines → Target | Status |
|---|------|------|----------------|--------|
| 6C.1 | Split SimpleXChatScreen | 3,748 → <1,000 | 🔲 |
| 6C.2 | Split GameScreen | 1,923 → <800 | 🔲 |
| 6C.3 | Split GameSettingsDialog | 783 → <400 | 🔲 |
| 6C.4 | Split GameWelcomeScreen | 719 → <400 | 🔲 |
| 6C.5 | Split GameCryptoWizard | 698 → <400 | 🔲 |
| 6C.6 | Split MatrixNoGameScreen | 634 → <400 | 🔲 |
| 6C.7 | Split GameEngine | 856 → <500 | 🔲 |
| 6C.8 | Split GameDicePanel | 516 → <300 | 🔲 |

## Phase 6D — Test Expansion (🟡 Medium)

| Priority | Package | Current | Target |
|----------|---------|---------|--------|
| P0 | security/ | 0 | 15+ |
| P0 | protocols/ | 0 | 20+ |
| P1 | audio/ | 0 | 15+ |
| P1 | navigation/ | 0 | 10+ |
| P2 | settings/ | 0 | 10+ |
| P2 | chat/ | 2 | 8+ |

## Phase 6E — Architecture (🟢 Low)

| # | Task | Approach |
|---|------|----------|
| 6E.1 | DataStore migration | Replace SharedPreferences |
| 6E.2 | Encrypt sensitive prefs | Route through SecureStorage |
| 6E.3 | Remove wildcard imports | ktlint rule + auto-fix |
| 6E.4 | Extract hardcoded URLs | NetworkConfig.kt |
| 6E.5 | SavedStateHandle | Process death survival |

## Phase 6F — Feature Parity

| Feature | Priority | Current → Target |
|---------|----------|-----------------|
| Contact QR scanning | High | Partial → Full |
| Message persistence | High | Emoji only → Persisted |
| Group admin tools | Medium | Basic → Admin controls |
| Message search UI | Medium | No UI → Search screen |
| E2E test | High | 1 test → Full flow |
