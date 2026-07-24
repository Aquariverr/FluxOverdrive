package com.aquariverr.fluxod.block.entity;

import com.aquariverr.fluxod.Config;
import com.aquariverr.fluxod.register.RegistryBlockEntityTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
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
        private static final String DATA_VERSION_TAG = "FluxOverdriveStorageVersion";
        private static final int DATA_VERSION = 1;

        private final long capacity;
        private final long defaultTransfer;

        ConfigStorageHandler(long transfer, long capacity) {
            // FluxStorageHandler's constructor calls the overridable setLimit method.
            // Initialize capacity before applying the real transfer limit.
            super(0);
            this.capacity = Math.max(0, capacity);
            this.defaultTransfer = Math.max(0, transfer);
            setLimit(transfer);
        }

        @Override
        public long getMaxEnergyStorage() {
            return capacity;
        }

        @Override
        public void writeCustomTag(@Nonnull CompoundTag tag, byte type) {
            super.writeCustomTag(tag, type);
            tag.putInt(DATA_VERSION_TAG, DATA_VERSION);
        }

        @Override
        public void readCustomTag(@Nonnull CompoundTag tag, byte type) {
            boolean legacyZeroLimit = !tag.contains(DATA_VERSION_TAG) && tag.getLong("limit") == 0;
            super.readCustomTag(tag, type);
            mBuffer = Math.max(0, Math.min(mBuffer, capacity));
            setLimit(legacyZeroLimit ? defaultTransfer : getRawLimit());
        }
    }

    public record StorageConsumeResult(long capacity, long energy) {}

    public static boolean isConsumableStorageItem(ItemStack stack) {
        if (stack.isEmpty()) return false;
        Block block = Block.byItem(stack.getItem());
        return block instanceof FluxStorageBlock storageBlock && storageBlock.getEnergyCapacity() > 0;
    }

    @Nullable
    public static StorageConsumeResult inspectStorageItem(ItemStackHandler inventory, int slot) {
        ItemStack stack = inventory.getStackInSlot(slot);
        if (!isConsumableStorageItem(stack)) return null;
        Block block = Block.byItem(stack.getItem());
        FluxStorageBlock storageBlock = (FluxStorageBlock) block;
        long cap = storageBlock.getEnergyCapacity();
        Long stored = stack.get(FluxDataComponents.STORED_ENERGY);
        long energy = stored != null ? Math.max(0, Math.min(stored, cap)) : 0;
        int count = stack.getCount();
        try {
            return new StorageConsumeResult(Math.multiplyExact(cap, count), Math.multiplyExact(energy, count));
        } catch (ArithmeticException ignored) {
            return null;
        }
    }

    public static void consumeStorageItem(ItemStackHandler inventory, int slot) {
        inventory.setStackInSlot(slot, ItemStack.EMPTY);
    }
}
