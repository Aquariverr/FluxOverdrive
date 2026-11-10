package com.aquariverr.fluxod.client.render;

import com.aquariverr.fluxod.block.entity.TileQuantumBatteryController;
import com.aquariverr.fluxod.multiblock.QuantumBatteryStructure;
import com.aquariverr.fluxod.register.RegistryBlocks;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.aquariverr.fluxod.client.render.QuantumEnergySphereMath.mix;
import static com.aquariverr.fluxod.client.render.QuantumEnergySphereMath.signed;
import static com.aquariverr.fluxod.client.render.QuantumEnergySphereMath.smoothstep;
import static com.aquariverr.fluxod.client.render.QuantumEnergySphereMath.unit;

public final class QuantumEnergySphereRenderer {

    private static final int CORE_LATITUDE_SEGMENTS = 32;
    private static final int CORE_LONGITUDE_SEGMENTS = 64;
    private static final int SHELL_LATITUDE_SEGMENTS = 24;
    private static final int SHELL_LONGITUDE_SEGMENTS = 48;
    private static final int TESLA_SLOTS = 7;
    private static final int TESLA_SEGMENTS = 18;
    private static final int ARC_SIDES = 6;
    private static final float WORLD_CENTER = 0.5F;
    private static final List<IntersectionOccluder> INTERSECTION_OCCLUDERS = createIntersectionOccluders();

    private static final RenderType PLASMA_CORE = RenderType.create(
            "flux_overdrive_quantum_plasma_core",
            DefaultVertexFormat.POSITION_COLOR,
            VertexFormat.Mode.TRIANGLES,
            RenderType.TRANSIENT_BUFFER_SIZE,
            false,
            false,
            RenderType.CompositeState.builder()
                    .setShaderState(RenderStateShard.POSITION_COLOR_SHADER)
                    .setCullState(RenderStateShard.NO_CULL)
                    .setDepthTestState(RenderStateShard.LEQUAL_DEPTH_TEST)
                    .setWriteMaskState(RenderStateShard.COLOR_DEPTH_WRITE)
                    .createCompositeState(false));

    private static final RenderType PLASMA_GLOW = RenderType.create(
            "flux_overdrive_quantum_plasma_glow",
            DefaultVertexFormat.POSITION_COLOR,
            VertexFormat.Mode.TRIANGLES,
            RenderType.TRANSIENT_BUFFER_SIZE,
            false,
            false,
            RenderType.CompositeState.builder()
                    .setShaderState(RenderStateShard.POSITION_COLOR_SHADER)
                    .setTransparencyState(RenderStateShard.TRANSLUCENT_TRANSPARENCY)
                    .setCullState(RenderStateShard.NO_CULL)
                    .setDepthTestState(RenderStateShard.LEQUAL_DEPTH_TEST)
                    .setWriteMaskState(RenderStateShard.COLOR_WRITE)
                    .createCompositeState(false));

    private static final RenderType WORLD_PLASMA_CORE = RenderType.create(
            "flux_overdrive_quantum_world_plasma_core",
            DefaultVertexFormat.POSITION_COLOR,
            VertexFormat.Mode.TRIANGLES,
            RenderType.TRANSIENT_BUFFER_SIZE,
            false,
            false,
            RenderType.CompositeState.builder()
                    .setShaderState(RenderStateShard.POSITION_COLOR_SHADER)
                    .setCullState(RenderStateShard.NO_CULL)
                    .setDepthTestState(RenderStateShard.LEQUAL_DEPTH_TEST)
                    .setWriteMaskState(RenderStateShard.COLOR_DEPTH_WRITE)
                    .createCompositeState(false));

    private static final RenderType WORLD_PLASMA_GLOW = RenderType.create(
            "flux_overdrive_quantum_world_plasma_glow",
            DefaultVertexFormat.POSITION_COLOR,
            VertexFormat.Mode.TRIANGLES,
            RenderType.TRANSIENT_BUFFER_SIZE,
            false,
            false,
            RenderType.CompositeState.builder()
                    .setShaderState(RenderStateShard.POSITION_COLOR_SHADER)
                    .setTransparencyState(RenderStateShard.TRANSLUCENT_TRANSPARENCY)
                    .setCullState(RenderStateShard.NO_CULL)
                    .setDepthTestState(RenderStateShard.LEQUAL_DEPTH_TEST)
                    .setWriteMaskState(RenderStateShard.COLOR_WRITE)
                    .setOutputState(RenderStateShard.PARTICLES_TARGET)
                    .createCompositeState(false));

    public static void render(GuiGraphics graphics, int centerX, int centerY, float radius,
                              double gameTime, double energyScale, int networkColor,
                              QuantumEnergySphereEffectMath.Effects effects) {
        graphics.flush();
        MultiBufferSource.BufferSource source = Minecraft.getInstance().renderBuffers().bufferSource();
        PoseStack poseStack = graphics.pose();
        poseStack.pushPose();
        poseStack.translate(0.0F, 0.0F, 220.0F);
        Matrix4f matrix = poseStack.last().pose();
        PlasmaPalette palette = PlasmaPalette.from(networkColor);

        double yaw = gameTime * 0.0065;
        double pitch = 0.30 + Math.sin(gameTime * 0.0035) * 0.10;
        VertexConsumer core = source.getBuffer(PLASMA_CORE);
        renderSphereLayer(core, matrix, centerX, centerY, 0.0F, radius, gameTime, energyScale,
                yaw, pitch, CORE_LATITUDE_SEGMENTS, CORE_LONGITUDE_SEGMENTS,
                0, 1.0, 0.0, palette, effects, Double.POSITIVE_INFINITY);
        source.endBatch(PLASMA_CORE);

        VertexConsumer glow = source.getBuffer(PLASMA_GLOW);
        renderSphereLayer(glow, matrix, centerX, centerY, 0.0F, radius * 1.018F, gameTime + 13.0,
                energyScale, -yaw * 0.86, pitch * 0.72,
                SHELL_LATITUDE_SEGMENTS, SHELL_LONGITUDE_SEGMENTS, 84, 0.66, 0.10,
                palette, effects, Double.POSITIVE_INFINITY);
        renderSphereLayer(glow, matrix, centerX, centerY, 0.0F, radius * 1.064F, gameTime + 37.0,
                energyScale, yaw * 0.53, -pitch * 0.58,
                SHELL_LATITUDE_SEGMENTS, SHELL_LONGITUDE_SEGMENTS, 42, 0.48, -0.04,
                palette, effects, Double.POSITIVE_INFINITY);
        renderSphereLayer(glow, matrix, centerX, centerY, 0.0F, radius * 1.12F, gameTime + 71.0,
                energyScale, -yaw * 0.31, pitch * 0.35,
                SHELL_LATITUDE_SEGMENTS, SHELL_LONGITUDE_SEGMENTS, 20, 0.34, 0.16,
                palette, effects, Double.POSITIVE_INFINITY);
        if (energyScale > 0.0) {
            renderTeslaDischarges(glow, matrix, centerX, centerY, 0.0F, radius,
                    gameTime, energyScale, palette, effects, 1.0, TESLA_SLOTS,
                    TESLA_SEGMENTS, 0L, Double.POSITIVE_INFINITY);
        }
        source.endBatch(PLASMA_GLOW);

        poseStack.popPose();
    }

