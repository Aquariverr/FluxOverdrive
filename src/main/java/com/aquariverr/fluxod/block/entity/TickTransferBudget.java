package com.aquariverr.fluxod.block.entity;

final class TickTransferBudget {

    private long tick = Long.MIN_VALUE;
    private long transferred;

    long remaining(long currentTick, long limit) {
        resetIfNeeded(currentTick);
        long sanitizedLimit = Math.max(0, limit);
        return sanitizedLimit - Math.min(sanitizedLimit, transferred);
    }

    void record(long currentTick, long amount) {
        resetIfNeeded(currentTick);
        if (amount <= 0) return;
        try {
            transferred = Math.addExact(transferred, amount);
        } catch (ArithmeticException ignored) {
            transferred = Long.MAX_VALUE;
        }
    }

    private void resetIfNeeded(long currentTick) {
        if (currentTick != tick) {
            tick = currentTick;
            transferred = 0;
        }
    }
}
