package com.aquariverr.fluxod.integration.mekanism;

import com.aquariverr.fluxod.block.entity.TileQuantumAdvancedCoolingPlugin;
import com.aquariverr.fluxod.block.entity.TileQuantumEntanglementResourcePort;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;

public final class QuantumCoolingChemicalIntegration {

    private static final String MEKANISM_MOD_ID = "mekanism";

    public static OptionalChemicalTank create(long capacity, Runnable contentsChanged) {
        if (ModList.get().isLoaded(MEKANISM_MOD_ID)) {
            return new MekanismChemicalTankAdapter(capacity, contentsChanged);
        }
        return new UnavailableChemicalTank(capacity);
    }

    public static boolean isAvailable() {
        return ModList.get().isLoaded(MEKANISM_MOD_ID);
    }

    public static Object createEntanglementProxy(TileQuantumEntanglementResourcePort port) {
        return isAvailable() ? new MekanismEntanglementChemicalHandler(port) : null;
    }

    public static void registerCapability(
            RegisterCapabilitiesEvent event,
            BlockEntityType<TileQuantumAdvancedCoolingPlugin> blockEntityType) {
        if (ModList.get().isLoaded(MEKANISM_MOD_ID)) {
            MekanismChemicalTankAdapter.registerCapability(event, blockEntityType);
        }
    }

    public static void registerEntanglementCapability(
            RegisterCapabilitiesEvent event,
            BlockEntityType<TileQuantumEntanglementResourcePort> blockEntityType) {
        if (isAvailable()) {
            MekanismEntanglementChemicalHandler.registerCapability(event, blockEntityType);
        }
    }

    public static Object createMiniatureHandler(com.aquariverr.fluxod.block.entity.TileMiniatureUniverse owner) {
        return isAvailable() ? new MekanismMiniatureChemicalHandler(owner) : null;
    }

    public static com.aquariverr.fluxod.miniature.model.ResourceKey chemicalKeyOf(ItemStack stack) {
        return isAvailable() ? MekanismMiniatureChemicalHandler.keyOfHeld(stack) : null;
    }

    public static void registerMiniatureCapability(
            RegisterCapabilitiesEvent event,
            net.minecraft.world.level.block.entity.BlockEntityType<com.aquariverr.fluxod.block.entity.TileMiniatureUniverse> type) {
        if (isAvailable()) {
            MekanismMiniatureChemicalHandler.registerCapability(event, type);
        }
    }

    public static void tickEntanglementTransfer(TileQuantumEntanglementResourcePort port,
                                                BlockPos neighborPos, Direction neighborSide) {
        if (isAvailable()) {
            MekanismEntanglementChemicalHandler.tickAutomaticTransfer(
                    port, neighborPos, neighborSide);
        }
    }

    private static final class UnavailableChemicalTank implements OptionalChemicalTank {
        private final long capacity;
        private CompoundTag preservedData = new CompoundTag();

        private UnavailableChemicalTank(long capacity) {
            this.capacity = Math.max(1L, capacity);
        }

        @Override
        public boolean isAvailable() {
            return false;
        }

        @Override
        public long getAmount() {
            return 0L;
        }

        @Override
        public long getCapacity() {
            return capacity;
        }

        @Override
        public long getTargetMilliKelvin() {
            return -1L;
        }

        @Override
        public String getChemicalId() {
            return "";
        }

        @Override
        public int getTint() {
            return 0xFF7F7F7F;
        }

        @Override
        public long extract(long amount) {
            return 0L;
        }

        @Override
        public boolean interactWithHeldItem(Player player, InteractionHand hand) {
            return false;
        }

        @Override
        public CompoundTag serializeNBT(HolderLookup.Provider registries) {
            return preservedData.copy();
        }

        @Override
        public void deserializeNBT(HolderLookup.Provider registries, CompoundTag tag) {
            preservedData = tag.copy();
        }

        @Override
        public Object getCapability() {
            return null;
        }
    }

    private QuantumCoolingChemicalIntegration() {}
}