    public static void renderWorld(TileQuantumBatteryController tile, float partialTick,
                                   PoseStack poseStack, MultiBufferSource.BufferSource source,
                                   double distanceSqr, boolean translucentPass) {
        if (!tile.isAnimationActive() || tile.getLevel() == null
                || !tile.areStructureChunksLoadedForRender()) return;

        double gameTime = tile.getLevel().getGameTime() + partialTick;
        double energyScale = tile.getRenderEnergyLogScale(gameTime);
        boolean quenchImminent = tile.isStructureReady() && !tile.isStructureSuspended()
                && !tile.isConstraintPowered() && tile.areRenderInstabilityPenaltiesEnabled();
        QuantumEnergySphereEffectMath.Effects effects = QuantumEnergySphereEffectMath.calculate(
                tile.getStabilityBasisPoints(), quenchImminent, tile.getLeakEventSequence(),
                tile.getRenderLeakPulseAge(gameTime), gameTime);
        float radius = QuantumBatteryRenderMath.worldSphereRadius(
                energyScale, effects.radiusScale());
        double maxExtent = QuantumBatteryRenderMath.worldEffectMaxExtent();
        WorldDetail detail = WorldDetail.forDistance(distanceSqr);
        Matrix4f matrix = poseStack.last().pose();
        PlasmaPalette palette = PlasmaPalette.from(tile.getNetworkColorRgb());
        long positionSalt = mix(tile.getBlockPos().asLong() ^ 0x6A09E667F3BCC909L);
        double yaw = gameTime * 0.0065;
        double pitch = 0.30 + Math.sin(gameTime * 0.0035) * 0.10;
        float outerAngle = QuantumBatteryRenderMath.outerAngle(
                tile.getLevel().getGameTime(), partialTick);
        float middleAngle = QuantumBatteryRenderMath.middleAngle(
                tile.getLevel().getGameTime(), partialTick);
        float innerAngle = QuantumBatteryRenderMath.innerAngle(
                tile.getLevel().getGameTime(), partialTick);

        if (!translucentPass) {
            VertexConsumer core = source.getBuffer(WORLD_PLASMA_CORE);
            renderSphereLayer(core, matrix, WORLD_CENTER, WORLD_CENTER, WORLD_CENTER,
                    radius, gameTime, energyScale, yaw, pitch,
                    detail.coreLatitudeSegments(), detail.coreLongitudeSegments(),
                    0, 1.0, 0.0, palette, effects, maxExtent);
            return;
        }

        VertexConsumer glow = source.getBuffer(WORLD_PLASMA_GLOW);
        renderSphereLayer(glow, matrix, WORLD_CENTER, WORLD_CENTER, WORLD_CENTER,
                radius * 1.018F, gameTime + 13.0, energyScale, -yaw * 0.86, pitch * 0.72,
                detail.shellLatitudeSegments(), detail.shellLongitudeSegments(),
                84, 0.66, 0.10, palette, effects, maxExtent);
        if (detail.shellLayers() >= 2) {
            renderSphereLayer(glow, matrix, WORLD_CENTER, WORLD_CENTER, WORLD_CENTER,
                    radius * 1.064F, gameTime + 37.0, energyScale, yaw * 0.53, -pitch * 0.58,
                    detail.shellLatitudeSegments(), detail.shellLongitudeSegments(),
                    42, 0.48, -0.04, palette, effects, maxExtent);
        }
        if (detail.shellLayers() >= 3) {
            renderSphereLayer(glow, matrix, WORLD_CENTER, WORLD_CENTER, WORLD_CENTER,
                    radius * 1.12F, gameTime + 71.0, energyScale, -yaw * 0.31, pitch * 0.35,
                    detail.shellLatitudeSegments(), detail.shellLongitudeSegments(),
                    20, 0.34, 0.16, palette, effects, maxExtent);
        }
        if (energyScale > 0.0) {
            renderTeslaDischarges(glow, matrix, WORLD_CENTER, WORLD_CENTER, WORLD_CENTER,
                    radius, gameTime, energyScale, palette, effects, 0.10,
                    detail.teslaSlots(), detail.teslaSegments(), positionSalt, maxExtent);
        }
        renderConstraintBeams(glow, matrix, tile, tile.getNetworkColorRgb(),
                outerAngle, middleAngle, innerAngle);
        renderRingStreams(glow, matrix, tile.getStructureTier(), gameTime, positionSalt,
                detail.streamDensity(), outerAngle, middleAngle, innerAngle);
    }

    public static void endWorldBatch(MultiBufferSource.BufferSource source, boolean translucentPass) {
        source.endBatch(translucentPass ? WORLD_PLASMA_GLOW : WORLD_PLASMA_CORE);
    }

    private static void renderSphereLayer(VertexConsumer consumer, Matrix4f matrix,
                                          float centerX, float centerY, float centerZ, float radius,
                                          double gameTime, double energyScale,
                                          double yaw, double pitch, int latitudeSegments,
                                          int longitudeSegments, int layerAlpha,
                                          double displacementScale, double colorShift,
                                          PlasmaPalette palette,
                                          QuantumEnergySphereEffectMath.Effects effects,
                                          double maxExtent) {
        PlasmaVertex[][] vertices = new PlasmaVertex[latitudeSegments + 1][longitudeSegments + 1];
        for (int latitude = 0; latitude <= latitudeSegments; latitude++) {
            double theta = -Math.PI * 0.5 + Math.PI * latitude / latitudeSegments;
            for (int longitude = 0; longitude <= longitudeSegments; longitude++) {
                double phi = Math.PI * 2.0 * longitude / longitudeSegments;
                vertices[latitude][longitude] = sphereVertex(theta, phi, radius, gameTime,
                        energyScale, yaw, pitch, layerAlpha, displacementScale,
                        colorShift, palette, effects);
            }
        }
        for (int latitude = 0; latitude < latitudeSegments; latitude++) {
            for (int longitude = 0; longitude < longitudeSegments; longitude++) {
                PlasmaVertex v00 = vertices[latitude][longitude];
                PlasmaVertex v01 = vertices[latitude][longitude + 1];
                PlasmaVertex v10 = vertices[latitude + 1][longitude];
                PlasmaVertex v11 = vertices[latitude + 1][longitude + 1];
                triangle(consumer, matrix, centerX, centerY, centerZ,
                        v00, v10, v11, maxExtent);
                triangle(consumer, matrix, centerX, centerY, centerZ,
                        v00, v11, v01, maxExtent);
            }
        }
    }

