package com.chetan.discovery.studentservice;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import java.util.List;

/** Describes the public API; a relative server URL supports both direct and Gateway access. */
@Configuration
public class OpenApiConfiguration {
    @Bean
    public OpenAPI serviceOpenApi() {
        return new OpenAPI().info(new Info().title("Student Service API").version("1.0.0")
                .description("Demo student lookup with instance and configuration identity. Data is illustrative, not persisted."))
                .servers(List.of(new Server().url("/").description("Current host: service or API Gateway")));
    }
}
