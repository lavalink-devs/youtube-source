import com.sedmelluq.discord.lavaplayer.player.DefaultAudioPlayerManager;
import com.sedmelluq.discord.lavaplayer.tools.FriendlyException;
import com.sedmelluq.discord.lavaplayer.tools.JsonBrowser;
import com.sedmelluq.discord.lavaplayer.tools.io.HttpInterface;
import com.sedmelluq.discord.lavaplayer.track.AudioItem;
import com.sedmelluq.discord.lavaplayer.track.AudioPlaylist;
import com.sedmelluq.discord.lavaplayer.track.AudioReference;
import dev.lavalink.youtube.YoutubeAudioSourceManager;
import java.io.IOException;
import dev.lavalink.youtube.clients.ClientConfig;
import dev.lavalink.youtube.clients.skeleton.MusicClient;
import dev.lavalink.youtube.clients.skeleton.NonMusicClient;
import dev.lavalink.youtube.track.format.TrackFormats;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * Verifies that a client whose search response yields no tracks (e.g. because the
 * response uses an unexpected layout) no longer terminates the client chain with
 * {@code AudioReference.NO_TRACK}, and instead lets the next client try.
 *
 * @see <a href="https://github.com/lavalink-devs/youtube-source/issues/250">issue #250</a>
 */
public class SearchClientFallThroughTest {
    // A search response whose layout has no recognizable track renderers (what the
    // IOS client currently returns: elementRenderer instead of compactVideoRenderer).
    private static final String UNRECOGNIZED_SEARCH_JSON =
        "{\"contents\":{\"sectionListRenderer\":{\"contents\":[{\"itemSectionRenderer\":{\"contents"
            + "\":[{\"elementRenderer\":{}}]}}]}}}";

    private static final String RECOGNIZED_SEARCH_JSON =
        "{\"contents\":{\"sectionListRenderer\":{\"contents\":[{\"itemSectionRenderer\":{\"contents"
            + "\":[{\"compactVideoRenderer\":{\"videoId\":\"vid001\",\"title\":{\"simpleText\":\"Test vid"
            + "eo\"},\"lengthText\":{\"simpleText\":\"3:33\"},\"longBylineText\":{\"runs\":[{\"text\":\"T"
            + "est author\"}]}}}]}}]}}}";

    // A music search response with no musicShelfRenderer at all.
    private static final String UNRECOGNIZED_MUSIC_SEARCH_JSON =
        "{\"contents\":{\"tabbedSearchResultsRenderer\":{\"tabs\":[{\"tabRenderer\":{\"content\":{\""
            + "sectionListRenderer\":{\"contents\":[]}}}}]}}}";

    private static final String RECOGNIZED_MUSIC_SEARCH_JSON =
        "{\"contents\":{\"tabbedSearchResultsRenderer\":{\"tabs\":[{\"tabRenderer\":{\"content\":{\""
            + "sectionListRenderer\":{\"contents\":[{\"musicShelfRenderer\":{\"contents\":[{\"musicRespon"
            + "siveListItemRenderer\":{\"flexColumns\":[{\"musicResponsiveListItemFlexColumnRenderer\":{\""
            + "text\":{\"runs\":[{\"text\":\"Music test video\",\"navigationEndpoint\":{\"watchEndpoint\""
            + ":{\"videoId\":\"mus001\"}}}]}}},{\"musicResponsiveListItemFlexColumnRenderer\":{\"text\":{"
            + "\"runs\":[{\"text\":\"Test author\"},{\"text\":\"3:33\"}]}}}]}}]}}]}}}}]}}}";

    static class UnrecognizedNonMusicClient extends NonMusicClient {
        int calls;

        @Override
        protected ClientConfig getBaseClientConfig(HttpInterface httpInterface) {
            return new ClientConfig();
        }

        @Override
        public String getIdentifier() {
            return "stub-unrecognized";
        }

        @Override
        public String getPlayerParams() {
            return null;
        }

        @Override
        public TrackFormats loadFormats(YoutubeAudioSourceManager source,
                                        HttpInterface httpInterface,
                                        String videoId) {
            throw new UnsupportedOperationException();
        }

