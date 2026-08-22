// Package api provides HTTP handlers and API endpoints for the ParanoidX server
package api

import (
	"fmt"
	"net/http"
	"os"
	"path/filepath"
	"sort"
	"strings"
	"sync"
)

// DocEntry describes a documentation file in the docs directory.
type DocEntry struct {
	Name     string `json:"name"`
	Path     string `json:"path"`
	Size     int64  `json:"size"`
	Modified string `json:"modified"`
	Lang     string `json:"lang,omitempty"`
}

// RouteDoc documents a single API route with its method, path, and description.
type RouteDoc struct {
	Method      string `json:"method"`
	Path        string `json:"path"`
	Description string `json:"description,omitempty"`
	Category    string `json:"category,omitempty"`
	Example     string `json:"example,omitempty"`
}

var (
	routeDocs   []RouteDoc
	routeDocsMu sync.Mutex
)


// RegisterRoute adds a route to the API documentation registry.
func RegisterRoute(method, path, desc, category string) {
	routeDocsMu.Lock()
	routeDocs = append(routeDocs, RouteDoc{
		Method:      method,
		Path:        path,
		Description: desc,
		Category:    category,
	})
	routeDocsMu.Unlock()
}


// RegisterRouteWithExample adds a route with an example payload to the API documentation registry.
func RegisterRouteWithExample(method, path, desc, category, example string) {
	routeDocsMu.Lock()
	routeDocs = append(routeDocs, RouteDoc{
		Method:      method,
		Path:        path,
		Description: desc,
		Category:    category,
		Example:     example,
	})
	routeDocsMu.Unlock()
}


// GetRouteDocs returns all registered API route documentation entries.
func GetRouteDocs() []RouteDoc {
	routeDocsMu.Lock()
	defer routeDocsMu.Unlock()
	out := make([]RouteDoc, len(routeDocs))
	copy(out, routeDocs)
	return out
}


// DocsListHandler returns a list of available documentation files.
func DocsListHandler(projectDir string) http.HandlerFunc {
	return func(w http.ResponseWriter, r *http.Request) {
		w.Header().Set("Content-Type", "application/json")

		docsDir := filepath.Join(projectDir, "docs")
		entries, err := os.ReadDir(docsDir)
		if err != nil {
			writeJSON(w, map[string]any{"error": "docs dir not found", "entries": []DocEntry{}})
			return
		}

		var docs []DocEntry
		for _, e := range entries {
			if e.IsDir() || !strings.HasSuffix(e.Name(), ".md") {
				continue
			}
			info, err := e.Info()
			if err != nil {
				continue
			}
			lang := ""
			name := e.Name()
			if strings.Contains(name, "-RU.") || strings.Contains(name, "-RU.md") {
				lang = "ru"
			} else if strings.Contains(name, "-ES.") || strings.Contains(name, "-ES.md") {
				lang = "es"
			}
			if strings.HasPrefix(name, "WHITE-PAPER") {
				if lang == "" {
					lang = "en"
				}
			}
			docs = append(docs, DocEntry{
				Name:     name,
				Path:     "/api/docs/download?name=" + name,
				Size:     info.Size(),
				Modified: info.ModTime().Format("2006-01-02 15:04:05"),
				Lang:     lang,
			})
		}

		sort.Slice(docs, func(i, j int) bool {
			if docs[i].Lang != docs[j].Lang {
				order := map[string]int{"en": 0, "ru": 1, "es": 2, "": 3}
				return order[docs[i].Lang] < order[docs[j].Lang]
			}
			return docs[i].Name < docs[j].Name
		})

		rootEntries := []string{"THEPLAN.md", "Architecture.md", "README.md", "MANIFEST-A1.md"}
		for _, name := range rootEntries {
			fullPath := filepath.Join(projectDir, name)
			if info, err := os.Stat(fullPath); err == nil {
				docs = append(docs, DocEntry{
					Name:     name,
					Path:     "/api/docs/download?name=" + name + "&root=1",
					Size:     info.Size(),
					Modified: info.ModTime().Format("2006-01-02 15:04:05"),
				})
			}
		}

		writeJSON(w, map[string]any{"count": len(docs), "entries": docs})
	}
}


