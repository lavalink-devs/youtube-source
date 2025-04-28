package dev.lavalink.youtube.cipher;

public class ScriptExtractionException extends RuntimeException {
    private final ExtractionFailureType failureType;

    public enum ExtractionFailureType {
        TIMESTAMP_NOT_FOUND,
        ACTION_FUNCTIONS_NOT_FOUND,
        DECIPHER_FUNCTION_NOT_FOUND,
        N_FUNCTION_NOT_FOUND
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