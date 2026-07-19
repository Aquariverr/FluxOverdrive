package com.aquariverr.fluxod.client.render;

import com.aquariverr.fluxod.block.entity.TileFluxFEStorage;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import org.joml.Matrix4f;

import javax.annotation.ParametersAreNonnullByDefault;

@ParametersAreNonnullByDefault
public class FluxFEStorageEntityRenderer implements BlockEntityRenderer<TileFluxFEStorage> {

    private static final ResourceLocation ENERGY_TEXTURE =
            ResourceLocation.fromNamespaceAndPath("fluxnetworks", "textures/block/flux_storage_energy.png");
    private static final RenderType RENDER_TYPE = RenderType.entityTranslucent(ENERGY_TEXTURE);

    public static final BlockEntityRendererProvider<TileFluxFEStorage> PROVIDER =
            ctx -> new FluxFEStorageEntityRenderer();

    private static final float X_MIN = 0.09375F;
    private static final float X_MAX = 0.90625F;
    private static final float Y_BASE = 0.0625F;
    private static final float Y_MAX = 0.9375F;
    private static final float Z_MIN = 0.09375F;
    private static final float Z_MAX = 0.90625F;

    @Override
    public void render(TileFluxFEStorage tile, float partialTick, PoseStack poseStack,
                       MultiBufferSource bufferSource, int light, int overlay) {
        long buffer = tile.getFEBuffer();
        long capacity = tile.getFECapacity();
        if (buffer <= 0 || capacity <= 0) return;

        float ratio = Math.min((float) buffer / capacity, 1.0F);
        float yTop = Y_BASE + ratio * (Y_MAX - Y_BASE);

        int color = tile.mClientColor;
        int r = (color >> 16) & 0xFF;
        int g = (color >> 8) & 0xFF;
        int b = color & 0xFF;

        VertexConsumer builder = bufferSource.getBuffer(RENDER_TYPE);
        Matrix4f matrix = poseStack.last().pose();
        PoseStack.Pose normal = poseStack.last();

        renderFace(matrix, normal, builder,
                X_MIN, Y_BASE, Z_MIN, X_MAX, Y_BASE, Z_MIN,
                X_MAX, Y_BASE, Z_MAX, X_MIN, Y_BASE, Z_MAX,
                r, g, b, overlay);

        if (ratio < 1.0F) {
            renderFace(matrix, normal, builder,
                    X_MIN, yTop, Z_MAX, X_MAX, yTop, Z_MAX,
                    X_MAX, yTop, Z_MIN, X_MIN, yTop, Z_MIN,
                    r, g, b, overlay);
        }

        renderFace(matrix, normal, builder,
                X_MAX, Y_BASE, Z_MIN, X_MIN, Y_BASE, Z_MIN,
                X_MIN, yTop, Z_MIN, X_MAX, yTop, Z_MIN,
                r, g, b, overlay);

        renderFace(matrix, normal, builder,
                X_MIN, Y_BASE, Z_MAX, X_MAX, Y_BASE, Z_MAX,
                X_MAX, yTop, Z_MAX, X_MIN, yTop, Z_MAX,
                r, g, b, overlay);

        renderFace(matrix, normal, builder,
                X_MIN, Y_BASE, Z_MAX, X_MIN, Y_BASE, Z_MIN,
                X_MIN, yTop, Z_MIN, X_MIN, yTop, Z_MAX,
                r, g, b, overlay);

        renderFace(matrix, normal, builder,
                X_MAX, Y_BASE, Z_MIN, X_MAX, Y_BASE, Z_MAX,
                X_MAX, yTop, Z_MAX, X_MAX, yTop, Z_MIN,
                r, g, b, overlay);
    }

    private static void renderFace(Matrix4f matrix, PoseStack.Pose normal, VertexConsumer builder,
                                   float x1, float y1, float z1, float x2, float y2, float z2,
                                   float x3, float y3, float z3, float x4, float y4, float z4,
                                   int r, int g, int b, int overlay) {
        builder.addVertex(matrix, x1, y1, z1)
                .setColor(r, g, b, 150)
                .setUv(0, 0)
                .setOverlay(overlay)
                .setLight(15728880)
                .setNormal(normal, 0, 1, 0);
        builder.addVertex(matrix, x2, y2, z2)
                .setColor(r, g, b, 150)
                .setUv(0, 1)
                .setOverlay(overlay)
                .setLight(15728880)
                .setNormal(normal, 0, 1, 0);
        builder.addVertex(matrix, x3, y3, z3)
                .setColor(r, g, b, 150)
                .setUv(1, 1)
                .setOverlay(overlay)
                .setLight(15728880)
                .setNormal(normal, 0, 1, 0);
        builder.addVertex(matrix, x4, y4, z4)
                .setColor(r, g, b, 150)
                .setUv(1, 0)
                .setOverlay(overlay)
                .setLight(15728880)
                .setNormal(normal, 0, 1, 0);
    }
}
