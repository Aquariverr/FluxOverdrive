package com.aquariverr.fluxod.gametest;

import com.aquariverr.fluxod.FluxOverdrive;
import com.aquariverr.fluxod.block.entity.TileMiniatureUniverse;
import com.aquariverr.fluxod.integration.mekanism.QuantumCoolingChemicalIntegration;
import com.aquariverr.fluxod.miniature.model.*;
import com.aquariverr.fluxod.miniature.runtime.RunningBatch;
import com.aquariverr.fluxod.miniature.storage.*;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.math.BigInteger;
import java.nio.file.Path;
import java.nio.file.Files;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** This entry point must remain loadable when no Mekanism classes exist in the server JVM. */
@GameTestHolder(FluxOverdrive.MODID)
@PrefixGameTestTemplate(false)
public final class MiniatureChemicalCapabilityGameTests {
    @GameTest(template = "empty")
    public static void nativeChemicalCapabilityTransfersBeyondLongAndPreservesSimulation(GameTestHelper helper) {
        if (QuantumCoolingChemicalIntegration.isAvailable()) {
            com.aquariverr.fluxod.integration.mekanism.MiniatureChemicalVerification.transfers(helper);
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void chemicalCapabilityRejectsUnavailableFullAndUncompiledFactories(GameTestHelper helper) {
        if (QuantumCoolingChemicalIntegration.isAvailable()) {
            com.aquariverr.fluxod.integration.mekanism.MiniatureChemicalVerification.boundaries(helper);
        } else {
            var tile = MiniatureFluidCapabilityGameTests.fixture(helper, FactoryStorage.empty());
            helper.assertTrue(tile.chemicalHandler() == null, "Missing mod created a chemical handler");
            helper.assertTrue(QuantumCoolingChemicalIntegration.chemicalKeyOf(new ItemStack(Items.WATER_BUCKET)) == null,
                    "Missing mod marker path loaded chemical classes");
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void chemicalTransfersRetainExactIdentityAcrossSaveReload(GameTestHelper helper) {
        if (QuantumCoolingChemicalIntegration.isAvailable()) {
            com.aquariverr.fluxod.integration.mekanism.MiniatureChemicalVerification.persistedTransfers(helper);
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void chemicalFullResourceTableAndRecipeInvalidationRejectTransfers(GameTestHelper helper) {
        if (QuantumCoolingChemicalIntegration.isAvailable()) {
            com.aquariverr.fluxod.integration.mekanism.MiniatureChemicalVerification.resourceBoundaries(helper);
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void optionalDependencyMatrixPreservesInventoryMarkersBindingsAndBatch(GameTestHelper helper) throws Exception {
        boolean available = QuantumCoolingChemicalIntegration.isAvailable();
        helper.assertTrue(available != Boolean.getBoolean("flux_overdrive.miniatureWithoutMekanism"),
                "The launched JVM did not use the requested optional dependency configuration");
        var chemical = new ResourceKey(ResourceKind.CHEMICAL, "mekanism:oxygen");
        var inventory = new ResourceInventory(Map.of(chemical, BigInteger.valueOf(Long.MAX_VALUE).add(BigInteger.ONE)));
        FactoryStorage machine = FactoryStorage.empty().insertMachine(new GridPosition(1, 1), new ItemStack(Items.FURNACE),
                helper.getLevel().registryAccess()).storage();
        var batch = new RunningBatch(UUID.fromString("e0767abe-0d28-42ed-bc65-7d31cc7f85ba"), "0".repeat(64),
                FactoryRules.environment(0, 0), 1, ResourceInventory.empty(), inventory,
                FactoryRules.energyPerTick(1), 7);
        List<FactoryStorage> states = List.of(FactoryStorage.empty().withInputs(inventory),
                FactoryStorage.empty().withOutputs(inventory),
                FactoryStorage.empty().withConfiguration(new GridPosition(0, 0), new FactoryCell.Marker(chemical)),
                machine.withRecipe(new GridPosition(1, 1), new RecipeReference("flux_overdrive:smelting", "minecraft:test", 1, 1,
                        List.of(new RecipeReference.Binding(0, chemical)))), machine.withBatch(batch, 10),
                machine.withBatch(new RunningBatch(batch.id(), batch.fingerprint(), batch.environment(), 1,
                        ResourceInventory.empty(), new ResourceInventory(Map.of(new ResourceKey(ResourceKind.ITEM, "minecraft:iron_ingot"), BigInteger.ONE)),
                        inventory, batch.energyPerTick(), 7), 10));
        CompoundTag fixture = new CompoundTag();
        for (int i = 0; i < states.size(); i++) fixture.put("state" + i, FactoryState.of(states.get(i), helper.getLevel().registryAccess()).save());
        Path savedPath = Path.of("..", "s08-optional-chemical-states.nbt");
        Path roundTripPath = Path.of("..", "s08-optional-chemical-states-without-mekanism.nbt");
        boolean restoring = System.getProperty("flux_overdrive.miniaturePersistencePhase", "write").equals("read");
        if (available && !restoring) {
            NbtIo.writeCompressed(fixture, savedPath);
        } else {
            helper.assertTrue(Files.isRegularFile(savedPath), "Missing fixture from the prior independent Mekanism JVM");
            fixture = NbtIo.readCompressed(savedPath, NbtAccounter.unlimitedHeap());
            if (available) {
                helper.assertTrue(Files.isRegularFile(roundTripPath), "Missing fixture re-saved by the independent JVM without Mekanism");
                CompoundTag roundTrip = NbtIo.readCompressed(roundTripPath, NbtAccounter.unlimitedHeap());
                helper.assertTrue(fixture.equals(roundTrip), "The JVM without Mekanism changed the persisted chemical states");
                fixture = roundTrip;
            }
        }
        CompoundTag resaved = new CompoundTag();
        for (String name : fixture.getAllKeys()) {
            CompoundTag original = fixture.getCompound(name);
            var tile = MiniatureFluidCapabilityGameTests.fixture(helper, FactoryStorage.empty());
            CompoundTag wrapped = new CompoundTag();
            wrapped.put(TileMiniatureUniverse.STATE_TAG, original);
            tile.loadAdditional(wrapped, helper.getLevel().registryAccess());
            helper.assertTrue(tile.factoryState().storage().isPresent() == available && original.equals(tile.factoryState().save()),
                    "Optional-mod round trip changed or incorrectly enabled " + name);
            resaved.put(name, tile.factoryState().save());
        }
        if (!available) NbtIo.writeCompressed(resaved, roundTripPath);
        helper.succeed();
    }

    private MiniatureChemicalCapabilityGameTests() {}
}
