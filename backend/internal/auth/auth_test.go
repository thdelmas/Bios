package auth

import (
	"testing"
	"time"
)

var testSecret = []byte("test-secret-key-for-unit-tests-32b")

func TestGenerateAndValidateToken(t *testing.T) {
	token, err := GenerateToken("user123", 1*time.Hour, testSecret)
	if err != nil {
		t.Fatalf("GenerateToken: %v", err)
	}
	if token == "" {
		t.Fatal("expected non-empty token")
	}

	parsed, err := ValidateToken(token, testSecret)
	if err != nil {
		t.Fatalf("ValidateToken: %v", err)
	}
	if parsed.UserID != "user123" {
		t.Errorf("UserID = %q, want %q", parsed.UserID, "user123")
	}
	if parsed.ExpiresAt.Before(time.Now()) {
		t.Error("token should not be expired yet")
	}
}

func TestExpiredToken(t *testing.T) {
	token, err := GenerateToken("user456", -1*time.Second, testSecret)
	if err != nil {
		t.Fatalf("GenerateToken: %v", err)
	}

	_, err = ValidateToken(token, testSecret)
	if err != ErrInvalidToken {
		t.Errorf("expected ErrInvalidToken, got %v", err)
	}
}

func TestWrongSecret(t *testing.T) {
	token, err := GenerateToken("user789", 1*time.Hour, testSecret)
	if err != nil {
		t.Fatalf("GenerateToken: %v", err)
	}

	_, err = ValidateToken(token, []byte("wrong-secret"))
	if err != ErrInvalidToken {
		t.Errorf("expected ErrInvalidToken, got %v", err)
	}
}

func TestTamperedToken(t *testing.T) {
	token, err := GenerateToken("user123", 1*time.Hour, testSecret)
	if err != nil {
		t.Fatalf("GenerateToken: %v", err)
	}

	// Tamper with the token
	tampered := "tampered" + token[8:]
	_, err = ValidateToken(tampered, testSecret)
	if err != ErrInvalidToken {
		t.Errorf("expected ErrInvalidToken for tampered token, got %v", err)
	}
}

func TestMalformedToken(t *testing.T) {
	_, err := ValidateToken("not.a.valid.token.at.all", testSecret)
	if err != ErrInvalidToken {
		t.Errorf("expected ErrInvalidToken, got %v", err)
	}

	_, err = ValidateToken("", testSecret)
	if err != ErrInvalidToken {
		t.Errorf("expected ErrInvalidToken for empty token, got %v", err)
	}
}

func TestExtractBearer(t *testing.T) {
	tests := []struct {
		name    string
		header  string
		want    string
		wantErr error
	}{
		{"valid", "Bearer abc123", "abc123", nil},
		{"case insensitive", "bearer abc123", "abc123", nil},
		{"empty", "", "", ErrMissingAuth},
		{"no bearer prefix", "Token abc123", "", ErrInvalidToken},
		{"only prefix", "Bearer", "", ErrInvalidToken},
	}

	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			got, err := ExtractBearer(tt.header)
			if err != tt.wantErr {
				t.Errorf("err = %v, want %v", err, tt.wantErr)
			}
			if got != tt.want {
				t.Errorf("got = %q, want %q", got, tt.want)
			}
		})
	}
}

func TestUniqueTokens(t *testing.T) {
	token1, _ := GenerateToken("user", 1*time.Hour, testSecret)
	token2, _ := GenerateToken("user", 1*time.Hour, testSecret)
	if token1 == token2 {
		t.Error("two tokens for same user should be unique")
	}
}
