package api

import (
	"encoding/json"
	"fmt"
	"io"
	"net/http"
	"os"
	"path/filepath"
	"strings"
	"time"

	"px-transport/internal/state"
)

// BridgeChainStatusHandler returns the full bridge chain status
func BridgeChainStatusHandler(b state.BridgeState) http.HandlerFunc {
	return func(w http.ResponseWriter, r *http.Request) {
		if r.Method != "GET" {
			http.Error(w, "GET required", 405)
			return
		}

		// Get bridge chain status from client-bridge scripts
		status := getBridgeChainStatus()
		writeJSON(w, status)
	}
}

// BridgeStartHandler starts the bridge chain
func BridgeStartHandler(b state.BridgeState) http.HandlerFunc {
	return func(w http.ResponseWriter, r *http.Request) {
		if r.Method != "POST" {
			http.Error(w, "POST required", 405)
			return
		}
		
		// Execute client-bridge.sh start
		go executeBridgeCommand("start")
		writeJSON(w, map[string]any{"ok": true, "message": "Bridge start initiated"})
	}
}

// BridgeStopHandler stops the bridge chain
func BridgeStopHandler(b state.BridgeState) http.HandlerFunc {
	return func(w http.ResponseWriter, r *http.Request) {
		if r.Method != "POST" {
			http.Error(w, "POST required", 405)
			return
		}
		
		go executeBridgeCommand("stop")
		writeJSON(w, map[string]any{"ok": true, "message": "Bridge stop initiated"})
	}
}

// OnionCheckHandler checks if an onion service is reachable via Tor
func OnionCheckHandler() http.HandlerFunc {
	return func(w http.ResponseWriter, r *http.Request) {
		if r.Method != "GET" {
			http.Error(w, "GET required", 405)
			return
		}
		
		onion := r.URL.Query().Get("onion")
		port := r.URL.Query().Get("port")
		if onion == "" || port == "" {
			http.Error(w, "onion and port required", 400)
			return
		}
		
		// Check via Tor SOCKS5 proxy
		reachable := checkOnionViaTor(onion, port)
		writeJSON(w, map[string]any{
			"onion": onion,
			"port": port,
			"reachable": reachable,
			"checked_at": time.Now().Unix(),
		})
	}
}

// ConfigListHandler lists available configs
func ConfigListHandler() http.HandlerFunc {
	return func(w http.ResponseWriter, r *http.Request) {
		if r.Method != "GET" {
			http.Error(w, "GET required", 405)
			return
		}
		
		vpnType := r.URL.Query().Get("vpn") // "1", "2", or "auto"
		files := listConfigFiles(vpnType)
		writeJSON(w, map[string]any{"files": files})
	}
}

// ConfigUploadHandler uploads a new config file
func ConfigUploadHandler() http.HandlerFunc {
	return func(w http.ResponseWriter, r *http.Request) {
		if r.Method != "POST" {
			http.Error(w, "POST required", 405)
			return
		}
		
		// Parse multipart form
		err := r.ParseMultipartForm(32 << 20) // 32MB max
		if err != nil {
			http.Error(w, "Failed to parse form", 400)
			return
		}
		
		file, header, err := r.FormFile("file")
		if err != nil {
			http.Error(w, "No file uploaded", 400)
			return
		}
		defer file.Close()
		
		configType := r.FormValue("type") // "vpn1" or "vpn2"
		proto := r.FormValue("proto")
		
		if configType == "" || proto == "" {
			http.Error(w, "type and proto required", 400)
			return
		}
		
		// Save file
		savePath, err := saveConfigFile(file, header.Filename, configType, proto)
		if err != nil {
			http.Error(w, "Failed to save: "+err.Error(), 500)
			return
		}
		
		writeJSON(w, map[string]any{"ok": true, "path": savePath})
	}
}

