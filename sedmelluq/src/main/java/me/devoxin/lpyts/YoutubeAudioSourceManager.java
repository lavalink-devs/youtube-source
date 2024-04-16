package me.devoxin.lpyts;

import com.sedmelluq.discord.lavaplayer.player.AudioPlayerManager;
import com.sedmelluq.discord.lavaplayer.source.AudioSourceManager;
import com.sedmelluq.discord.lavaplayer.tools.ExceptionTools;
import com.sedmelluq.discord.lavaplayer.tools.FriendlyException;
import com.sedmelluq.discord.lavaplayer.tools.FriendlyException.Severity;
import com.sedmelluq.discord.lavaplayer.tools.io.HttpClientTools;
import com.sedmelluq.discord.lavaplayer.tools.io.HttpInterface;
import com.sedmelluq.discord.lavaplayer.tools.io.HttpInterfaceManager;
import com.sedmelluq.discord.lavaplayer.track.AudioItem;
import com.sedmelluq.discord.lavaplayer.track.AudioReference;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import com.sedmelluq.discord.lavaplayer.track.AudioTrackInfo;
import me.devoxin.lpyts.UrlTools.UrlInfo;
import me.devoxin.lpyts.cipher.SignatureCipherManager;
import me.devoxin.lpyts.clients.Android;
import me.devoxin.lpyts.clients.Music;
import me.devoxin.lpyts.clients.TvHtml5Embedded;
import me.devoxin.lpyts.clients.Web;
import me.devoxin.lpyts.clients.skeleton.Client;
import me.devoxin.lpyts.http.YoutubeAccessTokenTracker;
import me.devoxin.lpyts.http.YoutubeHttpContextFilter;
import me.devoxin.lpyts.track.YoutubeAudioTrack;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.net.URI;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.sedmelluq.discord.lavaplayer.tools.FriendlyException.Severity.SUSPICIOUS;

@SuppressWarnings("RegExpUnnecessaryNonCapturingGroup")
public class YoutubeAudioSourceManager implements AudioSourceManager {
    // TODO: consider adding notnull/nullable annotations
    // TODO: connect timeout = 16000ms, read timeout = 8000ms (as observed from scraped youtube config)
    // TODO: look at possibly scraping jsUrl from WEB config to save a request
    // TODO: search providers use cookieless httpinterfacemanagers. should this do the same?
    // TODO(music): scrape config? it's identical to WEB.

    private static final Logger log = LoggerFactory.getLogger(YoutubeAudioSourceManager.class);
    public static final String SEARCH_PREFIX = "ytsearch:";
    public static final String MUSIC_SEARCH_PREFIX = "ytmsearch:";

    private static final String PROTOCOL_REGEX = "(?:http://|https://|)";
    private static final String DOMAIN_REGEX = "(?:www\\.|m\\.|music\\.|)youtube\\.com";
    private static final String SHORT_DOMAIN_REGEX = "(?:www\\.|)youtu\\.be";
    private static final String VIDEO_ID_REGEX = "(?<v>[a-zA-Z0-9_-]{11})";

    private static final Pattern directVideoIdPattern = Pattern.compile("^" + VIDEO_ID_REGEX + "$");
    private static final Pattern mainDomainPattern = Pattern.compile("^" + PROTOCOL_REGEX + DOMAIN_REGEX + "/.*");
    private static final Pattern shortHandPattern = Pattern.compile("^" + PROTOCOL_REGEX + "(?:" + DOMAIN_REGEX + "/(?:live|embed|shorts)|" + SHORT_DOMAIN_REGEX + ")/(?<id>.*)");


    protected HttpInterfaceManager httpInterfaceManager = HttpClientTools.createDefaultThreadLocalManager();
    protected boolean allowSearch;
    protected Client[] clients;

    protected SignatureCipherManager cipherManager;

    public YoutubeAudioSourceManager() {
        this(true);
    }

    public YoutubeAudioSourceManager(boolean allowSearch) {
        // query order: music -> web -> android -> tvhtml5embedded
        this(allowSearch, new Music(), new Web(), new Android(), new TvHtml5Embedded());
    }

