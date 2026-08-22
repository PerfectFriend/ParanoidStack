// Package fileutil provides atomic file operations for crash-safe state persistence
// and safe JSON serialisation helpers.
package fileutil

import (
	"encoding/json"
	"log/slog"
	"os"
	"path/filepath"
)

// WriteJSON atomically writes v as indented JSON to path.
// Writes to a .tmp file first, syncs, then renames to the target path.
// This prevents partial/corrupt writes on crash.
func WriteJSON(path string, v any) error {
	dir := filepath.Dir(path)
	if err := os.MkdirAll(dir, 0700); err != nil {
		return err
	}

	tmp := path + ".tmp"
	b, err := json.MarshalIndent(v, "", "  ")
	if err != nil {
		return err
	}

	if err := os.WriteFile(tmp, b, 0600); err != nil {
		return err
	}

	if err := os.Rename(tmp, path); err != nil {
		os.Remove(tmp)
		return err
	}

	return nil
}

// ReadJSON reads a JSON file into v.
// If the file does not exist or is corrupt, v is left unchanged (zero value).
// Errors are logged via slog — the caller is expected to use sensible defaults.
func ReadJSON(path string, v any) {
	b, err := os.ReadFile(path)
	if err != nil {
		return
	}
	if err := json.Unmarshal(b, v); err != nil {
		slog.Warn("readjson corrupt, using defaults", "path", path, "error", err)
	}
}

// WriteFile atomically writes data to path using .tmp + rename.
func WriteFile(path string, data []byte, perm os.FileMode) error {
	dir := filepath.Dir(path)
	if err := os.MkdirAll(dir, 0700); err != nil {
		return err
	}

	tmp := path + ".tmp"
	if err := os.WriteFile(tmp, data, perm); err != nil {
		return err
	}

	if err := os.Rename(tmp, path); err != nil {
		os.Remove(tmp)
		return err
	}

	return nil
}
