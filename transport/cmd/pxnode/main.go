package main

import (
	"context"
	"flag"
	"fmt"
	"log"
	"net/http"
	"os"
	"os/signal"
	"path/filepath"
	"syscall"
	"time"

	"px-transport/internal/api"
	"px-transport/internal/bridge"
	"px-transport/internal/common"
	"px-transport/internal/config"
	"px-transport/internal/dockerutil"
	"px-transport/internal/health"
	"px-transport/internal/lock"
	"px-transport/internal/paranoidx"
	"px-transport/internal/status"
	"px-transport/internal/store"
	"px-transport/internal/transport"
)

var (
	buildVersion = "C41-C60"
	listenAddr   = flag.String("listen", "0.0.0.0:8080", "HTTP listen address")
	dataDir      = flag.String("data", "", "Data directory (default: ~/.local/share/px-transport)")
	versionFlag  = flag.Bool("version", false, "Print version and exit")
)

func main() {
	flag.Parse()

	if *versionFlag {
		fmt.Printf("px-node-%s\n", buildVersion)
		os.Exit(0)
	}

	if *dataDir == "" {
		home, _ := os.UserHomeDir()
		*dataDir = filepath.Join(home, ".local/share/px-transport")
	}

	if err := os.MkdirAll(*dataDir, 0700); err != nil {
		log.Fatalf("Failed to create data dir: %v", err)
	}

	_ = config.Load(*dataDir)

	_, err := store.Open(filepath.Join(*dataDir, "paranoidx.db"))
	if err != nil {
		log.Fatalf("Failed to initialize store: %v", err)
	}

	_ = lock.New(*dataDir)

	vaultPath := filepath.Join(*dataDir, "vault")
	_ = health.New(*dataDir, vaultPath, common.StartTime)

	_ = transport.NewHub(*dataDir)

	_ = paranoidx.GetStatus()

	_ = status.Collect(*dataDir, vaultPath, common.StartTime)

	_ = dockerutil.New(*dataDir)

	bridgeMgr := bridge.New(*dataDir)

	server := &http.Server{
		Addr:         *listenAddr,
		Handler:      api.NewHandler(nil),
		ReadTimeout:  30 * time.Second,
		WriteTimeout: 30 * time.Second,
	}

	go func() {
		log.Printf("Starting PX Node on %s (data: %s)", *listenAddr, *dataDir)
		if err := server.ListenAndServe(); err != nil && err != http.ErrServerClosed {
			log.Fatalf("Server failed: %v", err)
		}
	}()

	ctx, cancel := context.WithCancel(context.Background())
	defer cancel()

	go bridgeMgr.RunContext(ctx)

	sigCh := make(chan os.Signal, 1)
	signal.Notify(sigCh, syscall.SIGINT, syscall.SIGTERM)
	<-sigCh

	log.Println("Shutting down...")

	shutdownCtx, shutdownCancel := context.WithTimeout(context.Background(), 10*time.Second)
	defer shutdownCancel()

	if err := server.Shutdown(shutdownCtx); err != nil {
		log.Printf("Server shutdown error: %v", err)
	}

	log.Println("PX Node stopped")
}
