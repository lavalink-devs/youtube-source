package dev.lavalink.youtube;

import com.sedmelluq.discord.lavaplayer.player.AudioPlayerManager;
import com.sedmelluq.discord.lavaplayer.source.AudioSourceManager;
import com.sedmelluq.discord.lavaplayer.tools.DataFormatTools;
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
import dev.lavalink.youtube.UrlTools.UrlInfo;
import dev.lavalink.youtube.cipher.LocalSignatureCipherManager;
import dev.lavalink.youtube.cipher.RemoteCipherManager;
import dev.lavalink.youtube.cipher.CipherManager;
import dev.lavalink.youtube.clients.*;
import dev.lavalink.youtube.clients.skeleton.Client;
import dev.lavalink.youtube.http.YoutubeAccessTokenTracker;
import dev.lavalink.youtube.http.YoutubeHttpContextFilter;
import dev.lavalink.youtube.http.YoutubeOauth2Handler;
import dev.lavalink.youtube.track.YoutubeAudioTrack;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static com.sedmelluq.discord.lavaplayer.tools.FriendlyException.Severity.SUSPICIOUS;

@SuppressWarnings("RegExpUnnecessaryNonCapturingGroup")
public class YoutubeAudioSourceManager implements AudioSourceManager {
    // TODO: connect timeout = 16000ms, read timeout = 8000ms (as observed from scraped youtube config)
    // TODO: look at possibly scraping jsUrl from WEB config to save a request
    // TODO(music): scrape config? it's identical to WEB.

    private static final Logger log = LoggerFactory.getLogger(YoutubeAudioSourceManager.class);
    public static final String SEARCH_PREFIX = "ytsearch:";
    public static final String MUSIC_SEARCH_PREFIX = "ytmsearch:";

    public static final Client[] DEFAULT_CLIENTS = new Client[] {
        new Music(), new AndroidVr(), new Web(), new WebEmbedded()
    };

    private static boolean loggedOauthClientNoAccountWarning = false;

    private static final String PROTOCOL_REGEX = "(?:http://|https://|)";
    private static final String DOMAIN_REGEX = "(?:www\\.|m\\.|music\\.|)youtube\\.com";
    private static final String SHORT_DOMAIN_REGEX = "(?:www\\.|)youtu\\.be";
    private static final String VIDEO_ID_REGEX = "(?<v>[a-zA-Z0-9_-]{11})";
    private static final String PLAYLIST_ID_REGEX = "(?<list>(PL|UU)[a-zA-Z0-9_-]+)";

    private static final Pattern directVideoIdPattern = Pattern.compile("^" + VIDEO_ID_REGEX + "$");
    private static final Pattern directPlaylistIdPattern = Pattern.compile("^" + PLAYLIST_ID_REGEX + "$");
    private static final Pattern mainDomainPattern = Pattern.compile("^" + PROTOCOL_REGEX + DOMAIN_REGEX + "/.*");
    private static final Pattern shortHandPattern = Pattern.compile("^" + PROTOCOL_REGEX + "(?:" + DOMAIN_REGEX + "/(?:live|embed|shorts)|" + SHORT_DOMAIN_REGEX + ")/(?<videoId>.*)");

    protected final HttpInterfaceManager httpInterfaceManager;

    protected final boolean allowSearch;
    protected final boolean allowDirectVideoIds;
    protected final boolean allowDirectPlaylistIds;
    protected final Client[] clients;

    protected YoutubeOauth2Handler oauth2Handler;
    protected YoutubeHttpContextFilter contextFilter;
    protected CipherManager cipherManager;

    public YoutubeAudioSourceManager() {
        this(true);
    }

    public YoutubeAudioSourceManager(boolean allowSearch) {
        this(allowSearch, true, true);
    }

    public YoutubeAudioSourceManager(boolean allowSearch, boolean allowDirectVideoIds, boolean allowDirectPlaylistIds) {
        this(allowSearch, allowDirectVideoIds, allowDirectPlaylistIds, DEFAULT_CLIENTS);
    }

    /**
     * Construct an instance of YoutubeAudioSourceManager with default settings
     * and the given clients.
     * @param clients The clients to use for track loading. They will be queried in
     *                the order they are provided.
     */
    public YoutubeAudioSourceManager(@NotNull Client... clients) {
        this(true, true, true, clients);
    }

