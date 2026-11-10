package com.aquariverr.fluxod.miniature.model;

import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FactoryLayoutTest {

    private static final ResourceKey FURNACE = new ResourceKey(ResourceKind.ITEM, "minecraft:furnace");

    @Test
    void acceptsOnlyTheFiveGridSizesWithSeparateOutputColumns() {
        for (int size : List.of(3, 5, 7, 9, 11)) {
            FactoryLayout layout = FactoryLayout.empty(size);
            assertEquals(size * size, layout.cells().size());
            assertEquals(0, layout.machineCount());
            for (int y = 0; y < size; y++) {
                assertTrue(layout.contains(new GridPosition(size - 1, y)));
                assertFalse(layout.isOutput(new GridPosition(size - 1, y)));
                assertTrue(layout.isOutput(new GridPosition(size, y)));
                assertFalse(layout.contains(new GridPosition(size, y)));
            }
        }
        for (int size : List.of(Integer.MIN_VALUE, -1, 0, 1, 2, 4, 6, 8, 10, 12, Integer.MAX_VALUE)) {
            assertCode(FactoryDiagnostic.Code.INVALID_GRID_SIZE,
                    () -> FactoryLayout.empty(size));
        }
    }

    @Test
    void outputCellsCannotHoldMachinesOrBeReadAsOrdinaryCells() {
        FactoryLayout layout = FactoryLayout.empty(3);
        GridPosition output = new GridPosition(3, 0);
        ModelValidationException error = assertThrows(ModelValidationException.class,
                () -> layout.withCell(output, new FactoryCell.Machine(new MachineStack(FURNACE, 1))));
        assertEquals(FactoryDiagnostic.Code.INVALID_POSITION, error.diagnostic().code());
        assertEquals(Optional.of(output), error.diagnostic().position());
        assertCode(FactoryDiagnostic.Code.INVALID_POSITION, () -> layout.cellAt(output));
        assertCode(FactoryDiagnostic.Code.INVALID_POSITION,
                () -> layout.withCell(new GridPosition(0, 3), new FactoryCell.Arrow()));
        assertEquals(0, layout.machineCount());
        assertEquals(0, layout.revision());
    }

    @Test
    void rejectsCoordinatesOutsideTheLargestGridAndOutputColumn() {
        assertCode(FactoryDiagnostic.Code.INVALID_POSITION, () -> new GridPosition(-1, 0));
        assertCode(FactoryDiagnostic.Code.INVALID_POSITION, () -> new GridPosition(0, -1));
        assertCode(FactoryDiagnostic.Code.INVALID_POSITION, () -> new GridPosition(12, 0));
        assertCode(FactoryDiagnostic.Code.INVALID_POSITION, () -> new GridPosition(0, 11));
        assertTrue(FactoryLayout.empty(11).isOutput(new GridPosition(11, 10)));
    }

    @Test
    void usesRowMajorCoordinatesAndImmutableSnapshots() {
        List<FactoryCell> cells = new ArrayList<>(Collections.nCopies(9, FactoryCell.Empty.INSTANCE));
        FactoryLayout original = new FactoryLayout(3, cells, 7);
        cells.set(0, new FactoryCell.Arrow());
        assertSame(FactoryCell.Empty.INSTANCE, original.cellAt(new GridPosition(0, 0)));

        FactoryCell machine = new FactoryCell.Machine(new MachineStack(FURNACE, 2));
        FactoryLayout updated = original.withCell(new GridPosition(2, 1), machine);
        assertSame(machine, updated.cells().get(5));
        assertSame(machine, updated.cellAt(new GridPosition(2, 1)));
        assertEquals(8, updated.revision());
        assertEquals(7, original.revision());
        assertEquals(0, original.machineCount());
        assertEquals(2, updated.machineCount());
        assertSame(updated, updated.withCell(new GridPosition(2, 1), machine));
        assertThrows(UnsupportedOperationException.class, () -> updated.cells().clear());
    }

    @Test
    void validatesCellCountAndRevisionWithoutWrapping() {
        assertCode(FactoryDiagnostic.Code.INVALID_CELL_COUNT,
                () -> new FactoryLayout(3, List.of(), 0));
        assertCode(FactoryDiagnostic.Code.INVALID_CELL_COUNT,
                () -> new FactoryLayout(3, Collections.nCopies(12, FactoryCell.Empty.INSTANCE), 0));
        assertCode(FactoryDiagnostic.Code.INVALID_REVISION,
                () -> new FactoryLayout(3, Collections.nCopies(9, FactoryCell.Empty.INSTANCE), -1));
        FactoryLayout exhausted = new FactoryLayout(3, Collections.nCopies(9, FactoryCell.Empty.INSTANCE),
                Long.MAX_VALUE);
        assertCode(FactoryDiagnostic.Code.INVALID_REVISION,
                () -> exhausted.withCell(new GridPosition(0, 0), new FactoryCell.Arrow()));
        assertEquals(Long.MAX_VALUE, exhausted.revision());
        assertSame(FactoryCell.Empty.INSTANCE, exhausted.cellAt(new GridPosition(0, 0)));
    }

    @Test
    void cellKindsAreExclusiveAndRejectMissingPayloads() {
        assertThrows(NullPointerException.class, () -> new FactoryCell.Machine(null));
        assertThrows(NullPointerException.class, () -> new FactoryCell.Marker(null, GridDirection.RIGHT));
        assertThrows(NullPointerException.class,
                () -> new FactoryCell.Marker(ResourceAmount.defaultMarker(FURNACE), null));
        assertThrows(NullPointerException.class, () -> new FactoryCell.Arrow((GridDirection) null));
        FactoryLayout layout = FactoryLayout.empty(3);
        assertThrows(NullPointerException.class, () -> layout.withCell(new GridPosition(0, 0), null));
    }

    @Test
    void machineCountsAreIndependentOfNaturalStackLimitsAndPreserveVariants() {
        CompoundTag components = new CompoundTag();
        components.putInt("minecraft:max_stack_size", 1);
        ResourceKey unstackable = new ResourceKey(ResourceKind.ITEM, "test:machine", components);
        MachineStack stack = new MachineStack(unstackable, 64);
        assertEquals(64, stack.count());
        assertEquals(unstackable, stack.variant());
        assertNotEquals(new MachineStack(new ResourceKey(ResourceKind.ITEM, "test:machine"), 64), stack);
        assertEquals(1, new MachineStack(FURNACE, 1).count());
        assertCode(FactoryDiagnostic.Code.INVALID_MACHINE_COUNT, () -> new MachineStack(FURNACE, 0));
        assertCode(FactoryDiagnostic.Code.INVALID_MACHINE_COUNT, () -> new MachineStack(FURNACE, 65));
        assertCode(FactoryDiagnostic.Code.INVALID_MACHINE_COUNT, () -> new MachineStack(FURNACE, -1));
        assertCode(FactoryDiagnostic.Code.INVALID_MACHINE,
                () -> new MachineStack(new ResourceKey(ResourceKind.FLUID, "minecraft:water"), 1));
    }

    @Test
    void countsEveryMachineIncludingUnconnectedCells() {
        FactoryLayout layout = FactoryLayout.empty(3)
                .withCell(new GridPosition(0, 0), new FactoryCell.Machine(new MachineStack(FURNACE, 2)))
                .withCell(new GridPosition(2, 2), new FactoryCell.Machine(new MachineStack(FURNACE, 5)))
                .withCell(new GridPosition(1, 0), new FactoryCell.Marker(FURNACE))
                .withCell(new GridPosition(1, 1), new FactoryCell.Arrow());
        assertEquals(7, layout.machineCount());
        assertEquals(new BigInteger("140000000"), FactoryRules.energyPerTick(layout.machineCount()));
    }

    @Test
    void maximumLayoutUsesLargeIntegerEnergyCosts() {
        FactoryCell.Machine machine = new FactoryCell.Machine(new MachineStack(FURNACE, 64));
        FactoryLayout layout = new FactoryLayout(11, Collections.nCopies(121, machine), 0);
        assertEquals(7_744, layout.machineCount());
        assertEquals(new BigInteger("154880000000"), FactoryRules.energyPerTick(layout.machineCount()));
        assertEquals(new BigInteger("3097600000000"), FactoryRules.energyPerBatch(layout.machineCount()));
    }

    @Test
    void upgradesOnlyBridgeMachinesWhoseOutputFacesTheOldOutputColumn() {
        for (GridDirection direction : GridDirection.values()) {
            var machine = new FactoryCell.Machine(new MachineStack(FURNACE, 2), null, direction);
            var layout = FactoryLayout.empty(3).withCell(new GridPosition(2, 1), machine).upgrade();
            assertEquals(machine, layout.cellAt(new GridPosition(2, 1)));
            for (int x = 3; x < 5; x++) assertEquals(direction == GridDirection.RIGHT
                    ? new FactoryCell.Arrow() : FactoryCell.Empty.INSTANCE, layout.cellAt(new GridPosition(x, 1)));
        }
    }

    @Test
    void directionsCycleClockwiseAndDefaultsPointRight() {
        assertEquals(GridDirection.RIGHT, new FactoryCell.Arrow().direction());
        assertEquals(GridDirection.RIGHT, new FactoryCell.Marker(FURNACE).direction());
        GridDirection direction = GridDirection.RIGHT;
        for (GridDirection expected : List.of(GridDirection.DOWN, GridDirection.LEFT,
                GridDirection.UP, GridDirection.RIGHT)) {
            direction = direction.clockwise();
            assertEquals(expected, direction);
        }
        assertEquals(1, GridDirection.RIGHT.deltaX());
        assertEquals(0, GridDirection.RIGHT.deltaY());
        assertEquals(1, GridDirection.DOWN.deltaY());
        assertEquals(-1, GridDirection.LEFT.deltaX());
        assertEquals(-1, GridDirection.UP.deltaY());
    }

    @Test
    void diagnosticsPreserveLocationResourceAndExactQuantities() {
        BigInteger expected = new BigInteger("1000000000000000000000000000000");
        BigInteger actual = expected.subtract(BigInteger.ONE);
        FactoryDiagnostic diagnostic = new FactoryDiagnostic(FactoryDiagnostic.Code.INVALID_QUANTITY,
                Optional.of(new GridPosition(2, 1)), Optional.of(FURNACE),
                Optional.of(expected), Optional.of(actual));
        ModelValidationException error = new ModelValidationException(diagnostic, "Invalid marker quantity");
        assertSame(diagnostic, error.diagnostic());
        assertEquals(Optional.of(expected), error.diagnostic().expected());
        assertEquals(Optional.of(actual), error.diagnostic().actual());
        assertEquals(Optional.of(FURNACE), error.diagnostic().resource());
        assertEquals(Optional.of(new GridPosition(2, 1)), error.diagnostic().position());
    }

    private static void assertCode(FactoryDiagnostic.Code code, Runnable operation) {
        ModelValidationException exception = assertThrows(ModelValidationException.class, operation::run);
        assertEquals(code, exception.diagnostic().code());
    }
}
