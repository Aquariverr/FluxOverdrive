package com.aquariverr.fluxod.block.entity;

import java.util.NoSuchElementException;

final class QuantumBatteryQuenchGeometry {

    private static final double DISTANCE_EPSILON = 1.0E-7;

    static boolean containsBlockCenter(double centerX, double centerY, double centerZ,
                                       double radius, int blockX, int blockY, int blockZ) {
        return squaredDistanceToBlockCenter(centerX, centerY, centerZ, blockX, blockY, blockZ)
                <= radius * radius + DISTANCE_EPSILON;
    }

    static double squaredDistanceToBlockCenter(double centerX, double centerY, double centerZ,
                                               int blockX, int blockY, int blockZ) {
        double dx = blockX + 0.5 - centerX;
        double dy = blockY + 0.5 - centerY;
        double dz = blockZ + 0.5 - centerZ;
        return dx * dx + dy * dy + dz * dz;
    }

    static int minBlockCoordinate(double center, double radius) {
        return (int) Math.ceil(center - radius - 0.5);
    }

    static int maxBlockCoordinate(double center, double radius) {
        return (int) Math.floor(center + radius - 0.5);
    }

    static boolean sphereIntersectsBlockBox(double centerX, double centerY, double centerZ,
                                            double radius, int minX, int minY, int minZ,
                                            int maxX, int maxY, int maxZ) {
        double dx = distanceToInterval(centerX, minX + 0.5, maxX + 0.5);
        double dy = distanceToInterval(centerY, minY + 0.5, maxY + 0.5);
        double dz = distanceToInterval(centerZ, minZ + 0.5, maxZ + 0.5);
        return dx * dx + dy * dy + dz * dz <= radius * radius + DISTANCE_EPSILON;
    }

    static double squaredDistanceToBlockBox(double centerX, double centerZ,
                                            int minX, int maxX, int minZ, int maxZ) {
        double dx = distanceToInterval(centerX, minX + 0.5, maxX + 0.5);
        double dz = distanceToInterval(centerZ, minZ + 0.5, maxZ + 0.5);
        return dx * dx + dz * dz;
    }

    private static double distanceToInterval(double value, double min, double max) {
        if (value < min) return min - value;
        if (value > max) return value - max;
        return 0.0;
    }

    static final class BlockCursor {
        private final int minX;
        private final int maxX;
        private final int maxY;
        private final int minZ;
        private final int maxZ;
        private int x;
        private int y;
        private int z;
        private boolean hasNext;

        BlockCursor(int minX, int maxX, int minY, int maxY, int minZ, int maxZ) {
            this.minX = minX;
            this.maxX = maxX;
            this.maxY = maxY;
            this.minZ = minZ;
            this.maxZ = maxZ;
            this.x = minX;
            this.y = minY;
            this.z = minZ;
            this.hasNext = minX <= maxX && minY <= maxY && minZ <= maxZ;
        }

        boolean hasNext() {
            return hasNext;
        }

        int x() {
            requireNext();
            return x;
        }

        int y() {
            requireNext();
            return y;
        }

        int z() {
            requireNext();
            return z;
        }

        void advance() {
            requireNext();
            x++;
            if (x > maxX) {
                x = minX;
                z++;
                if (z > maxZ) {
                    z = minZ;
                    y++;
                    if (y > maxY) hasNext = false;
                }
            }
        }

        private void requireNext() {
            if (!hasNext) throw new NoSuchElementException();
        }
    }

    private QuantumBatteryQuenchGeometry() {}
}
