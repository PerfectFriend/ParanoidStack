// Package store provides persistent storage backends
package store

import (
	"database/sql"
	"encoding/json"
	"sync"
	"time"

	_ "modernc.org/sqlite"
)

type StoredMessage struct {
	ID        string `json:"id"`
	ChatID    string `json:"chat_id"`
	Sender    string `json:"sender"`
	Text      string `json:"text"`
	Timestamp string `json:"timestamp"`
	Status    string `json:"status"`
	Metadata  string `json:"metadata"`
	IsUser    bool   `json:"is_user"`
	Pinned    bool   `json:"pinned"`
}

type ChatStore struct {
	db *sql.DB
	mu sync.RWMutex
}


// NewChatStore handles the NewChatStore HTTP request.
func NewChatStore(path string) (*ChatStore, error) {
	db, err := sql.Open("sqlite", path+"?_journal_mode=WAL&_synchronous=NORMAL")
	if err != nil {
		return nil, err
	}
	db.SetMaxOpenConns(1)

	s := &ChatStore{db: db}
	if err := s.createTables(); err != nil {
		db.Close()
		return nil, err
	}
	return s, nil
}

func (s *ChatStore) createTables() error {
	s.mu.Lock()
	defer s.mu.Unlock()

	tx, err := s.db.Begin()
	if err != nil {
		return err
	}
	defer tx.Rollback()

	statements := []string{
		`CREATE TABLE IF NOT EXISTS messages (
			id TEXT PRIMARY KEY,
			chat_id TEXT NOT NULL,
			sender TEXT NOT NULL DEFAULT '',
			text TEXT NOT NULL DEFAULT '',
			timestamp TEXT NOT NULL,
			status TEXT NOT NULL DEFAULT 'sent',
			metadata TEXT NOT NULL DEFAULT '{}',
			is_user INTEGER NOT NULL DEFAULT 0,
			pinned INTEGER NOT NULL DEFAULT 0
		)`,
		`CREATE INDEX IF NOT EXISTS idx_messages_chat_id ON messages(chat_id)`,
		`CREATE INDEX IF NOT EXISTS idx_messages_timestamp ON messages(timestamp)`,
		`CREATE INDEX IF NOT EXISTS idx_messages_sender ON messages(sender)`,
		`CREATE TABLE IF NOT EXISTS contacts (
			id TEXT PRIMARY KEY,
			alias TEXT NOT NULL DEFAULT '',
			group_name TEXT NOT NULL DEFAULT '',
			created_at TEXT NOT NULL
		)`,
	}

	for _, stmt := range statements {
		if _, err := tx.Exec(stmt); err != nil {
			return err
		}
	}

	return tx.Commit()
}


// AddMessage handles the AddMessage HTTP request.
func (s *ChatStore) AddMessage(msg StoredMessage) error {
	s.mu.Lock()
	defer s.mu.Unlock()

	_, err := s.db.Exec(
		`INSERT OR REPLACE INTO messages (id, chat_id, sender, text, timestamp, status, metadata, is_user, pinned)
		VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)`,
		msg.ID, msg.ChatID, msg.Sender, msg.Text, msg.Timestamp,
		msg.Status, msg.Metadata, boolToInt(msg.IsUser), boolToInt(msg.Pinned),
	)
	return err
}


// GetMessages handles the GetMessages HTTP request.
func (s *ChatStore) GetMessages(chatID string, limit, offset int) ([]StoredMessage, error) {
	s.mu.RLock()
	defer s.mu.RUnlock()

	rows, err := s.db.Query(
		`SELECT id, chat_id, sender, text, timestamp, status, metadata, is_user, pinned
		FROM messages WHERE chat_id = ?
		ORDER BY timestamp DESC LIMIT ? OFFSET ?`,
		chatID, limit, offset,
	)
	if err != nil {
		return nil, err
	}
	defer rows.Close()

	var msgs []StoredMessage
	for rows.Next() {
		var m StoredMessage
		var isUser, pinned int
		if err := rows.Scan(&m.ID, &m.ChatID, &m.Sender, &m.Text, &m.Timestamp,
			&m.Status, &m.Metadata, &isUser, &pinned); err != nil {
			return nil, err
		}
		m.IsUser = intToBool(isUser)
		m.Pinned = intToBool(pinned)
		msgs = append(msgs, m)
	}
	if msgs == nil {
		msgs = []StoredMessage{}
	}
	return msgs, rows.Err()
}


