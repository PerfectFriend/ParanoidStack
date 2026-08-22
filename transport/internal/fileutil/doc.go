// Package fileutil provides atomic file operations for crash-safe state persistence.
// It offers WriteJSON (atomic JSON serialization via temp file + rename), ReadJSON
// (safe deserialization with fallback to zero value on corruption), and WriteFile
// (generic atomic write). All write operations use a .tmp intermediate file with
// os.Rename to prevent partial or corrupt writes on process crash.
package fileutil
