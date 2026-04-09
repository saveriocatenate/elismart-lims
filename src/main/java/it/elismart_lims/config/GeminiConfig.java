package it.elismart_lims.config;

import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.googleai.GoogleAiGeminiChatModel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

/**
 * Spring configuration that registers a LangChain4j {@link ChatLanguageModel} bean backed by
 * Google Gemini.
 *
 * <p>Required properties:
 * <ul>
 *   <li>{@code gemini.api-key} — Google Gemini API key</li>
 *   <li>{@code gemini.model} — Gemini model name (e.g. {@code gemini-2.0-flash})</li>
 * </ul>
 * </p>
 */
@Configuration
public class GeminiConfig {

    /**
     * Creates a {@link ChatLanguageModel} bean pointed at the Google AI Gemini API.
     *
     * <p>The read timeout is set to 120 seconds to accommodate long-running analysis prompts.</p>
     *
     * @param apiKey    the Google Gemini API key, from {@code gemini.api-key}
     * @param modelName the Gemini model name, from {@code gemini.model}
     * @return a configured {@link GoogleAiGeminiChatModel}
     */
    @Bean
    public ChatLanguageModel chatLanguageModel(
            @Value("${gemini.api-key:}") String apiKey,
            @Value("${gemini.model}") String modelName) {
        return GoogleAiGeminiChatModel.builder()
                .apiKey(apiKey)
                .modelName(modelName)
                .timeout(Duration.ofSeconds(120))
                .build();
    }
}
