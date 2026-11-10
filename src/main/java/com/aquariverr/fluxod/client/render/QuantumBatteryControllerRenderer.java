package com.aquariverr.fluxod.client.render;

import com.aquariverr.fluxod.block.entity.TileQuantumBatteryController;
import com.aquariverr.fluxod.client.model.ctm.ConnectedTextureBakedModel;
import com.aquariverr.fluxod.FluxOverdrive;
import com.aquariverr.fluxod.multiblock.QuantumBatteryAssembler;
import com.aquariverr.fluxod.multiblock.QuantumBatteryStructure;
import com.aquariverr.fluxod.register.RegistryBlocks;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexBuffer;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.ChunkPos;
import net.neoforged.neoforge.client.model.data.ModelData;
import org.joml.Matrix4f;
import org.joml.Matrix3f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public final class QuantumBatteryControllerRenderer {

    public static final ModelResourceLocation ADVANCED_INTERFACE_TIER_2_MODEL = ModelResourceLocation.standalone(
            FluxOverdrive.location("block/quantum_energy_constraint_interface_3x3_assembled_tier_2"));
    public static final ModelResourceLocation ADVANCED_INTERFACE_TIER_3_MODEL = ModelResourceLocation.standalone(
            FluxOverdrive.location("block/quantum_energy_constraint_interface_3x3_assembled_tier_3"));
    private static final float ADVANCED_INTERFACE_PLANE_SCALE = 97.0F / 96.0F;
    private static final int ADVANCED_INTERFACE_MIN_BLOCK_LIGHT = 8;
    private static final Map<Long, CachedRenderMesh> MESH_CACHE = new ConcurrentHashMap<>();
    private static final RenderType BATTERY_TRANSLUCENT = RenderType.create(
            "flux_overdrive_quantum_battery_translucent",
            DefaultVertexFormat.BLOCK,
            VertexFormat.Mode.QUADS,
            RenderType.TRANSIENT_BUFFER_SIZE,
            false,
            false,
            RenderType.CompositeState.builder()
                    .setShaderState(RenderStateShard.RENDERTYPE_TRANSLUCENT_SHADER)
                    .setTextureState(RenderStateShard.BLOCK_SHEET_MIPPED)
                    .setTransparencyState(RenderStateShard.TRANSLUCENT_TRANSPARENCY)
                    .setCullState(RenderStateShard.NO_CULL)
                    .setDepthTestState(RenderStateShard.LEQUAL_DEPTH_TEST)
                    .setWriteMaskState(RenderStateShard.COLOR_WRITE)
                    .setLightmapState(RenderStateShard.LIGHTMAP)
                    .setOutputState(RenderStateShard.PARTICLES_TARGET)
                    .createCompositeState(true));

    private record AxisPair(QuantumBatteryStructure.Axis first, QuantumBatteryStructure.Axis second) {
        private static AxisPair fromAxes(Iterable<QuantumBatteryStructure.Axis> axes) {
            QuantumBatteryStructure.Axis first = null;
            QuantumBatteryStructure.Axis second = null;
            for (QuantumBatteryStructure.Axis axis : QuantumBatteryStructure.Axis.values()) {
                if (!contains(axes, axis)) continue;
                if (first == null) first = axis;
                else if (second == null) {
                    second = axis;
                    break;
                }
            }
            if (first == null || second == null) {
                throw new IllegalArgumentException("Intersection cell must contain at least two axes");
            }
            return new AxisPair(first, second);
        }

        private static boolean contains(Iterable<QuantumBatteryStructure.Axis> axes,
                                        QuantumBatteryStructure.Axis target) {
            for (QuantumBatteryStructure.Axis axis : axes) {
                if (axis == target) return true;
            }
            return false;
        }
    }

    private record RingKey(int tier, QuantumBatteryStructure.Axis axis) {}

    private record IntersectionKey(int tier, AxisPair pair, int branch) {}

    private record FormedInterfacePart(BlockPos centerPos, int tier, AxisPair pair, int branch) {}

    public static void clearCache() {
        if (!RenderSystem.isOnRenderThread()) {
            RenderSystem.recordRenderCall(QuantumBatteryControllerRenderer::clearCache);
            return;
        }
        for (CachedRenderMesh mesh : MESH_CACHE.values()) mesh.close();
        MESH_CACHE.clear();
    }

    public static void clearChunk(ChunkPos chunkPos) {
        if (!RenderSystem.isOnRenderThread()) {
            RenderSystem.recordRenderCall(() -> clearChunk(chunkPos));
            return;
        }
        MESH_CACHE.forEach((pos, mesh) -> {
            if (structureIntersectsChunk(BlockPos.of(pos), chunkPos)
                    && MESH_CACHE.remove(pos, mesh)) {
                mesh.close();
            }
        });
    }

    public static void retainControllers(Collection<BlockPos> controllers) {
        Set<Long> retained = controllers.stream().map(BlockPos::asLong)
                .collect(java.util.stream.Collectors.toSet());
        MESH_CACHE.forEach((pos, mesh) -> {
            if (!retained.contains(pos) && MESH_CACHE.remove(pos, mesh)) mesh.close();
        });
    }

    private static boolean structureIntersectsChunk(BlockPos controllerPos, ChunkPos chunkPos) {
        int radius = 15;
        int minChunkX = Math.floorDiv(controllerPos.getX() - radius, 16);
        int maxChunkX = Math.floorDiv(controllerPos.getX() + radius, 16);
        int minChunkZ = Math.floorDiv(controllerPos.getZ() - radius, 16);
        int maxChunkZ = Math.floorDiv(controllerPos.getZ() + radius, 16);
        return chunkPos.x >= minChunkX && chunkPos.x <= maxChunkX
                && chunkPos.z >= minChunkZ && chunkPos.z <= maxChunkZ;
    }

    public static void renderDynamic(TileQuantumBatteryController tile, float partialTick,
                                     PoseStack poseStack, Matrix4f baseModelView, Matrix4f projection,
                                     boolean translucentPass) {
        if (!tile.isAnimationActive() || tile.getLevel() == null
                || !tile.areStructureChunksLoadedForRender()) return;

        BlockRenderDispatcher dispatcher = Minecraft.getInstance().getBlockRenderer();
        long gameTime = tile.getLevel().getGameTime();
        float outerAngle = QuantumBatteryRenderMath.outerAngle(gameTime, partialTick);
        float middleAngle = QuantumBatteryRenderMath.middleAngle(gameTime, partialTick);
        float innerAngle = QuantumBatteryRenderMath.innerAngle(gameTime, partialTick);
        CachedRenderMesh mesh = getMesh(tile, dispatcher);

        for (int tier = 1; tier <= tile.getStructureTier(); tier++) {
            for (QuantumBatteryStructure.Axis axis : QuantumBatteryStructure.Axis.values()) {
                poseStack.pushPose();
                poseStack.translate(0.5, 0.5, 0.5);
                poseStack.mulPose(QuantumBatteryIntersectionMath.ringRotation(
                        tier, axis, outerAngle, middleAngle, innerAngle));
                poseStack.translate(-0.5, -0.5, -0.5);
                mesh.render(new RingKey(tier, axis), poseStack, baseModelView, projection, translucentPass);
                poseStack.popPose();
            }
        }
        mesh.renderIntersections(poseStack, baseModelView, projection, translucentPass,
                outerAngle, middleAngle, innerAngle);
    }

    private static CachedRenderMesh getMesh(TileQuantumBatteryController tile, BlockRenderDispatcher dispatcher) {
        long key = tile.getBlockPos().asLong();
        CachedRenderMesh previous = MESH_CACHE.get(key);
        long structureSignature = tile.getStructureRenderSignature();
        if (previous != null && previous.tier == tile.getStructureTier()
                && previous.structureSignature == structureSignature) return previous;

        CachedRenderMesh replacement = CachedRenderMesh.build(tile, dispatcher, structureSignature);
        if (previous != null) previous.close();
        MESH_CACHE.put(key, replacement);
        return replacement;
    }

    private static RenderType renderType(BlockState state) {
        return state.is(RegistryBlocks.TRANSMON_QUBIT_CHAIN_CASING_GLASS.get())
                || state.is(RegistryBlocks.WEAK_COUPLING_CAVITY_QED_CASING_GLASS.get())
                || state.is(RegistryBlocks.STRONG_COUPLING_CAVITY_QED_CASING_GLASS.get())
                ? RenderType.translucent()
                : RenderType.solid();
    }

    private static final class CachedRenderMesh implements AutoCloseable {
        private final int tier;
        private final long structureSignature;
        private final Map<RingKey, GpuAxisMesh> rings;
        private final Map<IntersectionKey, GpuAxisMesh> intersections;

        private CachedRenderMesh(int tier, long structureSignature,
                                 Map<RingKey, GpuAxisMesh> rings,
                                 Map<IntersectionKey, GpuAxisMesh> intersections) {
            this.tier = tier;
            this.structureSignature = structureSignature;
            this.rings = rings;
            this.intersections = intersections;
        }

        private static CachedRenderMesh build(TileQuantumBatteryController tile,
                                              BlockRenderDispatcher dispatcher, long structureSignature) {
            Map<RingKey, RecordingVertexConsumer> solid = new java.util.HashMap<>();
            Map<RingKey, RecordingVertexConsumer> translucent = new java.util.HashMap<>();
            Map<IntersectionKey, RecordingVertexConsumer> intersectionSolid = new java.util.HashMap<>();
            Map<IntersectionKey, RecordingVertexConsumer> intersectionTranslucent = new java.util.HashMap<>();
            for (int tier = 1; tier <= tile.getStructureTier(); tier++) {
                for (QuantumBatteryStructure.Axis axis : QuantumBatteryStructure.Axis.values()) {
                    RingKey key = new RingKey(tier, axis);
                    solid.put(key, new RecordingVertexConsumer());
                    translucent.put(key, new RecordingVertexConsumer());
                }
            }

            if (tile.getLevel() == null) {
                return new CachedRenderMesh(tile.getStructureTier(), structureSignature, Map.of(), Map.of());
            }
            RandomSource random = RandomSource.create(0L);
            Map<Integer, BakedModel> assembledInterfaceModels = new java.util.HashMap<>();
            BakedModel tierTwoInterfaceModel = Minecraft.getInstance().getModelManager()
                    .getModel(ADVANCED_INTERFACE_TIER_2_MODEL);
            if (tierTwoInterfaceModel != Minecraft.getInstance().getModelManager().getMissingModel()) {
                assembledInterfaceModels.put(2, tierTwoInterfaceModel);
            }
            BakedModel tierThreeInterfaceModel = Minecraft.getInstance().getModelManager()
                    .getModel(ADVANCED_INTERFACE_TIER_3_MODEL);
            if (tierThreeInterfaceModel != Minecraft.getInstance().getModelManager().getMissingModel()) {
                assembledInterfaceModels.put(3, tierThreeInterfaceModel);
            }
            Map<BlockPos, FormedInterfacePart> advancedInterfaceParts = findFormedInterfaceParts(
                    tile, RegistryBlocks.QUANTUM_ENERGY_CONSTRAINT_INTERFACE_3X3.get(), 2);
            Map<Integer, BlockState> glassStates = new java.util.HashMap<>();
            Map<Integer, BakedModel> glassModels = new java.util.HashMap<>();
            Map<Integer, Map<QuantumBatteryStructure.Axis, Set<BlockPos>>> virtualGlassPositions =
                    new java.util.HashMap<>();
            for (int tier = 1; tier <= tile.getStructureTier(); tier++) {
                Map<QuantumBatteryStructure.Axis, Set<BlockPos>> positionsByAxis = new EnumMap<>(
                        QuantumBatteryStructure.Axis.class);
                for (QuantumBatteryStructure.Axis axis : QuantumBatteryStructure.Axis.values()) {
                    positionsByAxis.put(axis, new HashSet<>());
                }
                for (QuantumBatteryStructure.Cell cell : QuantumBatteryStructure.layer(tier).cells()) {
                    QuantumBatteryStructure.Offset offset = cell.offset();
                    BlockPos blockPos = tile.getBlockPos().offset(offset.x(), offset.y(), offset.z()).immutable();
                    for (QuantumBatteryStructure.Axis axis : cell.axes()) {
                        positionsByAxis.get(axis).add(blockPos);
                    }
                }
                virtualGlassPositions.put(tier, positionsByAxis);
            }
            for (int tier = 1; tier <= tile.getStructureTier(); tier++) {
                for (QuantumBatteryStructure.Cell cell : QuantumBatteryStructure.layer(tier).cells()) {
                    QuantumBatteryStructure.Offset offset = cell.offset();
                    BlockPos blockPos = tile.getBlockPos().offset(offset.x(), offset.y(), offset.z());
                    if (!tile.getLevel().isLoaded(blockPos)) continue;
                    BlockState state = tile.getLevel().getBlockState(blockPos);
                    if (state.isAir()) continue;

                    FormedInterfacePart formedPart = advancedInterfaceParts.get(blockPos);

                    BakedModel model = dispatcher.getBlockModel(state);
                    ModelData modelData = model.getModelData(tile.getLevel(), blockPos, state, ModelData.EMPTY)
                            .derive()
                            .with(ConnectedTextureBakedModel.DYNAMIC_RENDER, true)
                            .build();
                    RenderType renderType = renderType(state);
                    if (renderType == RenderType.translucent()) {
                        QuantumBatteryStructure.Axis axis = cell.axes().iterator().next();
                        Set<BlockPos> ringPositions = virtualGlassPositions.get(tier).get(axis);
                        long connections = ConnectedTextureBakedModel.computeVirtualConnections(
                                blockPos, ringPositions::contains);
                        modelData = modelData.derive()
                                .with(ConnectedTextureBakedModel.CONNECTIONS_OVERRIDE, connections)
                                .build();
                    }
                    RecordingVertexConsumer consumer;
                    if (cell.isIntersection()) {
                        AxisPair pair = AxisPair.fromAxes(cell.axes());
                        IntersectionKey key = new IntersectionKey(tier, pair,
                                intersectionBranch(cell.offset(), pair));
                        Map<IntersectionKey, RecordingVertexConsumer> target =
                                renderType == RenderType.translucent()
                                        ? intersectionTranslucent : intersectionSolid;
                        consumer = target.computeIfAbsent(key, ignored -> new RecordingVertexConsumer());
                    } else {
                        QuantumBatteryStructure.Axis axis = cell.axes().iterator().next();
                        RingKey ringKey = new RingKey(tier, axis);
                        consumer = renderType == RenderType.translucent()
                                ? translucent.get(ringKey)
                                : solid.get(ringKey);
                    }
                    if (renderType == RenderType.translucent() && !cell.isIntersection()) {
                        QuantumBatteryStructure.Axis axis = cell.axes().iterator().next();
                        consumer.beginVirtualCulling(blockPos,
                                virtualGlassPositions.get(tier).get(axis));
                    }
                    random.setSeed(blockPos.asLong());
                    PoseStack localPose = new PoseStack();
                    localPose.translate(offset.x(), offset.y(), offset.z());
                    dispatcher.renderBatched(state, blockPos, tile.getLevel(), localPose, consumer,
                            renderType == RenderType.translucent() ? false : true,
                            random, modelData, renderType(state));
                    consumer.endVirtualCulling();

                    if (formedPart != null && blockPos.equals(formedPart.centerPos())) {
                        BakedModel assembledInterfaceModel = assembledInterfaceModels.get(formedPart.tier());
                        if (assembledInterfaceModel != null) {
                            IntersectionKey key = new IntersectionKey(formedPart.tier(), formedPart.pair(),
                                    formedPart.branch());
                            RecordingVertexConsumer panelConsumer = intersectionSolid.computeIfAbsent(
                                    key, ignored -> new RecordingVertexConsumer());
                            ModelData assembledModelData = assembledInterfaceModel.getModelData(
                                            tile.getLevel(), blockPos, state, ModelData.EMPTY)
                                    .derive()
                                    .with(ConnectedTextureBakedModel.DYNAMIC_RENDER, true)
                                    .build();
                            random.setSeed(blockPos.asLong());
                            PoseStack panelPose = new PoseStack();
                            panelPose.translate(offset.x(), offset.y(), offset.z());
                            panelPose.translate(0.5F, 0.5F, 0.5F);
                            panelPose.mulPose(advancedInterfaceRotation(formedPart));
                            panelPose.scale(ADVANCED_INTERFACE_PLANE_SCALE,
                                    ADVANCED_INTERFACE_PLANE_SCALE, 1.0F);
                            panelPose.translate(-0.5F, -0.5F, -0.5F);
                            panelConsumer.beginMinimumBlockLight(ADVANCED_INTERFACE_MIN_BLOCK_LIGHT);
                            dispatcher.getModelRenderer().tesselateBlock(
                                    tile.getLevel(), assembledInterfaceModel, state, blockPos, panelPose,
                                    panelConsumer, false, random, state.getSeed(blockPos),
                                    OverlayTexture.NO_OVERLAY, assembledModelData, RenderType.solid());
                            panelConsumer.endMinimumBlockLight();
                        }
                    }

                    if (formedPart == null && cell.isIntersection()
                            && renderType != RenderType.translucent()) {
                        BlockState glassState = glassStates.get(tier);
                        if (glassState == null) {
                            glassState = QuantumBatteryAssembler.expectedBlock(
                                    tier, QuantumBatteryStructure.CellType.GLASS).defaultBlockState();
                            glassStates.put(tier, glassState);
                        }
                        BakedModel glassModel = glassModels.get(tier);
                        if (glassModel == null) {
                            glassModel = dispatcher.getBlockModel(glassState);
                            glassModels.put(tier, glassModel);
                        }
                        for (QuantumBatteryStructure.Axis axis : cell.axes()) {
                            Set<BlockPos> ringPositions = virtualGlassPositions.get(tier).get(axis);
                            long connections = ConnectedTextureBakedModel.computeVirtualConnections(
                                    blockPos, ringPositions::contains);
                            ModelData glassModelData = glassModel.getModelData(
                                            tile.getLevel(), blockPos, glassState, ModelData.EMPTY)
                                    .derive()
                                    .with(ConnectedTextureBakedModel.DYNAMIC_RENDER, true)
                                    .with(ConnectedTextureBakedModel.CONNECTIONS_OVERRIDE, connections)
                                    .build();
                            RecordingVertexConsumer glassConsumer = translucent.get(new RingKey(tier, axis));
                            glassConsumer.beginVirtualCulling(blockPos,
                                    virtualGlassPositions.get(tier).get(axis));
                            PoseStack glassPose = new PoseStack();
                            glassPose.translate(offset.x() + 0.5F, offset.y() + 0.5F, offset.z() + 0.5F);
                            glassPose.scale(1.015F, 1.015F, 1.015F);
                            glassPose.translate(-0.5F, -0.5F, -0.5F);
                            random.setSeed(blockPos.asLong());
                            dispatcher.renderBatched(glassState, blockPos, tile.getLevel(), glassPose,
                                    glassConsumer, false, random, glassModelData, RenderType.translucent());
                            glassConsumer.endVirtualCulling();
                        }
                    }
                }
            }

            Map<RingKey, GpuAxisMesh> meshes = new java.util.HashMap<>();
            for (RingKey key : solid.keySet()) {
                solid.get(key).finish();
                translucent.get(key).finish();
                meshes.put(key, new GpuAxisMesh(
                        GpuBatch.upload(solid.get(key).vertices),
                        GpuBatch.upload(translucent.get(key).vertices)));
            }
            Map<IntersectionKey, GpuAxisMesh> intersectionMeshes = new java.util.HashMap<>();
            for (IntersectionKey key : intersectionSolid.keySet()) {
                RecordingVertexConsumer solidConsumer = intersectionSolid.get(key);
                RecordingVertexConsumer translucentConsumer = intersectionTranslucent.get(key);
                solidConsumer.finish();
                if (translucentConsumer != null) translucentConsumer.finish();
                intersectionMeshes.put(key, new GpuAxisMesh(
                        GpuBatch.upload(solidConsumer.vertices),
                        translucentConsumer == null ? null : GpuBatch.upload(translucentConsumer.vertices)));
            }
            for (Map.Entry<IntersectionKey, RecordingVertexConsumer> entry : intersectionTranslucent.entrySet()) {
                if (intersectionMeshes.containsKey(entry.getKey())) continue;
                entry.getValue().finish();
                intersectionMeshes.put(entry.getKey(), new GpuAxisMesh(
                        null, GpuBatch.upload(entry.getValue().vertices)));
            }
            return new CachedRenderMesh(tile.getStructureTier(), structureSignature,
                    Map.copyOf(meshes), Map.copyOf(intersectionMeshes));
        }

        private void render(RingKey key, PoseStack poseStack,
                            Matrix4f baseModelView, Matrix4f projection, boolean translucentPass) {
            GpuAxisMesh mesh = rings.get(key);
            if (mesh == null) return;
            Matrix4f modelView = new Matrix4f(baseModelView).mul(poseStack.last().pose());
            mesh.render(modelView, projection, translucentPass);
        }

        private void renderIntersections(PoseStack poseStack, Matrix4f baseModelView, Matrix4f projection,
                                         boolean translucentPass, float outerAngle, float middleAngle,
                                         float innerAngle) {
            for (Map.Entry<IntersectionKey, GpuAxisMesh> entry : intersections.entrySet()) {
                IntersectionKey key = entry.getKey();
                QuantumBatteryIntersectionMath.IntersectionTransform transform =
                        QuantumBatteryIntersectionMath.intersectionTransform(
                                key.tier(), key.pair().first(), key.pair().second(), key.branch(),
                                outerAngle, middleAngle, innerAngle);
                float radius = QuantumBatteryStructure.layer(key.tier()).radius();
                Vector3f source = new Vector3f(transform.sourceDirection()).mul(radius);
                Vector3f target = new Vector3f(transform.targetDirection()).mul(radius);

                poseStack.pushPose();
                poseStack.translate(0.5, 0.5, 0.5);
                poseStack.translate(target.x, target.y, target.z);
                poseStack.mulPose(transform.rotation());
                poseStack.translate(-source.x, -source.y, -source.z);
                poseStack.translate(-0.5, -0.5, -0.5);
                Matrix4f modelView = new Matrix4f(baseModelView).mul(poseStack.last().pose());
                entry.getValue().render(modelView, projection, translucentPass);
                poseStack.popPose();
            }
        }

        @Override
        public void close() {
            rings.values().forEach(GpuAxisMesh::close);
            intersections.values().forEach(GpuAxisMesh::close);
        }
    }

    private static Map<BlockPos, FormedInterfacePart> findFormedInterfaceParts(
            TileQuantumBatteryController tile, Block centerBlock, int minimumTier) {
        if (tile.getLevel() == null) return Map.of();

        Map<BlockPos, FormedInterfacePart> parts = new java.util.HashMap<>();
        for (int tier = minimumTier; tier <= tile.getStructureTier(); tier++) {
            for (QuantumBatteryStructure.Cell centerCell : QuantumBatteryStructure.layer(tier).cells()) {
                List<QuantumBatteryStructure.Offset> footprint =
                        QuantumBatteryStructure.threeByThreeStabilizerFootprint(tier, centerCell);
                if (footprint.isEmpty()) continue;

                BlockPos centerPos = tile.getBlockPos().offset(
                        centerCell.offset().x(), centerCell.offset().y(), centerCell.offset().z()).immutable();
                if (!tile.getLevel().isLoaded(centerPos)
                        || !tile.getLevel().getBlockState(centerPos).is(centerBlock)) {
                    continue;
                }

                Block casing = QuantumBatteryAssembler.expectedBlock(
                        tier, QuantumBatteryStructure.CellType.CASING);
                boolean valid = true;
                for (QuantumBatteryStructure.Offset offset : footprint) {
                    BlockPos partPos = tile.getBlockPos().offset(offset.x(), offset.y(), offset.z());
                    if (!tile.getLevel().isLoaded(partPos)) {
                        valid = false;
                        break;
                    }
                    if (partPos.equals(centerPos)) continue;
                    if (!tile.getLevel().getBlockState(partPos).is(casing)) {
                        valid = false;
                        break;
                    }
                }
                if (!valid) continue;

                AxisPair pair = AxisPair.fromAxes(centerCell.axes());
                FormedInterfacePart part = new FormedInterfacePart(
                        centerPos, tier, pair, intersectionBranch(centerCell.offset(), pair));
                for (QuantumBatteryStructure.Offset offset : footprint) {
                    parts.put(tile.getBlockPos().offset(offset.x(), offset.y(), offset.z()).immutable(), part);
                }
            }
        }
        return Map.copyOf(parts);
    }

    private static Quaternionf advancedInterfaceRotation(FormedInterfacePart part) {
        Vector3f sourceDirection = QuantumBatteryIntersectionMath.sourceIntersectionDirection(
                part.pair().first(), part.pair().second(), part.branch());
        Vector3f firstNormal = QuantumBatteryIntersectionMath.axisVector(part.pair().first());
        Vector3f tangent = new Vector3f(sourceDirection).cross(firstNormal).normalize();
        return new Quaternionf().setFromNormalized(new Matrix3f(firstNormal, tangent, sourceDirection));
    }

    private static int intersectionBranch(QuantumBatteryStructure.Offset offset, AxisPair pair) {
        Vector3f direction = QuantumBatteryIntersectionMath.sourceIntersectionDirection(
                pair.first(), pair.second(), 1);
        float dot = offset.x() * direction.x + offset.y() * direction.y + offset.z() * direction.z;
        return dot < 0.0F ? -1 : 1;
    }

    private record GpuAxisMesh(GpuBatch solid, GpuBatch translucent) implements AutoCloseable {
        private void render(Matrix4f modelView, Matrix4f projection, boolean translucentPass) {
            if (!translucentPass && solid != null) {
                solid.render(RenderType.solid(), modelView, projection,
                        GameRenderer.getRendertypeSolidShader());
            }
            if (translucentPass && translucent != null) {
                translucent.render(BATTERY_TRANSLUCENT, modelView, projection,
                        GameRenderer.getRendertypeTranslucentShader());
            }
        }

        @Override
        public void close() {
            if (solid != null) solid.close();
            if (translucent != null) translucent.close();
        }
    }

    private record GpuBatch(VertexBuffer buffer) implements AutoCloseable {
        private static GpuBatch upload(List<CachedVertex> vertices) {
            if (vertices.isEmpty()) return null;

            ByteBufferBuilder backing = new ByteBufferBuilder(
                    Math.max(256, vertices.size() * DefaultVertexFormat.BLOCK.getVertexSize()));
            try {
                BufferBuilder builder = new BufferBuilder(backing, VertexFormat.Mode.QUADS, DefaultVertexFormat.BLOCK);
                for (CachedVertex vertex : vertices) {
                    builder.addVertex(vertex.x(), vertex.y(), vertex.z())
                            .setColor(vertex.color())
                            .setUv(vertex.u(), vertex.v())
                            .setOverlay(vertex.overlay())
                            .setLight(vertex.light())
                            .setNormal(vertex.normalX(), vertex.normalY(), vertex.normalZ());
                }
                MeshData meshData = builder.buildOrThrow();
                VertexBuffer buffer = new VertexBuffer(VertexBuffer.Usage.STATIC);
                buffer.bind();
                try {
                    buffer.upload(meshData);
                } finally {
                    VertexBuffer.unbind();
                }
                return new GpuBatch(buffer);
            } finally {
                backing.close();
            }
        }

        private void render(RenderType renderType, Matrix4f modelView, Matrix4f projection,
                            net.minecraft.client.renderer.ShaderInstance shader) {
            renderType.setupRenderState();
            try {
                RenderSystem.setShaderTexture(0, InventoryMenu.BLOCK_ATLAS);
                buffer.bind();
                buffer.drawWithShader(modelView, projection, shader);
            } finally {
                VertexBuffer.unbind();
                renderType.clearRenderState();
            }
        }

        @Override
        public void close() {
            buffer.close();
        }
    }

    private record CachedVertex(float x, float y, float z, int color, float u, float v,
                                int overlay, int light, float normalX, float normalY, float normalZ) {}

    private static final class RecordingVertexConsumer implements VertexConsumer {
        private final List<CachedVertex> vertices = new ArrayList<>();
        private final List<CachedVertex> pendingQuad = new ArrayList<>(4);
        private PendingVertex pending;
        private BlockPos virtualCullingPosition;
        private Set<BlockPos> virtualCullingPositions;
        private int color = -1;
        private float u;
        private float v;
        private int overlay;
        private int light;
        private int minimumBlockLight = -1;
        private float normalX;
        private float normalY = 1.0F;
        private float normalZ;

        @Override
        public VertexConsumer addVertex(float x, float y, float z) {
            finishPending();
            pending = new PendingVertex(x, y, z, color, u, v, overlay, light,
                    normalX, normalY, normalZ);
            return this;
        }

        @Override
        public VertexConsumer setColor(int red, int green, int blue, int alpha) {
            int packed = (alpha & 0xFF) << 24 | (red & 0xFF) << 16
                    | (green & 0xFF) << 8 | (blue & 0xFF);
            if (pending != null) pending.color = packed;
            else color = packed;
            return this;
        }

        @Override
        public VertexConsumer setUv(float u, float v) {
            if (pending != null) {
                pending.u = u;
                pending.v = v;
            } else {
                this.u = u;
                this.v = v;
            }
            return this;
        }

        @Override
        public VertexConsumer setUv1(int u, int v) {
            int packed = (v & 0xFFFF) << 16 | (u & 0xFFFF);
            if (pending != null) pending.overlay = packed;
            else overlay = packed;
            return this;
        }

        @Override
        public VertexConsumer setUv2(int u, int v) {
            int packed = (v & 0xFFFF) << 16 | (u & 0xFFFF);
            if (minimumBlockLight >= 0) {
                packed = QuantumBatteryRenderMath.withMinimumBlockLight(packed, minimumBlockLight);
            }
            if (pending != null) pending.light = packed;
            else light = packed;
            return this;
        }

        @Override
        public VertexConsumer setNormal(float x, float y, float z) {
            if (pending != null) {
                pending.normalX = x;
                pending.normalY = y;
                pending.normalZ = z;
            } else {
                normalX = x;
                normalY = y;
                normalZ = z;
            }
            return this;
        }

        private void finish() {
            finishPending();
            finishQuad();
        }

        private void beginVirtualCulling(BlockPos position, Set<BlockPos> occupied) {
            finish();
            virtualCullingPosition = position;
            virtualCullingPositions = occupied;
        }

        private void endVirtualCulling() {
            finish();
            virtualCullingPosition = null;
            virtualCullingPositions = null;
        }

        private void beginMinimumBlockLight(int minimumLevel) {
            finish();
            minimumBlockLight = minimumLevel;
        }

        private void endMinimumBlockLight() {
            finish();
            minimumBlockLight = -1;
        }

        private void finishPending() {
            if (pending == null) return;
            pendingQuad.add(pending.toVertex());
            pending = null;
            if (pendingQuad.size() == 4) finishQuad();
        }

        private void finishQuad() {
            if (pendingQuad.isEmpty()) return;
            if (virtualCullingPosition == null || virtualCullingPositions == null
                    || isVirtualQuadVisible(pendingQuad)) {
                vertices.addAll(pendingQuad);
            }
            pendingQuad.clear();
        }

        private boolean isVirtualQuadVisible(List<CachedVertex> quad) {
            CachedVertex vertex = quad.get(0);
            Direction face = Direction.getNearest(vertex.normalX(), vertex.normalY(), vertex.normalZ());
            return !virtualCullingPositions.contains(virtualCullingPosition.relative(face));
        }

        private static final class PendingVertex {
            private final float x;
            private final float y;
            private final float z;
            private int color;
            private float u;
            private float v;
            private int overlay;
            private int light;
            private float normalX;
            private float normalY;
            private float normalZ;

            private PendingVertex(float x, float y, float z, int color, float u, float v,
                                  int overlay, int light, float normalX, float normalY, float normalZ) {
                this.x = x;
                this.y = y;
                this.z = z;
                this.color = color;
                this.u = u;
                this.v = v;
                this.overlay = overlay;
                this.light = light;
                this.normalX = normalX;
                this.normalY = normalY;
                this.normalZ = normalZ;
            }

            private CachedVertex toVertex() {
                return new CachedVertex(x, y, z, color, u, v, overlay, light,
                        normalX, normalY, normalZ);
            }
        }
    }
}
