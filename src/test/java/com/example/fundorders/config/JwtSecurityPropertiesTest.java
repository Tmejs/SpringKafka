package com.example.fundorders.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

class JwtSecurityPropertiesTest {
    @ParameterizedTest
    @CsvSource({"issuer-uri,''", "audience,''", "jwk-set-uri,''", "issuer-uri,not-a-url",
            "jwk-set-uri,file:/tmp/keys", "issuer-uri,http:///missing-host", "jwk-set-uri,/relative"})
    void missingOrInvalidTrustConfigurationPreventsStartup(String property, String value) {
        new ApplicationContextRunner().withUserConfiguration(PropertiesConfiguration.class)
                .withPropertyValues("app.security.jwt.issuer-uri=https://issuer.example.test",
                        "app.security.jwt.audience=fund-orders-api",
                        "app.security.jwt.jwk-set-uri=https://issuer.example.test/jwks",
                        "app.security.jwt." + property + "=" + value)
                .run(context -> assertThat(context).hasFailed());
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(JwtSecurityProperties.class)
    static class PropertiesConfiguration {}
}
