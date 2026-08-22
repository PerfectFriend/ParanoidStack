// Package middleware provides HTTP middleware for security and rate limiting
package middleware

import (
	"fmt"
	"io"
	"log/slog"
	"net/http"
	"strings"
	"sync/atomic"
	"time"
)

type responseWriter struct {
	http.ResponseWriter
	status int
	written int64
}


// WriteHeader handles the WriteHeader HTTP request.
func (rw *responseWriter) WriteHeader(code int) {
	rw.status = code
	rw.ResponseWriter.WriteHeader(code)
}


// Write handles the Write HTTP request.
func (rw *responseWriter) Write(b []byte) (int, error) {
	n, err := rw.ResponseWriter.Write(b)
	rw.written += int64(n)
	return n, err
}


// Flush handles the Flush HTTP request.
func (rw *responseWriter) Flush() {
	if f, ok := rw.ResponseWriter.(http.Flusher); ok {
		f.Flush()
	}
}

var reqID int64

func nextReqID() string {
	return fmt.Sprintf("%s-%d-%d", "simplex", time.Now().UnixNano(), atomic.AddInt64(&reqID, 1))
}


// SecurityMiddleware handles the SecurityMiddleware HTTP request.
func SecurityMiddleware(next http.Handler) http.Handler {
	return http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		rID := nextReqID()
		w.Header().Set("X-Request-ID", rID)
		w.Header().Set("X-Content-Type-Options", "nosniff")
		w.Header().Set("X-Frame-Options", "DENY")
		w.Header().Set("X-XSS-Protection", "1; mode=block")
		w.Header().Set("Referrer-Policy", "strict-origin-when-cross-origin")
		w.Header().Set("Content-Security-Policy", "default-src 'self'; script-src 'self' 'unsafe-inline'; style-src 'self' 'unsafe-inline'; img-src 'self' data:; connect-src 'self' ws: wss:; font-src 'self' data:")
		w.Header().Set("Strict-Transport-Security", "max-age=31536000; includeSubDomains")
		w.Header().Set("Permissions-Policy", "camera=(), microphone=(), geolocation=()")
		r.Header.Set("X-Request-ID", rID)

		origin := r.Header.Get("Origin")
		if origin != "" {
			if strings.HasSuffix(origin, ".onion") || strings.HasSuffix(origin, ".local") || origin == "http://localhost:8080" || origin == "http://127.0.0.1:8080" || origin == "null" {
				w.Header().Set("Access-Control-Allow-Origin", origin)
				w.Header().Set("Access-Control-Allow-Methods", "GET, POST, PUT, PATCH, DELETE, OPTIONS")
				w.Header().Set("Access-Control-Allow-Headers", "Content-Type, Authorization, X-Requested-With")
				w.Header().Set("Access-Control-Max-Age", "86400")
			}
		}
		if r.Method == "OPTIONS" {
			w.WriteHeader(http.StatusNoContent)
			return
		}

		r.Body = http.MaxBytesReader(w, r.Body, 10<<20)

		rw := &responseWriter{ResponseWriter: w, status: http.StatusOK}
		start := time.Now()
		next.ServeHTTP(rw, r)
		dur := time.Since(start)
		if dur > time.Second {
			slog.Warn("slow request", "method", r.Method, "path", r.URL.Path, "status", rw.status, "duration", dur.String(), "size", rw.written)
		}
	})
}


// SanitizeBody handles the SanitizeBody HTTP request.
func SanitizeBody(next http.Handler) http.Handler {
	return http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		if r.Body != nil && (r.Method == "POST" || r.Method == "PUT" || r.Method == "PATCH") {
			body, err := io.ReadAll(r.Body)
			if err != nil {
				http.Error(w, "body read error", http.StatusBadRequest)
				return
			}
			r.Body.Close()
			cleaned := strings.ReplaceAll(string(body), "\x00", "")
			r.Body = io.NopCloser(strings.NewReader(cleaned))
		}
		next.ServeHTTP(w, r)
	})
}
