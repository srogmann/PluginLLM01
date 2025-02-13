package org.rogmann.llm.pluginllm01;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.NlsContexts;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Task to execute a request to a LLM in the background.
 */
public class LlmBackgroundableHttp extends Task.Backgroundable {
    /** logger */
    private static final Logger LOGGER = Logger.getInstance(LlmBackgroundableHttp.class);

    /** URL of chat/completions-endpoint */
    private final String sUrlChatCompletion = System.getProperty("pluginllm01.url", "http://localhost:7681/v1/chat/completions");
    /** URL of infill-endpoint */
    private final String sUrlInfill = System.getProperty("pluginllm01.url", "http://localhost:7681/infill");

    /** optional api-key */
    private final String sApiKey = System.getProperty("pluginllm01.key");

    /** Consumer to send output-stream */
    private final Consumer<String> responseStream;
    /** Consumer to send the current status */
    private final Consumer<String> outputStatus;

    /** result consumer */
    private final Consumer<String> resultConsumer;

    /** prompt */
    private final LlmTask llmTask;

    public LlmBackgroundableHttp(@Nullable Project project, @NlsContexts.ProgressTitle @NotNull String title,
                                 LlmTask llmTask,
                                 Consumer<String> responseStream,
                                 Consumer<String> outputStatus,
                                 Consumer<String> resultConsumer) {
        super(project, title, true);
        this.llmTask = llmTask;
        this.responseStream = responseStream;
        this.outputStatus = outputStatus;
        this.resultConsumer = resultConsumer;
    }

