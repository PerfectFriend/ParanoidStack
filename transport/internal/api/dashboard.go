package api

import (
	"net/http"
	"os"
	"path/filepath"
)

// DashboardHandler serves the dashboard HTML from file system
func DashboardHandler() http.HandlerFunc {
	return func(w http.ResponseWriter, r *http.Request) {
		dataDir := os.Getenv("DATA_DIR")
		if dataDir == "" {
			home, _ := os.UserHomeDir()
			dataDir = filepath.Join(home, ".local/share/ParanoidX")
		}
		dashboardPath := filepath.Join(dataDir, "dashboard.html")
		
		// Check if file exists
		if _, err := os.Stat(dashboardPath); err == nil {
			http.ServeFile(w, r, dashboardPath)
			return
		}
		
		// Fallback to embedded (if any) or 404
		http.Error(w, "Dashboard not found", 404)
	}
}
