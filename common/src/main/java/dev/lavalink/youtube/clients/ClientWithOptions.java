package dev.lavalink.youtube.clients;

import dev.lavalink.youtube.clients.skeleton.Client;

@FunctionalInterface
public interface ClientWithOptions<T extends Client> {
    T create(ClientOptions options);
}
