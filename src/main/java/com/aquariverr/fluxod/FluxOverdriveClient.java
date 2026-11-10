package com.aquariverr.fluxod;

import com.aquariverr.fluxod.client.render.FluxFEStorageEntityRenderer;
import com.aquariverr.fluxod.client.render.FluxOverdriveStorageEntityRenderer;
import com.aquariverr.fluxod.client.particle.QuantumRainParticle;
import com.aquariverr.fluxod.register.RegistryBlockEntityTypes;
import com.aquariverr.fluxod.register.RegistryBlocks;
import net.minecraft.core.particles.ParticleTypes;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterColorHandlersEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import net.neoforged.neoforge.client.event.RegisterParticleProvidersEvent;
import com.aquariverr.fluxod.client.screen.QuantumBatteryScreen;
import com.aquariverr.fluxod.client.screen.QuantumEntanglementScreen;
import com.aquariverr.fluxod.client.screen.QuantumCoolingPluginScreen;
import com.aquariverr.fluxod.client.screen.QuantumFeedbackPluginScreen;
import com.aquariverr.fluxod.client.screen.MiniatureUniverseScreen;
import com.aquariverr.fluxod.register.RegistryMenuTypes;
import sonar.fluxnetworks.client.FluxColorHandler;

@EventBusSubscriber(modid = FluxOverdrive.MODID, value = Dist.CLIENT)
public class FluxOverdriveClient {

    @SubscribeEvent
    static void registerBlockColorHandlers(RegisterColorHandlersEvent.Block event) {
        event.register(FluxColorHandler.INSTANCE,
                RegistryBlocks.ENDER_FLUX_STORAGE.get(),
                RegistryBlocks.NETHER_FLUX_STORAGE.get(),
                RegistryBlocks.FLUX_STORAGE_TERMINAL.get(),
                 RegistryBlocks.FLUX_FE_STORAGE.get(),
                  RegistryBlocks.FLUX_LINK_CRYSTAL.get(),
                  RegistryBlocks.FLUX_EXTRACT_CRYSTAL.get(),
                  RegistryBlocks.FLUX_AUTO_LINK_CRYSTAL.get(),
                 RegistryBlocks.FLUX_AUTO_EXTRACT_CRYSTAL.get());
        event.register(FluxColorHandler.INSTANCE, RegistryBlocks.QUANTUM_BATTERY_CONTROLLER.get());
    }

    @SubscribeEvent
    static void registerItemColorHandlers(RegisterColorHandlersEvent.Item event) {
        event.register(FluxColorHandler.INSTANCE,
                 RegistryBlocks.FLUX_FE_STORAGE.get(),
                  RegistryBlocks.FLUX_LINK_CRYSTAL.get(),
                  RegistryBlocks.FLUX_EXTRACT_CRYSTAL.get(),
                  RegistryBlocks.FLUX_AUTO_LINK_CRYSTAL.get(),
                 RegistryBlocks.FLUX_AUTO_EXTRACT_CRYSTAL.get());
        event.register(FluxColorHandler.INSTANCE, RegistryBlocks.QUANTUM_BATTERY_CONTROLLER.get());
    }

    @SubscribeEvent
    static void registerEntityRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(RegistryBlockEntityTypes.ENDER_FLUX_STORAGE.get(), FluxOverdriveStorageEntityRenderer.PROVIDER);
        event.registerBlockEntityRenderer(RegistryBlockEntityTypes.NETHER_FLUX_STORAGE.get(), FluxOverdriveStorageEntityRenderer.PROVIDER);
        event.registerBlockEntityRenderer(RegistryBlockEntityTypes.FLUX_STORAGE_TERMINAL.get(), FluxOverdriveStorageEntityRenderer.PROVIDER);
        event.registerBlockEntityRenderer(RegistryBlockEntityTypes.FLUX_FE_STORAGE.get(), FluxFEStorageEntityRenderer.PROVIDER);
    }

    @SubscribeEvent
    static void registerMenuScreens(RegisterMenuScreensEvent event) {
        event.register(RegistryMenuTypes.QUANTUM_BATTERY.get(), QuantumBatteryScreen::new);
        event.register(RegistryMenuTypes.QUANTUM_ENTANGLEMENT.get(), QuantumEntanglementScreen::new);
        event.register(RegistryMenuTypes.QUANTUM_COOLING_PLUGIN.get(), QuantumCoolingPluginScreen::new);
        event.register(RegistryMenuTypes.QUANTUM_FEEDBACK_PLUGIN.get(), QuantumFeedbackPluginScreen::new);
        event.register(RegistryMenuTypes.MINIATURE_UNIVERSE.get(), MiniatureUniverseScreen::new);
    }

    @SubscribeEvent
    static void registerParticleProviders(RegisterParticleProvidersEvent event) {
        event.registerSpriteSet(ParticleTypes.RAIN, QuantumRainParticle.Provider::new);
    }
}
