package org.rogmann.llm.pluginllm01;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.NlsContexts;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.net.SocketFactory;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketAddress;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.util.function.Consumer;

/**
 * Task to execute a request to a LLM in the background.
 */
public class LlmBackgroundable extends Task.Backgroundable {
    /** logger */
    private static final Logger LOGGER = Logger.getInstance(LlmBackgroundable.class);

    /** Typ of a chunk in TCP/IP message */
    enum ChunkType {
        BEGIN_OF_REQUEST(0x01),
        END_OF_REQUEST(0x02),
        CLOSE_CONNECTION(0x03),
        SYSTEM_PROMPT(0x04),
        PROMPT(0x05),
        FIM_BEFORE(0x06),
        FIM_AFTER(0x07);

        private final byte id;

        ChunkType(int id) {
            this.id = (byte) id;
        }

        public byte getId() {
            return id;
        }
    }
    /** Consumer to send output-stream */
    private final Consumer<String> responseStream;
    /** Consumer to send the current status */
    private final Consumer<String> outputStatus;

    /** result consumer */
    private final Consumer<String> resultConsumer;

    /** prompt */
    private final LlmTask llmTask;

    /** input buffer */
    private final byte[] bufIn = new byte[256];

    public LlmBackgroundable(@Nullable Project project, @NlsContexts.ProgressTitle @NotNull String title,
                             LlmTask llmTask,
                             Consumer<String> responseStream,
                             Consumer<String> outputStatus,
                             Consumer<String> resultConsumer) {
        super(project, title, true);
        this.llmTask = llmTask;
        this.responseStream = responseStream;
        this.outputStatus = outputStatus;
        this.resultConsumer = resultConsumer;
    }

    @Override
    public void run(@NotNull ProgressIndicator indicator) {
        try {
            SocketFactory factory = SocketFactory.getDefault();
            try (Socket socket = factory.createSocket()) {
                socket.setSoTimeout(3000);
                final SocketAddress endpoint = new InetSocketAddress("localhost", 8089);
                LOGGER.info("Connect to llm-server: " + endpoint);
                socket.connect(endpoint, 3000);

                try (OutputStream os = socket.getOutputStream();
                    BufferedOutputStream bos = new BufferedOutputStream(os)) {

                    byte[] buf = new byte[6];
                    // Eye-catcher, begin of request and type of LLM-task.
                    buf[0] = 'L';
                    buf[1] = 'L';
                    buf[2] = 'M';
                    buf[3] = '1';
                    buf[4] = ChunkType.BEGIN_OF_REQUEST.getId();
                    buf[5] = llmTask.type().getId();
                    bos.write(buf);

                    writeString(bos, ChunkType.SYSTEM_PROMPT, llmTask.systemPrompt());
                    if (llmTask.type() == LlmTaskType.PROMPT) {
                        writeString(bos, ChunkType.PROMPT, llmTask.prompt());
                        LOGGER.info("Sent prompt of length " + llmTask.prompt().length());
                    }
                    else if (llmTask.type() == LlmTaskType.FILL_IN_MIDDLE) {
                        writeString(bos, ChunkType.FIM_BEFORE, llmTask.fimBegin());
                        writeString(bos, ChunkType.FIM_AFTER, llmTask.fimEnd());
                        LOGGER.info(String.format("Sent FIM of lengths %d and %d",
                                llmTask.fimBegin().length(), llmTask.fimEnd().length()));
                    }

                    bos.write(ChunkType.END_OF_REQUEST.getId());
                    bos.flush();
                    final StringBuilder sbResponse = new StringBuilder();
                    try (InputStream is = socket.getInputStream()) {
                        readBytes(is, 4, indicator);
                        final String eyecatcher = new String(bufIn, 0, 4, StandardCharsets.ISO_8859_1);
                        if (!"LLM1".equals(eyecatcher)) {
                            throw new IOException("Invalid eyecatcher: " + eyecatcher);
                        }
                        while (true) {
                            readBytes(is, 1, indicator);
                            final int tokenLen = bufIn[0] & 0xff;
                            if (tokenLen == 0) {
                                break;
                            }
                            readBytes(is, tokenLen, indicator);
                            final String token = new String(bufIn, 0, tokenLen, StandardCharsets.UTF_8);
                            responseStream.accept(token);
                            LOGGER.debug("Got token: " + token);
                            sbResponse.append(token);
                            final int respLen = sbResponse.length();
                            indicator.setText(String.format("#len=%d [...%s]",
                                    respLen, sbResponse.subSequence(Math.max(0, respLen - 32), respLen)));
                        }

                        // Close the connection.
                        bos.write(ChunkType.CLOSE_CONNECTION.getId());
                        bos.flush();
                    }
                    resultConsumer.accept(sbResponse.toString());
                }
            }
        }
        catch (IOException e) {
            LOGGER.error("IO-exception occured when communication with LLM-server", e);
            outputStatus.accept("IO-error: " + e);
            throw new ProcessCanceledException();
        }
    }

    static void writeString(BufferedOutputStream bos, ChunkType chunkType, String s) throws IOException {
        final byte[] bufText = (s != null) ? s.getBytes(StandardCharsets.UTF_8) : new byte[0];
        final byte[] buf = new byte[5];
        //`Writes a chunk header with length and data to a byte output stream, where length is encoded in big-endian byte order.`
        final int len = bufText.length;
        buf[0] = chunkType.getId();
        buf[1] = (byte) (len >> 24);
        buf[2] = (byte) (len >> 16);
        buf[3] = (byte) (len >> 8);
        buf[4] = (byte) len;
        bos.write(buf);
        bos.write(bufText);
    }

    /**
     * Read bytes into the input-buffer.
     * @param is input-stream
     * @param n number of bytes to be read
     * @param indicator indicator to check for cancel
     * @throws IOException in case of an IO-error or cancellation
     */
    private void readBytes(InputStream is, int n, ProgressIndicator indicator) throws IOException {
        int offset = 0;
        while (offset < n) {
            if (indicator.isCanceled()) {
                throw new IOException(String.format("Cancel while reading %d %s (offset = %d)",
                        n, (n == 1) ? "byte" : "bytes", offset));
            }
            final int len;
            try {
                len = is.read(bufIn, offset, n - offset);
            } catch (SocketTimeoutException e) {
                // We wait for the next character (but check for cancellation).
                continue;
            }
            if (len == -1) {
                break;
            }
            offset += len;
        }
        if (offset < n) {
            throw new IOException(String.format("Read %d of %d %s only",
                    offset, n, (n == 1) ? "byte" : "bytes"));
        }
    }
}
