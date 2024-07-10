# youtube-source
A rewritten YouTube source manager for Lavaplayer.

This source aims to provide robustness by leveraging multiple InnerTube clients
for requests. Where one client fails, another will try to load the request.
Which clients are used is entirely configurable.

## common
This module provides the base source manager, which can be used with any
`com.sedmelluq.discord.lavaplayer` packages still on major version `1`.

<details>
<summary>Using in Gradle:</summary>

```kotlin
repositories {
  // replace with https://maven.lavalink.dev/snapshots if you want to use a snapshot version.
  maven(url = "https://maven.lavalink.dev/releases")
}

dependencies {
  // Replace VERSION with the current version as shown by the Releases tab or a long commit hash `-SNAPSHOT` for snapshots.
  implementation("dev.lavalink.youtube:common:VERSION")
}
```

</details>
Example usage:

```java
YoutubeAudioSourceManager youtube = new YoutubeAudioSourceManager();
// Optionally, you may instantiate the source with a custom options, such as toggling use of searching, and clients.
YoutubeAudioSourceManager youtube = new YoutubeAudioSourceManager(/*allowSearch:*/ true, new Client[] { new Music(), new Web(), new AndroidTestsuite() });
```

You may also extend the `Client` interface to support additional InnerTube clients. There are a few abstract classes to
make this easier, notably, `MusicClient` (for `music.youtube.com` InnerTube clients), `NonMusicClient` (for youtube.com
innertube clients) and `StreamingNonMusicClient` (for clients that can be used to stream videos).

Support for IP rotation has been included, and can be achieved using the following:
```java
AbstractRoutePlanner routePlanner = new ...
YoutubeIpRotatorSetup rotator = new YoutubeIpRotatorSetup(routePlanner);

rotator.forConfiguration(youtube.getHttpInterfaceManager(), false)
    .withMainDelegateFilter(null) // This is important, otherwise you may get NullPointerExceptions.
    .setup();
```

