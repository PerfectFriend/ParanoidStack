// Package middleware provides HTTP middleware for security and rate limiting
package middleware

import (
	"net"
	"net/http"
	"sync"
	"time"
)

type visitor struct {
	tokens    int
	lastCheck time.Time
}

type RateLimiter struct {
	mu       sync.Mutex
	visitors map[string]*visitor
	rate     int
	burst    int
	interval time.Duration
}


// NewRateLimiter handles the NewRateLimiter HTTP request.
func NewRateLimiter(rate, burst int, interval time.Duration) *RateLimiter {
	return &RateLimiter{
		visitors: make(map[string]*visitor),
		rate:     rate,
		burst:    burst,
		interval: interval,
	}
}


// Allow handles the Allow HTTP request.
func (rl *RateLimiter) Allow(ip string) bool {
	rl.mu.Lock()
	defer rl.mu.Unlock()

	v, ok := rl.visitors[ip]
	if !ok {
		rl.visitors[ip] = &visitor{tokens: rl.burst - 1, lastCheck: time.Now()}
		return true
	}

	elapsed := time.Since(v.lastCheck)
	v.lastCheck = time.Now()

	refill := int(elapsed / rl.interval)
	v.tokens += refill * rl.rate
	if v.tokens > rl.burst {
		v.tokens = rl.burst
	}

	if v.tokens > 0 {
		v.tokens--
		return true
	}
	return false
}

func clientIP(r *http.Request) string {
	if xf := r.Header.Get("X-Forwarded-For"); xf != "" {
		if ip := net.ParseIP(xf); ip != nil {
			return ip.String()
		}
	}
	host, _, err := net.SplitHostPort(r.RemoteAddr)
	if err != nil {
		return r.RemoteAddr
	}
	return host
}


// Middleware handles the Middleware HTTP request.
func (rl *RateLimiter) Middleware(next http.HandlerFunc) http.HandlerFunc {
	return func(w http.ResponseWriter, r *http.Request) {
		ip := clientIP(r)
		if !rl.Allow(ip) {
			http.Error(w, "429 Too Many Requests", http.StatusTooManyRequests)
			return
		}
		next(w, r)
	}
}


// Handler handles the Handler HTTP request.
func (rl *RateLimiter) Handler(next http.HandlerFunc) http.HandlerFunc {
	return rl.Middleware(next)
}


// GetRate handles the GetRate HTTP request.
func (rl *RateLimiter) GetRate() int {
	rl.mu.Lock()
	defer rl.mu.Unlock()
	return rl.rate
}


// SetRate handles the SetRate HTTP request.
func (rl *RateLimiter) SetRate(rate int) {
	rl.mu.Lock()
	defer rl.mu.Unlock()
	rl.rate = rate
}


// GetBurst handles the GetBurst HTTP request.
func (rl *RateLimiter) GetBurst() int {
	rl.mu.Lock()
	defer rl.mu.Unlock()
	return rl.burst
}


// SetBurst handles the SetBurst HTTP request.
func (rl *RateLimiter) SetBurst(burst int) {
	rl.mu.Lock()
	defer rl.mu.Unlock()
	rl.burst = burst
}


// Status handles the Status HTTP request.
func (rl *RateLimiter) Status() map[string]interface{} {
	rl.mu.Lock()
	defer rl.mu.Unlock()
	return map[string]interface{}{
		"rate":     rl.rate,
		"burst":    rl.burst,
		"interval": rl.interval.String(),
		"clients":  len(rl.visitors),
	}
}
