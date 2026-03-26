package api

import (
	"encoding/json"
	"net/http"
	"net/http/httptest"
	"testing"
)

var testSecret = []byte("test-secret-for-handler-tests-32b!")

func TestHealthEndpoint(t *testing.T) {
	srv := NewServer(nil, testSecret)

	req := httptest.NewRequest(http.MethodGet, "/health", nil)
	w := httptest.NewRecorder()

	srv.Handler().ServeHTTP(w, req)

	if w.Code != http.StatusOK {
		t.Errorf("status = %d, want %d", w.Code, http.StatusOK)
	}

	var body map[string]string
	if err := json.Unmarshal(w.Body.Bytes(), &body); err != nil {
		t.Fatalf("invalid JSON: %v", err)
	}
	if body["status"] != "ok" {
		t.Errorf("status = %q, want %q", body["status"], "ok")
	}
}

func TestSyncPushRequiresAuth(t *testing.T) {
	srv := NewServer(nil, testSecret)

	req := httptest.NewRequest(http.MethodPost, "/v1/sync", nil)
	w := httptest.NewRecorder()

	srv.Handler().ServeHTTP(w, req)

	if w.Code != http.StatusUnauthorized {
		t.Errorf("status = %d, want %d", w.Code, http.StatusUnauthorized)
	}
}

func TestSyncPullRequiresAuth(t *testing.T) {
	srv := NewServer(nil, testSecret)

	req := httptest.NewRequest(http.MethodGet, "/v1/sync", nil)
	w := httptest.NewRecorder()

	srv.Handler().ServeHTTP(w, req)

	if w.Code != http.StatusUnauthorized {
		t.Errorf("status = %d, want %d", w.Code, http.StatusUnauthorized)
	}
}

func TestContributeRequiresAuth(t *testing.T) {
	srv := NewServer(nil, testSecret)

	req := httptest.NewRequest(http.MethodPost, "/v1/community/contribute", nil)
	w := httptest.NewRecorder()

	srv.Handler().ServeHTTP(w, req)

	if w.Code != http.StatusUnauthorized {
		t.Errorf("status = %d, want %d", w.Code, http.StatusUnauthorized)
	}
}

func TestCommunityAggregatesRequiresParams(t *testing.T) {
	srv := NewServer(nil, testSecret)

	req := httptest.NewRequest(http.MethodGet, "/v1/community/aggregates", nil)
	w := httptest.NewRecorder()

	srv.Handler().ServeHTTP(w, req)

	if w.Code != http.StatusBadRequest {
		t.Errorf("status = %d, want %d", w.Code, http.StatusBadRequest)
	}
}

func TestInvalidBearerToken(t *testing.T) {
	srv := NewServer(nil, testSecret)

	req := httptest.NewRequest(http.MethodPost, "/v1/sync", nil)
	req.Header.Set("Authorization", "Bearer invalid.token.here")
	w := httptest.NewRecorder()

	srv.Handler().ServeHTTP(w, req)

	if w.Code != http.StatusUnauthorized {
		t.Errorf("status = %d, want %d", w.Code, http.StatusUnauthorized)
	}
}
