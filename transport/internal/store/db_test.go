// Package store provides persistent storage backends
package store

import (
	"os"
	"path/filepath"
	"testing"
	"time"
)


// TestOpenAndMigrate handles the TestOpenAndMigrate HTTP request.
func TestOpenAndMigrate(t *testing.T) {
	dir, err := os.MkdirTemp("", "store-test-*")
	if err != nil {
		t.Fatal(err)
	}
	defer os.RemoveAll(dir)

	db, err := Open(filepath.Join(dir, "test.db"))
	if err != nil {
		t.Fatal(err)
	}
	defer db.Close()

	db.EnsureSchemaVersion()
}


// TestStationCRUD handles the TestStationCRUD HTTP request.
func TestStationCRUD(t *testing.T) {
	dir, err := os.MkdirTemp("", "store-test-*")
	if err != nil {
		t.Fatal(err)
	}
	defer os.RemoveAll(dir)

	db, err := Open(filepath.Join(dir, "test.db"))
	if err != nil {
		t.Fatal(err)
	}
	defer db.Close()

	st := &Station{
		ID: "test-station", Name: "Test Station", Type: "music",
		Lang: "en", Description: "Test", Enabled: true, CreatedAt: time.Now(),
	}
	if err := db.SaveStation(st); err != nil {
		t.Fatal(err)
	}

	got, err := db.GetStation("test-station")
	if err != nil {
		t.Fatal(err)
	}
	if got.Name != "Test Station" {
		t.Fatalf("expected Test Station, got %s", got.Name)
	}

	list, err := db.ListStations()
	if err != nil {
		t.Fatal(err)
	}
	if len(list) != 1 {
		t.Fatalf("expected 1 station, got %d", len(list))
	}
}


// TestJSONMigration handles the TestJSONMigration HTTP request.
func TestJSONMigration(t *testing.T) {
	dir, err := os.MkdirTemp("", "store-test-*")
	if err != nil {
		t.Fatal(err)
	}
	defer os.RemoveAll(dir)

	radioDir := filepath.Join(dir, "radio")
	os.MkdirAll(radioDir, 0755)

	os.WriteFile(filepath.Join(radioDir, "stations.json"), []byte(`[{"id":"s1","name":"S1","type":"music","lang":"en","description":"","icon":"","enabled":true,"created_at":"2026-01-01T00:00:00Z","track_count":5}]`), 0644)
	os.WriteFile(filepath.Join(radioDir, "announcements.json"), []byte(`[{"id":"a1","announcer":"king","title":"Test","body":"Hello","lang":"en","priority":1,"stations":[],"audio_file":"","paid":false,"paid_amount_ng":0,"created_at":"2026-01-01T00:00:00Z"}]`), 0644)

	db, err := Open(filepath.Join(dir, "test.db"))
	if err != nil {
		t.Fatal(err)
	}
	defer db.Close()

	if err := db.MigrateFromJSON(dir); err != nil {
		t.Fatal(err)
	}

	st, err := db.GetStation("s1")
	if err != nil {
		t.Fatal(err)
	}
	if st.Name != "S1" {
		t.Fatalf("expected S1, got %s", st.Name)
	}
}


// TestPINStore handles the TestPINStore HTTP request.
func TestPINStore(t *testing.T) {
	dir, err := os.MkdirTemp("", "store-test-*")
	if err != nil {
		t.Fatal(err)
	}
	defer os.RemoveAll(dir)

	db, err := Open(filepath.Join(dir, "test.db"))
	if err != nil {
		t.Fatal(err)
	}
	defer db.Close()

	ps := NewPINStore(db)
	if ps.HasPIN() {
		t.Fatal("should not have PIN initially")
	}
	if err := ps.SetPIN("1234"); err != nil {
		t.Fatal(err)
	}
	if !ps.HasPIN() {
		t.Fatal("should have PIN after set")
	}
	ok, err := ps.VerifyPIN("1234")
	if err != nil || !ok {
		t.Fatal("should verify correct PIN")
	}
	ok, err = ps.VerifyPIN("wrong")
	if err != nil || ok {
		t.Fatal("should reject wrong PIN")
	}
}


// TestAccountCRUD handles the TestAccountCRUD HTTP request.
func TestAccountCRUD(t *testing.T) {
	dir, err := os.MkdirTemp("", "store-test-*")
	if err != nil {
		t.Fatal(err)
	}
	defer os.RemoveAll(dir)

	db, err := Open(filepath.Join(dir, "test.db"))
	if err != nil {
		t.Fatal(err)
	}
	defer db.Close()

	now := time.Now()
	a := &Account{Pubkey: "testpub", Privkey: "testpriv", Mnemonic: "test mnemonic", CreatedAt: now}
	if err := db.SaveAccount(a); err != nil {
		t.Fatal(err)
	}

	got, err := db.GetAccount("testpub")
	if err != nil {
		t.Fatal(err)
	}
	if got.Pubkey != "testpub" {
		t.Fatalf("expected testpub, got %s", got.Pubkey)
	}

	list, err := db.ListAccounts()
	if err != nil {
		t.Fatal(err)
	}
	if len(list) != 1 {
		t.Fatalf("expected 1 account, got %d", len(list))
	}
}
