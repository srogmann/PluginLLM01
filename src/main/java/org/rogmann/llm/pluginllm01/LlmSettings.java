package org.rogmann.llm.pluginllm01;

import com.intellij.openapi.components.*;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;


/**
 * Persistent settings component for the LLM Plugin.
 * <p>
 * Stores configuration data (server URL, default prompt) and handles its serialization/deserialization.
 * Uses IntelliJ's {@code @State} annotation to persist settings in {@code llm_settings.xml}.
 * <p>
 * Access instance via: {@code ApplicationManager.getApplication().getService(LlmSettings.class)}.
 *
 * @see com.intellij.openapi.components.PersistentStateComponent
 */
@Service
@State(
    name = "org.rogmann.llm.pluginllm01.LlmSettings",
    storages = @Storage("llm_settings.xml")
)
public final class LlmSettings implements PersistentStateComponent<Element> {

    private String serverUrl = "http://localhost:7681/";
    private String defaultPrompt = "Look at the following code and implement missing parts, add JavaDoc if it is missing.\n\n[Range]";

    @Override
    public void loadState(@NotNull Element state) {
        serverUrl = state.getAttributeValue("serverUrl");
        defaultPrompt = state.getAttributeValue("defaultPrompt");
    }

    @Override
    public @NotNull Element getState() {
        Element element = new Element("state");
        element.setAttribute("serverUrl", serverUrl);
        element.setAttribute("defaultPrompt", defaultPrompt);
        return element;
    }

    public String getServerUrl() {
        return serverUrl;
    }

    public void setServerUrl(String serverUrl) {
        this.serverUrl = serverUrl;
    }

    public String getDefaultPrompt() {
        return defaultPrompt;
    }

    public void setDefaultPrompt(String defaultPrompt) {
        this.defaultPrompt = defaultPrompt;
    }
}
