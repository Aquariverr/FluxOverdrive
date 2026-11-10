package com.aquariverr.fluxod.client.render;

import com.aquariverr.fluxod.multiblock.QuantumBatteryStructure;
import com.aquariverr.fluxod.multiblock.QuantumBatteryAnimationMath;
import org.joml.Matrix3f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

final class QuantumBatteryIntersectionMath {

    static Quaternionf ringRotation(int tier, QuantumBatteryStructure.Axis axis, float outerAngle,
                                    float middleAngle, float innerAngle) {
        float phase = QuantumBatteryRenderMath.ringPhase(
                tier, axis, outerAngle, middleAngle, innerAngle);
        float tilt = QuantumBatteryRenderMath.ringTilt(axis);
        Vector3f normal = precessedNormal(axis, tilt, phase);
        return rotationBetween(axisVector(axis), normal);
    }

    static Vector3f axisVector(QuantumBatteryStructure.Axis axis) {
        return switch (axis) {
            case X -> new Vector3f(1.0F, 0.0F, 0.0F);
            case Y -> new Vector3f(0.0F, 1.0F, 0.0F);
            case Z -> new Vector3f(0.0F, 0.0F, 1.0F);
        };
    }

    static Vector3f sourceIntersectionDirection(QuantumBatteryStructure.Axis first,
                                                QuantumBatteryStructure.Axis second, int branch) {
        return axisVector(first).cross(axisVector(second)).normalize().mul(branch);
    }

    static IntersectionTransform intersectionTransform(int tier, QuantumBatteryStructure.Axis first,
                                                         QuantumBatteryStructure.Axis second,
                                                         int branch, float outerAngle,
                                                         float middleAngle, float innerAngle) {
        Vector3f sourceDirection = sourceIntersectionDirection(first, second, branch);
        Quaternionf firstRotation = ringRotation(tier, first, outerAngle, middleAngle, innerAngle);
        Vector3f firstNormal = axisVector(first).rotate(new Quaternionf(firstRotation));
        Vector3f secondNormal = axisVector(second)
                .rotate(ringRotation(tier, second, outerAngle, middleAngle, innerAngle));
        Vector3f referenceDirection = new Vector3f(sourceDirection)
                .rotate(new Quaternionf(firstRotation)).normalize();
        Vector3f targetDirection = new Vector3f(firstNormal).cross(secondNormal);
        if (targetDirection.lengthSquared() < 1.0E-6F) {
            targetDirection.set(referenceDirection)
                    .fma(-referenceDirection.dot(firstNormal), firstNormal)
                    .normalize();
        } else {
            targetDirection.normalize();
            if (targetDirection.dot(referenceDirection) < 0.0F) targetDirection.negate();
        }

        Vector3f sourceNormal = axisVector(first);
        Vector3f sourceTangent = new Vector3f(sourceDirection).cross(sourceNormal).normalize();
        Vector3f targetTangent = new Vector3f(targetDirection).cross(firstNormal).normalize();
        Matrix3f sourceFrame = new Matrix3f(sourceDirection, sourceNormal, sourceTangent);
        Matrix3f targetFrame = new Matrix3f(targetDirection, firstNormal, targetTangent);
        Quaternionf rotation = new Quaternionf().setFromNormalized(
                new Matrix3f(targetFrame).mul(new Matrix3f(sourceFrame).transpose()));
        return new IntersectionTransform(sourceDirection, targetDirection, rotation);
    }

    private static Vector3f precessedNormal(QuantumBatteryStructure.Axis axis, float tilt, float phase) {
        return QuantumBatteryAnimationMath.ringNormal(axis, tilt, phase);
    }

    private static Quaternionf rotationBetween(Vector3f from, Vector3f to) {
        float dot = Math.clamp(from.dot(to), -1.0F, 1.0F);
        if (dot > 0.999999F) return new Quaternionf();
        if (dot < -0.999999F) {
            Vector3f perpendicular = Math.abs(from.x) < 0.9F
                    ? new Vector3f(1.0F, 0.0F, 0.0F)
                    : new Vector3f(0.0F, 1.0F, 0.0F);
            Vector3f axis = new Vector3f(from).cross(perpendicular).normalize();
            return new Quaternionf().rotationAxis((float) Math.PI, axis.x, axis.y, axis.z);
        }
        Vector3f axis = new Vector3f(from).cross(to).normalize();
        return new Quaternionf().rotationAxis((float) Math.acos(dot), axis.x, axis.y, axis.z);
    }

    record IntersectionTransform(Vector3f sourceDirection, Vector3f targetDirection,
                                 Quaternionf rotation) {}

    private QuantumBatteryIntersectionMath() {}
}
