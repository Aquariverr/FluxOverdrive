package com.aquariverr.fluxod.block.entity;

import com.aquariverr.fluxod.Config;
import com.aquariverr.fluxod.register.RegistryBlockEntityTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.items.ItemStackHandler;
import sonar.fluxnetworks.api.FluxDataComponents;
import sonar.fluxnetworks.common.block.FluxStorageBlock;
import sonar.fluxnetworks.common.device.FluxStorageHandler;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public abstract class TileFluxStorage extends sonar.fluxnetworks.common.device.TileFluxStorage {

    protected TileFluxStorage(@Nonnull BlockEntityType<?> type, BlockPos pos, BlockState state,
                               FluxStorageHandler handler) {
        super(type, pos, state, handler);
    }

    public static class Ender extends TileFluxStorage {

        public Ender(@Nonnull BlockPos pos, @Nonnull BlockState state) {
            super(RegistryBlockEntityTypes.ENDER_FLUX_STORAGE.get(), pos, state,
                    new ConfigStorageHandler(Config.enderTransfer, Config.enderCapacity));
        }

        @Nonnull
        @Override
        public ItemStack getDisplayStack() {
            return writeToDisplayStack(new ItemStack(getBlockState().getBlock()));
        }
    }

    public static class Nether extends TileFluxStorage {

        public Nether(@Nonnull BlockPos pos, @Nonnull BlockState state) {
            super(RegistryBlockEntityTypes.NETHER_FLUX_STORAGE.get(), pos, state,
                    new ConfigStorageHandler(Config.netherTransfer, Config.netherCapacity));
        }

        @Nonnull
        @Override
        public ItemStack getDisplayStack() {
            return writeToDisplayStack(new ItemStack(getBlockState().getBlock()));
        }
    }

    private static class ConfigStorageHandler extends FluxStorageHandler {
        private final long capacity;

        ConfigStorageHandler(long transfer, long capacity) {
            super(transfer);
            this.capacity = capacity;
        }

        @Override
        public long getMaxEnergyStorage() {
            return capacity;
        }
    }

    public record StorageConsumeResult(long capacity, long energy) {}

    @Nullable
    public static StorageConsumeResult tryConsumeStorageItem(ItemStackHandler inventory, int slot) {
        ItemStack stack = inventory.getStackInSlot(slot);
        if (stack.isEmpty()) return null;
        Block block = Block.byItem(stack.getItem());
        if (!(block instanceof FluxStorageBlock storageBlock)) return null;
        long cap = storageBlock.getEnergyCapacity();
        Long stored = stack.get(FluxDataComponents.STORED_ENERGY);
        long energy = stored != null ? stored : 0;
        int count = stack.getCount();
        inventory.setStackInSlot(slot, ItemStack.EMPTY);
        return new StorageConsumeResult(cap * count, energy * count);
    }
}
