package api

import (
	"net/http/httptest"
	"testing"
)

// The global guard must reject requests that do not originate from localhost
// or a Tor onion circuit. Simulated by setting RemoteAddr directly.
func TestGuardBlocksRemote(t *testing.T) {
	h := NewHandler(nil)
	req := httptest.NewRequest("POST", "/api/config/delete", nil)
	req.RemoteAddr = "8.8.8.8:1234"
	w := httptest.NewRecorder()
	h.ServeHTTP(w, req)
	if w.Code != 403 {
		t.Fatalf("expected 403 for remote POST, got %d", w.Code)
	}
}

func TestGuardAllowsPrivateIPs(t *testing.T) {
	h := NewHandler(nil)
	
	// Test various private IP ranges - all should be allowed (200 or 404, not 403)
	testCases := []struct{
		name string
		ip string
	}{
		{"loopback", "127.0.0.1:5555"},
		{"classA", "10.0.0.1:5555"},
		{"classB", "172.16.0.1:5555"},
		{"classC", "192.168.1.50:5555"},
		{"health-loopback", "127.0.0.1:5555"},
		{"health-classA", "10.0.0.1:5555"},
	}
	
	for _, tc := range testCases {
		req := httptest.NewRequest("GET", "/api/admin/info", nil)
		req.RemoteAddr = tc.ip
		w := httptest.NewRecorder()
		h.ServeHTTP(w, req)
		
		if w.Code == 403 {
			t.Fatalf("%s: expected allowed (not 403) for private IP %s, got %d", tc.name, tc.ip, w.Code)
		}
	}
	
	// Health endpoints should be allowed even for public IPs
	req := httptest.NewRequest("GET", "/api/health", nil)
	req.RemoteAddr = "8.8.8.8:1234"
	w := httptest.NewRecorder()
	h.ServeHTTP(w, req)
	if w.Code == 403 {
		t.Fatal("health endpoint should not be blocked for public IP")
	}
}

func TestGuardAllowsHealthForPublic(t *testing.T) {
	h := NewHandler(nil)
	publicEndpoints := []string{"/api/health", "/api/version", "/api/status"}
	
	for _, ep := range publicEndpoints {
		req := httptest.NewRequest("GET", ep, nil)
		req.RemoteAddr = "8.8.8.8:1234"
		w := httptest.NewRecorder()
		h.ServeHTTP(w, req)
		if w.Code == 403 {
			t.Fatalf("%s: public endpoint should not be blocked for public IP, got %d", ep, w.Code)
		}
	}
}