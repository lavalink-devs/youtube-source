package dev.lavalink.youtube;

import dev.lavalink.youtube.clients.skeleton.Client;
import dev.lavalink.youtube.polyfill.DetailMessageBuilder;

public class ClientInformation extends Exception {
    private ClientInformation(String message) {
        super(message, null, false, false);
    }

    public static ClientInformation create(Client client) {
        DetailMessageBuilder builder = new DetailMessageBuilder();
        builder.appendField("client.identifier", client.getIdentifier());
        builder.appendField("client.options", client.getOptions());
        return new ClientInformation(builder.toString());
    }
}
