# lavaplayer-youtube-source
A rewritten YouTube source manager for Lavaplayer.

This source aims to provide robustness by leveraging multiple InnerTube clients
for requests. Where one client fails, another will try to load the request.
Which clients are used is entirely configurable.

## common
This module provides the base source manager, which can be used with any
`com.sedmelluq.discord.lavaplayer` packages still on major version `1`.

Example usage:
```java
YoutubeAudioSourceManager youtube = new YoutubeAudioSourceManager();
// Optionally, you may instantiate the source with a custom options, such as toggling use of searching, and clients.
YoutubeAudioSourceManager youtube = new YoutubeAudioSourceManager(/*allowSearch:*/ true, new Client[] { new Music(), new Web(), new Android() });
```

You may also extend the `Client` interface to support additional InnerTube clients. There are a few abstract classes to
make this easier, notably, `MusicClient` (for `music.youtube.com` InnerTube clients), `NonMusicClient` (for youtube.com
innertube clients) and `StreamingNonMusicClient` (for clients that can be used to stream videos).

Support for IP rotation has been included, and can be achieved using the following:
```java
AbstractRouterPlanner routePlanner = new ...
YoutubeIpRotatorSetup rotator = new YoutubeIpRotatorSetup(routePlanner);

rotator.forConfiguration(youtube.getHttpInterfaceManager(), false)
    .withMainDelegateFilter(null) // This is important, otherwise you may get NullPointerExceptions.
    .setup();
```

## lldevs
This modules expands on `common` by providing additional support for
Lavaplayer `2.x` clients, such as [Lavalink-Devs/Lavaplayer](https://github.com/lavalink-devs/lavaplayer).
Such features currently include thumbnail support within `AudioTrackInfo`.
Additional clients are included that provide access to this additional information.
These clients are suffixed with `Thumbnail`, such as `WebWithThumbnail`, `AndroidWithThumbnail` etc.

Example usage:
```java
// same as the 'common' module but there are additional clients that provide video thumbnails in the returned metadata.
YoutubeAudioSourceManager youtube = new YoutubeAudioSourceManager(/*allowSearch:*/ true, new Client[] { new MusicWithThumbnail(), new WebWithThumbnail(), new AndroidWithThumbnail() });
```

## plugin
This module serves as the plugin for use with [Lavalink](https://github.com/lavalink-devs/lavalink).

To use this plugin with Lavalink, you must declare the dependency.
```yaml
lavalink:
  # ...
  plugins:
    # replace VERSION with the current version as shown by the Releases tab.
    - dependency: "com.github.lavalink-devs.lavaplayer-youtube-source:plugin:VERSION"
      repository: "https://jitpack.io"
```

Configuring the plugin:
```yaml
plugins:
  youtube:
    enabled: true
    clients: ["MUSIC", "ANDROID", "WEB"]
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
- `WEB`
- `ANDROID`
- `ANDROID_TESTSUITE`
  - NOTE: This client does NOT support loading of mixes or playlists.
    It is advised not to use this client on its own for that reason, if playlists and mix support is required.
- `IOS`
  - NOTE: This client does not receive Opus formats, so transcoding is required. This can
    increase resource consumption. It is recommended not to use this client unless it has
    the least priority (specified last), or where hardware usage is not a concern.
- `TVHTML5EMBEDDED`
  - This client is useful for playing age-restricted tracks. Do keep in mind that, even with this
    client enabled, age-restricted tracks are **not** guaranteed to play.

## Migration from Lavaplayer's built-in YouTube source
This client is intended to be a drop-in replacement, however there are a couple of things to note:

- This source's class structure differs so if you had custom classes that you were initialising
  the source manager with (e.g. an overridden `YoutubeTrackDetailsLoader`), this **is not** compatible
  with this source manager.

- Support for logging into accounts as a means of playing age-restricted tracks has been removed, with the
  `TVHTML5EMBEDDED` client instead being the preferred workaround. There were a large number of
  reasons for this change, but not least the fact that logging in was slowly becoming problematic and deprecated
  on the YouTube backend. The amount of code to support this feature meant that it has been axed.
