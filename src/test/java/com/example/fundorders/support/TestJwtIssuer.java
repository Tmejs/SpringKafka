package com.example.fundorders.support;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.JWK;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.sun.net.httpserver.HttpServer;

/** Real signing and local JWK HTTP fixture; never used by the application. */
public final class TestJwtIssuer implements AutoCloseable {
    public static final String ISSUER = "https://issuer.example.test/fund-orders";
    public static final String AUDIENCE = "fund-orders-api";
    public final RSAKey key = newKey("primary");
    public final AtomicInteger requests = new AtomicInteger();
    public volatile boolean available = true;
    private volatile String publishedKeys;
    private final HttpServer server;

    public TestJwtIssuer() {
        try {
            publish(key);
            server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.createContext("/jwks", exchange -> {
                requests.incrementAndGet();
                var body = (available ? publishedKeys : "unavailable").getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().set("Content-Type", "application/json");
                exchange.sendResponseHeaders(available ? 200 : 503, body.length);
                try (var output = exchange.getResponseBody()) { output.write(body); }
            });
            server.start();
        } catch (IOException exception) {
            throw new IllegalStateException(exception);
        }
    }

    public String jwkSetUri() { return "http://127.0.0.1:" + server.getAddress().getPort() + "/jwks"; }

    public void publish(RSAKey... keys) {
        publishedKeys = new JWKSet(List.of(keys).stream().map(key -> (JWK) key.toPublicJWK()).toList()).toString();
    }

    public String token(String scopes) { return token(key, JWSAlgorithm.RS256, claims -> claims.claim("scope", scopes)); }

    public String token(Consumer<JWTClaimsSet.Builder> changes) { return token(key, JWSAlgorithm.RS256, changes); }

    public String token(RSAKey signingKey, JWSAlgorithm algorithm, Consumer<JWTClaimsSet.Builder> changes) {
        try {
            var now = Instant.now();
            var claims = new JWTClaimsSet.Builder().issuer(ISSUER).audience(AUDIENCE).subject("test-client")
                    .issueTime(Date.from(now)).notBeforeTime(Date.from(now.minusSeconds(5)))
                    .expirationTime(Date.from(now.plusSeconds(300)))
                    .claim("scope", "fund-orders.read fund-orders.write");
            changes.accept(claims);
            var jwt = new SignedJWT(new JWSHeader.Builder(algorithm).type(JOSEObjectType.JWT)
                    .keyID(signingKey.getKeyID()).build(), claims.build());
            jwt.sign(new RSASSASigner(signingKey));
            return jwt.serialize();
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }

    public static RSAKey newKey(String id) {
        try {
            var generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            var pair = generator.generateKeyPair();
            return new RSAKey.Builder((RSAPublicKey) pair.getPublic())
                    .privateKey((RSAPrivateKey) pair.getPrivate()).keyID(id).build();
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }

    @Override public void close() { server.stop(0); }
}
