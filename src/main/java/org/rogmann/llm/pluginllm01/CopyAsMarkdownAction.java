package org.rogmann.llm.pluginllm01;

import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.PlatformIcons;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;

import java.awt.*;
import java.awt.datatransfer.StringSelection;
import java.awt.datatransfer.Transferable;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * An IntelliJ action that copies the selected files' contents as formatted Markdown to the system clipboard.
 * Each file is represented in a code block with syntax highlighting based on its extension. The action checks
 * for valid files, displays feedback messages, and ensures it is enabled only when applicable files are selected.
 */
public class CopyAsMarkdownAction extends AnAction {

    public CopyAsMarkdownAction() {
        super("Copy Classes as Markdown",
              "Copy selected files as markdown to clipboard",
              PlatformIcons.COPY_ICON); // Use an appropriate icon
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
        Project project = e.getProject();
        if (project == null) return;

        VirtualFile[] files = e.getData(CommonDataKeys.VIRTUAL_FILE_ARRAY);
        if (files == null || files.length == 0) {
            Messages.showInfoMessage(project, "No files selected", "Info");
            return;
        }

        StringBuilder markdown = new StringBuilder();

        for (VirtualFile file : files) {
            if (!file.exists()) continue; // Skip directories
            String content = getFileMarkdown(project, file);
            if (content != null) {
                markdown.append(content).append("\n\n");
            }
        }

        if (markdown.isEmpty()) {
            Messages.showInfoMessage(project, "No valid files selected", "Info");
        } else {
            copyToClipboard(markdown.toString());
            Messages.showInfoMessage(project, "Copied to clipboard", "Success");
        }
    }

    private String getFileMarkdown(Project project, VirtualFile file) {
        try {
            String fileName = file.getName();
            String extension = fileName.substring(fileName.lastIndexOf('.') + 1);

            // For Java files, extract class name using PSI
            //if ("java".equals(extension)) {
            //    PsiFile psiFile = PsiManager.getInstance(project).findFile(file);
            //    if (psiFile instanceof PsiJavaFile javaFile) {
            //        PsiClass[] classes = javaFile.getClasses();
            //        if (classes.length == 0) return null;
            //        String className = classes[0].getName();
            //        String source = new String(file.contentsToByteArray(), StandardCharsets.UTF_8);
            //        return "## " + className + "\n\n```java\n" + source + "\n```\n";
            //    }
            //*}

            // For non-Java files, read the entire file
            byte[] bytes = file.contentsToByteArray();
            String content = new String(bytes, StandardCharsets.UTF_8);
            return "## " + fileName + "\n\n```" + extension + "\n" + content + "\n```\n";
        } catch (IOException ex) {
            Messages.showErrorDialog(project, "Error reading file: " + file.getName(), "Error");
            return null;
        }
    }

    private void copyToClipboard(String text) {
        Transferable transferable = new StringSelection(text);
        UIUtil.invokeLaterIfNeeded(() -> {
            Toolkit.getDefaultToolkit().getSystemClipboard().setContents(transferable, null);
        });
    }

    @Override
    public void update(@NotNull AnActionEvent e) {
        VirtualFile[] files = e.getData(CommonDataKeys.VIRTUAL_FILE_ARRAY);
        e.getPresentation().setEnabledAndVisible(files != null && files.length > 0);
    }
}