    /**
     * Construct an instance of YoutubeAudioSourceManager with default settings
     * and the given clients.
     * @param clients The clients to use for track loading. They will be queried in
     *                the order they are provided.
     */
    public YoutubeAudioSourceManager(Client... clients) {
        this(true, clients);
    }

    /**
     * Construct an instance of YoutubeAudioSourceManager with the given settings
     * and clients.
     * @param allowSearch Whether to allow searching for tracks. If disabled, the
     *                    "ytsearch:" and "ytmsearch:" prefixes will return nothing.
     * @param clients The clients to use for track loading. They will be queried in
     *                the order they are provided.
     */
    public YoutubeAudioSourceManager(boolean allowSearch, Client... clients) {
        this.allowSearch = allowSearch;
        this.clients = clients == null ? new Client[0] : clients;
        this.cipherManager = new SignatureCipherManager();

        YoutubeAccessTokenTracker tokenTracker = new YoutubeAccessTokenTracker(httpInterfaceManager);
        YoutubeHttpContextFilter youtubeHttpContextFilter = new YoutubeHttpContextFilter();
        youtubeHttpContextFilter.setTokenTracker(tokenTracker);
        httpInterfaceManager.setHttpContextFilter(youtubeHttpContextFilter);
    }

    @Override
    public String getSourceName() {
        return "youtube";
    }

    public void setPlaylistPageCount(int count) {
        for (Client client : clients) {
            client.setPlaylistPageCount(count);
        }
    }

    @Override
    public AudioItem loadItem(AudioPlayerManager manager, AudioReference reference) {
        try {
            return loadItemOnce(reference);
        } catch (FriendlyException exception) {
            // In case of a connection reset exception, try once more.
            if (HttpClientTools.isRetriableNetworkException(exception.getCause())) {
                return loadItemOnce(reference);
            } else {
                throw exception;
            }
        }
    }

    public AudioItem loadItemOnce(AudioReference reference) {
        Throwable lastException = null;

        try (HttpInterface httpInterface = httpInterfaceManager.getInterface()) {
            Router router = getRouter(reference.identifier, httpInterface);

            if (router == null) {
                return null;
            }

            if (router == Router.none) {
                return AudioReference.NO_TRACK;
            }

            for (Client client : clients) {
                if (!client.canHandleRequest(reference.identifier)) {
                    continue;
                }

                log.debug("Attempting to load {} with client \"{}\"", reference.identifier, client.getIdentifier());

                try {
                    AudioItem item = router.route(client);

                    if (item != null) {
                        return item;
                    }
                } catch (CannotBeLoaded cbl) {
                    throw ExceptionTools.wrapUnfriendlyExceptions("This video cannot be loaded.", Severity.SUSPICIOUS, cbl.getCause());
                } catch (Throwable t) {
                    log.debug("Client \"{}\" threw a non-fatal exception, storing and proceeding...", client.getIdentifier(), t);
                    lastException = t;
                }
            }
        } catch (IOException e) {
            throw ExceptionTools.toRuntimeException(e);
        }

        if (lastException != null) {
            throw ExceptionTools.wrapUnfriendlyExceptions("This video cannot be loaded.", SUSPICIOUS, lastException);
        }

        return null;
    }

