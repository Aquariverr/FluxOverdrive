package com.aquariverr.fluxod.miniature.model;

import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FactoryRulesTest {
    private static final ResourceKey ORE = key(ResourceKind.ITEM, "ore");
    private static final ResourceKey FLUID = key(ResourceKind.FLUID, "fluid");
    private static final ResourceKey CHEMICAL = key(ResourceKind.CHEMICAL, "chemical");
    private static final BigInteger MAX_QUANTITY = BigInteger.ONE.shiftLeft(256).subtract(BigInteger.ONE);

    private static final int[][] EXPECTED_PARALLELISM = {
            {1001, 1008, 1027, 1064, 1125, 1216, 1343, 1512, 1729, 2000},
            {730, 737, 756, 793, 854, 945, 1072, 1241, 1458, 1729},
            {513, 520, 539, 576, 637, 728, 855, 1024, 1241, 1512},
            {344, 351, 370, 407, 468, 559, 686, 855, 1072, 1343},
            {217, 224, 243, 280, 341, 432, 559, 728, 945, 1216},
            {126, 133, 152, 189, 250, 341, 468, 637, 854, 1125},
            {65, 72, 91, 128, 189, 280, 407, 576, 793, 1064},
            {28, 35, 54, 91, 152, 243, 370, 539, 756, 1027},
            {9, 16, 35, 72, 133, 224, 351, 520, 737, 1008},
            {2, 9, 28, 65, 126, 217, 344, 513, 730, 1001}
    };

    @Test
    void stabilityUsesCeilingAtEveryBandBoundaryAndClampsExtremeReadings() {
        assertEquals(1, FactoryRules.stabilityBand(Integer.MIN_VALUE));
        assertEquals(1, FactoryRules.stabilityBand(-1));
        assertEquals(1, FactoryRules.stabilityBand(0));
        assertEquals(1, FactoryRules.stabilityBand(1));
        for (int band = 1; band <= 10; band++) {
            int threshold = band * 1_000;
            assertEquals(band, FactoryRules.stabilityBand(threshold - 1));
            assertEquals(band, FactoryRules.stabilityBand(threshold));
            assertEquals(Math.min(10, band + 1), FactoryRules.stabilityBand(threshold + 1));
        }
        assertEquals(10, FactoryRules.stabilityBand(Integer.MAX_VALUE));
    }

    @Test
    void temperatureUsesKelvinAndPreservesEveryAdjacentFloatingPointBoundary() {
        assertEquals(1, FactoryRules.temperatureBand(-Double.MAX_VALUE));
        assertEquals(1, FactoryRules.temperatureBand(-1));
        assertEquals(1, FactoryRules.temperatureBand(-0.0));
        assertEquals(1, FactoryRules.temperatureBand(0));
        assertEquals(1, FactoryRules.temperatureBand(Double.MIN_VALUE));
        for (int band = 1; band <= 10; band++) {
            double threshold = band * 4_000.0;
            assertEquals(band, FactoryRules.temperatureBand(Math.nextDown(threshold)));
            assertEquals(band, FactoryRules.temperatureBand(threshold));
            assertEquals(Math.min(10, band + 1), FactoryRules.temperatureBand(Math.nextUp(threshold)));
        }
        assertEquals(2, FactoryRules.temperatureBand(4_000.001));
        assertEquals(10, FactoryRules.temperatureBand(36_000.001));
        assertEquals(10, FactoryRules.temperatureBand(50_000));
        assertEquals(10, FactoryRules.temperatureBand(Double.MAX_VALUE));
    }

    @Test
    void rejectsAllNonFiniteTemperatures() {
        for (double temperature : new double[]{Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY}) {
            assertDiagnostic(FactoryDiagnostic.Code.INVALID_ENVIRONMENT,
                    () -> FactoryRules.temperatureBand(temperature));
            assertDiagnostic(FactoryDiagnostic.Code.INVALID_ENVIRONMENT,
                    () -> FactoryRules.environment(5_000, temperature));
        }
    }

    @Test
    void allOneHundredEnvironmentCombinationsMatchIndependentExpectedValues() {
        for (int n = 1; n <= 10; n++) {
            for (int m = 1; m <= 10; m++) {
                int parallelism = FactoryRules.parallelism(n, m);
                assertEquals(EXPECTED_PARALLELISM[n - 1][m - 1], parallelism, "n=" + n + ", m=" + m);
                assertTrue(parallelism >= 2 && parallelism <= 2_000);
                if (m < 10) {
                    assertTrue(parallelism < FactoryRules.parallelism(n, m + 1));
                }
                if (n < 10) {
                    assertTrue(parallelism > FactoryRules.parallelism(n + 1, m));
                }
            }
        }
    }

    @Test
    void environmentRecordsValidatedBandsAndDerivedParallelism() {
        FactoryRules.Environment environment = FactoryRules.environment(5_000, 8_000);
        assertEquals(5, environment.stabilityBand());
        assertEquals(2, environment.temperatureBand());
        assertEquals(224, environment.parallelism());
        assertEquals(new FactoryRules.Environment(1, 1), FactoryRules.environment(-1, -1));
        assertEquals(new FactoryRules.Environment(10, 10),
                FactoryRules.environment(Integer.MAX_VALUE, Double.MAX_VALUE));
    }

    @Test
    void rejectsBandsOutsideOneThroughTenIncludingExtremeIntegers() {
        for (int invalid : new int[]{Integer.MIN_VALUE, -1, 0, 11, Integer.MAX_VALUE}) {
            assertDiagnostic(FactoryDiagnostic.Code.INVALID_ENVIRONMENT,
                    () -> FactoryRules.parallelism(invalid, 1));
            assertDiagnostic(FactoryDiagnostic.Code.INVALID_ENVIRONMENT,
                    () -> FactoryRules.parallelism(1, invalid));
            assertDiagnostic(FactoryDiagnostic.Code.INVALID_ENVIRONMENT,
                    () -> new FactoryRules.Environment(invalid, 1));
            assertDiagnostic(FactoryDiagnostic.Code.INVALID_ENVIRONMENT,
                    () -> new FactoryRules.Environment(1, invalid));
        }
    }

    @Test
    void mixedOutputsUseLargestRequiredFactorForEveryInputAndOutput() {
        List<ResourceAmount> inputs = List.of(amount(ORE, 1));
        List<ResourceAmount> outputs = List.of(amount(FLUID, 250), amount(CHEMICAL, 40));
        BigInteger k = FactoryRules.normalizationFactor(outputs);

        assertEquals(BigInteger.valueOf(25), k);
        assertEquals(List.of(amount(ORE, 25)), FactoryRules.scaleForMachines(inputs, k, 1));
        assertEquals(List.of(amount(FLUID, 6_250), amount(CHEMICAL, 1_000)),
                FactoryRules.scaleForMachines(outputs, k, 1));
    }

    @Test
    void mergesExactDuplicateOutputsBeforeChoosingNormalizationFactor() {
        List<ResourceAmount> outputs = List.of(amount(FLUID, 500), amount(FLUID, 500));

        assertEquals(BigInteger.ONE, FactoryRules.normalizationFactor(outputs));
        assertEquals(List.of(amount(FLUID, 1_000)),
                FactoryRules.scaleForMachines(outputs, BigInteger.ONE, 1));
        assertEquals(BigInteger.ONE, FactoryRules.normalizationFactor(
                Collections.nCopies(100, amount(FLUID, 10))));
    }

    @Test
    void distinctComponentsAndResourceKindsNeverMerge() {
        CompoundTag componentsA = new CompoundTag();
        componentsA.putInt("test:variant", 1);
        CompoundTag componentsB = new CompoundTag();
        componentsB.putInt("test:variant", 2);
        ResourceKey fluidA = new ResourceKey(ResourceKind.FLUID, "test:variant", componentsA);
        ResourceKey fluidB = new ResourceKey(ResourceKind.FLUID, "test:variant", componentsB);
        ResourceKey fluidWithoutComponents = new ResourceKey(ResourceKind.FLUID, "test:variant");
        ResourceKey chemical = new ResourceKey(ResourceKind.CHEMICAL, "test:variant");
        List<ResourceAmount> outputs = List.of(amount(fluidA, 500), amount(fluidB, 500),
                amount(fluidWithoutComponents, 500), amount(chemical, 500));

        assertEquals(BigInteger.TWO, FactoryRules.normalizationFactor(outputs));
        assertEquals(List.of(amount(fluidA, 1_000), amount(fluidB, 1_000),
                        amount(fluidWithoutComponents, 1_000), amount(chemical, 1_000)),
                FactoryRules.scaleForMachines(outputs, BigInteger.TWO, 1));
    }

    @Test
    void normalizationUsesCeilingWithoutForcingWholeBucketMultiples() {
        assertEquals(BigInteger.ONE, FactoryRules.normalizationFactor(List.of(amount(FLUID, 1_500), amount(ORE, 1))));
        assertEquals(BigInteger.ONE, FactoryRules.normalizationFactor(List.of(amount(CHEMICAL, 1_001))));
        assertEquals(BigInteger.TWO, FactoryRules.normalizationFactor(List.of(amount(FLUID, 999))));
        assertEquals(BigInteger.valueOf(3), FactoryRules.normalizationFactor(List.of(amount(FLUID, 334))));
        assertEquals(BigInteger.valueOf(4), FactoryRules.normalizationFactor(List.of(amount(FLUID, 333))));
        assertEquals(BigInteger.valueOf(1_000), FactoryRules.normalizationFactor(List.of(amount(CHEMICAL, 1))));
    }

    @Test
    void doublingMachineCountDoublesItsResourcesAndEnergyWithoutChangingAnotherCell() {
        List<ResourceAmount> inputs = List.of(amount(ORE, 1));
        List<ResourceAmount> outputs = List.of(amount(FLUID, 250));
        BigInteger k = FactoryRules.normalizationFactor(outputs);
        List<ResourceAmount> otherCell = FactoryRules.scaleForMachines(List.of(amount(CHEMICAL, 40)),
                BigInteger.valueOf(25), 1);

        assertEquals(List.of(amount(ORE, 4)), FactoryRules.scaleForMachines(inputs, k, 1));
        assertEquals(List.of(amount(ORE, 8)), FactoryRules.scaleForMachines(inputs, k, 2));
        assertEquals(List.of(amount(FLUID, 1_000)), FactoryRules.scaleForMachines(outputs, k, 1));
        assertEquals(List.of(amount(FLUID, 2_000)), FactoryRules.scaleForMachines(outputs, k, 2));
        assertEquals(BigInteger.valueOf(40_000_000), FactoryRules.energyPerTick(2));
        assertEquals(FactoryRules.energyPerTick(1).multiply(BigInteger.TWO), FactoryRules.energyPerTick(2));
        assertEquals(List.of(amount(CHEMICAL, 1_000)), otherCell);
        assertEquals(List.of(amount(ORE, 1)), inputs);
        assertEquals(List.of(amount(FLUID, 250)), outputs);
    }

    @Test
    void scaledResourcesMergeInputsAndReturnAnImmutableList() {
        List<ResourceAmount> resources = new ArrayList<>(List.of(amount(ORE, 2), amount(ORE, 3), amount(FLUID, 4)));
        List<ResourceAmount> scaled = FactoryRules.scaleForMachines(resources, BigInteger.valueOf(3), 2);

        assertEquals(List.of(amount(ORE, 30), amount(FLUID, 24)), scaled);
        resources.clear();
        assertEquals(2, scaled.size());
        assertThrows(UnsupportedOperationException.class, () -> scaled.add(amount(ORE, 1)));
        assertEquals(List.of(), FactoryRules.scaleForMachines(List.of(), BigInteger.ONE, 1));
        assertDiagnostic(FactoryDiagnostic.Code.EMPTY_OUTPUTS, () -> FactoryRules.normalizationFactor(List.of()));
    }

    @Test
    void allowsExactlySixtyFourDistinctResourcesAndRejectsOneMore() {
        List<ResourceAmount> resources = new ArrayList<>();
        for (int i = 0; i < 64; i++) {
            resources.add(amount(key(ResourceKind.ITEM, "output_" + i), 1));
        }
        assertEquals(BigInteger.ONE, FactoryRules.normalizationFactor(resources));
        assertEquals(64, FactoryRules.scaleForMachines(resources, BigInteger.ONE, 64).size());
        resources.add(amount(key(ResourceKind.ITEM, "output_64"), 1));

        assertDiagnostic(FactoryDiagnostic.Code.RESOURCE_LIMIT_EXCEEDED,
                () -> FactoryRules.normalizationFactor(resources));
        assertDiagnostic(FactoryDiagnostic.Code.RESOURCE_LIMIT_EXCEEDED,
                () -> FactoryRules.scaleForMachines(resources, BigInteger.ONE, 1));
    }

    @Test
    void quantityCalculationsRemainExactBeyondLongRangeAndAtTheHardLimit() {
        BigInteger beyondLong = BigInteger.valueOf(Long.MAX_VALUE).add(BigInteger.ONE);
        assertEquals(List.of(new ResourceAmount(ORE, beyondLong.multiply(BigInteger.valueOf(128)))),
                FactoryRules.scaleForMachines(List.of(new ResourceAmount(ORE, beyondLong)), BigInteger.TWO, 64));
        List<ResourceAmount> maximum = List.of(new ResourceAmount(FLUID, MAX_QUANTITY));
        assertEquals(BigInteger.ONE, FactoryRules.normalizationFactor(maximum));
        assertEquals(maximum, FactoryRules.scaleForMachines(maximum, BigInteger.ONE, 1));
    }

    @Test
    void rejectsQuantityOverflowFromEitherMergingOrScaling() {
        List<ResourceAmount> mergedOverflow = List.of(new ResourceAmount(ORE, MAX_QUANTITY), amount(ORE, 1));
        assertDiagnostic(FactoryDiagnostic.Code.QUANTITY_LIMIT_EXCEEDED,
                () -> FactoryRules.normalizationFactor(mergedOverflow));
        assertDiagnostic(FactoryDiagnostic.Code.QUANTITY_LIMIT_EXCEEDED,
                () -> FactoryRules.scaleForMachines(mergedOverflow, BigInteger.ONE, 1));
        assertDiagnostic(FactoryDiagnostic.Code.QUANTITY_LIMIT_EXCEEDED,
                () -> FactoryRules.scaleForMachines(List.of(new ResourceAmount(ORE, MAX_QUANTITY)), BigInteger.TWO, 1));
        assertDiagnostic(FactoryDiagnostic.Code.QUANTITY_LIMIT_EXCEEDED,
                () -> FactoryRules.scaleForMachines(List.of(new ResourceAmount(ORE, MAX_QUANTITY)), BigInteger.ONE, 2));
    }

    @Test
    void rejectsNonPositiveAndOversizedNormalizationFactors() {
        for (BigInteger invalid : List.of(BigInteger.ZERO, BigInteger.ONE.negate())) {
            assertDiagnostic(FactoryDiagnostic.Code.INVALID_QUANTITY,
                    () -> FactoryRules.scaleForMachines(List.of(amount(ORE, 1)), invalid, 1));
        }
        assertDiagnostic(FactoryDiagnostic.Code.QUANTITY_LIMIT_EXCEEDED,
                () -> FactoryRules.scaleForMachines(List.of(amount(ORE, 1)), MAX_QUANTITY.add(BigInteger.ONE), 1));
    }

    @Test
    void energyChargesOnlyActualMachinesAcrossNormalizationAndParallelismExtremes() {
        List<ResourceAmount> rawOutput = List.of(amount(FLUID, 1));
        BigInteger k = FactoryRules.normalizationFactor(rawOutput);
        BigInteger normalizedOutput = FactoryRules.scaleForMachines(rawOutput, k, 1).getFirst().amount();
        int lowParallelism = FactoryRules.environment(10_000, 4_000).parallelism();
        int highParallelism = FactoryRules.environment(0, 40_000).parallelism();

        assertEquals(BigInteger.valueOf(1_000), k);
        assertEquals(BigInteger.valueOf(2_000), normalizedOutput.multiply(BigInteger.valueOf(lowParallelism)));
        assertEquals(BigInteger.valueOf(2_000_000), normalizedOutput.multiply(BigInteger.valueOf(highParallelism)));
        assertEquals(BigInteger.valueOf(20_000_000), FactoryRules.energyPerTick(1));
        assertEquals(BigInteger.valueOf(400_000_000), FactoryRules.energyPerBatch(1));
        assertEquals(20, FactoryRules.BATCH_TICKS);
    }

    @Test
    void maximumMachineCountCannotOverflowIntegerEnergyAndEmptyLayoutCostsZero() {
        assertEquals(BigInteger.ZERO, FactoryRules.energyPerTick(0));
        assertEquals(BigInteger.ZERO, FactoryRules.energyPerBatch(0));
        assertEquals(new BigInteger("154880000000"), FactoryRules.energyPerTick(7_744));
        assertEquals(new BigInteger("3097600000000"), FactoryRules.energyPerBatch(7_744));
        assertEquals(BigInteger.valueOf(440_000_000), FactoryRules.energyPerTick(22));
        assertEquals(BigInteger.valueOf(8_800_000_000L), FactoryRules.energyPerBatch(22));
    }

    @Test
    void rejectsInvalidCellAndFactoryMachineCounts() {
        for (int invalid : new int[]{Integer.MIN_VALUE, -1, 0, 65, Integer.MAX_VALUE}) {
            assertDiagnostic(FactoryDiagnostic.Code.INVALID_MACHINE_COUNT,
                    () -> FactoryRules.scaleForMachines(List.of(amount(ORE, 1)), BigInteger.ONE, invalid));
        }
        for (int invalid : new int[]{Integer.MIN_VALUE, -1, 7_745, Integer.MAX_VALUE}) {
            assertDiagnostic(FactoryDiagnostic.Code.INVALID_MACHINE_COUNT, () -> FactoryRules.energyPerTick(invalid));
            assertDiagnostic(FactoryDiagnostic.Code.INVALID_MACHINE_COUNT, () -> FactoryRules.energyPerBatch(invalid));
        }
    }

    private static ResourceKey key(ResourceKind kind, String path) {
        return new ResourceKey(kind, "test:" + path);
    }

    private static ResourceAmount amount(ResourceKey key, long amount) {
        return new ResourceAmount(key, BigInteger.valueOf(amount));
    }

    private static void assertDiagnostic(FactoryDiagnostic.Code code, Runnable action) {
        ModelValidationException exception = assertThrows(ModelValidationException.class, action::run);
        assertEquals(code, exception.diagnostic().code());
    }
}