    private static PlasmaVertex sphereVertex(double theta, double phi, float radius,
                                             double gameTime, double energyScale,
                                             double yaw, double pitch, int layerAlpha,
                                             double displacementScale, double colorShift,
                                             PlasmaPalette palette,
                                             QuantumEnergySphereEffectMath.Effects effects) {
        Vec normal = rotate(new Vec(
                Math.cos(theta) * Math.cos(phi),
                Math.sin(theta),
                Math.cos(theta) * Math.sin(phi)), yaw, pitch);
        double broadFlow = Math.sin(phi * 4.0 + Math.sin(theta * 3.0) * 1.8 + gameTime * 0.011);
        double counterFlow = Math.cos(phi * 7.0 - theta * 5.0 - gameTime * 0.009
                + Math.sin(phi * 2.0 + gameTime * 0.004));
        double filamentA = Math.pow(Math.max(0.0,
                Math.sin(phi * 13.0 + theta * 9.0 - gameTime * 0.018)), 10.0);
        double filamentB = Math.pow(1.0 - Math.abs(Math.sin(
                phi * 9.0 - theta * 17.0 + gameTime * 0.014)), 12.0);
        double granulation = Math.sin(phi * 31.0 + theta * 23.0 + gameTime * 0.024)
                * Math.sin(phi * 17.0 - theta * 29.0 - gameTime * 0.017);
        double cells = Math.sin(phi * 19.0 + Math.sin(theta * 11.0) * 1.4 + gameTime * 0.012)
                * Math.cos(theta * 21.0 - gameTime * 0.008);
        double eruption = Math.pow(Math.max(0.0,
                Math.sin(phi * 6.0 + theta * 5.0 + gameTime * 0.0075)), 12.0);
        double broadNoise = surfaceNoise(theta, phi, gameTime,
                10, 6, 0x243F6A8885A308D3L, 150.0);
        double detailNoise = surfaceNoise(theta, phi, gameTime,
                26, 14, 0x13198A2E03707344L, 92.0);
        double microNoise = surfaceNoise(theta, phi, gameTime,
                48, 24, 0xA4093822299F31D0L, 58.0);
        double randomLight = broadNoise * 0.15 + detailNoise * 0.10 + microNoise * 0.045;
        double luminousPocket = Math.pow(Math.max(0.0,
                broadNoise * 0.58 + detailNoise * 0.42), 4.0);
        double displacement = (broadFlow * 0.014 + counterFlow * 0.009
                + granulation * 0.005 + cells * 0.004 + eruption * 0.013
                + detailNoise * 0.004 + microNoise * 0.0015)
                * displacementScale * effects.surfaceDisplacementScale()
                * (0.70 + energyScale * 0.30);
        Vec position = normal.scale(radius * (1.0 + displacement));

        double directional = Math.clamp(normal.dot(new Vec(-0.42, -0.56, 0.71).normalize())
                * 0.5 + 0.5, 0.0, 1.0);
        double rim = Math.pow(1.0 - Math.abs(normal.z()), 1.7);
        double activity = Math.clamp(0.10 + directional * 0.28
                + (broadFlow + 1.0) * 0.075 + Math.abs(counterFlow) * 0.07
                + filamentA * 0.22 + filamentB * 0.18
                + (granulation + 1.0) * 0.045 + Math.abs(cells) * 0.06
                + eruption * 0.26 + luminousPocket * 0.20 + randomLight
                + rim * (layerAlpha == 0 ? 0.03 : 0.12)
                + effects.surfaceActivityBoost() + effects.flashIntensity() * 0.25, 0.0, 1.0);
        int alpha = layerAlpha == 0 ? 255 : (int) Math.round(layerAlpha * Math.clamp(
                0.16 + activity * 0.56 + rim * 0.22 + (filamentA + filamentB) * 0.10,
                0.0, 1.0));
        return new PlasmaVertex(position, palette.surface(
                activity, colorShift, alpha, effects.flashIntensity()));
    }

