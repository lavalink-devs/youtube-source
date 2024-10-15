# youtube-source
A rewritten YouTube source manager for Lavaplayer.

This source aims to provide robustness by leveraging multiple InnerTube clients
for requests. Where one client fails, another will try to load the request.
Which clients are used is entirely configurable.

## Table of Contents
- [Common](#common)
  - Information about the `common` module and usage of.
- [V2](#v2)
  - Information about the `v2` module and usage of.
- [Plugin](#plugin)
  - Information about the `plugin` module and usage of.
- [Available Clients](#available-clients)
  - Information about the clients provided by `youtube-source`, as well as their advantages/disadvantages.
- [Using OAuth tokens](#using-oauth-tokens)
  - Information on using OAuth tokens with `youtube-source`.
- [Using a poToken](#using-a-potoken)
  - Information on using a `poToken` with `youtube-source`.
- [REST Routes (`plugin` only)](#rest-routes-plugin-only)
  - Information on the REST routes provided by the `youtube-source` plugin module.
- [Migration Information](#migration-from-lavaplayers-built-in-youtube-source)
  - Information on migrating from Lavaplayer's built-in Youtube source manager.
- [Additional Support](#additional-support)
  - For everything else.

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

// 'youtube' is the variable holding your YoutubeAudioSourceManager instance.
rotator.forConfiguration(youtube.getHttpInterfaceManager(), false)
    .withMainDelegateFilter(youtube.getContextFilter()) // IMPORTANT
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
> [!IMPORTANT]
> You must make sure to disable the built-in YouTube source like so:
```yaml
lavalink:
  server:
    sources:
      youtube: false
```

> [!NOTE]
> Existing options, such as `ratelimit` and `youtubePlaylistLoadLimit` will be picked up automatically by the plugin,
> so these don't need changing.
> 
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
      - TVHTML5EMBEDDED
```

### Advanced Options
```yaml
    # The below section of the config allows setting specific options for each client, such as the requests they will handle.
    # If an option, or client, is unspecified, then the default option value/client values will be used instead.
    # If a client is configured, but is not registered above, the options for that client will be ignored.
    # WARNING!: THE BELOW CONFIG IS FOR ILLUSTRATION PURPOSES. DO NOT COPY OR USE THIS WITHOUT
    # WARNING!: UNDERSTANDING WHAT IT DOES. MISCONFIGURATION WILL HINDER YOUTUBE-SOURCE'S ABILITY TO WORK PROPERLY.

    # Write the names of clients as they are specified under the heading "Available Clients".
    clientOptions:
      WEB:
        # Example: Disabling a client's playback capabilities.
        playback: false
        videoLoading: false # Disables loading of videos for this client. A client may still be used for playback even if this is set to 'false'.
      TVHTML5EMBEDDED:
        # Example: Configuring a client to exclusively be used for video loading and playback.
        playlistLoading: false # Disables loading of playlists and mixes.
        searching: false # Disables the ability to search for videos.
```

## Available Clients
Currently, the following clients are available for use:

- `MUSIC`
  - ✔ Provides support for searching YouTube music (`ytmsearch:`).
  - ❌ Cannot be used for playback, or playlist/mix/livestream loading.
- `WEB`
  - ✔ Opus formats.
- `WEBEMBEDDED`
  - ✔ Opus formats.
  - ❌ No mix/playlist/search support.
- `ANDROID`
  - ❌ Heavily restricted, frequently dysfunctional.
- `ANDROID_TESTSUITE`
  - ✔ Opus formats.
  - ❌ No mix/playlist/livestream support.
- `ANDROID_MUSIC`
  - ✔ Opus formats.
  - ❌ No playlist/livestream support.
- `MEDIA_CONNECT`
  - ❌ No Opus formats (requires transcoding).
  - ❌ No mix/playlist/search support.
- `IOS`
  - ❌ No Opus formats (requires transcoding).
- `TVHTML5EMBEDDED`
  - ✔ Opus formats.
  - ✔ Age-restricted video playback.
  - ❌ No playlist support.

## Using OAuth Tokens
You may notice that some requests are flagged by YouTube, causing an error message asking you to sign in to confirm you're not a bot.
With OAuth integration, you can request that `youtube-source` use your account credentials to appear as a normal user, with varying degrees
of efficacy. **You do _not_ need to use `poToken` with OAuth.**

> [!WARNING]
> Similar to the `poToken` method, this is NOT a silver bullet solution, and worst case could get your account terminated!
> For this reason, it is advised that **you use burner accounts and NOT your primary!**.
> This method may also trigger ratelimit errors if used in a high traffic environment.
> USE WITH CAUTION!

> [!NOTE]
> You may need to set your log level for `dev.lavalink.youtube.http.YoutubeOauth2Handler` to `INFO`, to see additional information
> within your terminal regarding completing the OAuth flow.

> [!NOTE]
> If you do not have a refresh token, then do not supply one. The source will output your refresh token into your terminal upon
> successfully completing the OAuth flow at least **once**. If you do not see your token, you may need to configure your
> logging (see above note).

You can instruct `youtube-source` to use OAuth with the following:

### Lavaplayer
```java
YoutubeAudioSourceManager source = new YoutubeAudioSourceManager();
// This will trigger an OAuth flow, where you will be instructed to head to YouTube's OAuth page and input a code.
// This is safe, as it only uses YouTube's official OAuth flow. No tokens are seen or stored by us.
source.useOauth2(null, false);

// If you already have a refresh token, you can instruct the source to use it, skipping the OAuth flow entirely.
// You can also set the `skipInitialization` parameter, which skips the OAuth flow. This should only be used
// if you intend to supply a refresh token later on. You **must** either complete the OAuth flow or supply
// a refresh token for OAuth integration to work.
source.useOauth2("your refresh token", true);
```

### Lavalink
```yaml
plugins:
  youtube:
    enabled: true
    oauth:
      # setting "enabled: true" is the bare minimum to get OAuth working.
      enabled: true

      # if you have a refresh token, you may set it below (make sure to uncomment the line to apply it).
      # setting a valid refresh token will skip the OAuth flow entirely. See above note on how to retrieve
      # your refreshToken.
      # refreshToken: "paste your refresh token here if applicable"

      # Set this if you don't want the OAuth flow to be triggered, if you intend to supply a refresh token later.
      # Initialization is skipped automatically if a valid refresh token is supplied. Leave this commented if you're
      # completing the OAuth flow for the first time/do not have a refresh token.
      # skipInitialization: true
```

## Using a `poToken`
A `poToken`, also known as a "Proof of Origin Token" is a way to identify what requests originate from.
In YouTube's case, this is sent as a JavaScript challenge that browsers must evaluate, and send back the resolved
string. Typically, this challenge would remain unsolved for bots as more often than not, they don't simulate an entire
browser environment, instead only evaluating the minimum amount of JS required to do its job. Therefore, it's a reasonable
assumption that if the challenge is not fulfilled, the request origin is a bot.

To obtain a `poToken`, you can use https://github.com/iv-org/youtube-trusted-session-generator, by running the Python script
or the docker image. Both methods will print a `poToken` after a successful run, which you can supply to `youtube-source`
to try and work around having automated requests blocked.


> [!NOTE]
> A `poToken` is not a silver bullet, and currently it only applies to requests made via the `WEB` client.
> You do not need to specify a `poToken` if using OAuth, and vice versa.

Specifying the token is as simple as doing:

### Lavaplayer
```java
// Web is dev.lavalink.youtube.clients.Web
Web.setPoTokenAndVisitorData("your po_token", "your visitor_data");
```

### Lavalink
```yaml
plugins:
  youtube:
    pot:
      token: "paste your po_token here"
      visitorData: "paste your visitor_data here"
```

## REST routes (`plugin` only)
### `POST` `/youtube`

Body:

> [!NOTE]
> You do not need to provide everything as it is shown.
> For example, you can specify just `refreshToken` and `skipInitialization`, or just `poToken` and `visitorData`.
> You do **not** need to use `poToken` with OAuth and vice versa.

```json
{
  "refreshToken": "your new refresh token",
  "skipInitialization": true,
  "poToken": "your po_token",
  "visitorData": "your visitor_data"
}
```

Response:

If the YouTube source is not enabled, or the `refreshToken` is invalid:
`500 - Internal Server Error`

Otherwise:
`204 - No Content`

### `GET` `/youtube`

Response:

If the YouTube source is not enabled:
`500 - Internal Server Error`

Otherwise:
```json
{
  "refreshToken": "your current refresh token, or null"
}
```

### `GET` `/youtube/stream/{videoId}`

Query parameters:

| Key          | Value Type | Required | Notes                                                                                                                                                                       |
|--------------|------------|----------|-----------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| itag         | integer    | No       | The [itag](https://gist.github.com/AgentOak/34d47c65b1d28829bb17c24c04a0096f) of the desired format. If unspecified, youtube-source's default format selector will be used. |
| withClient   | string     | No       | The identifier of the client to use for streaming. Uses all clients if unspecified.                                                                                         |

Response:

If `videoId` could not be found or loaded, or the `itag` does not exist, or if no client supports format loading:
`400 - Bad Request`

Otherwise:
`200 - OK` accompanied by the selected format stream (audio or video). `Content-Type` header will be set appropriately.

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
