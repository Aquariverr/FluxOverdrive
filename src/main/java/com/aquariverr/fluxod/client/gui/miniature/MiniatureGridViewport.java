package com.aquariverr.fluxod.client.gui.miniature;

import com.aquariverr.fluxod.miniature.model.FactoryLayout;

import java.util.Objects;
import java.util.Optional;

public final class MiniatureGridViewport {

    private final int gridSize;
    private final Rect clip;
    private final double cellSize;
    private final double gap;
    private final double minScale;
    private final double maxScale;
    private double originX;
    private double originY;
    private double scale;

    public MiniatureGridViewport(int gridSize, Rect clip, double cellSize, double gap,
                                 double minScale, double maxScale) {
        if (!FactoryLayout.VALID_SIZES.contains(gridSize)) {
            throw new IllegalArgumentException("Unsupported miniature grid size");
        }
        this.gridSize = gridSize;
        this.clip = Objects.requireNonNull(clip, "clip");
        requirePositive(cellSize, "cellSize");
        requireFinite(gap, "gap");
        requirePositive(minScale, "minScale");
        requirePositive(maxScale, "maxScale");
        if (gap < 0 || minScale > maxScale) {
            throw new IllegalArgumentException("Invalid grid spacing or zoom bounds");
        }
        this.cellSize = cellSize;
        this.gap = gap;
        this.minScale = minScale;
        this.maxScale = maxScale;
        requireFinite(contentWidth() * maxScale, "scaled content width");
        requireFinite(contentHeight() * maxScale, "scaled content height");
        reset();
    }

    public int gridSize() { return gridSize; }
    public Rect clip() { return clip; }
    public double cellSize() { return cellSize; }
    public double gap() { return gap; }
    public double stride() { return cellSize + gap; }
    public double originX() { return originX; }
    public double originY() { return originY; }
    public double scale() { return scale; }
    public double contentWidth() { return (gridSize + 1) * cellSize + gridSize * gap; }
    public double contentHeight() { return gridSize * cellSize + (gridSize - 1) * gap; }

    public Rect cellBounds(int x, int y) {
        if (x < 0 || x > gridSize || y < 0 || y >= gridSize) {
            throw new IllegalArgumentException("Cell is outside the grid and output column");
        }
        return new Rect(originX + x * stride() * scale, originY + y * stride() * scale,
                cellSize * scale, cellSize * scale);
    }

    public Optional<Hit> hitTest(double mouseX, double mouseY) {
        if (!clip.contains(mouseX, mouseY)) return Optional.empty();
        double column = (mouseX - originX) / (stride() * scale);
        double row = (mouseY - originY) / (stride() * scale);
        if (column < -1 || column > gridSize + 1 || row < -1 || row > gridSize) {
            return Optional.empty();
        }
        int candidateX = (int) Math.floor(column);
        int candidateY = (int) Math.floor(row);
        // Check adjacent candidates against the drawn bounds to preserve exact floating-point edges.
        for (int y = Math.max(0, candidateY - 1); y <= Math.min(gridSize - 1, candidateY + 1); y++) {
            for (int x = Math.max(0, candidateX - 1); x <= Math.min(gridSize, candidateX + 1); x++) {
                if (cellBounds(x, y).contains(mouseX, mouseY)) {
                    return Optional.of(new Hit(x, y, x == gridSize));
                }
            }
        }
        return Optional.empty();
    }

    public void panBy(double deltaX, double deltaY) {
        requireFinite(deltaX, "deltaX");
        requireFinite(deltaY, "deltaY");
        double nextX = originX + deltaX;
        double nextY = originY + deltaY;
        requireFinite(nextX, "originX");
        requireFinite(nextY, "originY");
        originX = nextX;
        originY = nextY;
    }

    public boolean zoomAt(double mouseX, double mouseY, double factor) {
        requirePositive(factor, "factor");
        if (!clip.contains(mouseX, mouseY)) return false;
        double nextScale = clampScale(scale * factor);
        if (nextScale == scale) return false;
        double ratio = nextScale / scale;
        double nextX = mouseX - (mouseX - originX) * ratio;
        double nextY = mouseY - (mouseY - originY) * ratio;
        requireFinite(nextX, "originX");
        requireFinite(nextY, "originY");
        originX = nextX;
        originY = nextY;
        scale = nextScale;
        return true;
    }

    public void reset() {
        center(clampScale(1));
    }

    public void fit() {
        center(clampScale(Math.min(clip.width() / contentWidth(), clip.height() / contentHeight())));
    }

    private void center(double nextScale) {
        scale = nextScale;
        originX = clip.x() + (clip.width() - contentWidth() * scale) / 2;
        originY = clip.y() + (clip.height() - contentHeight() * scale) / 2;
    }

    private double clampScale(double value) {
        return Math.max(minScale, Math.min(maxScale, value));
    }

    private static void requireFinite(double value, String name) {
        if (!Double.isFinite(value)) throw new IllegalArgumentException(name + " must be finite");
    }

    private static void requirePositive(double value, String name) {
        requireFinite(value, name);
        if (value <= 0) throw new IllegalArgumentException(name + " must be positive");
    }

    public record Rect(double x, double y, double width, double height) {
        public Rect {
            requireFinite(x, "x");
            requireFinite(y, "y");
            requirePositive(width, "width");
            requirePositive(height, "height");
            requireFinite(x + width, "right");
            requireFinite(y + height, "bottom");
        }

        public double right() { return x + width; }
        public double bottom() { return y + height; }

        public boolean contains(double pointX, double pointY) {
            return pointX >= x && pointX < right() && pointY >= y && pointY < bottom();
        }
    }

    public record Hit(int x, int y, boolean output) {
        public boolean editable() { return !output; }
    }
}
