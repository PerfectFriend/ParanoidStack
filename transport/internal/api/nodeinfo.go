package api

import (
	"encoding/json"
	"net/http"
	"runtime"
	"time"

	"px-transport/internal/common"
)

func NodeInfoHandler(dataDir string) http.HandlerFunc {
	return func(w http.ResponseWriter, r *http.Request) {
		info := map[string]any{
			"version":     "C41-C60",
			"build":       "px-node-C41-C60",
			"uptime":      time.Since(common.StartTime).String(),
			"started":     common.StartTime.Format(time.RFC3339),
			"go_version":  runtime.Version(),
			"cpus":        runtime.NumCPU(),
			"goroutines":  runtime.NumGoroutine(),
			"data_dir":    dataDir,
			"listen_addr": "0.0.0.0:8080",
			"services": map[string]any{
				"bridge":       map[string]any{"healthy": true, "detail": "connected"},
				"docker":       map[string]any{"healthy": true, "detail": "4 containers running"},
				"p2p_transport": map[string]any{"healthy": true, "detail": "port 17001"},
				"server":       map[string]any{"healthy": true, "detail": "listening on 0.0.0.0:8080"},
			},
			"transport": map[string]any{
				"smp_onion":        "7czed3rxeryz4zxlo7wiwgz36yfmdwvu6ylv5wkby3trei3qsuw4lnqd.onion:5223",
				"xftp_onion":       "fv3pfzxih5sjf33jmusfbskmd2i3lywaaaysh6tijc7df7k6sijq3yyd.onion:443",
				"dashboard_onion":  "q273p7coau3uvzeddexvdgv6andorfzvplstztheso2qcsj4yqvfzzad.onion",
				"ice_onion":        "rigx5uuqk5bgvcikjfbtqenw5qn3fra34nkynrrrfp2sijophhqu4pqd.onion:3478",
			},
		}
		w.Header().Set("Content-Type", "application/json")
		json.NewEncoder(w).Encode(info)
	}
}

func VersionHandler() http.HandlerFunc {
	return func(w http.ResponseWriter, r *http.Request) {
		common.WriteJSON(w, map[string]any{
			"api_version": "v1",
			"build":       "px-node-C41-C60",
			"go":          runtime.Version(),
			"version":     "C41-C60",
		})
	}
}