// ConfigUseHandler sets a config as active
func ConfigUseHandler() http.HandlerFunc {
	return func(w http.ResponseWriter, r *http.Request) {
		if r.Method != "POST" {
			http.Error(w, "POST required", 405)
			return
		}
		
		var req struct {
			Type string `json:"type"`
			Path string `json:"path"`
		}
		if err := json.NewDecoder(r.Body).Decode(&req); err != nil && err != io.EOF {
			http.Error(w, "Invalid JSON", 400)
			return
		}
		
		err := setActiveConfig(req.Type, req.Path)
		if err != nil {
			http.Error(w, err.Error(), 500)
			return
		}
		
		writeJSON(w, map[string]any{"ok": true})
	}
}

// ConfigDeleteHandler deletes a config file
func ConfigDeleteHandler() http.HandlerFunc {
	return func(w http.ResponseWriter, r *http.Request) {
		if r.Method != "POST" {
			http.Error(w, "POST required", 405)
			return
		}
		
		var req struct {
			Type string `json:"type"`
			Path string `json:"path"`
		}
		if err := json.NewDecoder(r.Body).Decode(&req); err != nil && err != io.EOF {
			http.Error(w, "Invalid JSON", 400)
			return
		}
		
		err := deleteConfigFile(req.Type, req.Path)
		if err != nil {
			http.Error(w, err.Error(), 500)
			return
		}
		
		writeJSON(w, map[string]any{"ok": true})
	}
}

// ConfigImportURLHandler imports a config from URL
func ConfigImportURLHandler() http.HandlerFunc {
	return func(w http.ResponseWriter, r *http.Request) {
		if r.Method != "POST" {
			http.Error(w, "POST required", 405)
			return
		}
		
		var req struct {
			URL   string `json:"url"`
			Proto string `json:"proto"`
			Type  string `json:"type"`
		}
		if err := json.NewDecoder(r.Body).Decode(&req); err != nil && err != io.EOF {
			http.Error(w, "Invalid JSON", 400)
			return
		}
		
		path, err := importConfigFromURL(req.URL, req.Proto, req.Type)
		if err != nil {
			http.Error(w, err.Error(), 500)
			return
		}
		
		writeJSON(w, map[string]any{"ok": true, "path": path})
	}
}

// ConfigAutoHandler saves auto-discovery settings
func ConfigAutoHandler() http.HandlerFunc {
	return func(w http.ResponseWriter, r *http.Request) {
		if r.Method != "POST" {
			http.Error(w, "POST required", 405)
			return
		}
		
		var req struct {
			WatchDir      string   `json:"watch_dir"`
			Subscriptions []string `json:"subscriptions"`
			Interval      int      `json:"interval"`
		}
		if err := json.NewDecoder(r.Body).Decode(&req); err != nil && err != io.EOF {
			http.Error(w, "Invalid JSON", 400)
			return
		}
		
		err := saveAutoConfig(req.WatchDir, req.Subscriptions)
		if err != nil {
			http.Error(w, err.Error(), 500)
			return
		}
		
		writeJSON(w, map[string]any{"ok": true})
	}
}

// ConfigScanHandler triggers auto-discovery scan
func ConfigScanHandler() http.HandlerFunc {
	return func(w http.ResponseWriter, r *http.Request) {
		if r.Method != "POST" {
			http.Error(w, "POST required", 405)
			return
		}
		
		go runConfigScan()
		writeJSON(w, map[string]any{"ok": true, "message": "Scan started"})
	}
}

// ConfigFetchSubsHandler fetches subscription configs
func ConfigFetchSubsHandler() http.HandlerFunc {
	return func(w http.ResponseWriter, r *http.Request) {
		if r.Method != "POST" {
			http.Error(w, "POST required", 405)
			return
		}
		
		go fetchSubscriptions()
		writeJSON(w, map[string]any{"ok": true, "message": "Fetch started"})
	}
}

// TorBackupKeysHandler backs up current onion keys
func TorBackupKeysHandler() http.HandlerFunc {
	return func(w http.ResponseWriter, r *http.Request) {
		if r.Method != "POST" {
			http.Error(w, "POST required", 405)
			return
		}
		
		err := backupOnionKeys()
		if err != nil {
			http.Error(w, err.Error(), 500)
			return
		}
		
		writeJSON(w, map[string]any{"ok": true, "message": "Keys backed up"})
	}
}

