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
            writeException(printer, this, 3);
        }
        return writer.toString();
    }

    // Recursively iterate down the causes to our stored exceptions
    private void writeException(@NotNull PrintWriter writer, @NotNull Throwable throwable, int maxDepth) {
        writer.print(throwable.getMessage());
        StackTraceElement[] stackTrace = throwable.getStackTrace();

        for (int i = 0; i < Math.min(5, stackTrace.length); i++) {
            writer.println();
            writer.format("\tat %s", stackTrace[i]);
        }

        if (throwable.getCause() != null && maxDepth > 0) {
            writer.println();
            writer.print("Caused by: ");
            writeException(writer, throwable.getCause(), maxDepth - 1);
        }
    }
}