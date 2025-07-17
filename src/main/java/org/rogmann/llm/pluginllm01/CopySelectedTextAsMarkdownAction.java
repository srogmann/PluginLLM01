package org.rogmann.llm.pluginllm01;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.SelectionModel;
import com.intellij.openapi.ui.Messages;
import org.jetbrains.annotations.NotNull;
import org.rogmann.llm.pluginllm01.clipb.ClipboardUtil;

import java.util.regex.Pattern;

/**
 * An intellij action that copies the selected text into the clipboard in a markdown code-block.
 *
 * <p>This implementation assumes Java-code as default.</p>
 */
public class CopySelectedTextAsMarkdownAction extends AnAction {
    /** XML-hint */
    private static final Pattern P_XML = Pattern.compile("\\s*<[a-zA-Z].*", Pattern.DOTALL);

    @Override
    public void actionPerformed(@NotNull AnActionEvent ev) {
        Editor editor = ev.getRequiredData(CommonDataKeys.EDITOR);
        SelectionModel selectionModel = editor.getSelectionModel();
        String selectedText = selectionModel.getSelectedText();

        if (selectedText != null && !selectedText.isEmpty()) {
            String type = "java";
            if (P_XML.matcher(selectedText).matches()) {
                type = "xml";
            }

            String markdownText = String.format("```%s%n%s%n```%n", type, selectedText);
            ClipboardUtil.copy(markdownText);
        } else {
            Messages.showMessageDialog("No text selected.", "error", Messages.getErrorIcon());
        }
    }

    @Override
    public void update(@NotNull AnActionEvent ev) {
        Editor editor = ev.getData(CommonDataKeys.EDITOR);
        SelectionModel selectionModel = editor != null ? editor.getSelectionModel() : null;
        ev.getPresentation().setEnabledAndVisible(selectionModel != null && selectionModel.hasSelection());
    }
}
