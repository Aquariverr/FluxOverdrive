package com.aquariverr.fluxod;

import com.aquariverr.fluxod.block.entity.TileFluxWirelessCrystal;
import com.aquariverr.fluxod.block.entity.TileQuantumBatteryController;
import com.aquariverr.fluxod.block.entity.TileQuantumEntanglementController;
import com.aquariverr.fluxod.block.QuantumEntanglementControllerBlock;
import com.aquariverr.fluxod.client.render.QuantumBatteryControllerRenderer;
import com.aquariverr.fluxod.client.render.QuantumEnergySphereRenderer;
import com.aquariverr.fluxod.item.FluxLinkToolItem;
import com.aquariverr.fluxod.multiblock.QuantumBatteryAssembler;
import com.aquariverr.fluxod.multiblock.QuantumBatteryPlugin;
import com.aquariverr.fluxod.multiblock.QuantumBatteryStructure;
import com.aquariverr.fluxod.multiblock.QuantumEntanglementStructure;
import com.aquariverr.fluxod.multiblock.QuantumEntanglementTemplate;
import com.aquariverr.fluxod.register.RegistryBlocks;
import com.aquariverr.fluxod.register.RegistryItems;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.event.level.ChunkEvent;

import java.util.Collection;
import java.util.List;

@EventBusSubscriber(modid = FluxOverdrive.MODID, value = Dist.CLIENT)
public class FluxOverdriveClientEvents {

    private static final double MAX_HIGHLIGHT_DISTANCE_SQR = 128.0 * 128.0;
    private static final double MAX_DYNAMIC_DISTANCE_SQR = 256.0 * 256.0;

    @SubscribeEvent
    public static void onLevelUnload(LevelEvent.Unload event) {
        if (event.getLevel() instanceof Level level && level.isClientSide) {
            TileQuantumBatteryController.clearLevelControllerState(level);
            QuantumBatteryControllerRenderer.clearCache();
        }
    }

    @SubscribeEvent
    public static void onChunkUnload(ChunkEvent.Unload event) {
        if (event.getLevel() instanceof Level level && level.isClientSide) {
            QuantumBatteryControllerRenderer.clearChunk(event.getChunk().getPos());
        }
    }

    @SubscribeEvent
    public static void onChunkLoad(ChunkEvent.Load event) {
        if (event.getLevel() instanceof Level level && level.isClientSide) {
            QuantumBatteryControllerRenderer.clearChunk(event.getChunk().getPos());
        }
    }

    @SubscribeEvent
    @SuppressWarnings("resource")
    public static void onRenderLevelStage(RenderLevelStageEvent event) {
        Minecraft mc = Minecraft.getInstance();
        Level level = mc.level;
        if (level == null) return;
        if (event.getStage() == RenderLevelStageEvent.Stage.AFTER_BLOCK_ENTITIES) {
            renderQuantumBatteries(event, level, false);
            return;
        }
        if (event.getStage() == RenderLevelStageEvent.Stage.AFTER_PARTICLES) {
            renderQuantumBatteries(event, level, true);
            return;
        }
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) return;

        Player player = mc.player;
        if (player == null) return;

        ItemStack stack = player.getMainHandItem();
        if (!(stack.getItem() instanceof FluxLinkToolItem)) {
            if (isQuantumReplacementItem(stack)) {
                renderQuantumReplacementHighlights(event, level, player, stack);
                return;
            }
            stack = player.getOffhandItem();
            if (!(stack.getItem() instanceof FluxLinkToolItem)) {
                if (isQuantumReplacementItem(stack)) {
                    renderQuantumReplacementHighlights(event, level, player, stack);
                }
                return;
            }
        }

        renderQuantumReplacementHighlights(event, level, player, stack);

