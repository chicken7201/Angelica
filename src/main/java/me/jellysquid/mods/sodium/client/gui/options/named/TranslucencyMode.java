package me.jellysquid.mods.sodium.client.gui.options.named;

public enum TranslucencyMode implements NamedState {
    OFF("options.off"),
    FAST("options.graphics.fast"),
    FANCY("options.graphics.fancy");

    private final String name;

    /** Initializes TranslucencyMode. */
    TranslucencyMode(String name) {
        this.name = name;
    }

    /** Handles get key. */
    @Override
    public String getKey() {
        return this.name;
    }

    /** Handles sorts. */
    public boolean sorts() {
        return this != OFF;
    }

    /** Handles composites. */
    public boolean composites() {
        return this == FANCY;
    }
}
