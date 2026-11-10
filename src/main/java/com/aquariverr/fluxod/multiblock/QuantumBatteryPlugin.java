package com.aquariverr.fluxod.multiblock;

public interface QuantumBatteryPlugin {

    int MIN_TIER = 1;
    int MAX_TIER = 3;

    int getPluginTier();

    default boolean canInstallInRing(int ringTier) {
        return canInstall(getPluginTier(), ringTier);
    }

    static boolean canInstall(int pluginTier, int ringTier) {
        return pluginTier >= MIN_TIER && pluginTier <= MAX_TIER
                && ringTier >= pluginTier && ringTier <= MAX_TIER;
    }
}
