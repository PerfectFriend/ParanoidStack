// Package store provides persistent storage backends
package store

import (
	"crypto/aes"
	"crypto/cipher"
	"crypto/rand"
	"crypto/sha256"
	"encoding/hex"
	"errors"
	"io"
)

func deriveKey(pin string) []byte {
	h := sha256.Sum256([]byte(pin))
	return h[:]
}


// EncryptPrivateKey handles the EncryptPrivateKey HTTP request.
func EncryptPrivateKey(privkey string, pin string) (string, error) {
	key := deriveKey(pin)
	block, err := aes.NewCipher(key)
	if err != nil {
		return "", err
	}
	aesGCM, err := cipher.NewGCM(block)
	if err != nil {
		return "", err
	}
	nonce := make([]byte, aesGCM.NonceSize())
	if _, err := io.ReadFull(rand.Reader, nonce); err != nil {
		return "", err
	}
	ciphertext := aesGCM.Seal(nonce, nonce, []byte(privkey), nil)
	return hex.EncodeToString(ciphertext), nil
}


// DecryptPrivateKey handles the DecryptPrivateKey HTTP request.
func DecryptPrivateKey(encrypted string, pin string) (string, error) {
	key := deriveKey(pin)
	block, err := aes.NewCipher(key)
	if err != nil {
		return "", err
	}
	aesGCM, err := cipher.NewGCM(block)
	if err != nil {
		return "", err
	}
	ciphertext, err := hex.DecodeString(encrypted)
	if err != nil {
		return "", err
	}
	nonceSize := aesGCM.NonceSize()
	if len(ciphertext) < nonceSize {
		return "", errors.New("ciphertext too short")
	}
	nonce, ciphertext := ciphertext[:nonceSize], ciphertext[nonceSize:]
	plaintext, err := aesGCM.Open(nil, nonce, ciphertext, nil)
	if err != nil {
		return "", err
	}
	return string(plaintext), nil
}
