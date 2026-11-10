package com.aquariverr.fluxod.mixin.client;

import com.aquariverr.fluxod.util.BigIntegerFormatter;
import com.aquariverr.fluxod.util.ExactNetworkStatistics;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import sonar.fluxnetworks.api.energy.EnergyType;
import sonar.fluxnetworks.client.gui.tab.GuiTabStatistics;
import sonar.fluxnetworks.common.connection.NetworkStatistics;

@Mixin(value = GuiTabStatistics.class, remap = false)
public abstract class GuiTabStatisticsMixin {

    @Redirect(method = "drawForegroundLayer", at = @At(value = "INVOKE",
            target = "Lsonar/fluxnetworks/api/energy/EnergyType;getUsage(J)Ljava/lang/String;"),
            require = 2)
    private String fluxod$useCompactTransferValue(EnergyType energyType, long value) {
        return energyType.getUsageCompact(value < 0 ? Long.MAX_VALUE : value);
    }

    @Redirect(method = "drawForegroundLayer", at = @At(value = "INVOKE",
            target = "Lsonar/fluxnetworks/api/energy/EnergyType;getStorage(J)Ljava/lang/String;", ordinal = 0),
            require = 1)
    private String fluxod$useCompactBufferValue(EnergyType energyType, long value) {
        return energyType.getStorageCompact(value < 0 ? Long.MAX_VALUE : value);
    }

    @Redirect(method = "drawForegroundLayer", at = @At(value = "INVOKE",
            target = "Lsonar/fluxnetworks/api/energy/EnergyType;getStorage(J)Ljava/lang/String;", ordinal = 1),
            require = 1)
    private String fluxod$useExactStorageValue(EnergyType energyType, long value) {
        NetworkStatistics statistics = ((GuiTabStatistics) (Object) this).getNetwork().getStatistics();
        if (statistics instanceof ExactNetworkStatistics exactStatistics) {
            return BigIntegerFormatter.formatStorage(exactStatistics.fluxod$getEnergyTotals(),
                    energyType.getStorageSuffix());
        }
        return energyType.getStorageCompact(value < 0 ? Long.MAX_VALUE : value);
    }
}
