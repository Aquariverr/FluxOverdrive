package com.aquariverr.fluxod.multiblock;

import com.aquariverr.fluxod.block.entity.TileQuantumBatteryController;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

import java.util.Collection;

public final class QuantumBatteryCollision {

    private static final int CONTROLLER_SEARCH_RADIUS = 18;
    private static final double EPSILON = 1.0E-6;

    public static void resolve(Entity entity) {
        if (entity.isRemoved() || entity.isSpectator() || entity.noPhysics) return;

        Level level = entity.level();
        BlockPos entityPos = entity.blockPosition();
        Collection<BlockPos> controllers = TileQuantumBatteryController.getLoadedControllersNear(
                level, entityPos, CONTROLLER_SEARCH_RADIUS);
        for (BlockPos controllerPos : controllers) {
            if (Math.abs(controllerPos.getX() - entityPos.getX()) > CONTROLLER_SEARCH_RADIUS
                    || Math.abs(controllerPos.getY() - entityPos.getY()) > CONTROLLER_SEARCH_RADIUS
                    || Math.abs(controllerPos.getZ() - entityPos.getZ()) > CONTROLLER_SEARCH_RADIUS) {
                continue;
            }
            if (!level.isLoaded(controllerPos)) continue;
            if (!(level.getBlockEntity(controllerPos) instanceof TileQuantumBatteryController controller)
                    || !controller.isAnimationActive()) {
                continue;
            }
            resolveAgainstController(entity, controllerPos, controller.getStructureTier(), level.getGameTime());
        }
    }

    static Vec3 ringPush(Vec3 relativePosition, Vector3f normal, double radius,
                         double tubeRadius, double entityRadius) {
        QuantumBatteryCollisionMath.Push push = QuantumBatteryCollisionMath.ringPush(
                relativePosition.x, relativePosition.y, relativePosition.z,
                normal.x, normal.y, normal.z, radius, tubeRadius, entityRadius);
        return new Vec3(push.x(), push.y(), push.z());
    }

    private static void resolveAgainstController(Entity entity, BlockPos controllerPos, int structureTier,
                                                 long gameTime) {
        Vec3 center = Vec3.atCenterOf(controllerPos);
        double entityRadius = Math.max(entity.getBbWidth(), entity.getBbHeight()) * 0.5;
        float outerAngle = QuantumBatteryAnimationMath.outerAngle(gameTime, 0.0F);
        float middleAngle = QuantumBatteryAnimationMath.middleAngle(gameTime, 0.0F);
        float innerAngle = QuantumBatteryAnimationMath.innerAngle(gameTime, 0.0F);

        for (int tier = 1; tier <= structureTier; tier++) {
            QuantumBatteryStructure.Layer layer = QuantumBatteryStructure.layer(tier);
            for (QuantumBatteryStructure.Axis axis : QuantumBatteryStructure.Axis.values()) {
                float phase = QuantumBatteryAnimationMath.ringPhase(
                        tier, axis, outerAngle, middleAngle, innerAngle);
                Vector3f normal = QuantumBatteryAnimationMath.ringNormal(
                        axis, QuantumBatteryAnimationMath.ringTilt(axis), phase);
                Vec3 push = ringPush(entity.getBoundingBox().getCenter().subtract(center), normal,
                        layer.radius(), layer.tubeDiameter() * 0.5, entityRadius);
                if (push.lengthSqr() < EPSILON) continue;

                entity.move(MoverType.SELF, push);
                Vec3 velocity = entity.getDeltaMovement();
                Vec3 direction = push.scale(1.0 / push.length());
                double inwardVelocity = velocity.dot(direction);
                if (inwardVelocity < 0.0) {
                    entity.setDeltaMovement(velocity.subtract(direction.scale(inwardVelocity)));
                }
            }
        }
    }

    private QuantumBatteryCollision() {}
}
