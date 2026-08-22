package api

import (
	"encoding/json"
	"net/http"

	"px-transport/internal/state"
)

// BridgeStatusHandler returns the SimpleX bridge connection status
func BridgeStatusHandler(b state.BridgeState) http.HandlerFunc {
	return func(w http.ResponseWriter, r *http.Request) {
		if r.Method != "GET" {
			http.Error(w, "GET required", 405)
			return
		}
		writeJSON(w, map[string]any{
			"bridge":      b.IsConnected(),
			"reconnects":  b.ReconnectCount(),
			"last_error":  b.LastError(),
			"cli_running": b.IsCLIRunning(),
		})
	}
}

// BridgeConnectHandler triggers a bridge reconnection
func BridgeConnectHandler(b state.BridgeState) http.HandlerFunc {
	return func(w http.ResponseWriter, r *http.Request) {
		if r.Method != "POST" {
			http.Error(w, "POST required", 405)
			return
		}
		b.TriggerReconnect()
		writeJSON(w, map[string]any{"ok": true, "message": "reconnect triggered"})
	}
}

// BridgeCLIHandler starts the simplex-chat-island CLI
func BridgeCLIHandler(b state.BridgeState) http.HandlerFunc {
	return func(w http.ResponseWriter, r *http.Request) {
		if r.Method != "POST" {
			http.Error(w, "POST required", 405)
			return
		}
		err := b.StartCLI()
		if err != nil {
			writeJSON(w, map[string]any{"ok": false, "error": err.Error()})
			return
		}
		writeJSON(w, map[string]any{"ok": true, "message": "CLI started"})
	}
}

// BridgeQRHandler returns the bridge connection QR code
func BridgeQRHandler(b state.BridgeState) http.HandlerFunc {
	return func(w http.ResponseWriter, r *http.Request) {
		if r.Method != "GET" {
			http.Error(w, "GET required", 405)
			return
		}
		qr := b.GetQRCode()
		writeJSON(w, map[string]any{"qr": qr})
	}
}

func writeJSON(w http.ResponseWriter, v any) {
	w.Header().Set("Content-Type", "application/json")
	json.NewEncoder(w).Encode(v)
}
