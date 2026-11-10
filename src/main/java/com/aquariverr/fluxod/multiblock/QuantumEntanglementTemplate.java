package com.aquariverr.fluxod.multiblock;

import java.util.ArrayList;
import java.util.List;

public final class QuantumEntanglementTemplate {

    public enum CellType {
        CASING,
        PORT,
        ANTENNA
    }

    public record Offset(int x, int y, int z) {}

    public record Cell(Offset offset, CellType type) {}

    public record MaterialCounts(int casing, int port, int antenna) {}

    private static final List<Cell> CELLS = createCells();

    public static List<Cell> cells() {
        return CELLS;
    }

    public static MaterialCounts materialCounts() {
        int casing = 0;
        int port = 0;
        int antenna = 0;
        for (Cell cell : CELLS) {
            switch (cell.type()) {
                case CASING -> casing++;
                case PORT -> port++;
                case ANTENNA -> antenna++;
            }
        }
        return new MaterialCounts(casing, port, antenna);
    }

    public static Offset rotate(Offset offset, int quarterTurns) {
        return switch (Math.floorMod(quarterTurns, 4)) {
            case 0 -> offset;
            case 1 -> new Offset(-offset.z(), offset.y(), offset.x());
            case 2 -> new Offset(-offset.x(), offset.y(), -offset.z());
            default -> new Offset(offset.z(), offset.y(), -offset.x());
        };
    }

    private static List<Cell> createCells() {
        List<Cell> cells = new ArrayList<>();
        for (int x = -1; x <= 1; x++) {
            for (int z = -2; z <= 0; z++) {
                if (x == 0 && z == 0) continue;
                cells.add(new Cell(new Offset(x, 0, z), CellType.CASING));
            }
        }
        cells.add(new Cell(new Offset(0, 1, -1), CellType.CASING));
        cells.add(new Cell(new Offset(0, 2, -1), CellType.CASING));
        cells.add(new Cell(new Offset(0, 3, -1), CellType.CASING));
        cells.add(new Cell(new Offset(-1, 4, -1), CellType.CASING));
        cells.add(new Cell(new Offset(1, 4, -1), CellType.CASING));
        cells.add(new Cell(new Offset(0, 4, -2), CellType.CASING));
        cells.add(new Cell(new Offset(0, 4, 0), CellType.CASING));
        cells.add(new Cell(new Offset(0, 4, -1), CellType.ANTENNA));
        return List.copyOf(cells);
    }

    private QuantumEntanglementTemplate() {}
}
