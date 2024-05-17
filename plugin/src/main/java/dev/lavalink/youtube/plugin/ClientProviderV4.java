package dev.lavalink.youtube.plugin;

import dev.lavalink.youtube.clients.*;
import dev.lavalink.youtube.clients.skeleton.Client;

import java.util.function.Supplier;

public class ClientProviderV4 implements ClientProvider {
    @Override
    public Client[] getClients(String[] clients) {
        return getClients(ClientMapping.values(), clients);
    }

    private enum ClientMapping implements ClientReference {
        ANDROID(AndroidWithThumbnail::new),
        ANDROID_TESTSUITE(AndroidTestsuiteWithThumbnail::new),
        ANDROID_LITE(AndroidLite::new),
        IOS(IosWithThumbnail::new),
        MUSIC(MusicWithThumbnail::new),
        TVHTML5EMBEDDED(TvHtml5EmbeddedWithThumbnail::new),
        WEB(WebWithThumbnail::new),
        MEDIA_CONNECT(MediaConnectWithThumbnail::new);

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
