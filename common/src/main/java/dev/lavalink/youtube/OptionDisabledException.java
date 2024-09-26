package dev.lavalink.youtube;

public class OptionDisabledException extends RuntimeException {
    public OptionDisabledException(String message) {
        super(message, null, true, false);
    }
}
