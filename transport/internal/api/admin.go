package api

import (
	"fmt"
	"net"
	"net/http"
	"os/exec"
	"runtime"
	"strings"
	"time"

	"px-transport/internal/common"
)

type AdminHandler struct{}

func NewAdminHandler() *AdminHandler {
	return &AdminHandler{}
}

func (h *AdminHandler) ServeHTTP(w http.ResponseWriter, r *http.Request) {
	switch r.URL.Path {
	case "/api/admin/info":
		h.info(w, r)
	case "/api/admin/docker":
		h.dockerStatus(w, r)
	case "/api/admin/metrics":
		h.metrics(w, r)
	case "/api/admin/metrics/system":
		h.systemMetrics(w, r)
	case "/api/admin/status-page":
		h.statusPage(w, r)
	case "/api/admin/rate-limit-status":
		h.rateLimitStatus(w, r)
	case "/api/admin/backup":
		h.backup(w, r)
	case "/api/version":
		h.version(w, r)
	case "/api/health":
		h.healthCheck(w, r)
	case "/api/status":
		h.statusCheck(w, r)
	case "/api/addresses":
		h.addresses(w, r)
	default:
		http.NotFound(w, r)
	}
}

func (h *AdminHandler) info(w http.ResponseWriter, r *http.Request) {
	// Real service health: probe the actual docker containers instead of
	// reporting hardcoded "healthy" values.
	services := map[string]any{}
	if out, err := exec.Command("docker", "ps", "--format", "{{.Names}}	{{.Status}}").Output(); err == nil {
		lines := strings.Split(strings.TrimSpace(string(out)), "\n")
		services["docker"] = map[string]any{
			"healthy": len(lines) > 0 && lines[0] != "",
			"detail":  fmt.Sprintf("%d containers running", len(lines)),
		}
	} else {
		services["docker"] = map[string]any{"healthy": false, "detail": "docker unavailable"}
	}
	if _, err := net.DialTimeout("tcp", "127.0.0.1:17001", 2*time.Second); err == nil {
		services["p2p_transport"] = map[string]any{"healthy": true, "detail": "port 17001"}
	} else {
		services["p2p_transport"] = map[string]any{"healthy": false, "detail": "port 17001 closed"}
	}
	services["server"] = map[string]any{"healthy": true, "detail": "listening"}

	info := map[string]any{
		"version":    "C41-C60",
		"build":      "px-node-C41-C60",
		"uptime":     time.Since(common.StartTime).String(),
		"started":    common.StartTime.Format(time.RFC3339),
		"go_version": runtime.Version(),
		"cpus":       runtime.NumCPU(),
		"goroutines": runtime.NumGoroutine(),
		"services":   services,
	}
	common.WriteJSON(w, info)
}

func (h *AdminHandler) dockerStatus(w http.ResponseWriter, r *http.Request) {
	output, err := exec.Command("docker", "ps", "--format", "{{.Names}}\t{{.Status}}").Output()
	containers := map[string]string{}
	if err == nil {
		lines := strings.Split(strings.TrimSpace(string(output)), "\n")
		for _, line := range lines {
			parts := strings.SplitN(line, "\t", 2)
			if len(parts) == 2 {
				containers[parts[0]] = parts[1]
			}
		}
	}
	common.WriteJSON(w, map[string]any{"containers": containers})
}

func (h *AdminHandler) metrics(w http.ResponseWriter, r *http.Request) {
	common.WriteJSON(w, map[string]any{"metrics": "available at /metrics"})
}

func (h *AdminHandler) systemMetrics(w http.ResponseWriter, r *http.Request) {
	var m runtime.MemStats
	runtime.ReadMemStats(&m)
	common.WriteJSON(w, map[string]any{
		"alloc_mb":   m.Alloc / 1024 / 1024,
		"sys_mb":     m.Sys / 1024 / 1024,
		"num_gc":     m.NumGC,
		"goroutines": runtime.NumGoroutine(),
	})
}

func (h *AdminHandler) statusPage(w http.ResponseWriter, r *http.Request) {
	common.WriteJSON(w, map[string]any{"status": "ok"})
}



func (h *AdminHandler) version(w http.ResponseWriter, r *http.Request) {
	common.WriteJSON(w, map[string]any{
		"api_version": "v1",
		"build":       "px-node-C41-C60",
		"go":          runtime.Version(),
	})
}

func (h *AdminHandler) healthCheck(w http.ResponseWriter, r *http.Request) {
	common.WriteJSON(w, map[string]any{
		"healthy": true,
		"bridge":  true,
		"status":  "ok",
	})
}

func (h *AdminHandler) statusCheck(w http.ResponseWriter, r *http.Request) {
	common.WriteJSON(w, map[string]any{
		"status": "running",
		"uptime": time.Since(common.StartTime).String(),
	})
}

func (h *AdminHandler) addresses(w http.ResponseWriter, r *http.Request) {
	common.WriteJSON(w, map[string]any{"addresses": []string{}})
}

// rateLimitStatus reports the current rate-limiter configuration.
func (h *AdminHandler) rateLimitStatus(w http.ResponseWriter, r *http.Request) {
	common.WriteJSON(w, map[string]any{
		"enabled": true,
		"note":    "per-visitor token bucket; see internal/middleware/ratelimit.go",
	})
}

// backup triggers a best-effort backup of the data directory listing.
// TODO(C-RC): stream a real tar archive of $DATA_DIR instead of metadata.
func (h *AdminHandler) backup(w http.ResponseWriter, r *http.Request) {
	if r.Method != http.MethodPost {
		http.Error(w, "POST required", http.StatusMethodNotAllowed)
		return
	}
	common.WriteJSON(w, map[string]any{
		"ok":   false,
		"note": "backup not implemented yet — tracked for RC",
	})
}

