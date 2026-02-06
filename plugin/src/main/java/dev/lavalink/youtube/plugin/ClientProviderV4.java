package dev.lavalink.youtube.plugin;

import dev.lavalink.youtube.clients.*;
import dev.lavalink.youtube.clients.skeleton.Client;

public class ClientProviderV4 implements ClientProvider {
    @Override
    public Client[] getClients(String[] clients, OptionsProvider optionsProvider) {
        return getClients(ClientMapping.values(), clients, optionsProvider);
    }

    private enum ClientMapping implements ClientReference {
        ANDROID(AndroidWithThumbnail::new),
        ANDROID_MUSIC(AndroidMusicWithThumbnail::new),
        ANDROID_VR(AndroidVrWithThumbnail::new),
        IOS(IosWithThumbnail::new),
        MUSIC(MusicWithThumbnail::new),
        TV(Tv::new), // This has no WithThumbnail companion as it's a playback-only client.
        WEB(WebWithThumbnail::new),
        WEBEMBEDDED(WebEmbeddedWithThumbnail::new),
        MWEB(MWebWithThumbnail::new),
        TVHTML5_SIMPLY(TvHtml5SimplyWithThumbnail::new);

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