        GlobalPos quantumBattery = FluxLinkToolItem.getQuantumBatteryBound(stack);
        if (quantumBattery != null) {
            if (!quantumBattery.dimension().equals(level.dimension())) return;
            PoseStack poseStack = event.getPoseStack();
            Camera camera = mc.gameRenderer.getMainCamera();
            Vec3 camPos = camera.getPosition();
            MultiBufferSource.BufferSource source = mc.renderBuffers().bufferSource();
            BlockPos batteryPos = quantumBattery.pos();
            BlockEntity batteryEntity = level.isLoaded(batteryPos) ? level.getBlockEntity(batteryPos) : null;
            poseStack.pushPose();
            poseStack.translate(-camPos.x, -camPos.y, -camPos.z);
            if (Vec3.atCenterOf(batteryPos).distanceToSqr(camPos) <= MAX_HIGHLIGHT_DISTANCE_SQR) {
                renderCrystalHighlight(poseStack, source, batteryPos, batteryEntity instanceof TileQuantumBatteryController);
            }
            source.endBatch(RenderType.LINES);
            poseStack.popPose();
            return;
        }

        if (!FluxLinkToolItem.isBound(stack)) return;

        GlobalPos boundPos = FluxLinkToolItem.getBound(stack);
        if (boundPos == null) return;
        if (!boundPos.dimension().equals(level.dimension())) return;

        PoseStack poseStack = event.getPoseStack();
        Camera camera = mc.gameRenderer.getMainCamera();
        Vec3 camPos = camera.getPosition();
        MultiBufferSource.BufferSource source = mc.renderBuffers().bufferSource();

        poseStack.pushPose();
        poseStack.translate(-camPos.x, -camPos.y, -camPos.z);

        BlockPos crystalPos = boundPos.pos();
        BlockEntity crystalEntity = level.isLoaded(crystalPos) ? level.getBlockEntity(crystalPos) : null;

        if (Vec3.atCenterOf(crystalPos).distanceToSqr(camPos) <= MAX_HIGHLIGHT_DISTANCE_SQR) {
            renderCrystalHighlight(poseStack, source, crystalPos, crystalEntity instanceof TileFluxWirelessCrystal);
        }

        if (crystalEntity instanceof TileFluxWirelessCrystal crystal) {
            List<TileFluxWirelessCrystal.LinkTarget> targets = crystal.getLinkedTargets();
            for (TileFluxWirelessCrystal.LinkTarget target : targets) {
                BlockPos targetPos = target.pos().pos();
                if (target.pos().dimension().equals(level.dimension()) && level.isLoaded(targetPos)
                        && Vec3.atCenterOf(targetPos).distanceToSqr(camPos) <= MAX_HIGHLIGHT_DISTANCE_SQR) {
                    renderLinkTargetHighlight(poseStack, source, target);
                }
            }
        }

