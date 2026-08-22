// Package status provides server status tracking
package status

import (
	"os"
	"path/filepath"
	"testing"
)


// TestGetTierDiamond handles the TestGetTierDiamond HTTP request.
func TestGetTierDiamond(t *testing.T) {
	if got := getTier(map[string]any{"score": float64(250)}); got != "diamond" {
		t.Fatalf("expected diamond, got %s", got)
	}
}


// TestGetTierGold handles the TestGetTierGold HTTP request.
func TestGetTierGold(t *testing.T) {
	if got := getTier(map[string]any{"score": float64(150)}); got != "gold" {
		t.Fatalf("expected gold, got %s", got)
	}
}


// TestGetTierSilver handles the TestGetTierSilver HTTP request.
func TestGetTierSilver(t *testing.T) {
	if got := getTier(map[string]any{"score": float64(75)}); got != "silver" {
		t.Fatalf("expected silver, got %s", got)
	}
}


// TestGetTierBronze handles the TestGetTierBronze HTTP request.
func TestGetTierBronze(t *testing.T) {
	if got := getTier(map[string]any{"score": float64(30)}); got != "bronze" {
		t.Fatalf("expected bronze, got %s", got)
	}
}


// TestGetTierBasic handles the TestGetTierBasic HTTP request.
func TestGetTierBasic(t *testing.T) {
	if got := getTier(map[string]any{"score": float64(5)}); got != "basic" {
		t.Fatalf("expected basic, got %s", got)
	}
}


// TestGetTierEmptyMap handles the TestGetTierEmptyMap HTTP request.
func TestGetTierEmptyMap(t *testing.T) {
	if got := getTier(map[string]any{}); got != "basic" {
		t.Fatalf("expected basic, got %s", got)
	}
}


// TestGetTierFloat handles the TestGetTierFloat HTTP request.
func TestGetTierFloat(t *testing.T) {
	if got := getTier(float64(100)); got != "gold" {
		t.Fatalf("expected gold, got %s", got)
	}
}


// TestIsRoyalNode handles the TestIsRoyalNode HTTP request.
func TestIsRoyalNode(t *testing.T) {
	dir := t.TempDir()
	os.WriteFile(filepath.Join(dir, "royal.enabled"), []byte("1"), 0644)

	if !isRoyalNode(dir) {
		t.Fatal("expected royal node")
	}
}


// TestIsRoyalNodeDisabled handles the TestIsRoyalNodeDisabled HTTP request.
func TestIsRoyalNodeDisabled(t *testing.T) {
	dir := t.TempDir()
	os.WriteFile(filepath.Join(dir, "royal.enabled"), []byte("0"), 0644)

	if isRoyalNode(dir) {
		t.Fatal("expected not royal (0)")
	}
}


// TestIsRoyalNodeMissing handles the TestIsRoyalNodeMissing HTTP request.
func TestIsRoyalNodeMissing(t *testing.T) {
	dir := t.TempDir()
	if isRoyalNode(dir) {
		t.Fatal("expected not royal (no file)")
	}
}


// TestCalculateReputationStubBasic handles the TestCalculateReputationStubBasic HTTP request.
func TestCalculateReputationStubBasic(t *testing.T) {
	dir := t.TempDir()
	rep := calculateReputationStub(dir)
	score := rep["score"].(float64)
	if score != 0 {
		t.Fatalf("expected 0 score, got %f", score)
	}
}


// TestCalculateReputationStubRoyal handles the TestCalculateReputationStubRoyal HTTP request.
func TestCalculateReputationStubRoyal(t *testing.T) {
	dir := t.TempDir()
	os.WriteFile(filepath.Join(dir, "royal.enabled"), []byte("1"), 0644)

	rep := calculateReputationStub(dir)
	score := rep["score"].(float64)
	if score < 100 {
		t.Fatalf("expected at least 100 for royal, got %f", score)
	}
	reasons := rep["reasons"].([]string)
	found := false
	for _, r := range reasons {
		if r == "royal_node" {
			found = true
		}
	}
	if !found {
		t.Fatal("expected royal_node reason")
	}
}


// TestCalcRepWithHolders handles the TestCalcRepWithHolders HTTP request.
func TestCalcRepWithHolders(t *testing.T) {
	dir := t.TempDir()
	os.WriteFile(filepath.Join(dir, "royal.enabled"), []byte("1"), 0644)
	os.WriteFile(filepath.Join(dir, "banknotes_registry.json"), []byte(`[{"serial":"MB001","holder":"alice"}]`), 0644)

	rep := calculateReputationStub(dir)
	score := rep["score"].(float64)
	if score < 110 {
		t.Fatalf("expected at least 110 (100 royal + 10 holder), got %f", score)
	}
}


// TestGetVaultFileCount handles the TestGetVaultFileCount HTTP request.
func TestGetVaultFileCount(t *testing.T) {
	dir := t.TempDir()
	os.MkdirAll(dir, 0700)
	os.WriteFile(filepath.Join(dir, "a.txt"), []byte("a"), 0600)
	os.WriteFile(filepath.Join(dir, "b.txt"), []byte("b"), 0600)

	if got := getVaultFileCount(dir); got != 2 {
		t.Fatalf("expected 2, got %d", got)
	}
}


// TestGetVaultFileCountEmpty handles the TestGetVaultFileCountEmpty HTTP request.
func TestGetVaultFileCountEmpty(t *testing.T) {
	dir := t.TempDir()
	if got := getVaultFileCount(dir); got != 0 {
		t.Fatalf("expected 0, got %d", got)
	}
}


// TestGetVaultSizeMB handles the TestGetVaultSizeMB HTTP request.
func TestGetVaultSizeMB(t *testing.T) {
	dir := t.TempDir()
	os.WriteFile(filepath.Join(dir, "test.dat"), make([]byte, 1024*1024), 0600)

	sz := getVaultSizeMB(dir)
	if sz < 0.9 || sz > 1.1 {
		t.Fatalf("expected ~1 MB, got %f", sz)
	}
}


// TestReadTrim handles the TestReadTrim HTTP request.
func TestReadTrim(t *testing.T) {
	dir := t.TempDir()
	p := filepath.Join(dir, "test.txt")
	os.WriteFile(p, []byte("  hello world  \n"), 0600)

	if got := readTrim(p); got != "hello world" {
		t.Fatalf("expected 'hello world', got '%s'", got)
	}
}


// TestReadTrimMissing handles the TestReadTrimMissing HTTP request.
func TestReadTrimMissing(t *testing.T) {
	if got := readTrim("/nonexistent"); got != "" {
		t.Fatalf("expected empty, got '%s'", got)
	}
}
