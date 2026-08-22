// Package crypto provides cryptographic primitives for the px-transport economy.
// It includes:
//   - BIP39 mnemonic generation and validation (sub-package bip39)
//   - Ed25519 keypair derivation from mnemonics
//   - Entropy-to-mnemonic encoding with SHA256 checksum
//
// The bip39 sub-package implements the full BIP39 specification using the
// official 2048-word English wordlist. It supports 12, 15, 18, 21, and 24
// word mnemonics corresponding to 128, 160, 192, 224, and 256 bits of entropy.
package crypto
