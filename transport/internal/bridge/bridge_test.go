// Package bridge provides WebSocket bridge to the simplex-chat CLI with auto-reconnect
package bridge

import (
	"os"
	"path/filepath"
	"testing"
)


// TestNewBridge handles the TestNewBridge HTTP request.
func TestNewBridge(t *testing.T) {
	dir := t.TempDir()
	b := New(dir)
	if b.DataDir != dir {
		t.Fatalf("expected data dir %s, got %s", dir, b.DataDir)
	}
	if b.cliBin == "" {
		t.Fatal("expected cli bin path")
	}
}


// TestNewBridgeDefaultDir handles the TestNewBridgeDefaultDir HTTP request.
func TestNewBridgeDefaultDir(t *testing.T) {
	b := New("/tmp/test-bridge")
	// Should have a default cli bin path
	if b.cliBin == "" {
		t.Fatal("expected default cli bin path")
	}
}


// TestUpdateContact handles the TestUpdateContact HTTP request.
func TestUpdateContact(t *testing.T) {
	dir := t.TempDir()
	b := New(dir)

	link := "smp://abc123@example.com/123"
	b.updateContact(link)

	path := filepath.Join(dir, "island_contact_link.txt")
	b2, err := os.ReadFile(path)
	if err != nil {
		t.Fatal(err)
	}
	if string(b2) != link+"\n" {
		t.Fatalf("expected %s, got %s", link, string(b2))
	}
}


// TestUpdateContactUpdatesExisting handles the TestUpdateContactUpdatesExisting HTTP request.
func TestUpdateContactUpdatesExisting(t *testing.T) {
	dir := t.TempDir()
	b := New(dir)

	b.updateContact("smp://old-link")
	b.updateContact("smp://new-link")

	path := filepath.Join(dir, "island_contact_link.txt")
	b2, _ := os.ReadFile(path)
	if string(b2) != "smp://new-link\n" {
		t.Fatalf("expected new link, got %s", string(b2))
	}
}


// TestUpdateContactEmpty handles the TestUpdateContactEmpty HTTP request.
func TestUpdateContactEmpty(t *testing.T) {
	dir := t.TempDir()
	b := New(dir)

	// Should not crash or create file
	b.updateContact("")

	path := filepath.Join(dir, "island_contact_link.txt")
	if _, err := os.Stat(path); err == nil {
		t.Fatal("expected no file for empty link")
	}
}


// TestUpdateContactDeduplicates handles the TestUpdateContactDeduplicates HTTP request.
func TestUpdateContactDeduplicates(t *testing.T) {
	dir := t.TempDir()
	b := New(dir)

	b.updateContact("smp://same-link")
	b.updateContact("smp://same-link")

	// File should only have the link once (dedup in updateContact logic)
	b2, _ := os.ReadFile(filepath.Join(dir, "island_contact_link.txt"))
	if string(b2) != "smp://same-link\n" {
		t.Fatalf("expected single link, got %s", string(b2))
	}
}
