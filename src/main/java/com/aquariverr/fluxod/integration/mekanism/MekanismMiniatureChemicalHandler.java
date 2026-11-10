package com.aquariverr.fluxod.integration.mekanism;

import com.aquariverr.fluxod.block.entity.TileMiniatureUniverse;
import com.aquariverr.fluxod.miniature.model.ResourceKey;
import com.aquariverr.fluxod.miniature.model.ResourceKind;
import com.aquariverr.fluxod.miniature.model.FactoryLimits;
import com.aquariverr.fluxod.miniature.storage.FactoryAutomation;
import com.aquariverr.fluxod.miniature.storage.FactoryStorage;
import mekanism.api.Action;
import mekanism.api.chemical.ChemicalStack;
import mekanism.api.chemical.IChemicalHandler;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.capabilities.ItemCapability;
import net.neoforged.neoforge.capabilities.BlockCapability;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.minecraft.world.level.block.entity.BlockEntityType;

import java.math.BigInteger;
import java.util.List;

final class MekanismMiniatureChemicalHandler implements IChemicalHandler {
    private static final ItemCapability<IChemicalHandler, Void> ITEM_CAPABILITY = ItemCapability.createVoid(
            ResourceLocation.fromNamespaceAndPath("mekanism", "chemical_handler"), IChemicalHandler.class);
    private static final BlockCapability<IChemicalHandler, Direction> BLOCK_CAPABILITY = BlockCapability.createSided(
            ResourceLocation.fromNamespaceAndPath("mekanism", "chemical_handler"), IChemicalHandler.class);
    private static final int INPUT_TANKS = FactoryLimits.MAX_BOUNDARY_RESOURCES;
    private final TileMiniatureUniverse owner;

    MekanismMiniatureChemicalHandler(TileMiniatureUniverse owner) { this.owner = owner; }

    static void registerCapability(RegisterCapabilitiesEvent event, BlockEntityType<TileMiniatureUniverse> type) {
        event.registerBlockEntity(BLOCK_CAPABILITY, type,
                (be, side) -> be.isRemoved() ? null : (IChemicalHandler) be.chemicalHandler());
    }

    @Override public int getChemicalTanks() { return INPUT_TANKS * 2; }
    @Override public long getChemicalTankCapacity(int tank) {
        if (!valid(tank)) return 0;
        FactoryStorage storage = FactoryAutomation.storage(owner);
        if (storage == null) return 0;
        ResourceKey key = resource(storage, tank);
        return tank < INPUT_TANKS ? (key == null ? storage.inputLimit() : storage.inputLimit(key))
                .min(BigInteger.valueOf(Long.MAX_VALUE)).longValueExact() : Long.MAX_VALUE;
    }

    @Override public ChemicalStack getChemicalInTank(int tank) {
        FactoryStorage storage = FactoryAutomation.storage(owner);
        ResourceKey key = storage == null ? null : resource(storage, tank);
        if (key == null) return ChemicalStack.EMPTY;
        try {
            ChemicalStack stack = chemicalOf(key, owner.getLevel().registryAccess());
            BigInteger amount = (tank < INPUT_TANKS ? storage.inputs() : storage.outputs()).amount(key);
            return stack.copyWithAmount(amount.min(BigInteger.valueOf(Long.MAX_VALUE)).longValueExact());
        } catch (IllegalArgumentException | IllegalStateException exception) { return ChemicalStack.EMPTY; }
    }

    @Override public void setChemicalInTank(int tank, ChemicalStack stack) {}

    @Override public boolean isValid(int tank, ChemicalStack stack) {
        FactoryStorage storage = FactoryAutomation.storage(owner);
        if (storage == null || tank < 0 || tank >= INPUT_TANKS || stack == null || stack.isEmpty()) return false;
        ResourceKey present = resource(storage, tank);
        ResourceKey offered = keyOf(stack);
        return (present == null || present.equals(offered)) && FactoryAutomation.accepts(owner, offered);
    }

