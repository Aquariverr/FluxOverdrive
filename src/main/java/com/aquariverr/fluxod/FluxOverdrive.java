package com.aquariverr.fluxod;

import com.mojang.logging.LogUtils;
import com.aquariverr.fluxod.register.FluxOdDataComponents;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.loading.FMLLoader;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import org.slf4j.Logger;

import javax.annotation.Nonnull;

@Mod(FluxOverdrive.MODID)
public class FluxOverdrive {
    public static final String MODID = "flux_overdrive";
    public static final Logger LOGGER = LogUtils.getLogger();

    public FluxOverdrive(IEventBus bus, ModContainer container) {
        container.registerConfig(ModConfig.Type.SERVER, Config.SPEC);
        bus.addListener(Config::onConfigEvent);
        if (FMLLoader.getDist().isClient()) {
            container.registerExtensionPoint(IConfigScreenFactory.class, ConfigurationScreen::new);
        }
        FluxOdDataComponents.REGISTRY.register(bus);
    }

    @Nonnull
    public static ResourceLocation location(String path) {
        return ResourceLocation.fromNamespaceAndPath(MODID, path);
    }
}
