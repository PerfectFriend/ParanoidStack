package store

import (
	"fmt"
	"time"
)

type ExternalWallet struct {
	ID             string    `json:"id"`
	Pubkey         string    `json:"pubkey"`
	WalletType     string    `json:"wallet_type"`
	WalletAddress  string    `json:"wallet_address"`
	Label          string    `json:"label"`
	Chain          string    `json:"chain"`
	IsVerified     bool      `json:"is_verified"`
	CreatedAt      time.Time `json:"created_at"`
	LastSyncAt     string    `json:"last_sync_at,omitempty"`
}

type ExternalWalletStore struct {
	db *DB
}

func NewExternalWalletStore(db *DB) *ExternalWalletStore {
	ews := &ExternalWalletStore{db: db}
	ews.migrate()
	return ews
}

func (ews *ExternalWalletStore) migrate() {
	_, _ = ews.db.Exec(`CREATE TABLE IF NOT EXISTS external_wallets (
		id TEXT PRIMARY KEY,
		pubkey TEXT NOT NULL,
		wallet_type TEXT NOT NULL,
		wallet_address TEXT NOT NULL,
		label TEXT NOT NULL DEFAULT '',
		chain TEXT NOT NULL DEFAULT 'all',
		is_verified INTEGER NOT NULL DEFAULT 0,
		created_at TEXT NOT NULL,
		last_sync_at TEXT
	)`)
	_, _ = ews.db.Exec(`CREATE INDEX IF NOT EXISTS idx_extwallet_pubkey ON external_wallets(pubkey)`)
}

func (ews *ExternalWalletStore) Add(w ExternalWallet) error {
	w.ID = fmt.Sprintf("ew-%d", time.Now().UnixNano())
	if w.Chain == "" {
		w.Chain = "all"
	}
	_, err := ews.db.Exec(`INSERT INTO external_wallets (id, pubkey, wallet_type, wallet_address, label, chain, is_verified, created_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?)`,
		w.ID, w.Pubkey, w.WalletType, w.WalletAddress, w.Label, w.Chain, boolToInt(w.IsVerified), formatTime(time.Now()))
	return err
}

func (ews *ExternalWalletStore) List(pubkey string) ([]ExternalWallet, error) {
	rows, err := ews.db.Query(`SELECT id, pubkey, wallet_type, wallet_address, label, chain, is_verified, created_at, COALESCE(last_sync_at, '') FROM external_wallets WHERE pubkey = ? ORDER BY created_at DESC`, pubkey)
	if err != nil {
		return nil, err
	}
	defer rows.Close()
	var wallets []ExternalWallet
	for rows.Next() {
		var w ExternalWallet
		var isVerified int
		var createdAt string
		if err := rows.Scan(&w.ID, &w.Pubkey, &w.WalletType, &w.WalletAddress, &w.Label, &w.Chain, &isVerified, &createdAt, &w.LastSyncAt); err != nil {
			return nil, err
		}
		w.IsVerified = intToBool(isVerified)
		w.CreatedAt, _ = parseTime(createdAt)
		wallets = append(wallets, w)
	}
	return wallets, rows.Err()
}

func (ews *ExternalWalletStore) Remove(pubkey, walletType string) error {
	_, err := ews.db.Exec(`DELETE FROM external_wallets WHERE pubkey = ? AND wallet_type = ?`, pubkey, walletType)
	return err
}

func (ews *ExternalWalletStore) Verify(pubkey, walletType string) error {
	_, err := ews.db.Exec(`UPDATE external_wallets SET is_verified = 1 WHERE pubkey = ? AND wallet_type = ?`, pubkey, walletType)
	return err
}

func (ews *ExternalWalletStore) UpdateSyncTime(pubkey, walletType string) error {
	_, err := ews.db.Exec(`UPDATE external_wallets SET last_sync_at = ? WHERE pubkey = ? AND wallet_type = ?`, formatTime(time.Now()), pubkey, walletType)
	return err
}
