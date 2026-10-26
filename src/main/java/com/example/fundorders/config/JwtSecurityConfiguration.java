package com.example.fundorders.config;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpMethod;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.ObjectPostProcessor;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwtClaimValidator;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AuthenticationEntryPointFailureHandler;
import org.springframework.security.oauth2.server.resource.web.BearerTokenAuthenticationEntryPoint;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;
import org.springframework.web.client.RestTemplate;

@Configuration
@Profile("jwt")
@EnableConfigurationProperties(JwtSecurityProperties.class)
public class JwtSecurityConfiguration {
    @Bean
    SecurityFilterChain jwtSecurityFilterChain(HttpSecurity http) throws Exception {
        return http
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(requests -> requests
                        .requestMatchers("/actuator/health", "/actuator/health/liveness",
                                "/actuator/health/readiness").permitAll()
                        .requestMatchers("/swagger-ui.html", "/swagger-ui/**", "/v3/api-docs",
                                "/v3/api-docs/**", "/v3/api-docs.yaml").authenticated()
                        .requestMatchers(HttpMethod.GET, "/api/v1/fund-order-events",
                                "/api/v1/fund-order-events/**").hasAuthority("SCOPE_fund-orders.read")
                        .requestMatchers(HttpMethod.HEAD, "/api/v1/fund-order-events",
                                "/api/v1/fund-order-events/**").hasAuthority("SCOPE_fund-orders.read")
                        .requestMatchers(HttpMethod.POST, "/api/v1/fund-order-events",
                                "/api/v1/fund-order-events/raw").hasAuthority("SCOPE_fund-orders.write")
                        .anyRequest().denyAll())
                .oauth2ResourceServer(server -> server.jwt(Customizer.withDefaults())
                        .withObjectPostProcessor(new ObjectPostProcessor<BearerTokenAuthenticationFilter>() {
                            @Override
                            public <O extends BearerTokenAuthenticationFilter> O postProcess(O filter) {
                                var handler = new AuthenticationEntryPointFailureHandler(
                                        new BearerTokenAuthenticationEntryPoint());
                                // Key-fetch failures must fail authentication without a servlet error dispatch.
                                handler.setRethrowAuthenticationServiceException(false);
                                filter.setAuthenticationFailureHandler(handler);
                                return filter;
                            }
                        }))
                .build();
    }

    @Bean
    JwtDecoder jwtDecoder(JwtSecurityProperties properties) {
        var requests = new SimpleClientHttpRequestFactory();
        requests.setConnectTimeout(Duration.ofSeconds(3));
        requests.setReadTimeout(Duration.ofSeconds(3));
        var decoder = NimbusJwtDecoder.withJwkSetUri(properties.jwkSetUri())
                .jwsAlgorithm(SignatureAlgorithm.RS256)
                .restOperations(new RestTemplate(requests))
                .build();
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
                JwtValidators.createDefaultWithIssuer(properties.issuerUri()),
                new JwtClaimValidator<List<String>>("aud", audiences ->
                        audiences != null && audiences.contains(properties.audience())),
                // The default timestamp validator permits an absent exp claim.
                new JwtClaimValidator<Instant>("exp", expiry -> expiry != null)));
        return decoder;
    }
}
