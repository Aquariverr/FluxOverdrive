package com.aquariverr.fluxod;

import com.aquariverr.fluxod.block.entity.TileFluxLinkCrystal;
import com.aquariverr.fluxod.item.FluxLinkToolItem;
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
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

import java.util.List;

@EventBusSubscriber(modid = FluxOverdrive.MODID, value = Dist.CLIENT)
public class FluxOverdriveClientEvents {

    @SubscribeEvent
    @SuppressWarnings("resource")
    public static void onRenderLevelStage(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) return;

        Minecraft mc = Minecraft.getInstance();
        Player player = mc.player;
        if (player == null) return;

        ItemStack stack = player.getMainHandItem();
        if (!(stack.getItem() instanceof FluxLinkToolItem)) {
            stack = player.getOffhandItem();
            if (!(stack.getItem() instanceof FluxLinkToolItem)) return;
        }

        if (!FluxLinkToolItem.isBound(stack)) return;

        GlobalPos boundPos = FluxLinkToolItem.getBound(stack);
        Level level = player.level();
        if (boundPos.dimension() != level.dimension()) return;

        PoseStack poseStack = event.getPoseStack();
        Camera camera = mc.gameRenderer.getMainCamera();
        Vec3 camPos = camera.getPosition();
        MultiBufferSource.BufferSource source = mc.renderBuffers().bufferSource();

        poseStack.pushPose();
        poseStack.translate(-camPos.x, -camPos.y, -camPos.z);

        BlockPos crystalPos = boundPos.pos();
        BlockEntity crystalEntity = level.getBlockEntity(crystalPos);

        renderCrystalHighlight(poseStack, source, crystalPos, crystalEntity instanceof TileFluxLinkCrystal);

        if (crystalEntity instanceof TileFluxLinkCrystal crystal) {
            List<TileFluxLinkCrystal.LinkTarget> targets = crystal.getLinkedTargets();
            for (TileFluxLinkCrystal.LinkTarget target : targets) {
                if (target.pos.dimension() == level.dimension()) {
                    renderLinkTargetHighlight(poseStack, source, target);
                }
            }
        }

        source.endBatch();
        poseStack.popPose();
    }

    private static void renderCrystalHighlight(PoseStack poseStack, MultiBufferSource source, BlockPos pos, boolean valid) {
        AABB aabb = new AABB(pos).inflate(0.02);
        float r = valid ? 0f : 1f;
        float g = valid ? 1f : 0f;
        float b = 0f;

        VertexConsumer consumer = source.getBuffer(RenderType.LINES);
        LevelRenderer.renderLineBox(poseStack, consumer, aabb, r, g, b, 1f);
    }

    private static void renderLinkTargetHighlight(PoseStack poseStack, MultiBufferSource source, TileFluxLinkCrystal.LinkTarget target) {
        BlockPos pos = target.pos.pos();
        Direction side = target.side;

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