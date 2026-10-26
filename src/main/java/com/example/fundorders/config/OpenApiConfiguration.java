package com.example.fundorders.config;

import java.util.List;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;

@Configuration
public class OpenApiConfiguration {
    @Bean
    OpenAPI openApi(Environment environment) {
        var jwt = environment.acceptsProfiles(Profiles.of("jwt"));
        var name = jwt ? "bearerAuth" : "basicAuth";
        var scheme = new SecurityScheme().type(SecurityScheme.Type.HTTP).scheme(jwt ? "bearer" : "basic");
        if (jwt) { scheme.bearerFormat("JWT"); }
        return new OpenAPI()
                .info(new Info().title("Fund Order Event API").version("0.1.0")
                        .description("Fictional investment-fund order status events. Publication and consumption are asynchronous."))
                .servers(List.of(new Server().url("/").description("Current server")))
                .components(new Components().addSecuritySchemes(name, scheme))
                .addSecurityItem(new SecurityRequirement().addList(name));
    }

    @Bean
    @Profile("jwt")
    OpenApiCustomizer jwtScopeDocumentation() {
        return document -> document.getPaths().values().forEach(path ->
                path.readOperationsMap().forEach((method, operation) -> {
                    var scope = method.name().equals("POST") ? "fund-orders.write" : "fund-orders.read";
                    operation.addExtension("x-required-scope", scope);
                    operation.getResponses().addApiResponse("403",
                            new ApiResponse().description("Required scope: " + scope));
                    operation.getResponses().get("401").setDescription("Bearer authentication required");
                }));
    }
}