// TorRestoreKeysHandler restores onion keys from backup
func TorRestoreKeysHandler() http.HandlerFunc {
	return func(w http.ResponseWriter, r *http.Request) {
		if r.Method != "POST" {
			http.Error(w, "POST required", 405)
			return
		}
		
		err := restoreOnionKeys()
		if err != nil {
			http.Error(w, err.Error(), 500)
			return
		}
		
		writeJSON(w, map[string]any{"ok": true, "message": "Keys restored"})
	}
}

// TorTestClientHandler tests the Tor client
func TorTestClientHandler() http.HandlerFunc {
	return func(w http.ResponseWriter, r *http.Request) {
		// Allow both GET and POST for dashboard compatibility
		if r.Method != "GET" && r.Method != "POST" {
			http.Error(w, "GET required", 405)
			return
		}
		
		reachable := testTorClient()
		writeJSON(w, map[string]any{
			"reachable": reachable,
			"checked_at": time.Now().Unix(),
		})
	}
}

// LogsHandler returns filtered logs
func LogsHandler() http.HandlerFunc {
	return func(w http.ResponseWriter, r *http.Request) {
		if r.Method != "GET" {
			http.Error(w, "GET required", 405)
			return
		}
		
		filter := r.URL.Query().Get("filter")
		limit := r.URL.Query().Get("limit")
		
		logs := getLogs(filter, limit)
		writeJSON(w, map[string]any{"logs": logs})
	}
}

// ========== Helper Functions ==========

func getBridgeChainStatus() map[string]any {
	// Read status from client-bridge config files
	CONFIG_DIR := os.ExpandEnv("$HOME/.local/share/ParanoidX/client-bridge")
	PID_DIR := filepath.Join(CONFIG_DIR, "pids")
	
	vpn1 := getVPN1Status(CONFIG_DIR, PID_DIR)
	vpn2 := getVPN2Status(CONFIG_DIR, PID_DIR)
	tor := getTorStatus(CONFIG_DIR, PID_DIR)
	chain := getChainStatus(CONFIG_DIR, PID_DIR, vpn2)
	
	return map[string]any{
		"vpn1": vpn1,
		"vpn2": vpn2,
		"tor": tor,
		"chain": chain,
	}
}

func getVPN1Status(CONFIG_DIR, PID_DIR string) map[string]any {
	vpn1 := map[string]any{
		"configured": false,
		"running": false,
		"proto": nil,
		"config": nil,
		"ip": nil,
	}
	
	protoPath := filepath.Join(CONFIG_DIR, "vpn1_proto")
	configPath := filepath.Join(CONFIG_DIR, "vpn1_config")
	ipPath := filepath.Join(CONFIG_DIR, "vpn1_ip")
	
	if _, err := os.Stat(protoPath); err == nil {
		vpn1["configured"] = true
		if b, err := os.ReadFile(protoPath); err == nil {
			vpn1["proto"] = strings.TrimSpace(string(b))
		}
		if b, err := os.ReadFile(configPath); err == nil {
			vpn1["config"] = strings.TrimSpace(string(b))
		}
	}
	
	// Check if running
	for _, pidFile := range []string{"vpn1.pid", "vpn1-hy2.pid"} {
		pidPath := filepath.Join(PID_DIR, pidFile)
		if isProcessRunning(pidPath) {
			vpn1["running"] = true
			break
		}
	}
	
	if _, err := os.Stat(ipPath); err == nil {
		if b, err := os.ReadFile(ipPath); err == nil {
			vpn1["ip"] = strings.TrimSpace(string(b))
		}
	}
	
	return vpn1
}

func getVPN2Status(CONFIG_DIR, PID_DIR string) map[string]any {
	vpn2 := map[string]any{
		"configured": false,
		"running": false,
		"proto": nil,
		"config": nil,
		"socks_port": nil,
	}
	
	protoPath := filepath.Join(CONFIG_DIR, "vpn2_proto")
	configPath := filepath.Join(CONFIG_DIR, "vpn2_config")
	socksPath := filepath.Join(CONFIG_DIR, "vpn2_socks_port")
	
	if _, err := os.Stat(protoPath); err == nil {
		vpn2["configured"] = true
		if b, err := os.ReadFile(protoPath); err == nil {
			vpn2["proto"] = strings.TrimSpace(string(b))
		}
		if b, err := os.ReadFile(configPath); err == nil {
			vpn2["config"] = strings.TrimSpace(string(b))
		}
	}
	
	pidPath := filepath.Join(PID_DIR, "vpn2.pid")
	if isProcessRunning(pidPath) {
		vpn2["running"] = true
	}
	
	if _, err := os.Stat(socksPath); err == nil {
		if b, err := os.ReadFile(socksPath); err == nil {
			vpn2["socks_port"] = strings.TrimSpace(string(b))
		}
	}
	
	return vpn2
}

