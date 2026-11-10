package com.aquariverr.fluxod.client.render;

import com.aquariverr.fluxod.block.entity.TileFluxStorage;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;

import javax.annotation.ParametersAreNonnullByDefault;

@ParametersAreNonnullByDefault
public class FluxOverdriveStorageEntityRenderer implements BlockEntityRenderer<TileFluxStorage> {

    public static final BlockEntityRendererProvider<TileFluxStorage> PROVIDER =
            context -> new FluxOverdriveStorageEntityRenderer();

    @Override
    public void render(TileFluxStorage tile, float partialTick, PoseStack poseStack,
                       MultiBufferSource bufferSource, int light, int overlay) {
        float height = StorageRenderMath.calculateHeight(tile.getTransferBuffer(), tile.getMaxTransferLimit());
        if (height <= 0) return;

        FluxStorageRendering.render(poseStack, bufferSource, height, tile.mClientColor, overlay);
    }
}
