package org.rogmann.llm.pluginllm01;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowFactory;
import org.jetbrains.annotations.NotNull;

public class LlmToolWindowFactory implements ToolWindowFactory {

    @Override
    public void createToolWindowContent(@NotNull Project project, @NotNull ToolWindow toolWindow) {
        var mainForm = new MainForm();
        var contentManager = toolWindow.getContentManager();
        var content = contentManager.getFactory().createContent(mainForm.getMainPanel(), "Local-LLM (0.2.3)", true);
        contentManager.addContent(content);
    }
}
