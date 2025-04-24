package org.rogmann.llm.pluginllm01;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.options.Configurable;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBTextField;
import com.intellij.ui.components.JBTextArea;
import javax.swing.*;
import java.awt.*;

/**
 * Configuration UI for the LLM Plugin settings.
 * <p>
 * Provides a form to edit the server URL and default prompt template.
 * Binds to {@link LlmSettings} to save/restore values.
 * Registered in {@code plugin.xml} under the "Tools" settings category.
 *
 * <p>See also: <a href="https://plugins.jetbrains.com/docs/intellij/settings-tutorial.html#creating-the-appsettings-implementation">settings tutorial</a></p>
 */
public class LlmSettingsConfigurable implements Configurable {

    private JPanel mainPanel;
    private JBTextField serverUrlField;
    private JBTextArea defaultPromptArea;

    @Override
    public String getDisplayName() {
        return "LLM Plugin Einstellungen";
    }

    @Override
    public String getHelpTopic() {
        return "llm-plugin-settings";
    }

    @Override
    public JComponent createComponent() {
        mainPanel = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 5, 5, 5);

        // URL-Eingabefeld
        gbc.gridx = 0;
        gbc.gridy = 0;
        mainPanel.add(new JBLabel("LLM server-URL:"), gbc);
        gbc.gridy = 1;
        serverUrlField = new JBTextField(30);
        mainPanel.add(serverUrlField, gbc);

        // Default-Prompt-Bereich
        gbc.gridy = 2;
        mainPanel.add(new JBLabel("Default prompt template:"), gbc);
        gbc.gridy = 3;
        defaultPromptArea = new JBTextArea(5, 30);
        JScrollPane scroll = new JScrollPane(defaultPromptArea);
        mainPanel.add(scroll, gbc);

        return mainPanel;
    }

    @Override
    public boolean isModified() {
        LlmSettings settings = ApplicationManager.getApplication().getService(LlmSettings.class);
        return !serverUrlField.getText().equals(settings.getServerUrl()) ||
                !defaultPromptArea.getText().equals(settings.getDefaultPrompt());
    }

    @Override
    public void apply() {
        LlmSettings settings = ApplicationManager.getApplication().getService(LlmSettings.class);
        settings.setServerUrl(serverUrlField.getText());
        settings.setDefaultPrompt(defaultPromptArea.getText());
    }

    @Override
    public void reset() {
        LlmSettings settings = ApplicationManager.getApplication().getService(LlmSettings.class);
        serverUrlField.setText(settings.getServerUrl());
        defaultPromptArea.setText(settings.getDefaultPrompt());
    }

    @Override
    public void disposeUIResources() {
        mainPanel = null;
    }
}
