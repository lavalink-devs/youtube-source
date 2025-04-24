package dev.lavalink.youtube.cipher;

import org.jetbrains.annotations.NotNull;

public class TCEVariable {
    private final String name;
    private final String code;
    private final String value;

    public TCEVariable(@NotNull String name, @NotNull String code, @NotNull String value) {
        this.name = name;
        this.code = code;
        this.value = value;
    }

    public String getEscapedName() {
        return this.name.replace("$", "\\$");
    }

    public String getName() {
        return this.name;
    }

    public String getCode() {
        return this.code;
    }

    public String getValue() {
        return this.value;
    }

}
