package com.aquariverr.fluxod.mixin.accessor;

import net.minecraft.core.GlobalPos;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import sonar.fluxnetworks.common.device.TileFluxDevice;

import javax.annotation.Nullable;

@Mixin(value = TileFluxDevice.class, remap = false)
public interface TileFluxDeviceAccessor {

    @Nullable
    @Accessor(value = "mPlayerUsing", remap = false)
    Player fluxod$getPlayerUsing();

    @Accessor(value = "mGlobalPos", remap = false)
    void fluxod$setGlobalPos(GlobalPos globalPos);
}
