package com.aquariverr.fluxod.register;

import com.mojang.datafixers.DSL;
import net.minecraft.core.registries.BuiltInRegistries;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.RegisterEvent;
import com.aquariverr.fluxod.block.entity.TileFluxLinkCrystal;
import com.aquariverr.fluxod.block.entity.TileFluxFEStorage;
import com.aquariverr.fluxod.block.entity.TileFluxStorage;
import com.aquariverr.fluxod.block.entity.TileFluxStorageTerminal;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import sonar.fluxnetworks.api.FluxCapabilities;

import java.util.Set;

public class RegistryBlockEntityTypes {
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<TileFluxStorage.Ender>> ENDER_FLUX_STORAGE =
            holder(RegistryBlocks.ENDER_FLUX_STORAGE_KEY);
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<TileFluxStorage.Nether>> NETHER_FLUX_STORAGE =
            holder(RegistryBlocks.NETHER_FLUX_STORAGE_KEY);
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<TileFluxStorageTerminal>> FLUX_STORAGE_TERMINAL =
            holder(RegistryBlocks.FLUX_STORAGE_TERMINAL_KEY);
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<TileFluxFEStorage>> FLUX_FE_STORAGE =
            holder(RegistryBlocks.FLUX_FE_STORAGE_KEY);
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<TileFluxLinkCrystal>> FLUX_LINK_CRYSTAL =
            holder(RegistryBlocks.FLUX_LINK_CRYSTAL_KEY);

    static <T extends BlockEntity> DeferredHolder<BlockEntityType<?>, BlockEntityType<T>> holder(ResourceLocation location) {
        return DeferredHolder.create(BuiltInRegistries.BLOCK_ENTITY_TYPE.key(), location);
    }

    static void register(RegisterEvent.RegisterHelper<BlockEntityType<?>> helper) {
        helper.register(RegistryBlocks.ENDER_FLUX_STORAGE_KEY, new BlockEntityType<>(
                TileFluxStorage.Ender::new,
                Set.of(RegistryBlocks.ENDER_FLUX_STORAGE.get()),
                DSL.remainderType()
        ));
        helper.register(RegistryBlocks.NETHER_FLUX_STORAGE_KEY, new BlockEntityType<>(
                TileFluxStorage.Nether::new,
                Set.of(RegistryBlocks.NETHER_FLUX_STORAGE.get()),
                DSL.remainderType()
        ));
        helper.register(RegistryBlocks.FLUX_STORAGE_TERMINAL_KEY, new BlockEntityType<>(
                TileFluxStorageTerminal::new,
                Set.of(RegistryBlocks.FLUX_STORAGE_TERMINAL.get()),
                DSL.remainderType()
        ));
        helper.register(RegistryBlocks.FLUX_FE_STORAGE_KEY, new BlockEntityType<>(
                TileFluxFEStorage::new,
                Set.of(RegistryBlocks.FLUX_FE_STORAGE.get()),
                DSL.remainderType()
        ));
        helper.register(RegistryBlocks.FLUX_LINK_CRYSTAL_KEY, new BlockEntityType<>(
                TileFluxLinkCrystal::new,
                Set.of(RegistryBlocks.FLUX_LINK_CRYSTAL.get()),
                DSL.remainderType()
        ));
    }

    static void registerBlockCapabilities(RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(Capabilities.ItemHandler.BLOCK, FLUX_STORAGE_TERMINAL.get(),
                (be, side) -> be.getInventory());
        event.registerBlockEntity(Capabilities.ItemHandler.BLOCK, FLUX_FE_STORAGE.get(),
                (be, side) -> be.getInventory());
        event.registerBlockEntity(Capabilities.EnergyStorage.BLOCK, FLUX_FE_STORAGE.get(),
                (be, side) -> be.getEnergyCapability(Capabilities.EnergyStorage.BLOCK, side));
        event.registerBlockEntity(FluxCapabilities.BLOCK, FLUX_FE_STORAGE.get(),
                (be, side) -> be.getEnergyCapability(FluxCapabilities.BLOCK, side));
    }

    private RegistryBlockEntityTypes() {}
}
