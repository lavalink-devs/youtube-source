package dev.lavalink.youtube.plugin;

import dev.lavalink.youtube.clients.*;
import dev.lavalink.youtube.clients.skeleton.Client;

public class ClientProviderV3 implements ClientProvider {
    @Override
    public Client[] getClients(String[] clients, OptionsProvider optionsProvider) {
        return getClients(ClientMapping.values(), clients, optionsProvider);
    }

    private enum ClientMapping implements ClientReference {
        ANDROID(Android::new),
        ANDROID_TESTSUITE(AndroidTestsuite::new),
        ANDROID_LITE(AndroidLite::new),
        IOS(Ios::new),
        MUSIC(Music::new),
        TVHTML5EMBEDDED(TvHtml5Embedded::new),
        WEB(Web::new),
        MEDIA_CONNECT(MediaConnect::new);

        private final ClientWithOptions<Client> clientFactory;

        ClientMapping(ClientWithOptions<Client> clientFactory) {
            this.clientFactory = clientFactory;
        }

        @Override
        public String getName() {
            return name();
        }

        @Override
        public Client getClient(ClientOptions options) {
            return clientFactory.create(options);
        }
    }
}
