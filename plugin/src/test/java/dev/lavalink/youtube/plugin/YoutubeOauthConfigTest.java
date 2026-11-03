package dev.lavalink.youtube.plugin;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

public class YoutubeOauthConfigTest {

    @Test
    public void testSingleRefreshTokenString() {
        YoutubeOauthConfig config = new YoutubeOauthConfig();
        config.setRefreshToken("token_single");

        Assertions.assertEquals("token_single", config.getRefreshToken());
        Assertions.assertEquals(List.of("token_single"), config.getTokens());
    }

    @Test
    public void testMultipleRefreshTokenList() {
        YoutubeOauthConfig config = new YoutubeOauthConfig();
        config.setRefreshToken(Arrays.asList("token_1", "token_2", "token_3"));

        Assertions.assertEquals("token_1", config.getRefreshToken());
        Assertions.assertEquals(Arrays.asList("token_1", "token_2", "token_3"), config.getTokens());
    }

    @Test
    public void testWhitespaceAndNullHandling() {
        YoutubeOauthConfig config = new YoutubeOauthConfig();
        config.setRefreshToken(Arrays.asList("  token_a  ", null, "", "token_b"));

        Assertions.assertEquals(Arrays.asList("token_a", "token_b"), config.getTokens());
    }
}
