package dev.lavalink.youtube;

import com.sedmelluq.discord.lavaplayer.tools.exception.DetailMessageBuilder;
import dev.lavalink.youtube.clients.skeleton.Client;

public class ClientInformation extends Exception {
    private ClientInformation(String message) {
        super(message, null, false, false);
    }

    public static ClientInformation create(Client client) {
        DetailMessageBuilder builder = new DetailMessageBuilder();
        builder.appendField("yts.version", YoutubeSource.VERSION);
        builder.appendField("client.identifier", client.getIdentifier());
        builder.appendField("client.options", client.getOptions());
        return new ClientInformation(builder.toString());
    }
}
