// Package health implements service health monitoring.
//
// Provides the /api/health endpoint that reports:
//   - Server uptime in hours
//   - Bridge connection status
//   - Overall healthy/unhealthy state
//   - Message count statistics
//
// Health data is gathered from multiple subsystems and consolidated
// into a single JSON response for external monitoring (Torquemada
// bot, node-monitor, Docker health checks).
package health
