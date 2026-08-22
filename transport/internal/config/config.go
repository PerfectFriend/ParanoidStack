package config

import (
	"encoding/json"
	"os"
	"path/filepath"
)

type Config struct {
	// Node settings
	ListenAddr string `json:"listen_addr"`
	DataDir    string `json:"data_dir"`
	
	// Tor settings
	TorSocksPort int `json:"tor_socks_port"`
	
	// Bridge settings
	BridgeWSport int `json:"bridge_ws_port"`
	
	// Network settings
	TransportConfig map[string]string `json:"transport_config"`
}

func Load(dataDir string) *Config {
	configPath := filepath.Join(dataDir, "config.json")
	
	cfg := &Config{
		ListenAddr:     "0.0.0.0:8080",
		DataDir:        dataDir,
		TorSocksPort:   9050,
		BridgeWSport:   5230,
		TransportConfig: make(map[string]string),
	}
	
	if data, err := os.ReadFile(configPath); err == nil {
		json.Unmarshal(data, cfg)
	}
	
	return cfg
}

func (c *Config) Save(dataDir string) error {
	configPath := filepath.Join(dataDir, "config.json")
	data, err := json.MarshalIndent(c, "", "  ")
	if err != nil {
		return err
	}
	return os.WriteFile(configPath, data, 0600)
}
