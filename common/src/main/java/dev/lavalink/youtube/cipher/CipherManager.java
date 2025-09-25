package dev.lavalink.youtube.cipher;

import com.sedmelluq.discord.lavaplayer.tools.io.HttpInterface;
import dev.lavalink.youtube.track.format.StreamFormat;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.net.URI;
import java.util.concurrent.TimeUnit;

/**
 * Handles parsing and caching of signature ciphers
 */
public interface CipherManager {

    /**
     * Produces a valid playback URL for the specified track
     *
     * @param httpInterface HTTP interface to use
     * @param playerScript  Address of the script which is used to decipher signatures
     * @param format        The track for which to get the URL
     * @return Valid playback URL
     * @throws IOException On network IO error
     */
    @NotNull URI resolveFormatUrl(@NotNull HttpInterface httpInterface,
                                  @NotNull String playerScript,
                                  @NotNull StreamFormat format) throws IOException;

    CachedPlayerScript getCachedPlayerScript(@NotNull HttpInterface httpInterface);

    String getTimestamp(HttpInterface httpInterface, String sourceUrl) throws IOException;

    class CachedPlayerScript {
        public final String url;
        public final long expireTimestampMs;

        protected CachedPlayerScript(@NotNull String url) {
            this.url = url;
            this.expireTimestampMs = System.currentTimeMillis() + TimeUnit.DAYS.toMillis(1);
        }
    }


}
