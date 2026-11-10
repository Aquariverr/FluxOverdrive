package com.aquariverr.fluxod.multiblock;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class QuantumBatteryStructureTest {

    @Test
    void usesConfiguredRadiiAndTubeDiameters() {
        assertEquals(3, QuantumBatteryStructure.layer(1).radius());
        assertEquals(1, QuantumBatteryStructure.layer(1).tubeDiameter());
        assertEquals(7, QuantumBatteryStructure.layer(2).radius());
        assertEquals(3, QuantumBatteryStructure.layer(2).tubeDiameter());
        assertEquals(13, QuantumBatteryStructure.layer(3).radius());
        assertEquals(5, QuantumBatteryStructure.layer(3).tubeDiameter());
        assertEquals(42, QuantumBatteryStructure.layer(1).cells().size());
        assertEquals(846, QuantumBatteryStructure.layer(2).cells().size());
        assertEquals(4410, QuantumBatteryStructure.layer(3).cells().size());
        assertEquals(new QuantumBatteryStructure.MaterialCounts(6, 36),
                QuantumBatteryStructure.materialCounts(1));
        assertEquals(new QuantumBatteryStructure.MaterialCounts(162, 684),
                QuantumBatteryStructure.materialCounts(2));
        assertEquals(new QuantumBatteryStructure.MaterialCounts(558, 3852),
                QuantumBatteryStructure.materialCounts(3));
    }

    @Test
    void tierThreeFitsWithinThirtyOneBlocks() {
        QuantumBatteryStructure.Layer layer = QuantumBatteryStructure.layer(3);
        int maximumOffset = layer.cells().stream()
                .flatMapToInt(cell -> java.util.stream.IntStream.of(
                        Math.abs(cell.offset().x()), Math.abs(cell.offset().y()), Math.abs(cell.offset().z())))
                .max()
                .orElseThrow();

        assertEquals(15, maximumOffset);
    }

    @Test
    void axisIntersectionsUseCasingAndArcsUseGlass() {
        QuantumBatteryStructure.Layer layer = QuantumBatteryStructure.layer(1);

        assertTrue(hasCell(layer, new QuantumBatteryStructure.Offset(3, 0, 0),
                QuantumBatteryStructure.CellType.CASING));
        assertTrue(layer.cells().stream().anyMatch(cell -> cell.type() == QuantumBatteryStructure.CellType.GLASS));
    }

    @Test
    void recordsTheAxesCoveredByEachCell() {
        QuantumBatteryStructure.Cell intersection = layerCell(
                QuantumBatteryStructure.layer(1), new QuantumBatteryStructure.Offset(3, 0, 0));

        assertTrue(intersection.isIntersection());
        assertFalse(intersection.axes().contains(QuantumBatteryStructure.Axis.X));
        assertTrue(intersection.axes().contains(QuantumBatteryStructure.Axis.Y));
        assertTrue(intersection.axes().contains(QuantumBatteryStructure.Axis.Z));
    }

    @Test
    void indexesCellsByTier() {
        assertEquals(1, QuantumBatteryStructure.tierAt(3, 0, 0));
        assertEquals(2, QuantumBatteryStructure.tierAt(7, 0, 0));
        assertEquals(3, QuantumBatteryStructure.tierAt(13, 0, 0));
        assertEquals(0, QuantumBatteryStructure.tierAt(0, 0, 0));
    }

    @Test
    void tierTwoAndThreeIntersectionsHaveThreeByThreeFootprints() {
        for (int tier : new int[]{2, 3}) {
            int radius = QuantumBatteryStructure.stabilizerSlotRadius(tier);
            QuantumBatteryStructure.Cell positiveX = layerCell(QuantumBatteryStructure.layer(tier),
                    new QuantumBatteryStructure.Offset(radius, 0, 0));
            assertEquals(9, QuantumBatteryStructure.threeByThreeStabilizerFootprint(tier, positiveX).size());
            assertTrue(QuantumBatteryStructure.supportsThreeByThreeStabilizer(tier, positiveX));
            assertTrue(QuantumBatteryStructure.supportsThreeByThreeStabilizer(
                    tier, layerCell(QuantumBatteryStructure.layer(tier),
                            new QuantumBatteryStructure.Offset(-radius, 0, 0))));
            assertTrue(QuantumBatteryStructure.supportsThreeByThreeStabilizer(
                    tier, layerCell(QuantumBatteryStructure.layer(tier),
                            new QuantumBatteryStructure.Offset(0, radius, 0))));
            assertTrue(QuantumBatteryStructure.supportsThreeByThreeStabilizer(
                    tier, layerCell(QuantumBatteryStructure.layer(tier),
                            new QuantumBatteryStructure.Offset(0, -radius, 0))));
            assertTrue(QuantumBatteryStructure.supportsThreeByThreeStabilizer(
                    tier, layerCell(QuantumBatteryStructure.layer(tier),
                            new QuantumBatteryStructure.Offset(0, 0, radius))));
            assertTrue(QuantumBatteryStructure.supportsThreeByThreeStabilizer(
                    tier, layerCell(QuantumBatteryStructure.layer(tier),
                            new QuantumBatteryStructure.Offset(0, 0, -radius))));
        }

        assertTrue(QuantumBatteryStructure.layer(1).cells().stream()
                .filter(QuantumBatteryStructure.Cell::isIntersection)
                .noneMatch(cell -> QuantumBatteryStructure.supportsThreeByThreeStabilizer(1, cell)));
    }

    @Test
    void onlyAxisCenteredIntersectionCellsAreStabilizerSlots() {
        assertEquals(3, QuantumBatteryStructure.stabilizerSlotRadius(1));
        assertEquals(6, QuantumBatteryStructure.stabilizerSlotRadius(2));
        assertEquals(11, QuantumBatteryStructure.stabilizerSlotRadius(3));
        for (int tier = 1; tier <= 3; tier++) {
            int currentTier = tier;
            int radius = QuantumBatteryStructure.stabilizerSlotRadius(tier);
            Set<QuantumBatteryStructure.Offset> expected = Set.of(
                    new QuantumBatteryStructure.Offset(radius, 0, 0),
                    new QuantumBatteryStructure.Offset(-radius, 0, 0),
                    new QuantumBatteryStructure.Offset(0, radius, 0),
                    new QuantumBatteryStructure.Offset(0, -radius, 0),
                    new QuantumBatteryStructure.Offset(0, 0, radius),
                    new QuantumBatteryStructure.Offset(0, 0, -radius));
            Set<QuantumBatteryStructure.Offset> actual = QuantumBatteryStructure.stabilizerSlots(tier).stream()
                    .map(QuantumBatteryStructure.Cell::offset)
                    .collect(java.util.stream.Collectors.toSet());

            assertEquals(expected, actual);
            assertTrue(QuantumBatteryStructure.stabilizerSlots(tier).stream()
                    .allMatch(cell -> QuantumBatteryStructure.isStabilizerSlot(currentTier, cell)));
        }
    }

    @Test
    void nonCenteredThickIntersectionCellsCannotHostInterfaces() {
        for (int tier : new int[]{2, 3}) {
            QuantumBatteryStructure.Cell nonCenter = QuantumBatteryStructure.intersections(tier).stream()
                    .filter(cell -> !QuantumBatteryStructure.isStabilizerSlot(tier, cell))
                    .findFirst()
                    .orElseThrow();

            assertTrue(QuantumBatteryStructure.threeByThreeStabilizerFootprint(tier, nonCenter).isEmpty());
            assertFalse(QuantumBatteryStructure.supportsThreeByThreeStabilizer(tier, nonCenter));
        }
    }

    @Test
    void layersDoNotShareBlocks() {
        Set<QuantumBatteryStructure.Offset> occupied = new HashSet<>();
        for (QuantumBatteryStructure.Layer layer : QuantumBatteryStructure.layers()) {
            for (QuantumBatteryStructure.Cell cell : layer.cells()) {
                assertTrue(occupied.add(cell.offset()), () -> "Overlapping cell " + cell.offset());
            }
        }
        assertFalse(occupied.isEmpty());
    }

    private static boolean hasCell(QuantumBatteryStructure.Layer layer, QuantumBatteryStructure.Offset offset,
                                   QuantumBatteryStructure.CellType type) {
        return layer.cells().stream().anyMatch(cell -> cell.offset().equals(offset) && cell.type() == type);
    }

    private static QuantumBatteryStructure.Cell layerCell(QuantumBatteryStructure.Layer layer,
                                                           QuantumBatteryStructure.Offset offset) {
        return layer.cells().stream()
                .filter(cell -> cell.offset().equals(offset))
                .findFirst()
                .orElseThrow();
    }
}
