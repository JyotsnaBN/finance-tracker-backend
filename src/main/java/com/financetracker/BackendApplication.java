package com.financetracker;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.github.cdimascio.dotenv.Dotenv;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@EnableAsync
public class BackendApplication {

    public static void main(String[] args) {
        Dotenv dotenv = Dotenv.configure()
            .directory("./")
            .ignoreIfMissing()
            .load();

        dotenv.entries().forEach(entry ->
            System.setProperty(entry.getKey(), entry.getValue())
        );

        SpringApplication.run(BackendApplication.class, args);
    }

    @Bean
    public ObjectMapper objectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        // Serialize Instant/LocalDateTime as ISO-8601 strings, not epoch numbers.
        // Matches spring.jackson.serialization.write-dates-as-timestamps=false
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        return mapper;
    }
}
