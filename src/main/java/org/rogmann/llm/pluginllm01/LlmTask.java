package org.rogmann.llm.pluginllm01;

public record LlmTask(LlmTaskType type, String systemPrompt, String prompt,
                      String fimBegin, String fimEnd) {
}
