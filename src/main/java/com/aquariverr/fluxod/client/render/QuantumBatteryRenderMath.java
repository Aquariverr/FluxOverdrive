package com.aquariverr.fluxod.client.render;

import com.aquariverr.fluxod.multiblock.QuantumBatteryStructure;
import com.aquariverr.fluxod.multiblock.QuantumBatteryAnimationMath;

public final class QuantumBatteryRenderMath {

    static final float OUTER_SPEED = QuantumBatteryAnimationMath.OUTER_SPEED;
    static final float MIDDLE_SPEED = QuantumBatteryAnimationMath.MIDDLE_SPEED;
    static final float INNER_SPEED = QuantumBatteryAnimationMath.INNER_SPEED;
    static final float OUTER_TILT = QuantumBatteryAnimationMath.OUTER_TILT;
    static final float MIDDLE_TILT = QuantumBatteryAnimationMath.MIDDLE_TILT;
    static final float INNER_TILT = QuantumBatteryAnimationMath.INNER_TILT;
    static final float MAX_PRECESSION_TILT = QuantumBatteryAnimationMath.MAX_PRECESSION_TILT;
    private static final float MIN_HEMISPHERE_EXTENT = 0.35F;
    static final float WORLD_EFFECT_CLEARANCE = 0.15F;
    static final float WORLD_SPHERE_MIN_RADIUS = 0.36F;
    static final float WORLD_SPHERE_MAX_RADIUS = 1.45F;
    private static final float WORLD_EFFECT_MAX_EXTENT = calculateWorldEffectMaxExtent();

    static int withMinimumBlockLight(int packedLight, int minimumLevel) {
        int block = packedLight & 0xFFFF;
        int sky = packedLight >>> 16 & 0xFFFF;
        int minimum = Math.clamp(minimumLevel, 0, 15) << 4;
        return sky << 16 | Math.max(block, minimum);
    }

    public static float outerAngle(long gameTime, float partialTick) {
        return QuantumBatteryAnimationMath.outerAngle(gameTime, partialTick);
    }

    public static float middleAngle(long gameTime, float partialTick) {
        return QuantumBatteryAnimationMath.middleAngle(gameTime, partialTick);
    }

    public static float innerAngle(long gameTime, float partialTick) {
        return QuantumBatteryAnimationMath.innerAngle(gameTime, partialTick);
    }

    static float tierAngle(int tier, float outerAngle, float middleAngle, float innerAngle) {
        return QuantumBatteryAnimationMath.tierAngle(tier, outerAngle, middleAngle, innerAngle);
    }

    static float ringPhase(int tier, QuantumBatteryStructure.Axis axis, float outerAngle,
                           float middleAngle, float innerAngle) {
        return QuantumBatteryAnimationMath.ringPhase(tier, axis, outerAngle, middleAngle, innerAngle);
    }

    static float ringTilt(QuantumBatteryStructure.Axis axis) {
        return QuantumBatteryAnimationMath.ringTilt(axis);
    }

    static boolean hasWorldHemisphereCoverage(int maximumTier, float outerAngle,
                                              float middleAngle, float innerAngle) {
        boolean xCovered = false;
        boolean yCovered = false;
        boolean zCovered = false;
        for (int tier = 1; tier <= maximumTier; tier++) {
            for (QuantumBatteryStructure.Axis axis : QuantumBatteryStructure.Axis.values()) {
                float phase = ringPhase(tier, axis, outerAngle, middleAngle, innerAngle);
                float axial = (float) Math.cos(ringTilt(axis));
                float radial = (float) Math.sin(ringTilt(axis));
                float first = radial * (float) Math.cos(phase);
                float second = radial * (float) Math.sin(phase);
                float normalX;
                float normalY;
                float normalZ;
                switch (axis) {
                    case X -> {
                        normalX = axial;
                        normalY = first;
                        normalZ = second;
                    }
                    case Y -> {
                        normalX = first;
                        normalY = axial;
                        normalZ = second;
                    }
                    case Z -> {
                        normalX = first;
                        normalY = second;
                        normalZ = axial;
                    }
                    default -> throw new IllegalStateException("Unexpected axis: " + axis);
                }
                xCovered |= ringExtent(normalX) >= MIN_HEMISPHERE_EXTENT;
                yCovered |= ringExtent(normalY) >= MIN_HEMISPHERE_EXTENT;
                zCovered |= ringExtent(normalZ) >= MIN_HEMISPHERE_EXTENT;
            }
        }
        // A complete centered ring reaches positive and negative hemispheres equally.
        return xCovered && yCovered && zCovered;
    }

    static float worldEffectMaxExtent() {
        return WORLD_EFFECT_MAX_EXTENT;
    }

    private static float calculateWorldEffectMaxExtent() {
        double closestBlock = QuantumBatteryStructure.layer(1).cells().stream()
                .mapToDouble(cell -> distanceToCellBounds(cell.offset()))
                .min()
                .orElseThrow();
        return (float) (closestBlock - WORLD_EFFECT_CLEARANCE);
    }

