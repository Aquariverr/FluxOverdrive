package com.aquariverr.fluxod.mixin;

import com.aquariverr.fluxod.util.FluxDeviceTiming;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import sonar.fluxnetworks.common.connection.FluxNetwork;
import sonar.fluxnetworks.common.connection.ServerFluxNetwork;
import sonar.fluxnetworks.common.connection.TransferHandler;
import sonar.fluxnetworks.common.device.TileFluxDevice;

import java.util.IdentityHashMap;

@Mixin(value = ServerFluxNetwork.class, remap = false)
public abstract class ServerFluxNetworkMixin {

    @Unique
    private final IdentityHashMap<TransferHandler, FluxDeviceTiming> fluxod$deviceTimings =
            new IdentityHashMap<>();

    @Inject(method = "onEndServerTick", at = @At(value = "INVOKE",
            target = "Lsonar/fluxnetworks/common/connection/ServerFluxNetwork;handleConnectionQueue()V",
            shift = At.Shift.AFTER, remap = false), remap = false, require = 1)
    private void fluxod$beginDeviceTiming(CallbackInfo callback) {
        fluxod$deviceTimings.clear();
        ServerFluxNetwork network = (ServerFluxNetwork) (Object) this;
        for (TileFluxDevice device : network.getLogicalDevices(FluxNetwork.ANY)) {
            if (device instanceof FluxDeviceTiming timing) {
                timing.fluxod$beginTimingTick();
                fluxod$deviceTimings.put(device.getTransferHandler(), timing);
            }
        }
    }

    @WrapOperation(method = "onEndServerTick", at = @At(value = "INVOKE",
            target = "Lsonar/fluxnetworks/common/connection/TransferHandler;onCycleStart()V",
            remap = false), remap = false, require = 1)
    private void fluxod$timeCycleStart(TransferHandler handler, Operation<Void> original) {
        FluxDeviceTiming timing = fluxod$deviceTimings.get(handler);
        long started = System.nanoTime();
        try {
            original.call(handler);
        } finally {
            if (timing != null) timing.fluxod$recordTimingNanos(System.nanoTime() - started);
        }
    }

    @WrapOperation(method = "onEndServerTick", at = @At(value = "INVOKE",
            target = "Lsonar/fluxnetworks/common/connection/TransferHandler;onCycleEnd()V",
            remap = false), remap = false, require = 1)
    private void fluxod$timeCycleEnd(TransferHandler handler, Operation<Void> original) {
        FluxDeviceTiming timing = fluxod$deviceTimings.get(handler);
        long started = System.nanoTime();
        try {
            original.call(handler);
        } finally {
            if (timing != null) timing.fluxod$recordTimingNanos(System.nanoTime() - started);
        }
    }

    @Inject(method = "onEndServerTick", at = @At("RETURN"), remap = false, require = 1)
    private void fluxod$finishDeviceTiming(CallbackInfo callback) {
        for (FluxDeviceTiming timing : fluxod$deviceTimings.values()) {
            timing.fluxod$endTimingTick();
        }
        fluxod$deviceTimings.clear();
    }
}
