package org.rogmann.llm.pluginllm01.clipb;

import com.intellij.openapi.ide.CopyPasteManager;

/**
 * This class contains a utility method to transfer text into the clipboard.
 */
public class ClipboardUtil {
    public static void copy(String text) {
        CopyPasteManager.getInstance().setContents(new TransferableString(text));
    }
}

