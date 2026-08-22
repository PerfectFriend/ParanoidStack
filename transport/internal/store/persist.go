// Package store provides persistent storage backends
package store

import (
	"encoding/json"
	"log/slog"
	"os"
	"path/filepath"
	"sync"
)

type KVStore struct {
	mu   sync.RWMutex
	data map[string][]byte
	path string
}


// NewKVStore handles the NewKVStore HTTP request.
func NewKVStore(dir, name string) *KVStore {
	s := &KVStore{
		data: map[string][]byte{},
		path: filepath.Join(dir, name+".json"),
	}
	s.load()
	return s
}

func (s *KVStore) load() {
	data, err := os.ReadFile(s.path)
	if err != nil {
		return
	}
	if err := json.Unmarshal(data, &s.data); err != nil {
		slog.Warn("persist load", "path", s.path, "error", err)
	}
}


// Save handles the Save HTTP request.
func (s *KVStore) Save() {
	s.mu.RLock()
	defer s.mu.RUnlock()
	data, err := json.MarshalIndent(s.data, "", "  ")
	if err != nil {
		slog.Error("persist marshal", "error", err)
		return
	}
	if err := os.WriteFile(s.path, data, 0600); err != nil {
		slog.Error("persist write", "error", err)
	}
}


// Get handles the Get HTTP request.
func (s *KVStore) Get(key string) ([]byte, bool) {
	s.mu.RLock()
	defer s.mu.RUnlock()
	v, ok := s.data[key]
	return v, ok
}


// Set handles the Set HTTP request.
func (s *KVStore) Set(key string, value []byte) {
	s.mu.Lock()
	defer s.mu.Unlock()
	s.data[key] = value
}


// Delete handles the Delete HTTP request.
func (s *KVStore) Delete(key string) {
	s.mu.Lock()
	defer s.mu.Unlock()
	delete(s.data, key)
}


// Keys handles the Keys HTTP request.
func (s *KVStore) Keys() []string {
	s.mu.RLock()
	defer s.mu.RUnlock()
	out := make([]string, 0, len(s.data))
	for k := range s.data {
		out = append(out, k)
	}
	return out
}


// All handles the All HTTP request.
func (s *KVStore) All() map[string][]byte {
	s.mu.RLock()
	defer s.mu.RUnlock()
	out := map[string][]byte{}
	for k, v := range s.data {
		out[k] = v
	}
	return out
}


// SetJSON handles the SetJSON HTTP request.
func (s *KVStore) SetJSON(key string, v interface{}) error {
	data, err := json.Marshal(v)
	if err != nil {
		return err
	}
	s.Set(key, data)
	return nil
}


// GetJSON handles the GetJSON HTTP request.
func (s *KVStore) GetJSON(key string, v interface{}) error {
	data, ok := s.Get(key)
	if !ok {
		return os.ErrNotExist
	}
	return json.Unmarshal(data, v)
}
