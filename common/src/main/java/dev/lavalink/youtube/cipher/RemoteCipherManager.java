package dev.lavalink.youtube.cipher;

import com.grack.nanojson.JsonWriter;
import com.grack.nanojson.JsonStringWriter;
import com.sedmelluq.discord.lavaplayer.tools.DataFormatTools;
import com.sedmelluq.discord.lavaplayer.tools.JsonBrowser;
import com.sedmelluq.discord.lavaplayer.tools.io.HttpInterface;
import dev.lavalink.youtube.ExceptionWithResponseBody;
import dev.lavalink.youtube.http.YoutubeHttpContextFilter;
import dev.lavalink.youtube.track.format.StreamFormat;
import org.apache.http.HttpEntity;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.apache.http.util.EntityUtils;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;

import static com.sedmelluq.discord.lavaplayer.tools.ExceptionTools.throwWithDebugInfo;

/**
 * Handles parsing and caching of ciphers via a remote service
 */
public class RemoteCipherManager implements CipherManager {
    private static final Logger log = LoggerFactory.getLogger(RemoteCipherManager.class);

    private final @NotNull String remoteUrl;

    protected volatile CachedPlayerScript cachedPlayerScript;

    /**
     * Create a new remote cipher manager
     */
    public RemoteCipherManager(@NotNull String remoteUrl) {
        this.remoteUrl = remoteUrl;
    }

    @NotNull
    public String getRemoteUrl() {
        return remoteUrl;
    }


    /**
     * Produces a valid playback URL for the specified track
     *
     * @param httpInterface HTTP interface to use
     * @param playerScript  Address of the script which is used to decipher signatures
     * @param format        The track for which to get the URL
     * @return Valid playback URL
     * @throws IOException On network IO error
     */
    @NotNull
    public URI resolveFormatUrl(@NotNull HttpInterface httpInterface,
                                @NotNull String playerScript,
                                @NotNull StreamFormat format) throws IOException {
        return resolveUrl(
            configureHttpInterface(httpInterface),
            format.getUrl(),
            playerScript,
            format.getSignature(),
            format.getNParameter(),
            format.getSignatureKey()
        );
    }

    public CachedPlayerScript getCachedPlayerScript(@NotNull HttpInterface httpInterface) {
        if (cachedPlayerScript == null || System.currentTimeMillis() >= cachedPlayerScript.expireTimestampMs) {
            synchronized (this) {
                if (cachedPlayerScript == null || System.currentTimeMillis() >= cachedPlayerScript.expireTimestampMs) {
                    try {
                        return (cachedPlayerScript = getPlayerScript(httpInterface));
                    } catch (RuntimeException e) {
                        if (e instanceof ExceptionWithResponseBody) {
                            throw throwWithDebugInfo(log, null, e.getMessage(), "html", ((ExceptionWithResponseBody) e).getResponseBody());
                        }

                        throw e;
                    }
                }
            }
        }

        return cachedPlayerScript;
    }

    public String getTimestamp(HttpInterface httpInterface, String sourceUrl) throws IOException {
        synchronized (this) {
            log.debug("Timestamp from script {}", sourceUrl);
            return getTimestampFromScript(configureHttpInterface(httpInterface), sourceUrl);
        }
    }

    private String getRemoteEndpoint(String path) {
        return remoteUrl.endsWith("/") ? remoteUrl + path : remoteUrl + "/" + path;
    }

    private String getTimestampFromScript(HttpInterface httpInterface, String playerScript) throws IOException {
        HttpPost request = new HttpPost(getRemoteEndpoint("get_sts"));

        log.debug("Getting timestamp for script: {}", playerScript);

        String requestBody = JsonWriter.string()
            .object()
            .value("player_url", playerScript)
            .end()
            .done();
        request.setEntity(new StringEntity(requestBody, ContentType.APPLICATION_JSON));

        try (CloseableHttpResponse response = httpInterface.execute(request)) {
            int statusCode = response.getStatusLine().getStatusCode();
            HttpEntity entity = response.getEntity();
            String responseBody = (entity != null) ? EntityUtils.toString(entity, StandardCharsets.UTF_8) : null;

            if (statusCode >= 200 && statusCode < 300) {
                if (DataFormatTools.isNullOrEmpty(responseBody)) {
                    throw new IOException("Received empty successful response from remote cipher service.");
                }
                log.debug("Received response from remote cipher service: {}", responseBody);

                JsonBrowser json = JsonBrowser.parse(responseBody);

                return json.get("sts").text();
            } else {
                throw new IOException("Remote cipher service request failed with status code: " + statusCode + ". Response: " + responseBody);
            }
        }
    }

    public HttpInterface configureHttpInterface(HttpInterface httpInterface) {
        httpInterface.getContext().setAttribute(YoutubeHttpContextFilter.ATTRIBUTE_CIPHER_REQUEST_SPECIFIED, true);
        return httpInterface;
    }

    private URI resolveUrl(HttpInterface httpInterface,
                           URI baseUrl,
                           String playerScript,
                           String signature,
                           String nParam,
                           String sigKey) throws IOException {
        HttpPost request = new HttpPost(getRemoteEndpoint("resolve_url"));
        log.debug("Resolving stream url {} with player script {}", baseUrl, playerScript);

        JsonStringWriter writer = JsonWriter.string()
            .object()
            .value("stream_url", baseUrl.toString())
            .value("player_url", playerScript);

        if (signature != null) {
            writer.value("encrypted_signature", signature);
        }
        if (nParam != null) {
            writer.value("n_param", nParam);
        }
        if (sigKey != null) {
            writer.value("signature_key", sigKey);
        }

        String requestBody = writer.end().done();

        request.setEntity(new StringEntity(requestBody, ContentType.APPLICATION_JSON));

        try (CloseableHttpResponse response = httpInterface.execute(request)) {
            int statusCode = response.getStatusLine().getStatusCode();
            HttpEntity entity = response.getEntity();
            String responseBody = (entity != null) ? EntityUtils.toString(entity, StandardCharsets.UTF_8) : null;

            if (statusCode >= 200 && statusCode < 300) {
                if (DataFormatTools.isNullOrEmpty(responseBody)) {
                    throw new IOException("Received empty successful response from remote cipher service.");
                }

                JsonBrowser json = JsonBrowser.parse(responseBody);
                String resolvedUrl = json.get("resolved_url").text();

                if (resolvedUrl == null || resolvedUrl.isEmpty()) {
                    throw new IOException("Remote cipher service did not return a resolved URL.");
                }

                return new URI(resolvedUrl);
            } else {
                throw new IOException("Remote cipher service request to resolve URL failed with status code: " + statusCode + ". Response: " + responseBody);
            }
        } catch (URISyntaxException e) {
            throw new RuntimeException(e);
        }
    }
}

