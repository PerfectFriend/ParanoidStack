// Package store provides persistent storage backends
package store

import (
	"time"
)

// VaultFile represents an encrypted file in the vault.
type VaultFile struct {
	ID          string    `json:"id"`
	OwnerPubkey string    `json:"owner_pubkey"`
	Name        string    `json:"name"`
	Size        int64     `json:"size"`
	MimeType    string    `json:"mime_type"`
	Encrypted   bool      `json:"encrypted"`
	Checksum    string    `json:"checksum,omitempty"`
	StoragePath string    `json:"storage_path"`
	CreatedAt   time.Time `json:"created_at"`
	UpdatedAt   time.Time `json:"updated_at"`
}

// VaultStore manages encrypted file metadata via SQLite.
type VaultStore struct {
	db *DB
}


// NewVaultStore handles the NewVaultStore HTTP request.
func NewVaultStore(db *DB) *VaultStore {
	s := &VaultStore{db: db}
	s.db.mu.Lock()
	defer s.db.mu.Unlock()
	s.db.Exec(`CREATE TABLE IF NOT EXISTS vault_files (
		id TEXT PRIMARY KEY,
		owner_pubkey TEXT NOT NULL,
		name TEXT NOT NULL,
		size INTEGER NOT NULL DEFAULT 0,
		mime_type TEXT NOT NULL DEFAULT 'application/octet-stream',
		encrypted INTEGER NOT NULL DEFAULT 0,
		checksum TEXT NOT NULL DEFAULT '',
		storage_path TEXT NOT NULL,
		created_at TEXT NOT NULL,
		updated_at TEXT NOT NULL
	)`)
	return s
}


// Store handles the Store HTTP request.
func (s *VaultStore) Store(f *VaultFile) error {
	s.db.mu.Lock()
	defer s.db.mu.Unlock()
	_, err := s.db.Exec(`INSERT OR REPLACE INTO vault_files (id, owner_pubkey, name, size, mime_type, encrypted, checksum, storage_path, created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)`,
		f.ID, f.OwnerPubkey, f.Name, f.Size, f.MimeType, boolToInt(f.Encrypted), f.Checksum, f.StoragePath, formatTime(f.CreatedAt), formatTime(f.UpdatedAt))
	return err
}


// Get handles the Get HTTP request.
func (s *VaultStore) Get(id string) (*VaultFile, error) {
	s.db.mu.RLock()
	defer s.db.mu.RUnlock()
	var f VaultFile
	var createdAt, updatedAt string
	err := s.db.QueryRow(`SELECT id, owner_pubkey, name, size, mime_type, encrypted, checksum, storage_path, created_at, updated_at FROM vault_files WHERE id = ?`, id).
		Scan(&f.ID, &f.OwnerPubkey, &f.Name, &f.Size, &f.MimeType, &f.Encrypted, &f.Checksum, &f.StoragePath, &createdAt, &updatedAt)
	if err != nil {
		return nil, err
	}
	f.CreatedAt, _ = parseTime(createdAt)
	f.UpdatedAt, _ = parseTime(updatedAt)
	return &f, nil
}


// List handles the List HTTP request.
func (s *VaultStore) List(ownerPubkey string) ([]VaultFile, error) {
	s.db.mu.RLock()
	defer s.db.mu.RUnlock()
	rows, err := s.db.Query(`SELECT id, owner_pubkey, name, size, mime_type, encrypted, checksum, storage_path, created_at, updated_at FROM vault_files WHERE owner_pubkey = ? ORDER BY updated_at DESC`, ownerPubkey)
	if err != nil {
		return nil, err
	}
	defer rows.Close()
	var out []VaultFile
	for rows.Next() {
		var f VaultFile
		var createdAt, updatedAt string
		if err := rows.Scan(&f.ID, &f.OwnerPubkey, &f.Name, &f.Size, &f.MimeType, &f.Encrypted, &f.Checksum, &f.StoragePath, &createdAt, &updatedAt); err != nil {
			return nil, err
		}
		f.CreatedAt, _ = parseTime(createdAt)
		f.UpdatedAt, _ = parseTime(updatedAt)
		out = append(out, f)
	}
	return out, rows.Err()
}


// Delete handles the Delete HTTP request.
func (s *VaultStore) Delete(id string) error {
	s.db.mu.Lock()
	defer s.db.mu.Unlock()
	_, err := s.db.Exec(`DELETE FROM vault_files WHERE id = ?`, id)
	return err
}


// TotalSize handles the TotalSize HTTP request.
func (s *VaultStore) TotalSize(ownerPubkey string) (int64, error) {
	s.db.mu.RLock()
	defer s.db.mu.RUnlock()
	var total int64
	err := s.db.QueryRow(`SELECT COALESCE(SUM(size), 0) FROM vault_files WHERE owner_pubkey = ?`, ownerPubkey).Scan(&total)
	return total, err
}
