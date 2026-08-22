// Package fileutil provides atomic file operations and file system utilities
package fileutil

import (
	"encoding/json"
	"os"
	"path/filepath"
	"testing"
)


// TestWriteJSON handles the TestWriteJSON HTTP request.
func TestWriteJSON(t *testing.T) {
	dir, err := os.MkdirTemp("", "fileutil-*")
	if err != nil {
		t.Fatal(err)
	}
	t.Cleanup(func() { os.RemoveAll(dir) })

	path := filepath.Join(dir, "test.json")
	data := map[string]any{"hello": "world", "num": 42}

	if err := WriteJSON(path, data); err != nil {
		t.Fatal("WriteJSON:", err)
	}

	b, err := os.ReadFile(path)
	if err != nil {
		t.Fatal("ReadFile:", err)
	}
	if len(b) == 0 {
		t.Fatal("expected non-empty file")
	}
}


// TestWriteJSONNoTempLeftover handles the TestWriteJSONNoTempLeftover HTTP request.
func TestWriteJSONNoTempLeftover(t *testing.T) {
	dir, err := os.MkdirTemp("", "fileutil-*")
	if err != nil {
		t.Fatal(err)
	}
	t.Cleanup(func() { os.RemoveAll(dir) })

	path := filepath.Join(dir, "clean.json")
	if err := WriteJSON(path, "data"); err != nil {
		t.Fatal(err)
	}

	if _, err := os.Stat(path + ".tmp"); !os.IsNotExist(err) {
		t.Fatal("expected .tmp file to be removed after write")
	}
}


// TestWriteJSONCreatesDir handles the TestWriteJSONCreatesDir HTTP request.
func TestWriteJSONCreatesDir(t *testing.T) {
	dir, err := os.MkdirTemp("", "fileutil-*")
	if err != nil {
		t.Fatal(err)
	}
	t.Cleanup(func() { os.RemoveAll(dir) })

	nestedPath := filepath.Join(dir, "sub", "nested", "file.json")
	if err := WriteJSON(nestedPath, "nested"); err != nil {
		t.Fatal("WriteJSON with nested dirs:", err)
	}

	if _, err := os.Stat(nestedPath); os.IsNotExist(err) {
		t.Fatal("expected file to be created in nested dirs")
	}
}


// TestWriteFile handles the TestWriteFile HTTP request.
func TestWriteFile(t *testing.T) {
	dir, err := os.MkdirTemp("", "fileutil-*")
	if err != nil {
		t.Fatal(err)
	}
	t.Cleanup(func() { os.RemoveAll(dir) })

	path := filepath.Join(dir, "test.bin")
	data := []byte("hello world")

	if err := WriteFile(path, data, 0600); err != nil {
		t.Fatal("WriteFile:", err)
	}

	b, err := os.ReadFile(path)
	if err != nil {
		t.Fatal("ReadFile:", err)
	}
	if string(b) != "hello world" {
		t.Fatalf("got %q, want %q", string(b), "hello world")
	}
}


// TestWriteFilePermission handles the TestWriteFilePermission HTTP request.
func TestWriteFilePermission(t *testing.T) {
	dir, err := os.MkdirTemp("", "fileutil-*")
	if err != nil {
		t.Fatal(err)
	}
	t.Cleanup(func() { os.RemoveAll(dir) })

	path := filepath.Join(dir, "perm.json")
	if err := WriteFile(path, []byte("{}"), 0600); err != nil {
		t.Fatal(err)
	}

	info, err := os.Stat(path)
	if err != nil {
		t.Fatal(err)
	}
	if info.Mode()&0o077 != 0 {
		t.Fatalf("expected no group/other perms, got %v", info.Mode())
	}
}


// TestWriteJSONRoundTrip handles the TestWriteJSONRoundTrip HTTP request.
func TestWriteJSONRoundTrip(t *testing.T) {
	dir, err := os.MkdirTemp("", "fileutil-*")
	if err != nil {
		t.Fatal(err)
	}
	t.Cleanup(func() { os.RemoveAll(dir) })

	type Person struct {
		Name string `json:"name"`
		Age  int    `json:"age"`
	}

	original := Person{Name: "Alice", Age: 30}
	path := filepath.Join(dir, "person.json")

	if err := WriteJSON(path, original); err != nil {
		t.Fatal(err)
	}

	var loaded Person
	if b, err := os.ReadFile(path); err != nil {
		t.Fatal(err)
	} else if err := json.Unmarshal(b, &loaded); err != nil {
		t.Fatal(err)
	}

	if loaded.Name != "Alice" || loaded.Age != 30 {
		t.Fatalf("got %+v, want %+v", loaded, original)
	}
}
