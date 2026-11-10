package com.aquariverr.fluxod.register;

import com.aquariverr.fluxod.FluxOverdrive;
import com.aquariverr.fluxod.menu.QuantumBatteryMenu;
import com.aquariverr.fluxod.menu.QuantumEntanglementMenu;
import com.aquariverr.fluxod.menu.QuantumCoolingPluginMenu;
import com.aquariverr.fluxod.menu.QuantumFeedbackPluginMenu;
import com.aquariverr.fluxod.menu.MiniatureUniverseMenu;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.MenuType;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.RegisterEvent;

public final class RegistryMenuTypes {

    public static final ResourceLocation QUANTUM_BATTERY_KEY = FluxOverdrive.location("quantum_battery");
    public static final DeferredHolder<MenuType<?>, MenuType<QuantumBatteryMenu>> QUANTUM_BATTERY =
            DeferredHolder.create(BuiltInRegistries.MENU.key(), QUANTUM_BATTERY_KEY);
    public static final ResourceLocation QUANTUM_ENTANGLEMENT_KEY = FluxOverdrive.location("quantum_entanglement");
    public static final DeferredHolder<MenuType<?>, MenuType<QuantumEntanglementMenu>> QUANTUM_ENTANGLEMENT =
            DeferredHolder.create(BuiltInRegistries.MENU.key(), QUANTUM_ENTANGLEMENT_KEY);
    public static final ResourceLocation QUANTUM_COOLING_PLUGIN_KEY = FluxOverdrive.location("quantum_cooling_plugin");
    public static final DeferredHolder<MenuType<?>, MenuType<QuantumCoolingPluginMenu>> QUANTUM_COOLING_PLUGIN =
            DeferredHolder.create(BuiltInRegistries.MENU.key(), QUANTUM_COOLING_PLUGIN_KEY);
    public static final ResourceLocation QUANTUM_FEEDBACK_PLUGIN_KEY =
            FluxOverdrive.location("quantum_feedback_plugin");
    public static final DeferredHolder<MenuType<?>, MenuType<QuantumFeedbackPluginMenu>> QUANTUM_FEEDBACK_PLUGIN =
            DeferredHolder.create(BuiltInRegistries.MENU.key(), QUANTUM_FEEDBACK_PLUGIN_KEY);
    public static final ResourceLocation MINIATURE_UNIVERSE_KEY = FluxOverdrive.location("miniature_universe");
    public static final DeferredHolder<MenuType<?>, MenuType<MiniatureUniverseMenu>> MINIATURE_UNIVERSE =
            DeferredHolder.create(BuiltInRegistries.MENU.key(), MINIATURE_UNIVERSE_KEY);

    static void register(RegisterEvent.RegisterHelper<MenuType<?>> helper) {
        helper.register(QUANTUM_BATTERY_KEY, IMenuTypeExtension.create(QuantumBatteryMenu::new));
        helper.register(QUANTUM_ENTANGLEMENT_KEY, IMenuTypeExtension.create(QuantumEntanglementMenu::new));
        helper.register(QUANTUM_COOLING_PLUGIN_KEY, IMenuTypeExtension.create(QuantumCoolingPluginMenu::new));
        helper.register(QUANTUM_FEEDBACK_PLUGIN_KEY, IMenuTypeExtension.create(QuantumFeedbackPluginMenu::new));
        helper.register(MINIATURE_UNIVERSE_KEY, IMenuTypeExtension.create(MiniatureUniverseMenu::new));
    }

    private RegistryMenuTypes() {}
}
