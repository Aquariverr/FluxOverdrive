package com.aquariverr.fluxod.integration.mekanism;

import com.aquariverr.fluxod.gametest.MiniatureFluidCapabilityGameTests;
import com.aquariverr.fluxod.block.entity.TileMiniatureUniverse;
import com.aquariverr.fluxod.miniature.model.FactoryLimits;
import com.aquariverr.fluxod.miniature.model.ResourceKey;
import com.aquariverr.fluxod.miniature.model.ResourceKind;
import com.aquariverr.fluxod.miniature.recipe.MiniatureRecipeCatalog;
import com.aquariverr.fluxod.miniature.storage.*;
import mekanism.api.Action;
import mekanism.api.chemical.IChemicalHandler;
import mekanism.api.chemical.ChemicalStack;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.capabilities.BlockCapability;

import java.math.BigInteger;
import java.util.LinkedHashMap;
import java.util.Map;

/** Invoked only by the mod-independent GameTest entry point after checking ModList. */
public final class MiniatureChemicalVerification {
    public static void localCapacity(GameTestHelper h) {
        var tile = MiniatureFluidCapabilityGameTests.fixture(h, FactoryStorage.empty().withInputCapacity(OXYGEN, BigInteger.TEN)
                .withInputCapacity(HYDROGEN, BigInteger.valueOf(20))
                .withInputs(new ResourceInventory(Map.of(OXYGEN, BigInteger.valueOf(8)))));
        MiniatureFluidCapabilityGameTests.allowInput(h, tile, OXYGEN);
        var handler = capability(h, tile);
        var offered = MekanismMiniatureChemicalHandler.chemicalOf(OXYGEN, h.getLevel().registryAccess()).copyWithAmount(5);
        var before = tile.factoryState();
        h.assertTrue(handler.getChemicalTankCapacity(0) == 10 && handler.insertChemical(offered, Action.SIMULATE).getAmount() == 3
                && before == tile.factoryState(), "Chemical simulation ignored local capacity");
        h.assertTrue(handler.insertChemical(0, offered, Action.EXECUTE).getAmount() == 3, "Chemical execution ignored local capacity");
        before = tile.factoryState();
        h.assertTrue(tile.applyPreparedState(before, FactoryState.of(before.storage().orElseThrow().withInputCapacity(OXYGEN, BigInteger.ONE), h.getLevel().registryAccess()))
                && handler.insertChemical(offered, Action.EXECUTE).getAmount() == 5
                && tile.factoryState().storage().orElseThrow().inputs().amount(OXYGEN).equals(BigInteger.TEN), "Lowering local capacity changed chemical assets");
        MiniatureFluidCapabilityGameTests.allowInput(h, tile, HYDROGEN);
        var hydrogen = MekanismMiniatureChemicalHandler.chemicalOf(HYDROGEN, h.getLevel().registryAccess()).copyWithAmount(25);
        h.assertTrue(handler.insertChemical(hydrogen, Action.EXECUTE).getAmount() == 5
                && tile.factoryState().storage().orElseThrow().inputs().amount(HYDROGEN).equals(BigInteger.valueOf(20)),
                "Changing oxygen capacity affected hydrogen");
    }

