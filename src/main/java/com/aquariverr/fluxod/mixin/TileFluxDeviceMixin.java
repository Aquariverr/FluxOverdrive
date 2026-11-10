package com.aquariverr.fluxod.mixin;

import com.aquariverr.fluxod.util.DeviceTickSampler;
import com.aquariverr.fluxod.util.FluxDeviceTiming;
import net.minecraft.nbt.CompoundTag;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import sonar.fluxnetworks.api.FluxConstants;
import sonar.fluxnetworks.common.connection.FluxNetwork;
import sonar.fluxnetworks.common.device.TileFluxDevice;

@Mixin(value = TileFluxDevice.class, remap = false)
public abstract class TileFluxDeviceMixin implements FluxDeviceTiming {

    @Unique
    private final DeviceTickSampler fluxod$deviceTickSampler = new DeviceTickSampler();

    @Inject(method = "writeCustomTag", at = @At("TAIL"), remap = false, require = 1)
    private void fluxod$writeAverageTick(CompoundTag tag, byte type, CallbackInfo callback) {
        if (type == FluxConstants.NBT_PHANTOM_UPDATE) {
            tag.putInt(AVERAGE_TICK_MICRO_TAG, fluxod$deviceTickSampler.averageTickMicro());
        }
    }

    @Inject(method = "connect", at = @At(value = "INVOKE",
            target = "Lsonar/fluxnetworks/common/connection/TransferHandler;onNetworkChanged()V",
            shift = At.Shift.AFTER, remap = false), remap = false, require = 1)
    private void fluxod$resetTimingAfterNetworkChange(FluxNetwork network,
                                                       CallbackInfoReturnable<Boolean> callback) {
        fluxod$deviceTickSampler.reset();
    }

    @Inject(method = "onChunkUnloaded", at = @At("HEAD"), remap = false, require = 1)
    private void fluxod$resetTimingOnChunkUnload(CallbackInfo callback) {
        fluxod$deviceTickSampler.reset();
    }

    @Inject(method = "setRemoved", at = @At("HEAD"), remap = false, require = 1)
    private void fluxod$resetTimingOnRemoval(CallbackInfo callback) {
        fluxod$deviceTickSampler.reset();
    }

    @Override
    public int fluxod$getAverageTickMicro() {
        return fluxod$deviceTickSampler.averageTickMicro();
    }

    @Override
    public void fluxod$beginTimingTick() {
        fluxod$deviceTickSampler.beginTick();
    }

    @Override
    public void fluxod$recordTimingNanos(long nanos) {
        fluxod$deviceTickSampler.recordNanos(nanos);
    }

    @Override
    public void fluxod$endTimingTick() {
        fluxod$deviceTickSampler.endTick();
    }

    @Override
    public void fluxod$resetTiming() {
        fluxod$deviceTickSampler.reset();
    }
}
