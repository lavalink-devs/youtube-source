package dev.lavalink.youtube.plugin;

import java.util.ArrayList;
import java.util.List;

public class YoutubeOauthConfig {
    private boolean enabled = false;
    private final List<String> refreshTokens = new ArrayList<>();
    private boolean skipInitialization = false;

    public boolean getEnabled() {
        return enabled;
    }

    public String getRefreshToken() {
        return refreshTokens.isEmpty() ? null : refreshTokens.get(0);
    }

    public List<String> getRefreshTokens() {
        return refreshTokens;
    }

    public List<String> getTokens() {
        return new ArrayList<>(refreshTokens);
    }

    public boolean getSkipInitialization() {
        return skipInitialization;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public void setRefreshToken(Object refreshToken) {
        if (refreshToken instanceof java.util.Collection<?>) {
            for (Object item : (java.util.Collection<?>) refreshToken) {
                if (item != null) {
                    String str = item.toString().trim();
                    if (!str.isEmpty() && !refreshTokens.contains(str)) {
                        refreshTokens.add(str);
                    }
                }
            }
        } else if (refreshToken != null) {
            String str = refreshToken.toString().trim();
            if (!str.isEmpty() && !refreshTokens.contains(str)) {
                refreshTokens.add(str);
            }
        }
    }

    public void setRefreshTokens(List<String> tokens) {
        if (tokens != null) {
            for (String t : tokens) {
                if (t != null) {
                    String str = t.trim();
                    if (!str.isEmpty() && !refreshTokens.contains(str)) {
                        refreshTokens.add(str);
                    }
                }
            }
        }
    }

    public void setSkipInitialization(boolean skipInitialization) {
        this.skipInitialization = skipInitialization;
    }
}
