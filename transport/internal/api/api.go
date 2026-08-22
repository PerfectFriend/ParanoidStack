package api

import (
	"net/http"

	"px-transport/internal/middleware"
)

func NewHandler(cfg interface{}) http.Handler {
	mux := http.NewServeMux()

	// Admin routes - all handled by AdminHandler
	admin := NewAdminHandler()
	mux.Handle("/api/admin/", admin)
	mux.Handle("/api/admin/docker", admin)
	mux.Handle("/api/admin/metrics", admin)
	mux.Handle("/api/admin/metrics/system", admin)
	mux.Handle("/api/admin/status-page", admin)
	mux.Handle("/api/admin/rate-limit-status", admin)
	mux.Handle("/api/admin/backup", admin)

	// Version/Health
	mux.Handle("/api/version", VersionHandler())
	mux.Handle("/api/health", admin)
	mux.Handle("/api/status", admin)
	mux.Handle("/api/addresses", admin)

	// Chat - use ChatHandler
	chat := NewChatHandler()
	mux.Handle("/api/chat/status", chat)
	mux.Handle("/api/chat/history", chat)
	mux.Handle("/api/chat/stream", chat)
	mux.Handle("/api/chat/send", chat)
	mux.Handle("/api/chat/contacts", chat)
	mux.Handle("/api/chat/address", chat)
	mux.Handle("/api/chat/qr", chat)

	// Bridge
	mux.Handle("/api/bridge/status", BridgeChainStatusHandler(nil))
	mux.Handle("/api/bridge/start", BridgeStartHandler(nil))
	mux.Handle("/api/bridge/stop", BridgeStopHandler(nil))
	mux.Handle("/api/bridge/chain", BridgeChainStatusHandler(nil))
	mux.Handle("/api/bridge/start-vpn1", BridgeStartVPN1Handler(nil))
	mux.Handle("/api/bridge/stop-vpn1", BridgeStopVPN1Handler(nil))
	mux.Handle("/api/bridge/start-vpn2", BridgeStartVPN2Handler(nil))
	mux.Handle("/api/bridge/stop-vpn2", BridgeStopVPN2Handler(nil))

	// Node info
	mux.Handle("/api/admin/info", NodeInfoHandler(""))

	// Transport
	mux.Handle("/api/transport/info", TransportInfoHandler())
	mux.Handle("/api/transport/health", TransportHealthHandler())
	mux.Handle("/api/transport/status", TransportStatusHandler())
	mux.Handle("/api/transport/send", TransportSendHandler())

	// Dashboard
	mux.Handle("/api/dashboard", DashboardHandler())
	mux.Handle("/", DashboardHandler())

	// Onion Service Checks
	mux.Handle("/api/onion/check", OnionCheckHandler())

	// Config Management
	mux.Handle("/api/config/list", ConfigListHandler())
	mux.Handle("/api/config/upload", ConfigUploadHandler())
	mux.Handle("/api/config/use", ConfigUseHandler())
	mux.Handle("/api/config/delete", ConfigDeleteHandler())
	mux.Handle("/api/config/import-url", ConfigImportURLHandler())
	mux.Handle("/api/config/auto", ConfigAutoHandler())
	mux.Handle("/api/config/scan", ConfigScanHandler())
	mux.Handle("/api/config/fetch-subs", ConfigFetchSubsHandler())
	mux.Handle("/api/config/system", SystemConfigHandler())

	// Tor Key Management
	mux.Handle("/api/tor/backup-keys", TorBackupKeysHandler())
	mux.Handle("/api/tor/restore-keys", TorRestoreKeysHandler())
	mux.Handle("/api/tor/test-client", TorTestClientHandler())

	// System Management
	mux.Handle("/api/admin/restart", RestartNodeHandler())
	mux.Handle("/api/config/wipe", WipeConfigsHandler())
	mux.Handle("/api/config/reset", ResetConfigsHandler())

	// Logs
	mux.Handle("/api/logs", LogsHandler())

	// Fallback
	mux.Handle("/api/", http.NotFoundHandler())

	// Wrap the whole API with the local/onion guard. Public (unauthenticated
	// but harmless) endpoints stay reachable for status pages.
	public := map[string]bool{
		"/api/health":  true,
		"/api/version": true,
		"/api/status":  true,
	}
	return http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		if !public[r.URL.Path] {
			if middleware.DenyIfNotLocalOrOnion(w, r) {
				return
			}
		}
		mux.ServeHTTP(w, r)
	})
}