    public static void configuredCapacity(GameTestHelper h) {
        var previous = com.aquariverr.fluxod.Config.miniatureInputCapacity;
        try {
            com.aquariverr.fluxod.Config.miniatureInputCapacity = BigInteger.valueOf(1000);
            var tile = MiniatureFluidCapabilityGameTests.fixture(h, FactoryStorage.empty()
                    .withInputs(new ResourceInventory(Map.of(OXYGEN, BigInteger.valueOf(997)))));
            MiniatureFluidCapabilityGameTests.allowInput(h, tile, OXYGEN);
            var handler = capability(h, tile);
            var offered = MekanismMiniatureChemicalHandler.chemicalOf(OXYGEN, h.getLevel().registryAccess()).copyWithAmount(1000);
            var before = tile.factoryState();
            h.assertTrue(handler.getChemicalTankCapacity(0) == 1000 && handler.insertChemical(offered, Action.SIMULATE).getAmount() == 997
                    && handler.insertChemical(0, offered, Action.SIMULATE).getAmount() == 997 && before == tile.factoryState(), "Chemical simulation ignored capacity");
            h.assertTrue(handler.insertChemical(offered, Action.EXECUTE).getAmount() == 997, "Chemical execution disagrees with simulation");
            com.aquariverr.fluxod.Config.miniatureInputCapacity = BigInteger.ONE;
            h.assertTrue(handler.insertChemical(offered, Action.EXECUTE).getAmount() == 1000
                    && tile.factoryState().storage().orElseThrow().inputs().amount(OXYGEN).equals(BigInteger.valueOf(1000)), "Lowered chemical limit changed assets");
        } finally { com.aquariverr.fluxod.Config.miniatureInputCapacity = previous; }
    }

    private static final int FIRST_OUTPUT_TANK = FactoryLimits.MAX_BOUNDARY_RESOURCES;
    private static final ResourceKey OXYGEN = new ResourceKey(ResourceKind.CHEMICAL, "mekanism:oxygen");
    private static final ResourceKey HYDROGEN = new ResourceKey(ResourceKind.CHEMICAL, "mekanism:hydrogen");
    private static final BlockCapability<IChemicalHandler, Direction> CAPABILITY = BlockCapability.createSided(
            ResourceLocation.fromNamespaceAndPath("mekanism", "chemical_handler"), IChemicalHandler.class);

    public static void catalystReservation(GameTestHelper helper) {
        var storage = com.aquariverr.fluxod.gametest.MiniatureCatalystGameTests.startedWithCatalyst(helper, OXYGEN, 1000,
                FactoryLimits.MAX_INVENTORY_AMOUNT.subtract(BigInteger.valueOf(7)));
        var tile = MiniatureFluidCapabilityGameTests.fixture(helper, storage);
        var handler = capability(helper, tile);
        var oxygen = MekanismMiniatureChemicalHandler.chemicalOf(OXYGEN, helper.getLevel().registryAccess());
        var before = tile.factoryState();
        helper.assertTrue(handler.insertChemical(oxygen.copyWithAmount(9), Action.SIMULATE).getAmount() == 2
                && handler.extractChemical(oxygen.copyWithAmount(Long.MAX_VALUE), Action.EXECUTE).isEmpty()
                && before == tile.factoryState(), "Chemical simulation changed state or exposed locked catalysts");
        helper.assertTrue(handler.insertChemical(oxygen.copyWithAmount(9), Action.EXECUTE).getAmount() == 2
                && handler.insertChemical(oxygen, Action.EXECUTE).getAmount() == 1, "Chemical input occupied catalyst return space");
        helper.assertTrue(com.aquariverr.fluxod.gametest.MiniatureCatalystGameTests.finish(tile.factoryState().storage().orElseThrow())
                .inputs().amount(OXYGEN).equals(FactoryLimits.MAX_INVENTORY_AMOUNT), "Chemical catalyst return lost units above long range");
    }

