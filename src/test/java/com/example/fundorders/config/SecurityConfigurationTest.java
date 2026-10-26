package com.example.fundorders.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import com.example.fundorders.controller.FundOrderQueryController;
import com.example.fundorders.repository.ProcessedEventRepository;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.security.autoconfigure.web.servlet.ServletWebSecurityAutoConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Import;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.data.domain.PageImpl;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(FundOrderQueryController.class)
@Import({SecurityConfiguration.class, JwtSecurityConfiguration.class})
@ImportAutoConfiguration(ServletWebSecurityAutoConfiguration.class)
@TestPropertySource(properties = {
        "app.security.username=analyst",
        "app.security.password=local-test-secret"
})
class SecurityConfigurationTest {
    @Autowired
    MockMvc mvc;

    @Autowired
    ApplicationContext context;

    @MockitoBean
    ProcessedEventRepository repository;

    @Test
    void credentialsAreRequiredForConsumedData() throws Exception {
        when(repository.findPage(any())).thenReturn(new PageImpl<>(List.of()));
        mvc.perform(get("/api/v1/fund-order-events")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/fund-order-events").with(request -> {
            request.addHeader("Authorization", "Basic YW5hbHlzdDp3cm9uZw==");
            return request;
        })).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/fund-order-events").with(request -> {
            request.addHeader("Authorization", "Basic YW5hbHlzdDpsb2NhbC10ZXN0LXNlY3JldA==");
            return request;
        })).andExpect(status().isOk());
    }

    @Test
    void defaultProfileSelectsOnlyBasicAndRejectsBearer() throws Exception {
        assertThat(context.getBeansOfType(SecurityFilterChain.class)).hasSize(1);
        assertThat(context.getBeansOfType(BasicCredentialsProperties.class)).hasSize(1);
        assertThat(context.getBeansOfType(JwtDecoder.class)).isEmpty();
        mvc.perform(get("/api/v1/fund-order-events").header("Authorization", "Bearer unused-token"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void healthPathsDoNotRequireCredentials() throws Exception {
        mvc.perform(get("/actuator/health")).andExpect(status().isNotFound());
        mvc.perform(get("/actuator/health/liveness")).andExpect(status().isNotFound());
        mvc.perform(get("/actuator/health/readiness")).andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/fund-order-events")).andExpect(status().isUnauthorized());
    }
}
