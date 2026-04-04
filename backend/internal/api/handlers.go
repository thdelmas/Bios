package api

import (
	"encoding/json"
	"log"
	"net/http"
	"strconv"

	"github.com/bios-health/backend/internal/auth"
	"github.com/bios-health/backend/internal/model"
	"github.com/bios-health/backend/internal/store"
)

// Server holds the HTTP handlers and dependencies.
type Server struct {
	store  *store.Store
	secret []byte
	mux    *http.ServeMux
}

// NewServer creates a Server with all routes registered.
func NewServer(s *store.Store, secret []byte) *Server {
	srv := &Server{store: s, secret: secret, mux: http.NewServeMux()}
	srv.routes()
	return srv
}

// Handler returns the http.Handler for the server.
func (s *Server) Handler() http.Handler {
	return s.mux
}

func (s *Server) routes() {
	s.mux.HandleFunc("GET /health", s.handleHealth)
	s.mux.HandleFunc("POST /v1/sync", s.requireAuth(s.handleSyncPush))
	s.mux.HandleFunc("GET /v1/sync", s.requireAuth(s.handleSyncPull))
	s.mux.HandleFunc("POST /v1/community/contribute", s.requireAuth(s.handleContribute))
	s.mux.HandleFunc("GET /v1/community/aggregates", s.handleCommunityAggregates)

	// Phase 5 endpoints
	s.mux.HandleFunc("GET /v1/population/signals", s.handlePopulationSignals)
	s.mux.HandleFunc("POST /v1/research/contribute", s.handleResearchContribute)
	s.mux.HandleFunc("GET /v1/models/latest", s.handleLatestModel)
	s.mux.HandleFunc("DELETE /v1/account", s.requireAuth(s.handleDeleteAccount))
}

func (s *Server) handleHealth(w http.ResponseWriter, r *http.Request) {
	writeJSON(w, http.StatusOK, map[string]string{"status": "ok"})
}

// handleSyncPush receives an E2E-encrypted sync payload from the client.
func (s *Server) handleSyncPush(w http.ResponseWriter, r *http.Request) {
	token := r.Context().Value(ctxTokenKey).(*auth.Token)

	var payload model.SyncPayload
	if err := json.NewDecoder(r.Body).Decode(&payload); err != nil {
		writeJSON(w, http.StatusBadRequest, map[string]string{"error": "invalid request body"})
		return
	}
	payload.UserID = token.UserID

	if len(payload.EncPayload) == 0 || payload.PayloadHash == "" {
		writeJSON(w, http.StatusBadRequest, map[string]string{"error": "enc_payload and payload_hash required"})
		return
	}

	if err := s.store.SaveSyncPayload(r.Context(), &payload); err != nil {
		log.Printf("ERROR save sync payload: %v", err)
		writeJSON(w, http.StatusInternalServerError, map[string]string{"error": "storage error"})
		return
	}

	writeJSON(w, http.StatusCreated, map[string]string{"status": "stored"})
}

// handleSyncPull returns the user's encrypted payloads for device restore.
func (s *Server) handleSyncPull(w http.ResponseWriter, r *http.Request) {
	token := r.Context().Value(ctxTokenKey).(*auth.Token)

	limit := 100
	if l := r.URL.Query().Get("limit"); l != "" {
		if parsed, err := strconv.Atoi(l); err == nil && parsed > 0 && parsed <= 1000 {
			limit = parsed
		}
	}

	payloads, err := s.store.GetSyncPayloads(r.Context(), token.UserID, limit)
	if err != nil {
		log.Printf("ERROR get sync payloads: %v", err)
		writeJSON(w, http.StatusInternalServerError, map[string]string{"error": "storage error"})
		return
	}

	writeJSON(w, http.StatusOK, payloads)
}

// handleContribute receives anonymized aggregate contributions.
func (s *Server) handleContribute(w http.ResponseWriter, r *http.Request) {
	var contribution model.AggregateContribution
	if err := json.NewDecoder(r.Body).Decode(&contribution); err != nil {
		writeJSON(w, http.StatusBadRequest, map[string]string{"error": "invalid request body"})
		return
	}

	if contribution.MetricType == "" || contribution.Period == "" {
		writeJSON(w, http.StatusBadRequest, map[string]string{"error": "metric_type and period required"})
		return
	}

	if err := s.store.SaveContribution(r.Context(), &contribution); err != nil {
		log.Printf("ERROR save contribution: %v", err)
		writeJSON(w, http.StatusInternalServerError, map[string]string{"error": "storage error"})
		return
	}

	writeJSON(w, http.StatusCreated, map[string]string{"status": "accepted"})
}

