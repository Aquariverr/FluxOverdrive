package com.aquariverr.fluxod.client.render;

import com.aquariverr.fluxod.block.entity.TileFluxStorage;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.Direction;
import org.joml.Matrix4f;
import sonar.fluxnetworks.client.render.FluxStorageRenderType;

import javax.annotation.ParametersAreNonnullByDefault;

@ParametersAreNonnullByDefault
public class FluxOverdriveStorageEntityRenderer implements BlockEntityRenderer<TileFluxStorage> {

    public static final BlockEntityRendererProvider<TileFluxStorage> PROVIDER =
            context -> new FluxOverdriveStorageEntityRenderer();

    private static final float START = 0.125F;
    private static final float END = 0.875F;
    private static final float OFFSET = 0.0625F;
    private static final float WIDTH = 0.75F;
    private static final int ALPHA = 150;
    private static final int FULL_BRIGHT = 15728880;

    @Override
    public void render(TileFluxStorage tile, float partialTick, PoseStack poseStack,
                       MultiBufferSource bufferSource, int light, int overlay) {
        float height = StorageRenderMath.calculateHeight(tile.getTransferBuffer(), tile.getMaxTransferLimit());
        if (height <= 0) return;

        int color = tile.mClientColor;
        int r = (color >> 16) & 0xFF;
        int g = (color >> 8) & 0xFF;
        int b = color & 0xFF;

        VertexConsumer builder = bufferSource.getBuffer(FluxStorageRenderType.getType());
        renderSide(poseStack, builder, Direction.NORTH, START, OFFSET, END, WIDTH, height,
                r, g, b, overlay, -1);
        renderSide(poseStack, builder, Direction.SOUTH, START, OFFSET, END, WIDTH, height,
                r, g, b, overlay, -1);
        renderSide(poseStack, builder, Direction.EAST, START, OFFSET, END, WIDTH, height,
                r, g, b, overlay, -1);
        renderSide(poseStack, builder, Direction.WEST, START, OFFSET, END, WIDTH, height,
                r, g, b, overlay, -1);
        if (StorageRenderMath.shouldRenderTop(height)) {
            renderSide(poseStack, builder, Direction.UP, OFFSET, START + height, OFFSET, END, -END,
                    r, g, b, overlay, 1);
        }
    }

    private static void renderSide(PoseStack poseStack, VertexConsumer builder, Direction direction,
                                   float start, float offset, float end, float width, float height,
                                   int r, int g, int b, int overlay, int normalY) {
        poseStack.pushPose();
        poseStack.translate(0.5, 0.5, 0.5);
        poseStack.mulPose(direction.getRotation());
        poseStack.translate(-0.5, -0.5, -0.5);

        Matrix4f matrix = poseStack.last().pose();
        PoseStack.Pose normal = poseStack.last();
        builder.addVertex(matrix, start, offset, end - height)
                .setColor(r, g, b, ALPHA)
                .setUv(start, end - height)
                .setOverlay(overlay)
                .setLight(FULL_BRIGHT)
                .setNormal(normal, 0, normalY, 0);
        builder.addVertex(matrix, start + width, offset, end - height)
                .setColor(r, g, b, ALPHA)
                .setUv(start + width, end - height)
                .setOverlay(overlay)
                .setLight(FULL_BRIGHT)
                .setNormal(normal, 0, normalY, 0);
        builder.addVertex(matrix, start + width, offset, end)
                .setColor(r, g, b, ALPHA)
                .setUv(start + width, end)
                .setOverlay(overlay)
                .setLight(FULL_BRIGHT)
                .setNormal(normal, 0, normalY, 0);
        builder.addVertex(matrix, start, offset, end)
                .setColor(r, g, b, ALPHA)
                .setUv(start, end)
                .setOverlay(overlay)
                .setLight(FULL_BRIGHT)
                .setNormal(normal, 0, normalY, 0);
        poseStack.popPose();
    }
}
