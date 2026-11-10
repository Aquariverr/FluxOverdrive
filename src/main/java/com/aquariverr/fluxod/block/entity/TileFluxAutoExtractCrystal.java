package com.aquariverr.fluxod.block.entity;

import com.aquariverr.fluxod.register.RegistryBlockEntityTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import sonar.fluxnetworks.api.device.FluxDeviceType;
import sonar.fluxnetworks.api.device.IFluxPlug;
import sonar.fluxnetworks.common.device.FluxConnectorHandler;

import javax.annotation.Nonnull;
import javax.annotation.ParametersAreNonnullByDefault;
@ParametersAreNonnullByDefault
public class TileFluxAutoExtractCrystal extends TileFluxAutoCrystal implements IFluxPlug {

    private final WirelessPlugHandler handler = createPlugHandler();

    public TileFluxAutoExtractCrystal(BlockPos pos, BlockState state) {
        super(RegistryBlockEntityTypes.FLUX_AUTO_EXTRACT_CRYSTAL.get(), pos, state);
    }

    @Nonnull
    @Override
    public FluxDeviceType getDeviceType() {
        return FluxDeviceType.PLUG;
    }

    @Nonnull
    @Override
    public FluxConnectorHandler getTransferHandler() {
        return handler;
    }

    @Override
    protected boolean canTransferWith(BlockEntity target, Direction side) {
        return WirelessEnergyExtractor.canExtract(target, side);
    }

    @Override
    public String getInvalidLinkTargetMessageKey() {
        return "message.flux_overdrive.invalid_extract_target";
    }

    @Override
    protected void onServerTick() {
        super.onServerTick();
        handler.pullFromTargets();
    }
}