    /**
     * Construct an instance of YoutubeAudioSourceManager with the given settings
     * and clients.
     * @param allowSearch Whether to allow searching for tracks. If disabled, the
     *                    "ytsearch:" and "ytmsearch:" prefixes will return nothing.
     * @param clients The clients to use for track loading. They will be queried in
     *                the order they are provided.
     */
    public YoutubeAudioSourceManager(boolean allowSearch, @NotNull Client... clients) {
        this(allowSearch, true, true, clients);
    }

    /**
     * Construct an instance of YoutubeAudioSourceManager with the given settings
     * and clients.
     * @param allowSearch Whether to allow searching for tracks. If disabled, the
     *                    "ytsearch:" and "ytmsearch:" prefixes will return nothing.
     * @param allowDirectVideoIds Whether this source will attempt to load video identifiers
     *                            if they're provided without a complete URL (i.e. "dQw4w9WgXcQ")
     * @param allowDirectPlaylistIds Whether this source will attempt to load playlist identifiers
     *                               if they're provided without a complete URL.
     * @param clients The clients to use for track loading. They will be queried in
     *                the order they are provided.
     */
    public YoutubeAudioSourceManager(boolean allowSearch,
                                     boolean allowDirectVideoIds,
                                     boolean allowDirectPlaylistIds,
                                     @NotNull Client... clients) {
        this(
            new YoutubeSourceOptions()
                .setAllowSearch(allowSearch)
                .setAllowDirectVideoIds(allowDirectVideoIds)
                .setAllowDirectPlaylistIds(allowDirectPlaylistIds),
            clients
        );
    }

