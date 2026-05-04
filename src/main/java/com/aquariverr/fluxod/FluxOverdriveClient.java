package com.aquariverr.fluxod;

import com.aquariverr.fluxod.register.RegistryBlockEntityTypes;
import com.aquariverr.fluxod.register.RegistryBlocks;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterColorHandlersEvent;
import sonar.fluxnetworks.client.FluxColorHandler;
import sonar.fluxnetworks.client.render.FluxStorageEntityRenderer;

@EventBusSubscriber(modid = FluxOverdrive.MODID, value = Dist.CLIENT)
public class FluxOverdriveClient {

    @SubscribeEvent
    static void registerBlockColorHandlers(RegisterColorHandlersEvent.Block event) {
        event.register(FluxColorHandler.INSTANCE,
                RegistryBlocks.ENDER_FLUX_STORAGE.get(),
                RegistryBlocks.NETHER_FLUX_STORAGE.get(),
                RegistryBlocks.FLUX_STORAGE_TERMINAL.get(),
                RegistryBlocks.FLUX_FE_STORAGE.get(),
                RegistryBlocks.FLUX_LINK_CRYSTAL.get());
    }

    @SubscribeEvent
    static void registerEntityRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(RegistryBlockEntityTypes.ENDER_FLUX_STORAGE.get(), FluxStorageEntityRenderer.PROVIDER);
        event.registerBlockEntityRenderer(RegistryBlockEntityTypes.NETHER_FLUX_STORAGE.get(), FluxStorageEntityRenderer.PROVIDER);
        event.registerBlockEntityRenderer(RegistryBlockEntityTypes.FLUX_STORAGE_TERMINAL.get(), FluxStorageEntityRenderer.PROVIDER);
    }
}