    private static void renderTeslaDischarges(VertexConsumer consumer, Matrix4f matrix,
                                               float centerX, float centerY, float centerZ,
                                               float radius, double gameTime, double energyScale,
                                               PlasmaPalette palette,
                                               QuantumEnergySphereEffectMath.Effects effects,
                                               double unitScale, int baseSlots, int baseSegments,
                                               long seedSalt, double maxExtent) {
        int slotCount = baseSlots + effects.dischargeSlotBonus();
        for (int slot = 0; slot < slotCount; slot++) {
            double cycle = 20.0 + slot * 2.6;
            double shiftedTime = gameTime + slot * 5.73;
            long epoch = (long) Math.floor(shiftedTime / cycle);
            double phase = shiftedTime / cycle - Math.floor(shiftedTime / cycle);
            long seed = mix(seedSalt ^ (epoch * 0x9E3779B97F4A7C15L
                    + slot * 0xC2B2AE3D27D4EB4FL));
            double activeWindow = Math.min(0.52,
                    0.24 + effects.dischargeActivityBoost() * 0.18);
            double activityChance = Math.clamp(0.10 + energyScale * 0.38
                    + effects.dischargeActivityBoost(), 0.0, 0.96);
            if (phase > activeWindow || unit(seed + 13) > activityChance) continue;

            double progress = phase / activeWindow;
            double envelope = smoothstep(Math.min(1.0, progress * 9.0))
                    * (1.0 - smoothstep(Math.max(0.0, (progress - 0.54) / 0.46)));
            long frameSeed = mix(seed ^ ((long) Math.floor(gameTime * 1.5)
                    * 0xD6E8FEB86659FD93L));
            double intensity = envelope * (0.72 + unit(frameSeed + 4) * 0.28);
            if (intensity <= 0.004) continue;

            double reachRoll = unit(seed + 2);
            boolean extendedDischarge = unit(seed + 10) < Math.min(0.82,
                    0.10 + energyScale * 0.12 + effects.dischargeActivityBoost() * 0.45);
            double reachScale = 0.12 + Math.pow(reachRoll, 2.25) * 0.34;
            if (extendedDischarge) {
                reachScale += 0.18 + unit(seed + 11) * 0.24;
            }
            boolean worldSpace = Double.isFinite(maxExtent);
            Vec startDirection = worldSpace ? randomDirection(seed) : randomVisibleDirection(seed);
            if (extendedDischarge && !worldSpace) {
                startDirection = new Vec(startDirection.x(), startDirection.y() * 0.55,
                        startDirection.z()).normalize();
            }
            Vec tangentA = startDirection.cross(randomDirection(seed + 7)).normalize();
            if (tangentA.lengthSquared() < 1.0E-8) {
                tangentA = startDirection.cross(new Vec(0.0, 1.0, 0.0)).normalize();
            }
            Vec tangentB = startDirection.cross(tangentA).normalize();
            double reach = (4.0 * unitScale + radius * reachScale)
                    * (0.72 + energyScale * 0.28) * effects.dischargeLengthScale();
            Vec start = startDirection.scale(radius * 0.90);
            Vec target = startDirection.scale(radius + reach)
                    .add(tangentA.scale((unit(seed + 3) - 0.5) * reach * 0.82))
                    .add(tangentB.scale((unit(seed + 5) - 0.5) * reach * 0.46));
            int segments = Math.max(5, baseSegments - 2 + (int) Math.floor(unit(seed + 6) * 5.0));
            double jaggedness = 0.42 * unitScale
                    + reach * (0.052 + unit(seed + 9) * 0.026);
            Vec[] primary = buildTeslaPath(start, target, segments, jaggedness, frameSeed);

            double haloWidth = Math.max(0.58 * unitScale, radius * 0.047)
                    * (0.78 + energyScale * 0.22) * effects.dischargeWidthScale();
            double coreWidth = Math.max(0.16 * unitScale, haloWidth * 0.25);
            boolean alternate = (slot & 1) != 0;
            renderArcTube(consumer, matrix, centerX, centerY, centerZ, primary,
                    haloWidth, haloWidth * 0.28,
                    palette.dischargeHalo(alternate, (int) Math.round(105.0 * intensity)), maxExtent);
            renderArcTube(consumer, matrix, centerX, centerY, centerZ, primary,
                    coreWidth, Math.max(0.055 * unitScale, coreWidth * 0.32),
                    palette.dischargeCore((int) Math.round(245.0 * intensity)), maxExtent);

            int branchCount = 1 + (int) Math.floor(effects.dischargeBranchBoost());
            double branchRemainder = effects.dischargeBranchBoost()
                    - Math.floor(effects.dischargeBranchBoost());
            if (unit(seed + 19) < branchRemainder) branchCount++;
            if (unit(seed + 20) < 0.45 + energyScale * 0.35) branchCount++;
            if (unit(seed + 21) < 0.12 + energyScale * 0.28) branchCount++;
            for (int branch = 0; branch < branchCount; branch++) {
                long branchSeed = mix(frameSeed + branch * 0x94D049BB133111EBL);
                int startIndex = 4 + (int) Math.floor(unit(seed + 30 + branch)
                        * Math.max(1, primary.length - 8));
                startIndex = Math.min(startIndex, primary.length - 4);
                Vec branchStart = primary[startIndex];
                Vec forward = primary[startIndex + 1].subtract(primary[startIndex - 1]).normalize();
                Vec branchDirection = forward.scale(0.48)
                        .add(startDirection.scale(0.34))
                        .add(randomDirection(branchSeed + 2).scale(0.68)).normalize();
                if (branchDirection.dot(startDirection) < 0.05) {
                    branchDirection = branchDirection.add(startDirection.scale(0.55)).normalize();
                }
                double branchLength = reach * (0.20 + unit(seed + 40 + branch) * 0.18);
                Vec branchEnd = branchStart.add(branchDirection.scale(branchLength));
                int branchSegments = 6 + (int) Math.floor(unit(seed + 50 + branch) * 4.0);
                Vec[] branchPath = buildTeslaPath(branchStart, branchEnd, branchSegments,
                        jaggedness * 0.68, branchSeed);
                double branchIntensity = intensity * (0.64 + unit(branchSeed + 8) * 0.20);
                renderArcTube(consumer, matrix, centerX, centerY, centerZ, branchPath,
                        haloWidth * 0.54, 0.045 * unitScale,
                        palette.dischargeHalo(!alternate,
                                (int) Math.round(76.0 * branchIntensity)), maxExtent);
                renderArcTube(consumer, matrix, centerX, centerY, centerZ, branchPath,
                        coreWidth * 0.62, 0.025 * unitScale,
                        palette.dischargeCore((int) Math.round(205.0 * branchIntensity)), maxExtent);
            }
        }
    }

    private static Vec[] buildTeslaPath(Vec start, Vec end, int segments,
                                        double jaggedness, long seed) {
        Vec direction = end.subtract(start).normalize();
        Vec first = direction.cross(randomDirection(seed + 1)).normalize();
        if (first.lengthSquared() < 1.0E-8) {
            first = direction.cross(new Vec(0.0, 0.0, 1.0)).normalize();
        }
        if (first.lengthSquared() < 1.0E-8) {
            first = direction.cross(new Vec(0.0, 1.0, 0.0)).normalize();
        }
        Vec second = direction.cross(first).normalize();
        Vec[] path = new Vec[segments + 1];
        for (int segment = 0; segment <= segments; segment++) {
            double t = segment / (double) segments;
            double edgeFade = Math.sin(Math.PI * t);
            double roughness = jaggedness * edgeFade * (0.72 + t * 0.28);
            double offsetA = signed(seed + segment * 0x9E3779B97F4A7C15L) * roughness;
            double offsetB = signed(seed + segment * 0xC2B2AE3D27D4EB4FL + 17) * roughness;
            path[segment] = start.scale(1.0 - t).add(end.scale(t))
                    .add(first.scale(offsetA)).add(second.scale(offsetB));
        }
        path[0] = start;
        path[segments] = end;
        return path;
    }

    private static void renderArcTube(VertexConsumer consumer, Matrix4f matrix,
                                      float centerX, float centerY, float centerZ, Vec[] path,
                                      double startWidth, double endWidth, int color,
                                      double maxExtent) {
        for (int segment = 0; segment < path.length - 1; segment++) {
            double t = (segment + 0.5) / (path.length - 1.0);
            double width = (startWidth * (1.0 - t) + endWidth * t)
                    * (0.84 + Math.sin(Math.PI * t) * 0.16);
            tubeSegment(consumer, matrix, centerX, centerY, centerZ,
                    path[segment], path[segment + 1], width,
                    scaleAlpha(color, 1.0 - t * 0.28), maxExtent);
        }
    }

    private static void tubeSegment(VertexConsumer consumer, Matrix4f matrix,
                                    float centerX, float centerY, float centerZ,
                                    Vec start, Vec end, double width, int color,
                                    double maxExtent) {
        tubeSegment(consumer, matrix, centerX, centerY, centerZ,
                start, end, width, color, maxExtent, ARC_SIDES);
    }

