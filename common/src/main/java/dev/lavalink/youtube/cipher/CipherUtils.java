package dev.lavalink.youtube.cipher;

import org.jetbrains.annotations.NotNull;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class CipherUtils {

    private CipherUtils() {
    }

    static URI parseTokenScriptUrl(@NotNull String urlString) {
        try {
            if (urlString.startsWith("//")) {
                return new URI("https:" + urlString);
            } else if (urlString.startsWith("/")) {
                return new URI("https://www.youtube.com" + urlString);
            } else {
                return new URI(urlString);
            }
        } catch (URISyntaxException e) {
            throw new RuntimeException(e);
        }
    }

    static String extractDollarEscapedFirstGroup(@NotNull Pattern pattern, @NotNull String text) {
        Matcher matcher = pattern.matcher(text);
        return matcher.find() ? matcher.group(1).replace("$", "\\$") : null;
    }
}