    public static void transfers(GameTestHelper helper) {
        var key = OXYGEN;
        ChemicalStack oxygen = MekanismMiniatureChemicalHandler.chemicalOf(key, helper.getLevel().registryAccess());
        BigInteger total = BigInteger.valueOf(Long.MAX_VALUE).multiply(BigInteger.TWO).add(BigInteger.valueOf(9));
        var tile = MiniatureFluidCapabilityGameTests.fixture(helper, FactoryStorage.empty()
                .withOutputs(new ResourceInventory(Map.of(key, total))));
        MiniatureFluidCapabilityGameTests.allowInput(helper, tile, key);
        IChemicalHandler handler = helper.getLevel().getCapability(CAPABILITY, tile.getBlockPos(), Direction.NORTH);
        helper.assertTrue(handler != null, "Factory is not exposed through Mekanism's native chemical capability");
        CompoundTag before = tile.factoryState().save();
        helper.assertTrue(handler.insertChemical(0, oxygen.copyWithAmount(Long.MAX_VALUE), Action.SIMULATE).isEmpty()
                && handler.insertChemical(oxygen.copyWithAmount(Long.MAX_VALUE), Action.SIMULATE).isEmpty()
                && handler.extractChemical(FIRST_OUTPUT_TANK, Long.MAX_VALUE, Action.SIMULATE).getAmount() == Long.MAX_VALUE
                && handler.extractChemical(Long.MAX_VALUE, Action.SIMULATE).getAmount() == Long.MAX_VALUE
                && before.equals(tile.factoryState().save()), "Chemical simulation changed state or clamped incorrectly");
        helper.assertTrue(handler.insertChemical(0, oxygen.copyWithAmount(Long.MAX_VALUE), Action.EXECUTE).isEmpty()
                && handler.insertChemical(oxygen.copyWithAmount(Long.MAX_VALUE), Action.EXECUTE).isEmpty(), "Long input bridge failed");
        helper.assertTrue(tile.factoryState().storage().orElseThrow().inputs().amount(key)
                .equals(BigInteger.valueOf(Long.MAX_VALUE).multiply(BigInteger.TWO)), "Chemical input overflow");
        helper.assertTrue(handler.extractChemical(0, Long.MAX_VALUE, Action.EXECUTE).isEmpty(), "Automation extracted chemical inputs");
        helper.assertTrue(handler.extractChemical(FIRST_OUTPUT_TANK, Long.MAX_VALUE, Action.EXECUTE).getAmount() == Long.MAX_VALUE
                && handler.extractChemical(Long.MAX_VALUE, Action.EXECUTE).getAmount() == Long.MAX_VALUE
                && handler.extractChemical(oxygen.copyWithAmount(Long.MAX_VALUE), Action.EXECUTE).getAmount() == 9
                && tile.factoryState().storage().orElseThrow().outputs().amount(key).signum() == 0,
                "Repeated chemical extraction lost or duplicated units");
    }

    public static void boundaries(GameTestHelper helper) {
        var key = OXYGEN;
        ChemicalStack oxygen = MekanismMiniatureChemicalHandler.chemicalOf(key, helper.getLevel().registryAccess());
        var tile = MiniatureFluidCapabilityGameTests.fixture(helper, FactoryStorage.empty().withInputs(new ResourceInventory(
                Map.of(key, FactoryLimits.MAX_INVENTORY_AMOUNT.subtract(BigInteger.valueOf(7))))));
        IChemicalHandler handler = helper.getLevel().getCapability(CAPABILITY, tile.getBlockPos(), Direction.SOUTH);
        helper.assertTrue(handler != null && handler.insertChemical(0, oxygen.copyWithAmount(8), Action.SIMULATE).getAmount() == 8,
                "Uncompiled factory accepted chemicals");
        MiniatureFluidCapabilityGameTests.allowInput(helper, tile, key);
        CompoundTag before = tile.factoryState().save();
        helper.assertTrue(handler.insertChemical(0, oxygen.copyWithAmount(8), Action.SIMULATE).getAmount() == 1
                && handler.insertChemical(oxygen.copyWithAmount(8), Action.SIMULATE).getAmount() == 1
                && before.equals(tile.factoryState().save()), "Capacity simulation inaccurate or mutating");
        helper.assertTrue(handler.insertChemical(oxygen.copyWithAmount(8), Action.EXECUTE).getAmount() == 1
                && handler.insertChemical(0, oxygen, Action.EXECUTE).getAmount() == 1, "Chemical hard limit exceeded");
        helper.assertTrue(tile.factoryState().storage().orElseThrow().inputs().amount(key).equals(FactoryLimits.MAX_INVENTORY_AMOUNT),
                "Chemical capacity transfer did not commit the exact accepted amount");
        helper.assertTrue(handler.insertChemical(FIRST_OUTPUT_TANK, oxygen, Action.EXECUTE).getAmount() == 1
                && handler.insertChemical(-1, oxygen, Action.EXECUTE).getAmount() == 1, "Output/invalid tank accepted input");
        tile.setRemoved();
        helper.assertTrue(handler.getChemicalInTank(0).isEmpty() && !handler.isValid(0, oxygen)
                && handler.extractChemical(FIRST_OUTPUT_TANK, Long.MAX_VALUE, Action.EXECUTE).isEmpty()
                && handler.insertChemical(0, oxygen, Action.EXECUTE).getAmount() == 1
                && handler.insertChemical(oxygen, Action.SIMULATE).getAmount() == 1, "Stale handler accessed assets");
    }

