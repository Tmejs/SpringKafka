package com.example.fundorders;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.example.fundorders.config.BasicCredentialsProperties;
import com.example.fundorders.support.TestJwtIssuer;
import com.nimbusds.jose.JWSAlgorithm;

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.postgresql.PostgreSQLContainer;
import tools.jackson.databind.json.JsonMapper;

@Testcontainers
@ActiveProfiles("jwt")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class JwtFundOrderFlowIT {
    private static final String API = "/api/v1/fund-order-events";
    private static final TestJwtIssuer ISSUER = new TestJwtIssuer();
    @Container static final KafkaContainer kafka = new KafkaContainer("apache/kafka:4.0.0");
    @Container static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16-alpine");
    @Value("${local.server.port}") int port;
    @Autowired ApplicationContext context;
    private final HttpClient http = HttpClient.newHttpClient();

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.kafka.bootstrap-servers", kafka::getBootstrapServers);
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("app.kafka.partitions", () -> 1);
        registry.add("app.security.username", () -> "");
        registry.add("app.security.password", () -> "");
        registry.add("app.security.jwt.issuer-uri", () -> TestJwtIssuer.ISSUER);
        registry.add("app.security.jwt.audience", () -> TestJwtIssuer.AUDIENCE);
        registry.add("app.security.jwt.jwk-set-uri", ISSUER::jwkSetUri);
    }

    @AfterAll static void closeIssuer() { ISSUER.close(); }

    @Test
    void writeTokenPublishesAndReadTokenRetrievesConsumedData() throws Exception {
        var id = UUID.randomUUID();
        var write = "Bearer " + ISSUER.token("fund-orders.write");
        var read = "Bearer " + ISSUER.token("fund-orders.read");
        assertThat(send("POST", API, event(id), "application/json", write).statusCode()).isEqualTo(202);
        await().atMost(Duration.ofSeconds(20)).untilAsserted(() -> {
            var response = send("GET", API + "/" + id, null, null, read);
            assertThat(response.statusCode()).isEqualTo(200);
            assertThat(response.body()).contains(id.toString(), "REJECTED");
            assertThat(response.headers().allValues("Set-Cookie")).isEmpty();
        });
        assertThat(send("GET", API, null, null, read).statusCode()).isEqualTo(200);
        assertThat(send("GET", API, null, null, null).statusCode()).isEqualTo(401);
    }

    @Test
    void realScopeMappingEnforcesBothReadAndPublishBoundaries() throws Exception {
        var id = UUID.randomUUID();
        var write = "Bearer " + ISSUER.token("fund-orders.write");
        var read = "Bearer " + ISSUER.token("fund-orders.read");
        for (var path : List.of(API, API + "/" + id)) {
            assertThat(send("GET", path, null, null, write).statusCode()).isEqualTo(403);
        }
        assertThat(send("POST", API, event(id), "application/json", read).statusCode()).isEqualTo(403);
        assertThat(send("POST", API + "/raw", "not-json", "text/plain", read).statusCode()).isEqualTo(403);
        var noScopes = "Bearer " + ISSUER.token("");
        assertThat(send("GET", API, null, null, noScopes).statusCode()).isEqualTo(403);
        assertThat(send("POST", API, event(id), "application/json", noScopes).statusCode()).isEqualTo(403);
    }

    @ParameterizedTest
    @ValueSource(strings = {"malformed", "expired", "future", "issuer", "audience", "missing-expiry", "signature", "algorithm"})
    void invalidSignedOrMalformedBearerRequestsAreUnauthorized(String defect) throws Exception {
        var token = switch (defect) {
            case "malformed" -> "not-a-jwt";
            case "expired" -> ISSUER.token(claims -> claims.expirationTime(Date.from(Instant.now().minusSeconds(180))));
            case "future" -> ISSUER.token(claims -> claims.notBeforeTime(Date.from(Instant.now().plusSeconds(180))));
            case "issuer" -> ISSUER.token(claims -> claims.issuer("https://untrusted.example.test"));
            case "audience" -> ISSUER.token(claims -> claims.audience("another-api"));
            case "missing-expiry" -> ISSUER.token(claims -> claims.expirationTime(null));
            case "signature" -> ISSUER.token(TestJwtIssuer.newKey("primary"), JWSAlgorithm.RS256, claims -> {});
            case "algorithm" -> ISSUER.token(ISSUER.key, JWSAlgorithm.RS512, claims -> {});
            default -> throw new IllegalArgumentException(defect);
        };
        var response = send("GET", API, null, null, "Bearer " + token);
        assertThat(response.statusCode()).isEqualTo(401);
        assertThat(response.headers().firstValue("WWW-Authenticate")).get().asString().startsWith("Bearer");
    }

    @Test
    void jwtProfileHasNoBasicFallbackAndHealthIsPublic() throws Exception {
        assertThat(context.getBeansOfType(SecurityFilterChain.class)).hasSize(1);
        assertThat(context.getBeansOfType(BasicCredentialsProperties.class)).isEmpty();
        assertThat(context.getBeansOfType(UserDetailsService.class)).isEmpty();
        assertThat(send("GET", API, null, null, "Basic YW5hbHlzdDpsb2NhbC10ZXN0LXNlY3JldA==").statusCode())
                .isEqualTo(401);
        for (var path : List.of("/actuator/health", "/actuator/health/liveness", "/actuator/health/readiness")) {
            assertThat(send("GET", path, null, null, null).statusCode()).isEqualTo(200);
        }
    }

    @Test
    void rawWriteTokenSupportsValidJsonAndConsumerDlqRecovery() throws Exception {
        var id = UUID.randomUUID();
        var write = "Bearer " + ISSUER.token("fund-orders.write");
        var read = "Bearer " + ISSUER.token("fund-orders.read");
        assertThat(send("POST", API + "/raw", event(id), "text/plain", write).statusCode()).isEqualTo(202);
        await().atMost(Duration.ofSeconds(20)).untilAsserted(() ->
                assertThat(send("GET", API + "/" + id, null, null, read).statusCode()).isEqualTo(200));
        var malformed = "jwt-demo-invalid-" + UUID.randomUUID();
        assertThat(send("POST", API + "/raw", malformed, "text/plain", write).statusCode()).isEqualTo(202);
        var props = Map.<String, Object>of(
                ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, kafka.getBootstrapServers(),
                ConsumerConfig.GROUP_ID_CONFIG, "jwt-dlq-" + UUID.randomUUID(),
                ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class,
                ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        try (var consumer = new KafkaConsumer<String, String>(props)) {
            consumer.assign(List.of(new TopicPartition("fund-order-events.DLQ", 0)));
            consumer.seekToBeginning(consumer.assignment());
            var found = false;
            var end = System.nanoTime() + Duration.ofSeconds(20).toNanos();
            while (!found && System.nanoTime() < end) {
                for (var record : consumer.poll(Duration.ofMillis(200))) {
                    if (record.value().equals(malformed)) {
                        assertThat(new String(record.headers().lastHeader(KafkaHeaders.DLT_ORIGINAL_TOPIC).value(),
                                StandardCharsets.UTF_8)).isEqualTo("fund-order-events");
                        assertThat(record.headers().lastHeader(KafkaHeaders.DLT_ORIGINAL_OFFSET)).isNotNull();
                        found = true;
                    }
                }
            }
            assertThat(found).as("original raw message in DLQ").isTrue();
        }
    }

    @Test
    @ExtendWith(OutputCaptureExtension.class)
    void unavailableJwkEndpointRejectsUncachedKeysWithoutServerError(CapturedOutput output) throws Exception {
        var unknown = ISSUER.token(TestJwtIssuer.newKey("outage-" + UUID.randomUUID()),
                JWSAlgorithm.RS256, claims -> {});
        ISSUER.available = false;
        try {
            var response = send("GET", API, null, null, "Bearer " + unknown);
            assertThat(response.statusCode()).isEqualTo(401);
            assertThat(output.getAll()).doesNotContain("Servlet.service()", "AuthenticationServiceException");
            assertThat(response.headers().firstValue("WWW-Authenticate")).get().asString().startsWith("Bearer");
        } finally {
            ISSUER.available = true;
        }
    }

    @Test
    void demoScriptPublishesAndPollsUsingBearerWithoutBasicCredentials() throws Exception {
        var output = Files.createTempFile("jwt-demo-", ".log");
        var builder = new ProcessBuilder("bash", "scripts/demo.sh", "raw-valid")
                .redirectErrorStream(true).redirectOutput(output.toFile());
        builder.environment().put("BASE_URL", "http://localhost:" + port);
        builder.environment().put("AUTH_MODE", "jwt");
        builder.environment().put("ACCESS_TOKEN", ISSUER.token("fund-orders.read fund-orders.write"));
        builder.environment().remove("APP_BASIC_AUTH_USERNAME");
        builder.environment().remove("APP_BASIC_AUTH_PASSWORD");
        builder.environment().remove("EVENT_ID");
        builder.environment().remove("ORDER_ID");
        var process = builder.start();
        try {
            assertThat(process.waitFor(40, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
            assertThat(process.exitValue()).as(Files.readString(output)).isZero();
            assertThat(Files.readString(output)).contains("HTTP 202", "Consumed event", "HTTP 200");
        } finally {
            process.destroyForcibly();
            Files.deleteIfExists(output);
        }
    }

    @Test
    void jwtOpenApiDescribesBearerScopesAndExportsSeparateArtifact() throws Exception {
        for (var path : List.of("/swagger-ui/index.html", "/swagger-ui/swagger-ui-bundle.js",
                "/v3/api-docs", "/v3/api-docs/swagger-config", "/v3/api-docs.yaml")) {
            assertThat(send("GET", path, null, null, null).statusCode()).as(path).isEqualTo(200);
        }
        assertThat(send("GET", "/swagger-ui.html", null, null, null).statusCode()).isEqualTo(302);
        assertThat(send("GET", API, null, null, null).statusCode()).isEqualTo(401);
        var response = send("GET", "/v3/api-docs", null, null, null);
        assertThat(response.statusCode()).isEqualTo(200);
        var document = JsonMapper.builder().build().readTree(response.body());
        assertThat(document.at("/security/0/bearerAuth").isArray()).isTrue();
        assertThat(document.at("/components/securitySchemes/bearerAuth/scheme").asString()).isEqualTo("bearer");
        assertThat(document.at("/components/securitySchemes/basicAuth").isMissingNode()).isTrue();
        for (var path : document.path("paths")) {
            for (var method : List.of("get", "post")) {
                if (path.has(method)) {
                    assertThat(path.path(method).at("/responses/403").isMissingNode()).isFalse();
                    assertThat(path.path(method).path("x-required-scope").asString())
                            .isEqualTo(method.equals("get") ? "fund-orders.read" : "fund-orders.write");
                }
            }
        }
        var output = Path.of(System.getProperty("openapi.outputDirectory", "target/openapi"));
        Files.createDirectories(output);
        Files.writeString(output.resolve("openapi-jwt.json"), response.body(), StandardCharsets.UTF_8);
    }

    private HttpResponse<String> send(String method, String path, String body, String contentType, String authorization)
            throws Exception {
        var request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path)).timeout(Duration.ofSeconds(10));
        if (authorization != null) { request.header("Authorization", authorization); }
        if (contentType != null) { request.header("Content-Type", contentType); }
        request.method(method, body == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(body));
        return http.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }

    private String event(UUID id) {
        return """
                {"schemaVersion":1,"eventId":"%s","orderId":"%s","partnerCode":"AGENCY_A",
                 "status":"REJECTED","occurredAt":"2026-10-26T08:00:00Z"}
                """.formatted(id, UUID.randomUUID());
    }
}