## v2
This modules expands on `common` by providing additional support for
Lavaplayer `2.x` clients, such as [Lavalink-Devs/Lavaplayer](https://github.com/lavalink-devs/lavaplayer).
Such features currently include thumbnail support within `AudioTrackInfo`.
Additional clients are included that provide access to this additional information.
These clients are suffixed with `Thumbnail`, such as `WebWithThumbnail`, `AndroidWithThumbnail` etc.

<details>
<summary>Using in Gradle:</summary>

```kotlin
repositories {
  // replace with https://maven.lavalink.dev/snapshots if you want to use a snapshot version.
  maven(url = "https://maven.lavalink.dev/releases")
}

dependencies {
  // Replace VERSION with the current version as shown by the Releases tab or a long commit hash `-SNAPSHOT` for snapshots.
  implementation("dev.lavalink.youtube:v2:VERSION")
}
```

</details>

Example usage:
```java
// same as the 'common' module but there are additional clients that provide video thumbnails in the returned metadata.
YoutubeAudioSourceManager youtube = new YoutubeAudioSourceManager(/*allowSearch:*/ true, new Client[] { new MusicWithThumbnail(), new WebWithThumbnail(), new AndroidTestsuiteWithThumbnail() });
```

## plugin
This module serves as the plugin for use with [Lavalink](https://github.com/lavalink-devs/Lavalink).

To use this plugin with Lavalink, you must declare the dependency.

<details>
<summary>Using with Lavalink v3:</summary>

```yaml
lavalink:
  plugins:
    # Replace VERSION with the current version as shown by the Releases tab or a long commit hash for snapshots.
    - dependency: "dev.lavalink.youtube:youtube-plugin:VERSION"
      repository: "https://maven.lavalink.dev/releases" # use https://maven.lavalink.dev/snapshots if you want to use a snapshot version.
```

</details>

<details>
<summary>Using with Lavalink v4:</summary>

```yaml
lavalink:
  plugins:
    # Replace VERSION with the current version as shown by the Releases tab or a long commit hash for snapshots.
    - dependency: "dev.lavalink.youtube:youtube-plugin:VERSION"
      snapshot: false # Set to true if you want to use a snapshot version.
```

</details>

Configuring the plugin:
```yaml
plugins:
  youtube:
    enabled: true # Whether this source can be used.
    allowSearch: true # Whether "ytsearch:" and "ytmsearch:" can be used.
    allowDirectVideoIds: true # Whether just video IDs can match. If false, only complete URLs will be loaded.
    allowDirectPlaylistIds: true # Whether just playlist IDs can match. If false, only complete URLs will be loaded.
    # The clients to use for track loading. See below for a list of valid clients.
    # Clients are queried in the order they are given (so the first client is queried first and so on...)
    clients:
      - MUSIC
      - ANDROID_TESTSUITE
      - WEB

    # You can configure individual clients with the following.
    # Any options or clients left unspecified will use their default values
    # for those individual clients.
    # Client configurations will ONLY take effect if the client is registered above,
    # otherwise they are ignored.
    # ---------------- WARNING ----------------
    # This part of the config is for DEMONSTRATION PURPOSES ONLY!
    # Do NOT use this config before understanding what the options do.
    # You do NOT need to copy this config just because it is published here.
    # ---------------- WARNING ----------------
    WEB: # names are specified as they are written below under "Available Clients".
      # This will disable using the WEB client for video playback.
      playback: false
    TVHTML5EMBEDDED:
      # The below config disables everything except playback for this client.
      playlistLoading: false # Disables loading of playlists and mixes for this client.
      videoLoading: false # Disables loading of videos for this client (playback is still allowed).
      searching: false # Disables the ability to search for videos for this client.
```

> [!IMPORTANT]
> You must make sure to disable the built-in YouTube source like so:
```yaml
lavalink:
  server:
    sources:
      youtube: false
```

Existing options, such as `ratelimit` and `youtubePlaylistLoadLimit` will be picked up automatically by the plugin,
so these don't need changing.

## Available Clients
Currently, the following clients are available for use:

- `MUSIC`
  - Provides support for searching YouTube music (`ytmsearch:`)
  - **This client CANNOT be used to play tracks.** You must also register one of the
    below clients for track playback.
- `WEB`
- `ANDROID`
- `ANDROID_TESTSUITE`
  - This client has restrictions imposed, notably: it does NOT support loading of mixes or playlists,
    and it is unable to yield any supported formats when playing livestreams.
    It is advised not to use this client on its own for that reason, if those features are required.
- `ANDROID_LITE`
  - This client **does not receive Opus formats** so transcoding is required.
  - Similar restrictions to that of `ANDROID_TESTSUITE` except livestreams are playable.
- `MEDIA_CONNECT`
  - This client **does not receive Opus formats** so transcoding is required.
  - This client has restrictions imposed, including but possibly not limited to:
    - Unable to load playlists.
    - Unable to use search.
- `IOS`
  - This client **does not receive Opus formats**, so transcoding is required. This can
    increase resource consumption. It is recommended not to use this client unless it has
    the least priority (specified last), or where hardware usage is not a concern.
- `TVHTML5EMBEDDED`
  - This client is useful for playing age-restricted tracks. Do keep in mind that, even with this
    client enabled, age-restricted tracks are **not** guaranteed to play.

## Migration from Lavaplayer's built-in YouTube source

This client is intended as a direct replacement for Lavaplayer's built-in `YoutubeAudioSourceManager`,
which has been deprecated in a recent release of [Lavalink-Devs/Lavaplayer](https://github.com/lavalink-devs/lavaplayer).

When using `AudioSourceManagers.registerRemoteSources(AudioPlayerManager)`, Lavaplayer will register its own
deprecated `YoutubeAudioSourceManager`, which must be disabled.
Some versions of Lavaplayer may include an optional `excludeSources` parameter, allowing you to toggle the adding of the source.
If the version you are using does not support this, you will need to manually register each `AudioSourceManager` yourself.

First, create and register an instance of the supported `YoutubeAudioSourceManager` from the `youtube-source` package.
```java
AudioPlayerManager playerManager = new DefaultAudioPlayerManager();
YoutubeAudioSourceManager ytSourceManager = new dev.lavalink.youtube.YoutubeAudioSourceManager();
playerManager.registerSourceManager(ytSourceManager);
```

If your version of Lavaplayer supports an `excludeSources` parameter or equivalent, you may exclude the built-in
`YoutubeAudioSourceManager` using the following:
```java
AudioSourceManagers.registerRemoteSources(playerManager,
                                          com.sedmelluq.discord.lavaplayer.source.youtube.YoutubeAudioSourceManager.class);
```

Otherwise, you will need to register each source manager individually.

In addition, there are a few significant changes to note:

- This source's class structure differs so if you had custom classes that you were initialising
  the source manager with (e.g. an overridden `YoutubeTrackDetailsLoader`), this **is not** compatible
  with this source manager.

- Support for logging into accounts as a means of playing age-restricted tracks has been removed, with the
  `TVHTML5EMBEDDED` client instead being the preferred workaround. There were a large number of
  reasons for this change, but not least the fact that logging in was slowly becoming problematic and deprecated
  on the YouTube backend. The amount of code to support this feature meant that it has been axed.

## Additional Support
If you need additional help with using this source, that's not covered here or in any of the issues, 
[join our Discord server](https://discord.gg/ZW4s47Ppw4).