        source.endBatch(RenderType.LINES);
        poseStack.popPose();
    }

    private static void renderQuantumBatteries(RenderLevelStageEvent event, Level level, boolean translucentPass) {
        Minecraft mc = Minecraft.getInstance();
        Camera camera = event.getCamera();
        Vec3 cameraPos = camera.getPosition();
        float partialTick = event.getPartialTick().getGameTimeDeltaPartialTick(false);

        PoseStack poseStack = event.getPoseStack();
        MultiBufferSource.BufferSource source = mc.renderBuffers().bufferSource();
        poseStack.pushPose();
        poseStack.translate(-cameraPos.x, -cameraPos.y, -cameraPos.z);
        Collection<BlockPos> activeControllers = TileQuantumBatteryController.getClientActiveControllerPositions();
        QuantumBatteryControllerRenderer.retainControllers(activeControllers);
        for (BlockPos controllerPos : activeControllers) {
            if (!level.isLoaded(controllerPos)
                    || Vec3.atCenterOf(controllerPos).distanceToSqr(cameraPos) > MAX_DYNAMIC_DISTANCE_SQR) {
                continue;
            }
            if (!event.getFrustum().isVisible(new AABB(controllerPos).inflate(16.0))) continue;
            if (!(level.getBlockEntity(controllerPos) instanceof TileQuantumBatteryController controller)) continue;

            poseStack.pushPose();
            poseStack.translate(controllerPos.getX(), controllerPos.getY(), controllerPos.getZ());
            QuantumBatteryControllerRenderer.renderDynamic(controller, partialTick, poseStack,
                    event.getModelViewMatrix(), event.getProjectionMatrix(), translucentPass);
            QuantumEnergySphereRenderer.renderWorld(controller, partialTick, poseStack, source,
                    Vec3.atCenterOf(controllerPos).distanceToSqr(cameraPos), translucentPass);
            poseStack.popPose();
        }
        QuantumEnergySphereRenderer.endWorldBatch(source, translucentPass);
        poseStack.popPose();
    }

    private static void renderQuantumReplacementHighlights(RenderLevelStageEvent event, Level level,
                                                            Player player, ItemStack stack) {
        boolean entanglementPort = isEntanglementPortItem(stack);
        QuantumBatteryPlugin batteryPlugin = getHeldQuantumBatteryPlugin(stack);
        if (!entanglementPort && batteryPlugin == null) return;

        Camera camera = event.getCamera();
        Vec3 cameraPos = camera.getPosition();
        PoseStack poseStack = event.getPoseStack();
        MultiBufferSource.BufferSource source = Minecraft.getInstance().renderBuffers().bufferSource();
        poseStack.pushPose();
        poseStack.translate(-cameraPos.x, -cameraPos.y, -cameraPos.z);

        if (batteryPlugin != null) {
            renderBatteryReplacementHighlights(level, player, cameraPos, poseStack, source,
                    batteryPlugin);
        }
        if (entanglementPort) {
            renderEntanglementReplacementHighlights(level, player, cameraPos, poseStack, source);
        }

        source.endBatch(RenderType.LINES);
        poseStack.popPose();
    }

    private static boolean isQuantumReplacementItem(ItemStack stack) {
        return isEntanglementPortItem(stack)
                || getHeldQuantumBatteryPlugin(stack) != null;
    }

    private static boolean isEntanglementPortItem(ItemStack stack) {
        return stack.is(RegistryItems.QUANTUM_ENERGY_CONSTRAINT_ENTANGLEMENT_PORT.get())
                || stack.is(RegistryItems.QUANTUM_ENTANGLEMENT_INPUT_PORT.get())
                || stack.is(RegistryItems.QUANTUM_ENTANGLEMENT_OUTPUT_PORT.get());
    }

    private static QuantumBatteryPlugin getHeldQuantumBatteryPlugin(ItemStack stack) {
        if (stack.getItem() instanceof BlockItem blockItem
                && blockItem.getBlock() instanceof QuantumBatteryPlugin plugin) {
            return plugin;
        }
        return null;
    }

    private static void renderBatteryReplacementHighlights(Level level, Player player, Vec3 cameraPos,
                                                            PoseStack poseStack, MultiBufferSource source,
                                                            QuantumBatteryPlugin plugin) {
        Collection<BlockPos> controllers = TileQuantumBatteryController.getLoadedControllersNear(
                level, player.blockPosition(), 96);
        for (BlockPos controllerPos : controllers) {
            if (!level.isLoaded(controllerPos)
                    || Vec3.atCenterOf(controllerPos).distanceToSqr(cameraPos) > MAX_HIGHLIGHT_DISTANCE_SQR) continue;
            if (!(level.getBlockEntity(controllerPos) instanceof TileQuantumBatteryController controller)) continue;

            for (int tier = 1; tier <= controller.getCoreLevel(); tier++) {
                if (!plugin.canInstallInRing(tier)) continue;
                for (QuantumBatteryStructure.Cell cell : QuantumBatteryStructure.stabilizerSlots(tier)) {
                    BlockPos target = controllerPos.offset(cell.offset().x(), cell.offset().y(), cell.offset().z());
                    if (!level.isLoaded(target)
                            || Vec3.atCenterOf(target).distanceToSqr(cameraPos) > MAX_HIGHLIGHT_DISTANCE_SQR) continue;
                    if (!canReplaceWithQuantumInterface(level.getBlockState(target), tier, cell)) continue;
                    renderReplacementBox(poseStack, source, target, 0.1f, 0.9f, 1.0f);
                }
            }
        }
    }

    private static boolean canReplaceWithQuantumInterface(net.minecraft.world.level.block.state.BlockState state,
                                                            int tier, QuantumBatteryStructure.Cell cell) {
        if (state.is(QuantumBatteryAssembler.expectedBlock(tier, cell.type()))) return true;
        return state.canBeReplaced();
    }

    private static void renderEntanglementReplacementHighlights(Level level, Player player, Vec3 cameraPos,
                                                                 PoseStack poseStack, MultiBufferSource source) {
        Collection<BlockPos> controllers = TileQuantumEntanglementController.getLoadedControllersNear(
                level, player.blockPosition(), 64);
        for (BlockPos controllerPos : controllers) {
            if (!level.isLoaded(controllerPos)
                    || Vec3.atCenterOf(controllerPos).distanceToSqr(cameraPos) > MAX_HIGHLIGHT_DISTANCE_SQR) continue;
            if (!(level.getBlockEntity(controllerPos) instanceof TileQuantumEntanglementController)) continue;

            BlockState controllerState = level.getBlockState(controllerPos);
            for (QuantumEntanglementTemplate.Cell cell : QuantumEntanglementStructure.cells()) {
                if (cell.type() != QuantumEntanglementTemplate.CellType.CASING) continue;
                BlockPos target = QuantumEntanglementStructure.worldPos(controllerPos,
                        controllerState.getValue(QuantumEntanglementControllerBlock.FACING),
                        cell.offset());
                if (!level.isLoaded(target)
                        || Vec3.atCenterOf(target).distanceToSqr(cameraPos) > MAX_HIGHLIGHT_DISTANCE_SQR) continue;
                BlockState state = level.getBlockState(target);
                if (!state.is(RegistryBlocks.QUANTUM_ENTANGLEMENT_CASING.get()) && !state.canBeReplaced()) continue;
                renderReplacementBox(poseStack, source, target, 0.9f, 0.65f, 0.1f);
            }
        }
    }

    private static void renderReplacementBox(PoseStack poseStack, MultiBufferSource source, BlockPos pos,
                                             float red, float green, float blue) {
        LevelRenderer.renderLineBox(poseStack, source.getBuffer(RenderType.LINES),
                new AABB(pos).inflate(0.025), red, green, blue, 1.0f);
    }

    private static void renderCrystalHighlight(PoseStack poseStack, MultiBufferSource source, BlockPos pos, boolean valid) {
        AABB aabb = new AABB(pos).inflate(0.02);
        float r = valid ? 0f : 1f;
        float g = valid ? 1f : 0f;
        float b = 0f;

        VertexConsumer consumer = source.getBuffer(RenderType.LINES);
        LevelRenderer.renderLineBox(poseStack, consumer, aabb, r, g, b, 1f);
    }

    private static void renderLinkTargetHighlight(PoseStack poseStack, MultiBufferSource source,
                                                  TileFluxWirelessCrystal.LinkTarget target) {
        BlockPos pos = target.pos().pos();
        Direction side = target.side();

        float r = 0.3f;
        float g = 0.5f;
        float b = 1f;
        float alpha = 0.6f;

        poseStack.pushPose();
        poseStack.translate(pos.getX(), pos.getY(), pos.getZ());

        VertexConsumer consumer = source.getBuffer(RenderType.LINES);

        switch (side) {
            case DOWN ->
                LevelRenderer.renderLineBox(poseStack, consumer, 0, -0.01, 0, 1, 0, 1, r, g, b, alpha);
            case UP ->
                LevelRenderer.renderLineBox(poseStack, consumer, 0, 1, 0, 1, 1.01, 1, r, g, b, alpha);
            case NORTH ->
                LevelRenderer.renderLineBox(poseStack, consumer, 0, 0, -0.01, 1, 1, 0, r, g, b, alpha);
            case SOUTH ->
                LevelRenderer.renderLineBox(poseStack, consumer, 0, 0, 1, 1, 1, 1.01, r, g, b, alpha);
            case WEST ->
                LevelRenderer.renderLineBox(poseStack, consumer, -0.01, 0, 0, 0, 1, 1, r, g, b, alpha);
            case EAST ->
                LevelRenderer.renderLineBox(poseStack, consumer, 1, 0, 0, 1.01, 1, 1, r, g, b, alpha);
        }

        poseStack.popPose();
    }
}
