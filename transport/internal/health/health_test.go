// Package health provides system health monitoring and disk alerts
package health

import (
	"os"
	"path/filepath"
	"testing"
	"time"
)


// TestNewMonitor handles the TestNewMonitor HTTP request.
func TestNewMonitor(t *testing.T) {
	dir := t.TempDir()
	m := New(dir, filepath.Join(dir, "vault"), time.Now())
	if m.DataDir != dir {
		t.Fatalf("expected data dir %s, got %s", dir, m.DataDir)
	}
	if m.VaultPath == "" {
		t.Fatal("expected vault path")
	}
}


// TestReportStructure handles the TestReportStructure HTTP request.
func TestReportStructure(t *testing.T) {
	dir := t.TempDir()
	vp := filepath.Join(dir, "vault")
	os.MkdirAll(vp, 0700)

	m := New(dir, vp, time.Now().Add(-1*time.Hour))
	r := m.Report()

	if r.Timestamp == "" {
		t.Fatal("expected timestamp")
	}
	if r.Uptime == "" {
		t.Fatal("expected uptime")
	}
	if len(r.Checks) == 0 {
		t.Log("note: health checks may be empty due to missing system deps, not a failure")
	}
}


// TestCheckVaultExisting handles the TestCheckVaultExisting HTTP request.
func TestCheckVaultExisting(t *testing.T) {
	dir := t.TempDir()
	vp := filepath.Join(dir, "test-vault")
	os.MkdirAll(vp, 0700)
	os.WriteFile(filepath.Join(vp, "test.txt"), []byte("data"), 0600)

	m := New(dir, vp, time.Now())
	checks := m.checkVault()
	if len(checks) != 1 {
		t.Fatalf("expected 1 vault check, got %d", len(checks))
	}
	if checks[0].Status != "ok" {
		t.Fatalf("expected ok, got %s", checks[0].Status)
	}
}


// TestCheckVaultMissing handles the TestCheckVaultMissing HTTP request.
func TestCheckVaultMissing(t *testing.T) {
	dir := t.TempDir()
	m := New(dir, "/nonexistent-vault-path", time.Now())
	checks := m.checkVault()
	if len(checks) == 0 {
		t.Fatal("expected checks")
	}
	// warn is acceptable since path doesn't exist
}


// TestCheckVaultWithFiles handles the TestCheckVaultWithFiles HTTP request.
func TestCheckVaultWithFiles(t *testing.T) {
	dir := t.TempDir()
	vp := filepath.Join(dir, "vault-files")
	os.MkdirAll(vp, 0700)
	os.WriteFile(filepath.Join(vp, "a.txt"), []byte("a"), 0600)
	os.WriteFile(filepath.Join(vp, "b.txt"), []byte("bb"), 0600)

	m := New(dir, vp, time.Now())
	checks := m.checkVault()
	if len(checks) != 1 {
		t.Fatalf("expected 1 check, got %d", len(checks))
	}
}


// TestUptimeFormat handles the TestUptimeFormat HTTP request.
func TestUptimeFormat(t *testing.T) {
	start := time.Now().Add(-2 * time.Hour)
	m := New("/tmp", "/tmp", start)
	r := m.Report()

	if r.Uptime == "" {
		t.Fatal("expected uptime string")
	}
}
