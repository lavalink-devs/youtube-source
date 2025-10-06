package dev.lavalink.youtube.cipher;

import com.sedmelluq.discord.lavaplayer.tools.DataFormatTools;
import com.sedmelluq.discord.lavaplayer.tools.ExceptionTools;
import com.sedmelluq.discord.lavaplayer.tools.io.HttpClientTools;
import com.sedmelluq.discord.lavaplayer.tools.io.HttpInterface;
import dev.lavalink.youtube.ExceptionWithResponseBody;
import dev.lavalink.youtube.track.format.StreamFormat;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.util.EntityUtils;
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

    default CachedPlayerScript getPlayerScript(@NotNull HttpInterface httpInterface) {
        synchronized (this) {
            try (CloseableHttpResponse response = httpInterface.execute(new HttpGet("https://www.youtube.com/embed/"))) {
                HttpClientTools.assertSuccessWithContent(response, "fetch player script (embed)");

                String responseText = EntityUtils.toString(response.getEntity());
                String scriptUrl = DataFormatTools.extractBetween(responseText, "\"jsUrl\":\"", "\"");

                if (scriptUrl == null) {
                    throw new ExceptionWithResponseBody("no jsUrl found", responseText);
                }

                return new CachedPlayerScript(scriptUrl);
            } catch (IOException e) {
                throw ExceptionTools.toRuntimeException(e);
            }
        }
    }

    class CachedPlayerScript {
        public final String url;
        public final long expireTimestampMs;

        protected CachedPlayerScript(@NotNull String url) {
            this.url = url;
            this.expireTimestampMs = System.currentTimeMillis() + TimeUnit.DAYS.toMillis(1);
        }
    }
}
