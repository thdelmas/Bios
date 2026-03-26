package api

import (
	"context"
	"net/http"

	"github.com/bios-health/backend/internal/auth"
)

type contextKey string

const ctxTokenKey contextKey = "token"

// requireAuth wraps a handler with bearer token validation.
func (s *Server) requireAuth(next http.HandlerFunc) http.HandlerFunc {
	return func(w http.ResponseWriter, r *http.Request) {
		raw, err := auth.ExtractBearer(r.Header.Get("Authorization"))
		if err != nil {
			writeJSON(w, http.StatusUnauthorized, map[string]string{"error": "missing or invalid authorization"})
			return
		}

		token, err := auth.ValidateToken(raw, s.secret)
		if err != nil {
			writeJSON(w, http.StatusUnauthorized, map[string]string{"error": "invalid or expired token"})
			return
		}

		ctx := context.WithValue(r.Context(), ctxTokenKey, token)
		next(w, r.WithContext(ctx))
	}
}