    private static void tubeSegment(VertexConsumer consumer, Matrix4f matrix,
                                    float centerX, float centerY, float centerZ,
                                    Vec start, Vec end, double width, int color,
                                    double maxExtent, int sides) {
        Vec axis = end.subtract(start).normalize();
        Vec first = axis.cross(new Vec(0.0, 0.0, 1.0)).normalize();
        if (first.lengthSquared() < 1.0E-8) first = axis.cross(new Vec(0.0, 1.0, 0.0)).normalize();
        Vec second = axis.cross(first).normalize();
        for (int side = 0; side < sides; side++) {
            double angle0 = Math.PI * 2.0 * side / sides;
            double angle1 = Math.PI * 2.0 * (side + 1) / sides;
            Vec offset0 = first.scale(Math.cos(angle0) * width).add(second.scale(Math.sin(angle0) * width));
            Vec offset1 = first.scale(Math.cos(angle1) * width).add(second.scale(Math.sin(angle1) * width));
            Vec start0 = start.add(offset0);
            Vec start1 = start.add(offset1);
            Vec end0 = end.add(offset0);
            Vec end1 = end.add(offset1);
            vertex(consumer, matrix, centerX, centerY, centerZ, start0, color, maxExtent);
            vertex(consumer, matrix, centerX, centerY, centerZ, end0, color, maxExtent);
            vertex(consumer, matrix, centerX, centerY, centerZ, end1, color, maxExtent);
            vertex(consumer, matrix, centerX, centerY, centerZ, start0, color, maxExtent);
            vertex(consumer, matrix, centerX, centerY, centerZ, end1, color, maxExtent);
            vertex(consumer, matrix, centerX, centerY, centerZ, start1, color, maxExtent);
        }
    }

    private static void taperedTubeSegment(VertexConsumer consumer, Matrix4f matrix,
                                           float centerX, float centerY, float centerZ,
                                           Vec start, Vec end, double startWidth,
                                           double endWidth, int color, int sides) {
        Vec axis = end.subtract(start).normalize();
        Vec first = axis.cross(new Vec(0.0, 0.0, 1.0)).normalize();
        if (first.lengthSquared() < 1.0E-8) {
            first = axis.cross(new Vec(0.0, 1.0, 0.0)).normalize();
        }
        Vec second = axis.cross(first).normalize();
        for (int side = 0; side < sides; side++) {
            double angle0 = Math.PI * 2.0 * side / sides;
            double angle1 = Math.PI * 2.0 * (side + 1) / sides;
            Vec direction0 = first.scale(Math.cos(angle0)).add(second.scale(Math.sin(angle0)));
            Vec direction1 = first.scale(Math.cos(angle1)).add(second.scale(Math.sin(angle1)));
            Vec start0 = start.add(direction0.scale(startWidth));
            Vec start1 = start.add(direction1.scale(startWidth));
            Vec end0 = end.add(direction0.scale(endWidth));
            Vec end1 = end.add(direction1.scale(endWidth));
            vertex(consumer, matrix, centerX, centerY, centerZ,
                    start0, color, Double.POSITIVE_INFINITY);
            vertex(consumer, matrix, centerX, centerY, centerZ,
                    end0, color, Double.POSITIVE_INFINITY);
            vertex(consumer, matrix, centerX, centerY, centerZ,
                    end1, color, Double.POSITIVE_INFINITY);
            vertex(consumer, matrix, centerX, centerY, centerZ,
                    start0, color, Double.POSITIVE_INFINITY);
            vertex(consumer, matrix, centerX, centerY, centerZ,
                    end1, color, Double.POSITIVE_INFINITY);
            vertex(consumer, matrix, centerX, centerY, centerZ,
                    start1, color, Double.POSITIVE_INFINITY);
        }
    }

    private static Vec randomVisibleDirection(long seed) {
        double angle = unit(seed + 1) * Math.PI * 2.0;
        double depth = unit(seed + 2) * 1.10 - 0.15;
        double radial = Math.sqrt(Math.max(0.0, 1.0 - depth * depth));
        return new Vec(Math.cos(angle) * radial, Math.sin(angle) * radial, depth).normalize();
    }

    private static Vec randomDirection(long seed) {
        double azimuth = unit(seed + 1) * Math.PI * 2.0;
        double vertical = unit(seed + 2) * 2.0 - 1.0;
        double horizontal = Math.sqrt(Math.max(0.0, 1.0 - vertical * vertical));
        return new Vec(Math.cos(azimuth) * horizontal, vertical,
                Math.sin(azimuth) * horizontal).normalize();
    }

    private static void renderRingStreams(VertexConsumer consumer, Matrix4f matrix,
                                          int maximumTier, double gameTime, long positionSalt,
                                          double density, float outerAngle,
                                          float middleAngle, float innerAngle) {
        for (int tier = 1; tier <= maximumTier; tier++) {
            QuantumBatteryStructure.Layer layer = QuantumBatteryStructure.layer(tier);
            int baseStreamCount = switch (tier) {
                case 1 -> 48;
                case 2 -> 72;
                case 3 -> 104;
                default -> 24;
            };
            int streamCount = Math.max(12, (int) Math.round(baseStreamCount * density));
            double tubeRadius = layer.tubeDiameter() * 0.5;
            int color = ringColor(tier);

            for (QuantumBatteryStructure.Axis axis : QuantumBatteryStructure.Axis.values()) {
                Quaternionf rotation = QuantumBatteryIntersectionMath.ringRotation(
                        tier, axis, outerAngle, middleAngle, innerAngle);
                long ringSeed = mix(positionSalt ^ tier * 0x9E3779B97F4A7C15L
                        ^ (long) axis.ordinal() * 0xC2B2AE3D27D4EB4FL);
                double direction = (tier + axis.ordinal()) % 2 == 0 ? 1.0 : -1.0;
                double angularSpeed = direction * (0.025 + tier * 0.006);
                double phase = gameTime * angularSpeed + unit(ringSeed) * Math.PI * 2.0;

                for (int stream = 0; stream < streamCount; stream++) {
                    long seed = mix(ringSeed + stream * 0x94D049BB133111EBL);
                    double angle = phase + Math.PI * 2.0 * stream / streamCount
                            + signed(seed + 5) * (Math.PI / streamCount) * 0.45;
                    double radialJitter = signed(seed + 11) * tubeRadius * 0.18;
                    double axialJitter = signed(seed + 17) * tubeRadius * 0.32;
                    double beamLength = QuantumBatteryRenderMath.ringStreamLength(
                            tier, unit(seed + 19));
                    double beamWidth = QuantumBatteryRenderMath.ringStreamWidth(
                            tier, unit(seed + 31));
                    Vec sourcePosition = ringPosition(axis, angle,
                            layer.radius() + radialJitter, axialJitter);
                    Vec sourceTangent = ringTangent(axis, angle).scale(direction);
                    Vec start = rotate(sourcePosition.subtract(
                            sourceTangent.scale(beamLength * 0.5)), rotation);
                    Vec end = rotate(sourcePosition.add(
                            sourceTangent.scale(beamLength * 0.5)), rotation);
                    int alpha = 72 + (int) Math.round(unit(seed + 23) * 112.0);
                    int beamColor = withAlpha(blendRgb(color, 0xFFFFFF,
                            0.16 + unit(seed + 29) * 0.28), alpha);
                    tubeSegment(consumer, matrix, WORLD_CENTER, WORLD_CENTER, WORLD_CENTER,
                            start, end, beamWidth, beamColor, Double.POSITIVE_INFINITY, 3);
                }
            }
        }
    }

