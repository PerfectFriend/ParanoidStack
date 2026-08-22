// Package middleware provides HTTP middleware for security and observability.
//
// Middleware chain (applied in order):
//   1. SecurityMiddleware:   CORS, CSP, HSTS, XSS protection, Permissions-Policy
//   2. RateLimiter:          Configurable rate limiting per endpoint
//   3. InputSanitizer:       XSS prevention via input sanitization
//   4. RequestID logging:    X-Request-ID tracing in all log entries
//   5. Slow request warning: Logs warnings for requests exceeding threshold
//
// The rate limiter supports per-endpoint configuration with:
//   - Requests-per-interval
//   - Burst size
//   - Configurable interval (second, minute, hour)
package middleware
