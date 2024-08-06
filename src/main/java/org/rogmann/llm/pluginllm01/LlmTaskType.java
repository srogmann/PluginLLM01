package org.rogmann.llm.pluginllm01;

/**
 * Type of LLM-task.
 */
public enum LlmTaskType {
    /** Single prompt */
    PROMPT("Prompt", 0x01),
    /** Fill in the middle (FIM) */
    FILL_IN_MIDDLE("Fill-in-Middle", 0x02);

    private final String title;
    private final byte id;

    LlmTaskType(String title, int id) {
        this.title = title;
        this.id = (byte) id;
    }

    /**
     * Gets id of the type to be serialized in a byte-array.
     * @return id of type
     */
    public byte getId() {
        return id;
    }

    public LlmTaskType lookupById(final byte id) {
        for (LlmTaskType type : values()) {
            if (type.id == id) {
                return type;
            }
        }
        throw new IllegalArgumentException("Unknown type-id " + id);
    }

    @Override
    public String toString() {
        return title;
    }
}