    protected Router getRouter(String identifier, HttpInterface httpInterface) {
        if (identifier.startsWith(SEARCH_PREFIX)) {
            if (allowSearch) return (client) -> client.loadSearch(this, httpInterface, identifier.substring(SEARCH_PREFIX.length()).trim());
        } else if (identifier.startsWith(MUSIC_SEARCH_PREFIX)) {
            if (allowSearch) return (client) -> client.loadSearchMusic(this, httpInterface, identifier.substring(MUSIC_SEARCH_PREFIX.length()).trim());
        } else {
            Matcher mainDomainMatcher = mainDomainPattern.matcher(identifier);

            if (mainDomainMatcher.matches()) {
                UrlInfo urlInfo = UrlTools.getUrlInfo(identifier, false);

                if ("/watch".equals(urlInfo.path)) {
                    String videoId = urlInfo.parameters.get("v");

                    if (videoId != null) return routeFromVideoId(videoId, urlInfo, httpInterface);
                } else if ("/playlist".equals(urlInfo.path)) {
                    String playlistId = urlInfo.parameters.get("list");

                    if (playlistId != null) return (client) -> client.loadPlaylist(this, httpInterface, playlistId, null);
                } else if ("/watch_videos".equals(urlInfo.path)) {
                    String videoIds = urlInfo.parameters.get("video_ids");

                    if (videoIds != null) {
                        try (CloseableHttpResponse response = httpInterface.execute(new HttpGet("https://www.youtube.com/watch_videos?video_ids=" + videoIds))) {
                            HttpClientTools.assertSuccessWithContent(response, "playlist response");
                            List<URI> redirects = httpInterface.getContext().getRedirectLocations();

                            if (redirects != null && !redirects.isEmpty()) {
                                return getRouter(redirects.get(0).toString(), httpInterface);
                            }

                            throw new FriendlyException("Unable to process youtube watch_videos link", SUSPICIOUS,
                                new IllegalStateException("Expected youtube to redirect watch_videos link to a watch?v={id}&list={list_id} link, but it did not redirect at all"));
                        } catch (Exception e) {
                            throw ExceptionTools.wrapUnfriendlyExceptions(e);
                        }
                    }
                }
            } else {
                Matcher shortHandMatcher = shortHandPattern.matcher(identifier);

                if (shortHandMatcher.matches()) {
                    return routeFromVideoId(shortHandMatcher.group("videoId"), null, httpInterface);
                }
            }
        }

        return null;
    }

    protected Router routeFromVideoId(String videoId, UrlInfo urlInfo, HttpInterface httpInterface) {
        String trimmedId = videoId.length() > 11 ? videoId.substring(0, 11) : videoId;

        if (!directVideoIdPattern.matcher(trimmedId).matches()) {
            return Router.none;
        } else if (urlInfo.parameters.containsKey("list")) {
            String playlistId = urlInfo.parameters.get("list");

            if (playlistId.startsWith("RD")) {
                return (client) -> client.loadMix(this, httpInterface, playlistId, trimmedId);
            }

            return (client) -> client.loadPlaylist(this, httpInterface, playlistId, trimmedId);
        }

        return (client) -> client.loadVideo(this, httpInterface, trimmedId);
    }

    public YoutubeAudioTrack buildAudioTrack(AudioTrackInfo trackInfo) {
        return new YoutubeAudioTrack(trackInfo, this);
    }

    public SignatureCipherManager getCipherManager() {
        return cipherManager;
    }

    /**
     * Returns a client by the given type, if registered.
     * @param cls The class of the client to return.
     * @return The client instance, or null if it's not registered.
     */
    public <T extends Client> T getClient(Class<T> cls) {
        for (Client client : clients) {
            if (cls.isAssignableFrom(client.getClass())) {
                return cls.cast(client);
            }
        }

        return null;
    }

    public Client[] getClients() {
        return clients;
    }

    public HttpInterfaceManager getHttpInterfaceManager() {
        return httpInterfaceManager;
    }

    public HttpInterface getInterface() {
        return httpInterfaceManager.getInterface();
    }

    @Override
    public boolean isTrackEncodable(AudioTrack track) {
        return true;
    }

    @Override
    public void encodeTrack(AudioTrack track, DataOutput output) {

    }

    @Override
    public AudioTrack decodeTrack(AudioTrackInfo trackInfo, DataInput input) {
        return new YoutubeAudioTrack(trackInfo, this);
    }

    @Override
    public void shutdown() {
        ExceptionTools.closeWithWarnings(httpInterfaceManager);
    }

    @FunctionalInterface
    protected interface Router {
        Router none = (unused) -> AudioReference.NO_TRACK;

        AudioItem route(Client client) throws CannotBeLoaded, IOException;
    }
}
