package com.aquariverr.fluxod.register;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.RegisterEvent;
import com.aquariverr.fluxod.FluxOverdrive;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;

public class RegistryCreativeModeTabs {
    public static final ResourceLocation CREATIVE_MODE_TAB_KEY = FluxOverdrive.location("tab");

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> CREATIVE_MODE_TAB =
            DeferredHolder.create(Registries.CREATIVE_MODE_TAB, CREATIVE_MODE_TAB_KEY);

    static void register(RegisterEvent.RegisterHelper<CreativeModeTab> helper) {
        helper.register(CREATIVE_MODE_TAB_KEY, CreativeModeTab.builder()
                .title(Component.translatable("itemGroup." + FluxOverdrive.MODID))
                .icon(() -> new ItemStack(RegistryItems.ENDER_FLUX_CORE.get()))
                .withTabsBefore(ResourceLocation.fromNamespaceAndPath("fluxnetworks", "tab"))
                .displayItems((parameters, output) -> {
                    output.accept(RegistryItems.ENDER_FLUX_BLOCK.get());
                    output.accept(RegistryItems.NETHER_FLUX_BLOCK.get());
                    output.accept(RegistryItems.ENDER_FLUX_STORAGE.get());
                    output.accept(RegistryItems.NETHER_FLUX_STORAGE.get());
                    output.accept(RegistryItems.FLUX_STORAGE_TERMINAL.get());
                    output.accept(RegistryItems.FLUX_FE_STORAGE.get());
                    output.accept(RegistryItems.ENDER_FLUX_DUST.get());
                    output.accept(RegistryItems.ENDER_FLUX_CORE.get());
                    output.accept(RegistryItems.NETHER_FLUX_DUST.get());
                    output.accept(RegistryItems.NETHER_FLUX_CORE.get());
                    output.accept(RegistryItems.FLUX_LINK_CRYSTAL.get());
                    output.accept(RegistryItems.FLUX_LINK_TOOL.get());
                })
                .build());
    }

    private RegistryCreativeModeTabs() {}
}