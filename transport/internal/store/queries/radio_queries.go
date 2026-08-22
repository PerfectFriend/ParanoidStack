// Package queries provides SQLite query helpers
package queries

const (
	InsertStation = `INSERT OR REPLACE INTO stations (id, name, type, lang, description, icon, enabled, created_at, track_count) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)`

	SelectAllStations = `SELECT id, name, type, lang, description, icon, enabled, created_at, track_count FROM stations ORDER BY name`

	SelectStation = `SELECT id, name, type, lang, description, icon, enabled, created_at, track_count FROM stations WHERE id = ?`

	DeleteStation = `DELETE FROM stations WHERE id = ?`

	InsertAnnouncement = `INSERT OR REPLACE INTO announcements (id, announcer, title, body, lang, priority, stations, audio_file, paid, paid_amount_ng, created_at, scheduled_at, played_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)`

	SelectPendingAnnouncements = `SELECT id, announcer, title, body, lang, priority, stations, audio_file, paid, paid_amount_ng, created_at, scheduled_at, played_at FROM announcements WHERE played_at IS NULL ORDER BY priority DESC, created_at ASC`

	MarkAnnouncementPlayed = `UPDATE announcements SET played_at = ? WHERE id = ? AND played_at IS NULL`
)
