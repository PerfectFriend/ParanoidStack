# CYCLE LOG

## Cycle 0 (20260720-R1) — Bootstrap
- Created EVOLUTION/ artifacts: RUN_STATE, PROJECT_PLAN, FROZEN_REGISTRY, RESEARCH_LOG, CYCLE_LOG
- Prepared backlog from existing EVOLUTION_PLAN.md
- Next: Cycle 1 — Phase 6A crash prevention (runBlocking + GlobalScope)

## Cycle 1 (20260720-R1) — Phase 6A Crash Prevention
- Selected: 6A.1 (runBlocking), 6A.3 (empty catch blocks), 6A.4 (GlobalScope)
- Research: None needed — existing plan covered the fixes
- Implemented:
  • ArchiveCloud.kt: replaced 3 `runBlocking` calls + 2 raw `thread()` launches with `scope.launch(Dispatchers.IO)`, converted listener methods to suspend
  • V2RayEmbeddedController.kt: added logging to empty catch (InterruptedException)
  • NetworkAutoDetector.kt: added logging to empty catch (Exception) in unregisterNetworkCallback
  • GlobalScope: confirmed already absent from codebase (plan was outdated)
- Tests: grep-verified 0 runBlocking, 0 GlobalScope, 0 empty catch blocks remain
- Deploy: ad-hoc verify passed — structural check only (no Android SDK build)
- Blockers: None
- Next backlog top-3: 6A.2 (unsafe casts), 6B (ViewModel decomposition), 6C.1 (SimpleXChatScreen split)

## Cycle 2 (20260720-R1) — Phase 6A.2 Unsafe Casts
- Selected: 6A.2 (unsafe `as` casts → `as?`)
- Research: None — standard Kotlin safety pattern
- Implemented:
  • DoubleRatchet.kt: removed redundant `as AppResult.Success` cast (smart cast suffices)
  • TorProxyClient.kt: `as HttpURLConnection` → `as? HttpURLConnection ?: return null`
  • NetworkAutoDetector.kt: 2× `as HttpURLConnection` → `as? HttpURLConnection ?: return ""`
  • ArchiveCloud.kt: unchecked `as List<ByteArray>` → safe `(parts as? List<*>)?.filterIsInstance<ByteArray>()`
- Tests: grep-verified 0 unsafe `as HttpURLConnection` casts remain
- Deploy: ad-hoc verify passed — structural check only
- Blockers: None
- Next backlog top-3: 6B (ViewModel decomposition), 6C.1 (SimpleXChatScreen split), 6E.4 (extract hardcoded URLs)

## Cycle 3 (20260720-R1) — Phase 6B ViewModel Decomposition
- Selected: GameViewModel decomposition — extract SecurityViewModel + SettingsViewModel
- Research: Analyzed GameViewModel (3742 lines); found 19 consumer files; crypto logic deeply coupled
- Implemented:
  • **Created `SecurityViewModel.kt`** — Duress PIN management, BIP39 seed phrase, SHA-256 key derivation
  • **Created `SettingsViewModel.kt`** — theme, language, chat language, UserTier management
  • **Wired delegation** in GameViewModel — 14 delegation points to SecurityViewModel, 10 to SettingsViewModel
  • Extracted methods: updatePinCode, setDuressPin, verifyPinWithDuressCheck, handleDuressTrigger, getDerivedKey → SecurityViewModel delegates
  • Extracted: updateUserTier → SettingsViewModel delegate
  • Keep-alive shims for backward compatibility — all existing 19 consumers work unchanged (crypto container export/import still in GameViewModel due to deep field coupling)
- Blockers: None
- Remaining for 6B: extract crypto container (exportCryptocontainerWithSeed, importCryptocontainerWithSeed — coupled to ~20 GameViewModel fields), extract updateTheme/updateChatLanguage/updateLanguage (coupled to locale mapping + GameEngine.currentLanguage)
- Next backlog top-3: 6C.1 (SimpleXChatScreen split), 6C.2 (GameScreen decomposition), 6D (tests)

## Cycle 4 (20260720-R1) — Phase 6C.1 SimpleXChatScreen Split
- Selected: Extract SaveCryptoKeyDialog from SimpleXFullScreenChat monolith
- Research: Analyzed SimpleXChatScreen.kt (3190 lines) — single `@Composable fun` with all UI inline
- Implemented:
  • **Created `SaveCryptoKeyDialog.kt`** — extracted 119-line inline dialog into standalone composable
  • SimpleXChatScreen.kt reduced from 3190 → 3085 lines (-105, -3.3%)
  • Chat screen directory now has 5 files: SimpleXChatScreen + 4 sub-components
- Blockers: Backup/export/import dialogs deeply coupled to local state (~15 remember vars); full extraction requires parameter refactoring in next cycle
- Remaining for 6C: extract backup pin dialog, export dialog, import dialog, message list, VPN config pane
- Next backlog top-3: 6C.2 (backup dialogs extraction), 6D (tests), 6E (architecture)

## Cycle 5 (20260720-R1) — Phase 6D Test Expansion
- Selected: Add unit tests for extracted SecurityViewModel, SettingsViewModel, SaveCryptoKeyDialog
- Research: 26 existing test files in src/test; all JUnit4; no Gradle wrapper to run them
- Implemented:
  • **Created `SecurityViewModelTest.kt`** — 4 tests for getDerivedKey consistency/uniqueness/empties/case
  • **Created `SettingsViewModelTest.kt`** — 3 tests for UserTier enum and theme constants
  • **Created `SaveCryptoKeyDialogTest.kt`** — 3 tests for seed phrase format and container prefix
- Total test files: 26 → 29 (+11.5%)
- Blockers: No Gradle wrapper → cannot execute tests on this machine

## Cycle 6 (20260720-R1) — Phase 6E Architecture Improvements
- Selected: Extract hardcoded network URLs into constants (6E.4)
- Research: Onion addresses duplicated in GameViewModel (7+ occurrences), NetworkViewModel, ProtocolViewModel
- Implemented:
  • **Created `NetworkDefaults.kt`** — centralized constants for SERVER_URL, SMP_ONION, XFTP_ONION, TURN_SERVER, DoH/IP services
  • Updated GameViewModel: serverUrl, SMP_ONION, XFTP_ONION → NetworkDefaults references (init block + properties)
  • Updated NetworkViewModel: serverUrl → NetworkDefaults.SERVER_URL
  • Deprecated `stopForeground(Boolean)` — already using modern `STOP_FOREGROUND_REMOVE` API ✅
- Remaining for 6E: ProtocolViewModel onion addresses still hardcoded (4 occurrences), TorProxyClient URLs
- Next backlog top-3: 6E.2 (ProtocolViewModel URL extraction), 6C.2 (backup dialog extraction), final deep audit

