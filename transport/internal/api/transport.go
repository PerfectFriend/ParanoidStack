package api

import (
	"net/http"
	"time"

	"px-transport/internal/common"
)

func TransportInfoHandler() http.HandlerFunc {
	return func(w http.ResponseWriter, r *http.Request) {
		common.WriteJSON(w, map[string]any{
			"transport": "ParanoidX",
			"uptime":    time.Since(common.StartTime).String(),
		})
	}
}

func TransportHealthHandler() http.HandlerFunc {
	return func(w http.ResponseWriter, r *http.Request) {
		common.WriteJSON(w, map[string]any{"healthy": true})
	}
}

func TransportStatusHandler() http.HandlerFunc {
	return func(w http.ResponseWriter, r *http.Request) {
		common.WriteJSON(w, map[string]any{"status": "ok"})
	}
}

func TransportSendHandler() http.HandlerFunc {
	return func(w http.ResponseWriter, r *http.Request) {
		common.WriteJSON(w, map[string]any{"ok": true})
	}
}
