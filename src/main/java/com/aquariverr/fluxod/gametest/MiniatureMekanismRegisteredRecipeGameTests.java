package com.aquariverr.fluxod.gametest;

import com.aquariverr.fluxod.FluxOverdrive;
import com.aquariverr.fluxod.miniature.model.FactoryRules;
import com.aquariverr.fluxod.miniature.compile.FactoryCompiler;
import com.aquariverr.fluxod.miniature.model.FactoryCell;
import com.aquariverr.fluxod.miniature.model.FactoryLayout;
import com.aquariverr.fluxod.miniature.model.GridDirection;
import com.aquariverr.fluxod.miniature.model.GridPosition;
import com.aquariverr.fluxod.miniature.model.ResourceAmount;
import com.aquariverr.fluxod.miniature.model.ResourceKey;
import com.aquariverr.fluxod.miniature.model.ResourceKind;
import com.aquariverr.fluxod.miniature.recipe.MiniatureRecipeCatalog;
import com.aquariverr.fluxod.miniature.recipe.ResolvedRecipe;
import com.aquariverr.fluxod.miniature.storage.FactoryItemResources;
import com.aquariverr.fluxod.miniature.storage.FactoryStorage;
import com.aquariverr.fluxod.miniature.storage.MachineInventory;
import com.aquariverr.fluxod.miniature.storage.ResourceInventory;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.math.BigInteger;
import java.util.List;

/** Exercises the recipes actually shipped by Mekanism, without replacing the server recipe manager. */
@GameTestHolder(FluxOverdrive.MODID)
@PrefixGameTestTemplate(false)
public final class MiniatureMekanismRegisteredRecipeGameTests {
    private static final GridPosition MACHINE = pos(1, 1);