func getTorStatus(CONFIG_DIR, PID_DIR string) map[string]any {
	tor := map[string]any{
		"client_running": false,
		"socks_port": 9050,
		"onion_reachable": false,
	}
	
	// Check if tor client process exists with our config
	// This is a simplified check - in reality we'd check process list
	// For now, return basic info
	
	return tor
}

func getChainStatus(CONFIG_DIR, PID_DIR string, vpn2 map[string]any) map[string]any {
	chain := map[string]any{
		"vpn1_to_vpn2": false,
		"vpn2_to_tor": false,
		"full_chain": false,
	}
	
	// These would be tested via actual connectivity
	// For now return basic structure
	
	return chain
}

func executeBridgeCommand(cmd string) {
	// Execute client-bridge.sh command
	// In production, this would use exec.Command
}

func checkOnionViaTor(onion, port string) bool {
	// Check via Tor SOCKS5 proxy
	// This would use golang.org/x/net/proxy
	return false // Placeholder
}

func listConfigFiles(vpnType string) []map[string]any {
	baseDir := os.ExpandEnv("$HOME/.local/share/ParanoidX/client-bridge")
	var dirs []string
	
	switch vpnType {
	case "1":
		dirs = []string{filepath.Join(baseDir, "vpn1")}
	case "2":
		dirs = []string{filepath.Join(baseDir, "vpn2")}
	default:
		dirs = []string{
			filepath.Join(baseDir, "vpn1"),
			filepath.Join(baseDir, "vpn2"),
			os.ExpandEnv("$HOME/ParanoidX/configs"),
		}
	}
	
	var files []map[string]any
	for _, dir := range dirs {
		entries, err := os.ReadDir(dir)
		if err != nil {
			continue
		}
		for _, entry := range entries {
			if entry.IsDir() {
				continue
			}
			name := entry.Name()
			// Skip .proto files
			if strings.HasSuffix(name, ".proto") {
				continue
			}
			info, _ := entry.Info()
			files = append(files, map[string]any{
				"name": name,
				"path": filepath.Join(dir, name),
				"size": info.Size(),
				"mtime": info.ModTime().Format("2006-01-02 15:04"),
				"proto": getProtoFromFile(filepath.Join(dir, name)),
			})
		}
	}
	return files
}

func getProtoFromFile(path string) string {
	protoPath := path + ".proto"
	if b, err := os.ReadFile(protoPath); err == nil {
		return strings.TrimSpace(string(b))
	}
	// Infer from extension
	ext := strings.ToLower(filepath.Ext(path))
	switch ext {
	case ".conf": return "wireguard"
	case ".ovpn": return "openvpn"
	case ".json": return "xray"
	default: return "unknown"
	}
}

func saveConfigFile(file io.Reader, filename, configType, proto string) (string, error) {
	baseDir := os.ExpandEnv("$HOME/.local/share/ParanoidX/client-bridge")
	var targetDir string
	switch configType {
	case "vpn1":
		targetDir = filepath.Join(baseDir, "vpn1")
	case "vpn2":
		targetDir = filepath.Join(baseDir, "vpn2")
	default:
		return "", os.ErrInvalid
	}
	
	if err := os.MkdirAll(targetDir, 0700); err != nil {
		return "", err
	}
	
	targetPath := filepath.Join(targetDir, filename)
	out, err := os.Create(targetPath)
	if err != nil {
		return "", err
	}
	defer out.Close()
	
	_, err = io.Copy(out, file)
	if err != nil {
		return "", err
	}
	
	// Save proto file
	protoPath := targetPath + ".proto"
	err = os.WriteFile(protoPath, []byte(proto), 0600)
	if err != nil {
		return "", err
	}
	
	return targetPath, nil
}

