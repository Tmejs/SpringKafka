package com.example.fundorders.security;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "app.security")
public record BasicCredentialsProperties(@NotBlank String username, @NotBlank String password) {
}
