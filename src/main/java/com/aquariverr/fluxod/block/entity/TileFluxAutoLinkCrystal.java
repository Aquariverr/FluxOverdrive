package com.aquariverr.fluxod.block.entity;

import com.aquariverr.fluxod.register.RegistryBlockEntityTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.energy.IEnergyStorage;
import sonar.fluxnetworks.api.FluxCapabilities;
import sonar.fluxnetworks.api.device.FluxDeviceType;
import sonar.fluxnetworks.api.device.IFluxPoint;
import sonar.fluxnetworks.api.energy.IBlockEnergyConnector;
import sonar.fluxnetworks.api.energy.IFNEnergyStorage;
import sonar.fluxnetworks.common.device.FluxConnectorHandler;
import sonar.fluxnetworks.common.util.EnergyUtils;
import sonar.fluxnetworks.common.util.FluxUtils;

import javax.annotation.Nonnull;
import javax.annotation.ParametersAreNonnullByDefault;

@ParametersAreNonnullByDefault
public class TileFluxAutoLinkCrystal extends TileFluxAutoCrystal implements IFluxPoint {

    private static final long INPUT_PROBE_ENERGY = 4L;

    private final WirelessPointHandler handler = createPointHandler();

    public TileFluxAutoLinkCrystal(BlockPos pos, BlockState state) {
        super(RegistryBlockEntityTypes.FLUX_AUTO_LINK_CRYSTAL.get(), pos, state);
    }

    @Nonnull
    @Override
    public FluxDeviceType getDeviceType() {
        return FluxDeviceType.POINT;
    }

    @Nonnull
    @Override
    public FluxConnectorHandler getTransferHandler() {
        return handler;
    }

    @Override
    protected boolean canTransferWith(BlockEntity target, Direction side) {
        IBlockEnergyConnector connector = EnergyUtils.getConnector(target, side);
        return connector != null && connector.canSendTo(target, side);
    }

    @Override
    protected boolean canAutoLinkWith(BlockEntity target, Direction side) {
        IBlockEnergyConnector connector = EnergyUtils.getConnector(target, side);
        return connector != null && connector.canSendTo(target, side)
                && (connector.sendTo(INPUT_PROBE_ENERGY, target, side, true) > 0
                        || isFullEnergyStorage(target, side));
    }

    private static boolean isFullEnergyStorage(BlockEntity target, Direction side) {
        IFNEnergyStorage fnStorage = FluxUtils.get(target, FluxCapabilities.BLOCK, side);
        if (fnStorage != null) {
            long capacity = fnStorage.getMaxEnergyStoredL();
            return capacity > 0 && fnStorage.getEnergyStoredL() >= capacity;
        }

        IEnergyStorage feStorage = FluxUtils.get(target, Capabilities.EnergyStorage.BLOCK, side);
        if (feStorage != null) {
            int capacity = feStorage.getMaxEnergyStored();
            return capacity > 0 && feStorage.getEnergyStored() >= capacity;
        }

        return true;
    }
}
