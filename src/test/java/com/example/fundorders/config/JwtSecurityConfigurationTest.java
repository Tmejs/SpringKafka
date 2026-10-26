package com.example.fundorders.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;

import com.example.fundorders.controller.EventPublishController;
import com.example.fundorders.controller.FundOrderQueryController;
import com.example.fundorders.model.OrderStatus;
import com.example.fundorders.model.ProcessedFundOrderEvent;
import com.example.fundorders.model.PublishReceipt;
import com.example.fundorders.repository.ProcessedEventRepository;
import com.example.fundorders.service.EventPublisher;
import com.example.fundorders.service.FundOrderEventCodec;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.security.autoconfigure.web.servlet.ServletWebSecurityAutoConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.http.HttpMethod;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

@WebMvcTest({FundOrderQueryController.class, EventPublishController.class})
@Import({SecurityConfiguration.class, JwtSecurityConfiguration.class})
@ImportAutoConfiguration(ServletWebSecurityAutoConfiguration.class)
@ActiveProfiles("jwt")
@TestPropertySource(properties = {
        "app.security.username=", "app.security.password=",
        "app.security.jwt.issuer-uri=https://issuer.example.test",
        "app.security.jwt.audience=fund-orders-api",
        "app.security.jwt.jwk-set-uri=http://localhost:1/jwks"
})
class JwtSecurityConfigurationTest {
    private static final String API = "/api/v1/fund-order-events";
    private static final UUID ID = UUID.fromString("065d9f50-94ae-4342-96cf-38a70a550184");
    @Autowired MockMvc mvc;
    @Autowired ApplicationContext context;
    @MockitoBean ProcessedEventRepository repository;
    @MockitoBean EventPublisher publisher;
    @MockitoBean FundOrderEventCodec codec;

    @BeforeEach
    void businessResponses() {
        when(repository.findPage(any())).thenReturn(new PageImpl<>(List.of()));
        when(repository.findByEventId(ID)).thenReturn(Optional.of(new ProcessedFundOrderEvent(
                1, ID, ID, "AGENCY_A", OrderStatus.ACCEPTED, Instant.now(), Instant.now())));
        when(publisher.publish(any())).thenReturn(new PublishReceipt("events", 0, 1, "key"));
        when(publisher.publishRaw(any())).thenReturn(new PublishReceipt("events", 0, 1, "key"));
    }

    private MockHttpServletRequestBuilder endpoint(String method, String path) {
        var request = request(HttpMethod.valueOf(method), path);
        if (method.equals("POST")) {
            request.contentType(path.endsWith("/raw") ? "text/plain" : "application/json")
                    .content(path.endsWith("/raw") ? "not-json" : "{\"schemaVersion\":1}");
        }
        return request;
    }

    @Test
    void jwtProfileRejectsBasicCredentials() throws Exception {
        mvc.perform(endpoint("GET", API)
                .header("Authorization", "Basic YW5hbHlzdDpsb2NhbC10ZXN0LXNlY3JldA=="))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void jwtProfileStartsWithoutBasicBeansOrCredentials() {
        assertThat(context.getBeansOfType(SecurityFilterChain.class)).hasSize(1);
        assertThat(context.getBeansOfType(JwtDecoder.class)).hasSize(1);
        assertThat(context.getBeansOfType(BasicCredentialsProperties.class)).isEmpty();
        assertThat(context.getBeansOfType(UserDetailsService.class)).isEmpty();
        assertThat(context.getBeansOfType(PasswordEncoder.class)).isEmpty();
    }

    static Stream<Arguments> endpoints() {
        return Stream.of(Arguments.of("GET", API, "fund-orders.read", 200),
                Arguments.of("GET", API + "/" + ID, "fund-orders.read", 200),
                Arguments.of("POST", API, "fund-orders.write", 202),
                Arguments.of("POST", API + "/raw", "fund-orders.write", 202));
    }

    @ParameterizedTest @MethodSource("endpoints")
    void appropriateScopeAllowsAccess(String method, String path, String scope, int expected) throws Exception {
        mvc.perform(endpoint(method, path).with(jwt().authorities(new SimpleGrantedAuthority("SCOPE_" + scope))))
                .andExpect(status().is(expected));
    }

    @ParameterizedTest @MethodSource("endpoints")
    void missingAuthenticationIsUnauthorized(String method, String path, String scope, int expected) throws Exception {
        mvc.perform(endpoint(method, path)).andExpect(status().isUnauthorized());
    }

    @ParameterizedTest @MethodSource("endpoints")
    void wrongScopeIsForbidden(String method, String path, String scope, int expected) throws Exception {
        var wrong = scope.equals("fund-orders.read") ? "fund-orders.write" : "fund-orders.read";
        mvc.perform(endpoint(method, path).with(jwt().authorities(new SimpleGrantedAuthority("SCOPE_" + wrong))))
                .andExpect(status().isForbidden());
    }

    @ParameterizedTest @MethodSource("endpoints")
    void noScopeIsForbidden(String method, String path, String scope, int expected) throws Exception {
        mvc.perform(endpoint(method, path).with(jwt().authorities(List.of()))).andExpect(status().isForbidden());
    }

    @Test
    void publicHealthNeedsNoCredentialsAndUnknownRoutesAreDenied() throws Exception {
        for (var path : List.of("/actuator/health", "/actuator/health/liveness", "/actuator/health/readiness")) {
            mvc.perform(endpoint("GET", path)).andExpect(status().isNotFound());
        }
        mvc.perform(endpoint("GET", "/unexpected").with(jwt())).andExpect(status().isForbidden());
        mvc.perform(endpoint("HEAD", API).with(jwt().authorities(new SimpleGrantedAuthority("SCOPE_fund-orders.read"))))
                .andExpect(status().isOk());
    }
}
