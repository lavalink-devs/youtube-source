package dev.lavalink.youtube;

import com.sedmelluq.discord.lavaplayer.tools.FriendlyException;

import java.util.List;

/**
 * Thrown when all clients failed to load a track.
 */
public class AllClientsFailedException extends FriendlyException {
    /**
     * @param suppressed The exceptions that were caused client failures.
     */
    public AllClientsFailedException(List<Throwable> suppressed) {
        super("All clients failed to load the item. See suppressed exceptions for details.", Severity.SUSPICIOUS, null);
        suppressed.forEach(this::addSuppressed);
    }
}