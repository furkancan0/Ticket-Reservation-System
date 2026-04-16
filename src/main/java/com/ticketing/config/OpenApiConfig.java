package com.ticketing.config;

import io.swagger.v3.oas.models.*;
import io.swagger.v3.oas.models.info.*;
import io.swagger.v3.oas.models.security.*;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class OpenApiConfig {

    @Value("${server.port:8080}")
    private String port;

    @Bean
    public OpenAPI openAPI() {
        return new OpenAPI()
                .info(apiInfo())
                .servers(servers())
                .components(securityComponents())
                .addSecurityItem(globalSecurity());
    }

    private Info apiInfo() {
        return new Info()
                .title("TicketHub API")
                .description("""
                        Ticket Reservation System
                        
                        A concurrency ticket booking API
                        5-minute seat holds, multi-provider payments, and real-time metrics.
                        
                        All endpoints except `POST /api/auth/*` and `GET /api/events/**` 
                        require a **Bearer JWT** token in the `Authorization` header.
                        
                        1. `POST /api/auth/register` or `POST /api/auth/login`
                        2. Copy the `token` from the response
                        3. Click **Authorize** above and paste: `Bearer <your-token>`
                        
                        ```
                        GET  /api/events                  → list events
                        GET  /api/events/{id}/seats       → view seating chart
                        POST /api/holds                   → hold a seat (5 min TTL)
                        POST /api/orders/checkout         → pay + confirm
                        GET  /api/orders/me               → view your orders
                        ```
                        """)
                .version("1.0.0")
                .contact(new Contact()
                        .name("TicketHub Team")
                        .email("api@tickethub.dev"))
                .license(new License()
                        .name("MIT")
                        .url("https://opensource.org/licenses/MIT"));
    }

    private List<Server> servers() {
        return List.of(
                new Server().url("http://localhost:" + port).description("Local development"),
                new Server().url("https://api.tickethub.dev").description("Production")
        );
    }

    private Components securityComponents() {
        return new Components()
                .addSecuritySchemes("bearerAuth",
                        new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")
                                .description("Paste your JWT token from POST /api/auth/login"));
    }

    private SecurityRequirement globalSecurity() {
        return new SecurityRequirement().addList("bearerAuth");
    }
}
