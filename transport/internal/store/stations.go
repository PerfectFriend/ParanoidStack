// Package store provides persistent storage backends
package store

import (
	"time"
)

type Station struct {
	ID         string    `json:"id"`
	Name       string    `json:"name"`
	Type       string    `json:"type"`
	Lang       string    `json:"lang"`
	Description string   `json:"description"`
	Icon       string    `json:"icon,omitempty"`
	Enabled    bool      `json:"enabled"`
	CreatedAt  time.Time `json:"created_at"`
	TrackCount int       `json:"track_count"`
}


// ListStations handles the ListStations HTTP request.
func (s *DB) ListStations() ([]Station, error) {
	s.mu.RLock()
	defer s.mu.RUnlock()

	rows, err := s.Query(`SELECT id, name, type, lang, description, icon, enabled, created_at, track_count FROM stations ORDER BY name`)
	if err != nil {
		return nil, err
	}
	defer rows.Close()

	var out []Station
	for rows.Next() {
		var st Station
		var createdAt string
		if err := rows.Scan(&st.ID, &st.Name, &st.Type, &st.Lang, &st.Description, &st.Icon, &st.Enabled, &createdAt, &st.TrackCount); err != nil {
			return nil, err
		}
		st.CreatedAt, _ = parseTime(createdAt)
		out = append(out, st)
	}
	return out, rows.Err()
}


// GetStation handles the GetStation HTTP request.
func (s *DB) GetStation(id string) (*Station, error) {
	s.mu.RLock()
	defer s.mu.RUnlock()

	var st Station
	var createdAt string
	err := s.QueryRow(`SELECT id, name, type, lang, description, icon, enabled, created_at, track_count FROM stations WHERE id = ?`, id).
		Scan(&st.ID, &st.Name, &st.Type, &st.Lang, &st.Description, &st.Icon, &st.Enabled, &createdAt, &st.TrackCount)
	if err != nil {
		return nil, err
	}
	st.CreatedAt, _ = parseTime(createdAt)
	return &st, nil
}


// SaveStation handles the SaveStation HTTP request.
func (s *DB) SaveStation(st *Station) error {
	s.mu.Lock()
	defer s.mu.Unlock()

	_, err := s.Exec(`INSERT OR REPLACE INTO stations (id, name, type, lang, description, icon, enabled, created_at, track_count) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)`,
		st.ID, st.Name, st.Type, st.Lang, st.Description, st.Icon, boolToInt(st.Enabled), formatTime(st.CreatedAt), st.TrackCount)
	return err
}


// DeleteStation handles the DeleteStation HTTP request.
func (s *DB) DeleteStation(id string) error {
	s.mu.Lock()
	defer s.mu.Unlock()

	_, err := s.Exec(`DELETE FROM stations WHERE id = ?`, id)
	return err
}