// DocsDownloadHandler serves a documentation file as a download.
func DocsDownloadHandler(projectDir string) http.HandlerFunc {
	return func(w http.ResponseWriter, r *http.Request) {
		name := r.URL.Query().Get("name")
		if name == "" {
			http.Error(w, "name required", 400)
			return
		}
		root := r.URL.Query().Get("root")
		var baseDir string
		if root == "1" {
			baseDir = projectDir
		} else {
			baseDir = filepath.Join(projectDir, "docs")
		}
		name = filepath.Base(name)
		fullPath := filepath.Join(baseDir, name)
		if _, err := os.Stat(fullPath); os.IsNotExist(err) {
			http.Error(w, "not found", 404)
			return
		}
		w.Header().Set("Content-Type", "text/markdown; charset=utf-8")
		w.Header().Set("Content-Disposition", fmt.Sprintf(`attachment; filename="%s"`, name))
		http.ServeFile(w, r, fullPath)
	}
}


// DocsServeHandler renders a documentation file inline in the browser.
func DocsServeHandler(projectDir string) http.HandlerFunc {
	return func(w http.ResponseWriter, r *http.Request) {
		name := r.URL.Query().Get("name")
		if name == "" {
			http.Error(w, "name required", 400)
			return
		}
		root := r.URL.Query().Get("root")
		var baseDir string
		if root == "1" {
			baseDir = projectDir
		} else {
			baseDir = filepath.Join(projectDir, "docs")
		}
		name = filepath.Base(name)
		fullPath := filepath.Join(baseDir, name)
		if _, err := os.Stat(fullPath); os.IsNotExist(err) {
			http.Error(w, "not found", 404)
			return
		}
		w.Header().Set("Content-Type", "text/markdown; charset=utf-8")
		http.ServeFile(w, r, fullPath)
	}
}


// APIDocsHandler returns all registered API routes grouped by category.
func APIDocsHandler() http.HandlerFunc {
	return func(w http.ResponseWriter, r *http.Request) {
		docs := GetRouteDocs()
		categories := make(map[string][]RouteDoc)
		for _, d := range docs {
			cat := d.Category
			if cat == "" {
				cat = "uncategorized"
			}
			categories[cat] = append(categories[cat], d)
		}
		writeJSON(w, map[string]any{
			"ok":         true,
			"total":      len(docs),
			"categories": categories,
			"routes":     docs,
		})
	}
}