// handleCommunityAggregates returns public community-level statistics.
func (s *Server) handleCommunityAggregates(w http.ResponseWriter, r *http.Request) {
	metricType := r.URL.Query().Get("metric_type")
	period := r.URL.Query().Get("period")
	if metricType == "" || period == "" {
		writeJSON(w, http.StatusBadRequest, map[string]string{"error": "metric_type and period required"})
		return
	}

	limit := 30
	if l := r.URL.Query().Get("limit"); l != "" {
		if parsed, err := strconv.Atoi(l); err == nil && parsed > 0 && parsed <= 365 {
			limit = parsed
		}
	}

	results, err := s.store.GetCommunityAggregates(r.Context(), metricType, period, limit)
	if err != nil {
		log.Printf("ERROR get community aggregates: %v", err)
		writeJSON(w, http.StatusInternalServerError, map[string]string{"error": "storage error"})
		return
	}

	writeJSON(w, http.StatusOK, results)
}

// handlePopulationSignals returns anonymized population-level health signals.
// No auth required — signals are public and non-identifying.
// The requesting device reveals nothing about itself beyond IP (Tor on LETHE).
func (s *Server) handlePopulationSignals(w http.ResponseWriter, r *http.Request) {
	limit := 20
	if l := r.URL.Query().Get("limit"); l != "" {
		if parsed, err := strconv.Atoi(l); err == nil && parsed > 0 && parsed <= 100 {
			limit = parsed
		}
	}

	signals, err := s.store.GetPopulationSignals(r.Context(), limit)
	if err != nil {
		log.Printf("ERROR get population signals: %v", err)
		writeJSON(w, http.StatusInternalServerError, map[string]string{"error": "storage error"})
		return
	}

	writeJSON(w, http.StatusOK, map[string]any{
		"signals": signals,
		"count":   len(signals),
	})
}

// handleResearchContribute receives a de-identified research contribution.
// No auth required — contributions are anonymous (random session ID, no account link).
func (s *Server) handleResearchContribute(w http.ResponseWriter, r *http.Request) {
	var contribution model.ResearchContribution
	if err := json.NewDecoder(r.Body).Decode(&contribution); err != nil {
		writeJSON(w, http.StatusBadRequest, map[string]string{"error": "invalid request body"})
		return
	}

	if contribution.SessionID == "" || len(contribution.Payload) == 0 {
		writeJSON(w, http.StatusBadRequest, map[string]string{"error": "session_id and payload required"})
		return
	}

	if err := s.store.SaveResearchContribution(r.Context(), &contribution); err != nil {
		log.Printf("ERROR save research contribution: %v", err)
		writeJSON(w, http.StatusInternalServerError, map[string]string{"error": "storage error"})
		return
	}

	writeJSON(w, http.StatusCreated, map[string]string{"status": "accepted"})
}

// handleLatestModel returns metadata for the latest TFLite anomaly model.
// Clients download the model file separately (or via IPFS on LETHE).
func (s *Server) handleLatestModel(w http.ResponseWriter, r *http.Request) {
	m, err := s.store.GetLatestModelVersion(r.Context())
	if err != nil {
		writeJSON(w, http.StatusNotFound, map[string]string{"error": "no model available"})
		return
	}
	writeJSON(w, http.StatusOK, m)
}

// handleDeleteAccount permanently removes the user and all associated data.
// Called when the owner requests "Delete all data" from the app.
// Server-side data is deleted within this request; no delayed processing.
func (s *Server) handleDeleteAccount(w http.ResponseWriter, r *http.Request) {
	token := r.Context().Value(ctxTokenKey).(*auth.Token)

	if err := s.store.DeleteUser(r.Context(), token.UserID); err != nil {
		log.Printf("ERROR delete user %s: %v", token.UserID, err)
		writeJSON(w, http.StatusInternalServerError, map[string]string{"error": "deletion failed"})
		return
	}

	log.Printf("User %s and all associated data deleted", token.UserID)
	writeJSON(w, http.StatusOK, map[string]string{"status": "deleted"})
}

func writeJSON(w http.ResponseWriter, status int, v any) {
	w.Header().Set("Content-Type", "application/json")
	w.WriteHeader(status)
	json.NewEncoder(w).Encode(v)
}
