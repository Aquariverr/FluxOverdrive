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
import sonar.fluxnetworks.api.FluxDataComponents;
import sonar.fluxnetworks.common.block.FluxStorageBlock;
import sonar.fluxnetworks.common.device.FluxStorageHandler;
import sonar.fluxnetworks.common.item.FluxStorageItem;

import javax.annotation.Nonnull;

public class TileFluxStorageTerminal extends TileFluxStorage {

    private static final int SLOTS = 4;
    private static final long BASE_TRANSFER = 1_000_000L;

    private final TerminalHandler handler = new TerminalHandler();
    private long totalCapacity;

    private final ItemStackHandler inventory = new ItemStackHandler(SLOTS) {
        @Override
        protected void onContentsChanged(int slot) {
            consumeItem(slot);
        }

        @Override
        public boolean isItemValid(int slot, @Nonnull ItemStack stack) {
            return stack.getItem() instanceof FluxStorageItem;
        }
    };

    public TileFluxStorageTerminal(@Nonnull BlockPos pos, @Nonnull BlockState state) {
        super(RegistryBlockEntityTypes.FLUX_STORAGE_TERMINAL.get(), pos, state, null);
        handler.setTerminal(this);
    }

    @Override
    public void onLoad() {
        super.onLoad();
    }

    @Override
    protected void collectImplicitComponents(DataComponentMap.@NotNull Builder builder) {
        super.collectImplicitComponents(builder);
        builder.set(FluxOdDataComponents.TOTAL_CAPACITY, totalCapacity);
    }

    @Override
    protected void applyImplicitComponents(BlockEntity.@NotNull DataComponentInput input) {
        super.applyImplicitComponents(input);
        totalCapacity = input.getOrDefault(FluxOdDataComponents.TOTAL_CAPACITY, 0L);
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

    public long getTotalCapacity() {
        return totalCapacity;
    }

    public void setTotalCapacity(long capacity) {
        totalCapacity = capacity;
    }

    private void consumeItem(int slot) {
        ItemStack stack = inventory.getStackInSlot(slot);
        if (stack.isEmpty()) return;
        Block block = Block.byItem(stack.getItem());
        if (!(block instanceof FluxStorageBlock storageBlock)) return;
        long capacity = storageBlock.getEnergyCapacity();
        Long stored = stack.get(FluxDataComponents.STORED_ENERGY);
        long energy = stored != null ? stored : 0;
        int count = stack.getCount();

        totalCapacity += capacity * count;
        handler.addEnergy(energy * count);
        inventory.setStackInSlot(slot, ItemStack.EMPTY);

        if (level != null && !level.isClientSide) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_ALL);
            setChanged();
        }
    }

    @Override
    public void writeCustomTag(@Nonnull CompoundTag tag, byte type) {
        super.writeCustomTag(tag, type);
        tag.putLong("TotalCapacity", totalCapacity);
    }

    @Override
    public void readCustomTag(@Nonnull CompoundTag tag, byte type) {
        if (tag.contains("TotalCapacity")) {
            totalCapacity = tag.getLong("TotalCapacity");
        }
        super.readCustomTag(tag, type);
        if (type == FluxConstants.NBT_SAVE_ALL) {
            setChanged();
        }
    }

    private class TerminalHandler extends FluxStorageHandler {

        private TileFluxStorageTerminal terminal;

        TerminalHandler() {
            super(BASE_TRANSFER);
        }

        void setTerminal(TileFluxStorageTerminal t) {
            this.terminal = t;
        }

        void addEnergy(long amount) {
            addToBuffer(amount);
            mFlags |= FLAG_ENERGY_CHANGED;
        }

        @Override
        public long getMaxEnergyStorage() {
            return terminal != null ? terminal.totalCapacity : 0;
        }
    }
}