// APIDocsUIHandler serves an interactive HTML API documentation browser.
func APIDocsUIHandler() http.HandlerFunc {
	return func(w http.ResponseWriter, r *http.Request) {
		w.Header().Set("Content-Type", "text/html; charset=utf-8")
		w.Write([]byte(`<!DOCTYPE html>
<html lang="en"><head><meta charset="UTF-8"><meta name="viewport" content="width=device-width,initial-scale=1">
<title>API Documentation — ParanoidX</title>
<style>
*{box-sizing:border-box;margin:0;padding:0}
body{background:#0d0d0f;color:#e2e8f0;font-family:system-ui,-apple-system,sans-serif;padding:20px;max-width:1200px;margin:0 auto}
h1{font-size:1.3rem;margin-bottom:20px;color:#b8860b;border-bottom:1px solid #222;padding-bottom:10px}
h2{color:#b8860b;margin:24px 0 12px;padding-bottom:6px;border-bottom:1px solid #1e1e24}
.route{background:#16161b;border:1px solid #1e1e24;border-radius:8px;padding:12px 16px;margin:6px 0;display:flex;align-items:flex-start;gap:12px}
.route:hover{border-color:#b8860b}
.method{font-family:ui-monospace,monospace;font-size:0.75rem;font-weight:700;padding:3px 8px;border-radius:4px;min-width:60px;text-align:center;text-transform:uppercase}
.method-GET{background:#1a3a2a;color:#22c55e}
.method-POST{background:#1a2a3a;color:#3b82f6}
.method-PUT{background:#2a1a3a;color:#a855f7}
.method-DELETE{background:#3a1a1a;color:#ef4444}
.method-PATCH{background:#3a2a1a;color:#eab308}
.path{font-family:ui-monospace,monospace;font-size:0.9rem;color:#94a3b8;flex:1}
.desc{font-size:0.85rem;color:#64748b;margin-top:2px}
.category-badge{display:inline-block;padding:2px 8px;border-radius:999px;font-size:0.7rem;background:#141418;color:#b8860b;border:1px solid #333;margin-left:8px}
.loading{text-align:center;padding:40px;color:#666}
.error{color:#ef4444;padding:20px;text-align:center}
input{width:100%;padding:10px;margin:10px 0;background:#141418;border:1px solid #333;border-radius:8px;color:#fff;font-size:0.95rem}
</style></head><body>
<h1>📖 API Documentation — ParanoidX</h1>
<input id="search" placeholder="Search endpoints..." onkeyup="filter()">
<div id="loading" class="loading">Loading API docs...</div>
<div id="content" style="display:none"></div>
<script>
async function load(){try{
const r=await(await fetch('/api/docs')).json();if(!r.ok){throw new Error('Failed to load')}
window.docs=r.routes||[];render(window.docs)
}catch(e){document.getElementById('loading').innerHTML='<div class="error">Error: '+e.message+'</div>'}}
function render(docs){
const cats={};for(const d of docs){const c=d.category||'uncategorized';if(!cats[c])cats[c]=[];cats[c].push(d)}
let html='';const catOrder=['chat','admin','economy','radio','ai','container','dc','paranoidx','health','account','swap','bridge','market','escrow','royal','tracker','node','relay','simplex','did','rwa','wallet','vault','billing','genesis','pack','auction','buyback','transport','inquisitor','subscription','advertising','mining','pos','franchise','arbitration','services','misc','uncategorized'];
for(const c of catOrder){if(cats[c]){html+='<h2>'+c.charAt(0).toUpperCase()+c.slice(1)+' <span class="category-badge">'+cats[c].length+'</span></h2>'
for(const d of cats[c]){
const m=d.method||'GET';const mc='method-'+m;
html+='<div class="route"><span class="method '+mc+'">'+m+'</span>'
+'<div><div class="path">'+d.path+'</div><div class="desc">'+(d.description||'')+'</div></div></div>'}
delete cats[c]}}
for(const[c,routes]of Object.entries(cats)){html+='<h2>'+c+' <span class="category-badge">'+routes.length+'</span></h2>'
for(const d of routes){const m=d.method||'GET';html+='<div class="route"><span class="method method-'+m+'">'+m+'</span>'
+'<div><div class="path">'+d.path+'</div><div class="desc">'+(d.description||'')+'</div></div></div>'}}
document.getElementById('loading').style.display='none';document.getElementById('content').style.display='block';document.getElementById('content').innerHTML=html;}
function filter(){const q=document.getElementById('search').value.toLowerCase();if(!window.docs)return
const filtered=window.docs.filter(d=>d.path.toLowerCase().includes(q)||(d.description||'').toLowerCase().includes(q)||(d.category||'').toLowerCase().includes(q));render(filtered)}
load();
</script></body></html>`))
	}
}

// APIVersioningStrategy describes the API versioning approach.
// We use a single v1 namespace (/api/*) with backward-compatible additions.
// Breaking changes introduce a new version prefix (e.g., /api/v2/*).
// Current version is "v1", returned in /api/version as api_version.
// Version is set in main.go as const APIVersion.
// No deprecation timeline — existing endpoints remain supported indefinitely.
func APIVersioningStrategy() string {
	return "v1"
}