func setActiveConfig(configType, path string) error {
	baseDir := os.ExpandEnv("$HOME/.local/share/ParanoidX/client-bridge")
	CONFIG_DIR := baseDir
	
	var configFile, protoFile string
	switch configType {
	case "vpn1":
		configFile = filepath.Join(CONFIG_DIR, "vpn1_config")
		protoFile = filepath.Join(CONFIG_DIR, "vpn1_proto")
	case "vpn2":
		configFile = filepath.Join(CONFIG_DIR, "vpn2_config")
		protoFile = filepath.Join(CONFIG_DIR, "vpn2_proto")
	default:
		return os.ErrInvalid
	}
	
	// Copy config to active location
	data, err := os.ReadFile(path)
	if err != nil {
		return err
	}
	
	activePath := filepath.Join(filepath.Dir(configFile), filepath.Base(path))
	err = os.WriteFile(activePath, data, 0600)
	if err != nil {
		return err
	}
	
	// Update config file pointer
	err = os.WriteFile(configFile, []byte(activePath), 0600)
	if err != nil {
		return err
	}
	
	// Update proto
	protoPath := activePath + ".proto"
	if b, err := os.ReadFile(protoPath); err == nil {
		err = os.WriteFile(protoFile, b, 0600)
	}
	
	return err
}

func deleteConfigFile(configType, path string) error {
	// Don't delete if it's the active config
	baseDir := os.ExpandEnv("$HOME/.local/share/ParanoidX/client-bridge")
	CONFIG_DIR := baseDir

	// Path traversal guard: the requested path must live inside CONFIG_DIR.
	absPath, err := filepath.Abs(path)
	if err != nil {
		return fmt.Errorf("invalid path: %w", err)
	}
	absDir, err := filepath.Abs(CONFIG_DIR)
	if err != nil {
		return fmt.Errorf("invalid config dir: %w", err)
	}
	if !strings.HasPrefix(absPath, absDir+string(filepath.Separator)) {
		return os.ErrPermission // refuse anything outside client-bridge dir
	}

	var activeConfigPath string
	switch configType {
	case "vpn1":
		activeConfigPath = filepath.Join(CONFIG_DIR, "vpn1_config")
	case "vpn2":
		activeConfigPath = filepath.Join(CONFIG_DIR, "vpn2_config")
	}
	
	if activeConfigPath != "" {
		if b, err := os.ReadFile(activeConfigPath); err == nil {
			if strings.TrimSpace(string(b)) == path {
				return os.ErrPermission // Can't delete active config
			}
		}
	}
	
	// Delete config and proto file
	os.Remove(path)
	os.Remove(path + ".proto")
	return nil
}

func importConfigFromURL(url, proto, configType string) (string, error) {
	// Download and save config from URL
	// This would parse vmess://, vless://, trojan://, ss:// URLs
	return "", nil // Placeholder
}

func saveAutoConfig(watchDir string, subscriptions []string) error {
	config := map[string]any{
		"watch_dir": watchDir,
		"subscriptions": subscriptions,
	}
	
	data, _ := json.MarshalIndent(config, "", "  ")
	return os.WriteFile(os.ExpandEnv("$HOME/.local/share/ParanoidX/auto-config.json"), data, 0600)
}

func runConfigScan() {
	// Scan watch directory for configs
	// Import them automatically
}

func fetchSubscriptions() {
	// Fetch configs from subscription URLs
	// Parse and save
}

func backupOnionKeys() error {
	sourceDir := os.ExpandEnv("$HOME/.local/share/ParanoidX/tor/hidden_services")
	backupDir := os.ExpandEnv("$HOME/.local/share/ParanoidX/tor-keys-backup")
	
	services := []string{"smp", "xftp", "ice", "dashboard", "auditor"}
	for _, svc := range services {
		src := filepath.Join(sourceDir, svc)
		dst := filepath.Join(backupDir, svc)
		
		if err := os.MkdirAll(dst, 0700); err != nil {
			return err
		}
		
		files := []string{"hs_ed25519_secret_key", "hs_ed25519_public_key", "hostname"}
		for _, f := range files {
			srcFile := filepath.Join(src, f)
			dstFile := filepath.Join(dst, f)
			data, err := os.ReadFile(srcFile)
			if err != nil {
				return err
			}
			if err := os.WriteFile(dstFile, data, 0600); err != nil {
				return err
			}
		}
	}
	return nil
}

