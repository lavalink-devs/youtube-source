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
            printer.print(getMessage());

            Throwable cause = getCause();
            if (cause != null) {
                StackTraceElement[] stackTrace = cause.getStackTrace();
                int limit = Math.min(4, stackTrace.length);
                for (int i = 0; i < limit; i++) {
                    printer.println();
                    printer.format("\tat %s", stackTrace[i]);
                }
            }
        }
        return writer.toString();
    }
}