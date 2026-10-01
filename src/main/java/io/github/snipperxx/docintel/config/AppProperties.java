package io.github.snipperxx.docintel.config;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

@Validated
@ConfigurationProperties(prefix = "app")
public record AppProperties(@NotNull Jwt jwt, @NotNull Kafka kafka, @NotNull Chunking chunking, @NotNull Search search) {

    /**
     * @param secret base64-encoded HMAC key, at least 256 bits
     */
    public record Jwt(@NotBlank String secret, @NotBlank String issuer, @NotNull Duration ttl) {
    }

    public record Kafka(@NotBlank String documentUploadedTopic, @Min(1) int partitions) {
    }

    public record Chunking(@Min(200) int maxChars, @Min(0) int overlapChars) {
    }

    public record Search(@Min(1) int defaultTopK, @Min(1) int maxTopK) {
    }
}
