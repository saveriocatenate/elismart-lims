package it.elismart_lims.config;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import dev.langchain4j.model.chat.ChatLanguageModel;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for {@link GeminiConfig} startup behaviour.
 *
 * <p>Covers the graceful-degradation contract: the bean must always be created (so that
 * non-AI endpoints continue to work), and a {@code WARN}-level message must be logged
 * when {@code GEMINI_API_KEY} is absent. The API key value is never written to any log.</p>
 */
class GeminiConfigTest {

    private GeminiConfig geminiConfig;
    private Logger geminiConfigLogger;
    private ListAppender<ILoggingEvent> listAppender;

    /** A model name representative of the default configured in {@code application.yml}. */
    private static final String DEFAULT_MODEL = "gemini-2.0-flash";

    @BeforeEach
    void setUp() {
        geminiConfig = new GeminiConfig();

        geminiConfigLogger = (Logger) LoggerFactory.getLogger(GeminiConfig.class);
        listAppender = new ListAppender<>();
        listAppender.start();
        geminiConfigLogger.addAppender(listAppender);
    }

    @AfterEach
    void tearDown() {
        geminiConfigLogger.detachAppender(listAppender);
    }

    // ── Graceful degradation: bean must always be created ─────────────────────

    /**
     * {@link GeminiConfig#chatLanguageModel} must not throw when the API key is an empty string.
     * The application starts without a key; the endpoint fails gracefully at call time.
     * LangChain4j's {@code GoogleAiGeminiChatModel.builder()} rejects blank keys at build time,
     * so GeminiConfig must return a stub instead of calling the builder.
     */
    @Test
    void chatLanguageModel_shouldNotThrow_whenApiKeyIsBlank() {
        assertThatCode(() -> geminiConfig.chatLanguageModel("", DEFAULT_MODEL))
                .doesNotThrowAnyException();
    }

    /**
     * Even with a blank key, the method must return a non-null {@link ChatLanguageModel} bean.
     */
    @Test
    void chatLanguageModel_shouldReturnNonNullBean_whenApiKeyIsBlank() {
        ChatLanguageModel bean = geminiConfig.chatLanguageModel("", DEFAULT_MODEL);

        assertThat(bean).isNotNull();
    }

    /**
     * The stub returned for a blank key must throw a {@link RuntimeException} whose message
     * matches the {@code "HTTP error (401)"} pattern so that
     * {@code GeminiService.classifyException()} recognises it as an auth failure.
     */
    @Test
    void chatLanguageModel_stubBean_shouldThrowWith401Pattern_whenCalled() {
        ChatLanguageModel stub = geminiConfig.chatLanguageModel("", DEFAULT_MODEL);

        assertThatThrownBy(() -> stub.generate("any prompt"))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("HTTP error (401)");
    }

    // ── Warning logged when key is absent ─────────────────────────────────────

    /**
     * When the API key is blank, exactly one {@code WARN}-level message must be emitted
     * mentioning {@code GEMINI_API_KEY}. This alerts operators who may have forgotten to
     * set the environment variable before going live.
     */
    @Test
    void chatLanguageModel_shouldEmitWarn_whenApiKeyIsBlank() {
        geminiConfig.chatLanguageModel("", DEFAULT_MODEL);

        assertThat(listAppender.list)
                .filteredOn(e -> e.getLevel() == Level.WARN)
                .anyMatch(e -> e.getFormattedMessage().contains("GEMINI_API_KEY"));
    }

    /**
     * When the API key is null (not merely blank), the same {@code WARN} must appear.
     */
    @Test
    void chatLanguageModel_shouldEmitWarn_whenApiKeyIsNull() {
        geminiConfig.chatLanguageModel(null, DEFAULT_MODEL);

        assertThat(listAppender.list)
                .filteredOn(e -> e.getLevel() == Level.WARN)
                .anyMatch(e -> e.getFormattedMessage().contains("GEMINI_API_KEY"));
    }

    /**
     * When the API key is whitespace-only (e.g. a blank env var value), the warning must fire.
     */
    @Test
    void chatLanguageModel_shouldEmitWarn_whenApiKeyIsWhitespace() {
        geminiConfig.chatLanguageModel("   ", DEFAULT_MODEL);

        assertThat(listAppender.list)
                .filteredOn(e -> e.getLevel() == Level.WARN)
                .anyMatch(e -> e.getFormattedMessage().contains("GEMINI_API_KEY"));
    }

    // ── No warning when key is present ───────────────────────────────────────

    /**
     * When a non-blank API key is provided, no {@code WARN} message should be logged.
     * The warning is only a missing-key alert, not a key-validity check.
     */
    @Test
    void chatLanguageModel_shouldNotEmitWarn_whenApiKeyIsPresent() {
        geminiConfig.chatLanguageModel("some-api-key-value", DEFAULT_MODEL);

        assertThat(listAppender.list)
                .filteredOn(e -> e.getLevel() == Level.WARN)
                .isEmpty();
    }

    // ── Key value must never appear in logs ───────────────────────────────────

    /**
     * Even when a key is set, its value must not appear in any log message.
     * This test uses a distinctive sentinel value that would be trivially detectable.
     */
    @Test
    void chatLanguageModel_shouldNeverLogKeyValue() {
        String sentinelKey = "SUPER_SECRET_API_KEY_DO_NOT_LOG";

        geminiConfig.chatLanguageModel(sentinelKey, DEFAULT_MODEL);

        assertThat(listAppender.list)
                .noneMatch(e -> e.getFormattedMessage().contains(sentinelKey));
    }
}
