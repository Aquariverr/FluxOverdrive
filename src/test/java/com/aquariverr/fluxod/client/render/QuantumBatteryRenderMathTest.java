package com.aquariverr.fluxod.client.render;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class QuantumBatteryRenderMathTest {

    @Test
    void anglesUsePartialTickInterpolation() {
        assertEquals(0.0F, QuantumBatteryRenderMath.outerAngle(0, 0.0F));
        assertEquals(QuantumBatteryRenderMath.OUTER_SPEED * 10.5F,
                QuantumBatteryRenderMath.outerAngle(10, 0.5F));
        assertEquals(QuantumBatteryRenderMath.MIDDLE_SPEED * 10.5F,
                QuantumBatteryRenderMath.middleAngle(10, 0.5F));
    }

    @Test
    void tiersUseInnerToOuterSpeedOrder() {
        float outer = QuantumBatteryRenderMath.outerAngle(10, 0.5F);
        float middle = QuantumBatteryRenderMath.middleAngle(10, 0.5F);
        float inner = QuantumBatteryRenderMath.innerAngle(10, 0.5F);

        assertEquals(inner, QuantumBatteryRenderMath.tierAngle(1, outer, middle, inner));
        assertEquals(middle, QuantumBatteryRenderMath.tierAngle(2, outer, middle, inner));
        assertEquals(outer, QuantumBatteryRenderMath.tierAngle(3, outer, middle, inner));
        assertTrue(QuantumBatteryRenderMath.OUTER_SPEED < QuantumBatteryRenderMath.MIDDLE_SPEED);
        assertTrue(QuantumBatteryRenderMath.MIDDLE_SPEED < QuantumBatteryRenderMath.INNER_SPEED);
    }

    @Test
    void independentRingsCoverAllWorldHemispheres() {
        for (long gameTime = 0; gameTime <= 2400; gameTime += 40) {
            float outer = QuantumBatteryRenderMath.outerAngle(gameTime, 0.5F);
            float middle = QuantumBatteryRenderMath.middleAngle(gameTime, 0.5F);
            float inner = QuantumBatteryRenderMath.innerAngle(gameTime, 0.5F);
            for (int tier = 1; tier <= 3; tier++) {
                assertTrue(QuantumBatteryRenderMath.hasWorldHemisphereCoverage(
                        tier, outer, middle, inner),
                        "Uncovered hemisphere at tier " + tier + ", tick " + gameTime);
            }
        }
    }

    @Test
    void assembledInterfaceKeepsSkyLightAndFloorsOnlyBlockLight() {
        assertPackedLight(8, 0, QuantumBatteryRenderMath.withMinimumBlockLight(packLight(0, 0), 8));
        assertPackedLight(8, 12, QuantumBatteryRenderMath.withMinimumBlockLight(packLight(5, 12), 8));
        assertPackedLight(11, 3, QuantumBatteryRenderMath.withMinimumBlockLight(packLight(11, 3), 8));
        assertPackedLight(15, 15, QuantumBatteryRenderMath.withMinimumBlockLight(packLight(15, 15), 8));
    }

    @Test
    void worldSphereGrowsWithGuiEnergyScaleAndFitsInsideTheInnerRing() {
        float empty = QuantumBatteryRenderMath.worldSphereRadius(0.0, 1.0);
        float half = QuantumBatteryRenderMath.worldSphereRadius(0.5, 1.0);
        float full = QuantumBatteryRenderMath.worldSphereRadius(1.0, 1.0);
        float unstableFull = QuantumBatteryRenderMath.worldSphereRadius(1.0, 1.10);

        assertTrue(empty < half);
        assertTrue(half < full);
        assertEquals((float) (Math.sqrt(4.5) - 0.15),
                QuantumBatteryRenderMath.worldEffectMaxExtent(), 1.0E-6F);
        assertTrue(unstableFull * 1.12F < QuantumBatteryRenderMath.worldEffectMaxExtent());
    }

    @Test
    void worldSphereInputsAreClamped() {
        assertEquals(QuantumBatteryRenderMath.worldSphereRadius(0.0, 0.90),
                QuantumBatteryRenderMath.worldSphereRadius(-10.0, 0.0));
        assertEquals(QuantumBatteryRenderMath.worldSphereRadius(1.0, 1.10),
                QuantumBatteryRenderMath.worldSphereRadius(10.0, 5.0));
    }

    @Test
    void outerRingStreamsAreLongerAndEachTierHasVariation() {
        double tierOneMinimum = QuantumBatteryRenderMath.ringStreamLength(1, 0.0);
        double tierOneMaximum = QuantumBatteryRenderMath.ringStreamLength(1, 1.0);
        double tierTwoMinimum = QuantumBatteryRenderMath.ringStreamLength(2, 0.0);
        double tierTwoMaximum = QuantumBatteryRenderMath.ringStreamLength(2, 1.0);
        double tierThreeMinimum = QuantumBatteryRenderMath.ringStreamLength(3, 0.0);
        double tierThreeMaximum = QuantumBatteryRenderMath.ringStreamLength(3, 1.0);

        assertTrue(tierOneMinimum < tierOneMaximum);
        assertTrue(tierOneMaximum < tierTwoMinimum);
        assertTrue(tierTwoMaximum < tierThreeMinimum);
        assertTrue(tierThreeMinimum < tierThreeMaximum);
        assertTrue(QuantumBatteryRenderMath.ringStreamWidth(2, 0.0)
                < QuantumBatteryRenderMath.ringStreamWidth(2, 1.0));
    }

    @Test
    void advancedConstraintBeamIsThickerAndBothBeamsTaperTowardTheController() {
        double standardInterface = QuantumBatteryRenderMath.constraintBeamInterfaceWidth(false);
        double standardController = QuantumBatteryRenderMath.constraintBeamControllerWidth(false);
        double advancedInterface = QuantumBatteryRenderMath.constraintBeamInterfaceWidth(true);
        double advancedController = QuantumBatteryRenderMath.constraintBeamControllerWidth(true);

        assertTrue(standardInterface > standardController);
        assertTrue(advancedInterface > advancedController);
        assertTrue(advancedInterface > standardInterface);
        assertTrue(advancedController > standardController);
    }

    @Test
    void constraintBeamStopsAtTheFirstIntersectionBounds() {
        double hit = QuantumBatteryRenderMath.segmentAabbIntersection(
                3.0, 0.0, 0.0, 0.0, 0.0, 0.0,
                1.0, -0.5, -0.5, 2.0, 0.5, 0.5);
        double miss = QuantumBatteryRenderMath.segmentAabbIntersection(
                3.0, 2.0, 0.0, 0.0, 2.0, 0.0,
                1.0, -0.5, -0.5, 2.0, 0.5, 0.5);

        assertEquals(1.0 / 3.0, hit, 1.0E-9);
        assertEquals(Double.POSITIVE_INFINITY, miss);
    }

    @Test
    void taperedConstraintBeamUsesItsRadiusAtTheHitPosition() {
        double nearInterfaceHit = QuantumBatteryRenderMath.taperedSegmentAabbIntersection(
                3.0, 0.62, 0.0, 0.0, 0.62, 0.0,
                0.20, 0.05,
                1.0, -0.5, -0.5, 2.0, 0.5, 0.5);
        double nearControllerMiss = QuantumBatteryRenderMath.taperedSegmentAabbIntersection(
                3.0, 0.595, 0.0, 0.0, 0.595, 0.0,
                0.20, 0.02,
                0.2, -0.5, -0.5, 0.8, 0.5, 0.5);

        assertTrue(Double.isFinite(nearInterfaceHit));
        assertEquals(Double.POSITIVE_INFINITY, nearControllerMiss);
    }

    private static int packLight(int block, int sky) {
        return sky << 20 | block << 4;
    }

    private static void assertPackedLight(int block, int sky, int packed) {
        assertEquals(block << 4, packed & 0xFFFF);
        assertEquals(sky << 4, packed >>> 16 & 0xFFFF);
    }

}
