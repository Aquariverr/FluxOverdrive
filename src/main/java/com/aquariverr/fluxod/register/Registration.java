package com.aquariverr.fluxod.register;

import com.aquariverr.fluxod.FluxOverdrive;
import net.minecraft.core.registries.BuiltInRegistries;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.registries.RegisterEvent;

import javax.annotation.Nonnull;

@EventBusSubscriber(modid = FluxOverdrive.MODID)
public class Registration {

    @SubscribeEvent
    public static void register(@Nonnull RegisterEvent event) {
        event.register(BuiltInRegistries.BLOCK.key(), RegistryBlocks::register);
        event.register(BuiltInRegistries.ITEM.key(), RegistryItems::register);
        event.register(BuiltInRegistries.BLOCK_ENTITY_TYPE.key(), RegistryBlockEntityTypes::register);
        event.register(BuiltInRegistries.CREATIVE_MODE_TAB.key(), RegistryCreativeModeTabs::register);
    }

    @SubscribeEvent
    public static void registerCapabilities(@Nonnull RegisterCapabilitiesEvent event) {
        RegistryBlockEntityTypes.registerBlockCapabilities(event);
    }
}
