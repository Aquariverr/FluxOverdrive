package com.aquariverr.fluxod.util;

import java.math.BigInteger;
import java.util.Objects;

public record NetworkEnergyTotals(BigInteger stored, BigInteger finiteCapacity,
                                  CapacityStatus capacityStatus) {

    public NetworkEnergyTotals {
        stored = nonNegative(stored);
        finiteCapacity = nonNegative(finiteCapacity);
        Objects.requireNonNull(capacityStatus, "capacityStatus");
    }

    public static Builder builder() {
        return new Builder();
    }

    public static NetworkEnergyTotals fallback(long stored) {
        return new NetworkEnergyTotals(nonNegative(stored), BigInteger.ZERO, CapacityStatus.UNKNOWN);
    }

    public long storedAsSaturatedLong() {
        return stored.compareTo(BigInteger.valueOf(Long.MAX_VALUE)) >= 0
                ? Long.MAX_VALUE : stored.longValueExact();
    }

    private static BigInteger nonNegative(BigInteger value) {
        return Objects.requireNonNull(value, "value").max(BigInteger.ZERO);
    }

    private static BigInteger nonNegative(long value) {
        return value <= 0 ? BigInteger.ZERO : BigInteger.valueOf(value);
    }

    public enum CapacityStatus {
        FINITE(0),
        UNLIMITED(1),
        UNKNOWN(2);

        private final int id;

        CapacityStatus(int id) {
            this.id = id;
        }

        public int id() {
            return id;
        }

        public static CapacityStatus byId(int id) {
            return switch (id) {
                case 0 -> FINITE;
                case 1 -> UNLIMITED;
                default -> UNKNOWN;
            };
        }
    }

    public static final class Builder {

        private BigInteger stored = BigInteger.ZERO;
        private BigInteger finiteCapacity = BigInteger.ZERO;
        private boolean unlimited;
        private boolean unknown;

        public Builder addFinite(BigInteger stored, BigInteger capacity) {
            this.stored = this.stored.add(nonNegative(stored));
            finiteCapacity = finiteCapacity.add(nonNegative(capacity));
            return this;
        }

        public Builder addFinite(long stored, long capacity) {
            return addFinite(nonNegative(stored), nonNegative(capacity));
        }

        public Builder addUnlimited(BigInteger stored) {
            this.stored = this.stored.add(nonNegative(stored));
            unlimited = true;
            return this;
        }

        public Builder addUnknown(long stored) {
            this.stored = this.stored.add(nonNegative(stored));
            unknown = true;
            return this;
        }

        public NetworkEnergyTotals build() {
            CapacityStatus status = unlimited ? CapacityStatus.UNLIMITED
                    : unknown ? CapacityStatus.UNKNOWN : CapacityStatus.FINITE;
            return new NetworkEnergyTotals(stored, finiteCapacity, status);
        }
    }
}
