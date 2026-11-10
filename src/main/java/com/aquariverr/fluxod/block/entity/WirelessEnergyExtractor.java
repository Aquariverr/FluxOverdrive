package com.aquariverr.fluxod.block.entity;

import com.aquariverr.fluxod.integration.gtceu.FluxAutoExtractGtceuIntegration;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.energy.IEnergyStorage;
import sonar.fluxnetworks.api.FluxCapabilities;
import sonar.fluxnetworks.api.device.IFluxDevice;
import sonar.fluxnetworks.api.energy.IFNEnergyStorage;
import sonar.fluxnetworks.common.util.FluxUtils;

final class WirelessEnergyExtractor {

    private static final String GTCEU_MOD_ID = "gtceu";

    static boolean canExtract(BlockEntity target, Direction side) {
        if (target.isRemoved() || target instanceof IFluxDevice) return false;

        IFNEnergyStorage fnStorage = FluxUtils.get(target, FluxCapabilities.BLOCK, side);
        if (fnStorage != null) return fnStorage.canExtract();

        IEnergyStorage feStorage = FluxUtils.get(target, Capabilities.EnergyStorage.BLOCK, side);
        if (feStorage != null) return feStorage.canExtract();

        return ModList.get().isLoaded(GTCEU_MOD_ID)
                && FluxAutoExtractGtceuIntegration.canExtract(target, side);
    }

    static long extract(BlockEntity target, Direction side, long requested) {
        if (requested <= 0 || target.isRemoved() || target instanceof IFluxDevice) return 0;

        IFNEnergyStorage fnStorage = FluxUtils.get(target, FluxCapabilities.BLOCK, side);
        if (fnStorage != null) {
            if (!fnStorage.canExtract()) return 0;
            return clampExtracted(requested, fnStorage.extractEnergyL(requested, false));
        }

        IEnergyStorage feStorage = FluxUtils.get(target, Capabilities.EnergyStorage.BLOCK, side);
        if (feStorage != null) {
            if (!feStorage.canExtract()) return 0;
            int boundedRequest = (int) Math.min(requested, Integer.MAX_VALUE);
            return clampExtracted(requested, feStorage.extractEnergy(boundedRequest, false));
        }

        if (!ModList.get().isLoaded(GTCEU_MOD_ID)) return 0;
        return clampExtracted(requested, FluxAutoExtractGtceuIntegration.extract(target, side, requested));
    }

    private static long clampExtracted(long requested, long extracted) {
        return Math.max(0, Math.min(requested, extracted));
    }

    private WirelessEnergyExtractor() {
    }
}
