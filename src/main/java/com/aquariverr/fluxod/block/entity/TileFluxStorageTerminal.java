package com.aquariverr.fluxod.block.entity;

import com.aquariverr.fluxod.register.FluxOdDataComponents;
import com.aquariverr.fluxod.register.RegistryBlockEntityTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.items.ItemStackHandler;
import org.jetbrains.annotations.NotNull;
import sonar.fluxnetworks.api.FluxConstants;
import sonar.fluxnetworks.common.device.FluxStorageHandler;

import javax.annotation.Nonnull;

public class TileFluxStorageTerminal extends TileFluxStorage {

    private static final int SLOTS = 4;
    private static final long BASE_TRANSFER = 1_000_000L;
    private static final String DATA_VERSION_TAG = "FluxOverdriveTerminalVersion";
    private static final int DATA_VERSION = 1;

    private final TerminalHandler handler;
    private long totalCapacity;

    private final ItemStackHandler inventory = new ItemStackHandler(SLOTS) {
        @Override
        protected void onContentsChanged(int slot) {
            consumeItem(slot);
        }

        @Override
        public boolean isItemValid(int slot, @Nonnull ItemStack stack) {
            return TileFluxStorage.isConsumableStorageItem(stack);
        }
    };

    public TileFluxStorageTerminal(@Nonnull BlockPos pos, @Nonnull BlockState state) {
        this(pos, state, new TerminalHandler());
    }

    private TileFluxStorageTerminal(BlockPos pos, BlockState state, TerminalHandler handler) {
        super(RegistryBlockEntityTypes.FLUX_STORAGE_TERMINAL.get(), pos, state, handler);
        this.handler = handler;
        handler.attach(this);
    }

    @Override
    protected void collectImplicitComponents(DataComponentMap.@NotNull Builder builder) {
        super.collectImplicitComponents(builder);
        builder.set(FluxOdDataComponents.TOTAL_CAPACITY, totalCapacity);
    }

    @Override
    protected void applyImplicitComponents(BlockEntity.@NotNull DataComponentInput input) {
        totalCapacity = Math.max(0, input.getOrDefault(FluxOdDataComponents.TOTAL_CAPACITY, 0L));
        super.applyImplicitComponents(input);
        handler.sanitize();
    }

    @Nonnull
    @Override
    public FluxStorageHandler getTransferHandler() {
        return handler;
    }

    @Override
    public long getMaxTransferLimit() {
        return handler.getMaxEnergyStorage();
    }

    @Nonnull
    @Override
    public ItemStack getDisplayStack() {
        return writeToDisplayStack(new ItemStack(getBlockState().getBlock()));
    }

    public ItemStackHandler getInventory() {
        return inventory;
    }

    @SuppressWarnings("unused")
    public long getTotalCapacity() {
        return totalCapacity;
    }

    @SuppressWarnings("unused")
    public void setTotalCapacity(long capacity) {
        totalCapacity = Math.max(0, capacity);
        handler.sanitize();
    }

    private void consumeItem(int slot) {
        StorageConsumeResult result = TileFluxStorage.inspectStorageItem(inventory, slot);
        if (result == null) return;

        long newCapacity;
        try {
            newCapacity = Math.addExact(totalCapacity, result.capacity());
        } catch (ArithmeticException ignored) {
            return;
        }

        TileFluxStorage.consumeStorageItem(inventory, slot);
        long oldCapacity = totalCapacity;
        totalCapacity = newCapacity;
        handler.onCapacityChanged(oldCapacity);
        handler.addEnergy(result.energy());

        if (level != null && !level.isClientSide) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
            setChanged();
        }
    }

    @Override
    public void writeCustomTag(@Nonnull CompoundTag tag, byte type) {
        super.writeCustomTag(tag, type);
        tag.putLong("TotalCapacity", totalCapacity);
        tag.putInt(DATA_VERSION_TAG, DATA_VERSION);
    }

    @Override
    public void readCustomTag(@Nonnull CompoundTag tag, byte type) {
        boolean persistedData = type == FluxConstants.NBT_SAVE_ALL || type == FluxConstants.NBT_TILE_DROP;
        boolean legacyZeroLimit = persistedData && !tag.contains(DATA_VERSION_TAG) && tag.getLong("limit") == 0;
        if (tag.contains("TotalCapacity")) {
            totalCapacity = Math.max(0, tag.getLong("TotalCapacity"));
        }
        super.readCustomTag(tag, type);
        if (legacyZeroLimit && totalCapacity > 0) handler.setLimit(BASE_TRANSFER);
        handler.sanitize();
    }

    private static class TerminalHandler extends FluxStorageHandler {

        private TileFluxStorageTerminal terminal;

        TerminalHandler() {
            super(0);
        }

        void attach(TileFluxStorageTerminal t) {
            this.terminal = t;
        }

        void onCapacityChanged(long oldCapacity) {
            if (oldCapacity == 0 && getMaxEnergyStorage() > 0 && getRawLimit() == 0) {
                setLimit(BASE_TRANSFER);
            }
            sanitize();
        }

        void addEnergy(long amount) {
            long space = Math.max(0, getMaxEnergyStorage() - mBuffer);
            long added = Math.min(Math.max(0, amount), space);
            if (added > 0) {
                addToBuffer(added);
            }
        }

        void sanitize() {
            long capacity = getMaxEnergyStorage();
            mBuffer = Math.max(0, Math.min(mBuffer, capacity));
            setLimit(getRawLimit());
        }

        @Override
        public long getMaxEnergyStorage() {
            return terminal != null ? terminal.totalCapacity : 0;
        }
    }
}
