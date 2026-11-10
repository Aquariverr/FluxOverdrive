package com.aquariverr.fluxod.block.entity;

final class QuantumStabilityGauge {

    static final int MAX_BASIS_POINTS = 10_000;
    static final int CHANGE_BASIS_POINTS = 40;
    static final int TICKS_PER_CHANGE = 20;

    private int basisPoints = MAX_BASIS_POINTS;
    private int unpoweredTickRemainder;
    private int poweredTickRemainder;

    int basisPoints() {
        return basisPoints;
    }

    int unpoweredTickRemainder() {
        return unpoweredTickRemainder;
    }

    int poweredTickRemainder() {
        return poweredTickRemainder;
    }

    boolean recordConstraintPower(boolean powered) {
        if (powered) {
            if (basisPoints >= MAX_BASIS_POINTS) {
                poweredTickRemainder = 0;
                return false;
            }
            poweredTickRemainder++;
            if (poweredTickRemainder < TICKS_PER_CHANGE) return false;

            poweredTickRemainder -= TICKS_PER_CHANGE;
            basisPoints = Math.min(MAX_BASIS_POINTS, basisPoints + CHANGE_BASIS_POINTS);
            if (basisPoints == MAX_BASIS_POINTS) {
                unpoweredTickRemainder = 0;
                poweredTickRemainder = 0;
            }
            return true;
        }

        if (basisPoints <= 0) {
            unpoweredTickRemainder = 0;
            return false;
        }
        unpoweredTickRemainder++;
        if (unpoweredTickRemainder < TICKS_PER_CHANGE) return false;

        unpoweredTickRemainder -= TICKS_PER_CHANGE;
        basisPoints = Math.max(0, basisPoints - CHANGE_BASIS_POINTS);
        if (basisPoints == 0) unpoweredTickRemainder = 0;
        return true;
    }

    void load(int basisPoints, int unpoweredTickRemainder, int poweredTickRemainder) {
        this.basisPoints = Math.clamp(basisPoints, 0, MAX_BASIS_POINTS);
        this.unpoweredTickRemainder = Math.clamp(unpoweredTickRemainder, 0, TICKS_PER_CHANGE - 1);
        this.poweredTickRemainder = Math.clamp(poweredTickRemainder, 0, TICKS_PER_CHANGE - 1);
    }

    void reset() {
        basisPoints = MAX_BASIS_POINTS;
        unpoweredTickRemainder = 0;
        poweredTickRemainder = 0;
    }
}
