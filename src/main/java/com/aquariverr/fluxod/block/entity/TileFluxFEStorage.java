package com.aquariverr.fluxod.block.entity;

import com.aquariverr.fluxod.Config;
import com.aquariverr.fluxod.register.FluxOdDataComponents;
import com.aquariverr.fluxod.register.RegistryBlockEntityTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.capabilities.BlockCapability;
import net.neoforged.neoforge.energy.IEnergyStorage;
import net.neoforged.neoforge.items.ItemStackHandler;
import org.jetbrains.annotations.NotNull;
import sonar.fluxnetworks.api.FluxConstants;
import sonar.fluxnetworks.api.FluxDataComponents;
import sonar.fluxnetworks.api.device.FluxDeviceType;
import sonar.fluxnetworks.api.device.IFluxPoint;
import sonar.fluxnetworks.api.energy.IFNEnergyStorage;
import sonar.fluxnetworks.common.block.FluxStorageBlock;
import sonar.fluxnetworks.common.device.TileFluxConnector;
import sonar.fluxnetworks.common.item.FluxStorageItem;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public class TileFluxFEStorage extends TileFluxConnector implements IFluxPoint {

    private static final int FE_STORAGE_SLOTS = 4;

    private final FEStorageHandler mHandler = new FEStorageHandler(Config.feStorageTransfer, Config.feStorageCapacity);

    @Nullable
    private EnergyStorage mEnergyCap;

    @Nullable
    private CompoundTag pendingInv;

    private final ItemStackHandler inventory = new ItemStackHandler(FE_STORAGE_SLOTS) {
        @Override
        protected void onContentsChanged(int slot) {
            consumeItem(slot);
        }

        @Override
        public boolean isItemValid(int slot, @Nonnull ItemStack stack) {
            return stack.getItem() instanceof FluxStorageItem;
        }
    };

    public TileFluxFEStorage(@Nonnull BlockPos pos, @Nonnull BlockState state) {
        super(RegistryBlockEntityTypes.FLUX_FE_STORAGE.get(), pos, state);
    }

    @Override
    public void onLoad() {
        super.onLoad();
        if (pendingInv != null && level != null) {
            inventory.deserializeNBT(level.registryAccess(), pendingInv);
            pendingInv = null;
        }
    }

    @Nonnull
    @Override
    public FluxDeviceType getDeviceType() {
        return FluxDeviceType.POINT;
    }

    @Nonnull
    @Override
    public FEStorageHandler getTransferHandler() {
        return mHandler;
    }

    @Nonnull
    @Override
    public ItemStack getDisplayStack() {
        return new ItemStack(getBlockState().getBlock());
    }

    public ItemStackHandler getInventory() {
        return inventory;
    }

    public long getFECapacity() {
        return mHandler.getFECapacity();
    }

    public long getFEBuffer() {
        return mHandler.getFEBuffer();
    }

    @Override
    @SuppressWarnings("NonExtendableApiUsage")
    public void invalidateCapabilities() {
        mEnergyCap = null;
        super.invalidateCapabilities();
    }

    @Nullable
    @SuppressWarnings("unchecked")
    public <T> T getEnergyCapability(BlockCapability<T, Direction> cap, @Nullable Direction side) {
        if (!isRemoved()) {
            if (mEnergyCap == null) {
                mEnergyCap = new EnergyStorage();
            }
            return (T) mEnergyCap;
        }
        return null;
    }

    @Override
    protected void collectImplicitComponents(DataComponentMap.Builder builder) {
        super.collectImplicitComponents(builder);
        builder.set(FluxDataComponents.STORED_ENERGY, mHandler.getFEBuffer());
        builder.set(FluxOdDataComponents.TOTAL_CAPACITY, mHandler.getFECapacity());
    }

    @Override
    protected void applyImplicitComponents(BlockEntity.@NotNull DataComponentInput input) {
        super.applyImplicitComponents(input);
        mHandler.clearNetworkBuffer();
        Long energy = input.get(FluxDataComponents.STORED_ENERGY);
        if (energy != null && energy > 0) {
            mHandler.setFEBuffer(energy);
        }
        Long capacity = input.get(FluxOdDataComponents.TOTAL_CAPACITY);
        if (capacity != null && capacity > 0) {
            mHandler.setFECapacity(capacity);
        }
    }

    @Override
    public void writeCustomTag(@Nonnull CompoundTag tag, byte type) {
        super.writeCustomTag(tag, type);
        tag.putLong("FEBuffer", mHandler.getFEBuffer());
        tag.putLong("FECapacity", mHandler.getFECapacity());
        if (level != null && type != FluxConstants.NBT_TILE_SETTINGS && type != FluxConstants.NBT_TILE_UPDATE) {
            tag.put("Inventory", inventory.serializeNBT(level.registryAccess()));
        }
    }

    @Override
    public void readCustomTag(@Nonnull CompoundTag tag, byte type) {
        super.readCustomTag(tag, type);
        if (tag.contains("FEBuffer")) {
            mHandler.setFEBuffer(tag.getLong("FEBuffer"));
        }
        if (tag.contains("FECapacity")) {
            mHandler.setFECapacity(tag.getLong("FECapacity"));
        }
        if (type != FluxConstants.NBT_TILE_SETTINGS && type != FluxConstants.NBT_TILE_UPDATE && tag.contains("Inventory")) {
            if (level != null) {
                inventory.deserializeNBT(level.registryAccess(), tag.getCompound("Inventory"));
            } else {
                pendingInv = tag.getCompound("Inventory");
            }
        }
        mHandler.clearNetworkBuffer();
        if (type == FluxConstants.NBT_SAVE_ALL && level != null && !level.isClientSide) {
            setChanged();
        }
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

        long newCapacity = mHandler.getFECapacity() + capacity * count;
        mHandler.setFECapacity(newCapacity);
        if (energy > 0) {
            mHandler.setFEBuffer(mHandler.getFEBuffer() + energy * count);
        }
        inventory.setStackInSlot(slot, ItemStack.EMPTY);

        if (level != null && !level.isClientSide) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_ALL);
            setChanged();
        }
    }

    private class EnergyStorage implements IEnergyStorage, IFNEnergyStorage {

        @Override
        public int receiveEnergy(int maxReceive, boolean simulate) {
            return 0;
        }

        @Override
        public int extractEnergy(int maxExtract, boolean simulate) {
            return 0;
        }

        @Override
        public int getEnergyStored() {
            return (int) Math.min(getEnergyStoredL(), Integer.MAX_VALUE);
        }

        @Override
        public int getMaxEnergyStored() {
            return (int) Math.min(getMaxEnergyStoredL(), Integer.MAX_VALUE);
        }

        @Override
        public boolean canExtract() {
            return false;
        }

        @Override
        public boolean canReceive() {
            return false;
        }

        @Override
        public long receiveEnergyL(long maxReceive, boolean simulate) {
            return 0;
        }

        @Override
        public long extractEnergyL(long maxExtract, boolean simulate) {
            return 0;
        }

        @Override
        public long getEnergyStoredL() {
            return mHandler.getFEBuffer();
        }

        @Override
        public long getMaxEnergyStoredL() {
            return mHandler.getFECapacity();
        }
    }
}
