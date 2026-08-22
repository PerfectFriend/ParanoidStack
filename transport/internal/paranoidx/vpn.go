// Package paranoidx implements the ParanoidX multi-layer proxy chain
package paranoidx

import (
	"encoding/json"
	"fmt"
	"log/slog"
	"net/http"
	"os"
	"os/exec"
	"path/filepath"
	"strings"
	"sync"
	"time"
)

// VPNProfile represents a stored WireGuard configuration.
type VPNProfile struct {
	Name        string `json:"name"`
	Description string `json:"description,omitempty"`
	ConfigFile  string `json:"config_file"`
	Endpoint    string `json:"endpoint,omitempty"`
	Interface   string `json:"interface"`
	Active      bool   `json:"active"`
}

// VPNManager manages WireGuard VPN profiles and lifecycle.
type VPNManager struct {
	mu         sync.RWMutex
	DataDir    string
	Profiles   map[string]*VPNProfile
	activeName string
}

// NewVPNManager creates a VPN profile manager.
func NewVPNManager(dataDir string) *VPNManager {
	m := &VPNManager{
		DataDir:  filepath.Join(dataDir, "paranoidx", "vpn"),
		Profiles: map[string]*VPNProfile{},
	}
	m.loadProfiles()
	return m
}

func (m *VPNManager) profilesPath() string {
	return filepath.Join(m.DataDir, "profiles.json")
}

func (m *VPNManager) configPath(name string) string {
	return filepath.Join(m.DataDir, name+".conf")
}

func (m *VPNManager) loadProfiles() {
	os.MkdirAll(m.DataDir, 0755)
	p := m.profilesPath()
	data, err := os.ReadFile(p)
	if err != nil {
		return
	}
	var list []*VPNProfile
	if err := json.Unmarshal(data, &list); err != nil {
		slog.Warn("paranoidx: parse vpn profiles", "err", err)
		return
	}
	m.mu.Lock()
	defer m.mu.Unlock()
	for _, prof := range list {
		m.Profiles[prof.Name] = prof
		if prof.Active {
			m.activeName = prof.Name
		}
	}
}

func (m *VPNManager) saveProfiles() {
	m.mu.RLock()
	list := make([]*VPNProfile, 0, len(m.Profiles))
	for _, prof := range m.Profiles {
		list = append(list, prof)
	}
	m.mu.RUnlock()

	os.MkdirAll(m.DataDir, 0755)
	data, _ := json.MarshalIndent(list, "", "  ")
	os.WriteFile(m.profilesPath(), data, 0644)
}

// ListProfiles returns all stored VPN profiles.
func (m *VPNManager) ListProfiles() []*VPNProfile {
	m.mu.RLock()
	defer m.mu.RUnlock()
	list := make([]*VPNProfile, 0, len(m.Profiles))
	for _, prof := range m.Profiles {
		list = append(list, prof)
	}
	return list
}

// AddProfile stores a new WireGuard profile (config content + metadata).
func (m *VPNManager) AddProfile(name, description, configContent string) error {
	// Write config file
	confPath := m.configPath(name)
	if err := os.WriteFile(confPath, []byte(configContent), 0600); err != nil {
		return fmt.Errorf("write wg config: %w", err)
	}

	// Auto-detect interface name from config
	iface := m.detectInterface(configContent)
	if iface == "" {
		iface = name
	}

	m.mu.Lock()
	m.Profiles[name] = &VPNProfile{
		Name:        name,
		Description: description,
		ConfigFile:  confPath,
		Interface:   iface,
		Active:      false,
	}
	m.mu.Unlock()
	m.saveProfiles()
	return nil
}

// RemoveProfile deletes a VPN profile.
func (m *VPNManager) RemoveProfile(name string) error {
	m.mu.Lock()
	prof, ok := m.Profiles[name]
	delete(m.Profiles, name)
	if m.activeName == name {
		m.activeName = ""
	}
	m.mu.Unlock()
	if !ok {
		return fmt.Errorf("profile %s not found", name)
	}
	os.Remove(prof.ConfigFile)
	m.saveProfiles()
	return nil
}

// ActivateProfile marks a profile as active (but does not start it).
func (m *VPNManager) ActivateProfile(name string) error {
	m.mu.Lock()
	defer m.mu.Unlock()
	if _, ok := m.Profiles[name]; !ok {
		return fmt.Errorf("profile %s not found", name)
	}
	for _, p := range m.Profiles {
		p.Active = false
	}
	m.Profiles[name].Active = true
	m.activeName = name
	m.saveProfiles()
	return nil
}

// ActiveProfile returns the name of the currently active profile.
func (m *VPNManager) ActiveProfile() string {
	m.mu.RLock()
	defer m.mu.RUnlock()
	return m.activeName
}

// Up brings up the VPN via wg-quick.
func (m *VPNManager) Up(name string) error {
	confPath := m.configPath(name)
	if _, err := os.Stat(confPath); err != nil {
		return fmt.Errorf("config %s not found: %w", name, err)
	}

	slog.Info("paranoidx: wg-quick up", "profile", name)
	cmd := exec.Command("wg-quick", "up", confPath)
	out, err := cmd.CombinedOutput()
	if err != nil {
		return fmt.Errorf("wg-quick up %s: %w\n%s", name, err, string(out))
	}
	m.ActivateProfile(name)

	// Verify interface is up
	deadline := time.Now().Add(10 * time.Second)
	for time.Now().Before(deadline) {
		if m.checkInterface(name) {
			SetLayerStatus(LayerVPN, true, 0, fmt.Sprintf("vpn %s up", name))
			return nil
		}
		time.Sleep(1 * time.Second)
	}
	return fmt.Errorf("vpn interface not ready after wg-quick up")
}

