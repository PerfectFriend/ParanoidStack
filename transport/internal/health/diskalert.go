// Package health provides system health monitoring and disk alerts
package health

import (
	"encoding/json"
	"fmt"
	"log/slog"
	"net/http"
	"os"
	"os/exec"
	"path/filepath"
	"strconv"
	"strings"
	"sync"
	"time"
)

type AlertLevel string

const (
	AlertWarning  AlertLevel = "WARNING"
	AlertCritical AlertLevel = "CRITICAL"
)

type DiskAlert struct {
	ID        string     `json:"id"`
	Level     AlertLevel `json:"level"`
	Message   string     `json:"message"`
	UsedPct   float64    `json:"used_pct"`
	Timestamp string     `json:"timestamp"`
	Acked     bool       `json:"acked"`
	AckedAt   string     `json:"acked_at,omitempty"`
}

type DiskAlertManager struct {
	mu          sync.Mutex
	alerts      []DiskAlert
	filePath    string
	lastAlerts  map[AlertLevel]time.Time // dedup within 1h
	seq         int64
	alertURL    string
	stopCh      chan struct{}
}

var GlobalDiskAlertManager *DiskAlertManager


// NewDiskAlertManager handles the NewDiskAlertManager HTTP request.
func NewDiskAlertManager(dataDir, alertURL string) *DiskAlertManager {
	d := &DiskAlertManager{
		filePath:   filepath.Join(dataDir, "disk_alerts.json"),
		lastAlerts: make(map[AlertLevel]time.Time),
		alertURL:   alertURL,
		stopCh:     make(chan struct{}),
	}
	d.load()
	return d
}

func (d *DiskAlertManager) load() {
	b, err := os.ReadFile(d.filePath)
	if err != nil {
		d.alerts = make([]DiskAlert, 0)
		return
	}
	json.Unmarshal(b, &d.alerts)
	if d.alerts == nil {
		d.alerts = make([]DiskAlert, 0)
	}
	for _, a := range d.alerts {
		var id int64
		if _, err := fmt.Sscanf(a.ID, "da-%d", &id); err == nil && id > d.seq {
			d.seq = id
		}
	}
}

func (d *DiskAlertManager) save() {
	b, _ := json.MarshalIndent(d.alerts, "", "  ")
	os.WriteFile(d.filePath, b, 0600)
}


// Start handles the Start HTTP request.
func (d *DiskAlertManager) Start() {
	go func() {
		ticker := time.NewTicker(5 * time.Minute)
		defer ticker.Stop()
		d.check()
		for {
			select {
			case <-ticker.C:
				d.check()
			case <-d.stopCh:
				return
			}
		}
	}()
}


// Stop handles the Stop HTTP request.
func (d *DiskAlertManager) Stop() {
	close(d.stopCh)
}

func (d *DiskAlertManager) check() {
	usedPct, err := getDiskUsedPct()
	if err != nil {
		slog.Error("disk alert: failed to check disk", "error", err)
		return
	}

	var level AlertLevel
	var message string

	if usedPct > 95 {
		level = AlertCritical
		message = fmt.Sprintf("Disk usage CRITICAL: %.1f%% — immediate action required", usedPct)
	} else if usedPct > 85 {
		level = AlertWarning
		message = fmt.Sprintf("Disk usage WARNING: %.1f%% — consider cleaning up", usedPct)
	} else {
		return
	}

	d.mu.Lock()
	lastTime, exists := d.lastAlerts[level]
	now := time.Now()
	if exists && now.Sub(lastTime) < 1*time.Hour {
		d.mu.Unlock()
		return
	}
	d.lastAlerts[level] = now
	d.seq++
	alert := DiskAlert{
		ID:        fmt.Sprintf("da-%d", d.seq),
		Level:     level,
		Message:   message,
		UsedPct:   usedPct,
		Timestamp: now.Format(time.RFC3339),
	}
	d.alerts = append(d.alerts, alert)
	if len(d.alerts) > 100 {
		d.alerts = d.alerts[len(d.alerts)-100:]
	}
	d.save()
	alertURL := d.alertURL
	d.mu.Unlock()

	slog.Warn("disk alert fired", "level", level, "used_pct", usedPct)

	// Send Telegram alert
	if alertURL != "" {
		go func() {
			http.Get(fmt.Sprintf("%s?text=%s", alertURL, message))
		}()
	}

	logAuditDiskAlert(level, message)
}

