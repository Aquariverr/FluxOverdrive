package com.aquariverr.fluxod.block.entity;

import org.junit.jupiter.api.Test;

import java.math.BigInteger;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class QuantumBatteryStateTest {

    @Test
    void missingOuterInterfaceKeepsHighestContinuousFormedTier() {
        assertEquals(2, QuantumBatteryState.highestFormedTier(List.of(
                List.of("tier-1-interface"),
                List.of("tier-2-interface"),
                List.of())));
    }

    @Test
    void interfaceGapStopsFormationBeforeLaterLayers() {
        assertEquals(1, QuantumBatteryState.highestFormedTier(List.of(
                List.of("tier-1-interface"),
                List.of(),
                List.of("tier-3-interface"))));
    }

    @Test
    void animationTierDependsOnlyOnStructureReadiness() {
        assertEquals(0, QuantumBatteryState.animationTier(3, false, false));
        assertEquals(3, QuantumBatteryState.animationTier(3, false, true));
        assertEquals(0, QuantumBatteryState.animationTier(3, true, true));
    }

    @Test
    void formatsBasisPointsAsReadablePercentages() {
        assertEquals("100%", QuantumBatteryState.formatStability(10_000));
        assertEquals("99.6%", QuantumBatteryState.formatStability(9_960));
        assertEquals("0%", QuantumBatteryState.formatStability(0));
    }

    @Test
    void normalizesCoreLevelsAndMigratesLegacyStructureTier() {
        assertEquals(1, QuantumBatteryState.normalizeCoreLevel(0));
        assertEquals(2, QuantumBatteryState.normalizeCoreLevel(2));
        assertEquals(3, QuantumBatteryState.normalizeCoreLevel(4));
        assertEquals(3, QuantumBatteryState.coreLevelForLoad(false, 0, 3));
        assertEquals(1, QuantumBatteryState.coreLevelForLoad(false, 0, 0));
        assertEquals(1, QuantumBatteryState.coreLevelForLoad(true, 1, 3));
    }

    @Test
    void upgradesAtExactDecimalIoThresholds() {
        assertFalse(QuantumBatteryState.canUpgradeCore(1,
                QuantumBatteryState.LEVEL_2_TOTAL_IO.subtract(BigInteger.ONE)));
        assertTrue(QuantumBatteryState.canUpgradeCore(1, QuantumBatteryState.LEVEL_2_TOTAL_IO));
        assertFalse(QuantumBatteryState.canUpgradeCore(2,
                QuantumBatteryState.LEVEL_3_TOTAL_IO.subtract(BigInteger.ONE)));
        assertTrue(QuantumBatteryState.canUpgradeCore(2, QuantumBatteryState.LEVEL_3_TOTAL_IO));
        assertFalse(QuantumBatteryState.canUpgradeCore(3, QuantumBatteryState.LEVEL_3_TOTAL_IO));
    }

    @Test
    void reportsProgressWithinTheCurrentCoreStage() {
        assertEquals(0, QuantumBatteryState.coreProgressBasisPoints(1, BigInteger.ZERO));
        assertEquals(5_000, QuantumBatteryState.coreProgressBasisPoints(1,
                QuantumBatteryState.LEVEL_2_TOTAL_IO.divide(BigInteger.TWO)));
        assertEquals(10_000, QuantumBatteryState.coreProgressBasisPoints(1,
                QuantumBatteryState.LEVEL_2_TOTAL_IO));

        BigInteger levelTwoRange = QuantumBatteryState.LEVEL_3_TOTAL_IO
                .subtract(QuantumBatteryState.LEVEL_2_TOTAL_IO);
        assertEquals(0, QuantumBatteryState.coreProgressBasisPoints(2,
                QuantumBatteryState.LEVEL_2_TOTAL_IO));
        assertEquals(5_000, QuantumBatteryState.coreProgressBasisPoints(2,
                QuantumBatteryState.LEVEL_2_TOTAL_IO.add(levelTwoRange.divide(BigInteger.TWO))));
        assertEquals(10_000, QuantumBatteryState.coreProgressBasisPoints(2,
                QuantumBatteryState.LEVEL_3_TOTAL_IO));
        assertEquals(10_000, QuantumBatteryState.coreProgressBasisPoints(3, BigInteger.ZERO));
    }

    @Test
    void accumulatesAcceptedInputAndOutputInsteadOfNetChange() {
        assertEquals(BigInteger.valueOf(90), QuantumBatteryState.accumulateTotalIo(
                BigInteger.TEN, BigInteger.valueOf(40), BigInteger.valueOf(40)));
        assertEquals(BigInteger.TEN, QuantumBatteryState.accumulateTotalIo(
                BigInteger.TEN, BigInteger.valueOf(-40), BigInteger.valueOf(-40)));
    }

    @Test
    void quenchRadiusGrowsByTenBlocksPerOrderOfMagnitude() {
        assertEquals(8.0, QuantumBatteryState.quenchRadius(BigInteger.ZERO, 8.0, 10.0, 500.0));
        assertEquals(8.0, QuantumBatteryState.quenchRadius(BigInteger.ONE, 8.0, 10.0, 500.0));
        // 10^12 (Tier I full) -> 12 orders of magnitude -> 8 + 120 = 128
        assertEquals(128.0, QuantumBatteryState.quenchRadius(
                new BigInteger("1000000000000"), 8.0, 10.0, 500.0));
        // 10^21 (Tier II full) -> 21 orders of magnitude -> 8 + 210 = 218
        assertEquals(218.0, QuantumBatteryState.quenchRadius(
                new BigInteger("1000000000000000000000"), 8.0, 10.0, 500.0));
    }

    @Test
    void quenchRadiusIsCappedByMaxRadius() {
        // Tier II full with a cap of 100 -> clamped to 100
        assertEquals(100.0, QuantumBatteryState.quenchRadius(
                new BigInteger("1000000000000000000000"), 8.0, 10.0, 100.0));
        // 9 digits (10^8) -> 8 + 80 = 88, below cap
        assertEquals(88.0, QuantumBatteryState.quenchRadius(
                new BigInteger("123456789"), 8.0, 10.0, 500.0));
    }

    @Test
    void quenchRadiusHonorsConfigurationEdges() {
        // per-order 0 -> base radius only
        assertEquals(8.0, QuantumBatteryState.quenchRadius(
                new BigInteger("1000000000000"), 8.0, 0.0, 500.0));
        // negative stored energy treated as empty
        assertEquals(8.0, QuantumBatteryState.quenchRadius(
                new BigInteger("-5"), 8.0, 10.0, 500.0));
        // max radius below base -> base wins
        assertEquals(8.0, QuantumBatteryState.quenchRadius(
                BigInteger.ZERO, 8.0, 10.0, 4.0));
    }

    @Test
    void shouldDestroyBlockMatchesBlastPressureModel() {
        // No resistance: destroyed across the whole radius
        assertTrue(QuantumBatteryState.shouldDestroyBlock(64.0, 63.0, 0.0F));
        // Stone (resistance 6): only destroyed near the center
        assertTrue(QuantumBatteryState.shouldDestroyBlock(64.0, 10.0, 6.0F));
        assertFalse(QuantumBatteryState.shouldDestroyBlock(64.0, 21.0, 6.0F));
        // Bedrock-like resistance (3.6e6): never destroyed, including at the exact center
        assertFalse(QuantumBatteryState.shouldDestroyBlock(500.0, 0.0, 3_600_000.0F));
        assertFalse(QuantumBatteryState.shouldDestroyBlock(500.0, 0.5, 3_600_000.0F));
        // Small blast cannot penetrate stone beyond ~2.5 blocks (16 - 3*6.3 < 0)
        assertFalse(QuantumBatteryState.shouldDestroyBlock(8.0, 3.0, 6.0F));
    }

    @Test
    void explosionDamageFollowsVanillaFalloff() {
        // Center: (1+1)/2 * 7 * range + 1 = 7*range + 1
        assertEquals(113.0F, QuantumBatteryState.explosionDamage(16.0, 0.0));
        // Half distance: falloff 0.5 -> 0.375 * 7 * 16 + 1 = 43
        assertEquals(43.0F, QuantumBatteryState.explosionDamage(16.0, 8.0));
        // Edge: falloff 0 -> 1
        assertEquals(1.0F, QuantumBatteryState.explosionDamage(16.0, 16.0));
        // Beyond range clamps to the edge damage
        assertEquals(1.0F, QuantumBatteryState.explosionDamage(16.0, 32.0));
    }
}
