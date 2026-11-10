package com.aquariverr.fluxod.multiblock;

import org.joml.Vector3f;

public final class QuantumBatteryAnimationMath {

    public static final float OUTER_SPEED = 0.004F;
    public static final float MIDDLE_SPEED = 0.012F;
    public static final float INNER_SPEED = 0.024F;
    public static final float OUTER_TILT = 0.38F;
    public static final float MIDDLE_TILT = -0.49F;
    public static final float INNER_TILT = 0.61F;
    public static final float MAX_PRECESSION_TILT = 0.42F;

    public static float outerAngle(long gameTime, float partialTick) {
        return (gameTime + partialTick) * OUTER_SPEED;
    }

    public static float middleAngle(long gameTime, float partialTick) {
        return (gameTime + partialTick) * MIDDLE_SPEED;
    }

    public static float innerAngle(long gameTime, float partialTick) {
        return (gameTime + partialTick) * INNER_SPEED;
    }

    public static float tierAngle(int tier, float outerAngle, float middleAngle, float innerAngle) {
        return switch (tier) {
            case 1 -> innerAngle;
            case 2 -> middleAngle;
            case 3 -> outerAngle;
            default -> throw new IllegalArgumentException("Invalid quantum battery tier: " + tier);
        };
    }

    public static float ringPhase(int tier, QuantumBatteryStructure.Axis axis, float outerAngle,
                                  float middleAngle, float innerAngle) {
        float axisPhase = switch (axis) {
            case X -> outerAngle;
            case Y -> middleAngle + (float) (Math.PI * 2.0 / 3.0);
            case Z -> innerAngle + (float) (Math.PI * 4.0 / 3.0);
        };
        return axisPhase + tierAngle(tier, outerAngle, middleAngle, innerAngle);
    }

    public static float ringTilt(QuantumBatteryStructure.Axis axis) {
        float requested = switch (axis) {
            case X -> Math.abs(OUTER_TILT);
            case Y -> Math.abs(MIDDLE_TILT);
            case Z -> Math.abs(INNER_TILT);
        };
        return Math.min(requested, MAX_PRECESSION_TILT);
    }

    public static Vector3f ringNormal(QuantumBatteryStructure.Axis axis, float tilt, float phase) {
        float axial = (float) Math.cos(tilt);
        float radial = (float) Math.sin(tilt);
        float first = radial * (float) Math.cos(phase);
        float second = radial * (float) Math.sin(phase);
        return switch (axis) {
            case X -> new Vector3f(axial, first, second).normalize();
            case Y -> new Vector3f(first, axial, second).normalize();
            case Z -> new Vector3f(first, second, axial).normalize();
        };
    }

    private QuantumBatteryAnimationMath() {}
}
