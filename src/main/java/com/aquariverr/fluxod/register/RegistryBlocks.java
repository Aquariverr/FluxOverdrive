package com.aquariverr.fluxod.register;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.RegisterEvent;
import com.aquariverr.fluxod.FluxOverdrive;
import com.aquariverr.fluxod.block.FluxFEStorageBlock;
import com.aquariverr.fluxod.block.FluxLinkCrystalBlock;
import com.aquariverr.fluxod.block.FluxStorageBlock;
import com.aquariverr.fluxod.block.FluxStorageTerminalBlock;

public class RegistryBlocks {
    public static final ResourceLocation ENDER_FLUX_BLOCK_KEY = FluxOverdrive.location("ender_flux_block");
    public static final ResourceLocation NETHER_FLUX_BLOCK_KEY = FluxOverdrive.location("nether_flux_block");
    public static final ResourceLocation ENDER_FLUX_STORAGE_KEY = FluxOverdrive.location("ender_flux_storage");
    public static final ResourceLocation NETHER_FLUX_STORAGE_KEY = FluxOverdrive.location("nether_flux_storage");
    public static final ResourceLocation FLUX_STORAGE_TERMINAL_KEY = FluxOverdrive.location("flux_storage_terminal");
    public static final ResourceLocation FLUX_FE_STORAGE_KEY = FluxOverdrive.location("flux_fe_storage");
    public static final ResourceLocation FLUX_LINK_CRYSTAL_KEY = FluxOverdrive.location("flux_link_crystal");

    public static final DeferredBlock<Block> ENDER_FLUX_BLOCK = holder(ENDER_FLUX_BLOCK_KEY);
    public static final DeferredBlock<Block> NETHER_FLUX_BLOCK = holder(NETHER_FLUX_BLOCK_KEY);
    public static final DeferredBlock<FluxStorageBlock.Ender> ENDER_FLUX_STORAGE = holder(ENDER_FLUX_STORAGE_KEY);
    public static final DeferredBlock<FluxStorageBlock.Nether> NETHER_FLUX_STORAGE = holder(NETHER_FLUX_STORAGE_KEY);
    public static final DeferredBlock<FluxStorageTerminalBlock> FLUX_STORAGE_TERMINAL = holder(FLUX_STORAGE_TERMINAL_KEY);
    public static final DeferredBlock<FluxFEStorageBlock> FLUX_FE_STORAGE = holder(FLUX_FE_STORAGE_KEY);
    public static final DeferredBlock<FluxLinkCrystalBlock> FLUX_LINK_CRYSTAL = holder(FLUX_LINK_CRYSTAL_KEY);

    static <T extends Block> DeferredBlock<T> holder(ResourceLocation location) {
        return DeferredBlock.createBlock(location);
    }

    static void register(RegisterEvent.RegisterHelper<Block> helper) {
        BlockBehaviour.Properties normalProps = BlockBehaviour.Properties.of()
                .mapColor(MapColor.METAL)
                .sound(SoundType.METAL)
                .strength(1.0F, 1000.0F);

        BlockBehaviour.Properties deviceProps = BlockBehaviour.Properties.of()
                .mapColor(MapColor.METAL)
                .sound(SoundType.METAL)
                .strength(1.0F, 1000.0F)
                .noOcclusion();

        helper.register(ENDER_FLUX_BLOCK_KEY, new Block(normalProps));
        helper.register(NETHER_FLUX_BLOCK_KEY, new Block(normalProps));
        helper.register(ENDER_FLUX_STORAGE_KEY, new FluxStorageBlock.Ender(deviceProps));
        helper.register(NETHER_FLUX_STORAGE_KEY, new FluxStorageBlock.Nether(deviceProps));
        helper.register(FLUX_STORAGE_TERMINAL_KEY, new FluxStorageTerminalBlock(deviceProps));
        helper.register(FLUX_FE_STORAGE_KEY, new FluxFEStorageBlock(deviceProps));
        helper.register(FLUX_LINK_CRYSTAL_KEY, new FluxLinkCrystalBlock(deviceProps));
    }

    private RegistryBlocks() {}
}
