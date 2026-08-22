// Package crypto provides cryptographic primitives for the px-transport economy.
// It includes BIP39 mnemonic generation and validation, Ed25519 keypair derivation
// from mnemonics via PBKDF2, and entropy-to-mnemonic encoding with SHA256 checksums.
// Sub-packages implement Bitcoin atomic swaps (btc), Ethereum bridge transfers (eth),
// and the full BIP39 specification with the official 2048-word English wordlist (bip39).
package crypto
