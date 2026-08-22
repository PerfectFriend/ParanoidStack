// Package store provides persistent storage backends
package store

import "fmt"

// WalletStore extends Account storage with transparent
// AES-256-GCM encryption of private keys.
type WalletStore struct {
	db  *DB
	pin *PINStore
}


// NewWalletStore handles the NewWalletStore HTTP request.
func NewWalletStore(db *DB, pin *PINStore) *WalletStore {
	return &WalletStore{db: db, pin: pin}
}


func (ws *WalletStore) SaveAccountWithPIN(a *Account, pin string) error {
	enc, err := EncryptPrivateKey(a.Privkey, pin)
	if err != nil {
		return fmt.Errorf("wallet encrypt: %w", err)
	}
	a.Privkey = enc
	return ws.db.SaveAccount(a)
}

func (ws *WalletStore) GetAccountWithPIN(pubkey, pin string) (*Account, error) {
	a, err := ws.db.GetAccount(pubkey)
	if err != nil {
		return nil, err
	}
	dec, err := DecryptPrivateKey(a.Privkey, pin)
	if err != nil {
		return nil, fmt.Errorf("wallet decrypt: %w", err)
	}
	a.Privkey = dec
	return a, nil
}

func isEncrypted(s string) bool {
	if len(s) < 60 {
		return false
	}
	for _, c := range s {
		if !((c >= '0' && c <= '9') || (c >= 'a' && c <= 'f')) {
			return false
		}
	}
	return true
}