// Down brings down the VPN via wg-quick.
func (m *VPNManager) Down(name string) error {
	confPath := m.configPath(name)
	if _, err := os.Stat(confPath); err != nil {
		return nil
	}

	slog.Info("paranoidx: wg-quick down", "profile", name)
	cmd := exec.Command("wg-quick", "down", confPath)
	out, err := cmd.CombinedOutput()
	if err != nil {
		slog.Warn("paranoidx: wg-quick down", "err", err, "output", string(out))
	}
	SetLayerStatus(LayerVPN, false, 0, "vpn down")
	return nil
}

// CheckHealth checks if the active VPN interface is up.
func (m *VPNManager) CheckHealth() bool {
	start := time.Now()
	m.mu.RLock()
	iface := ""
	if prof, ok := m.Profiles[m.activeName]; ok {
		iface = prof.Interface
	}
	m.mu.RUnlock()

	if iface == "" {
		iface = "wg0"
	}

	if !m.checkInterface(iface) {
		SetLayerStatus(LayerVPN, false, 0, fmt.Sprintf("interface %s not found", iface))
		return false
	}
	latency := time.Since(start).Milliseconds()
	SetLayerStatus(LayerVPN, true, latency, fmt.Sprintf("%s up", iface))
	return true
}

func (m *VPNManager) checkInterface(iface string) bool {
	cmd := exec.Command("ip", "link", "show", iface)
	return cmd.Run() == nil
}

func (m *VPNManager) detectInterface(config string) string {
	for _, line := range strings.Split(config, "\n") {
		line = strings.TrimSpace(line)
		if strings.HasPrefix(line, "Interface") || strings.HasPrefix(strings.ToLower(line), "[interface]") {
			continue
		}
		if strings.HasPrefix(strings.ToLower(line), "interface = ") {
			return strings.TrimSpace(strings.SplitN(line, "=", 2)[1])
		}
	}
	return "wg0"
}

// VPNProfileHandler returns all stored VPN profiles as JSON.
func (b *Bridge) VPNProfileHandler(w http.ResponseWriter, r *http.Request) {
	w.Header().Set("Content-Type", "application/json")
	switch r.Method {
	case http.MethodGet:
		profiles := b.chain.VPN().ListProfiles()
		json.NewEncoder(w).Encode(map[string]any{
			"profiles": profiles,
			"active":   b.chain.VPN().ActiveProfile(),
		})
	case http.MethodPost:
		var req struct {
			Name    string `json:"name"`
			Desc    string `json:"description"`
			Config  string `json:"config"`
		}
		if err := json.NewDecoder(r.Body).Decode(&req); err != nil {
			http.Error(w, fmt.Sprintf("bad request: %v", err), http.StatusBadRequest)
			return
		}
		if err := b.chain.VPN().AddProfile(req.Name, req.Desc, req.Config); err != nil {
			http.Error(w, err.Error(), http.StatusInternalServerError)
			return
		}
		json.NewEncoder(w).Encode(map[string]string{"status": "ok", "name": req.Name})
	default:
		http.Error(w, "GET or POST required", http.StatusMethodNotAllowed)
	}
}

// VPNUpHandler brings up a VPN profile.
func (b *Bridge) VPNUpHandler(w http.ResponseWriter, r *http.Request) {
	if r.Method != http.MethodPost {
		http.Error(w, "POST required", http.StatusMethodNotAllowed)
		return
	}
	var req struct {
		Name string `json:"name"`
	}
	w.Header().Set("Content-Type", "application/json")
	if err := json.NewDecoder(r.Body).Decode(&req); err != nil {
		http.Error(w, fmt.Sprintf("bad request: %v", err), http.StatusBadRequest)
		return
	}
	if err := b.chain.VPN().Up(req.Name); err != nil {
		json.NewEncoder(w).Encode(map[string]any{"status": "error", "error": err.Error()})
		return
	}
	json.NewEncoder(w).Encode(map[string]string{"status": "ok", "profile": req.Name})
}

// VPNDownHandler brings down a VPN profile.
func (b *Bridge) VPNDownHandler(w http.ResponseWriter, r *http.Request) {
	if r.Method != http.MethodPost {
		http.Error(w, "POST required", http.StatusMethodNotAllowed)
		return
	}
	var req struct {
		Name string `json:"name"`
	}
	w.Header().Set("Content-Type", "application/json")
	if err := json.NewDecoder(r.Body).Decode(&req); err != nil {
		http.Error(w, fmt.Sprintf("bad request: %v", err), http.StatusBadRequest)
		return
	}
	if err := b.chain.VPN().Down(req.Name); err != nil {
		json.NewEncoder(w).Encode(map[string]any{"status": "error", "error": err.Error()})
		return
	}
	json.NewEncoder(w).Encode(map[string]string{"status": "ok"})
}

// VPNProfileDeleteHandler removes a VPN profile.
func (b *Bridge) VPNProfileDeleteHandler(w http.ResponseWriter, r *http.Request) {
	if r.Method != http.MethodPost {
		http.Error(w, "POST required", http.StatusMethodNotAllowed)
		return
	}
	var req struct {
		Name string `json:"name"`
	}
	w.Header().Set("Content-Type", "application/json")
	if err := json.NewDecoder(r.Body).Decode(&req); err != nil {
		http.Error(w, fmt.Sprintf("bad request: %v", err), http.StatusBadRequest)
		return
	}
	if err := b.chain.VPN().RemoveProfile(req.Name); err != nil {
		json.NewEncoder(w).Encode(map[string]any{"status": "error", "error": err.Error()})
		return
	}
	json.NewEncoder(w).Encode(map[string]string{"status": "ok"})
}
