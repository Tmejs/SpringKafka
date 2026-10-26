package com.example.fundorders.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.Date;
import java.util.List;

import com.example.fundorders.support.TestJwtIssuer;
import com.nimbusds.jose.JWSAlgorithm;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;

class JwtDecoderTest {
    private JwtDecoder decoder(TestJwtIssuer issuer) {
        return new JwtSecurityConfiguration().jwtDecoder(new JwtSecurityProperties(
                TestJwtIssuer.ISSUER, TestJwtIssuer.AUDIENCE, issuer.jwkSetUri()));
    }

    @Test
    void validSignedTokenAndAudienceMembershipAreAccepted() {
        try (var issuer = new TestJwtIssuer()) {
            var jwt = decoder(issuer).decode(issuer.token(claims ->
                    claims.audience(List.of("another-api", TestJwtIssuer.AUDIENCE))));
            assertThat(jwt.getSubject()).isEqualTo("test-client");
            assertThat(issuer.requests.get()).isPositive();
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"issuer", "missing-issuer", "audience", "missing-audience", "expired", "future", "missing-expiry"})
    void invalidClaimsAreRejected(String defect) {
        try (var issuer = new TestJwtIssuer()) {
            var token = issuer.token(claims -> {
                switch (defect) {
                    case "issuer" -> claims.issuer("https://untrusted.example.test");
                    case "missing-issuer" -> claims.issuer(null);
                    case "audience" -> claims.audience("another-api");
                    case "missing-audience" -> claims.audience((List<String>) null);
                    case "expired" -> claims.expirationTime(Date.from(Instant.now().minusSeconds(180)));
                    case "future" -> claims.notBeforeTime(Date.from(Instant.now().plusSeconds(180)));
                    case "missing-expiry" -> claims.expirationTime(null);
                    default -> throw new IllegalArgumentException(defect);
                }
            });
            assertThatThrownBy(() -> decoder(issuer).decode(token)).isInstanceOf(JwtException.class);
        }
    }

    @Test
    void wrongSignatureUnknownKeyAndUntrustedAlgorithmAreRejected() {
        try (var issuer = new TestJwtIssuer()) {
            var decoder = decoder(issuer);
            var forged = issuer.token(TestJwtIssuer.newKey("primary"), JWSAlgorithm.RS256, claims -> {});
            var unknown = issuer.token(TestJwtIssuer.newKey("unknown"), JWSAlgorithm.RS256, claims -> {});
            var algorithm = issuer.token(issuer.key, JWSAlgorithm.RS512, claims -> {});
            for (var token : List.of(forged, unknown, algorithm, "not-a-jwt", "eyJhbGciOiJub25lIn0.e30.")) {
                assertThatThrownBy(() -> decoder.decode(token)).isInstanceOf(JwtException.class);
            }
        }
    }

    @Test
    void newSigningKeyIsRetrievedOnRotation() {
        try (var issuer = new TestJwtIssuer()) {
            var decoder = decoder(issuer);
            decoder.decode(issuer.token("fund-orders.read"));
            var initialRequests = issuer.requests.get();
            var rotated = TestJwtIssuer.newKey("rotated");
            issuer.publish(issuer.key, rotated);

            assertThat(decoder.decode(issuer.token(rotated, JWSAlgorithm.RS256, claims -> {})).getSubject())
                    .isEqualTo("test-client");
            assertThat(issuer.requests.get()).isGreaterThan(initialRequests);
        }
    }

    @Test
    void cachedKeyWorksDuringOutageButUncachedKeyFailsClosed() {
        try (var issuer = new TestJwtIssuer()) {
            var decoder = decoder(issuer);
            var token = issuer.token("fund-orders.read");
            decoder.decode(token);
            var initialRequests = issuer.requests.get();
            issuer.available = false;

            assertThat(decoder.decode(token).getSubject()).isEqualTo("test-client");
            assertThat(issuer.requests.get()).isEqualTo(initialRequests);
            var unknown = issuer.token(TestJwtIssuer.newKey("uncached"), JWSAlgorithm.RS256, claims -> {});
            assertThatThrownBy(() -> decoder.decode(unknown)).isInstanceOf(JwtException.class);
            assertThatThrownBy(() -> decoder(issuer).decode(token)).isInstanceOf(JwtException.class);
        }
    }
}
