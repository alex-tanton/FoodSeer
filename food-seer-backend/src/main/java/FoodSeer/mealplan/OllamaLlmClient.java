package FoodSeer.mealplan;

import java.time.Duration;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * Calls a local Ollama server (the runtime the FoodSeer chatbot already uses) in
 * JSON mode with temperature 0, so the same request gives the same answer.
 *
 * <p>
 * Settings: {@code foodseer.llm.enabled} (default true), {@code foodseer.llm.url}
 * (default http://localhost:11434/api/chat), {@code foodseer.llm.model} (default
 * qwen2.5:1.5b), {@code foodseer.llm.timeout-seconds} (default 20).
 * </p>
 */
@Component
public class OllamaLlmClient implements LlmClient {

    private final ObjectMapper json = new ObjectMapper();
    private final RestTemplate http;
    private final boolean enabled;
    private final String url;
    private final String model;

    public OllamaLlmClient(final RestTemplateBuilder builder,
            @Value("${foodseer.llm.enabled:true}") final boolean enabled,
            @Value("${foodseer.llm.url:http://localhost:11434/api/chat}") final String url,
            @Value("${foodseer.llm.model:qwen2.5:1.5b}") final String model,
            @Value("${foodseer.llm.timeout-seconds:20}") final int timeoutSeconds) {
        this.http = builder.setConnectTimeout(Duration.ofSeconds(2))
                .setReadTimeout(Duration.ofSeconds(Math.max(1, timeoutSeconds))).build();
        this.enabled = enabled;
        this.url = url;
        this.model = model;
    }

    @Override
    public Optional<String> completeJson(final String system, final String user) {
        if (!enabled) {
            return Optional.empty();
        }
        try {
            final ObjectNode body = json.createObjectNode();
            body.put("model", model);
            body.put("stream", false);
            body.put("format", "json");
            body.putObject("options").put("temperature", 0);
            body.putArray("messages").add(message("system", system)).add(message("user", user));
            final HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            final String reply = http.postForObject(url, new HttpEntity<>(json.writeValueAsString(body), headers),
                    String.class);
            if (reply == null) {
                return Optional.empty();
            }
            final JsonNode content = json.readTree(reply).path("message").path("content");
            return content.isTextual() ? Optional.of(content.asText()) : Optional.empty();
        } catch (final Exception e) {
            return Optional.empty();
        }
    }

    private ObjectNode message(final String role, final String content) {
        final ObjectNode m = json.createObjectNode();
        m.put("role", role);
        m.put("content", content);
        return m;
    }

    @Override
    public String model() {
        return model;
    }
}
