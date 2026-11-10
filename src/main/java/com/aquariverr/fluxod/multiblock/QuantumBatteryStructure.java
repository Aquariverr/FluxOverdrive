package com.aquariverr.fluxod.multiblock;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class QuantumBatteryStructure {

    public enum CellType {
        CASING,
        GLASS
    }

    public record Offset(int x, int y, int z) {}

    public record Cell(Offset offset, CellType type, Set<Axis> axes) {
        public Cell {
            axes = Set.copyOf(axes);
        }

        public boolean isIntersection() {
            return axes.size() >= 2;
        }
    }

    public record Layer(int tier, int radius, int tubeDiameter, List<Cell> cells) {}

    public record MaterialCounts(int casing, int glass) {}

    private static final List<Layer> LAYERS = List.of(
            createLayer(1, 3, 1),
            createLayer(2, 7, 3),
            createLayer(3, 13, 5)
    );
    private static final List<MaterialCounts> MATERIAL_COUNTS = LAYERS.stream()
            .map(QuantumBatteryStructure::countMaterials)
            .toList();
    private static final List<List<Cell>> INTERSECTION_CELLS = LAYERS.stream()
            .map(layer -> layer.cells().stream().filter(Cell::isIntersection).toList())
            .toList();
    private static final Map<Offset, Integer> CELL_TIERS = createCellTierIndex();
    private static final Map<Offset, Cell> CELL_INDEX = createCellIndex();
    private static final List<List<Cell>> STABILIZER_SLOTS = LAYERS.stream()
            .map(layer -> layer.cells().stream()
                    .filter(cell -> isStabilizerSlot(layer.tier(), cell))
                    .toList())
            .toList();

    public static Layer layer(int tier) {
        if (tier < 1 || tier > LAYERS.size()) {
            throw new IllegalArgumentException("Invalid quantum battery tier: " + tier);
        }
        return LAYERS.get(tier - 1);
    }

    public static List<Layer> layers() {
        return LAYERS;
    }

    public static List<Cell> intersections(int tier) {
        layer(tier);
        return INTERSECTION_CELLS.get(tier - 1);
    }

    public static List<Cell> stabilizerSlots(int tier) {
        layer(tier);
        return STABILIZER_SLOTS.get(tier - 1);
    }

    public static int stabilizerSlotRadius(int tier) {
        Layer layer = layer(tier);
        return layer.radius() - layer.tubeDiameter() / 2;
    }

    public static boolean isStabilizerSlot(int tier, Cell cell) {
        layer(tier);
        if (!cell.isIntersection() || CELL_TIERS.get(cell.offset()) != tier) return false;
        Offset offset = cell.offset();
        int radius = stabilizerSlotRadius(tier);
        return Math.abs(offset.x()) == radius && offset.y() == 0 && offset.z() == 0
                || Math.abs(offset.y()) == radius && offset.x() == 0 && offset.z() == 0
                || Math.abs(offset.z()) == radius && offset.x() == 0 && offset.y() == 0;
    }

    public static MaterialCounts materialCounts(int tier) {
        layer(tier);
        return MATERIAL_COUNTS.get(tier - 1);
    }

    public static int tierAt(int x, int y, int z) {
        return CELL_TIERS.getOrDefault(new Offset(x, y, z), 0);
    }

    public static boolean supportsThreeByThreeStabilizer(int tier, Cell center) {
        return !threeByThreeStabilizerFootprint(tier, center).isEmpty();
    }

    public static List<Offset> threeByThreeStabilizerFootprint(int tier, Cell center) {
        if (tier < 2 || tier > 3 || !isStabilizerSlot(tier, center)) return List.of();

        Axis first = null;
        Axis second = null;
        for (Axis axis : Axis.values()) {
            if (!center.axes().contains(axis)) continue;
            if (first == null) first = axis;
            else if (second == null) {
                second = axis;
                break;
            }
        }
        if (first == null || second == null) return List.of();

        List<Offset> footprint = new ArrayList<>(9);
        for (int firstOffset = -1; firstOffset <= 1; firstOffset++) {
            for (int secondOffset = -1; secondOffset <= 1; secondOffset++) {
                Offset offset = offsetInPlane(center.offset(), first, firstOffset, second, secondOffset);
                Cell cell = CELL_INDEX.get(offset);
                if (cell == null || CELL_TIERS.get(offset) != tier || cell.type() != CellType.CASING) {
                    return List.of();
                }
                footprint.add(offset);
            }
        }
        return List.copyOf(footprint);
    }

    private static Map<Offset, Integer> createCellTierIndex() {
        Map<Offset, Integer> result = new HashMap<>();
        for (Layer layer : LAYERS) {
            for (Cell cell : layer.cells()) {
                result.put(cell.offset(), layer.tier());
            }
        }
        return Map.copyOf(result);
    }

    private static Map<Offset, Cell> createCellIndex() {
        Map<Offset, Cell> result = new HashMap<>();
        for (Layer layer : LAYERS) {
            for (Cell cell : layer.cells()) {
                result.put(cell.offset(), cell);
            }
        }
        return Map.copyOf(result);
    }

    private static Offset offsetInPlane(Offset center, Axis first, int firstOffset,
                                        Axis second, int secondOffset) {
        int x = center.x();
        int y = center.y();
        int z = center.z();
        switch (first) {
            case X -> x += firstOffset;
            case Y -> y += firstOffset;
            case Z -> z += firstOffset;
        }
        switch (second) {
            case X -> x += secondOffset;
            case Y -> y += secondOffset;
            case Z -> z += secondOffset;
        }
        return new Offset(x, y, z);
    }

    private static MaterialCounts countMaterials(Layer layer) {
        int casing = 0;
        int glass = 0;
        for (Cell cell : layer.cells()) {
            if (cell.type() == CellType.CASING) casing++;
            else glass++;
        }
        return new MaterialCounts(casing, glass);
    }

    private static Layer createLayer(int tier, int radius, int tubeDiameter) {
        double tubeRadius = tubeDiameter / 2.0;
        int bound = (int) Math.floor(radius + tubeRadius);
        List<Cell> cells = new ArrayList<>();
        Set<Offset> seen = new HashSet<>();

        for (int x = -bound; x <= bound; x++) {
            for (int y = -bound; y <= bound; y++) {
                for (int z = -bound; z <= bound; z++) {
                    EnumSet<Axis> axes = EnumSet.noneOf(Axis.class);
                    for (Axis axis : Axis.values()) {
                        if (insideTorus(x, y, z, radius, tubeRadius, axis)) axes.add(axis);
                    }
                    if (axes.isEmpty()) continue;

                    Offset offset = new Offset(x, y, z);
                    if (seen.add(offset)) {
                        cells.add(new Cell(offset, axes.size() >= 2 ? CellType.CASING : CellType.GLASS, axes));
                    }
                }
            }
        }

        return new Layer(tier, radius, tubeDiameter, List.copyOf(cells));
    }

    private static boolean insideTorus(int x, int y, int z, int radius, double tubeRadius, Axis axis) {
        double axial;
        double first;
        double second;
        switch (axis) {
            case X -> {
                axial = x;
                first = y;
                second = z;
            }
            case Y -> {
                axial = y;
                first = x;
                second = z;
            }
            case Z -> {
                axial = z;
                first = x;
                second = y;
            }
            default -> throw new IllegalStateException("Unexpected axis: " + axis);
        }

        double radialOffset = Math.hypot(first, second) - radius;
        return radialOffset * radialOffset + axial * axial <= tubeRadius * tubeRadius;
    }

    public enum Axis {
        X,
        Y,
        Z
    }

    private QuantumBatteryStructure() {}
}