    private static double distanceToCellBounds(QuantumBatteryStructure.Offset offset) {
        double x = Math.max(0.0, Math.abs(offset.x()) - 0.5);
        double y = Math.max(0.0, Math.abs(offset.y()) - 0.5);
        double z = Math.max(0.0, Math.abs(offset.z()) - 0.5);
        return Math.sqrt(x * x + y * y + z * z);
    }

    static float worldSphereRadius(double energyScale, double effectRadiusScale) {
        double normalized = Math.clamp(energyScale, 0.0, 1.0);
        double radius = WORLD_SPHERE_MIN_RADIUS
                + (WORLD_SPHERE_MAX_RADIUS - WORLD_SPHERE_MIN_RADIUS) * normalized;
        return (float) (radius * Math.clamp(effectRadiusScale, 0.90, 1.10));
    }

    static double clampWorldEffectLength(double x, double y, double z, double maxExtent) {
        double length = Math.sqrt(x * x + y * y + z * z);
        return Double.isFinite(maxExtent) ? Math.min(length, Math.max(0.0, maxExtent)) : length;
    }

    static double ringStreamLength(int tier, double randomUnit) {
        double base = switch (tier) {
            case 1 -> 0.22;
            case 2 -> 0.46;
            case 3 -> 0.90;
            default -> 0.22;
        };
        return base * (0.72 + Math.clamp(randomUnit, 0.0, 1.0) * 0.58);
    }

    static double ringStreamWidth(int tier, double randomUnit) {
        double base = switch (tier) {
            case 1 -> 0.026;
            case 2 -> 0.040;
            case 3 -> 0.058;
            default -> 0.026;
        };
        return base * (0.60 + Math.clamp(randomUnit, 0.0, 1.0) * 0.95);
    }

    static double constraintBeamInterfaceWidth(boolean advanced) {
        return advanced ? 0.26 : 0.14;
    }

    static double constraintBeamControllerWidth(boolean advanced) {
        return advanced ? 0.060 : 0.035;
    }

    static double segmentAabbIntersection(double startX, double startY, double startZ,
                                          double endX, double endY, double endZ,
                                          double minX, double minY, double minZ,
                                          double maxX, double maxY, double maxZ) {
        double[] range = {0.0, 1.0};
        if (!clipSegmentAxis(startX, endX - startX, minX, maxX, range)
                || !clipSegmentAxis(startY, endY - startY, minY, maxY, range)
                || !clipSegmentAxis(startZ, endZ - startZ, minZ, maxZ, range)) {
            return Double.POSITIVE_INFINITY;
        }
        return range[0];
    }

    static double taperedSegmentAabbIntersection(double startX, double startY, double startZ,
                                                 double endX, double endY, double endZ,
                                                 double startRadius, double endRadius,
                                                 double minX, double minY, double minZ,
                                                 double maxX, double maxY, double maxZ) {
        double[] range = {0.0, 1.0};
        double radiusStart = Math.max(0.0, startRadius);
        double radiusDelta = Math.max(0.0, endRadius) - radiusStart;
        return clipTaperedSegmentAxis(startX, endX - startX, radiusStart, radiusDelta,
                minX, maxX, range)
                && clipTaperedSegmentAxis(startY, endY - startY, radiusStart, radiusDelta,
                minY, maxY, range)
                && clipTaperedSegmentAxis(startZ, endZ - startZ, radiusStart, radiusDelta,
                minZ, maxZ, range)
                ? range[0]
                : Double.POSITIVE_INFINITY;
    }

    private static boolean clipTaperedSegmentAxis(double start, double delta,
                                                  double radiusStart, double radiusDelta,
                                                  double minimum, double maximum,
                                                  double[] range) {
        return clipLinearLessOrEqual(
                minimum - start - radiusStart, -delta - radiusDelta, range)
                && clipLinearLessOrEqual(
                start - radiusStart - maximum, delta - radiusDelta, range);
    }

    private static boolean clipLinearLessOrEqual(double constant, double slope, double[] range) {
        if (Math.abs(slope) < 1.0E-9) return constant <= 0.0;
        double boundary = -constant / slope;
        if (slope > 0.0) range[1] = Math.min(range[1], boundary);
        else range[0] = Math.max(range[0], boundary);
        return range[0] <= range[1];
    }

    private static boolean clipSegmentAxis(double start, double delta, double minimum,
                                           double maximum, double[] range) {
        if (Math.abs(delta) < 1.0E-9) return start >= minimum && start <= maximum;
        double first = (minimum - start) / delta;
        double second = (maximum - start) / delta;
        if (first > second) {
            double swap = first;
            first = second;
            second = swap;
        }
        range[0] = Math.max(range[0], first);
        range[1] = Math.min(range[1], second);
        return range[0] <= range[1];
    }

    private static float ringExtent(float normalComponent) {
        return (float) Math.sqrt(Math.max(0.0F, 1.0F - normalComponent * normalComponent));
    }

    private QuantumBatteryRenderMath() {}
}
