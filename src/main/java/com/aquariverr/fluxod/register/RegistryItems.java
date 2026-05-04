package com.aquariverr.fluxod.register;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.RegisterEvent;
import com.aquariverr.fluxod.FluxOverdrive;
import com.aquariverr.fluxod.item.FluxLinkCrystalItem;
import com.aquariverr.fluxod.item.FluxLinkToolItem;
import com.aquariverr.fluxod.item.FluxStorageTerminalItem;
import sonar.fluxnetworks.common.item.FluxDeviceItem;
import sonar.fluxnetworks.common.item.FluxStorageItem;

public class RegistryItems {
    private static final ResourceLocation ENDER_FLUX_DUST_KEY = FluxOverdrive.location("ender_flux_dust");
    private static final ResourceLocation ENDER_FLUX_CORE_KEY = FluxOverdrive.location("ender_flux_core");
    private static final ResourceLocation NETHER_FLUX_DUST_KEY = FluxOverdrive.location("nether_flux_dust");
    private static final ResourceLocation NETHER_FLUX_CORE_KEY = FluxOverdrive.location("nether_flux_core");
    private static final ResourceLocation FLUX_LINK_CRYSTAL_KEY = FluxOverdrive.location("flux_link_crystal");
    private static final ResourceLocation FLUX_LINK_TOOL_KEY = FluxOverdrive.location("flux_link_tool");

    public static final DeferredItem<BlockItem> ENDER_FLUX_BLOCK = holder(RegistryBlocks.ENDER_FLUX_BLOCK_KEY);
    public static final DeferredItem<BlockItem> NETHER_FLUX_BLOCK = holder(RegistryBlocks.NETHER_FLUX_BLOCK_KEY);
    public static final DeferredItem<FluxStorageItem> ENDER_FLUX_STORAGE = holder(RegistryBlocks.ENDER_FLUX_STORAGE_KEY);
    public static final DeferredItem<FluxStorageItem> NETHER_FLUX_STORAGE = holder(RegistryBlocks.NETHER_FLUX_STORAGE_KEY);
    public static final DeferredItem<FluxStorageItem> FLUX_STORAGE_TERMINAL = holder(RegistryBlocks.FLUX_STORAGE_TERMINAL_KEY);
    public static final DeferredItem<FluxDeviceItem> FLUX_FE_STORAGE = holder(RegistryBlocks.FLUX_FE_STORAGE_KEY);
    public static final DeferredItem<Item> ENDER_FLUX_DUST = holder(ENDER_FLUX_DUST_KEY);
    public static final DeferredItem<Item> ENDER_FLUX_CORE = holder(ENDER_FLUX_CORE_KEY);
    public static final DeferredItem<Item> NETHER_FLUX_DUST = holder(NETHER_FLUX_DUST_KEY);
    public static final DeferredItem<Item> NETHER_FLUX_CORE = holder(NETHER_FLUX_CORE_KEY);
    public static final DeferredItem<FluxLinkCrystalItem> FLUX_LINK_CRYSTAL = holder(FLUX_LINK_CRYSTAL_KEY);
    public static final DeferredItem<FluxLinkToolItem> FLUX_LINK_TOOL = holder(FLUX_LINK_TOOL_KEY);

    static <T extends Item> DeferredItem<T> holder(ResourceLocation location) {
        return DeferredItem.createItem(location);
    }

    static void register(RegisterEvent.RegisterHelper<Item> helper) {
        Item.Properties normalProps = new Item.Properties().fireResistant();

        helper.register(RegistryBlocks.ENDER_FLUX_BLOCK_KEY, new BlockItem(RegistryBlocks.ENDER_FLUX_BLOCK.get(), normalProps));
        helper.register(RegistryBlocks.NETHER_FLUX_BLOCK_KEY, new BlockItem(RegistryBlocks.NETHER_FLUX_BLOCK.get(), normalProps));
        helper.register(RegistryBlocks.ENDER_FLUX_STORAGE_KEY, new FluxStorageItem(RegistryBlocks.ENDER_FLUX_STORAGE.get(), normalProps));
        helper.register(RegistryBlocks.NETHER_FLUX_STORAGE_KEY, new FluxStorageItem(RegistryBlocks.NETHER_FLUX_STORAGE.get(), normalProps));
        helper.register(RegistryBlocks.FLUX_STORAGE_TERMINAL_KEY, new FluxStorageTerminalItem(RegistryBlocks.FLUX_STORAGE_TERMINAL.get(), normalProps));
        helper.register(RegistryBlocks.FLUX_FE_STORAGE_KEY, new FluxDeviceItem(RegistryBlocks.FLUX_FE_STORAGE.get(), normalProps));

        helper.register(ENDER_FLUX_DUST_KEY, new Item(normalProps));
        helper.register(ENDER_FLUX_CORE_KEY, new Item(normalProps));
        helper.register(NETHER_FLUX_DUST_KEY, new Item(normalProps));
        helper.register(NETHER_FLUX_CORE_KEY, new Item(normalProps));

        helper.register(FLUX_LINK_CRYSTAL_KEY, new FluxLinkCrystalItem(RegistryBlocks.FLUX_LINK_CRYSTAL.get(), normalProps));
        helper.register(FLUX_LINK_TOOL_KEY, new FluxLinkToolItem(normalProps.stacksTo(1)));
    }

    private RegistryItems() {}
}
