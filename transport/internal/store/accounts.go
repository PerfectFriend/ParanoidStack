// Package store provides persistent storage backends
package store

import "time"

type Account struct {
	Pubkey    string     `json:"pubkey"`
	Privkey   string     `json:"privkey"`
	Mnemonic  string     `json:"mnemonic"`
	CreatedAt time.Time  `json:"created_at"`
	LastSeen  *time.Time `json:"last_seen_at,omitempty"`
}


// SaveAccount handles the SaveAccount HTTP request.
func (s *DB) SaveAccount(a *Account) error {
	s.mu.Lock()
	defer s.mu.Unlock()
	lastSeen := ""
	if a.LastSeen != nil {
		lastSeen = formatTime(*a.LastSeen)
	}
	_, err := s.Exec(`INSERT OR REPLACE INTO accounts (pubkey, privkey, mnemonic, created_at, last_seen_at) VALUES (?, ?, ?, ?, ?)`,
		a.Pubkey, a.Privkey, a.Mnemonic, formatTime(a.CreatedAt), lastSeen)
	return err
}


// GetAccount handles the GetAccount HTTP request.
func (s *DB) GetAccount(pubkey string) (*Account, error) {
	s.mu.RLock()
	defer s.mu.RUnlock()
	var a Account
	var createdAt, lastSeen string
	err := s.QueryRow(`SELECT pubkey, privkey, mnemonic, created_at, last_seen_at FROM accounts WHERE pubkey = ?`, pubkey).
		Scan(&a.Pubkey, &a.Privkey, &a.Mnemonic, &createdAt, &lastSeen)
	if err != nil {
		return nil, err
	}
	a.CreatedAt, _ = parseTime(createdAt)
	if lastSeen != "" {
		t, err := parseTime(lastSeen)
		if err == nil {
			a.LastSeen = &t
		}
	}
	return &a, nil
}


// ListAccounts handles the ListAccounts HTTP request.
func (s *DB) ListAccounts() ([]Account, error) {
	s.mu.RLock()
	defer s.mu.RUnlock()
	rows, err := s.Query(`SELECT pubkey, privkey, mnemonic, created_at, last_seen_at FROM accounts ORDER BY created_at DESC`)
	if err != nil {
		return nil, err
	}
	defer rows.Close()
	var out []Account
	for rows.Next() {
		var a Account
		var createdAt, lastSeen string
		if err := rows.Scan(&a.Pubkey, &a.Privkey, &a.Mnemonic, &createdAt, &lastSeen); err != nil {
			return nil, err
		}
		a.CreatedAt, _ = parseTime(createdAt)
		if lastSeen != "" {
			t, err := parseTime(lastSeen)
			if err == nil {
				a.LastSeen = &t
			}
		}
		out = append(out, a)
	}
	return out, rows.Err()
}
