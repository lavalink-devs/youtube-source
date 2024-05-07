package dev.lavalink.youtube.plugin;

import dev.lavalink.youtube.clients.skeleton.Client;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public interface ClientProvider {
    Logger log = LoggerFactory.getLogger(ClientProvider.class);

    default String[] getDefaultClients() {
        // This is a default list of clients. This list matches that of the
        // YoutubeAudioSourceManager. If that is updated, this should probably be
        // updated too.
        return new String[] { "MUSIC", "WEB", "ANDROID", "TVHTML5EMBEDDED" };
    }

    Client[] getClients(String[] clients);

    default Client[] getClients(ClientReference[] clientValues, String[] clients) {
        List<Client> resolved = new ArrayList<>();

        for (String clientName : clients) {
            Client client = getClientByName(clientValues, clientName);

            if (client == null) {
                log.warn("Failed to resolve {} into a Client", clientName);
                continue;
            }

            resolved.add(client);
        }

        return resolved.toArray(new Client[0]);
    }

    interface ClientReference {
        String getName();
        Client getClient();
    }

    static Client getClientByName(ClientReference[] enumValues, String name) {
        return Arrays.stream(enumValues)
            .filter(it -> it.getName().equals(name))
            .findFirst()
            .map(ClientReference::getClient)
            .orElse(null);
    }
}