// GetAllMessages handles the GetAllMessages HTTP request.
func (s *ChatStore) GetAllMessages() ([]StoredMessage, error) {
	s.mu.RLock()
	defer s.mu.RUnlock()

	rows, err := s.db.Query(
		`SELECT id, chat_id, sender, text, timestamp, status, metadata, is_user, pinned
		FROM messages ORDER BY timestamp ASC`,
	)
	if err != nil {
		return nil, err
	}
	defer rows.Close()

	var msgs []StoredMessage
	for rows.Next() {
		var m StoredMessage
		var isUser, pinned int
		if err := rows.Scan(&m.ID, &m.ChatID, &m.Sender, &m.Text, &m.Timestamp,
			&m.Status, &m.Metadata, &isUser, &pinned); err != nil {
			return nil, err
		}
		m.IsUser = intToBool(isUser)
		m.Pinned = intToBool(pinned)
		msgs = append(msgs, m)
	}
	if msgs == nil {
		msgs = []StoredMessage{}
	}
	return msgs, rows.Err()
}


// SearchMessages handles the SearchMessages HTTP request.
func (s *ChatStore) SearchMessages(query string, limit int) ([]StoredMessage, error) {
	s.mu.RLock()
	defer s.mu.RUnlock()

	rows, err := s.db.Query(
		`SELECT id, chat_id, sender, text, timestamp, status, metadata, is_user, pinned
		FROM messages WHERE text LIKE ? OR sender LIKE ?
		ORDER BY timestamp DESC LIMIT ?`,
		"%"+query+"%", "%"+query+"%", limit,
	)
	if err != nil {
		return nil, err
	}
	defer rows.Close()

	var msgs []StoredMessage
	for rows.Next() {
		var m StoredMessage
		var isUser, pinned int
		if err := rows.Scan(&m.ID, &m.ChatID, &m.Sender, &m.Text, &m.Timestamp,
			&m.Status, &m.Metadata, &isUser, &pinned); err != nil {
			return nil, err
		}
		m.IsUser = intToBool(isUser)
		m.Pinned = intToBool(pinned)
		msgs = append(msgs, m)
	}
	if msgs == nil {
		msgs = []StoredMessage{}
	}
	return msgs, rows.Err()
}


// DeleteMessage handles the DeleteMessage HTTP request.
func (s *ChatStore) DeleteMessage(id string) error {
	s.mu.Lock()
	defer s.mu.Unlock()
	_, err := s.db.Exec(`DELETE FROM messages WHERE id = ?`, id)
	return err
}


// DeleteChatMessages handles the DeleteChatMessages HTTP request.
func (s *ChatStore) DeleteChatMessages(chatID string) error {
	s.mu.Lock()
	defer s.mu.Unlock()
	_, err := s.db.Exec(`DELETE FROM messages WHERE chat_id = ?`, chatID)
	return err
}


// DeleteOldMessages handles the DeleteOldMessages HTTP request.
func (s *ChatStore) DeleteOldMessages(before time.Time) (int64, error) {
	s.mu.Lock()
	defer s.mu.Unlock()
	res, err := s.db.Exec(`DELETE FROM messages WHERE timestamp < ?`,
		before.UTC().Format(time.RFC3339))
	if err != nil {
		return 0, err
	}
	return res.RowsAffected()
}


// ClearAll handles the ClearAll HTTP request.
func (s *ChatStore) ClearAll() error {
	s.mu.Lock()
	defer s.mu.Unlock()
	_, err := s.db.Exec(`DELETE FROM messages`)
	return err
}


// UpdateMessage handles the UpdateMessage HTTP request.
func (s *ChatStore) UpdateMessage(id string, updates map[string]any) error {
	s.mu.Lock()
	defer s.mu.Unlock()

	setClauses := ""
	args := []any{}
	for k, v := range updates {
		if setClauses != "" {
			setClauses += ", "
		}
		switch k {
		case "text":
			setClauses += "text = ?"
		case "status":
			setClauses += "status = ?"
		case "pinned":
			setClauses += "pinned = ?"
			v = boolToInt(v.(bool))
		case "metadata":
			setClauses += "metadata = ?"
		default:
			continue
		}
		args = append(args, v)
	}
	if setClauses == "" {
		return nil
	}
	args = append(args, id)
	_, err := s.db.Exec(`UPDATE messages SET `+setClauses+` WHERE id = ?`, args...)
	return err
}


// MessageCount handles the MessageCount HTTP request.
func (s *ChatStore) MessageCount() int {
	s.mu.RLock()
	defer s.mu.RUnlock()
	var count int
	s.db.QueryRow(`SELECT COUNT(*) FROM messages`).Scan(&count)
	return count
}


// Close handles the Close HTTP request.
func (s *ChatStore) Close() error {
	return s.db.Close()
}


// MarshalMetadata handles the MarshalMetadata HTTP request.
func MarshalMetadata(msg *StoredMessage, v any) error {
	b, err := json.Marshal(v)
	if err != nil {
		return err
	}
	msg.Metadata = string(b)
	return nil
}


// UnmarshalMetadata handles the UnmarshalMetadata HTTP request.
func UnmarshalMetadata(msg *StoredMessage, v any) error {
	return json.Unmarshal([]byte(msg.Metadata), v)
}
