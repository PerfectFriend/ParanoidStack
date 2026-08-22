// Package bip39 implements BIP39 mnemonic phrase generation and validation
package bip39

import (
	"strings"
	"testing"
)


// TestGenerateMnemonic handles the TestGenerateMnemonic HTTP request.
func TestGenerateMnemonic(t *testing.T) {
	mnemonic, err := GenerateMnemonic()
	if err != nil {
		t.Fatal(err)
	}
	words := strings.Fields(mnemonic)
	if len(words) != 24 {
		t.Fatalf("expected 24 words, got %d", len(words))
	}
}


// TestEntropyRoundTrip handles the TestEntropyRoundTrip HTTP request.
func TestEntropyRoundTrip(t *testing.T) {
	mnemonic, err := GenerateMnemonic()
	if err != nil {
		t.Fatal(err)
	}
	entropy, err := EntropyFromMnemonic(mnemonic)
	if err != nil {
		t.Fatal(err)
	}
	mnemonic2, err := MnemonicFromEntropy(entropy)
	if err != nil {
		t.Fatal(err)
	}
	if mnemonic != mnemonic2 {
		t.Fatalf("round trip failed:\n  original: %s\n  decoded:  %s", mnemonic, mnemonic2)
	}
}


// TestKeypairFromMnemonic handles the TestKeypairFromMnemonic HTTP request.
func TestKeypairFromMnemonic(t *testing.T) {
	mnemonic, err := GenerateMnemonic()
	if err != nil {
		t.Fatal(err)
	}
	pub1, priv1, err := KeypairFromMnemonic(mnemonic, "")
	if err != nil {
		t.Fatal(err)
	}
	if len(pub1) != 64 {
		t.Fatalf("expected 64-char pubkey, got %d", len(pub1))
	}
	if len(priv1) != 128 {
		t.Fatalf("expected 128-char privkey, got %d", len(priv1))
	}
	// Deterministic: same mnemonic → same keypair
	pub2, priv2, err := KeypairFromMnemonic(mnemonic, "")
	if err != nil {
		t.Fatal(err)
	}
	if pub1 != pub2 {
		t.Fatal("expected deterministic pubkey")
	}
	if priv1 != priv2 {
		t.Fatal("expected deterministic privkey")
	}
}


// TestInvalidMnemonic handles the TestInvalidMnemonic HTTP request.
func TestInvalidMnemonic(t *testing.T) {
	_, err := EntropyFromMnemonic("abandon abandon")
	if err == nil {
		t.Fatal("expected error for short mnemonic")
	}
	_, err = EntropyFromMnemonic("notarealword at all")
	if err == nil {
		t.Fatal("expected error for unknown word")
	}
}


// TestPassphraseChangesKey handles the TestPassphraseChangesKey HTTP request.
func TestPassphraseChangesKey(t *testing.T) {
	mnemonic, _ := GenerateMnemonic()
	pub1, _, _ := KeypairFromMnemonic(mnemonic, "")
	pub2, _, _ := KeypairFromMnemonic(mnemonic, "passphrase")
	if pub1 == pub2 {
		t.Fatal("expected different pubkey with passphrase")
	}
}


// TestMnemonicLengths handles the TestMnemonicLengths HTTP request.
func TestMnemonicLengths(t *testing.T) {
	tests := []struct {
		nBytes int
		words  int
	}{
		{16, 12},
		{20, 15},
		{24, 18},
		{32, 24},
	}
	for _, tt := range tests {
		entropy := make([]byte, tt.nBytes)
		for i := range entropy {
			entropy[i] = byte(i * 17)
		}
		mnemonic, err := MnemonicFromEntropy(entropy)
		if err != nil {
			t.Fatal(err)
		}
		wordList := strings.Fields(mnemonic)
		if len(wordList) != tt.words {
			t.Fatalf("%d bytes: expected %d words, got %d", tt.nBytes, tt.words, len(wordList))
		}
	}
}


// TestWordListSize handles the TestWordListSize HTTP request.
func TestWordListSize(t *testing.T) {
	if len(WordList) != 2048 {
		t.Fatalf("expected 2048 words, got %d", len(WordList))
	}
	// Check first and last word
	if WordList[0] != "abandon" {
		t.Fatalf("expected first word 'abandon', got %q", WordList[0])
	}
	if WordList[2047] != "zoo" {
		t.Fatalf("expected last word 'zoo', got %q", WordList[2047])
	}
}
