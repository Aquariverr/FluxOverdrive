package com.aquariverr.fluxod.multiblock;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

/** A one-shot plugin that can replace the destructive Quantum Battery quench path. */
public interface QuantumBatteryQuenchInterceptor {

    boolean tryInterceptQuench(ServerLevel level, BlockPos pluginPos);
}
