package com.aquariverr.fluxod.multiblock;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class QuantumEntanglementStructureTest {

    @Test
    void hasExpectedCellBudget() {
        assertEquals(16, QuantumEntanglementTemplate.cells().size());
        assertEquals(new QuantumEntanglementTemplate.MaterialCounts(15, 0, 1),
                QuantumEntanglementTemplate.materialCounts());
    }

    @Test
    void eachHorizontalFacingProducesUniqueCells() {
        for (int quarterTurns = 0; quarterTurns < 4; quarterTurns++) {
            Set<String> positions = new HashSet<>();
            for (QuantumEntanglementTemplate.Cell cell : QuantumEntanglementTemplate.cells()) {
                QuantumEntanglementTemplate.Offset rotated =
                        QuantumEntanglementTemplate.rotate(cell.offset(), quarterTurns);
                assertTrue(positions.add(key(rotated)));
            }
            assertEquals(16, positions.size());
            assertFalse(positions.contains(key(new QuantumEntanglementTemplate.Offset(0, 0, 0))));
        }
    }

    @Test
    void portOffsetRotatesAroundController() {
        QuantumEntanglementTemplate.Offset port = new QuantumEntanglementTemplate.Offset(1, 0, 0);
        assertEquals(new QuantumEntanglementTemplate.Offset(1, 0, 0),
                QuantumEntanglementTemplate.rotate(port, 0));
        assertEquals(new QuantumEntanglementTemplate.Offset(0, 0, 1),
                QuantumEntanglementTemplate.rotate(port, 1));
        assertEquals(new QuantumEntanglementTemplate.Offset(-1, 0, 0),
                QuantumEntanglementTemplate.rotate(port, 2));
        assertEquals(new QuantumEntanglementTemplate.Offset(0, 0, -1),
                QuantumEntanglementTemplate.rotate(port, 3));
    }

    private static String key(QuantumEntanglementTemplate.Offset offset) {
        return offset.x() + ":" + offset.y() + ":" + offset.z();
    }
}
