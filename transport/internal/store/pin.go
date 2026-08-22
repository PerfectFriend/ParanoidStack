// Package store provides persistent storage backends
package store

import (
	"crypto/rand"
	"crypto/sha256"
	"database/sql"
	"encoding/hex"
	"fmt"
	"time"
)

type PINStore struct {
	db *DB
}


// NewPINStore handles the NewPINStore HTTP request.
func NewPINStore(db *DB) *PINStore {
	s := &PINStore{db: db}
	s.db.mu.Lock()
	defer s.db.mu.Unlock()
	s.db.Exec(`CREATE TABLE IF NOT EXISTS pin (
		id INTEGER PRIMARY KEY CHECK (id = 1),
		hash TEXT NOT NULL,
		salt TEXT NOT NULL,
		created_at TEXT NOT NULL,
		updated_at TEXT NOT NULL
	)`)
	return s
}


// SetPIN handles the SetPIN HTTP request.
func (s *PINStore) SetPIN(pin string) error {
	salt := make([]byte, 16)
	if _, err := rand.Read(salt); err != nil {
		return err
	}
	saltHex := hex.EncodeToString(salt)
	hash := hashPIN(pin, saltHex)
	now := formatTime(time.Now())
	_, err := s.db.Exec(`INSERT OR REPLACE INTO pin (id, hash, salt, created_at, updated_at) VALUES (1, ?, ?, ?, ?)`,
		hash, saltHex, now, now)
	return err
}


// VerifyPIN handles the VerifyPIN HTTP request.
func (s *PINStore) VerifyPIN(pin string) (bool, error) {
	var hash, salt string
	err := s.db.QueryRow(`SELECT hash, salt FROM pin WHERE id = 1`).Scan(&hash, &salt)
	if err == sql.ErrNoRows {
		return false, nil
	}
	if err != nil {
		return false, err
	}
	return hashPIN(pin, salt) == hash, nil
}


// HasPIN handles the HasPIN HTTP request.
func (s *PINStore) HasPIN() bool {
	var count int
	if err := s.db.QueryRow(`SELECT COUNT(*) FROM pin WHERE id = 1`).Scan(&count); err != nil {
		return false
	}
	return count > 0
}

func hashPIN(pin, salt string) string {
	h := sha256.Sum256([]byte(fmt.Sprintf("%s:%s", salt, pin)))
	return hex.EncodeToString(h[:])
}
