package dev.lavalink.youtube.cipher;

import com.grack.nanojson.JsonWriter;
import com.sedmelluq.discord.lavaplayer.tools.DataFormatTools;
import com.sedmelluq.discord.lavaplayer.tools.JsonBrowser;
import com.sedmelluq.discord.lavaplayer.tools.io.HttpInterface;
import dev.lavalink.youtube.ExceptionWithResponseBody;
import dev.lavalink.youtube.http.YoutubeHttpContextFilter;
import dev.lavalink.youtube.track.format.StreamFormat;
import org.apache.http.HttpEntity;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.utils.URIBuilder;
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
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static com.sedmelluq.discord.lavaplayer.tools.ExceptionTools.throwWithDebugInfo;
/**
 * Handles parsing and caching of ciphers via a remote proxy
 */
public class RemoteCipherManager implements CipherManager {
    private static final Logger log = LoggerFactory.getLogger(RemoteCipherManager.class);

    private final @NotNull String remoteUrl;

    protected volatile CachedPlayerScript cachedPlayerScript;
    private final Map<String, String> cachedTimestamps = new ConcurrentHashMap<>();
 
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
        String signature = format.getSignature();
        String nParameter = format.getNParameter();
        URI initialUrl = format.getUrl();

        URIBuilder uri = new URIBuilder(initialUrl);

        if (!DataFormatTools.isNullOrEmpty(signature)) {
            return getUri(configureHttpInterface(httpInterface), format.getSignature(), format.getSignatureKey(), nParameter, initialUrl, playerScript);
        }

        uri.setParameter("n", decipherN(configureHttpInterface(httpInterface), nParameter, playerScript));
        try {
            return uri.build();
        } catch (URISyntaxException f) {
            throw new RuntimeException(f);
        }
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
        String cached = this.cachedTimestamps.get(sourceUrl);
        if (cached != null) {
            return cached;
        }

        log.debug("Timestamp from script {}", sourceUrl);
        String timestamp = getTimestampFromScript(configureHttpInterface(httpInterface), sourceUrl);
        this.cachedTimestamps.put(sourceUrl, timestamp);
        return timestamp;
    }

    private String getRemoteEndpoint(String path) {
        return remoteUrl.endsWith("/") ? remoteUrl + path : remoteUrl + "/" + path;
    }

    private String decipherN(HttpInterface httpInterface, String n, String playerScript) throws IOException {
        HttpPost request = new HttpPost(getRemoteEndpoint("decrypt_signature"));

        log.debug("Deciphering N param: {} with script: {}", n, playerScript);

        String requestBody = JsonWriter.string()
            .object()
            .value("player_url", playerScript)
            .value("n_param", n)
            .end()
            .done();
        request.setEntity(new StringEntity(requestBody, ContentType.APPLICATION_JSON));

        try (CloseableHttpResponse response = httpInterface.execute(request)) {
            int statusCode = response.getStatusLine().getStatusCode();
            HttpEntity entity = response.getEntity();
            String responseBody = (entity != null) ? EntityUtils.toString(entity, StandardCharsets.UTF_8) : null;

            if (statusCode >= 200 && statusCode < 300) {
                if (DataFormatTools.isNullOrEmpty(responseBody)) {
                    throw new IOException("Received empty successful response from decryption proxy.");
                }

                JsonBrowser json = JsonBrowser.parse(responseBody);
                String returnedN = json.get("decrypted_n_sig").text();

                log.debug("Received decrypted N: {}", returnedN);

                if (returnedN != null && !returnedN.isEmpty()) {
                    return returnedN;
                }

                return "";
            } else {
                throw new IOException("Decryption proxy request failed with status code: " + statusCode + ". Response: " + responseBody);
            }
        }
    }

    private URI getUri(HttpInterface httpInterface, String sig, String sigKey, String nParam, URI initial, String playerScript) throws IOException {
        HttpPost request = new HttpPost(getRemoteEndpoint("decrypt_signature"));

        log.debug("Deciphering N param: {} and Signature: {} with script: {}", nParam, sig, playerScript);

        String requestBody = JsonWriter.string()
            .object()
            .value("player_url", playerScript)
            .value("encrypted_signature", sig)
            .value("n_param", nParam)
            .value("signature_key", sigKey)
            .end()
            .done();
        request.setEntity(new StringEntity(requestBody, ContentType.APPLICATION_JSON));

        try (CloseableHttpResponse response = httpInterface.execute(request)) {
            int statusCode = response.getStatusLine().getStatusCode();
            HttpEntity entity = response.getEntity();
            String responseBody = (entity != null) ? EntityUtils.toString(entity, StandardCharsets.UTF_8) : null;

            if (statusCode >= 200 && statusCode < 300) {
                if (DataFormatTools.isNullOrEmpty(responseBody)) {
                    throw new IOException("Received empty successful response from decryption proxy.");
                }

                JsonBrowser json = JsonBrowser.parse(responseBody);

                String returnedSignature = json.get("decrypted_signature").text();
                String returnedN = json.get("decrypted_n_sig").text();

                log.debug("Received Decrypted N: {} and Decrypted Sig: {}", returnedN, returnedSignature);

                URIBuilder uriBuilder = new URIBuilder(initial);

                if (!DataFormatTools.isNullOrEmpty(returnedSignature)) {
                    if (sigKey == null || sigKey.trim().isEmpty()) {
                        log.error("Warning: Decrypted signature received, but sigKey is null or empty. Using default 'sig'.");
                        sigKey = "sig";
                    }
                    uriBuilder.setParameter(sigKey.trim(), returnedSignature);
                } else if (!DataFormatTools.isNullOrEmpty(sig)) {
                    log.warn("Warning: Original signature parameter 's' was present, but no decrypted signature returned from proxy.");
                }

                if (!DataFormatTools.isNullOrEmpty(returnedN)) {
                    uriBuilder.setParameter("n", returnedN);
                } else if (!DataFormatTools.isNullOrEmpty(nParam)) {
                    log.error("Warning: Original parameter 'n' was present, but no decrypted n-parameter returned from proxy.");
                }

                return uriBuilder.build();

            } else {
                throw new IOException("Decryption proxy request failed with status code: " + statusCode + ". Response: " + responseBody + " SIG: " + sig);
            }
        } catch (URISyntaxException e) {
            throw new RuntimeException(e);
        }
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
                    throw new IOException("Received empty successful response from decryption proxy.");
                }
                log.debug("Received response from proxy: {}", responseBody);

                JsonBrowser json = JsonBrowser.parse(responseBody);

                return json.get("sts").text();
            } else {
                throw new IOException("Decryption proxy request failed with status code: " + statusCode + ". Response: " + responseBody);
            }
        }
    }

    public HttpInterface configureHttpInterface(HttpInterface httpInterface) {
        httpInterface.getContext().setAttribute(YoutubeHttpContextFilter.ATTRIBUTE_CIPHER_REQUEST_SPECIFIED, true);
        return httpInterface;
    }
}

