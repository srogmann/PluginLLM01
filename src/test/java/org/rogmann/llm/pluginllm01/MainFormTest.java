package org.rogmann.llm.pluginllm01;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

public class MainFormTest {

    @Test
    void testTextWithoutThinkAndWithoutMarkdown() {
        String input = """
            Some simple text.
            """;
        String expected = input;
        String result = MainForm.extractMarkdown(input);
        assertEquals(expected, result);
    }

    @Test
    void testTextWithThinkAndWithoutMarkdown() {
        String input = """
            <think>
                Cogito ergo sum.
            </think>
            E = mc²
            """;
        String expected = """
            E = mc²
            """;
        String result = MainForm.extractMarkdown(input);
        assertEquals(expected, result);
    }

    @Test
    void testTextWithThinkAndWithMarkdown() {
        String input = """
            <think>
                Cogito ergo sum.
            </think>
            ```java
                public class Test {
                }
            ```
            Dieser Text nach dem Markdown soll ignoriert werden.
            """;
        String expected = """
                public class Test {
                }
            """;
        String result = MainForm.extractMarkdown(input);
        assertEquals(expected, result);
    }

    @Test
    void testTextWithoutThinkButWithMarkdown() {
        String input = """
            Markdown only:
            ```java
                public class Example {
                }
            ```
            Ignore me!
            """;
        String expected = """
                public class Example {
                }
            """;
        String result = MainForm.extractMarkdown(input);
        assertEquals(expected, result);
    }
}
