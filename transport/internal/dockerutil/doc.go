// Package dockerutil provides Docker container health management.
//
// Handles:
//   - Health checks via Docker API
//   - Auto-restart of unhealthy containers (15min cron)
//   - Per-container health status reporting
//
// Used by the /api/admin/docker endpoint and the auto-restart cron
// that monitors the ParanoidX Docker stack (Tor, SMP, XFTP,
// coturn, V2Ray).
package dockerutil
