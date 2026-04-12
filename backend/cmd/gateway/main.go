package main

import (
	"context"
	"log"
	"net/http"
	"os"
	"os/signal"
	"syscall"
	"time"

	"github.com/bios-health/backend/internal/api"
	"github.com/bios-health/backend/internal/store"
)

func main() {
	dsn := envOrDefault("DATABASE_URL", "postgres://localhost:5432/bios?sslmode=disable")
	addr := envOrDefault("LISTEN_ADDR", ":8080")
	secret := []byte(envOrDefault("TOKEN_SECRET", ""))
	retentionDays := 90 // sync payloads older than this are purged

	if len(secret) == 0 {
		log.Fatal("TOKEN_SECRET environment variable is required")
	}

	ctx, cancel := context.WithTimeout(context.Background(), 10*time.Second)
	defer cancel()

	db, err := store.New(ctx, dsn)
	if err != nil {
		log.Fatalf("Failed to connect to database: %v", err)
	}
	defer db.Close()

	if err := db.Migrate(ctx); err != nil {
		log.Fatalf("Failed to run migrations: %v", err)
	}

	srv := api.NewServer(db, secret)

	httpServer := &http.Server{
		Addr:         addr,
		Handler:      srv.Handler(),
		ReadTimeout:  10 * time.Second,
		WriteTimeout: 30 * time.Second,
		IdleTimeout:  60 * time.Second,
	}

	// Background purge: delete sync payloads older than retention period.
	// Runs once on startup and then every 24 hours.
	purgeCtx, purgeCancel := context.WithCancel(context.Background())
	go func() {
		ticker := time.NewTicker(24 * time.Hour)
		defer ticker.Stop()
		for {
			purged, err := db.PurgeSyncPayloads(purgeCtx, retentionDays)
			if err != nil {
				log.Printf("ERROR purge sync payloads: %v", err)
			} else if purged > 0 {
				log.Printf("Purged %d sync payloads older than %d days", purged, retentionDays)
			}
			select {
			case <-ticker.C:
			case <-purgeCtx.Done():
				return
			}
		}
	}()

	go func() {
		log.Printf("Bios sync gateway listening on %s", addr)
		if err := httpServer.ListenAndServe(); err != nil && err != http.ErrServerClosed {
			log.Fatalf("HTTP server error: %v", err)
		}
	}()

	// Graceful shutdown
	quit := make(chan os.Signal, 1)
	signal.Notify(quit, syscall.SIGINT, syscall.SIGTERM)
	<-quit

	log.Println("Shutting down...")
	purgeCancel()
	shutdownCtx, shutdownCancel := context.WithTimeout(context.Background(), 10*time.Second)
	defer shutdownCancel()

	if err := httpServer.Shutdown(shutdownCtx); err != nil {
		log.Fatalf("Shutdown error: %v", err)
	}
	log.Println("Server stopped")
}

func envOrDefault(key, fallback string) string {
	if v := os.Getenv(key); v != "" {
		return v
	}
	return fallback
}
