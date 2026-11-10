package com.aquariverr.fluxod.mixin.client;

import com.aquariverr.fluxod.util.FluxDeviceTiming;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import sonar.fluxnetworks.api.device.IFluxDevice;
import sonar.fluxnetworks.client.gui.tab.GuiTabConnections;

import java.util.ArrayList;
import java.util.List;

@Mixin(value = GuiTabConnections.class, remap = false)
public abstract class GuiTabConnectionsMixin {

    @Inject(method = "getElementTooltips", at = @At("RETURN"), remap = false,
            cancellable = true, require = 1)
    private void fluxod$addAverageTickTooltip(IFluxDevice element,
                                               CallbackInfoReturnable<List<Component>> callback) {
        if (!(element instanceof FluxDeviceTiming timing)) return;

        Component timingLine;
        if (!element.isChunkLoaded()) {
            timingLine = Component.translatable("gui.flux_overdrive.connection.average_tick_unavailable")
                    .withStyle(ChatFormatting.GRAY);
        } else if (timing.fluxod$getAverageTickMicro() == FluxDeviceTiming.UNAVAILABLE) {
            timingLine = Component.translatable("gui.flux_overdrive.connection.average_tick_sampling")
                    .withStyle(ChatFormatting.GRAY);
        } else {
            timingLine = Component.translatable("gui.flux_overdrive.connection.average_tick",
                    Component.literal(Integer.toString(timing.fluxod$getAverageTickMicro()))
                            .withStyle(ChatFormatting.GREEN));
        }

        List<Component> components = new ArrayList<>(callback.getReturnValue());
        components.add(Math.max(0, components.size() - 2), timingLine);
        callback.setReturnValue(components);
    }
}
