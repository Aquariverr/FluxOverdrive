package com.aquariverr.fluxod.integration.gtceu;

import com.gregtechceu.gtceu.api.capability.GTCapability;
import com.gregtechceu.gtceu.api.capability.IEnergyContainer;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.entity.BlockEntity;
import sonar.fluxnetworks.common.util.FluxUtils;

public final class FluxAutoExtractGtceuIntegration {

    public static boolean canExtract(BlockEntity target, Direction side) {
        IEnergyContainer container = getContainer(target, side);
        return container != null && container.outputsEnergy(side);
    }

    public static long extract(BlockEntity target, Direction side, long requestedFe) {
        IEnergyContainer container = getContainer(target, side);
        if (container == null || !container.outputsEnergy(side)) return 0;

        long requestedEu = GtceuEnergyMath.euToExtract(requestedFe,
                container.getEnergyStored(), container.getOutputVoltage(), container.getOutputAmperage());
        if (requestedEu <= 0) return 0;
        long extractedEu = Math.max(0, Math.min(requestedEu, container.removeEnergy(requestedEu)));
        return Math.min(requestedFe, GtceuEnergyMath.toFe(extractedEu));
    }

    private static IEnergyContainer getContainer(BlockEntity target, Direction side) {
        if (target.isRemoved()) return null;
        return FluxUtils.get(target, GTCapability.CAPABILITY_ENERGY_CONTAINER, side);
    }

    private FluxAutoExtractGtceuIntegration() {
    }
}
