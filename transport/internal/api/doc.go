// Package api implements the HTTP API layer for ParanoidX.
//
// This package provides all HTTP handlers organized by domain:
//   - chat.go:        Chat messaging, history, stream, contacts, invoices
//   - economy.go:     Economy endpoints (oracle, tokenomics, dividend, etc.)
//   - admin.go:       Admin functions (metrics, diagnostics, docker, etc.)
//   - radio.go:       Radio streaming, stations, playlists
//   - container.go:   CryptoContainer encrypt/decrypt/wipe
//   - steward.go:     Steward AI agent endpoint
//   - bridge.go:      Bridge status and management
//   - treasury.go:    Treasury state, proof-of-reserve
//   - vault_crypto.go: Encrypted vault operations
//   - transport.go:   Transport layer (Tor, SOCKS5)
//   - wallet.go:      Wallet management
//   - invoice.go:     Invoice creation/payment
//   - agent.go:       AI agent endpoint
//   - account.go:     Account management
//   - onboarding.go:  User onboarding flow
//   - db.go:          Database backup/restore
//   - nodeinfo.go:    Comprehensive node information
//   - lock.go:        PIN lock service
//   - royal.go:       Royal node networking
//   - docs.go:        API documentation
//   - ico.go:         Initial coin offering
//   - swap.go:        Cross-chain atomic swap
//   - dao.go:         DAO governance
//   - arbitration.go: Arbitration/dispute resolution
//   - market.go:      Marketplace
//   - mining.go:      Mining rewards
//   - argentum.go:    Silver token (Argentum) management
//   - advertising.go: Advertising marketplace
//   - genesis.go:     Genesis ICO and lock
//   - pos.go:         Point-of-sale system
//   - simplex_channels.go: SimpleX channel management
//   - webhookqueue.go: Webhook delivery queue
//   - silver.go:      Silver-backed asset mint/burn/list
//   - did.go:         W3C DID document management (in royal.go)
//   - relay.go:       Inter-node message relay (in royal.go)
package api