func getDiskUsedPct() (float64, error) {
	out, err := exec.Command("df", "-h", "/").Output()
	if err != nil {
		return 0, err
	}
	lines := strings.Split(strings.TrimSpace(string(out)), "\n")
	if len(lines) < 2 {
		return 0, fmt.Errorf("unexpected df output")
	}
	fields := strings.Fields(lines[1])
	if len(fields) < 5 {
		return 0, fmt.Errorf("unexpected df format")
	}
	pctStr := strings.TrimSuffix(fields[4], "%")
	pct, err := strconv.ParseFloat(pctStr, 64)
	if err != nil {
		return 0, err
	}
	return pct, nil
}

func logAuditDiskAlert(level AlertLevel, message string) {
	// Log to file for audit trail
	logDir := "/tmp"
	auditFile := filepath.Join(logDir, "disk_alert_audit.json")
	entries := make([]map[string]any, 0)
	if b, err := os.ReadFile(auditFile); err == nil {
		json.Unmarshal(b, &entries)
	}
	entries = append(entries, map[string]any{
		"level":     level,
		"message":   message,
		"timestamp": time.Now().Format(time.RFC3339),
	})
	if len(entries) > 100 {
		entries = entries[len(entries)-100:]
	}
	b, _ := json.Marshal(entries)
	os.WriteFile(auditFile, b, 0644)
}


// GetAlerts handles the GetAlerts HTTP request.
func (d *DiskAlertManager) GetAlerts(limit int) []DiskAlert {
	d.mu.Lock()
	defer d.mu.Unlock()
	if limit <= 0 || limit > len(d.alerts) {
		limit = len(d.alerts)
	}
	out := make([]DiskAlert, limit)
	copy(out, d.alerts[len(d.alerts)-limit:])
	// Reverse order (newest first)
	for i, j := 0, len(out)-1; i < j; i, j = i+1, j-1 {
		out[i], out[j] = out[j], out[i]
	}
	return out
}


// AckAlert handles the AckAlert HTTP request.
func (d *DiskAlertManager) AckAlert(id string) bool {
	d.mu.Lock()
	defer d.mu.Unlock()
	for i, a := range d.alerts {
		if a.ID == id {
			d.alerts[i].Acked = true
			d.alerts[i].AckedAt = time.Now().Format(time.RFC3339)
			d.save()
			return true
		}
	}
	return false
}


// DiskAlertHandler handles the DiskAlertHandler HTTP request.
func DiskAlertHandler() http.HandlerFunc {
	return func(w http.ResponseWriter, r *http.Request) {
		switch r.Method {
		case "GET":
			limitStr := r.URL.Query().Get("limit")
			limit := 20
			fmt.Sscanf(limitStr, "%d", &limit)
			alerts := GlobalDiskAlertManager.GetAlerts(limit)
			daWriteJSON(w, map[string]any{"ok": true, "alerts": alerts})
		default:
			http.Error(w, "GET", 405)
		}
	}
}


// DiskAlertAckHandler handles the DiskAlertAckHandler HTTP request.
func DiskAlertAckHandler() http.HandlerFunc {
	return func(w http.ResponseWriter, r *http.Request) {
		if r.Method != "POST" {
			http.Error(w, "POST required", 405)
			return
		}
		var req struct {
			ID string `json:"id"`
		}
		if err := json.NewDecoder(r.Body).Decode(&req); err != nil {
			daWriteJSON(w, map[string]any{"ok": false, "error": "bad json"})
			return
		}
		if req.ID == "" {
			daWriteJSON(w, map[string]any{"ok": false, "error": "id required"})
			return
		}
		if GlobalDiskAlertManager.AckAlert(req.ID) {
			daWriteJSON(w, map[string]any{"ok": true})
		} else {
			daWriteJSON(w, map[string]any{"ok": false, "error": "alert not found"})
		}
	}
}

func daWriteJSON(w http.ResponseWriter, v any) {
	w.Header().Set("Content-Type", "application/json")
	json.NewEncoder(w).Encode(v)
}
