package com.aquariverr.fluxod.multiblock;

import com.aquariverr.fluxod.block.entity.TileQuantumBatteryController;

/** Server-side processing hook driven by the formed battery controller. */
public interface QuantumBatteryProcessingPlugin {

    void tickFromQuantumBattery(TileQuantumBatteryController controller);
}
