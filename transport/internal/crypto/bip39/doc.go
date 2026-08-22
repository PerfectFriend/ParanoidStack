// Package bip39 implements the BIP39 mnemonic specification for the px-transport wallet.
// It supports 12, 15, 18, 21, and 24 word mnemonics (128–256 bits of entropy) using the
// official 2048-word English wordlist. Key exported functions include GenerateMnemonic,
// MnemonicFromEntropy, EntropyFromMnemonic, and KeypairFromMnemonic for Ed25519 key derivation
// via PBKDF2 with SHA512.
package bip39
