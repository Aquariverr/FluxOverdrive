package com.aquariverr.fluxod.util;

import org.junit.jupiter.api.Test;

import java.math.BigInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;

class BigIntegerFormatterTest {

    @Test
    void keepsSmallValuesReadable() {
        assertEquals("123456", BigIntegerFormatter.compact(BigInteger.valueOf(123456)));
    }

    @Test
    void compactsLargeValuesWithFourSignificantDigits() {
        assertEquals("1.234E20", BigIntegerFormatter.compact(new BigInteger("123456789012345678901")));
    }

    @Test
    void formatsFiniteNetworkStorage() {
        NetworkEnergyTotals totals = new NetworkEnergyTotals(
                new BigInteger("123456789012345678901"), new BigInteger("999999999999999999999"),
                NetworkEnergyTotals.CapacityStatus.FINITE);

        assertEquals("1.234E20 / 9.999E20 FE", BigIntegerFormatter.formatStorage(totals, "FE"));
    }

    @Test
    void formatsUnlimitedAndUnknownNetworkStorage() {
        NetworkEnergyTotals unlimited = new NetworkEnergyTotals(BigInteger.TEN, BigInteger.ZERO,
                NetworkEnergyTotals.CapacityStatus.UNLIMITED);
        NetworkEnergyTotals unknown = new NetworkEnergyTotals(BigInteger.TEN, BigInteger.ZERO,
                NetworkEnergyTotals.CapacityStatus.UNKNOWN);

        assertEquals("10 / ∞ FE", BigIntegerFormatter.formatStorage(unlimited, "FE"));
        assertEquals("10 FE", BigIntegerFormatter.formatStorage(unknown, "FE"));
    }
}