    public YoutubeAudioSourceManager(@NotNull YoutubeSourceOptions options,
                                     @NotNull Client... clients) {
        this.httpInterfaceManager = HttpClientTools.createCookielessThreadLocalManager();
        this.allowSearch = options.isAllowSearch();
        this.allowDirectVideoIds = options.isAllowDirectVideoIds();
        this.allowDirectPlaylistIds = options.isAllowDirectPlaylistIds();
        this.clients = clients;
        this.oauth2Handler = new YoutubeOauth2Handler(httpInterfaceManager);

        contextFilter = new YoutubeHttpContextFilter();
        contextFilter.setTokenTracker(new YoutubeAccessTokenTracker(httpInterfaceManager));
        contextFilter.setOauth2Handler(oauth2Handler);
        httpInterfaceManager.setHttpContextFilter(contextFilter);

        if (!DataFormatTools.isNullOrEmpty(options.getRemoteCipherUrl())) {
            contextFilter.setCipherConfig(options.getRemoteCipherPassword(), options.getRemoteCipherUserAgent(), YoutubeSource.VERSION);
            this.cipherManager = new RemoteCipherManager(options.getRemoteCipherUrl());
        } else {
            this.cipherManager = new LocalSignatureCipherManager();
        }
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

    /**
     * Instructs this source to use Oauth2 integration.
     * {@code null} is valid and will kickstart the oauth process.
     * Providing a refresh token will likely skip having to authenticate your account prior to making requests,
     * as long as the provided token is still valid.
     * @param refreshToken The token to use for generating access tokens. Can be null.
     * @param skipInitialization Whether linking of an account should be skipped, if you intend to provide a
     *                           refresh token later. This only applies on null/empty/invalid refresh tokens.
     *                           Valid refresh tokens will not be presented with an initialization prompt.
     */
    public void useOauth2(@Nullable String refreshToken, boolean skipInitialization) {
        oauth2Handler.setRefreshToken(refreshToken, skipInitialization);

        if (Arrays.stream(clients).noneMatch(Client::supportsOAuth)) {
            log.warn("OAuth has been enabled without registering any OAuth-compatible clients. " +
                "Please consult https://github.com/lavalink-devs/youtube-source?tab=readme-ov-file#available-clients for a list of " +
                "OAuth-compatible clients.");
        }
    }

    @Nullable
    public String getOauth2RefreshToken() {
        return oauth2Handler.getRefreshToken();
    }

    @Override
    @Nullable
    public AudioItem loadItem(@NotNull AudioPlayerManager manager, @NotNull AudioReference reference) {
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

    @Nullable
    protected AudioItem loadItemOnce(@NotNull AudioReference reference) {
        AudioItem item = null;
        List<ClientException> exceptions = new ArrayList<>();

        try (HttpInterface httpInterface = httpInterfaceManager.getInterface()) {
            Router router = getRouter(httpInterface, reference.identifier);

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

                boolean shouldLogOauthWarning = client.supportsOAuth() && !loggedOauthClientNoAccountWarning &&
                    !oauth2Handler.hasAccessToken() && client.getOptions().getPlayback();

                if (shouldLogOauthWarning) {
                    loggedOauthClientNoAccountWarning = true;
                    log.warn("!!! You are using an OAuth-enabled client without a valid OAuth token! This client may not play videos!");
                }

                log.debug("Attempting to load {} with client \"{}\"", reference.identifier, client.getIdentifier());
                httpInterface.getContext().setAttribute(Client.OAUTH_CLIENT_ATTRIBUTE, client.supportsOAuth());

                try {
                    item = router.route(client);
                } catch (CannotBeLoaded cbl) {
                    throw ExceptionTools.wrapUnfriendlyExceptions("This video cannot be loaded.", Severity.SUSPICIOUS, cbl.getCause());
                } catch (Throwable t) {
                    log.debug("Client \"{}\" threw a non-fatal exception, storing and proceeding...", client.getIdentifier(), t);
                    exceptions.add(new ClientException(t.getMessage(), client, t));
                }

                if (item != null) {
                    break;
                }
            }
        } catch (IOException e) {
            throw ExceptionTools.toRuntimeException(e);
        }

        if (!exceptions.isEmpty()) {
            if (item == null) {
                throw new AllClientsFailedException(exceptions);
            }

            String exceptionSummary = exceptions.stream().map(ClientException::getFormattedMessage).collect(Collectors.toList()).toString();

            log.debug("Exceptions suppressed whilst loading {}: {}", reference.identifier, exceptionSummary);
        }

        return item;
    }

    @Nullable
    protected Router getRouter(@NotNull HttpInterface httpInterface, @NotNull String identifier) {
        if (identifier.startsWith(SEARCH_PREFIX)) {
            if (allowSearch) {
                String trimmed = identifier.substring(SEARCH_PREFIX.length()).trim();

                if (trimmed.isEmpty()) {
                    return Router.none; // Equivalent to returning AudioReference.NO_TRACK.
                }

                return (client) -> client.loadSearch(this, httpInterface, identifier.substring(SEARCH_PREFIX.length()).trim());
            }
        } else if (identifier.startsWith(MUSIC_SEARCH_PREFIX)) {
            if (allowSearch) {
                String trimmed = identifier.substring(MUSIC_SEARCH_PREFIX.length()).trim();

                if (trimmed.isEmpty()) {
                    return Router.none; // Equivalent to returning AudioReference.NO_TRACK.
                }

                return (client) -> client.loadSearchMusic(this, httpInterface, identifier.substring(MUSIC_SEARCH_PREFIX.length()).trim());
            }
        } else {
            Matcher mainDomainMatcher = mainDomainPattern.matcher(identifier);

            if (mainDomainMatcher.matches()) {
                UrlInfo urlInfo = UrlTools.getUrlInfo(identifier, false);

                if ("/watch".equals(urlInfo.path)) {
                    String videoId = urlInfo.parameters.get("v");

                    if (videoId != null) return routeFromVideoId(httpInterface, videoId, urlInfo);
                } else if ("/playlist".equals(urlInfo.path)) {
                    String playlistId = urlInfo.parameters.get("list");

                    if (playlistId != null) {
                        if (playlistId.startsWith("RD")) { // mix handling
                            String videoId = playlistId.substring(2);
                            return (client) -> client.loadMix(this, httpInterface, playlistId, videoId);
                        }

                        return (client) -> client.loadPlaylist(this, httpInterface, playlistId, null);
                    }
                } else if ("/watch_videos".equals(urlInfo.path)) {
                    String videoIds = urlInfo.parameters.get("video_ids");

                    if (videoIds != null) {
                        try (CloseableHttpResponse response = httpInterface.execute(new HttpGet("https://www.youtube.com/watch_videos?video_ids=" + videoIds))) {
                            HttpClientTools.assertSuccessWithContent(response, "playlist response");
                            List<URI> redirects = httpInterface.getContext().getRedirectLocations();

                            if (redirects != null && !redirects.isEmpty()) {
                                return getRouter(httpInterface, redirects.get(0).toString());
                            }

                            throw new FriendlyException("Unable to process youtube watch_videos link", SUSPICIOUS,
                                new IllegalStateException("Expected youtube to redirect watch_videos link to a watch?v={id}&list={list_id} link, but it did not redirect at all"));
                        } catch (Exception e) {
                            throw ExceptionTools.wrapUnfriendlyExceptions(e);
                        }
                    }
                }
            }

            Matcher directVideoIdMatcher = directVideoIdPattern.matcher(identifier);

            if (allowDirectVideoIds && directVideoIdMatcher.matches()) {
                return routeFromVideoId(httpInterface, identifier, null);
            }

            Matcher playlistIdMatcher = directPlaylistIdPattern.matcher(identifier);

            if (allowDirectPlaylistIds && playlistIdMatcher.matches()) {
                return (client) -> client.loadPlaylist(this, httpInterface, identifier, null);
            }

            Matcher shortHandMatcher = shortHandPattern.matcher(identifier);

            if (shortHandMatcher.matches()) {
                return routeFromVideoId(httpInterface, shortHandMatcher.group("videoId"), null);
            }
        }

        return null;
    }

    @Nullable
    protected Router routeFromVideoId(@NotNull HttpInterface httpInterface,
                                      @NotNull String videoId,
                                      @Nullable UrlInfo urlInfo) {
        String trimmedId = videoId.length() > 11 ? videoId.substring(0, 11) : videoId;

        if (!directVideoIdPattern.matcher(trimmedId).matches()) {
            return Router.none;
        } else if (urlInfo != null && urlInfo.parameters.containsKey("list")) {
            String playlistId = urlInfo.parameters.get("list");

            if (playlistId.startsWith("RD")) {
                return (client) -> client.loadMix(this, httpInterface, playlistId, trimmedId);
            }

            if (!playlistId.startsWith("LL") && // Liked videos (requires logged-in user)
                !playlistId.startsWith("WL") && // Watch later (requires logged-in user)
                !playlistId.startsWith("LM")) { // Liked music (requires logged-in user)
                return (client) -> client.loadPlaylist(this, httpInterface, playlistId, trimmedId);
            }
        }

        return (client) -> client.loadVideo(this, httpInterface, trimmedId);
    }

    @NotNull
    public YoutubeAudioTrack buildAudioTrack(AudioTrackInfo trackInfo) {
        return new YoutubeAudioTrack(trackInfo, this);
    }

    @NotNull
    public CipherManager getCipherManager() {
        return cipherManager;
    }

    /**
     * Gets the cipher manager as a {@link RemoteCipherManager} instance, if applicable, otherwise null.
     * @return The cipher manager as a {@link RemoteCipherManager} instance or null.
     */
    @Nullable
    public RemoteCipherManager getRemoteCipherManager() {
        if (cipherManager instanceof RemoteCipherManager) {
            return (RemoteCipherManager) cipherManager;
        }

        return null;
    }

    public void setCipherManager(@NotNull CipherManager cipherManager) {
        this.cipherManager = cipherManager;
    }

    /**
     * Returns a client by the given type, if registered.
     * @param cls The class of the client to return.
     * @return The client instance, or null if it's not registered.
     */
    @Nullable
    public <T extends Client> T getClient(@NotNull Class<T> cls) {
        for (Client client : clients) {
            if (cls.isAssignableFrom(client.getClass())) {
                return cls.cast(client);
            }
        }

        return null;
    }

    @NotNull
    public Client[] getClients() {
        return clients;
    }

    @NotNull
    public YoutubeHttpContextFilter getContextFilter() {
        return contextFilter;
    }

    @NotNull
    public YoutubeOauth2Handler getOauth2Handler() {
        return oauth2Handler;
    }

    @NotNull
    public HttpInterfaceManager getHttpInterfaceManager() {
        return httpInterfaceManager;
    }

    @NotNull
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
    @NotNull
    public AudioTrack decodeTrack(@NotNull AudioTrackInfo trackInfo, @NotNull DataInput input) {
        return new YoutubeAudioTrack(trackInfo, this);
    }

    @Override
    public void shutdown() {
        ExceptionTools.closeWithWarnings(httpInterfaceManager);
    }

    @FunctionalInterface
    protected interface Router {
        Router none = (unused) -> AudioReference.NO_TRACK;

        @Nullable
        AudioItem route(@NotNull Client client) throws CannotBeLoaded, IOException;
    }
}
