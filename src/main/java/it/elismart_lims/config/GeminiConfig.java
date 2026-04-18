package it.elismart_lims.config;

import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.googleai.GoogleAiGeminiChatModel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

/**
 * Spring configuration that registers a LangChain4j {@link ChatLanguageModel} bean backed by
 * Google Gemini.
 *
 * <p>The application uses <strong>graceful degradation</strong> for the Gemini key: the bean is
 * always created (even when {@code GEMINI_API_KEY} is not set) so that non-AI endpoints continue
 * to work. If the key is absent, a startup warning is emitted and the
 * {@code POST /api/ai/analyze} endpoint will fail at call time with a
 * {@link it.elismart_lims.exception.model.GeminiServiceException} (502). This design is
 * intentional: AI analysis is an optional feature, not a prerequisite for lab operations.</p>
 *
 * <p>The key is never logged — not even partially.</p>
 *
 * <p>Required properties:
 * <ul>
 *   <li>{@code gemini.api-key} — Google Gemini API key (optional; app starts without it)</li>
 *   <li>{@code gemini.model} — Gemini model name (e.g. {@code gemini-2.0-flash})</li>
 *   <li>{@code gemini.timeout-ms} — HTTP read timeout in milliseconds (default: 120000)</li>
 * </ul>
 * </p>
 */
@Slf4j
@Configuration
public class GeminiConfig {

    /**
     * Creates a {@link ChatLanguageModel} bean pointed at the Google AI Gemini API.
     *
     * <p>The read timeout is driven by {@code gemini.timeout-ms} (default: 120 000 ms),
     * overridable via the {@code GEMINI_TIMEOUT_MS} environment variable.</p>
     *
     * <p><strong>Graceful degradation</strong>: if {@code apiKey} is blank, a {@code WARN}-level
     * message is logged and a stub {@link ChatLanguageModel} is returned instead of calling the
     * LangChain4j builder (which rejects blank keys at build time). The stub throws a
     * {@link RuntimeException} whose message matches the {@code "HTTP error (401)"} pattern, so
     * {@link it.elismart_lims.service.GeminiService#analyze} classifies it as an authentication
     * failure and the endpoint returns an appropriate error — without breaking non-AI endpoints.
     * The key value is never written to any log sink.</p>
     *
     * @param apiKey    the Google Gemini API key, from {@code gemini.api-key} (defaults to empty)
     * @param modelName the Gemini model name, from {@code gemini.model}
     * @param timeoutMs HTTP read timeout in milliseconds, from {@code gemini.timeout-ms}
     * @return a fully configured {@link GoogleAiGeminiChatModel}, or a fail-fast stub when
     *         the key is absent
     */
    @Bean
    public ChatLanguageModel chatLanguageModel(
            @Value("${gemini.api-key:}") String apiKey,
            @Value("${gemini.model}") String modelName,
            @Value("${gemini.timeout-ms}") long timeoutMs) {
        if (apiKey == null || apiKey.isBlank()) {
            log.warn("GEMINI_API_KEY environment variable is not set. "
                    + "AI analysis features (POST /api/ai/analyze) will fail at runtime. "
                    + "Set GEMINI_API_KEY in .env to enable Gemini integration.");
            // Return a stub so the Spring context loads and non-AI endpoints work normally.
            // The "HTTP error (401)" message pattern is recognised by GeminiService.classifyException()
            // and converted into a non-transient GeminiServiceException (HTTP 401).
            return prompt -> {
                throw new RuntimeException(
                        "HTTP error (401): GEMINI_API_KEY is not configured. "
                        + "Set GEMINI_API_KEY in .env to enable AI analysis.");
            };
        }
        return GoogleAiGeminiChatModel.builder()
                .apiKey(apiKey)
                .modelName(modelName)
                .timeout(Duration.ofMillis(timeoutMs))
                .build();
    }
}
