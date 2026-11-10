package com.aquariverr.fluxod.block.entity;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class QuantumBatteryQuenchProcessTest {

    @Test
    void sphereUsesTheSameRadiusOnEveryAxis() {
        assertTrue(QuantumBatteryQuenchGeometry.containsBlockCenter(
                0.5, 64.5, 0.5, 3.0, 3, 64, 0));
        assertTrue(QuantumBatteryQuenchGeometry.containsBlockCenter(
                0.5, 64.5, 0.5, 3.0, 0, 67, 0));
        assertTrue(QuantumBatteryQuenchGeometry.containsBlockCenter(
                0.5, 64.5, 0.5, 3.0, 0, 64, 3));

        assertFalse(QuantumBatteryQuenchGeometry.containsBlockCenter(
                0.5, 64.5, 0.5, 3.0, 4, 64, 0));
        assertFalse(QuantumBatteryQuenchGeometry.containsBlockCenter(
                0.5, 64.5, 0.5, 3.0, 0, 68, 0));
        assertFalse(QuantumBatteryQuenchGeometry.containsBlockCenter(
                0.5, 64.5, 0.5, 3.0, 0, 64, 4));
    }

    @Test
    void diagonalBoundaryUsesEuclideanDistance() {
        assertTrue(QuantumBatteryQuenchGeometry.containsBlockCenter(
                0.5, 0.5, 0.5, Math.sqrt(3.0), 1, 1, 1));
        assertFalse(QuantumBatteryQuenchGeometry.containsBlockCenter(
                0.5, 0.5, 0.5, 1.7, 1, 1, 1));
    }

    @Test
    void boundsUseBlockCentersAcrossNegativeCoordinatesAndWorldClipping() {
        assertEquals(-8, QuantumBatteryQuenchGeometry.minBlockCoordinate(-4.5, 3.0));
        assertEquals(-2, QuantumBatteryQuenchGeometry.maxBlockCoordinate(-4.5, 3.0));
        assertEquals(-64, Math.max(-64,
                QuantumBatteryQuenchGeometry.minBlockCoordinate(-60.5, 8.0)));
        assertEquals(319, Math.min(319,
                QuantumBatteryQuenchGeometry.maxBlockCoordinate(316.5, 8.0)));
    }

    @Test
    void sectionIntersectionRejectsBoxesOutsideTheSphere() {
        assertTrue(QuantumBatteryQuenchGeometry.sphereIntersectsBlockBox(
                0.5, 0.5, 0.5, 8.0, -4, -4, -4, 4, 4, 4));
        assertFalse(QuantumBatteryQuenchGeometry.sphereIntersectsBlockBox(
                0.5, 0.5, 0.5, 8.0, 16, -4, 16, 31, 4, 31));
    }

    @Test
    void cursorCanPauseAndResumeWithoutDuplicatesOrOmissions() {
        QuantumBatteryQuenchGeometry.BlockCursor cursor =
                new QuantumBatteryQuenchGeometry.BlockCursor(-1, 1, 5, 6, 2, 3);
        Set<Position> visited = new HashSet<>();

        for (int i = 0; i < 5; i++) {
            assertTrue(cursor.hasNext());
            assertTrue(visited.add(readAndAdvance(cursor)));
        }
        while (cursor.hasNext()) assertTrue(visited.add(readAndAdvance(cursor)));

        assertEquals(12, visited.size());
        for (int x = -1; x <= 1; x++) {
            for (int y = 5; y <= 6; y++) {
                for (int z = 2; z <= 3; z++) {
                    assertTrue(visited.contains(new Position(x, y, z)));
                }
            }
        }
    }

    private static Position readAndAdvance(QuantumBatteryQuenchGeometry.BlockCursor cursor) {
        Position result = new Position(cursor.x(), cursor.y(), cursor.z());
        cursor.advance();
        return result;
    }

    private record Position(int x, int y, int z) {}
}
