package dev.lavalink.youtube;

import dev.lavalink.youtube.clients.skeleton.Client;
import org.jetbrains.annotations.NotNull;

/**
 * Wraps an exception with client context
 */
public class ClientException extends Exception {
    public ClientException(@NotNull String message, @NotNull Client client, @NotNull Throwable cause) {
        super(String.format("Client [%s] failed: %s", client.getIdentifier(), message), cause);
        addSuppressed(ClientInformation.create(client));
    }
}