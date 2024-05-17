package dev.lavalink.youtube.plugin;

import dev.lavalink.youtube.clients.*;
import dev.lavalink.youtube.clients.skeleton.Client;

import java.util.function.Supplier;

public class ClientProviderV3 implements ClientProvider {
    @Override
    public Client[] getClients(String[] clients) {
        return getClients(ClientMapping.values(), clients);
    }

    private enum ClientMapping implements ClientReference {
        ANDROID(Android::new),
        ANDROID_TESTSUITE(AndroidTestsuite::new),
        IOS(Ios::new),
        MUSIC(Music::new),
        TVHTML5EMBEDDED(TvHtml5Embedded::new),
        WEB(Web::new),
        MEDIA_CONNECT(MediaConnect::new);

        private final Supplier<Client> clientFactory;

        ClientMapping(Supplier<Client> clientFactory) {
            this.clientFactory = clientFactory;
        }

        @Override
        public String getName() {
            return name();
        }

        @Override
        public Client getClient() {
            return clientFactory.get();
        }
    }
}