func restoreOnionKeys() error {
	backupDir := os.ExpandEnv("$HOME/.local/share/ParanoidX/tor-keys-backup")
	targetDir := os.ExpandEnv("$HOME/.local/share/ParanoidX/tor/hidden_services")
	
	services := []string{"smp", "xftp", "ice", "dashboard", "auditor"}
	for _, svc := range services {
		src := filepath.Join(backupDir, svc)
		dst := filepath.Join(targetDir, svc)
		
		if err := os.MkdirAll(dst, 0700); err != nil {
			return err
		}
		
		files := []string{"hs_ed25519_secret_key", "hs_ed25519_public_key", "hostname"}
		for _, f := range files {
			srcFile := filepath.Join(src, f)
			dstFile := filepath.Join(dst, f)
			data, err := os.ReadFile(srcFile)
			if err != nil {
				return err
			}
			if err := os.WriteFile(dstFile, data, 0600); err != nil {
				return err
			}
		}
	}
	return nil
}

func testTorClient() bool {
	// Test Tor client connectivity
	return false // Placeholder
}

func getLogs(filter, limit string) []map[string]any {
	// Read logs from log files
	// Filter by level and source
	return []map[string]any{} // Placeholder
}

func isProcessRunning(pidPath string) bool {
	if _, err := os.Stat(pidPath); err != nil {
		return false
	}
	b, err := os.ReadFile(pidPath)
	if err != nil {
		return false
	}
	pid := strings.TrimSpace(string(b))
	if pid == "" {
		return false
	}
	// Check if process exists
	// In Go: os.FindProcess(pid) then process.Signal(syscall.Signal(0))
	return true // Simplified
}

// BridgeStartVPN1Handler starts VPN1
func BridgeStartVPN1Handler(b state.BridgeState) http.HandlerFunc {
	return func(w http.ResponseWriter, r *http.Request) {
		if r.Method != "POST" {
			http.Error(w, "POST required", 405)
			return
		}
		go executeBridgeCommand("start-vpn1")
		writeJSON(w, map[string]any{"ok": true, "message": "VPN1 start initiated"})
	}
}

// BridgeStopVPN1Handler stops VPN1
func BridgeStopVPN1Handler(b state.BridgeState) http.HandlerFunc {
	return func(w http.ResponseWriter, r *http.Request) {
		if r.Method != "POST" {
			http.Error(w, "POST required", 405)
			return
		}
		go executeBridgeCommand("stop-vpn1")
		writeJSON(w, map[string]any{"ok": true, "message": "VPN1 stop initiated"})
	}
}

// BridgeStartVPN2Handler starts VPN2
func BridgeStartVPN2Handler(b state.BridgeState) http.HandlerFunc {
	return func(w http.ResponseWriter, r *http.Request) {
		if r.Method != "POST" {
			http.Error(w, "POST required", 405)
			return
		}
		go executeBridgeCommand("start-vpn2")
		writeJSON(w, map[string]any{"ok": true, "message": "VPN2 start initiated"})
	}
}

// BridgeStopVPN2Handler stops VPN2
func BridgeStopVPN2Handler(b state.BridgeState) http.HandlerFunc {
	return func(w http.ResponseWriter, r *http.Request) {
		if r.Method != "POST" {
			http.Error(w, "POST required", 405)
			return
		}
		go executeBridgeCommand("stop-vpn2")
		writeJSON(w, map[string]any{"ok": true, "message": "VPN2 stop initiated"})
	}
}

