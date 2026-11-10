package com.aquariverr.fluxod.integration.mekanism;

import com.aquariverr.fluxod.FluxOverdrive;
import com.aquariverr.fluxod.block.entity.QuantumBatteryCoolingModel;
import com.aquariverr.fluxod.block.entity.QuantumCoolingMedia;
import com.aquariverr.fluxod.block.entity.TileQuantumAdvancedCoolingPlugin;
import mekanism.api.Action;
import mekanism.api.AutomationType;
import mekanism.api.MekanismAPI;
import mekanism.api.chemical.BasicChemicalTank;
import mekanism.api.chemical.Chemical;
import mekanism.api.chemical.ChemicalStack;
import mekanism.api.chemical.IChemicalHandler;
import mekanism.api.chemical.IChemicalTank;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.neoforge.capabilities.BlockCapability;
import net.neoforged.neoforge.capabilities.ItemCapability;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;

final class MekanismChemicalTankAdapter implements OptionalChemicalTank {

    private static final ResourceLocation CAPABILITY_ID = ResourceLocation.fromNamespaceAndPath(
            "mekanism", "chemical_handler");
    private static final BlockCapability<IChemicalHandler, Direction> BLOCK_CAPABILITY =
            BlockCapability.createSided(CAPABILITY_ID, IChemicalHandler.class);
    private static final ItemCapability<IChemicalHandler, Void> ITEM_CAPABILITY =
            ItemCapability.createVoid(CAPABILITY_ID, IChemicalHandler.class);
    private static final TagKey<Chemical> CHEMICALS_273_K = chemicalTag("quantum_coolants_273k");
    private static final TagKey<Chemical> CHEMICALS_263_K = chemicalTag("quantum_coolants_263k");
    private static final TagKey<Chemical> CHEMICALS_253_K = chemicalTag("quantum_coolants_253k");

    private final IChemicalTank tank;

    MekanismChemicalTankAdapter(long capacity, Runnable contentsChanged) {
        tank = BasicChemicalTank.inputModern(Math.max(1L, capacity),
                stack -> targetFor(stack) != QuantumBatteryCoolingModel.NO_TARGET,
                contentsChanged::run);
    }

    static void registerCapability(
            RegisterCapabilitiesEvent event,
            BlockEntityType<TileQuantumAdvancedCoolingPlugin> blockEntityType) {
        event.registerBlockEntity(BLOCK_CAPABILITY, blockEntityType,
                (blockEntity, side) -> blockEntity.isRemoved()
                        ? null
                        : (IChemicalHandler) blockEntity.getChemicalTank().getCapability());
    }

    @Override
    public boolean isAvailable() {
        return true;
    }

    @Override
    public long getAmount() {
        return tank.getStored();
    }

    @Override
    public long getCapacity() {
        return tank.getCapacity();
    }

    @Override
    public long getTargetMilliKelvin() {
        return targetFor(tank.getStack());
    }

    @Override
    public String getChemicalId() {
        ChemicalStack stack = tank.getStack();
        return stack.isEmpty() ? "" : stack.getChemicalHolder().getRegisteredName();
    }

    @Override
    public int getTint() {
        ChemicalStack stack = tank.getStack();
        return stack.isEmpty() ? 0xFF7F7F7F : 0xFF000000 | stack.getChemicalTint();
    }

    @Override
    public long extract(long amount) {
        if (amount <= 0L) return 0L;
        return tank.extract(amount, Action.EXECUTE, AutomationType.INTERNAL).getAmount();
    }

    @Override
    public boolean interactWithHeldItem(Player player, InteractionHand hand) {
        ItemStack held = player.getItemInHand(hand);
        IChemicalHandler itemHandler = ITEM_CAPABILITY.getCapability(held, null);
        if (itemHandler == null) return false;

        if (transferFromItem(itemHandler)) return true;
        return transferToItem(itemHandler);
    }

    private boolean transferFromItem(IChemicalHandler itemHandler) {
        for (int itemTank = 0; itemTank < itemHandler.getChemicalTanks(); itemTank++) {
            ChemicalStack available = itemHandler.getChemicalInTank(itemTank);
            if (available.isEmpty() || !tank.isValid(available)) continue;
            ChemicalStack remainder = tank.insert(available, Action.SIMULATE, AutomationType.MANUAL);
            long amount = available.getAmount() - remainder.getAmount();
            if (amount <= 0L) continue;
            ChemicalStack extracted = itemHandler.extractChemical(itemTank, amount, Action.EXECUTE);
            if (extracted.isEmpty()) continue;
            tank.insert(extracted, Action.EXECUTE, AutomationType.MANUAL);
            return true;
        }
        return false;
    }

    private boolean transferToItem(IChemicalHandler itemHandler) {
        ChemicalStack stored = tank.getStack();
        if (stored.isEmpty()) return false;
        ChemicalStack remainder = itemHandler.insertChemical(stored, Action.SIMULATE);
        long amount = stored.getAmount() - remainder.getAmount();
        if (amount <= 0L) return false;
        ChemicalStack extracted = tank.extract(amount, Action.EXECUTE, AutomationType.MANUAL);
        if (extracted.isEmpty()) return false;
        itemHandler.insertChemical(extracted, Action.EXECUTE);
        return true;
    }

    @Override
    public CompoundTag serializeNBT(HolderLookup.Provider registries) {
        return tank.serializeNBT(registries);
    }

    @Override
    public void deserializeNBT(HolderLookup.Provider registries, CompoundTag tag) {
        tank.deserializeNBT(registries, tag);
    }

    @Override
    public Object getCapability() {
        return tank;
    }

    private static long targetFor(ChemicalStack stack) {
        if (stack.isEmpty()) return QuantumBatteryCoolingModel.NO_TARGET;
        if (stack.is(CHEMICALS_253_K)) return QuantumCoolingMedia.TARGET_253_K;
        if (stack.is(CHEMICALS_263_K)) return QuantumCoolingMedia.TARGET_263_K;
        if (stack.is(CHEMICALS_273_K)) return QuantumCoolingMedia.TARGET_273_K;
        return QuantumBatteryCoolingModel.NO_TARGET;
    }

    private static TagKey<Chemical> chemicalTag(String path) {
        return TagKey.create(MekanismAPI.CHEMICAL_REGISTRY_NAME, FluxOverdrive.location(path));
    }
}
