// Package store provides persistent storage backends
package store

import (
	"encoding/json"
	"log"
	"os"
	"path/filepath"
	"time"
)

type stationJSON struct {
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

type announcementJSON struct {
	ID         string     `json:"id"`
	Announcer  string     `json:"announcer"`
	Title      string     `json:"title"`
	Body       string     `json:"body"`
	Lang       string     `json:"lang"`
	Priority   int        `json:"priority"`
	Stations   []string   `json:"stations,omitempty"`
	AudioFile  string     `json:"audio_file,omitempty"`
	Paid       bool       `json:"paid"`
	PaidAmount int64      `json:"paid_amount_ng,omitempty"`
	CreatedAt  time.Time  `json:"created_at"`
	ScheduledAt *time.Time `json:"scheduled_at,omitempty"`
	PlayedAt   *time.Time `json:"played_at,omitempty"`
}


// MigrateFromJSON handles the MigrateFromJSON HTTP request.
func (s *DB) MigrateFromJSON(dataDir string) error {
	s.mu.Lock()
	defer s.mu.Unlock()

	if err := s.migrateStations(filepath.Join(dataDir, "radio", "stations.json")); err != nil {
		return err
	}
	if err := s.migrateAnnouncements(filepath.Join(dataDir, "radio", "announcements.json")); err != nil {
		return err
	}
	log.Printf("store: migration from JSON complete (dataDir=%s)", dataDir)
	return nil
}

func (s *DB) migrateStations(path string) error {
	b, err := os.ReadFile(path)
	if err != nil {
		log.Printf("store: no stations.json at %s, skipping (%v)", path, err)
		return nil
	}
	var stations []stationJSON
	if err := json.Unmarshal(b, &stations); err != nil {
		return err
	}

	var count int
	for _, st := range stations {
		_, err := s.Exec(`INSERT OR REPLACE INTO stations (id, name, type, lang, description, icon, enabled, created_at, track_count) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)`,
			st.ID, st.Name, st.Type, st.Lang, st.Description, st.Icon, boolToInt(st.Enabled), formatTime(st.CreatedAt), st.TrackCount)
		if err != nil {
			return err
		}
		count++
	}
	log.Printf("store: migrated %d stations from %s", count, path)
	return nil
}

func (s *DB) migrateAnnouncements(path string) error {
	b, err := os.ReadFile(path)
	if err != nil {
		log.Printf("store: no announcements.json at %s, skipping (%v)", path, err)
		return nil
	}
	var announcements []announcementJSON
	if err := json.Unmarshal(b, &announcements); err != nil {
		return err
	}

	var count int
	for _, a := range announcements {
		stationsJSON, _ := json.Marshal(a.Stations)
		scheduledStr := ""
		if a.ScheduledAt != nil {
			scheduledStr = formatTime(*a.ScheduledAt)
		}
		playedStr := ""
		if a.PlayedAt != nil {
			playedStr = formatTime(*a.PlayedAt)
		}
		_, err := s.Exec(`INSERT OR REPLACE INTO announcements (id, announcer, title, body, lang, priority, stations, audio_file, paid, paid_amount_ng, created_at, scheduled_at, played_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)`,
			a.ID, a.Announcer, a.Title, a.Body, a.Lang, a.Priority, string(stationsJSON), a.AudioFile, boolToInt(a.Paid), a.PaidAmount, formatTime(a.CreatedAt), scheduledStr, playedStr)
		if err != nil {
			return err
		}
		count++
	}
	log.Printf("store: migrated %d announcements from %s", count, path)
	return nil
}
