// Package store provides persistent storage backends
package store

import (
	"encoding/json"
	"log/slog"
	"os"
	"path/filepath"
	"sync"
)

type ChatPersistence struct {
	dir string
	mu  sync.RWMutex
}


// NewChatPersistence handles the NewChatPersistence HTTP request.
func NewChatPersistence(dataDir string) *ChatPersistence {
	return &ChatPersistence{dir: dataDir}
}

func (cp *ChatPersistence) path(name string) string {
	return filepath.Join(cp.dir, name+".json")
}


// Save handles the Save HTTP request.
func (cp *ChatPersistence) Save(name string, v interface{}) {
	data, err := json.MarshalIndent(v, "", "  ")
	if err != nil {
		slog.Error("persist save", "name", name, "error", err)
		return
	}
	if err := os.WriteFile(cp.path(name), data, 0600); err != nil {
		slog.Error("persist write", "name", name, "error", err)
	}
}


// Load handles the Load HTTP request.
func (cp *ChatPersistence) Load(name string, v interface{}) error {
	data, err := os.ReadFile(cp.path(name))
	if err != nil {
		return err
	}
	return json.Unmarshal(data, v)
}
