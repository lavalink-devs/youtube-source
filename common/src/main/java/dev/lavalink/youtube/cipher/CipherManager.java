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
import java.nio.charset.StandardCharsets;
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
            String scriptUrl = null;
            String lastResponseText = null;

            // 1. Try embed page
            HttpGet embedRequest = new HttpGet("https://www.youtube.com/embed/");
            embedRequest.setHeader("Referer", "https://www.google.com");
            embedRequest.setHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0.0.0 Safari/537.36");
            try (CloseableHttpResponse response = httpInterface.execute(embedRequest)) {
                if (response.getStatusLine().getStatusCode() == 200 && response.getEntity() != null) {
                    lastResponseText = EntityUtils.toString(response.getEntity(), StandardCharsets.UTF_8);
                    scriptUrl = extractJsUrl(lastResponseText);
                }
            } catch (Exception ignored) {
            }

            // 2. Fallback to tv_config
            if (scriptUrl == null) {
                HttpGet tvRequest = new HttpGet("https://www.youtube.com/tv_config?action_get_config=true&client=lb4&theme=cl");
                tvRequest.setHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0.0.0 Safari/537.36");
                try (CloseableHttpResponse response = httpInterface.execute(tvRequest)) {
                    if (response.getStatusLine().getStatusCode() == 200 && response.getEntity() != null) {
                        lastResponseText = EntityUtils.toString(response.getEntity(), StandardCharsets.UTF_8);
                        scriptUrl = extractJsUrl(lastResponseText);
                    }
                } catch (Exception ignored) {
                }
            }

            // 3. Fallback to watch page
            if (scriptUrl == null) {
                HttpGet watchRequest = new HttpGet("https://www.youtube.com/watch?v=dQw4w9WgXcQ");
                watchRequest.setHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0.0.0 Safari/537.36");
                try (CloseableHttpResponse response = httpInterface.execute(watchRequest)) {
                    if (response.getStatusLine().getStatusCode() == 200 && response.getEntity() != null) {
                        lastResponseText = EntityUtils.toString(response.getEntity(), StandardCharsets.UTF_8);
                        scriptUrl = extractJsUrl(lastResponseText);
                    }
                } catch (Exception ignored) {
                }
            }

            if (scriptUrl == null) {
                throw new ExceptionWithResponseBody("no jsUrl found in embed or fallback endpoints", lastResponseText != null ? lastResponseText : "empty");
            }

            String fullUrl = scriptUrl.startsWith("http") ? scriptUrl : "https://www.youtube.com" + scriptUrl;
            fullUrl = fullUrl.replaceAll("/[a-z]{2}_[A-Z]{2}/", "/en_US/");

            try {
                return new CachedPlayerScript(fullUrl, getTimestamp(httpInterface, fullUrl));
            } catch (IOException e) {
                throw ExceptionTools.toRuntimeException(e);
            }
        }
    }

    static String extractJsUrl(String content) {
        if (content == null) {
            return null;
        }
        String jsUrl = DataFormatTools.extractBetween(content, "\"jsUrl\":\"", "\"");
        if (jsUrl != null) {
            return jsUrl;
        }
        java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("(/s/player/[^\"]+\\.js)").matcher(content);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return null;
    }

    class CachedPlayerScript {
        public final String url;
        public final String signatureTimestamp;
        public final long expireTimestampMs;

        public CachedPlayerScript(@NotNull String url, @NotNull String signatureTimestamp) {
            String fullUrl = url.startsWith("http") ? url : "https://www.youtube.com" + url;
            this.url = fullUrl.replaceAll("/[a-z]{2}_[A-Z]{2}/", "/en_US/");
            this.signatureTimestamp = signatureTimestamp;
            this.expireTimestampMs = System.currentTimeMillis() + TimeUnit.DAYS.toMillis(1);
        }
    }
}
