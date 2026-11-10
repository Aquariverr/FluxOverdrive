package com.aquariverr.fluxod.block.entity;

final class QuantumCoolingTickBudget {

    private long gameTime = Long.MIN_VALUE;
    private long usedMilliKelvin;

    long limit(long currentGameTime, long maximumMilliKelvin, long requestedMilliKelvin) {
        beginTick(currentGameTime);
        long remaining = Math.max(0L, maximumMilliKelvin - usedMilliKelvin);
        return Math.min(Math.max(0L, requestedMilliKelvin), remaining);
    }

    void record(long currentGameTime, long consumedMilliKelvin) {
        beginTick(currentGameTime);
        long consumed = Math.max(0L, consumedMilliKelvin);
        usedMilliKelvin = Long.MAX_VALUE - usedMilliKelvin < consumed
                ? Long.MAX_VALUE
                : usedMilliKelvin + consumed;
    }

    private void beginTick(long currentGameTime) {
        if (gameTime == currentGameTime) return;
        gameTime = currentGameTime;
        usedMilliKelvin = 0L;
    }
}
