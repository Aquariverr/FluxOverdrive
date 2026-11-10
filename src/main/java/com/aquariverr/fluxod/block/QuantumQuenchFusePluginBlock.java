package com.aquariverr.fluxod.block;

import com.aquariverr.fluxod.multiblock.QuantumBatteryPlugin;
import com.aquariverr.fluxod.multiblock.QuantumBatteryQuenchInterceptor;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

public final class QuantumQuenchFusePluginBlock extends QuantumBatteryStructureBlock
        implements QuantumBatteryPlugin, QuantumBatteryQuenchInterceptor {

    public QuantumQuenchFusePluginBlock(Properties properties) {
        super(properties);
    }

    @Override
    public int getPluginTier() {
        return 1;
    }

    @Override
    public boolean canInstallInRing(int ringTier) {
        return ringTier == 1;
    }

    @Override
    public boolean tryInterceptQuench(ServerLevel level, BlockPos pluginPos) {
        return level.removeBlock(pluginPos, false);
    }
}