    @Override public ChemicalStack insertChemical(int tank, ChemicalStack stack, Action action) {
        if (!isValid(tank, stack)) return stack == null ? ChemicalStack.EMPTY : stack.copy();
        return insertChemical(stack, action);
    }

    @Override public ChemicalStack insertChemical(ChemicalStack stack, Action action) {
        if (stack == null || stack.isEmpty()) return ChemicalStack.EMPTY;
        FactoryStorage storage = FactoryAutomation.storage(owner);
        if (storage == null) return stack.copy();
        try {
            ResourceKey key = keyOf(stack);
            if (!FactoryAutomation.accepts(owner, key)) return stack.copy();
            // All tank views share this resource's inventory limit. The default multi-tank insertion
            // would count the same remaining capacity repeatedly while simulating empty tank views.
            BigInteger available = storage.inputCapacity(key);
            long accepted = available.min(BigInteger.valueOf(stack.getAmount())).longValueExact();
            if (accepted <= 0) return stack.copy();
            owner.insertChemical(key, accepted, action.simulate());
            return stack.copyWithAmount(stack.getAmount() - accepted);
        } catch (IllegalArgumentException | IllegalStateException exception) { return stack.copy(); }
    }

    @Override public ChemicalStack extractChemical(int tank, long amount, Action action) {
        if (!valid(tank) || tank < INPUT_TANKS || amount <= 0) return ChemicalStack.EMPTY;
        FactoryStorage storage = FactoryAutomation.storage(owner);
        ResourceKey key = storage == null ? null : resource(storage, tank);
        if (key == null) return ChemicalStack.EMPTY;
        try {
            BigInteger extracted = storage.outputs().amount(key).min(BigInteger.valueOf(amount));
            if (extracted.signum() == 0) return ChemicalStack.EMPTY;
            ChemicalStack result = chemicalOf(key, owner.getLevel().registryAccess()).copyWithAmount(extracted.longValueExact());
            owner.extractChemical(key, extracted.longValueExact(), action.simulate());
            return result;
        } catch (IllegalArgumentException | IllegalStateException exception) { return ChemicalStack.EMPTY; }
    }

    private ResourceKey resource(FactoryStorage storage, int tank) {
        if (!valid(tank)) return null;
        List<ResourceKey> keys = (tank < INPUT_TANKS ? storage.inputs() : storage.outputs()).amounts().keySet()
                .stream().filter(key -> key.kind() == ResourceKind.CHEMICAL).toList();
        int index = tank % INPUT_TANKS;
        return index < keys.size() ? keys.get(index) : null;
    }

    private boolean valid(int tank) { return tank >= 0 && tank < getChemicalTanks(); }

    static ResourceKey keyOf(ChemicalStack stack) {
        if (stack == null || stack.isEmpty()) throw new IllegalArgumentException("Empty chemical stack");
        return new ResourceKey(ResourceKind.CHEMICAL, stack.getChemicalHolder().getRegisteredName());
    }

    static ResourceKey keyOfHeld(ItemStack held) {
        IChemicalHandler handler = ITEM_CAPABILITY.getCapability(held, null);
        if (handler == null) return null;
        for (int tank = 0; tank < handler.getChemicalTanks(); tank++) {
            ChemicalStack stack = handler.getChemicalInTank(tank);
            if (!stack.isEmpty()) return keyOf(stack);
        }
        return null;
    }

    static ChemicalStack chemicalOf(ResourceKey key, HolderLookup.Provider registries) {
        if (key.kind() != ResourceKind.CHEMICAL || !key.components().isEmpty()) {
            throw new IllegalArgumentException("Chemical identities cannot contain data components");
        }
        CompoundTag tag = new CompoundTag();
        tag.putString("id", key.registryId());
        tag.putLong("amount", 1L);
        return ChemicalStack.parse(registries, tag).orElseThrow(() -> new IllegalArgumentException("Unknown chemical"));
    }
}
