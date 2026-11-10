package com.aquariverr.fluxod.block.entity;

import com.aquariverr.fluxod.register.RegistryBlockEntityTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import sonar.fluxnetworks.api.device.FluxDeviceType;
import sonar.fluxnetworks.api.device.IFluxPoint;
import sonar.fluxnetworks.api.energy.IBlockEnergyConnector;
import sonar.fluxnetworks.common.device.FluxConnectorHandler;
import sonar.fluxnetworks.common.util.EnergyUtils;

import javax.annotation.Nonnull;
import javax.annotation.ParametersAreNonnullByDefault;

@ParametersAreNonnullByDefault
public class TileFluxLinkCrystal extends TileFluxWirelessCrystal implements IFluxPoint {

    private final WirelessPointHandler handler = createPointHandler();

    public TileFluxLinkCrystal(BlockPos pos, BlockState state) {
        this(RegistryBlockEntityTypes.FLUX_LINK_CRYSTAL.get(), pos, state);
    }

    protected TileFluxLinkCrystal(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
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
}
