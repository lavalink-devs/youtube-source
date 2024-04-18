package dev.lavalink.youtube;

import com.sedmelluq.discord.lavaplayer.tools.FriendlyException;
import org.apache.http.NameValuePair;
import org.apache.http.client.utils.URIBuilder;
import org.jetbrains.annotations.NotNull;

import java.net.URISyntaxException;
import java.util.Map;
import java.util.stream.Collectors;

import static com.sedmelluq.discord.lavaplayer.tools.FriendlyException.Severity.COMMON;

public class UrlTools {
    @NotNull
    public static UrlInfo getUrlInfo(@NotNull String url,
                                     boolean retryValidPart) {
        try {
            if (!url.startsWith("http://") && !url.startsWith("https://")) {
                url = "https://" + url;
            }

            URIBuilder builder = new URIBuilder(url);
            return new UrlInfo(builder.getPath(), builder.getQueryParams().stream()
                .filter(it -> it.getValue() != null)
                .collect(Collectors.toMap(NameValuePair::getName, NameValuePair::getValue, (a, b) -> a)));
        } catch (URISyntaxException e) {
            if (retryValidPart) {
                return getUrlInfo(url.substring(0, e.getIndex() - 1), false);
            } else {
                throw new FriendlyException("Not a valid URL: " + url, COMMON, e);
            }
        }
    }

    public static class UrlInfo {
        public final String path;
        public final Map<String, String> parameters;

        private UrlInfo(@NotNull String path,
                        @NotNull Map<String, String> parameters) {
            this.path = path;
            this.parameters = parameters;
        }
    }
}
