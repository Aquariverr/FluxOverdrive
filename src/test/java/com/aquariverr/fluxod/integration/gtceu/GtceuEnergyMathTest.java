package com.aquariverr.fluxod.integration.gtceu;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class GtceuEnergyMathTest {

    @Test
    void limitsExtractionByRequestedFe() {
        assertEquals(25, GtceuEnergyMath.euToExtract(100, 1_000, 32, 4));
        assertEquals(24, GtceuEnergyMath.euToExtract(99, 1_000, 32, 4));
    }

    @Test
    void limitsExtractionByStoredEnergyAndOutputRate() {
        assertEquals(20, GtceuEnergyMath.euToExtract(1_000, 20, 32, 4));
        assertEquals(128, GtceuEnergyMath.euToExtract(1_000, 1_000, 32, 4));
    }

    @Test
    void rejectsAmountsBelowOneEuAndInvalidSources() {
        assertEquals(0, GtceuEnergyMath.euToExtract(3, 1_000, 32, 4));
        assertEquals(0, GtceuEnergyMath.euToExtract(100, 0, 32, 4));
        assertEquals(0, GtceuEnergyMath.euToExtract(100, 1_000, 0, 4));
        assertEquals(0, GtceuEnergyMath.euToExtract(100, 1_000, 32, 0));
    }

    @Test
    void handlesMultiplicationOverflowWithoutWrapping() {
        assertEquals(250, GtceuEnergyMath.euToExtract(1_000, Long.MAX_VALUE,
                Long.MAX_VALUE, Long.MAX_VALUE));
        assertEquals(Long.MAX_VALUE, GtceuEnergyMath.toFe(Long.MAX_VALUE));
    }
}
