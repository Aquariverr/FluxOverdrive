package com.aquariverr.fluxod.block.entity;

import com.aquariverr.fluxod.register.RegistryBlockEntityTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.energy.IEnergyStorage;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public final class TileQuantumEnergyConstraintEntanglementPort extends BlockEntity {

    private final EnergyStorage energyStorage = new EnergyStorage();

    public TileQuantumEnergyConstraintEntanglementPort(@Nonnull BlockPos pos, @Nonnull BlockState state) {
        super(RegistryBlockEntityTypes.QUANTUM_ENERGY_CONSTRAINT_ENTANGLEMENT_PORT.get(), pos, state);
    }

    @Nullable
    @SuppressWarnings("unchecked")
    public <T> T getEnergyCapability(@Nullable Direction side) {
        if (isRemoved()) return null;
        return (T) energyStorage;
    }

    @Nullable
    private TileQuantumEntanglementController getController() {
        if (level == null) return null;
        for (BlockPos controllerPos : TileQuantumEntanglementController.getLoadedControllersNear(level, worldPosition, 4)) {
            if (!level.isLoaded(controllerPos)) continue;
            if (!(level.getBlockEntity(controllerPos) instanceof TileQuantumEntanglementController controller)
                    || !controller.isPortMember(worldPosition) || !controller.isFormed()) continue;
            return controller;
        }
        return null;
    }

    private final class EnergyStorage implements IEnergyStorage {
        @Override
        public int receiveEnergy(int maxReceive, boolean simulate) {
            TileQuantumEntanglementController controller = getController();
            return controller == null ? 0 : controller.receiveConstraintEnergy(maxReceive, simulate);
        }

        @Override
        public int extractEnergy(int maxExtract, boolean simulate) {
            return 0;
        }

        @Override
        public int getEnergyStored() {
            return 0;
        }

        @Override
        public int getMaxEnergyStored() {
            TileQuantumEntanglementController controller = getController();
            return controller == null || !controller.canReceiveConstraintEnergy()
                    ? 0 : Integer.MAX_VALUE;
        }

        @Override
        public boolean canExtract() {
            return false;
        }

        @Override
        public boolean canReceive() {
            TileQuantumEntanglementController controller = getController();
            return controller != null && controller.canReceiveConstraintEnergy();
        }
    }
}
