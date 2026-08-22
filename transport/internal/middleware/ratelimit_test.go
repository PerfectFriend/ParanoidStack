// Package middleware provides HTTP middleware for security and rate limiting
package middleware

import (
	"net/http"
	"net/http/httptest"
	"testing"
	"time"
)


// TestRateLimiterAllow handles the TestRateLimiterAllow HTTP request.
func TestRateLimiterAllow(t *testing.T) {
	rl := NewRateLimiter(1, 5, time.Minute)
	if !rl.Allow("1.2.3.4") {
		t.Fatal("expected first request allowed")
	}
	for i := 0; i < 4; i++ {
		if !rl.Allow("1.2.3.4") {
			t.Fatalf("expected request %d/4 allowed (burst)", i+2)
		}
	}
	if rl.Allow("1.2.3.4") {
		t.Fatal("expected 6th request denied (burst=5)")
	}
}


// TestRateLimiterDifferentIPs handles the TestRateLimiterDifferentIPs HTTP request.
func TestRateLimiterDifferentIPs(t *testing.T) {
	rl := NewRateLimiter(1, 3, time.Minute)
	for i := 0; i < 3; i++ {
		if !rl.Allow("1.2.3.4") {
			t.Fatal("expected 1.2.3.4 allowed")
		}
	}
	if rl.Allow("1.2.3.4") {
		t.Fatal("expected 1.2.3.4 denied after burst")
	}
	if !rl.Allow("5.6.7.8") {
		t.Fatal("expected 5.6.7.8 allowed (different IP)")
	}
}


// TestRateLimiterRefill handles the TestRateLimiterRefill HTTP request.
func TestRateLimiterRefill(t *testing.T) {
	rl := NewRateLimiter(2, 2, 10*time.Millisecond)
	if !rl.Allow("1.2.3.4") {
		t.Fatal("expected 1st req allowed")
	}
	if !rl.Allow("1.2.3.4") {
		t.Fatal("expected 2nd req allowed")
	}
	if rl.Allow("1.2.3.4") {
		t.Fatal("expected 3rd req denied (burst exhausted)")
	}
	time.Sleep(15 * time.Millisecond)
	if !rl.Allow("1.2.3.4") {
		t.Fatal("expected req allowed after refill")
	}
}


// TestRateLimiterHTTPMiddleware handles the TestRateLimiterHTTPMiddleware HTTP request.
func TestRateLimiterHTTPMiddleware(t *testing.T) {
	rl := NewRateLimiter(1, 2, time.Minute)
	req := httptest.NewRequest("GET", "/", nil)
	req.RemoteAddr = "10.0.0.1:12345"

	for i := 0; i < 2; i++ {
		called := false
		handler := rl.Middleware(func(w http.ResponseWriter, r *http.Request) {
			called = true
		})
		w := httptest.NewRecorder()
		handler(w, req)
		if !called {
			t.Fatalf("expected handler called on req %d (burst)", i+1)
		}
	}

	called := false
	handler := rl.Middleware(func(w http.ResponseWriter, r *http.Request) {
		called = true
	})
	w := httptest.NewRecorder()
	handler(w, req)
	if called {
		t.Fatal("expected handler NOT called after burst exhausted")
	}
	if w.Code != http.StatusTooManyRequests {
		t.Fatalf("expected 429, got %d", w.Code)
	}
}


// TestRateLimiterXForwardedFor handles the TestRateLimiterXForwardedFor HTTP request.
func TestRateLimiterXForwardedFor(t *testing.T) {
	rl := NewRateLimiter(1, 1, time.Minute)
	called := false
	handler := rl.Middleware(func(w http.ResponseWriter, r *http.Request) {
		called = true
	})
	req := httptest.NewRequest("GET", "/", nil)
	req.Header.Set("X-Forwarded-For", "10.0.0.1")
	w := httptest.NewRecorder()
	handler(w, req)
	if !called {
		t.Fatal("expected handler called with X-Forwarded-For")
	}
}
