package com.aquariverr.fluxod.miniature.runtime;

public enum RedstoneMode {
    IGNORE, HIGH, LOW;

    public boolean permits(boolean powered) { return this == IGNORE || powered == (this == HIGH); }
    public RedstoneMode next() { return values()[(ordinal() + 1) % values().length]; }
    public String translationKey() {
        return "screen.flux_overdrive.miniature_universe.redstone." + name().toLowerCase(java.util.Locale.ROOT);
    }
}
