package dev.lavalink.youtube.cipher;

public class ScriptExtractionException extends RuntimeException {
    private final ExtractionFailureType failureType;

    public enum ExtractionFailureType {
        TIMESTAMP_NOT_FOUND("timestamp"),
        SIG_ACTIONS_NOT_FOUND("sig actions"),
        DECIPHER_FUNCTION_NOT_FOUND("sig function"),
        N_FUNCTION_NOT_FOUND("n function"),
        VARIABLES_NOT_FOUND("global variables");

        public final String friendlyName;

        ExtractionFailureType(String friendlyName) {
            this.friendlyName = friendlyName;
        }
    }

    public ScriptExtractionException(String message, ExtractionFailureType failureType) {
        super(message);
        this.failureType = failureType;
    }

    public ScriptExtractionException(String message, ExtractionFailureType failureType, Throwable cause) {
        super(message, cause);
        this.failureType = failureType;
    }

    public ExtractionFailureType getFailureType() {
        return failureType;
    }
}