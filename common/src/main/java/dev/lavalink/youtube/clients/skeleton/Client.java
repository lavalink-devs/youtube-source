package dev.lavalink.youtube.clients.skeleton;

import com.sedmelluq.discord.lavaplayer.tools.FriendlyException;
import com.sedmelluq.discord.lavaplayer.tools.JsonBrowser;
import com.sedmelluq.discord.lavaplayer.tools.io.HttpInterface;
import com.sedmelluq.discord.lavaplayer.track.AudioItem;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import com.sedmelluq.discord.lavaplayer.track.AudioTrackInfo;
import dev.lavalink.youtube.CannotBeLoaded;
import dev.lavalink.youtube.YoutubeAudioSourceManager;
import dev.lavalink.youtube.clients.ClientOptions;
import dev.lavalink.youtube.track.format.TrackFormats;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.List;
import java.util.stream.Collectors;

import static com.sedmelluq.discord.lavaplayer.tools.FriendlyException.Severity.COMMON;
import static com.sedmelluq.discord.lavaplayer.tools.FriendlyException.Severity.SUSPICIOUS;

/**
 * The interface for a Client.
 */
public interface Client {
    String WATCH_URL = "https://www.youtube.com/watch?v=";
    String API_BASE_URL = "https://youtubei.googleapis.com/youtubei/v1";
    String PLAYER_URL = API_BASE_URL + "/player";
    String SEARCH_URL = API_BASE_URL + "/search";
    String NEXT_URL = API_BASE_URL + "/next";
    String BROWSE_URL = API_BASE_URL + "/browse";

    String MUSIC_API_BASE_URL = "https://music.youtube.com/youtubei/v1";
    String MUSIC_SEARCH_URL = MUSIC_API_BASE_URL + "/search";

    // Should be videos only, whilst also bypassing YouTube's filter
    // for queries that trigger the suicide/self-harm warning.
    String SEARCH_PARAMS = "EgIQAfABAQ==";
    String MUSIC_SEARCH_PARAMS = "Eg-KAQwIARAAGAAgACgAMABqChADEAQQCRAFEAo=";

    @NotNull
    default PlayabilityStatus getPlayabilityStatus(@NotNull JsonBrowser playabilityStatus,
                                                   boolean throwOnNotOk) throws CannotBeLoaded {
        String status = playabilityStatus.get("status").text();

        if (playabilityStatus.isNull() || status == null) {
            throw new RuntimeException("No playability status block.");
        }

        switch (status) {
            case "OK":
                return PlayabilityStatus.OK;
            case "ERROR":
                String reason = playabilityStatus.get("reason").text();

//                if (reason.contains("This video is unavailable")) {
//                    throw new CannotBeLoaded(new FriendlyException(reason, COMMON, null));
//                }

                throw new FriendlyException(reason, COMMON, null);
            case "UNPLAYABLE":
                String unplayableReason = getUnplayableReason(playabilityStatus);

                if (unplayableReason == null) {
                    // We should have a reason so this is suspicious.
                    throw new FriendlyException("This video is unplayable.", SUSPICIOUS, null);
                }

                if (unplayableReason.contains("Playback on other websites has been disabled by the video owner") && !throwOnNotOk) {
                    return PlayabilityStatus.NON_EMBEDDABLE;
                }

                throw new FriendlyException(unplayableReason, COMMON, null);
            case "LOGIN_REQUIRED":
                String loginReason = playabilityStatus.get("reason").safeText();

                if (loginReason.contains("This video is private")) {
                    throw new CannotBeLoaded(new FriendlyException("This is a private video.", COMMON, null));
                }

                if (loginReason.contains("This video may be inappropriate for some users")) {
                    throw new FriendlyException("This video requires age verification.", SUSPICIOUS, null);
                }

                // I'm not sure if there's any conditions under which this branch can be reached,
                // but we should cover all cases. There's nothing more that this client can do,
                // so we can only hope that the next clients in the chain will catch this.
                // Although in the case of age verification, only TV_EMBEDDED can bypass that,
                // and even then, success is not guaranteed.
                throw new FriendlyException("This video requires login.", COMMON, null);
            case "CONTENT_CHECK_REQUIRED":
                throw new FriendlyException(getUnplayableReason(playabilityStatus), COMMON, null);
            case "LIVE_STREAM_OFFLINE":
                if (!playabilityStatus.get("errorScreen").get("ypcTrailerRenderer").isNull()) {
                    // ANDROID cannot load this as it returns a base64 protobuf response.
                    // Not sure on iOS. WEB should be able to load it.
                    throw new FriendlyException("This trailer cannot be loaded.", COMMON, null);
                }

                throw new FriendlyException(getUnplayableReason(playabilityStatus), COMMON, null);
            default:
                throw new FriendlyException("This video cannot be viewed anonymously.", COMMON, null);
        }
    }

    @Nullable
    default String getUnplayableReason(@NotNull JsonBrowser statusBlock) {
        JsonBrowser playerErrorMessage = statusBlock.get("errorScreen").get("playerErrorMessageRenderer");

        if (!playerErrorMessage.get("subreason").isNull()) {
            JsonBrowser subreason = playerErrorMessage.get("subreason");

            if (!subreason.get("simpleText").isNull()) {
                return subreason.get("simpleText").text();
            } else if (!subreason.get("runs").isNull() && subreason.get("runs").isList()) {
                return subreason.get("runs").values().stream()
                    .map(item -> item.get("text").text())
                    .collect(Collectors.joining("\n"));
            }
        }

        return statusBlock.get("reason").text();
    }