    @GameTest(template = "empty", batch = "miniature_mekanism")
    public static void registeredOreEnrichmentBindsBothNativeTagVariants(GameTestHelper helper) {
        if (!available(helper)) return;
        for (String ore : List.of("iron_ore", "deepslate_iron_ore")) {
            checkLine(helper, "enrichment_chamber", "processing/iron/dust/from_ore", 1,
                    List.of(item(helper, "minecraft:" + ore, 1)), List.of(item(helper, "mekanism:dust_iron", 2)));
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "miniature_mekanism")
    public static void registeredCobblestoneCrushingUsesNativeTagAndExactOutput(GameTestHelper helper) {
        if (!available(helper)) return;
        checkLine(helper, "crusher", "crushing/cobblestone_to_gravel", 1,
                List.of(item(helper, "minecraft:cobblestone", 1)), List.of(item(helper, "minecraft:gravel", 1)));
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "miniature_mekanism")
    public static void registeredCentrifugingNormalizesOneChemicalToOneThousand(GameTestHelper helper) {
        if (!available(helper)) return;
        checkLine(helper, "isotopic_centrifuge", "processing/uranium/fissile_fuel", 1000,
                List.of(chemical("uranium_hexafluoride", 1)), List.of(chemical("fissile_fuel", 1)));
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "miniature_mekanism")
    public static void registeredChemicalInfusingPreservesBothBindings(GameTestHelper helper) {
        if (!available(helper)) return;
        var recipe = checkLine(helper, "chemical_infuser", "chemical_infusing/hydrogen_chloride", 1000,
                List.of(chemical("hydrogen", 1), chemical("chlorine", 1)), List.of(chemical("hydrogen_chloride", 1)));
        helper.assertTrue(recipe.reference().bindings().get(0).resource().equals(chemical("hydrogen", 1).resource())
                        && recipe.reference().bindings().get(1).resource().equals(chemical("chlorine", 1).resource()),
                "Native chemical ingredient slots were reordered");
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "miniature_mekanism")
    public static void registeredWaterSeparationNormalizesTheSmallerCoproduct(GameTestHelper helper) {
        if (!available(helper)) return;
        checkLine(helper, "electrolytic_separator", "separator/water", 1000,
                List.of(fluid("water", 2)), List.of(chemical("hydrogen", 2), chemical("oxygen", 1)));
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "miniature_mekanism")
    public static void registeredPerTickOreRecipesUseFullNativeConsumption(GameTestHelper helper) {
        if (!available(helper)) return;
        for (String machine : List.of("purification_chamber", "ultimate_purifying_factory")) {
            checkLine(helper, machine, "processing/iron/clump/from_ore", 1,
                    List.of(item(helper, "minecraft:iron_ore", 1), chemical("oxygen", 20)),
                    List.of(item(helper, "mekanism:clump_iron", 3)));
        }
        checkLine(helper, "chemical_injection_chamber", "processing/iron/shard/from_ore", 1,
                List.of(item(helper, "minecraft:iron_ore", 1), chemical("hydrogen_chloride", 20)),
                List.of(item(helper, "mekanism:shard_iron", 4)));
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "miniature_mekanism")
    public static void registeredHydrofluoricAcidAcceptsGemAndBlockWithFullSulfuricAcidBudget(GameTestHelper helper) {
        if (!available(helper)) return;
        checkLine(helper, "chemical_dissolution_chamber", "processing/uranium/hydrofluoric_acid", 1,
                List.of(item(helper, "mekanism:fluorite_gem", 1), chemical("sulfuric_acid", 10)),
                List.of(chemical("hydrofluoric_acid", 1000)));
        checkLine(helper, "chemical_dissolution_chamber", "processing/uranium/hydrofluoric_acid_from_block", 1,
                List.of(item(helper, "mekanism:block_fluorite", 1), chemical("sulfuric_acid", 90)),
                List.of(chemical("hydrofluoric_acid", 9000)));
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "miniature_mekanism", timeoutTicks = 60)
    public static void registeredFissionFuelLineUsesUpgradedDissolutionAndRunsForTwentyPaidTicks(GameTestHelper helper) {
        if (!available(helper)) return;
        var registries = helper.getLevel().registryAccess();
        FactoryStorage storage = FactoryStorage.restore(FactoryLayout.empty(5), MachineInventory.empty(),
                        ResourceInventory.empty(), ResourceInventory.empty(), registries)
                .insertMachine(pos(1, 0), machine("ultimate_enriching_factory", 20), registries).storage()
                .insertMachine(pos(2, 0), machine("chemical_oxidizer", 10), registries).storage()
                .insertMachine(pos(3, 0), machine("chemical_infuser", 20), registries).storage()
                .insertMachine(pos(4, 0), machine("isotopic_centrifuge", 20), registries).storage()
                .insertMachine(pos(3, 1), machine("chemical_dissolution_chamber", 10), registries).storage()
                .withDirection(pos(3, 1), GridDirection.UP)
                .withConfiguration(pos(0, 0), marker(item(helper, "mekanism:ingot_uranium", 20), GridDirection.RIGHT))
                .withConfiguration(pos(2, 1), marker(item(helper, "mekanism:fluorite_gem", 10), GridDirection.RIGHT))
                .withConfiguration(pos(3, 2), marker(chemical("sulfuric_acid", 100), GridDirection.UP));
        var compiled = catalog(helper).compile(storage.layout(), helper.getLevel());
        helper.assertTrue(compiled.successful(), "Full registered fission fuel line failed: " + compiled.diagnostic());
        var inputs = List.of(item(helper, "mekanism:ingot_uranium", 20), item(helper, "mekanism:fluorite_gem", 10),
                chemical("sulfuric_acid", 100));
        var outputs = List.of(chemical("fissile_fuel", 20_000));
        helper.assertTrue(FactoryCompiler.map(compiled.factory().inputs()).equals(FactoryCompiler.map(inputs))
                        && FactoryCompiler.map(compiled.factory().outputs()).equals(FactoryCompiler.map(outputs)),
                "Full native chain lost a material or exposed an intermediate as an external resource");
        var dissolution = compiled.factory().recipes().get(pos(3, 1));
        helper.assertTrue(FactoryCompiler.map(dissolution.inputsForMachines(10)).equals(FactoryCompiler.map(
                        List.of(item(helper, "mekanism:fluorite_gem", 10), chemical("sulfuric_acid", 100)))),
                "Dissolution did not consume the eight-upgrade acid budget");
        // Installed machine counts only constrain throughput, never the configured material ratio.
        for (int count : List.of(9, 11)) {
            var changed = storage.layout().withCell(pos(3, 1), new FactoryCell.Machine(
                    new com.aquariverr.fluxod.miniature.model.MachineStack(machineKey(helper, "chemical_dissolution_chamber"), count),
                    dissolution.reference(), GridDirection.UP));
            var resized = catalog(helper).compile(changed, helper.getLevel());
            helper.assertTrue(resized.successful() && resized.factory().inputs().equals(compiled.factory().inputs())
                            && resized.factory().outputs().equals(compiled.factory().outputs()),
                    "Changing dissolution capacity changed the material ratio");
        }
        var fixture = MiniatureRuntimeGameTests.fixture(helper, storage.withInputs(inventory(scale(inputs, 2))), compiled.factory().energyPerBatch());
        fixture.tick();
        MiniatureMekanismRecipeGameTests.observeBatch(helper, fixture, inventory(scale(outputs, 2)), 80);
    }

    @GameTest(template = "empty", batch = "miniature_mekanism", timeoutTicks = 60)
    public static void registeredSulfuricAcidSupplyRemainsASeparateBufferableBatch(GameTestHelper helper) {
        if (!available(helper)) return;
        var registries = helper.getLevel().registryAccess();
        FactoryStorage storage = FactoryStorage.restore(FactoryLayout.empty(5), MachineInventory.empty(),
                        ResourceInventory.empty(), ResourceInventory.empty(), registries)
                .insertMachine(pos(1, 2), machine("chemical_oxidizer", 1), registries).storage()
                .insertMachine(pos(2, 2), machine("chemical_infuser", 1), registries).storage()
                .insertMachine(pos(3, 2), machine("chemical_infuser", 1), registries).storage()
                .withConfiguration(pos(0, 2), marker(item(helper, "mekanism:dust_sulfur", 10), GridDirection.RIGHT))
                .withConfiguration(pos(2, 1), marker(chemical("oxygen", 500), GridDirection.DOWN))
                .withConfiguration(pos(3, 1), marker(chemical("water_vapor", 1000), GridDirection.DOWN))
                .withConfiguration(pos(4, 2), new FactoryCell.Arrow());
        var compiled = catalog(helper).compile(storage.layout(), helper.getLevel());
        var inputs = List.of(item(helper, "mekanism:dust_sulfur", 10), chemical("oxygen", 500), chemical("water_vapor", 1000));
        var outputs = List.of(chemical("sulfuric_acid", 1000));
        helper.assertTrue(compiled.successful() && FactoryCompiler.map(compiled.factory().inputs()).equals(FactoryCompiler.map(inputs))
                        && FactoryCompiler.map(compiled.factory().outputs()).equals(FactoryCompiler.map(outputs)),
                "Chemical upgrades changed the fixed-usage sulfuric acid supply: " + compiled.diagnostic());
        var fixture = MiniatureRuntimeGameTests.fixture(helper, storage.withInputs(inventory(scale(inputs, 2))), compiled.factory().energyPerBatch());
        fixture.tick();
        MiniatureMekanismRecipeGameTests.observeBatch(helper, fixture, inventory(scale(outputs, 2)), 3);
    }

    @GameTest(template = "empty", batch = "miniature_mekanism", timeoutTicks = 60)
    public static void registeredMixedDirectionalRoutesKeepElectrolysisProductsTogether(GameTestHelper helper) {
        if (!available(helper)) return;
        var registries = helper.getLevel().registryAccess();
        FactoryStorage storage = FactoryStorage.restore(FactoryLayout.empty(5), MachineInventory.empty(),
                        ResourceInventory.empty(), ResourceInventory.empty(), registries)
                .insertMachine(pos(0, 0), machine("crusher", 1), registries).storage()
                .insertMachine(pos(1, 2), machine("electrolytic_separator", 1), registries).storage()
                .withDirection(pos(1, 2), GridDirection.DOWN)
                .insertMachine(pos(3, 2), machine("chemical_infuser", 2), registries).storage()
                .withConfiguration(pos(0, 1), marker(item(helper, "minecraft:cobblestone", 1), GridDirection.UP))
                .withConfiguration(pos(0, 2), marker(fluid("water", 2000), GridDirection.RIGHT))
                .withConfiguration(pos(2, 2), marker(chemical("hydrogen", 2000), GridDirection.RIGHT))
                .withConfiguration(pos(3, 1), marker(chemical("chlorine", 2000), GridDirection.DOWN))
                .withConfiguration(pos(1, 3), new FactoryCell.Arrow(GridDirection.DOWN))
                .withConfiguration(pos(4, 1), new FactoryCell.Arrow(GridDirection.DOWN))
                .withConfiguration(pos(4, 2), new FactoryCell.Arrow());
        for (int x = 1; x < 5; x++) {
            storage = storage.withConfiguration(pos(x, 0), new FactoryCell.Arrow(x == 4 ? GridDirection.DOWN : GridDirection.RIGHT))
                    .withConfiguration(pos(x, 4), new FactoryCell.Arrow());
        }
        var compiled = catalog(helper).compile(storage.layout(), helper.getLevel());
        helper.assertTrue(compiled.successful(), "Registered mixed line did not compile: " + compiled.diagnostic());
        var expectedInputs = List.of(item(helper, "minecraft:cobblestone", 1), fluid("water", 2000), chemical("chlorine", 2000), chemical("hydrogen", 2000));
        var expectedOutputs = List.of(item(helper, "minecraft:gravel", 1), chemical("hydrogen_chloride", 2000), chemical("oxygen", 1000), chemical("hydrogen", 2000));
        helper.assertTrue(FactoryCompiler.map(compiled.factory().inputs()).equals(FactoryCompiler.map(expectedInputs))
                        && FactoryCompiler.map(compiled.factory().outputs()).equals(FactoryCompiler.map(expectedOutputs))
                        && FactoryCompiler.map(compiled.factory().outputRows().get(2)).equals(FactoryCompiler.map(
                        List.of(item(helper, "minecraft:gravel", 1), chemical("hydrogen_chloride", 2000))))
                        && FactoryCompiler.map(compiled.factory().outputRows().get(4)).equals(FactoryCompiler.map(
                        List.of(chemical("oxygen", 1000), chemical("hydrogen", 2000)))),
                "The directional native lines lost or split electrolysis products, or changed a quantity");
        helper.assertTrue(compiled.factory().recipes().get(pos(3, 2)).reference().recipeId().equals(id("chemical_infusing/hydrogen_chloride").toString()),
                "Downstream did not resolve the real registered chemical recipe");
        var fixture = MiniatureRuntimeGameTests.fixture(helper, storage.withInputs(inventory(expectedInputs)), FactoryRules.energyPerBatch(3));
        fixture.tick();
        var started = fixture.tile().factoryState().storage().orElseThrow().batch();
        helper.assertTrue(started != null && started.parallelism() == 1 && started.paidTicks() == 1,
                "The native mixed line did not reserve its single available batch");
        for (int elapsed = 0; elapsed <= 21; elapsed++) {
            int tick = elapsed;
            helper.runAfterDelay(tick, () -> {
                var state = fixture.tile().factoryState().storage().orElseThrow();
                int paid = Math.min(tick + 1, 20);
                helper.assertTrue(state.inputs().amounts().isEmpty()
                                && state.outputs().equals(paid == 20 ? inventory(expectedOutputs) : ResourceInventory.empty())
                                && (paid == 20 ? state.batch() == null : state.batch() != null && state.batch().id().equals(started.id())
                                && state.batch().paidTicks() == paid),
                        "The native mixed batch consumed twice, committed early, or lost resources");
                helper.assertTrue(fixture.battery().getStoredEnergy().equals(FactoryRules.energyPerTick(3).multiply(BigInteger.valueOf(20 - paid))),
                        "Three working machines did not pay exactly 60M FE per successful tick");
                if (tick == 21) helper.succeed();
            });
        }
    }

    @GameTest(template = "empty", batch = "miniature_mekanism", timeoutTicks = 160)
    public static void completeFissionLineMakesItsOwnAcidAndRunsAcrossFiveRounds(GameTestHelper helper) {
        if (!available(helper)) return;
        var registries = helper.getLevel().registryAccess();
        FactoryStorage storage = FactoryStorage.restore(FactoryLayout.empty(5), MachineInventory.empty(),
                        ResourceInventory.empty(), ResourceInventory.empty(), registries)
                .insertMachine(pos(1, 0), machine("ultimate_enriching_factory", 20), registries).storage()
                .insertMachine(pos(2, 0), machine("chemical_oxidizer", 10), registries).storage()
                .insertMachine(pos(3, 0), machine("chemical_infuser", 20), registries).storage()
                .insertMachine(pos(4, 0), machine("isotopic_centrifuge", 20), registries).storage()
                .insertMachine(pos(3, 1), machine("chemical_dissolution_chamber", 10), registries).storage().withDirection(pos(3, 1), GridDirection.UP)
                .insertMachine(pos(1, 4), machine("chemical_oxidizer", 1), registries).storage()
                .insertMachine(pos(2, 3), machine("chemical_infuser", 1), registries).storage()
                .insertMachine(pos(4, 3), machine("chemical_infuser", 1), registries).storage().withDirection(pos(4, 3), GridDirection.UP)
                .withConfiguration(pos(0, 0), marker(item(helper, "mekanism:ingot_uranium", 200), GridDirection.RIGHT))
                .withConfiguration(pos(2, 1), marker(item(helper, "mekanism:fluorite_gem", 100), GridDirection.RIGHT))
                .withConfiguration(pos(3, 2), new FactoryCell.Arrow(GridDirection.UP))
                .withConfiguration(pos(4, 2), new FactoryCell.Arrow(GridDirection.LEFT))
                .withConfiguration(pos(1, 3), marker(chemical("oxygen", 500), GridDirection.RIGHT))
                .withConfiguration(pos(3, 3), new FactoryCell.Arrow())
                .withConfiguration(pos(0, 4), marker(item(helper, "mekanism:dust_sulfur", 10), GridDirection.RIGHT))
                .withConfiguration(pos(2, 4), new FactoryCell.Arrow(GridDirection.UP))
                .withConfiguration(pos(4, 4), marker(chemical("water_vapor", 1000), GridDirection.UP));
        var compiled = catalog(helper).compile(storage.layout(), helper.getLevel());
        helper.assertTrue(compiled.successful(), "Integrated acid and fission fuel chain failed: " + compiled.diagnostic());
        var expectedInputs = List.of(item(helper, "mekanism:ingot_uranium", 200), item(helper, "mekanism:fluorite_gem", 100),
                item(helper, "mekanism:dust_sulfur", 10), chemical("oxygen", 500), chemical("water_vapor", 1000));
        var expectedOutputs = inventory(List.of(chemical("fissile_fuel", 200_000)));
        helper.assertTrue(FactoryCompiler.map(compiled.factory().inputs()).equals(FactoryCompiler.map(expectedInputs))
                        && inventory(compiled.factory().outputs()).equals(expectedOutputs)
                        && compiled.factory().requiredTicks(1, 2) == 100 && compiled.factory().machineCount() == 83,
                "Integrated chain has wrong boundary, throughput, or physical machine count");
        var schedule = compiled.factory().energySchedule(1, 2);
        var totalEnergy = schedule.totalEnergy(100);
        helper.assertTrue(schedule.stages().size() > 1 && totalEnergy.compareTo(FactoryRules.energyPerTick(83).multiply(BigInteger.valueOf(100))) < 0,
                "Finished cells still consumed energy throughout the fission batch");
        var unbalanced = storage.layout();
        for (int y = 0; y < unbalanced.size(); y++) for (int x = 0; x < unbalanced.size(); x++) {
            if (unbalanced.cellAt(pos(x, y)) instanceof FactoryCell.Marker marker) unbalanced = unbalanced.withCell(pos(x, y),
                    marker(new ResourceAmount(marker.resource().resource(), BigInteger.ONE), marker.direction()));
        }
        var automatic = catalog(helper).balance(unbalanced, helper.getLevel());
        helper.assertTrue(automatic.successful() && automatic.layout().machineCount() == 83
                        && FactoryCompiler.map(automatic.compilation().factory().inputs()).equals(FactoryCompiler.map(expectedInputs.stream()
                        .map(amount -> new ResourceAmount(amount.resource(), amount.amount().divide(BigInteger.TEN))).toList()))
                        && inventory(automatic.compilation().factory().outputs()).equals(inventory(List.of(chemical("fissile_fuel", 20_000))))
                        && catalog(helper).compile(automatic.layout(), helper.getLevel()).successful(),
                "Automatic balance did not solve the complete native fission chain: " + automatic.compilation().diagnostic());
        var smaller = storage.layout();
        for (int y = 0; y < smaller.size(); y++) for (int x = 0; x < smaller.size(); x++) {
            if (smaller.cellAt(pos(x, y)) instanceof FactoryCell.Marker marker) smaller = smaller.withCell(pos(x, y),
                    marker(new ResourceAmount(marker.resource().resource(), marker.resource().amount().divide(BigInteger.TEN)), marker.direction()));
        }
        var smallCompiled = catalog(helper).compile(smaller, helper.getLevel());
        helper.assertTrue(smallCompiled.successful() && smallCompiled.factory().requiredTicks(1, 2) == 20
                        && inventory(smallCompiled.factory().outputs()).equals(inventory(List.of(chemical("fissile_fuel", 20_000)))),
                "The integrated chain could not use a native sulfuric acid flow below 1000");
        var fixture = MiniatureRuntimeGameTests.fixture(helper, storage.withInputs(inventory(expectedInputs)), totalEnergy);
        fixture.tick();
        var started = fixture.tile().factoryState().storage().orElseThrow().batch();
        helper.assertTrue(started != null && started.parallelism() == 1 && started.requiredTicks() == 100, "Wrong multi-round reservation");
        for (int elapsed = 0; elapsed <= 102; elapsed++) {
            int tick = elapsed;
            helper.runAfterDelay(tick, () -> {
                int paid = Math.min(tick + 1, 100);
                var state = fixture.tile().factoryState().storage().orElseThrow();
                helper.assertTrue(state.inputs().amounts().isEmpty()
                                && state.outputs().equals(paid == 100 ? expectedOutputs : ResourceInventory.empty())
                                && (paid == 100 ? state.batch() == null : state.batch() != null && state.batch().id().equals(started.id())
                                && state.batch().paidTicks() == paid && state.batch().requiredTicks() == 100),
                        "Integrated chain consumed twice, produced early, or lost multi-round progress");
                BigInteger remainingEnergy = BigInteger.ZERO;
                for (int payment = paid; payment < 100; payment++) remainingEnergy = remainingEnergy.add(schedule.energyPerTick(payment));
                helper.assertTrue(fixture.battery().getStoredEnergy().equals(remainingEnergy), "Incorrect multi-round energy");
                if (tick == 37) MiniatureRuntimeGameTests.reload(helper, fixture);
                var before = fixture.tile().factoryState().save(); fixture.tick();
                helper.assertTrue(before.equals(fixture.tile().factoryState().save()), "Repeated tick advanced the batch");
                if (tick == 102) helper.succeed();
            });
        }
    }

    private static ResolvedRecipe checkLine(GameTestHelper helper, String machine, String recipeId, long normalization,
                                            List<ResourceAmount> inputs, List<ResourceAmount> outputs) {
        helper.assertTrue(helper.getLevel().getRecipeManager().byKey(id(recipeId)).isPresent(), "Missing bundled recipe " + recipeId);
        var candidates = catalog(helper).search(machineKey(helper, machine), inputs.stream().map(ResourceAmount::resource).toList(), helper.getLevel());
        var recipe = candidates.candidates().stream().filter(candidate -> candidate.reference().recipeId().equals(id(recipeId).toString()))
                .findFirst().orElseThrow(() -> new IllegalStateException("Registered recipe did not bind: " + recipeId + " / " + candidates));
        helper.assertTrue(recipe.normalization().equals(BigInteger.valueOf(normalization))
                        && FactoryCompiler.map(recipe.inputs()).equals(FactoryCompiler.map(inputs))
                        && FactoryCompiler.map(recipe.outputs()).equals(FactoryCompiler.map(outputs)),
                "Registered recipe has incorrect native quantities: " + recipeId);
        helper.assertTrue(catalog(helper).resolve(machineKey(helper, machine), recipe.reference(), helper.getLevel()).equals(recipe),
                "Registered recipe could not revalidate its saved native binding");
        FactoryStorage storage = FactoryStorage.empty().insertMachine(MACHINE, machine(machine, 1), helper.getLevel().registryAccess()).storage()
                .withRecipe(MACHINE, recipe.reference()).withConfiguration(pos(2, 1), new FactoryCell.Arrow());
        var normalizedInputs = scale(inputs, normalization);
        for (int index = 0; index < normalizedInputs.size(); index++) storage = storage.withConfiguration(index == 0 ? pos(0, 1) : pos(1, 0),
                marker(normalizedInputs.get(index), index == 0 ? GridDirection.RIGHT : GridDirection.DOWN));
        var compiled = catalog(helper).compile(storage.layout(), helper.getLevel());
        helper.assertTrue(compiled.successful() && FactoryCompiler.map(compiled.factory().inputs()).equals(FactoryCompiler.map(normalizedInputs))
                        && FactoryCompiler.map(compiled.factory().outputs()).equals(FactoryCompiler.map(scale(outputs, normalization))),
                "Registered recipe line did not conserve normalized inputs and products: " + recipeId + " / " + compiled.diagnostic());
        return recipe;
    }

    private static boolean available(GameTestHelper helper) {
        if (ModList.get().isLoaded("mekanism")) return true;
        helper.succeed();
        return false;
    }

    private static ItemStack machine(String name, int count) { return new ItemStack(BuiltInRegistries.ITEM.get(id(name)), count); }
    private static ResourceKey machineKey(GameTestHelper helper, String name) {
        return FactoryItemResources.keyOf(machine(name, 1), helper.getLevel().registryAccess());
    }
    private static ResourceLocation id(String path) { return ResourceLocation.fromNamespaceAndPath("mekanism", path); }
    private static GridPosition pos(int x, int y) { return new GridPosition(x, y); }
    private static ResourceAmount item(GameTestHelper helper, String id, long amount) {
        var stack = new ItemStack(BuiltInRegistries.ITEM.get(ResourceLocation.parse(id)));
        return new ResourceAmount(FactoryItemResources.keyOf(stack, helper.getLevel().registryAccess()), BigInteger.valueOf(amount));
    }
    private static ResourceAmount fluid(String id, long amount) { return amount(ResourceKind.FLUID, "minecraft:" + id, amount); }
    private static ResourceAmount chemical(String id, long amount) { return amount(ResourceKind.CHEMICAL, "mekanism:" + id, amount); }
    private static ResourceAmount amount(ResourceKind kind, String id, long amount) { return new ResourceAmount(new ResourceKey(kind, id), BigInteger.valueOf(amount)); }
    private static FactoryCell.Marker marker(ResourceAmount amount, GridDirection direction) { return new FactoryCell.Marker(amount, direction); }
    private static List<ResourceAmount> scale(List<ResourceAmount> amounts, long multiplier) {
        return amounts.stream().map(amount -> new ResourceAmount(amount.resource(), amount.amount().multiply(BigInteger.valueOf(multiplier)))).toList();
    }
    private static ResourceInventory inventory(List<ResourceAmount> amounts) { return new ResourceInventory(FactoryCompiler.map(amounts)); }
    private static MiniatureRecipeCatalog catalog(GameTestHelper helper) { return MiniatureRecipeCatalog.get(helper.getLevel().getServer()); }
    private MiniatureMekanismRegisteredRecipeGameTests() {}
}
