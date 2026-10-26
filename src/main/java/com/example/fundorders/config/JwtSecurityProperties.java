package com.example.fundorders.config;

import java.net.URI;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.Assert;

@ConfigurationProperties(prefix = "app.security.jwt")
public record JwtSecurityProperties(String issuerUri, String audience, String jwkSetUri) {
    public JwtSecurityProperties {
        requireHttpEndpoint(issuerUri, "issuer-uri");
        Assert.hasText(audience, "app.security.jwt.audience must not be blank");
        requireHttpEndpoint(jwkSetUri, "jwk-set-uri");
    }

    private static void requireHttpEndpoint(String value, String name) {
        Assert.hasText(value, "app.security.jwt." + name + " must not be blank");
        var uri = URI.create(value);
        Assert.isTrue(uri.getHost() != null && ("http".equals(uri.getScheme()) || "https".equals(uri.getScheme())),
                "app.security.jwt." + name + " must be an absolute HTTP(S) URL");
    }
}
