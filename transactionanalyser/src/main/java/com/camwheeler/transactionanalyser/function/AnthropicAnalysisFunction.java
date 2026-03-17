package com.camwheeler.transactionanalyser.function;

import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import com.anthropic.models.messages.Message;
import com.anthropic.models.messages.MessageCreateParams;
import com.anthropic.models.messages.Model;
import com.camwheeler.transactionanalyser.model.AnalysisResult;
import com.camwheeler.transactionanalyser.model.EnrichedFilterResult;
import com.camwheeler.transactionanalyser.model.FilterResult;
import com.camwheeler.transactionanalyser.prompt.PromptBuilder;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.flink.configuration.Configuration;
import org.apache.flink.streaming.api.functions.async.ResultFuture;
import org.apache.flink.streaming.api.functions.async.RichAsyncFunction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.Collections;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class AnthropicAnalysisFunction extends RichAsyncFunction<EnrichedFilterResult, AnalysisResult> {
    // Async Flink function that calls the Anthropic API to analyse flagged transactions.

    private static final Logger LOG = LoggerFactory.getLogger(AnthropicAnalysisFunction.class);
    private static final String MODEL = "claude-sonnet-4-6";

    private transient AnthropicClient client;
    private transient ObjectMapper objectMapper;
    private transient ExecutorService executor;

    /*
    Initialises the Anthropic client, JSON mapper, and thread pool.
    Reads ANTHROPIC_API_KEY from environment. Fails fast if not set.
    */
    @Override
    public void open(Configuration parameters) {
        String apiKey = System.getenv("ANTHROPIC_API_KEY");
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException("ANTHROPIC_API_KEY environment variable is not set");
        }

        client = AnthropicOkHttpClient.builder()
                .apiKey(apiKey)
                .build();

        objectMapper = new ObjectMapper();
        executor = Executors.newFixedThreadPool(5);
    }

    @Override
    public void close() {
        if (executor != null) {
            executor.shutdown();
        }
    }

    /*
    Called for each flagged transaction. Sends the transaction to Anthropic
    asynchronously using a thread pool. Handles three outcomes:
    - Success: parses the JSON verdict from Claude's response.
    - Parse error: returns a PARSE_ERROR fallback result.
    - API failure: returns an API_ERROR fallback result.

    Usage: Invoked by Flink's AsyncDataStream, not called directly.
    */
    @Override
    public void asyncInvoke(EnrichedFilterResult input, ResultFuture<AnalysisResult> resultFuture) {
        FilterResult current = input.getCurrent();
        String prompt = PromptBuilder.buildPrompt(current, input.getRecentHistory());

        CompletableFuture.supplyAsync(() -> {
            MessageCreateParams params = MessageCreateParams.builder()
                    .model(Model.CLAUDE_SONNET_4_6)
                    .maxTokens(1024L)
                    .addUserMessage(prompt)
                    .build();

            return client.messages().create(params);
        }, executor).thenAccept(response -> {
            try {
                AnalysisResult result = parseResponse(current, response);
                resultFuture.complete(Collections.singleton(result));
            } catch (Exception e) {
                LOG.error("Failed to parse Anthropic response for transaction {}", current.getTransaction().getTransactionId(), e);
                AnalysisResult fallback = new AnalysisResult(
                        current.getTransaction().getTransactionId(),
                        current,
                        "PARSE_ERROR",
                        "Failed to parse model response: " + e.getMessage(),
                        0.0,
                        MODEL,
                        Instant.now().toString()
                );
                resultFuture.complete(Collections.singleton(fallback));
            }
        }).exceptionally(throwable -> {
            LOG.error("Anthropic API call failed for transaction {}", current.getTransaction().getTransactionId(), throwable);
            AnalysisResult fallback = new AnalysisResult(
                    current.getTransaction().getTransactionId(),
                    current,
                    "API_ERROR",
                    "API call failed: " + throwable.getMessage(),
                    0.0,
                    MODEL,
                    Instant.now().toString()
            );
            resultFuture.complete(Collections.singleton(fallback));
            return null;
        });
    }

    // Returns a TIMEOUT fallback result if the API call exceeds the 120s limit.
    @Override
    public void timeout(EnrichedFilterResult input, ResultFuture<AnalysisResult> resultFuture) {
        FilterResult current = input.getCurrent();
        LOG.warn("Timeout analysing transaction {}", current.getTransaction().getTransactionId());
        AnalysisResult timeoutResult = new AnalysisResult(
                current.getTransaction().getTransactionId(),
                current,
                "TIMEOUT",
                "Analysis timed out",
                0.0,
                MODEL,
                Instant.now().toString()
        );
        resultFuture.complete(Collections.singleton(timeoutResult));
    }

    // Extracts the text from Claude's response, strips markdown fences, and parses the JSON verdict.
    private AnalysisResult parseResponse(FilterResult input, Message response) throws Exception {
        String responseText = response.content().stream()
                .flatMap(block -> block.text().stream())
                .map(textBlock -> textBlock.text())
                .findFirst()
                .orElseThrow(() -> new RuntimeException("No text content in response"));

        // Strip markdown code fences if present
        String jsonText = responseText.strip();
        if (jsonText.startsWith("```")) {
            int firstNewline = jsonText.indexOf('\n');
            int lastFence = jsonText.lastIndexOf("```");
            if (firstNewline != -1 && lastFence > firstNewline) {
                jsonText = jsonText.substring(firstNewline + 1, lastFence).strip();
            }
        }

        JsonNode json = objectMapper.readTree(jsonText);

        String verdict = json.get("verdict").asText();
        double confidence = json.get("confidence").asDouble();
        String reasoning = json.get("reasoning").asText();

        return new AnalysisResult(
                input.getTransaction().getTransactionId(),
                input,
                verdict,
                reasoning,
                confidence,
                MODEL,
                Instant.now().toString()
        );
    }
}