// APIDocsRegisterDefault registers all standard API routes in the documentation registry.
func APIDocsRegisterDefault() {
	routes := []RouteDoc{
		{Method: "GET", Path: "/api/version", Description: "Server version and build info", Category: "health"},
		{Method: "GET", Path: "/api/status", Description: "Full node status", Category: "health"},
		{Method: "GET", Path: "/api/health", Description: "Health check (bridge, uptime)", Category: "health"},
		{Method: "GET", Path: "/api/health/checks", Description: "Detailed health check breakdown", Category: "health"},

		{Method: "GET", Path: "/api/chat/history", Description: "Message history (chat_id=@N)", Category: "chat"},
		{Method: "GET", Path: "/api/chat/stream", Description: "SSE chat stream", Category: "chat"},
		{Method: "POST", Path: "/api/chat/send", Description: "Send message to contact", Category: "chat"},
		{Method: "POST", Path: "/api/chat/clear", Description: "Clear all chat history", Category: "chat"},
		{Method: "POST", Path: "/api/chat/delete", Description: "Delete single message by id", Category: "chat"},
		{Method: "POST", Path: "/api/chat/edit", Description: "Edit message text by id", Category: "chat"},
		{Method: "GET", Path: "/api/chat/contacts", Description: "Contact list", Category: "chat"},
		{Method: "GET", Path: "/api/chat/contact", Description: "Single contact (id=@N)", Category: "chat"},
		{Method: "POST", Path: "/api/chat/contact/alias", Description: "Set contact alias", Category: "chat"},
		{Method: "GET", Path: "/api/chat/contact/info", Description: "Contact info (count + last_message)", Category: "chat"},
		{Method: "GET", Path: "/api/chat/address", Description: "Chat address info", Category: "chat"},
		{Method: "POST", Path: "/api/chat/address/create", Description: "Create new chat address", Category: "chat"},
		{Method: "GET", Path: "/api/chat/connect", Description: "Connect to contact via link", Category: "chat"},
		{Method: "GET", Path: "/api/chat/qr", Description: "QR code for chat address", Category: "chat"},
		{Method: "GET", Path: "/api/chat/status", Description: "Chat status", Category: "chat"},
		{Method: "GET", Path: "/api/chat/search", Description: "Search messages (q=)", Category: "chat"},
		{Method: "GET", Path: "/api/chat/stats", Description: "Message statistics", Category: "chat"},
		{Method: "GET", Path: "/api/chat/export", Description: "Export chat history", Category: "chat"},
		{Method: "POST", Path: "/api/chat/backup", Description: "Backup/download chat", Category: "chat"},
		{Method: "POST", Path: "/api/chat/restore", Description: "Restore/upload chat", Category: "chat"},
		{Method: "POST", Path: "/api/chat/clear-old", Description: "Delete messages older than N days", Category: "chat"},
		{Method: "POST", Path: "/api/chat/pin", Description: "Toggle pin message", Category: "chat"},
		{Method: "POST", Path: "/api/chat/react", Description: "Toggle reaction emoji", Category: "chat"},
		{Method: "GET", Path: "/api/chat/server-status", Description: "Get server status message", Category: "chat"},
		{Method: "POST", Path: "/api/chat/server-status", Description: "Set server status message", Category: "chat"},
		{Method: "POST", Path: "/api/chat/broadcast", Description: "Send message to all contacts", Category: "chat"},
		{Method: "GET", Path: "/api/chat/last-message", Description: "Last message per contact", Category: "chat"},
		{Method: "POST", Path: "/api/chat/typing", Description: "Typing indicator", Category: "chat"},
		{Method: "POST", Path: "/api/chat/schedule", Description: "Schedule a message", Category: "chat"},
		{Method: "GET", Path: "/api/chat/auto-reply", Description: "List auto-reply rules", Category: "chat"},
		{Method: "POST", Path: "/api/chat/auto-reply", Description: "Add auto-reply rule", Category: "chat"},
		{Method: "GET", Path: "/api/chat/groups", Description: "List contact groups", Category: "chat"},
		{Method: "POST", Path: "/api/chat/groups", Description: "Create contact group", Category: "chat"},
		{Method: "GET", Path: "/api/chat/labels", Description: "List message labels", Category: "chat"},
		{Method: "POST", Path: "/api/chat/labels", Description: "Set message labels", Category: "chat"},
		{Method: "GET", Path: "/api/chat/drafts", Description: "List drafts", Category: "chat"},
		{Method: "POST", Path: "/api/chat/drafts", Description: "Save draft", Category: "chat"},
		{Method: "GET", Path: "/api/chat/webhook", Description: "List chat webhooks", Category: "chat"},
		{Method: "POST", Path: "/api/chat/webhook", Description: "Register chat webhook", Category: "chat"},
		{Method: "POST", Path: "/api/chat/archive", Description: "Archive old messages", Category: "chat"},
		{Method: "GET", Path: "/api/chat/archive/list", Description: "List archive files", Category: "chat"},
		{Method: "POST", Path: "/api/chat/archive/restore", Description: "Restore archived messages", Category: "chat"},

		{Method: "GET", Path: "/api/admin/audit-log", Description: "Audit log", Category: "admin"},
		{Method: "GET", Path: "/api/admin/metrics", Description: "System metrics", Category: "admin"},
		{Method: "GET", Path: "/api/admin/diagnostics", Description: "System diagnostics", Category: "admin"},
		{Method: "GET", Path: "/api/admin/status-page", Description: "Operational status", Category: "admin"},
		{Method: "GET", Path: "/api/admin/rate-limit-status", Description: "Rate limit hit counts", Category: "admin"},
		{Method: "GET", Path: "/api/admin/rate-limit-config", Description: "Rate limiter configuration", Category: "admin"},
		{Method: "PUT", Path: "/api/admin/rate-limit-config/v2", Description: "Per-endpoint rate limit config", Category: "admin"},
		{Method: "GET", Path: "/api/admin/content-filter", Description: "List blocked words", Category: "admin"},
		{Method: "POST", Path: "/api/admin/content-filter", Description: "Add/remove/set blocked words", Category: "admin"},
		{Method: "GET", Path: "/api/admin/content-filter/rules", Description: "Content filter rules", Category: "admin"},
		{Method: "POST", Path: "/api/admin/content-filter/rules", Description: "Add content filter rule", Category: "admin"},
		{Method: "DELETE", Path: "/api/admin/content-filter/rules", Description: "Delete content filter rule", Category: "admin"},
		{Method: "POST", Path: "/api/admin/content-filter/test", Description: "Test message against filter", Category: "admin"},
		{Method: "GET", Path: "/api/admin/docker", Description: "Docker container status", Category: "admin"},
		{Method: "GET", Path: "/api/admin/events", Description: "SSE real-time events", Category: "admin"},
		{Method: "GET", Path: "/api/admin/live", Description: "Live dashboard HTML", Category: "admin"},
		{Method: "GET", Path: "/api/admin/routes", Description: "List all API routes", Category: "admin"},
		{Method: "GET", Path: "/api/admin/disk-usage", Description: "Disk usage", Category: "admin"},
		{Method: "GET", Path: "/api/admin/disk-trend", Description: "Disk usage trend", Category: "admin"},
		{Method: "POST", Path: "/api/admin/disk-cleanup", Description: "Trigger disk cleanup", Category: "admin"},
		{Method: "GET", Path: "/api/admin/maintenance", Description: "Maintenance mode status", Category: "admin"},
		{Method: "POST", Path: "/api/admin/maintenance", Description: "Toggle maintenance mode", Category: "admin"},
		{Method: "GET", Path: "/api/admin/logs", Description: "View log files", Category: "admin"},
		{Method: "GET", Path: "/api/admin/info", Description: "Comprehensive node info", Category: "admin"},
		{Method: "POST", Path: "/api/admin/backup", Description: "Trigger USB backup", Category: "admin"},
		{Method: "POST", Path: "/api/admin/backup/verify", Description: "Verify backup integrity", Category: "admin"},
		{Method: "GET", Path: "/api/admin/webhook-queue", Description: "List webhook deliveries", Category: "admin"},
		{Method: "POST", Path: "/api/admin/webhook-queue", Description: "Enqueue webhook delivery", Category: "admin"},
		{Method: "DELETE", Path: "/api/admin/webhook-queue", Description: "Clear webhook queue", Category: "admin"},
		{Method: "GET", Path: "/api/admin/webhook-queue/stats", Description: "Webhook delivery stats", Category: "admin"},
		{Method: "POST", Path: "/api/admin/webhook-queue/retry-dead", Description: "Retry dead webhooks", Category: "admin"},
		{Method: "GET", Path: "/api/admin/config", Description: "Get threshold config", Category: "admin"},
		{Method: "POST", Path: "/api/admin/config", Description: "Set threshold config", Category: "admin"},
		{Method: "GET", Path: "/api/admin/ping", Description: "Watchdog ping", Category: "admin"},
		{Method: "GET", Path: "/api/admin/service/status", Description: "Service status", Category: "admin"},
		{Method: "POST", Path: "/api/admin/service/restart", Description: "Restart service", Category: "admin"},
		{Method: "GET", Path: "/api/admin/disk-alerts", Description: "Get disk alerts", Category: "admin"},
		{Method: "POST", Path: "/api/admin/disk-alerts/ack", Description: "Acknowledge disk alert", Category: "admin"},

		{Method: "GET", Path: "/api/economy/state", Description: "Economy state overview", Category: "economy"},
		{Method: "GET", Path: "/api/economy/oracle", Description: "Silver spot price", Category: "economy"},
		{Method: "GET", Path: "/api/economy/rates", Description: "Multi-currency rates", Category: "economy"},
		{Method: "GET", Path: "/api/economy/tokenomics", Description: "Tokenomics constants", Category: "economy"},
		{Method: "GET", Path: "/api/economy/dividend-admin", Description: "Dividend history and trigger", Category: "economy"},
		{Method: "POST", Path: "/api/economy/dividend-admin", Description: "Manually trigger dividend", Category: "economy"},
		{Method: "GET", Path: "/api/economy/wheel", Description: "Golden wheel", Category: "economy"},
		{Method: "GET", Path: "/api/economy/crafting", Description: "Crafting info", Category: "economy"},
		{Method: "GET", Path: "/api/economy/deflate", Description: "Deflation stats", Category: "economy"},
		{Method: "GET", Path: "/api/economy/treasury-forecast", Description: "Treasury forecast", Category: "economy"},

		{Method: "GET", Path: "/api/radio", Description: "Radio stations/playlist", Category: "radio"},
		{Method: "GET", Path: "/api/radio/stream", Description: "MP3 stream", Category: "radio"},
		{Method: "GET", Path: "/api/radio/schedule", Description: "List content schedules", Category: "radio"},
		{Method: "POST", Path: "/api/radio/schedule", Description: "Create content schedule", Category: "radio"},
		{Method: "DELETE", Path: "/api/radio/schedule", Description: "Delete content schedule", Category: "radio"},
		{Method: "POST", Path: "/api/radio/schedule/optimize", Description: "Optimize playlist rotation", Category: "radio"},
		{Method: "GET", Path: "/api/radio/schedule/stats", Description: "Schedule stats by type", Category: "radio"},
		{Method: "GET", Path: "/api/radio/schedule/rotation", Description: "Get rotation mode", Category: "radio"},
		{Method: "POST", Path: "/api/radio/schedule/rotation", Description: "Set rotation mode", Category: "radio"},
		{Method: "GET", Path: "/api/radio/schedule/time-slots", Description: "Get time-of-day slots", Category: "radio"},
		{Method: "POST", Path: "/api/radio/schedule/time-slots", Description: "Set time-of-day slots", Category: "radio"},

		{Method: "POST", Path: "/api/ai/chat", Description: "Ask AI Steward", Category: "ai"},
		{Method: "GET", Path: "/api/ai/profiles", Description: "List AI personality profiles", Category: "ai"},
		{Method: "POST", Path: "/api/ai/profiles", Description: "Create AI profile", Category: "ai"},
		{Method: "GET", Path: "/api/ai/health", Description: "AI health check", Category: "ai"},

		{Method: "GET", Path: "/api/dc/list", Description: "List DC containers", Category: "dc"},
		{Method: "GET", Path: "/api/dc/status", Description: "DC cloud health", Category: "dc"},
		{Method: "POST", Path: "/api/dc/seed", Description: "Seed container for P2P", Category: "dc"},

		{Method: "GET", Path: "/api/inquisitor/report", Description: "Inquisitor report", Category: "chat"},

		{Method: "POST", Path: "/api/chat/invoice/create", Description: "Create invoice", Category: "chat"},
		{Method: "GET", Path: "/api/chat/invoice/list", Description: "List invoices", Category: "chat"},
		{Method: "POST", Path: "/api/chat/invoice/pay", Description: "Pay invoice", Category: "chat"},
		{Method: "GET", Path: "/api/chat/invoice/stats", Description: "Invoice statistics", Category: "chat"},

		{Method: "POST", Path: "/api/chat/auto-delete", Description: "Configure auto-delete", Category: "chat"},
		{Method: "POST", Path: "/api/panic", Description: "PANIC wipe", Category: "container"},

		{Method: "POST", Path: "/api/swap/create", Description: "Create BTC atomic swap (needs confirm)", Category: "swap"},
		{Method: "POST", Path: "/api/swap/confirm", Description: "Confirm pending swap", Category: "swap"},
		{Method: "POST", Path: "/api/swap/cancel", Description: "Cancel unclaimed swap", Category: "swap"},
		{Method: "POST", Path: "/api/swap/claim", Description: "Claim swap with secret", Category: "swap"},
		{Method: "POST", Path: "/api/swap/refund", Description: "Refund expired swap", Category: "swap"},
		{Method: "GET", Path: "/api/swap/list", Description: "List all swaps", Category: "swap"},

		{Method: "GET", Path: "/api/bridge/status", Description: "Bridge transfer status summary", Category: "bridge"},

		{Method: "GET", Path: "/api/admin/container/list", Description: "List all Docker containers", Category: "admin"},
		{Method: "GET", Path: "/api/admin/container/logs", Description: "Get container logs (name=, tail=)", Category: "admin"},

		{Method: "GET", Path: "/api/mobile/status", Description: "Lightweight mobile status", Category: "health"},
	}

	for _, r := range routes {
		RegisterRoute(r.Method, r.Path, r.Description, r.Category)
	}
}