    private static void renderConstraintBeams(VertexConsumer consumer, Matrix4f matrix,
                                              TileQuantumBatteryController tile, int networkColor,
                                              float outerAngle, float middleAngle,
                                              float innerAngle) {
        if (tile.getLevel() == null) return;

        for (int tier = 1; tier <= tile.getStructureTier(); tier++) {
            var slots = QuantumBatteryStructure.stabilizerSlots(tier);
            for (QuantumBatteryStructure.Cell cell : slots) {
                QuantumBatteryStructure.Offset offset = cell.offset();
                var interfacePos = tile.getBlockPos().offset(offset.x(), offset.y(), offset.z());
                if (!tile.getLevel().isLoaded(interfacePos)) continue;
                var state = tile.getLevel().getBlockState(interfacePos);
                boolean advanced = state.is(RegistryBlocks.QUANTUM_ENERGY_CONSTRAINT_INTERFACE_3X3.get());
                if (!advanced && !state.is(RegistryBlocks.QUANTUM_ENERGY_CONSTRAINT_INTERFACE.get())) {
                    continue;
                }

                InterfaceAxes axes = InterfaceAxes.from(cell);
                if (axes == null) continue;
                int branch = intersectionBranch(offset, axes);
                QuantumBatteryIntersectionMath.IntersectionTransform transform =
                        QuantumBatteryIntersectionMath.intersectionTransform(
                                tier, axes.first(), axes.second(), branch,
                                outerAngle, middleAngle, innerAngle);
                Vector3f direction = transform.targetDirection();
                double slotRadius = QuantumBatteryStructure.stabilizerSlotRadius(tier);
                Vec start = new Vec(direction.x * slotRadius,
                        direction.y * slotRadius, direction.z * slotRadius);
                double interfaceWidth = QuantumBatteryRenderMath.constraintBeamInterfaceWidth(advanced);
                double controllerWidth = QuantumBatteryRenderMath.constraintBeamControllerWidth(advanced);
                double stop = constraintBeamStop(tier, axes, branch, start,
                        interfaceWidth, controllerWidth,
                        tile.getStructureTier(), outerAngle, middleAngle, innerAngle);
                if (stop <= 1.0E-4) continue;
                Vec end = start.scale(1.0 - stop);
                double endWidth = interfaceWidth + (controllerWidth - interfaceWidth) * stop;
                taperedTubeSegment(consumer, matrix, WORLD_CENTER, WORLD_CENTER, WORLD_CENTER,
                        start, end, interfaceWidth, endWidth,
                        withAlpha(networkColor, advanced ? 56 : 46), ARC_SIDES);
                taperedTubeSegment(consumer, matrix, WORLD_CENTER, WORLD_CENTER, WORLD_CENTER,
                        start, end, interfaceWidth * 0.42, endWidth * 0.42,
                        withAlpha(networkColor, advanced ? 210 : 180), ARC_SIDES);
            }
        }
    }

    private static double constraintBeamStop(int sourceTier, InterfaceAxes sourceAxes,
                                             int sourceBranch, Vec start,
                                             double interfaceWidth, double controllerWidth,
                                             int maximumTier, float outerAngle,
                                             float middleAngle, float innerAngle) {
        double nearest = 1.0;
        Vec controller = new Vec(0.0, 0.0, 0.0);
        for (IntersectionOccluder occluder : INTERSECTION_OCCLUDERS) {
            if (occluder.tier() > maximumTier
                    || occluder.tier() == sourceTier
                    && occluder.axes().equals(sourceAxes)
                    && occluder.branch() == sourceBranch) {
                continue;
            }

            QuantumBatteryIntersectionMath.IntersectionTransform transform =
                    QuantumBatteryIntersectionMath.intersectionTransform(
                            occluder.tier(), occluder.axes().first(), occluder.axes().second(),
                            occluder.branch(), outerAngle, middleAngle, innerAngle);
            double radius = QuantumBatteryStructure.layer(occluder.tier()).radius();
            Quaternionf inverse = new Quaternionf(transform.rotation()).invert();
            Vector3f sourceAnchor = new Vector3f(transform.sourceDirection()).mul((float) radius);
            Vector3f targetAnchor = new Vector3f(transform.targetDirection()).mul((float) radius);
            Vec localStart = inverseIntersectionPoint(start, sourceAnchor, targetAnchor, inverse);
            Vec localEnd = inverseIntersectionPoint(controller, sourceAnchor, targetAnchor, inverse);
            double hit = QuantumBatteryRenderMath.taperedSegmentAabbIntersection(
                    localStart.x(), localStart.y(), localStart.z(),
                    localEnd.x(), localEnd.y(), localEnd.z(),
                    interfaceWidth, controllerWidth,
                    occluder.minX(), occluder.minY(), occluder.minZ(),
                    occluder.maxX(), occluder.maxY(), occluder.maxZ());
            if (hit < nearest) nearest = hit;
        }

        if (nearest >= 1.0) return 1.0;
        double length = Math.sqrt(start.lengthSquared());
        return Math.max(0.0, nearest - (length < 1.0E-8 ? 0.0 : 0.03 / length));
    }

    private static Vec inverseIntersectionPoint(Vec point, Vector3f sourceAnchor,
                                                Vector3f targetAnchor, Quaternionf inverse) {
        Vector3f local = new Vector3f(
                (float) point.x() - targetAnchor.x,
                (float) point.y() - targetAnchor.y,
                (float) point.z() - targetAnchor.z)
                .rotate(inverse)
                .add(sourceAnchor);
        return new Vec(local.x, local.y, local.z);
    }