    public static void persistedTransfers(GameTestHelper helper) {
        ChemicalStack oxygen = MekanismMiniatureChemicalHandler.chemicalOf(OXYGEN, helper.getLevel().registryAccess());
        ChemicalStack hydrogen = MekanismMiniatureChemicalHandler.chemicalOf(HYDROGEN, helper.getLevel().registryAccess());
        BigInteger total = BigInteger.valueOf(Long.MAX_VALUE).multiply(BigInteger.TWO).add(BigInteger.valueOf(9));
        var tile = MiniatureFluidCapabilityGameTests.fixture(helper, FactoryStorage.empty()
                .withInputs(new ResourceInventory(Map.of(OXYGEN, total)))
                .withOutputs(new ResourceInventory(Map.of(OXYGEN, total, HYDROGEN, BigInteger.valueOf(11)))));
        IChemicalHandler handler = capability(helper, tile);
        helper.assertTrue(handler.getChemicalInTank(0).getAmount() == Long.MAX_VALUE
                && handler.getChemicalTankCapacity(0) == Long.MAX_VALUE,
                "A chemical tank view overflowed the native long bridge");
        helper.assertTrue(handler.extractChemical(oxygen.copyWithAmount(Long.MAX_VALUE), Action.EXECUTE).getAmount() == Long.MAX_VALUE,
                "Typed chemical extraction did not select the exact output");
        CompoundTag saved = tile.factoryState().save();
        CompoundTag wrapped = new CompoundTag();
        wrapped.put(TileMiniatureUniverse.STATE_TAG, saved);
        tile.loadAdditional(wrapped, helper.getLevel().registryAccess());
        handler = capability(helper, tile);
        helper.assertTrue(saved.equals(tile.factoryState().save())
                && tile.factoryState().storage().orElseThrow().outputs().amount(OXYGEN)
                .equals(BigInteger.valueOf(Long.MAX_VALUE).add(BigInteger.valueOf(9))),
                "Saving between long-sized transfers truncated chemical output");
        helper.assertTrue(handler.extractChemical(hydrogen.copyWithAmount(Long.MAX_VALUE), Action.SIMULATE).getAmount() == 11
                && handler.extractChemical(oxygen.copyWithAmount(Long.MAX_VALUE), Action.SIMULATE).getAmount() == Long.MAX_VALUE
                && saved.equals(tile.factoryState().save()), "Typed simulation mutated or mixed reloaded chemical identities");
        helper.assertTrue(handler.extractChemical(oxygen.copyWithAmount(Long.MAX_VALUE), Action.EXECUTE).getAmount() == Long.MAX_VALUE
                && handler.extractChemical(oxygen.copyWithAmount(Long.MAX_VALUE), Action.EXECUTE).getAmount() == 9
                && handler.extractChemical(oxygen, Action.EXECUTE).isEmpty()
                && tile.factoryState().storage().orElseThrow().outputs().amount(HYDROGEN).equals(BigInteger.valueOf(11)),
                "Typed extraction lost the long remainder or extracted a different chemical");
        ChemicalStack remaining = handler.extractChemical(Long.MAX_VALUE, Action.EXECUTE);
        helper.assertTrue(MekanismMiniatureChemicalHandler.keyOf(remaining).equals(HYDROGEN) && remaining.getAmount() == 11
                && tile.factoryState().storage().orElseThrow().outputs().amounts().isEmpty()
                && tile.factoryState().storage().orElseThrow().inputs().amount(OXYGEN).equals(total),
                "Compacted output tank views lost chemicals or exposed input inventory");
    }

