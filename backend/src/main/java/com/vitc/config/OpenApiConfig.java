package com.vitc.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.servers.Server;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Swagger / OpenAPI 3 documentation.
 * UI: http://localhost:8080/swagger-ui.html   Spec: http://localhost:8080/v3/api-docs
 */
@Configuration
public class OpenApiConfig {

    @Value("${server.port:8080}")
    private String port;

    @Bean
    public OpenAPI vitcOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("VITC Institute REST API")
                        .version("1.0.0")
                        .description("""
                                REST APIs for the VITC website: courses, assignments, reviews, orders,
                                payments, gallery, FAQs, users and admin management.
                                All responses are wrapped in a common ApiResponse envelope and validation
                                errors are returned by a global exception handler.""")
                        .contact(new Contact().name("VITC Institute").email("support@vitc.in"))
                        .license(new License().name("Proprietary")))
                .servers(List.of(
                        new Server().url("http://localhost:" + port).description("Local"),
                        new Server().url("https://api.vitc.in").description("Production")));
    }
}