    @Nullable
    default AudioTrack findSelectedTrack(@NotNull List<AudioTrack> tracks,
                                         @Nullable String selectedVideoId) {
        if (selectedVideoId != null) {
            return tracks.stream().filter(track -> selectedVideoId.equals(track.getIdentifier())).findFirst().orElse(null);
        }

        return null;
    }

    /**
     * Builds an audio track with the given parameters.
     * Hint: You can use {@link YoutubeAudioSourceManager#buildAudioTrack(AudioTrackInfo)} to
     * build a track with the given AudioTrackInfo.
     * @param source The source responsible for this track.
     * @param json The video JSON. This can be used to extract extra metadata.
     * @param title The title of the video.
     * @param author The video uploader.
     * @param duration The duration of the video.
     * @param videoId The video identifier.
     * @param isStream Whether this video is a livestream.
     * @return The built audio track.
     */
    @NotNull
    default AudioTrack buildAudioTrack(@NotNull YoutubeAudioSourceManager source,
                                       @NotNull JsonBrowser json,
                                       @NotNull String title,
                                       @NotNull String author,
                                       long duration,
                                       @NotNull String videoId,
                                       boolean isStream) {
        AudioTrackInfo info = new AudioTrackInfo(title, author, duration, videoId, isStream, WATCH_URL + videoId);
        return source.buildAudioTrack(info);
    }

    /**
     * @return The unique identifier for this client.
     */
    @NotNull
    String getIdentifier();

    @NotNull
    String getPlayerParams();

    @NotNull
    default ClientOptions getOptions() {
        return ClientOptions.DEFAULT;
    }

    /**
     * Returns a boolean determining whether this client can be used to handle
     * requests for the given identifier.
     * @param identifier The resource identifier. Could be an arbitrary string or a URL.
     * @return True, if this client can handle the request.
     */
    boolean canHandleRequest(@NotNull String identifier);

    /**
     * @return True, if this client can be used for loading playback URLs.
     */
    default boolean supportsFormatLoading() {
        return getOptions().getPlayback();
    }

    void setPlaylistPageCount(int count);

    /**
     * Loads streaming formats for a video.
     * @param source The source manager responsible for this client.
     * @param httpInterface The interface to use for requests.
     * @param videoId The ID of the video to load formats for.
     * @throws CannotBeLoaded If a video doesn't exist etc.
     */
    @Nullable
    TrackFormats loadFormats(@NotNull YoutubeAudioSourceManager source,
                             @NotNull HttpInterface httpInterface,
                             @NotNull String videoId) throws CannotBeLoaded, IOException;

    /**
     * Loads a single video.
     * @param source The source manager responsible for this client.
     * @param httpInterface The interface to use for requests.
     * @param videoId The ID of the video to load.
     * @return An AudioItem.
     * @throws CannotBeLoaded If a video doesn't exist etc.
     */
    @Nullable
    AudioItem loadVideo(@NotNull YoutubeAudioSourceManager source,
                        @NotNull HttpInterface httpInterface,
                        @NotNull String videoId) throws CannotBeLoaded, IOException;

    /**
     * Loads search results for a query.
     * @param source The source manager responsible for this client.
     * @param httpInterface The interface to use for requests.
     * @param searchQuery The search query.
     * @return An AudioItem.
     * @throws CannotBeLoaded If a video doesn't exist etc.
     */
    @Nullable
    AudioItem loadSearch(@NotNull YoutubeAudioSourceManager source,
                         @NotNull HttpInterface httpInterface,
                         @NotNull String searchQuery) throws CannotBeLoaded, IOException;

    /**
     * Loads search results for a query.
     * @param source The source manager responsible for this client.
     * @param httpInterface The interface to use for requests.
     * @param searchQuery The search query.
     * @return An AudioItem.
     * @throws CannotBeLoaded If a video doesn't exist etc.
     */
    @Nullable
    AudioItem loadSearchMusic(@NotNull YoutubeAudioSourceManager source,
                              @NotNull HttpInterface httpInterface,
                              @NotNull String searchQuery) throws CannotBeLoaded, IOException;

    /**
     * Loads a mix playlist.
     * @param source The source manager responsible for this client.
     * @param httpInterface The interface to use for requests.
     * @param mixId The ID of the mix.
     * @return An AudioItem.
     * @throws CannotBeLoaded If a video doesn't exist etc.
     */
    @Nullable
    AudioItem loadMix(@NotNull YoutubeAudioSourceManager source,
                      @NotNull HttpInterface httpInterface,
                      @NotNull String mixId,
                      @Nullable String selectedVideoId) throws CannotBeLoaded, IOException;

    /**
     * Loads a playlist.
     * @param source The source manager responsible for this client.
     * @param httpInterface The interface to use for requests.
     * @param playlistId The ID of the playlist.
     * @return An AudioItem.
     * @throws CannotBeLoaded If a video doesn't exist etc.
     */
    @Nullable
    AudioItem loadPlaylist(@NotNull YoutubeAudioSourceManager source,
                           @NotNull HttpInterface httpInterface,
                           @NotNull String playlistId,
                           @Nullable String selectedVideoId) throws CannotBeLoaded, IOException;

    enum PlayabilityStatus {
        OK,
        NON_EMBEDDABLE,
        REQUIRES_LOGIN,
        PREMIERE_TRAILER
    }
}
