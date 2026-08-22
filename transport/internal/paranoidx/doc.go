// Package paranoidx implements the multi-layer secure communication bridge:
//
//	V2Ray → WireGuard (VPN) → Tor (Onion) → SimpleX (Metadata-free)
//
// Architecture:
//
//	+------------------------------------------------------------------+
//	|                      ParanoidX Bridge                             |
//	|                                                                   |
//	|  ┌────────────┐  ┌──────────┐  ┌──────────┐  ┌───────────────┐  |
//	|  │ V2Ray Proxy │→│ WireGuard │→│ Tor      │→│ SimpleX Bridge │  |
//	|  │ Manager     │  │ Manager   │  │ Manager  │  │ (existing)    │  |
//	|  └────────────┘  └──────────┘  └──────────┘  └───────────────┘  |
//	|       │               │             │               │            |
//	|       v               v             v               v            |
//	|  ┌──────────────────────────────────────────────────────────┐    |
//	|  │                 Health / Status Monitor                  │    |
//	|  └──────────────────────────────────────────────────────────┘    |
//	+------------------------------------------------------------------+
//
// Each layer provides defense-in-depth:
//   - V2Ray:   Obfuscates traffic patterns, bypasses DPI
//   - VPN:     Encrypts transport, provides fixed exit IP
//   - Tor:     Anonymizes origin through onion routing
//   - SimpleX: Metadata-free messaging with forward secrecy
//
// The bridge exposes /api/paranoidx/ endpoints for status monitoring
// and configuration management.
package paranoidx
