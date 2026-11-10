package com.aquariverr.fluxod.block.entity;

final class QuantumStabilityWindow {

    private long lastCheckedGameTime = Long.MIN_VALUE;

    boolean beginCheck(long gameTime) {
        if (lastCheckedGameTime == gameTime) return false;
        lastCheckedGameTime = gameTime;
        return true;
    }

    void reset() {
        lastCheckedGameTime = Long.MIN_VALUE;
    }
}
