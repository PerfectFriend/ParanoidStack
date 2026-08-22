// Package store provides persistent storage backends
package store

import (
	"database/sql"
	"log"
	"sync"
	"time"

	_ "modernc.org/sqlite"
)

type DB struct {
	*sql.DB
	mu sync.RWMutex
}


// Open handles the Open HTTP request.
func Open(path string) (*DB, error) {
	db, err := sql.Open("sqlite", path+"?_journal_mode=WAL&_synchronous=NORMAL")
	if err != nil {
		return nil, err
	}
	db.SetMaxOpenConns(1)
	s := &DB{DB: db}
	if err := s.migrate(); err != nil {
		db.Close()
		return nil, err
	}
	return s, nil
}

func (s *DB) migrate() error {
	s.mu.Lock()
	defer s.mu.Unlock()

	tx, err := s.Begin()
	if err != nil {
		return err
	}
	defer tx.Rollback()

	statements := []string{
		`CREATE TABLE IF NOT EXISTS schema_version (
			version INTEGER PRIMARY KEY,
			applied_at TEXT NOT NULL
		)`,
		`CREATE TABLE IF NOT EXISTS stations (
			id TEXT PRIMARY KEY,
			name TEXT NOT NULL,
			type TEXT NOT NULL DEFAULT 'music',
			lang TEXT NOT NULL DEFAULT 'en',
			description TEXT NOT NULL DEFAULT '',
			icon TEXT NOT NULL DEFAULT '',
			enabled INTEGER NOT NULL DEFAULT 1,
			created_at TEXT NOT NULL,
			track_count INTEGER NOT NULL DEFAULT 0
		)`,
		`CREATE TABLE IF NOT EXISTS announcements (
			id TEXT PRIMARY KEY,
			announcer TEXT NOT NULL,
			title TEXT NOT NULL,
			body TEXT NOT NULL,
			lang TEXT NOT NULL DEFAULT 'en',
			priority INTEGER NOT NULL DEFAULT 1,
			stations TEXT NOT NULL DEFAULT '',
			audio_file TEXT NOT NULL DEFAULT '',
			paid INTEGER NOT NULL DEFAULT 0,
			paid_amount_ng INTEGER NOT NULL DEFAULT 0,
			created_at TEXT NOT NULL,
			scheduled_at TEXT,
			played_at TEXT
		)`,
		`CREATE TABLE IF NOT EXISTS accounts (
			pubkey TEXT PRIMARY KEY,
			privkey TEXT NOT NULL,
			mnemonic TEXT NOT NULL,
			created_at TEXT NOT NULL,
			last_seen_at TEXT
		)`,
		`CREATE TABLE IF NOT EXISTS playlist_state (
			id TEXT PRIMARY KEY,
			station_id TEXT NOT NULL,
			playlist TEXT NOT NULL,
			current_index INTEGER NOT NULL DEFAULT 0,
			shuffled INTEGER NOT NULL DEFAULT 1,
			updated_at TEXT NOT NULL,
			FOREIGN KEY (station_id) REFERENCES stations(id)
		)`,
		`CREATE TABLE IF NOT EXISTS keys (
			id TEXT PRIMARY KEY,
			purpose TEXT NOT NULL,
			pubkey TEXT NOT NULL,
			privkey_encrypted BLOB NOT NULL,
			created_at TEXT NOT NULL
		)`,
	}

	for _, stmt := range statements {
		if _, err := tx.Exec(stmt); err != nil {
			return err
		}
	}

	if _, err := tx.Exec(`INSERT OR IGNORE INTO schema_version (version, applied_at) VALUES (1, ?)`, time.Now().UTC().Format(time.RFC3339)); err != nil {
		return err
	}

	return tx.Commit()
}


// Close handles the Close HTTP request.
func (s *DB) Close() error {
	return s.DB.Close()
}

func formatTime(t time.Time) string {
	return t.UTC().Format(time.RFC3339)
}

func parseTime(s string) (time.Time, error) {
	return time.Parse(time.RFC3339, s)
}

func boolToInt(b bool) int {
	if b {
		return 1
	}
	return 0
}

func intToBool(i int) bool {
	return i != 0
}

// EnsureSchemaVersion logs current schema version.
func (s *DB) EnsureSchemaVersion() {
	var version int
	var appliedAt string
	err := s.QueryRow(`SELECT version, applied_at FROM schema_version ORDER BY version DESC LIMIT 1`).Scan(&version, &appliedAt)
	if err != nil {
		log.Printf("store: no schema version found: %v", err)
		return
	}
	log.Printf("store: schema version %d applied at %s", version, appliedAt)
}
