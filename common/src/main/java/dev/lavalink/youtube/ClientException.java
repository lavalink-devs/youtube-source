package dev.lavalink.youtube;

import dev.lavalink.youtube.clients.skeleton.Client;
import org.jetbrains.annotations.NotNull;

import java.io.PrintWriter;
import java.io.StringWriter;

/**
 * Wraps an exception with client context
 */
public class ClientException extends RuntimeException {
    private final Client client;

    public ClientException(@NotNull String message, @NotNull Client client, @NotNull Throwable cause) {
        super(String.format("Client [%s] failed: %s", client.getIdentifier(), message), cause);
        this.client = client;
    }

    @NotNull
    public Client getClient() {
        return client;
    }

    @NotNull
    public String getFormattedMessage() {
        StringWriter writer = new StringWriter();
        try (PrintWriter printer = new PrintWriter(writer)) {
            printer.print(this.getMessage());
            writeException(printer, this.getCause(), 1);
        }
        return writer.toString();
    }

    // Safely write top stack trace elements to remain informative but concise
    private void writeException(@NotNull PrintWriter printer, @NotNull Throwable throwable, int maxDepth) {
        if (throwable == null) return;
        StackTraceElement[] stackTrace = throwable.getStackTrace();

        for (int i = 0; i < Math.min(2, stackTrace.length); i++) {
            printer.println();
            printer.format("\tat %s", stackTrace[i]);
        }

        if (throwable.getCause() != null && maxDepth > 0) {
            printer.println();
            printer.print("Caused by: " + throwable.getCause().getMessage());
            writeException(printer, throwable.getCause(), maxDepth - 1);
        }
    }
}