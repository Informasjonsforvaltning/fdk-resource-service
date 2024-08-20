package test

import (
	"crypto/rand"
	"crypto/rsa"
	"encoding/json"
	"fmt"
	"net/http"
	"net/http/httptest"
	"time"

	"github.com/lestrrat-go/jwx/jwa"
	"github.com/lestrrat-go/jwx/jwk"
	"github.com/lestrrat-go/jwx/jwt"
)

var MockRsaKey, _ = rsa.GenerateKey(rand.Reader, 2048)

func MockJwkStore() *httptest.Server {
	key, err := jwk.New(MockRsaKey)
	if err != nil {
		fmt.Println(err)
	}

	server := httptest.NewServer(
		http.HandlerFunc(
			func(rw http.ResponseWriter, r *http.Request) {
				rw.Header().Add("Content-Type", "application/json")

				key.Set(jwk.KeyIDKey, "testkid")

				buf, err := json.MarshalIndent(key, "", "  ")
				if err != nil {
					fmt.Printf("failed to marshal key into JSON: %s\n", err)
					return
				}

				fmt.Fprintf(rw, `{"keys":[%s]}`, buf)
			},
		),
	)

	return server
}

func CreateMockJwt(expiresAt int64, auth *string, audience *[]string) *string {
	t := jwt.New()
	t.Set(jwt.SubjectKey, `https://github.com/lestrrat-go/jwx/jwt`)
	t.Set(jwt.IssuedAtKey, time.Now().Unix())
	t.Set(jwt.ExpirationKey, expiresAt)
	if auth != nil {
		t.Set(`authorities`, auth)
	}
	if audience != nil {
		t.Set(jwt.AudienceKey, *audience)
	}

	jwk_key, _ := jwk.New(MockRsaKey)

	jwk_key.Set(jwk.KeyIDKey, "testkid")

	signed, _ := jwt.Sign(t, jwa.RS256, jwk_key)

	signed_string := string(signed)

	return &signed_string
}
