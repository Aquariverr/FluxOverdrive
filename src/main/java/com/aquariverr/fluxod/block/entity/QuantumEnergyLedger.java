package com.aquariverr.fluxod.block.entity;

import javax.annotation.Nullable;
import java.math.BigInteger;
import java.util.Optional;

public final class QuantumEnergyLedger {

    private static final BigInteger ZERO = BigInteger.ZERO;

    private BigInteger stored = ZERO;
    @Nullable
    private BigInteger capacity;

    public BigInteger stored() {
        return stored;
    }

    public Optional<BigInteger> capacity() {
        return Optional.ofNullable(capacity);
    }

    public void setStored(BigInteger value) {
        stored = QuantumEnergyCodec.clamp(value);
    }

    public void setCapacity(@Nullable BigInteger value) {
        capacity = value == null ? null : QuantumEnergyCodec.clamp(value);
    }

    public BigInteger insert(BigInteger amount) {
        if (amount.signum() <= 0) return ZERO;
        BigInteger accepted = amount.min(QuantumEnergyCodec.MAX_ENERGY.subtract(stored).max(ZERO));
        if (capacity != null) {
            accepted = accepted.min(capacity.subtract(stored).max(ZERO));
        }
        stored = stored.add(accepted);
        return accepted;
    }

    public BigInteger extract(BigInteger amount) {
        if (amount.signum() <= 0) return ZERO;
        BigInteger extracted = amount.min(stored);
        stored = stored.subtract(extracted);
        return extracted;
    }

    public long availableAsLong(long limit) {
        if (limit <= 0) return 0;
        return stored.min(BigInteger.valueOf(limit)).longValueExact();
    }

    public long spaceAfterPendingAsLong(long limit, long added, long removed) {
        if (limit <= 0) return 0;

        BigInteger pendingStored = stored
                .subtract(BigInteger.valueOf(Math.max(0, removed)))
                .add(BigInteger.valueOf(Math.max(0, added)));
        BigInteger effectiveCapacity = capacity == null ? QuantumEnergyCodec.MAX_ENERGY : capacity;
        BigInteger space = effectiveCapacity.subtract(pendingStored).max(ZERO);
        return space.min(BigInteger.valueOf(limit)).longValueExact();
    }

    public int fullnessScaled(int scale) {
        if (scale <= 0 || capacity == null || capacity.signum() == 0) return 0;
        return stored.min(capacity)
                .multiply(BigInteger.valueOf(scale))
                .divide(capacity)
                .intValueExact();
    }
}
