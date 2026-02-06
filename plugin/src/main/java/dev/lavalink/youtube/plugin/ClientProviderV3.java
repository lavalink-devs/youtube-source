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
        ANDROID_MUSIC(AndroidMusic::new),
        ANDROID_VR(AndroidVr::new),
        IOS(Ios::new),
        MUSIC(Music::new),
        TV(Tv::new),
        WEB(Web::new),
        WEBEMBEDDED(WebEmbedded::new),
        MWEB(MWeb::new),
        TVHTML5_SIMPLY(TvHtml5Simply::new);

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
