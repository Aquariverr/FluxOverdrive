package com.aquariverr.fluxod.util;

import org.junit.jupiter.api.Test;

import java.math.BigInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;

class NetworkEnergyTotalsTest {

    @Test
    void aggregatesFiniteStorageBeyondLongRange() {
        NetworkEnergyTotals totals = NetworkEnergyTotals.builder()
                .addFinite(Long.MAX_VALUE, Long.MAX_VALUE)
                .addFinite(1, 2)
                .build();

        assertEquals(BigInteger.valueOf(Long.MAX_VALUE).add(BigInteger.ONE), totals.stored());
        assertEquals(BigInteger.valueOf(Long.MAX_VALUE).add(BigInteger.TWO), totals.finiteCapacity());
        assertEquals(NetworkEnergyTotals.CapacityStatus.FINITE, totals.capacityStatus());
        assertEquals(Long.MAX_VALUE, totals.storedAsSaturatedLong());
    }

    @Test
    void unlimitedCapacityDominatesUnknownCapacity() {
        NetworkEnergyTotals totals = NetworkEnergyTotals.builder()
                .addUnknown(10)
                .addUnlimited(BigInteger.valueOf(20))
                .build();

        assertEquals(BigInteger.valueOf(30), totals.stored());
        assertEquals(NetworkEnergyTotals.CapacityStatus.UNLIMITED, totals.capacityStatus());
    }

    @Test
    void unknownCapacityIsRetainedWithoutUnlimitedStorage() {
        NetworkEnergyTotals totals = NetworkEnergyTotals.builder()
                .addFinite(10, 100)
                .addUnknown(20)
                .build();

        assertEquals(BigInteger.valueOf(30), totals.stored());
        assertEquals(BigInteger.valueOf(100), totals.finiteCapacity());
        assertEquals(NetworkEnergyTotals.CapacityStatus.UNKNOWN, totals.capacityStatus());
    }

    @Test
    void invalidLongValuesDoNotCreateNegativeTotals() {
        NetworkEnergyTotals totals = NetworkEnergyTotals.builder()
                .addFinite(-10, -100)
                .addUnknown(-20)
                .build();

        assertEquals(BigInteger.ZERO, totals.stored());
        assertEquals(BigInteger.ZERO, totals.finiteCapacity());
    }
}
