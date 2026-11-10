package com.aquariverr.fluxod.block.entity;

import com.aquariverr.fluxod.Config;
import com.aquariverr.fluxod.register.RegistryBlockEntityTypes;
import com.aquariverr.fluxod.register.RegistryBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.energy.IEnergyStorage;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public final class TileQuantumEnergyConstraintInterface extends BlockEntity {

    private static final String ENERGY_TAG = "StabilizationEnergy";

    private long energy;
    private final EnergyStorage energyStorage = new EnergyStorage();

    public TileQuantumEnergyConstraintInterface(@Nonnull BlockPos pos, @Nonnull BlockState state) {
        super(RegistryBlockEntityTypes.QUANTUM_ENERGY_CONSTRAINT_INTERFACE.get(), pos, state);
    }

    public long getEnergy() {
        return energy;
    }

    public long getCapacity() {
        long capacity = getBlockState().is(RegistryBlocks.QUANTUM_ENERGY_CONSTRAINT_INTERFACE_3X3.get())
                ? Config.quantumStabilizer3x3Capacity
                : Config.quantumStabilizerCapacity;
        return Math.max(0, capacity);
    }

    public long extractStabilizationEnergy(long amount) {
        if (amount <= 0) return 0;
        long extracted = Math.min(amount, energy);
        if (extracted > 0) {
            energy -= extracted;
            setChanged();
        }
        return extracted;
    }

    public int receiveStabilizationEnergy(int amount, boolean simulate) {
        return energyStorage.receiveEnergy(amount, simulate);
    }

    @Nullable
    @SuppressWarnings("unchecked")
    public <T> T getEnergyCapability(Direction side) {
        if (isRemoved()) return null;
        return (T) energyStorage;
    }

    @Override
    protected void saveAdditional(@Nonnull CompoundTag tag, net.minecraft.core.HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putLong(ENERGY_TAG, energy);
    }

    @Override
    public void loadAdditional(@Nonnull CompoundTag tag, net.minecraft.core.HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        energy = Math.clamp(tag.getLong(ENERGY_TAG), 0, getCapacity());
    }

    private final class EnergyStorage implements IEnergyStorage {
        @Override
        public int receiveEnergy(int maxReceive, boolean simulate) {
            if (maxReceive <= 0) return 0;
            long received = Math.min((long) maxReceive, getCapacity() - energy);
            if (!simulate && received > 0) {
                energy += received;
                setChanged();
            }
            return (int) received;
        }

        @Override
        public int extractEnergy(int maxExtract, boolean simulate) {
            if (maxExtract <= 0) return 0;
            long extracted = Math.min((long) maxExtract, energy);
            if (!simulate && extracted > 0) {
                energy -= extracted;
                setChanged();
            }
            return (int) extracted;
        }

        @Override
        public int getEnergyStored() {
            return (int) Math.min(energy, Integer.MAX_VALUE);
        }

        @Override
        public int getMaxEnergyStored() {
            return (int) Math.min(getCapacity(), Integer.MAX_VALUE);
        }

        @Override
        public boolean canExtract() {
            return energy > 0;
        }

        @Override
        public boolean canReceive() {
            return energy < getCapacity();
        }
    }
}
