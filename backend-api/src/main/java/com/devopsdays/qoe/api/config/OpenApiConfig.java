package com.devopsdays.qoe.api.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI qoeOpenApi(@Value("${server.port:8080}") String serverPort) {
        String localUrl = "http://localhost:" + serverPort;
        return new OpenAPI()
                .info(new Info()
                        .title("StreamApp API")
                        .description("REST API for the video catalog and streaming workshop demo.")
                        .version("1.0.0")
                        .contact(new Contact().name("StreamApp"))
                        .license(new License().name("Apache 2.0").url("https://www.apache.org/licenses/LICENSE-2.0")))
                .servers(List.of(new Server().url(localUrl).description("Local")));
    }
}
