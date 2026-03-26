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

func writeJSON(w http.ResponseWriter, status int, v any) {
	w.Header().Set("Content-Type", "application/json")
	w.WriteHeader(status)
	json.NewEncoder(w).Encode(v)
}