    private static List<IntersectionOccluder> createIntersectionOccluders() {
        Map<IntersectionOccluderKey, MutableIntersectionBounds> grouped = new HashMap<>();
        for (int tier = 1; tier <= 3; tier++) {
            for (QuantumBatteryStructure.Cell cell : QuantumBatteryStructure.intersections(tier)) {
                InterfaceAxes axes = InterfaceAxes.from(cell);
                if (axes == null) continue;
                int branch = intersectionBranch(cell.offset(), axes);
                IntersectionOccluderKey key = new IntersectionOccluderKey(tier, axes, branch);
                grouped.computeIfAbsent(key, ignored -> new MutableIntersectionBounds())
                        .include(cell.offset());
            }
        }

        List<IntersectionOccluder> result = new ArrayList<>(grouped.size());
        for (Map.Entry<IntersectionOccluderKey, MutableIntersectionBounds> entry : grouped.entrySet()) {
            IntersectionOccluderKey key = entry.getKey();
            MutableIntersectionBounds bounds = entry.getValue();
            result.add(new IntersectionOccluder(key.tier(), key.axes(), key.branch(),
                    bounds.minX, bounds.minY, bounds.minZ,
                    bounds.maxX, bounds.maxY, bounds.maxZ));
        }
        return List.copyOf(result);
    }

    private static int intersectionBranch(QuantumBatteryStructure.Offset offset, InterfaceAxes axes) {
        Vector3f direction = QuantumBatteryIntersectionMath.sourceIntersectionDirection(
                axes.first(), axes.second(), 1);
        float dot = offset.x() * direction.x + offset.y() * direction.y + offset.z() * direction.z;
        return dot < 0.0F ? -1 : 1;
    }

    private static Vec ringPosition(QuantumBatteryStructure.Axis axis, double angle,
                                    double radius, double axialOffset) {
        double first = Math.cos(angle) * radius;
        double second = Math.sin(angle) * radius;
        return switch (axis) {
            case X -> new Vec(axialOffset, first, second);
            case Y -> new Vec(first, axialOffset, second);
            case Z -> new Vec(first, second, axialOffset);
        };
    }

    private static Vec ringTangent(QuantumBatteryStructure.Axis axis, double angle) {
        double first = -Math.sin(angle);
        double second = Math.cos(angle);
        return switch (axis) {
            case X -> new Vec(0.0, first, second);
            case Y -> new Vec(first, 0.0, second);
            case Z -> new Vec(first, second, 0.0);
        };
    }

    private static Vec rotate(Vec value, Quaternionf rotation) {
        Vector3f vector = new Vector3f((float) value.x(), (float) value.y(), (float) value.z())
                .rotate(rotation);
        return new Vec(vector.x, vector.y, vector.z);
    }

    private static int ringColor(int tier) {
        return switch (tier) {
            case 1 -> 0x4FC2B8;
            case 2 -> 0x6C9EE0;
            case 3 -> 0xC06DCB;
            default -> 0xFFFFFF;
        };
    }

    private static void triangle(VertexConsumer consumer, Matrix4f matrix,
                                 float centerX, float centerY, float centerZ,
                                 PlasmaVertex first, PlasmaVertex second, PlasmaVertex third,
                                 double maxExtent) {
        vertex(consumer, matrix, centerX, centerY, centerZ,
                first.position(), first.color(), maxExtent);
        vertex(consumer, matrix, centerX, centerY, centerZ,
                second.position(), second.color(), maxExtent);
        vertex(consumer, matrix, centerX, centerY, centerZ,
                third.position(), third.color(), maxExtent);
    }

    private static void vertex(VertexConsumer consumer, Matrix4f matrix,
                               float centerX, float centerY, float centerZ,
                               Vec position, int color, double maxExtent) {
        Vec bounded = clampExtent(position, maxExtent);
        consumer.addVertex(matrix, centerX + (float) bounded.x(), centerY + (float) bounded.y(),
                centerZ + (float) bounded.z()).setColor(color);
    }

    private static Vec clampExtent(Vec position, double maxExtent) {
        if (!Double.isFinite(maxExtent)) return position;
        double lengthSquared = position.lengthSquared();
        double maximum = QuantumBatteryRenderMath.clampWorldEffectLength(
                position.x(), position.y(), position.z(), maxExtent);
        if (lengthSquared <= maximum * maximum || lengthSquared < 1.0E-16) return position;
        return position.scale(maximum / Math.sqrt(lengthSquared));
    }

    private static Vec rotate(Vec value, double yaw, double pitch) {
        double yawCos = Math.cos(yaw);
        double yawSin = Math.sin(yaw);
        double x = value.x() * yawCos + value.z() * yawSin;
        double z = -value.x() * yawSin + value.z() * yawCos;
        double pitchCos = Math.cos(pitch);
        double pitchSin = Math.sin(pitch);
        return new Vec(x, value.y() * pitchCos - z * pitchSin,
                value.y() * pitchSin + z * pitchCos);
    }

    private static int nearbyColor(int color, boolean clockwise) {
        int red = color >> 16 & 0xFF;
        int green = color >> 8 & 0xFF;
        int blue = color & 0xFF;
        if (clockwise) {
            return packRgb(red * 0.90 + blue * 0.10,
                    green * 0.90 + red * 0.10,
                    blue * 0.90 + green * 0.10);
        }
        return packRgb(red * 0.90 + green * 0.10,
                green * 0.90 + blue * 0.10,
                blue * 0.90 + red * 0.10);
    }

    private static int blendRgb(int from, int to, double amount) {
        double t = Math.clamp(amount, 0.0, 1.0);
        int red = (int) Math.round((from >> 16 & 0xFF) * (1.0 - t) + (to >> 16 & 0xFF) * t);
        int green = (int) Math.round((from >> 8 & 0xFF) * (1.0 - t) + (to >> 8 & 0xFF) * t);
        int blue = (int) Math.round((from & 0xFF) * (1.0 - t) + (to & 0xFF) * t);
        return red << 16 | green << 8 | blue;
    }

    private static int packRgb(double red, double green, double blue) {
        return Math.clamp((int) Math.round(red), 0, 255) << 16
                | Math.clamp((int) Math.round(green), 0, 255) << 8
                | Math.clamp((int) Math.round(blue), 0, 255);
    }

    private static int withAlpha(int color, int alpha) {
        return Math.clamp(alpha, 0, 255) << 24 | color & 0xFFFFFF;
    }

    private static int scaleAlpha(int color, double scale) {
        return withAlpha(color, (int) Math.round((color >>> 24) * Math.clamp(scale, 0.0, 1.0)));
    }

    private static double surfaceNoise(double theta, double phi, double gameTime,
                                       int longitudeCells, int latitudeCells,
                                       long salt, double period) {
        double noisePhi = Math.abs(Math.cos(theta)) < 1.0E-6 ? 0.0 : phi;
        double longitudePosition = noisePhi / (Math.PI * 2.0) * longitudeCells;
        int longitudeFloor = (int) Math.floor(longitudePosition);
        double longitudeBlend = smoothstep(longitudePosition - longitudeFloor);
        int longitude0 = Math.floorMod(longitudeFloor, longitudeCells);
        int longitude1 = (longitude0 + 1) % longitudeCells;

        double latitudePosition = Math.clamp((theta + Math.PI * 0.5) / Math.PI,
                0.0, 1.0) * latitudeCells;
        int latitude0 = Math.min((int) Math.floor(latitudePosition), latitudeCells - 1);
        int latitude1 = latitude0 + 1;
        double latitudeBlend = smoothstep(latitudePosition - latitude0);

        long epoch = (long) Math.floor(gameTime / period);
        double timeBlend = smoothstep(gameTime / period - Math.floor(gameTime / period));
        double current = noiseLayer(epoch, longitude0, longitude1,
                latitude0, latitude1, longitudeBlend, latitudeBlend, salt);
        double next = noiseLayer(epoch + 1, longitude0, longitude1,
                latitude0, latitude1, longitudeBlend, latitudeBlend, salt);
        return lerp(current, next, timeBlend);
    }

