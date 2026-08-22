// Package api — tests for admin handlers that exist in this codebase.
// NOTE: the original admin_test.go referenced handlers (ConfigHandler,
// AuditLogHandler, WebhookQueueStatsHandler) that were never implemented;
// it broke `go vet ./...` for the whole package. Rewritten against the
// real API surface (info, health, version, rate-limit-status, backup).
package api

import (
	"encoding/json"
	"net/http/httptest"
	"testing"
)

func TestAdminInfoHandler(t *testing.T) {
	h := NewAdminHandler()
	req := httptest.NewRequest("GET", "/api/admin/info", nil)
	w := httptest.NewRecorder()
	h.ServeHTTP(w, req)

	if w.Code != 200 {
		t.Fatalf("expected 200, got %d", w.Code)
	}
	var resp map[string]any
	if err := json.NewDecoder(w.Body).Decode(&resp); err != nil {
		t.Fatalf("decode error: %v", err)
	}
	services, ok := resp["services"].(map[string]any)
	if !ok {
		t.Fatal("expected services object in /api/admin/info")
	}
	// docker service must be present and honestly reflect availability
	if _, ok := services["docker"]; !ok {
		t.Fatal("expected docker entry in services")
	}
}

func TestHealthEndpoint(t *testing.T) {
	h := NewAdminHandler()
	req := httptest.NewRequest("GET", "/api/health", nil)
	w := httptest.NewRecorder()
	h.ServeHTTP(w, req)

	var resp map[string]any
	if err := json.NewDecoder(w.Body).Decode(&resp); err != nil {
		t.Fatalf("decode error: %v", err)
	}
	if resp["healthy"] != true {
		t.Fatalf("expected healthy=true, got %v", resp)
	}
}

func TestRateLimitStatusHandler(t *testing.T) {
	h := NewAdminHandler()
	req := httptest.NewRequest("GET", "/api/admin/rate-limit-status", nil)
	w := httptest.NewRecorder()
	h.ServeHTTP(w, req)

	if w.Code != 200 {
		t.Fatalf("expected 200, got %d", w.Code)
	}
	var resp map[string]any
	json.NewDecoder(w.Body).Decode(&resp)
	if resp["enabled"] != true {
		t.Fatalf("expected enabled=true, got %v", resp)
	}
}

func TestBackupRequiresPost(t *testing.T) {
	h := NewAdminHandler()
	req := httptest.NewRequest("GET", "/api/admin/backup", nil)
	w := httptest.NewRecorder()
	h.ServeHTTP(w, req)

	if w.Code != 405 {
		t.Fatalf("expected 405 for GET on backup, got %d", w.Code)
	}
}

func TestUnknownAdminRoute404(t *testing.T) {
	h := NewAdminHandler()
	req := httptest.NewRequest("GET", "/api/admin/nonexistent", nil)
	w := httptest.NewRecorder()
	h.ServeHTTP(w, req)

	if w.Code != 404 {
		t.Fatalf("expected 404, got %d", w.Code)
	}
}
