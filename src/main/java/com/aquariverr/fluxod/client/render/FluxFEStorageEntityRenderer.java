package com.aquariverr.fluxod.client.render;

import com.aquariverr.fluxod.block.entity.TileFluxFEStorage;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;

import javax.annotation.ParametersAreNonnullByDefault;

@ParametersAreNonnullByDefault
public class FluxFEStorageEntityRenderer implements BlockEntityRenderer<TileFluxFEStorage> {

    public static final BlockEntityRendererProvider<TileFluxFEStorage> PROVIDER =
            ctx -> new FluxFEStorageEntityRenderer();

    @Override
    public void render(TileFluxFEStorage tile, float partialTick, PoseStack poseStack,
                       MultiBufferSource bufferSource, int light, int overlay) {
        long buffer = tile.getFEBuffer();
        long capacity = tile.getFECapacity();
        if (buffer <= 0 || capacity <= 0) return;

        float height = StorageRenderMath.calculateHeight(buffer, capacity);

        FluxStorageRendering.render(poseStack, bufferSource, height, tile.mClientColor, overlay);
    }
}
