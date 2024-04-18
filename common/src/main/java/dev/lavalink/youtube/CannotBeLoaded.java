package dev.lavalink.youtube;

import org.jetbrains.annotations.NotNull;

public class CannotBeLoaded extends Throwable {
    // This is a 'cheap' exception used to tell the source manager to stop trying
    // to load a track as it's unloadable, e.g. if a video doesn't exist/is private etc...

    /**
     * Instantiates a new CannotBeLoaded exception to halt querying of the next clients
     * in the chain.
     * @param original The original exception that triggered this exception.
     */
    public CannotBeLoaded(@NotNull Throwable original) {
        super("The URL could not be loaded.", original, false, false);
    }
}
