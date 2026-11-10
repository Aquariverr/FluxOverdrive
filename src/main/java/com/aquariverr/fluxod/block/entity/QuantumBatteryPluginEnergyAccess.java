package com.aquariverr.fluxod.block.entity;

import java.math.BigInteger;
import java.util.Objects;

/** Atomic energy access for server-side quantum battery plugins. */
public final class QuantumBatteryPluginEnergyAccess {

    private QuantumBatteryPluginEnergyAccess() {}

    public static BigInteger available(TileQuantumBatteryController controller) {
        Objects.requireNonNull(controller, "controller");
        return controller.isOperational() ? controller.getStoredEnergy() : BigInteger.ZERO;
    }

    public static boolean canConsume(TileQuantumBatteryController controller, BigInteger amount) {
        Objects.requireNonNull(controller, "controller");
        Objects.requireNonNull(amount, "amount");
        return amount.signum() >= 0 && controller.isOperational()
                && controller.getStoredEnergy().compareTo(amount) >= 0;
    }

    /**
     * Consumes the full amount or nothing without counting the charge as
     * Flux-network I/O used for core-upgrade progression.
     */
    public static boolean tryConsume(TileQuantumBatteryController controller,
                                     BigInteger amount,
                                     boolean simulate) {
        Objects.requireNonNull(controller, "controller");
        Objects.requireNonNull(amount, "amount");
        if (amount.signum() < 0 || !canConsume(controller, amount)) return false;
        if (simulate || amount.signum() == 0) return true;

        return controller.tryConsumePluginEnergy(amount);
    }
}
