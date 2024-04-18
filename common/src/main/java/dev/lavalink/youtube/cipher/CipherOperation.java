package dev.lavalink.youtube.cipher;

import org.jetbrains.annotations.NotNull;

/**
 * One cipher operation definition.
 */
public class CipherOperation {
    /**
     * The type of the operation.
     */
    public final CipherOperationType type;
    /**
     * The parameter for the operation.
     */
    public final int parameter;

    /**
     * @param type The type of the operation.
     * @param parameter The parameter for the operation.
     */
    public CipherOperation(@NotNull CipherOperationType type, int parameter) {
        this.type = type;
        this.parameter = parameter;
    }
}
