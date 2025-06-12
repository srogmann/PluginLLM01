package org.rogmann.llm.pluginllm01;

import com.intellij.ide.DataManager;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.SelectionModel;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.project.Project;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MainForm extends JFrame {

    private final boolean legayProtocol = Boolean.getBoolean("pluginllm01.legacyProtocol");

    private JPanel panel;
    private JLabel lblType;
    private JComboBox<LlmTaskType> comboboxTyp;
    private JLabel lblSystemPrompt;
    private JLabel lblPrompt;
    private JTextArea txtPrompt;
    private JTextArea txtSystemPrompt;
    private JLabel lblStatus;
    private JTextField textStatus;
    private JLabel lblAusgabe;
    private JTextArea txtAusgabe;
    private JLabel lblAktionen;
    private JButton btnExecute;
    private JButton btnRange;
    private JButton btnStop;

    record SelectionRange(String range, int startOffset, int endOffset) { }

    private SelectionRange lastRange;

    public MainForm() {
        comboboxTyp.setModel(new DefaultComboBoxModel<>(LlmTaskType.values()));
        comboboxTyp.getModel().setSelectedItem(LlmTaskType.PROMPT);

        // Standard-Prompt aus den Einstellungen laden
        final LlmSettings llmSettings = ApplicationManager.getApplication().getService(LlmSettings.class);
        txtPrompt.setText(llmSettings.getDefaultPrompt());

        btnExecute.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent ev) {
                final DataManager dataManager = DataManager.getInstance();
                final DataContext dataContext = dataManager.getDataContext();
                final Project project = dataContext.getData(PlatformDataKeys.PROJECT);
                FileEditorManager fem = FileEditorManager.getInstance(project);
                Editor editor = fem.getSelectedTextEditor();
                final Document doc = editor.getDocument();

                Consumer<String> tokenConsumer = token -> { };
                Consumer<String> statusConsumer = status ->
                    ApplicationManager.getApplication().invokeLater(() ->
                       textStatus.setText(status)
                    );
                int offsetCaret = editor.getCaretModel().getOffset();
                final LlmTask llmTask = buildPrompt((LlmTaskType) comboboxTyp.getModel().getSelectedItem(), offsetCaret);
                Consumer<String> resultConsumer = response -> {
                    ApplicationManager.getApplication().invokeLater(() -> {
                        WriteCommandAction.runWriteCommandAction(project, () -> {
                            SelectionModel selectionModel = editor.getSelectionModel();
                            String text = extractMarkdown(response);
                            if (selectionModel.hasSelection()) {
                                doc.replaceString(selectionModel.getSelectionStart(), selectionModel.getSelectionEnd(), text);
                            } else {
                                doc.insertString(editor.getCaretModel().getOffset(), text);
                            }
                        });
                    });
                };
                if (legayProtocol) {
                    LlmBackgroundable task = new LlmBackgroundable(project, "LLM-Execution", llmTask,
                            tokenConsumer, statusConsumer, resultConsumer);
                    task.setCancelText("Stop LLM Execution").queue();
                } else {
                    LlmBackgroundableHttp task = new LlmBackgroundableHttp(project, "LLM-Execution", llmTask,
                            tokenConsumer, statusConsumer, resultConsumer);
                    task.setCancelText("Stop LLM Execution").queue();
                }
            }
        });
        btnRange.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent ev) {
                final DataManager dataManager = DataManager.getInstance();
                final DataContext dataContext = dataManager.getDataContext();
                final Project project = dataContext.getData(PlatformDataKeys.PROJECT);
                FileEditorManager fem = FileEditorManager.getInstance(project);
                Editor editor = fem.getSelectedTextEditor();
                SelectionModel selectionModel = editor.getSelectionModel();

                String selText = selectionModel.getSelectedText(true).trim();
                System.out.println("Sel Text: " + selText);
                lastRange = new SelectionRange(selText,
                        selectionModel.getSelectionStart(), selectionModel.getSelectionEnd());
                txtAusgabe.setText(selText);
            }
        });
    }

    /**
     * This method removes a &lt;think&gt;...&lt;/think&gt;-block at the begin
     * of the response and extracts the content of the first markdown-block (if available).
     * @param response response
     * @return response without thinking, content of first markdown if present
     */
    static String extractMarkdown(String response) {
        String text = response;
        Pattern pThink = Pattern.compile("<think>.*?</think>\\s*", Pattern.DOTALL);
        Matcher mThink = pThink.matcher(response);
        if (mThink.find()) {
            text = mThink.replaceFirst("");
        }
        Pattern pMarkdown = Pattern.compile("```[a-z0-9]+\r?\n(.*?\r?\n)```", Pattern.DOTALL);
        Matcher mMarkdown = pMarkdown.matcher(response);
        if (mMarkdown.find()) {
            text = mMarkdown.group(1);
        }
        return text;
    }

    private LlmTask buildPrompt(LlmTaskType llmTaskType, int offsetCaret) {
        String systemPrompt = txtSystemPrompt.getText().trim();
        String promptTemplate = txtPrompt.getText();
        return switch (llmTaskType) {
            case PROMPT -> {
                String prompt = promptTemplate;
                if (lastRange != null) {
                    prompt = prompt.replace("[Range]", this.lastRange.range());
                }
                yield new LlmTask(llmTaskType,
                        systemPrompt, prompt, null, null);
            }
            case FILL_IN_MIDDLE -> {
                if (lastRange == null) {
                    throw new IllegalStateException("Fill-in-Middle needs a marked range");
                }
                int offsetInRange = offsetCaret - lastRange.startOffset;
                if (offsetInRange > lastRange.range().length()) {
                    throw new IllegalStateException(String.format("Caret-offset %d not in previous range [%d, %d]",
                            offsetCaret, lastRange.startOffset, lastRange.endOffset));
                }
                String fimBegin = lastRange.range.substring(0, offsetInRange);
                String fimEnd = lastRange.range.substring(offsetInRange);
                String prompt = null;
                if (promptTemplate.contains("[FIM]")) {
                    fimBegin = promptTemplate.replace("[FIM]", fimBegin);
                } else {
                    String promptSuggestion = promptTemplate.replace("[Range]", "").trim();
                    if (!promptSuggestion.isEmpty()) {
                        prompt = promptSuggestion;
                    }
                }
                yield  new LlmTask(llmTaskType,
                        systemPrompt, prompt, fimBegin, fimEnd);
            }
        };
    }

    public JPanel getMainPanel() {
        return panel;
    }
}