    private static double noiseLayer(long epoch, int longitude0, int longitude1,
                                     int latitude0, int latitude1,
                                     double longitudeBlend, double latitudeBlend,
                                     long salt) {
        double lower = lerp(latticeNoise(epoch, longitude0, latitude0, salt),
                latticeNoise(epoch, longitude1, latitude0, salt), longitudeBlend);
        double upper = lerp(latticeNoise(epoch, longitude0, latitude1, salt),
                latticeNoise(epoch, longitude1, latitude1, salt), longitudeBlend);
        return lerp(lower, upper, latitudeBlend);
    }

    private static double latticeNoise(long epoch, int longitude, int latitude, long salt) {
        return signed(salt ^ epoch * 0x9E3779B97F4A7C15L
                ^ longitude * 0xC2B2AE3D27D4EB4FL
                ^ latitude * 0x165667B19E3779F9L);
    }

    private static double lerp(double from, double to, double amount) {
        return from + (to - from) * amount;
    }

    private record PlasmaPalette(int deep, int base, int adjacentA,
                                 int adjacentB, int bright, int hot) {

        private static PlasmaPalette from(int networkColor) {
            int base = networkColor & 0xFFFFFF;
            if (base == 0) base = 0x43D9FF;
            int adjacentA = nearbyColor(base, true);
            int adjacentB = nearbyColor(base, false);
            return new PlasmaPalette(
                    blendRgb(base, 0x02040A, 0.68),
                    base,
                    adjacentA,
                    adjacentB,
                    blendRgb(adjacentA, 0xFFFFFF, 0.58),
                    blendRgb(base, 0xFFFFFF, 0.88));
        }

        private int surface(double activity, double shift, int alpha, double flash) {
            double value = Math.clamp(activity + shift, 0.0, 1.0);
            int color;
            if (value < 0.26) {
                color = blendRgb(deep, base, value / 0.26);
            } else if (value < 0.56) {
                color = blendRgb(base, adjacentA, (value - 0.26) / 0.30);
            } else if (value < 0.82) {
                color = blendRgb(adjacentA, bright, (value - 0.56) / 0.26);
            } else {
                color = blendRgb(bright, hot, (value - 0.82) / 0.18);
            }
            return withAlpha(blendRgb(color, 0xFFFFFF, flash * 0.72), alpha);
        }

        private int dischargeHalo(boolean alternate, int alpha) {
            return withAlpha(blendRgb(base, alternate ? adjacentB : adjacentA, 0.48), alpha);
        }

        private int dischargeCore(int alpha) {
            return withAlpha(hot, alpha);
        }
    }

    private record PlasmaVertex(Vec position, int color) {}

    private record InterfaceAxes(QuantumBatteryStructure.Axis first,
                                 QuantumBatteryStructure.Axis second) {
        private static InterfaceAxes from(QuantumBatteryStructure.Cell cell) {
            QuantumBatteryStructure.Axis first = null;
            QuantumBatteryStructure.Axis second = null;
            for (QuantumBatteryStructure.Axis axis : QuantumBatteryStructure.Axis.values()) {
                if (!cell.axes().contains(axis)) continue;
                if (first == null) first = axis;
                else {
                    second = axis;
                    break;
                }
            }
            return first == null || second == null ? null : new InterfaceAxes(first, second);
        }
    }

    private record IntersectionOccluderKey(int tier, InterfaceAxes axes, int branch) {}

    private record IntersectionOccluder(int tier, InterfaceAxes axes, int branch,
                                        double minX, double minY, double minZ,
                                        double maxX, double maxY, double maxZ) {}

    private static final class MutableIntersectionBounds {
        private double minX = Double.POSITIVE_INFINITY;
        private double minY = Double.POSITIVE_INFINITY;
        private double minZ = Double.POSITIVE_INFINITY;
        private double maxX = Double.NEGATIVE_INFINITY;
        private double maxY = Double.NEGATIVE_INFINITY;
        private double maxZ = Double.NEGATIVE_INFINITY;

        private void include(QuantumBatteryStructure.Offset offset) {
            minX = Math.min(minX, offset.x() - 0.5);
            minY = Math.min(minY, offset.y() - 0.5);
            minZ = Math.min(minZ, offset.z() - 0.5);
            maxX = Math.max(maxX, offset.x() + 0.5);
            maxY = Math.max(maxY, offset.y() + 0.5);
            maxZ = Math.max(maxZ, offset.z() + 0.5);
        }
    }

    private record WorldDetail(int coreLatitudeSegments, int coreLongitudeSegments,
                               int shellLatitudeSegments, int shellLongitudeSegments,
                               int shellLayers, int teslaSlots, int teslaSegments,
                               double streamDensity) {
        private static WorldDetail forDistance(double distanceSqr) {
            if (distanceSqr <= 32.0 * 32.0) {
                return new WorldDetail(18, 36, 14, 28, 3, 7, 14, 1.0);
            }
            if (distanceSqr <= 96.0 * 96.0) {
                return new WorldDetail(14, 28, 10, 20, 2, 5, 11, 0.65);
            }
            return new WorldDetail(10, 20, 8, 16, 1, 3, 8, 0.28);
        }
    }

    private record Vec(double x, double y, double z) {
        private Vec add(Vec other) {
            return new Vec(x + other.x, y + other.y, z + other.z);
        }

        private Vec subtract(Vec other) {
            return new Vec(x - other.x, y - other.y, z - other.z);
        }

        private Vec scale(double scale) {
            return new Vec(x * scale, y * scale, z * scale);
        }

        private double dot(Vec other) {
            return x * other.x + y * other.y + z * other.z;
        }

        private Vec cross(Vec other) {
            return new Vec(y * other.z - z * other.y,
                    z * other.x - x * other.z,
                    x * other.y - y * other.x);
        }

        private double lengthSquared() {
            return dot(this);
        }

        private Vec normalize() {
            double length = Math.sqrt(lengthSquared());
            return length < 1.0E-8 ? new Vec(0.0, 0.0, 0.0) : scale(1.0 / length);
        }
    }

    private QuantumEnergySphereRenderer() {}
}
