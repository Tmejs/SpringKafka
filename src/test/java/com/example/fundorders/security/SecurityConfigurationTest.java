package com.example.fundorders.security;

import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.any;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.fundorders.read.FundOrderQueryController;
import com.example.fundorders.store.ProcessedEventRepository;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.security.autoconfigure.web.servlet.ServletWebSecurityAutoConfiguration;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(FundOrderQueryController.class)
@Import(SecurityConfiguration.class)
@ImportAutoConfiguration(ServletWebSecurityAutoConfiguration.class)
@TestPropertySource(properties = {
        "app.security.username=analyst",
        "app.security.password=local-test-secret"
})
class SecurityConfigurationTest {
    @Autowired
    MockMvc mvc;

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
    void healthPathsDoNotRequireCredentials() throws Exception {
        mvc.perform(get("/actuator/health")).andExpect(status().isNotFound());
        mvc.perform(get("/actuator/health/liveness")).andExpect(status().isNotFound());
        mvc.perform(get("/actuator/health/readiness")).andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/fund-order-events")).andExpect(status().isUnauthorized());
    }
}
