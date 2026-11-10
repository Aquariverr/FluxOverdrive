package com.aquariverr.fluxod.mixin;

import com.aquariverr.fluxod.util.FluxDeviceTiming;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import sonar.fluxnetworks.api.FluxConstants;
import sonar.fluxnetworks.common.connection.PhantomFluxDevice;

@Mixin(value = PhantomFluxDevice.class, remap = false)
public abstract class PhantomFluxDeviceMixin implements FluxDeviceTiming {

    @Unique
    private int fluxod$averageTickMicro = UNAVAILABLE;

    @Inject(method = "writeCustomTag", at = @At("TAIL"), remap = false, require = 1)
    private void fluxod$writeAverageTick(CompoundTag tag, byte type, CallbackInfo callback) {
        if (type == FluxConstants.NBT_PHANTOM_UPDATE) {
            tag.putInt(AVERAGE_TICK_MICRO_TAG, fluxod$averageTickMicro);
        }
    }

    @Inject(method = "readCustomTag", at = @At("TAIL"), remap = false, require = 1)
    private void fluxod$readAverageTick(CompoundTag tag, byte type, CallbackInfo callback) {
        if (type == FluxConstants.NBT_SAVE_ALL) {
            fluxod$averageTickMicro = UNAVAILABLE;
            return;
        }
        if (type != FluxConstants.NBT_PHANTOM_UPDATE) return;

        PhantomFluxDevice device = (PhantomFluxDevice) (Object) this;
        if (!device.isChunkLoaded() || !tag.contains(AVERAGE_TICK_MICRO_TAG, Tag.TAG_ANY_NUMERIC)) {
            fluxod$averageTickMicro = UNAVAILABLE;
            return;
        }
        fluxod$averageTickMicro = Math.max(UNAVAILABLE, tag.getInt(AVERAGE_TICK_MICRO_TAG));
    }

    @Override
    public int fluxod$getAverageTickMicro() {
        return fluxod$averageTickMicro;
    }
}