    public static void resourceBoundaries(GameTestHelper helper) {
        ChemicalStack oxygen = MekanismMiniatureChemicalHandler.chemicalOf(OXYGEN, helper.getLevel().registryAccess());
        ChemicalStack hydrogen = MekanismMiniatureChemicalHandler.chemicalOf(HYDROGEN, helper.getLevel().registryAccess());
        var full = new LinkedHashMap<ResourceKey, BigInteger>();
        helper.getLevel().registryAccess().lookupOrThrow(Registries.ITEM).listElements()
                .filter(holder -> holder.value() != Items.AIR)
                .limit(FactoryLimits.MAX_BOUNDARY_RESOURCES)
                .forEach(holder -> full.put(new ResourceKey(ResourceKind.ITEM, holder.key().location().toString()), BigInteger.ONE));
        helper.assertTrue(full.size() == FactoryLimits.MAX_BOUNDARY_RESOURCES, "Chemical resource table fixture is not full");
        var tile = MiniatureFluidCapabilityGameTests.fixture(helper, FactoryStorage.empty().withInputs(new ResourceInventory(full)));
        MiniatureFluidCapabilityGameTests.allowInput(helper, tile, OXYGEN);
        IChemicalHandler handler = capability(helper, tile);
        CompoundTag before = tile.factoryState().save();
        helper.assertTrue(handler.insertChemical(oxygen, Action.SIMULATE).getAmount() == 1
                && handler.insertChemical(oxygen, Action.EXECUTE).getAmount() == 1
                && before.equals(tile.factoryState().save()), "Full mixed resource table reported a phantom chemical transfer");

        tile = MiniatureFluidCapabilityGameTests.fixture(helper, FactoryStorage.empty());
        MiniatureFluidCapabilityGameTests.allowInput(helper, tile, OXYGEN);
        handler = capability(helper, tile);
        before = tile.factoryState().save();
        helper.assertTrue(handler.insertChemical(hydrogen, Action.SIMULATE).getAmount() == 1
                && handler.insertChemical(hydrogen, Action.EXECUTE).getAmount() == 1
                && before.equals(tile.factoryState().save()), "A different chemical satisfied the compiled input contract");
        helper.assertTrue(handler.insertChemical(oxygen, Action.EXECUTE).isEmpty(), "Compiled chemical input was rejected");
        MiniatureRecipeCatalog.get(helper.getLevel().getServer()).invalidate();
        before = tile.factoryState().save();
        helper.assertTrue(handler.insertChemical(oxygen, Action.SIMULATE).getAmount() == 1
                && handler.insertChemical(oxygen, Action.EXECUTE).getAmount() == 1
                && before.equals(tile.factoryState().save()), "Recipe invalidation retained stale chemical automation input");
    }

    private static IChemicalHandler capability(GameTestHelper helper, TileMiniatureUniverse tile) {
        IChemicalHandler handler = helper.getLevel().getCapability(CAPABILITY, tile.getBlockPos(), Direction.NORTH);
        helper.assertTrue(handler != null, "Factory is not exposed through Mekanism's native chemical capability");
        return handler;
    }

    private MiniatureChemicalVerification() {}
}
