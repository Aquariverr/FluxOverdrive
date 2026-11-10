package com.aquariverr.fluxod.miniature.model;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

// This is a layout snapshot; only the later server storage service can transfer real machine assets.
public record FactoryLayout(int size, List<FactoryCell> cells, long revision) {

    public static final List<Integer> VALID_SIZES = List.of(3, 5, 7, 9, 11);

    public FactoryLayout {
        requireSize(size);
        Objects.requireNonNull(cells, "cells");
        if (cells.size() != size * size) {
            throw new ModelValidationException(FactoryDiagnostic.Code.INVALID_CELL_COUNT,
                    "A layout must contain exactly N * N cells; outputs are separate");
        }
        if (revision < 0) {
            throw new ModelValidationException(FactoryDiagnostic.Code.INVALID_REVISION,
                    "Layout revision must be nonnegative");
        }
        cells = List.copyOf(cells);
    }

    public static FactoryLayout empty(int size) {
        requireSize(size);
        return new FactoryLayout(size, Collections.nCopies(size * size, FactoryCell.Empty.INSTANCE), 0);
    }

    public boolean contains(GridPosition position) {
        Objects.requireNonNull(position, "position");
        return position.x() < size && position.y() < size;
    }

    public boolean isOutput(GridPosition position) {
        Objects.requireNonNull(position, "position");
        return position.x() == size && position.y() < size;
    }

    public FactoryCell cellAt(GridPosition position) {
        return cells.get(index(position));
    }

    public FactoryLayout withCell(GridPosition position, FactoryCell cell) {
        int index = index(position);
        Objects.requireNonNull(cell, "cell");
        if (cells.get(index).equals(cell)) return this;
        if (revision == Long.MAX_VALUE) {
            throw new ModelValidationException(FactoryDiagnostic.Code.INVALID_REVISION,
                    "Layout revision is exhausted");
        }
        List<FactoryCell> updated = new ArrayList<>(cells);
        updated.set(index, cell);
        return new FactoryLayout(size, updated, revision + 1);
    }

    public int machineCount() {
        int count = 0;
        for (FactoryCell cell : cells) {
            if (cell instanceof FactoryCell.Machine machine) count += machine.stack().count();
        }
        return count;
    }

    /** Expand in place; only edges that previously reached the output column gain bridges. */
    public FactoryLayout upgrade() {
        if (size == 11) throw new ModelValidationException(FactoryDiagnostic.Code.INVALID_GRID_SIZE, "Maximum grid size reached");
        if (revision == Long.MAX_VALUE) throw new ModelValidationException(FactoryDiagnostic.Code.INVALID_REVISION, "Layout revision is exhausted");
        int nextSize = size + 2;
        List<FactoryCell> expanded = new ArrayList<>(Collections.nCopies(nextSize * nextSize, FactoryCell.Empty.INSTANCE));
        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) expanded.set(y * nextSize + x, cellAt(new GridPosition(x, y)));
            FactoryCell edge = cellAt(new GridPosition(size - 1, y));
            boolean output = edge instanceof FactoryCell.Machine machine && machine.directions().contains(GridDirection.RIGHT)
                    || edge instanceof FactoryCell.Arrow arrow && arrow.weight(GridDirection.RIGHT) > 0
                    || edge instanceof FactoryCell.Marker marker && marker.direction() == GridDirection.RIGHT;
            if (output) for (int x = size; x < nextSize; x++) expanded.set(y * nextSize + x, new FactoryCell.Arrow());
        }
        return new FactoryLayout(nextSize, expanded, revision + 1);
    }

    private int index(GridPosition position) {
        if (!contains(position)) {
            throw new ModelValidationException(new FactoryDiagnostic(FactoryDiagnostic.Code.INVALID_POSITION,
                    Optional.of(position), Optional.empty(), Optional.of(BigInteger.valueOf(size)),
                    Optional.empty()), "Only ordinary factory cells can be read or edited as layout cells");
        }
        return position.y() * size + position.x();
    }

    private static void requireSize(int size) {
        if (!VALID_SIZES.contains(size)) {
            throw new ModelValidationException(FactoryDiagnostic.Code.INVALID_GRID_SIZE,
                    "Grid size must be one of 3, 5, 7, 9 or 11");
        }
    }
}