   @Override
    public void run(@NotNull ProgressIndicator indicator) {
        try {
            String sUrl = switch (llmTask.type()) {
                case PROMPT -> sUrlChatCompletion;
                case FILL_IN_MIDDLE -> sUrlInfill;
            };
            LOGGER.info("Connect to llm-server: " + sUrl);
            URL url = new URL(sUrl);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestProperty("Content-Type", "text/event-stream");
            conn.setRequestProperty("Cache-Control", "no-cache");
            if (sApiKey != null) {
                conn.setRequestProperty("Authorization", sApiKey);
            }

            Map<String, Object> request = new HashMap<>();
            if (llmTask.type() == LlmTaskType.PROMPT) {
                List<Object> messages = new ArrayList<>();
                request.put("stream", true);
                request.put("messages", messages);
                String systemPrompt = llmTask.systemPrompt();
                if (systemPrompt != null && !systemPrompt.isEmpty()) {
                    Map<String, Object> msg = new HashMap<>();
                    msg.put("role", "system");
                    msg.put("content", systemPrompt);
                    messages.add(msg);
                }
                Map<String, Object> msg = new HashMap<>();
                msg.put("role", "user");
                msg.put("content", llmTask.prompt());
                messages.add(msg);
            }
            else if (llmTask.type() == LlmTaskType.FILL_IN_MIDDLE) {
                request.put("input_prefix", llmTask.fimBegin());
                request.put("input_suffix", llmTask.fimEnd());
                if (llmTask.prompt() != null && !llmTask.prompt().isEmpty()) {
                    request.put("prompt", llmTask.prompt());
                }
                request.put("stream", true);
            }
            else {
                LOGGER.error("Unexpected llm-task type " + llmTask.type());
                outputStatus.accept("internal error: " + llmTask.type());
                throw new ProcessCanceledException();
            }
            StringBuilder sb = new StringBuilder();
            LightweightJsonHandler.dumpJson(sb, request);

            conn.setDoOutput(true);
            conn.setDoInput(true);

            try (OutputStream os = conn.getOutputStream()) {
                String jsonRequest = sb.toString();
                System.out.println("JSON-Request: " + jsonRequest);
                os.write(jsonRequest.getBytes(StandardCharsets.UTF_8));
            }

            int rc = conn.getResponseCode();
            if (rc != 200) {
                LOGGER.error(String.format("Server error: %d - %s ", rc, conn.getResponseMessage()));
                outputStatus.accept("server error: " + rc);
                throw new ProcessCanceledException();
            }

            try (InputStream is = conn.getInputStream();
                 InputStreamReader isr = new InputStreamReader(is, StandardCharsets.UTF_8);
                 BufferedReader br = new BufferedReader(isr)) {
                final StringBuilder sbResponse = new StringBuilder();
                while (true) {
                    if (indicator.isCanceled()) {
                        break;
                    }
                    String line = readLine(br, sb, indicator);
                    if (LOGGER.isDebugEnabled()) {
                        LOGGER.debug("Line: " + line.trim());
                    }
                    System.out.println("Line: " + line.trim());
                    if (indicator.isCanceled()) {
                        break;
                    }
                    if (line.startsWith(":")) {
                        LOGGER.info("Server-side comment: " + line);
                        continue;
                    }
                    if (line.startsWith("data: [DONE]")) {
                        break;
                    }
                    if (!line.startsWith("data: ")) {
                        LOGGER.error("Unexpected message-line: " + line.trim());
                        continue;
                    }
                    String json = line.substring(6).trim();
                    Map<String, Object> response;
                    try (Reader reader = new StringReader(json)) {
                        LightweightJsonHandler.readChar(reader, true, '{');
                        response = LightweightJsonHandler.parseJsonDict(reader);
                    }
                    String content = null;
                    if (llmTask.type() == LlmTaskType.PROMPT) {
                        List<Map<String, Object>> choices = LightweightJsonHandler.getJsonArrayDicts(response, "choices");
                        if (choices == null || choices.isEmpty()) {
                            LOGGER.error("Response without choices: " + json);
                            continue;
                        }
                        Map<String, Object> choice = choices.get(0);
                        Map<String, Object> delta = LightweightJsonHandler.getJsonValue(choice, "delta", Map.class);
                        content = LightweightJsonHandler.getJsonValue(delta, "content", String.class);
                    }
                    else if (llmTask.type() == LlmTaskType.FILL_IN_MIDDLE) {
                        // {"index":0,"content":"Hello","tokens":[9707],"stop":false,"id_slot":-1,"tokens_predicted":6,"tokens_evaluated":23}
                        // ...
                        content = LightweightJsonHandler.getJsonValue(response, "content", String.class);
                        List<Object> tokens = LightweightJsonHandler.getJsonArray(response, "tokens");
                        if ("".equals(content) && tokens != null && !tokens.isEmpty()
                                && Integer.valueOf(151644).equals(tokens.get(0))) {
                            // Workaround Qwen2.5-Coder and llama.cpp (2025-02): <|im_start|> instead of STOP.
                            LOGGER.warn("break because of <|im_start|>: " + response);
                            break;
                        }
                        Boolean stop = LightweightJsonHandler.getJsonValue(response, "stop", Boolean.class);
                        if (Boolean.TRUE.equals(stop)) {
                            // {"index":0,"content":"","tokens":[],"id_slot":0,"stop":true,"model":"gpt-3.5-turbo","tokens_predicted":216,"tokens_evaluated":23,"generation_settings":{"n_predict":-1,"seed":4294967295,"temperature":0.800000011920929,"dynatemp_range":0.0,"dynatemp_exponent":1.0,"top_k":40,"top_p":0.9499[...]
                            break;
                        }
                    }
                    if (content != null) {
                        responseStream.accept(content);
                        sbResponse.append(content);
                    }
                }
                this.resultConsumer.accept(sbResponse.toString());
            }
        }
        catch (IOException e) {
            LOGGER.error("IO-exception occured when communication with LLM-server", e);
            outputStatus.accept("IO-error: " + e);
            throw new ProcessCanceledException();
        }
    }

    /**
     * Reads a message in an event stream (with trailing LF in each line).
     * @param br event stream
     * @param sb temporary string-builder
     * @param indicator progress-indicator
     * @return message
     * @throws IOException in case of an IO-error
     */
    private String readLine(BufferedReader br, StringBuilder sb, @NotNull ProgressIndicator indicator) throws IOException{
        sb.setLength(0);
        while (true) {
            if (indicator.isCanceled()) {
                LOGGER.info("Request cancelled by user or local system");
                break;
            }
            int c = br.read();
            if (c == -1) {
                LOGGER.error("Unexpected end of stream: " + sb);
                outputStatus.accept("Unexpected end of server response");
                throw new ProcessCanceledException();
            }
            sb.append((char) c);
            if (c != '\n') {
                continue;
            }
            c = br.read();
            if (c == -1) {
                LOGGER.error("Unexpected end of stream: " + sb);
                outputStatus.accept("Unexpected end of server response");
                throw new ProcessCanceledException();
            }
            if (c != '\n') {
                sb.append((char) c);
                continue;
            }
            // LF LF: End of message.
            break;
        }
        return sb.toString();
    }

}