        @Override
        protected JsonBrowser loadSearchResults(HttpInterface httpInterface, String searchQuery) {
            calls++;
            try {
                return JsonBrowser.parse(UNRECOGNIZED_SEARCH_JSON);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
    }

    static class RecognizedNonMusicClient extends NonMusicClient {
        int calls;

        @Override
        protected ClientConfig getBaseClientConfig(HttpInterface httpInterface) {
            return new ClientConfig();
        }

        @Override
        public String getIdentifier() {
            return "stub-recognized";
        }

        @Override
        public String getPlayerParams() {
            return null;
        }

        @Override
        public TrackFormats loadFormats(YoutubeAudioSourceManager source,
                                        HttpInterface httpInterface,
                                        String videoId) {
            throw new UnsupportedOperationException();
        }

        @Override
        protected JsonBrowser loadSearchResults(HttpInterface httpInterface, String searchQuery) {
            calls++;
            try {
                return JsonBrowser.parse(RECOGNIZED_SEARCH_JSON);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
    }

    static class UnrecognizedMusicClient extends MusicClient {
        int calls;

        @Override
        protected ClientConfig getBaseClientConfig(HttpInterface httpInterface) {
            return new ClientConfig();
        }

        @Override
        public String getIdentifier() {
            return "stub-music-unrecognized";
        }

        @Override
        public String getPlayerParams() {
            return null;
        }

        @Override
        protected JsonBrowser getMusicSearchResult(HttpInterface httpInterface, String searchQuery) {
            calls++;
            try {
                return JsonBrowser.parse(UNRECOGNIZED_MUSIC_SEARCH_JSON);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
    }

    static class RecognizedMusicClient extends MusicClient {
        int calls;

        @Override
        protected ClientConfig getBaseClientConfig(HttpInterface httpInterface) {
            return new ClientConfig();
        }

        @Override
        public String getIdentifier() {
            return "stub-music-recognized";
        }

        @Override
        public String getPlayerParams() {
            return null;
        }

        @Override
        protected JsonBrowser getMusicSearchResult(HttpInterface httpInterface, String searchQuery) {
            calls++;
            try {
                return JsonBrowser.parse(RECOGNIZED_MUSIC_SEARCH_JSON);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
    }

    @Test
    void secondClientIsTriedWhenFirstYieldsNoTracks() {
        UnrecognizedNonMusicClient iosLike = new UnrecognizedNonMusicClient();
        RecognizedNonMusicClient webLike = new RecognizedNonMusicClient();

        YoutubeAudioSourceManager source = new YoutubeAudioSourceManager(true, iosLike, webLike);
        AudioItem item = source.loadItem(new DefaultAudioPlayerManager(), new AudioReference("ytsearch:test query", null));
        Assertions.assertEquals(1, iosLike.calls);
        Assertions.assertEquals(1, webLike.calls);
        Assertions.assertTrue(item instanceof AudioPlaylist);
        Assertions.assertEquals(1, ((AudioPlaylist) item).getTracks().size());
        Assertions.assertEquals("vid001", ((AudioPlaylist) item).getTracks().get(0).getIdentifier());
    }

    @Test
    void secondMusicClientIsTriedWhenFirstYieldsNoTracks() {
        UnrecognizedMusicClient first = new UnrecognizedMusicClient();
        RecognizedMusicClient second = new RecognizedMusicClient();

        YoutubeAudioSourceManager source = new YoutubeAudioSourceManager(true, first, second);
        AudioItem item = source.loadItem(new DefaultAudioPlayerManager(), new AudioReference("ytmsearch:test query", null));
        Assertions.assertEquals(1, first.calls);
        Assertions.assertEquals(1, second.calls);
        Assertions.assertTrue(item instanceof AudioPlaylist);
        Assertions.assertEquals(1, ((AudioPlaylist) item).getTracks().size());
        Assertions.assertEquals("mus001", ((AudioPlaylist) item).getTracks().get(0).getIdentifier());
    }

    @Test
    void allClientsFailingYieldsAggregatedException() {
        UnrecognizedNonMusicClient first = new UnrecognizedNonMusicClient();
        UnrecognizedNonMusicClient second = new UnrecognizedNonMusicClient();

        YoutubeAudioSourceManager source = new YoutubeAudioSourceManager(true, first, second);

        FriendlyException exception = Assertions.assertThrows(FriendlyException.class,
            () -> source.loadItem(new DefaultAudioPlayerManager(), new AudioReference("ytsearch:test query", null)));
        Assertions.assertTrue(exception.getMessage().contains("All clients failed to load the item."));
        Assertions.assertEquals(1, first.calls);
        Assertions.assertEquals(1, second.calls);
    }
}
