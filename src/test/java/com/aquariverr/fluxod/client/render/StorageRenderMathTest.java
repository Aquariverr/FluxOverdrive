package com.aquariverr.fluxod.client.render;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StorageRenderMathTest {

    private static final long ENDER_CAPACITY = 64_000_000_000L;

    @Test
    void omitsTopAtAndImmediatelyBelowFullCapacity() {
        float fullHeight = StorageRenderMath.calculateHeight(
                ENDER_CAPACITY, ENDER_CAPACITY);
        float nearlyFullHeight = StorageRenderMath.calculateHeight(
                ENDER_CAPACITY - 1, ENDER_CAPACITY);

        assertEquals(StorageRenderMath.HEIGHT, fullHeight);
        assertFalse(StorageRenderMath.shouldRenderTop(fullHeight));
        assertFalse(StorageRenderMath.shouldRenderTop(nearlyFullHeight));
    }

    @Test
    void omitsTopAtAndImmediatelyAboveEmptyCapacity() {
        float minimumHeight = StorageRenderMath.calculateHeight(1, ENDER_CAPACITY);
        long firstClearEnergy = (long) Math.ceil(
                ENDER_CAPACITY * StorageRenderMath.CAP_CLEARANCE * 2 / StorageRenderMath.HEIGHT);
        float clearHeight = StorageRenderMath.calculateHeight(firstClearEnergy, ENDER_CAPACITY);

        assertFalse(StorageRenderMath.shouldRenderTop(minimumHeight));
        assertTrue(StorageRenderMath.shouldRenderTop(clearHeight));
    }

    @Test
    void rendersTopWhenItIsClearOfTheFrame() {
        float halfHeight = StorageRenderMath.calculateHeight(
                ENDER_CAPACITY / 2, ENDER_CAPACITY);
        float ninetyNinePercentHeight = StorageRenderMath.calculateHeight(
                ENDER_CAPACITY * 99 / 100, ENDER_CAPACITY);

        assertEquals(StorageRenderMath.HEIGHT / 2, halfHeight);
        assertTrue(StorageRenderMath.shouldRenderTop(halfHeight));
        assertTrue(StorageRenderMath.shouldRenderTop(ninetyNinePercentHeight));
    }

    @Test
    void clampsOverfilledAndInvalidValues() {
        assertEquals(StorageRenderMath.HEIGHT,
                StorageRenderMath.calculateHeight(Long.MAX_VALUE, ENDER_CAPACITY));
        assertEquals(0, StorageRenderMath.calculateHeight(1, 0));
        assertEquals(0, StorageRenderMath.calculateHeight(0, ENDER_CAPACITY));
        assertFalse(StorageRenderMath.shouldRenderTop(0));
    }
}
