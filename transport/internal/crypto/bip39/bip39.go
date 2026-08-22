// Package bip39 implements BIP39 mnemonic phrase generation and validation
package bip39

import (
	"crypto/ed25519"
	"crypto/rand"
	"crypto/sha512"
	"encoding/hex"
	"fmt"
	"hash"
	"strings"

	"golang.org/x/crypto/pbkdf2"
)

// GenerateMnemonic creates a 24-word BIP39 mnemonic from 32 bytes of entropy.
func GenerateMnemonic() (string, error) {
	entropy := make([]byte, 32)
	if _, err := rand.Read(entropy); err != nil {
		return "", err
	}
	return MnemonicFromEntropy(entropy)
}

// MnemonicFromEntropy encodes entropy bytes as a BIP39 mnemonic.
func MnemonicFromEntropy(entropy []byte) (string, error) {
	entropyBits := len(entropy) * 8
	if entropyBits%32 != 0 {
		return "", fmt.Errorf("entropy must be a multiple of 32 bits")
	}
	if entropyBits < 128 || entropyBits > 256 {
		return "", fmt.Errorf("entropy must be 128-256 bits")
	}

	hash := sha512.Sum512(entropy)
	checksumBits := entropyBits / 32

	totalBits := entropyBits + checksumBits
	combined := bitsFromBytes(entropy, totalBits)

	// Add checksum bits
	for i := 0; i < checksumBits; i++ {
		bit := int((hash[i/8] >> (7 - i%8)) & 1)
		combined[entropyBits+i] = bit
	}

	var words []string
	for i := 0; i < totalBits/11; i++ {
		idx := bitsToUint(combined, i*11, 11)
		words = append(words, WordList[idx])
	}

	return strings.Join(words, " "), nil
}

// EntropyFromMnemonic decodes a BIP39 mnemonic back to entropy bytes.
func EntropyFromMnemonic(mnemonic string) ([]byte, error) {
	words := strings.Fields(mnemonic)
	if len(words) < 12 || len(words) > 24 || len(words)%3 != 0 {
		return nil, fmt.Errorf("invalid mnemonic length: %d", len(words))
	}

	wordMap := make(map[string]int, len(WordList))
	for i, w := range WordList {
		wordMap[w] = i
	}

	totalBits := len(words) * 11
	combined := make([]int, totalBits)

	for i, w := range words {
		idx, ok := wordMap[w]
		if !ok {
			return nil, fmt.Errorf("unknown word: %q", w)
		}
		for j := 0; j < 11; j++ {
			bit := (idx >> (10 - j)) & 1
			combined[i*11+j] = bit
		}
	}

	entropyBits := len(words) * 11 / 33 * 32
	entropyBytes := entropyBits / 8

	entropy := make([]byte, entropyBytes)
	for i := 0; i < entropyBits; i++ {
		if combined[i] == 1 {
			entropy[i/8] |= 1 << (7 - i%8)
		}
	}

	// Verify checksum
	hash := sha512.Sum512(entropy)
	checksumBits := entropyBits / 32
	for i := 0; i < checksumBits; i++ {
		combinedBit := combined[entropyBits+i]
		hashBit := int((hash[i/8] >> (7 - i%8)) & 1)
		if combinedBit != hashBit {
			return nil, fmt.Errorf("checksum mismatch: invalid mnemonic")
		}
	}

	return entropy, nil
}

// KeypairFromMnemonic derives an Ed25519 keypair from a BIP39 mnemonic + passphrase.
func KeypairFromMnemonic(mnemonic, passphrase string) (pubkeyHex, privkeyHex string, err error) {
	seed := PBKDF2([]byte(mnemonic), []byte("mnemonic"+passphrase), 2048, 64, sha512.New)
	priv := ed25519.NewKeyFromSeed(seed[:32])
	pub := priv.Public().(ed25519.PublicKey)
	return hex.EncodeToString(pub), hex.EncodeToString(priv), nil
}

// PBKDF2 wraps golang.org/x/crypto/pbkdf2.
func PBKDF2(password, salt []byte, iter, keyLen int, h func() hash.Hash) []byte {
	return pbkdf2.Key(password, salt, iter, keyLen, h)
}

// bitsFromBytes converts a byte slice to a bit slice (MSB first), padded to totalBits.
func bitsFromBytes(data []byte, totalBits int) []int {
	result := make([]int, totalBits)
	for i := 0; i < len(data)*8 && i < totalBits; i++ {
		result[i] = int((data[i/8] >> (7 - i%8)) & 1)
	}
	return result
}

// bitsToUint converts bits[start:start+count] to a uint (MSB first).
func bitsToUint(bits []int, start, count int) int {
	var result int
	for i := 0; i < count && start+i < len(bits); i++ {
		if bits[start+i] == 1 {
			result |= 1 << (count - 1 - i)
		}
	}
	return result
}