// SystemConfigHandler saves system configuration
func SystemConfigHandler() http.HandlerFunc {
	return func(w http.ResponseWriter, r *http.Request) {
		if r.Method == "GET" {
			// Return current system config
			dataDir := os.Getenv("DATA_DIR")
			if dataDir == "" {
				home, _ := os.UserHomeDir()
				dataDir = filepath.Join(home, ".local/share/ParanoidX")
			}
			configPath := filepath.Join(dataDir, "system-config.json")
			if b, err := os.ReadFile(configPath); err == nil {
				writeJSON(w, map[string]any{"config": string(b)})
				return
			}
			writeJSON(w, map[string]any{"config": "{}"})
			return
		}
		if r.Method != "POST" {
			http.Error(w, "POST required", 405)
			return
		}
		var req map[string]any
		if err := json.NewDecoder(r.Body).Decode(&req); err != nil && err != io.EOF {
			http.Error(w, "Invalid JSON", 400)
			return
		}
		// Empty body is OK - use defaults
		if req == nil {
			req = make(map[string]any)
		}
		err := saveSystemConfig(req)
		if err != nil {
			http.Error(w, err.Error(), 500)
			return
		}
		writeJSON(w, map[string]any{"ok": true})
	}
}

// RestartNodeHandler restarts the node
func RestartNodeHandler() http.HandlerFunc {
	return func(w http.ResponseWriter, r *http.Request) {
		if r.Method != "POST" {
			http.Error(w, "POST required", 405)
			return
		}
		go func() {
			time.Sleep(1 * time.Second)
			os.Exit(0) // systemd will restart
		}()
		writeJSON(w, map[string]any{"ok": true, "message": "Node restart initiated"})
	}
}

// WipeConfigsHandler wipes all configs
func WipeConfigsHandler() http.HandlerFunc {
	return func(w http.ResponseWriter, r *http.Request) {
		if r.Method != "POST" {
			http.Error(w, "POST required", 405)
			return
		}
		err := wipeAllConfigs()
		if err != nil {
			http.Error(w, err.Error(), 500)
			return
		}
		writeJSON(w, map[string]any{"ok": true, "message": "All configs wiped"})
	}
}

// ResetConfigsHandler resets to defaults
func ResetConfigsHandler() http.HandlerFunc {
	return func(w http.ResponseWriter, r *http.Request) {
		if r.Method != "POST" {
			http.Error(w, "POST required", 405)
			return
		}
		err := resetConfigs()
		if err != nil {
			http.Error(w, err.Error(), 500)
			return
		}
		writeJSON(w, map[string]any{"ok": true, "message": "Reset to defaults"})
	}
}

// System config helpers
func saveSystemConfig(config map[string]any) error {
	dataDir := os.Getenv("DATA_DIR")
	if dataDir == "" {
		home, _ := os.UserHomeDir()
		dataDir = filepath.Join(home, ".local/share/ParanoidX")
	}
	configPath := filepath.Join(dataDir, "system-config.json")
	data, _ := json.MarshalIndent(config, "", "  ")
	return os.WriteFile(configPath, data, 0600)
}

func wipeAllConfigs() error {
	baseDir := os.ExpandEnv("$HOME/.local/share/ParanoidX/client-bridge")
	dirs := []string{
		filepath.Join(baseDir, "vpn1"),
		filepath.Join(baseDir, "vpn2"),
	}
	for _, dir := range dirs {
		entries, err := os.ReadDir(dir)
		if err != nil {
			continue
		}
		for _, entry := range entries {
			if entry.IsDir() { continue }
			name := entry.Name()
			if strings.HasSuffix(name, ".proto") { continue }
			os.Remove(filepath.Join(dir, name))
			os.Remove(filepath.Join(dir, name+".proto"))
		}
	}
	// Clear active config pointers
	CONFIG_DIR := baseDir
	os.Remove(filepath.Join(CONFIG_DIR, "vpn1_config"))
	os.Remove(filepath.Join(CONFIG_DIR, "vpn1_proto"))
	os.Remove(filepath.Join(CONFIG_DIR, "vpn2_config"))
	os.Remove(filepath.Join(CONFIG_DIR, "vpn2_proto"))
	return nil
}

func resetConfigs() error {
	// Reset to factory defaults - same as wipe but keep backup keys
	return wipeAllConfigs()
}
