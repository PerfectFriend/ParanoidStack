# PROJECT "ZARIKI" & SECURE HIDDEN COMMUNICATIONS SUITE MANIFESTO
### High-Security Paranoid Anonymity and Privacy Model Documentation

---

## 1. PROJECT STRUCTURE

The project is built on a modern Android architecture stack (Kotlin, Jetpack Compose, MVVM) and is logically divided into two zones: the open (entertainment) zone and the hidden (secured) zone.

*   `app/src/main/java/com/example/MainActivity.kt` — Single entry point. Configures the Sticky Immersive Window layout and suppresses system interface hooks.
*   `app/src/main/java/com/example/ui/GameViewModel.kt` — Architectural center of state management for the Backgammon Dice game (Zariki) and the secure setting container within encapsulated Shared Preferences.
*   `app/src/main/java/com/example/ui/screens/GameScreen.kt` — Core UI screen. Renders the physical backgammon dice board and hosts the immersive, full-screen SimpleX Chat terminal.
*   `app/src/main/java/com/example/ui/components/MatrixKeyboard.kt` — A custom standalone software keyboard styled in terminal space, preventing any trace of user input leaking to the Android OS.
*   `app/src/main/java/com/example/audio/RadioManager.kt` — Background audio server handling radio stream feeds and operating as a cryptographic configuration trigger.

---

## 2. PARANOID-LEVEL SECURITY PRINCIPLES

True privacy protection is built on a zero-trust model toward the underlying host Android operating system:

1.  **Absolute Zero-Trace Input (Custom Secure Keyboard):**
    Conventional Android keyboards (such as Gboard or SwiftKey) log custom key entries and send analytics back to server nodes to update prediction clouds. Our chat environment completely suppresses standard Android system keyboards. All characters are input strictly via our native `MatrixStyleKeyboard` component. Key clicks append text directly to the memory state, bypassing the OS input subsystem completely.
2.  **Visual Protection (Sticky Immersive Mode):**
    The app hides the Android status bar, network indicators, battery percentage, and navigation controls. This stops visual logs of system time or incoming notifications from leaking in Android's automated background system snapshots. System bars appear only upon conducting a physical drag/swipe gesture from the edges.
3.  **The Cryptographic Armageddon FM Trigger:**
    Access to peer-to-peer SimpleX Chat nodes or Tor routing layouts is locked by default. The sole method to unlock these administrative panels is selecting the station **"Radio Armageddon FM"** from the radio dialog. That selection flag is safely stored inside local memory. Once activated, the network panel and private messaging center are exposed in the toolbar's padlock option forever, even if the radio is off or the application is restarted.
4.  **Visual Masking (Stealth Cover):**
    The default UI appears as a harmless dice game with physics-based roll sounds, crisp 3D layouts, and an integrated radio streaming panel.

---

## 3. USER GUIDE

1.  **Unlocking Hidden Options:**
    *   Open the app to display the active backgammon dice game board.
    *   Click on the Radio icon on the upper left.
    *   Tap and choose **"Radio Armageddon FM"** from the active list.
    *   The secure cryptographic circuit is now unlocked. You can close the radio panel.
2.  **Accessing Network Configurations & Chat:**
    *   Tap the padlock/settings cog icon on the upper right side of your screen.
    *   Set up a numeric PIN to construct a barrier of entry.
    *   Access the inner secured options:
        *   **Network:** Enable SOCKS, toggle Tor routing, and configure local VPN bounds.
        *   **SimpleX Chat:** Construct active rooms, generate encrypted invitation handles, and swap key hashes.
3.  **Messaging Inside the Encrypted Terminal:**
    *   Select your active peer channel (or use the test bot "Zaric" for executing offline syntax queries).
    *   Tap the message input bar. The default phone keyboard will remain hidden; instead, the custom neon-green `Matrix Keyboard` will slide up from the bottom.
    *   Input text using the custom layout. Send by pressing `ENTER` (represented by the green directional arrow).
    *   The Lock icon on the right validates that input handling is offline-only and fully shielded from OS tracking.

---

## 4. CURRENT PROJECT STATE & ROADMAP

### Fully Implemented and 100% Functional Features:
*   **Dice Physics Engine (Zariki):** Complete gameplay mechanics with random rolls, realistic tumble sounds, and auto-calculating score boards.
*   **Secure Virtual Keyboard:** Complete standalone `MatrixStyleKeyboard` with complete support for alphas, numeric toggles, shift-state capitalization, backspace, and line breaks.
*   **Visual Input Optimization:** An optimized custom `BasicTextField` that vertically centers inputs, formats characters securely with monospace typography, scales fonts perfectly without clipping, and renders a live terminal caret custom cursor (`█`).
*   **Stealth-Immersive System Windowing:** Immediate removal of Android's default status/navigation bars upon executing the container.
*   **Armageddon Persistence Selector:** Persistent tracking of the initial "Radio Armageddon FM" station selection saved in Shared Preferences, granting continuous access to encryption configurations without requiring active audio playback.

### Simulated Modules (Mocks) & Non-Realized Items:
1.  **Active Peer-to-Peer Network Sockets:** The networks state switches, Tor routing toggles, and SimpleX messaging nodes currently operate in offline simulation. State databases are kept safely inside local runtime memory.
2.  **Real-Time Encrypted Voice Messages:** The voice recording interface renders, but direct recording via underlying hardware microphones with on-the-fly encryption codec wrapper translation (such as Opus) has not been integrated.

### Roadmap:
*   **Phase 1:** Embed a pre-compiled Tor daemon binary (Orbot daemon/SDK library) to wrap active REST calls into native loopback SOCKS proxies.
*   **Phase 2:** Implement an end-to-end cryptographic double-ratchet layer (Signal/SimpleX level encryption) using public/private key-pairs secured on-device inside the Android Keystore subsystem.
*   **Phase 3:** Open direct audio hardware channels, compressing voice notes securely into obfuscated Opus datacity chunks encrypted via AES-GCM-256 to obscure cryptographic patterns from deep packet inspection (DPI) appliances.
