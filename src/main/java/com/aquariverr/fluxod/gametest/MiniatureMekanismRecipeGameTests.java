package com.aquariverr.fluxod.gametest;

import com.aquariverr.fluxod.FluxOverdrive;
import com.aquariverr.fluxod.miniature.compile.FactoryCompiler;
import com.aquariverr.fluxod.miniature.model.*;
import com.aquariverr.fluxod.miniature.recipe.MiniatureRecipeCatalog;
import com.aquariverr.fluxod.miniature.storage.*;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import com.mojang.serialization.JsonOps;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.math.BigInteger;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.HashSet;

/** Native serializers and the real server recipe manager, with no optional types in the entry point. */
@GameTestHolder(FluxOverdrive.MODID)
@PrefixGameTestTemplate(false)
public final class MiniatureMekanismRecipeGameTests {
    private static final GridPosition MACHINE = new GridPosition(1, 1);
    private static final List<String> MACHINES = List.of("enrichment_chamber", "crusher", "purification_chamber",
            "chemical_injection_chamber", "isotopic_centrifuge", "chemical_infuser", "electrolytic_separator");
    private static final String ITEM_RECIPE = """
            {"type":"mekanism:%s","input":{"count":2,"item":"minecraft:stone"},
             "output":{"count":3,"id":"minecraft:diamond"}}
            """;
    private static final String ITEM_CHEMICAL_RECIPE = """
            {"type":"mekanism:%s","item_input":{"count":1,"item":"minecraft:iron_ore"},
             "chemical_input":{"amount":1000,"chemical":"mekanism:oxygen"},
             "output":{"count":2,"id":"minecraft:diamond"},"per_tick_usage":%s}
            """;
    private static final String ELECTROLYSIS = """
            {"type":"mekanism:separating","input":{"amount":2,"fluid":"minecraft:water"},
             "left_chemical_output":{"amount":2,"id":"mekanism:hydrogen"},
             "right_chemical_output":{"amount":1,"id":"mekanism:oxygen"}}
            """;
    private static final String DISSOLUTION = """
            {"type":"mekanism:dissolution","item_input":{"count":3,"item":"minecraft:iron_ore"},
             "chemical_input":{"amount":9,"chemical":"mekanism:sulfuric_acid"},
             "output":{"amount":100,"id":"mekanism:hydrofluoric_acid"},"per_tick_usage":%s}
            """;

    @GameTest(template = "empty", batch = "miniature_mekanism", timeoutTicks = 60)
    public static void nativeEnrichingRecipeCompilesAndRuns(GameTestHelper helper) {
        runFamily(helper, "enrichment_chamber", ITEM_RECIPE.formatted("enriching"), 1,
                List.of(item(helper, "stone", 2)), List.of(item(helper, "diamond", 3)));
    }

    @GameTest(template = "empty", batch = "miniature_mekanism", timeoutTicks = 60)
    public static void nativeCrushingRecipeCompilesAndRuns(GameTestHelper helper) {
        runFamily(helper, "crusher", ITEM_RECIPE.formatted("crushing"), 1,
                List.of(item(helper, "stone", 2)), List.of(item(helper, "diamond", 3)));
    }

    @GameTest(template = "empty", batch = "miniature_mekanism", timeoutTicks = 60)
    public static void nativeFixedPurifyingRecipeCompilesAndRuns(GameTestHelper helper) {
        runFamily(helper, "purification_chamber", ITEM_CHEMICAL_RECIPE.formatted("purifying", false), 1,
                List.of(item(helper, "iron_ore", 1), chemical("oxygen", 1000)), List.of(item(helper, "diamond", 2)));
    }

    @GameTest(template = "empty", batch = "miniature_mekanism", timeoutTicks = 60)
    public static void nativeFixedInjectingRecipeCompilesAndRuns(GameTestHelper helper) {
        runFamily(helper, "chemical_injection_chamber", ITEM_CHEMICAL_RECIPE.formatted("injecting", false), 1,
                List.of(item(helper, "iron_ore", 1), chemical("oxygen", 1000)), List.of(item(helper, "diamond", 2)));
    }

    @GameTest(template = "empty", batch = "miniature_mekanism", timeoutTicks = 60)
    public static void perTickPurifyingUsesEightChemicalUpgrades(GameTestHelper helper) {
        runPerTickItemFamily(helper, "purification_chamber", "purifying");
    }

    @GameTest(template = "empty", batch = "miniature_mekanism", timeoutTicks = 60)
    public static void perTickInjectingFactoryPaysOneBudgetPerMachineItem(GameTestHelper helper) {
        runPerTickItemFamily(helper, "ultimate_injecting_factory", "injecting");
    }

    @GameTest(template = "empty", batch = "miniature_mekanism", timeoutTicks = 60)
    public static void perTickCompressingPaysFullTwoHundredTickBudget(GameTestHelper helper) {
        runPerTickItemFamily(helper, "osmium_compressor", "compressing");
    }

    @GameTest(template = "empty", batch = "miniature_mekanism", timeoutTicks = 60)
    public static void perTickMetallurgicInfusingPaysFullTwoHundredTickBudget(GameTestHelper helper) {
        runPerTickItemFamily(helper, "metallurgic_infuser", "metallurgic_infusing");
    }

    @GameTest(template = "empty", batch = "miniature_mekanism", timeoutTicks = 60)
    public static void perTickPaintingPaysFullTwoHundredTickBudget(GameTestHelper helper) {
        runPerTickItemFamily(helper, "painting_machine", "painting");
    }

    private static void runPerTickItemFamily(GameTestHelper helper, String machine, String type) {
        int chemicalBudget = type.equals("purifying") || type.equals("injecting") ? 20_000 : 200_000;
        runFamily(helper, machine, ITEM_CHEMICAL_RECIPE.formatted(type, true), 1,
                List.of(item(helper, "iron_ore", 1), chemical("oxygen", chemicalBudget)), List.of(item(helper, "diamond", 2)));
    }

    @GameTest(template = "empty", batch = "miniature_mekanism", timeoutTicks = 60)
    public static void perTickNucleosynthesizingUsesItsRecipeDuration(GameTestHelper helper) {
        String json = ITEM_CHEMICAL_RECIPE.formatted("nucleosynthesizing", true).replace("\"per_tick_usage\"", "\"duration\":17,\"per_tick_usage\"");
        runFamily(helper, "antiprotonic_nucleosynthesizer", json, 1,
                List.of(item(helper, "iron_ore", 1), chemical("oxygen", 17_000)), List.of(item(helper, "diamond", 2)));
    }

    @GameTest(template = "empty", batch = "miniature_mekanism", timeoutTicks = 60)
    public static void fixedNucleosynthesizingDoesNotMultiplyByRecipeDuration(GameTestHelper helper) {
        String json = ITEM_CHEMICAL_RECIPE.formatted("nucleosynthesizing", false).replace("\"per_tick_usage\"", "\"duration\":400,\"per_tick_usage\"");
        runFamily(helper, "antiprotonic_nucleosynthesizer", json, 1,
                List.of(item(helper, "iron_ore", 1), chemical("oxygen", 1000)), List.of(item(helper, "diamond", 2)));
    }

    @GameTest(template = "empty", batch = "miniature_mekanism", timeoutTicks = 60)
    public static void perTickDissolutionNormalizesEightChemicalUpgradeBudget(GameTestHelper helper) {
        runFamily(helper, "chemical_dissolution_chamber", DISSOLUTION.formatted(true), 10,
                List.of(item(helper, "iron_ore", 3), chemical("sulfuric_acid", 90)), List.of(chemical("hydrofluoric_acid", 100)));
    }

    @GameTest(template = "empty", batch = "miniature_mekanism", timeoutTicks = 60)
    public static void fixedDissolutionDoesNotMultiplyChemicalUsage(GameTestHelper helper) {
        runFamily(helper, "chemical_dissolution_chamber", DISSOLUTION.formatted(false), 10,
                List.of(item(helper, "iron_ore", 3), chemical("sulfuric_acid", 9)), List.of(chemical("hydrofluoric_acid", 100)));
    }

    @GameTest(template = "empty", batch = "miniature_mekanism")
    public static void virtualChemicalUpgradeBaselineMatchesNativeMachines(GameTestHelper helper) throws ReflectiveOperationException {
        if (!available(helper)) return;
        // Exercise native upgrade components and native upgrade math without linking common Mekanism
        // classes from the optional-mod entry point. These detached tiles do not alter the world.
        var upgradeClass = Class.forName("mekanism.api.Upgrade");
        var chemicalUpgrade = upgradeClass.getField("CHEMICAL").get(null);
        var upgradeTile = Class.forName("mekanism.common.tile.interfaces.IUpgradeTile");
        var nativeMath = Class.forName("mekanism.common.util.MekanismUtils");
        Set<String> supported = Set.of("chemical_dissolution_chamber", "purification_chamber", "chemical_injection_chamber",
                "ultimate_purifying_factory", "ultimate_injecting_factory");
        for (String name : List.of("chemical_dissolution_chamber", "purification_chamber", "chemical_injection_chamber",
                "ultimate_purifying_factory", "ultimate_injecting_factory", "osmium_compressor", "ultimate_compressing_factory",
                "metallurgic_infuser", "ultimate_infusing_factory", "painting_machine", "antiprotonic_nucleosynthesizer")) {
            var block = BuiltInRegistries.BLOCK.get(ResourceLocation.parse("mekanism:" + name));
            var tile = ((net.minecraft.world.level.block.EntityBlock) block).newBlockEntity(net.minecraft.core.BlockPos.ZERO, block.defaultBlockState());
            var component = upgradeTile.getMethod("getComponent").invoke(tile);
            boolean acceptsChemicalUpgrade = (boolean) component.getClass().getMethod("supports", upgradeClass).invoke(component, chemicalUpgrade);
            helper.assertTrue(acceptsChemicalUpgrade == supported.contains(name), "Unexpected native chemical upgrade support: " + name);
            if (acceptsChemicalUpgrade) {
                component.getClass().getMethod("addUpgrades", upgradeClass, int.class).invoke(component, chemicalUpgrade, 8);
                helper.assertTrue((int) component.getClass().getMethod("getUpgrades", upgradeClass).invoke(component, chemicalUpgrade) == 8,
                        "Failed to install eight native chemical upgrades: " + name);
                double mean = (double) nativeMath.getMethod("getGasPerTickMeanMultiplier", upgradeTile).invoke(null, tile);
                int ticks = name.equals("chemical_dissolution_chamber") ? 100 : 200;
                long total = (long) nativeMath.getMethod("getBaseUsage", upgradeTile, int.class).invoke(null, tile, ticks);
                helper.assertTrue(Math.abs(mean - 0.1) < 1.0e-12 && total == ticks / 10,
                        "Native eight-upgrade consumption differs from the miniature baseline: " + name);
            }
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "miniature_mekanism", timeoutTicks = 60)
    public static void nativeCentrifugingRecipeNormalizesAndRuns(GameTestHelper helper) {
        runFamily(helper, "isotopic_centrifuge", """
                {"type":"mekanism:centrifuging","input":{"amount":4,"chemical":"mekanism:uranium_hexafluoride"},
                 "output":{"amount":2,"id":"mekanism:fissile_fuel"}}
                """, 500, List.of(chemical("uranium_hexafluoride", 4)), List.of(chemical("fissile_fuel", 2)));
    }

    @GameTest(template = "empty", batch = "miniature_mekanism", timeoutTicks = 60)
    public static void nativeInfusingRecipeBindsBothChemicalsAndRuns(GameTestHelper helper) {
        runFamily(helper, "chemical_infuser", """
                {"type":"mekanism:chemical_infusing","left_input":{"amount":1,"chemical":"mekanism:hydrogen"},
                 "right_input":{"amount":2,"chemical":"mekanism:oxygen"},
                 "output":{"amount":5,"id":"mekanism:water_vapor"}}
                """, 200, List.of(chemical("hydrogen", 1), chemical("oxygen", 2)), List.of(chemical("water_vapor", 5)));
    }

    @GameTest(template = "empty", batch = "miniature_mekanism", timeoutTicks = 60)
    public static void nativeElectrolysisRecipeNormalizesBothOutputsAndRuns(GameTestHelper helper) {
        runFamily(helper, "electrolytic_separator", ELECTROLYSIS, 1000,
                List.of(fluid("water", 2)), List.of(chemical("hydrogen", 2), chemical("oxygen", 1)));
    }

    private static void runFamily(GameTestHelper helper, String machineId, String json, long normalization,
                                  List<ResourceAmount> inputs, List<ResourceAmount> outputs) {
        if (!available(helper)) return;
        withRecipes(helper, List.of(json(helper, machineId, json)), () -> {
            var catalog = catalog(helper);
            ItemStack machine = machine(machineId);
            // Exercise real machine inventory admission, including Mekanism's pristine default components.
            FactoryStorage storage = FactoryStorage.empty().insertMachine(MACHINE, machine.copyWithCount(2),
                    helper.getLevel().registryAccess()).storage();
            var found = catalog.search(FactoryItemResources.keyOf(machine, helper.getLevel().registryAccess()),
                    inputs.stream().map(ResourceAmount::resource).toList(), helper.getLevel());
            helper.assertTrue(found.candidates().size() == 1, "Expected one native " + machineId + " recipe: " + found);
            var recipe = found.candidates().getFirst();
            helper.assertTrue(recipe.normalization().equals(BigInteger.valueOf(normalization))
                            && FactoryCompiler.map(recipe.inputs()).equals(FactoryCompiler.map(inputs))
                            && FactoryCompiler.map(recipe.outputs()).equals(FactoryCompiler.map(outputs)),
                    "Native quantities or normalization changed for " + machineId);
            helper.assertTrue(catalog.resolve(FactoryItemResources.keyOf(machine, helper.getLevel().registryAccess()),
                    recipe.reference(), helper.getLevel()).equals(recipe), "Saved binding did not revalidate");
            var scaledInputs = scale(inputs, normalization * 2);
            var scaledOutputs = scale(outputs, normalization * 2);
            storage = storage.withRecipe(MACHINE, recipe.reference());
            for (int index = 0; index < scaledInputs.size(); index++) {
                storage = storage.withConfiguration(switch (index) {
                            case 0 -> new GridPosition(0, 1); case 1 -> new GridPosition(1, 0); default -> new GridPosition(1, 2);
                        }, new FactoryCell.Marker(scaledInputs.get(index), switch (index) {
                            case 0 -> GridDirection.RIGHT; case 1 -> GridDirection.DOWN; default -> GridDirection.UP;
                        }));
            }
            storage = storage.withConfiguration(new GridPosition(2, 1), new FactoryCell.Arrow());
            var compiled = catalog.compile(storage.layout(), helper.getLevel());
            helper.assertTrue(compiled.successful() && FactoryCompiler.map(compiled.factory().inputs()).equals(FactoryCompiler.map(scaledInputs))
                            && FactoryCompiler.map(compiled.factory().outputs()).equals(FactoryCompiler.map(scaledOutputs))
                            && compiled.factory().outputRows().get(1).size() == outputs.size(),
                    "Native full line failed conservation: " + compiled.diagnostic());
            for (ResourceAmount output : scaledOutputs) helper.assertTrue(output.amount().compareTo(output.resource().kind().minimumOutput()) >= 0,
                    "Normalized product fell below its resource minimum");
            var fixture = MiniatureRuntimeGameTests.fixture(helper, storage.withInputs(inventory(scale(scaledInputs, 2))),
                    compiled.factory().energyPerBatch());
            fixture.tick();
            observeBatch(helper, fixture, inventory(scale(scaledOutputs, 2)), 2);
        });
    }

    @GameTest(template = "empty", batch = "miniature_mekanism", timeoutTicks = 60)
    public static void nativeWashingConsumesFluidAndChemical(GameTestHelper helper) {
        runFamily(helper, "chemical_washer", """
                {"type":"mekanism:washing","fluid_input":{"amount":5,"fluid":"minecraft:water"},
                 "chemical_input":{"amount":2,"chemical":"mekanism:dirty_iron"},
                 "output":{"amount":2,"id":"mekanism:clean_iron"}}
                """, 500, List.of(fluid("water", 5), chemical("dirty_iron", 2)), List.of(chemical("clean_iron", 2)));
    }

    @GameTest(template = "empty", batch = "miniature_mekanism", timeoutTicks = 60)
    public static void nativeOxidizingProducesChemical(GameTestHelper helper) {
        runFamily(helper, "chemical_oxidizer", """
                {"type":"mekanism:oxidizing","input":{"count":2,"item":"minecraft:stone"},
                 "output":{"amount":100,"id":"mekanism:oxygen"}}
                """, 10, List.of(item(helper, "stone", 2)), List.of(chemical("oxygen", 100)));
    }

    @GameTest(template = "empty", batch = "miniature_mekanism", timeoutTicks = 60)
    public static void nativeCrystallizingProducesItem(GameTestHelper helper) {
        runFamily(helper, "chemical_crystallizer", """
                {"type":"mekanism:crystallizing","input":{"amount":200,"chemical":"mekanism:clean_iron"},
                 "output":{"count":1,"id":"minecraft:iron_ingot"}}
                """, 1, List.of(chemical("clean_iron", 200)), List.of(item(helper, "iron_ingot", 1)));
    }

    @GameTest(template = "empty", batch = "miniature_mekanism", timeoutTicks = 60)
    public static void nativeCombiningBindsBothItems(GameTestHelper helper) {
        runFamily(helper, "combiner", """
                {"type":"mekanism:combining","main_input":{"count":2,"item":"minecraft:stone"},
                 "extra_input":{"count":3,"item":"minecraft:dirt"},"output":{"count":1,"id":"minecraft:diamond"}}
                """, 1, List.of(item(helper, "stone", 2), item(helper, "dirt", 3)), List.of(item(helper, "diamond", 1)));
    }

    @GameTest(template = "empty", batch = "miniature_mekanism", timeoutTicks = 60)
    public static void nativeReactionPreservesThreeInputsAndBothOutputs(GameTestHelper helper) {
        runFamily(helper, "pressurized_reaction_chamber", """
                {"type":"mekanism:reaction","item_input":{"count":2,"item":"minecraft:stone"},
                 "fluid_input":{"amount":10,"fluid":"minecraft:water"},
                 "chemical_input":{"amount":3,"chemical":"mekanism:hydrogen"},"duration":100,
                 "item_output":{"count":1,"id":"minecraft:diamond"},
                 "chemical_output":{"amount":10,"id":"mekanism:oxygen"}}
                """, 100, List.of(item(helper, "stone", 2), fluid("water", 10), chemical("hydrogen", 3)),
                List.of(item(helper, "diamond", 1), chemical("oxygen", 10)));
    }

    @GameTest(template = "empty", batch = "miniature_mekanism", timeoutTicks = 60)
    public static void nativeRotaryFluidToChemical(GameTestHelper helper) {
        runFamily(helper, "rotary_condensentrator", """
                {"type":"mekanism:rotary","fluid_input":{"amount":2,"fluid":"minecraft:water"},
                 "chemical_output":{"amount":2,"id":"mekanism:water_vapor"}}
                """, 500, List.of(fluid("water", 2)), List.of(chemical("water_vapor", 2)));
    }

    @GameTest(template = "empty", batch = "miniature_mekanism", timeoutTicks = 60)
    public static void nativeRotaryChemicalToFluid(GameTestHelper helper) {
        runFamily(helper, "rotary_condensentrator", """
                {"type":"mekanism:rotary","chemical_input":{"amount":2,"chemical":"mekanism:water_vapor"},
                 "fluid_output":{"amount":2,"id":"minecraft:water"}}
                """, 500, List.of(chemical("water_vapor", 2)), List.of(fluid("water", 2)));
    }

    @GameTest(template = "empty", batch = "miniature_mekanism")
    public static void extendedMachinePrototypesAndFactoryTiersAreAccepted(GameTestHelper helper) {
        if (!available(helper)) return;
        var names = new java.util.ArrayList<>(List.of("energized_smelter", "osmium_compressor", "metallurgic_infuser",
                "combiner", "precision_sawmill", "chemical_oxidizer", "chemical_washer", "chemical_crystallizer",
                "chemical_dissolution_chamber", "pressurized_reaction_chamber", "rotary_condensentrator",
                "thermal_evaporation_controller", "solar_neutron_activator", "antiprotonic_nucleosynthesizer",
                "pigment_extractor", "pigment_mixer", "painting_machine"));
        for (String tier : List.of("basic", "advanced", "elite", "ultimate"))
            for (String type : List.of("smelting", "enriching", "crushing", "compressing", "combining", "purifying", "injecting", "infusing", "sawing"))
                names.add(tier + "_" + type + "_factory");
        for (String name : names) {
            helper.assertTrue(!machine(name).isEmpty(), "Missing native machine " + name);
            try { VanillaMachinePolicy.requireAccepted(machine(name)); }
            catch (IllegalArgumentException invalid) { throw new IllegalArgumentException(name + ": " + invalid.getMessage(), invalid); }
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "miniature_mekanism", timeoutTicks = 60)
    public static void mixedWaterOreAndChemicalLineUsesNativeCapabilitiesAndConsumesAllIntermediateProducts(GameTestHelper helper) {
        if (!available(helper)) return;
        withRecipes(helper, List.of(json(helper, "water", ELECTROLYSIS), json(helper, "combine", """
                {"type":"mekanism:chemical_infusing","left_input":{"amount":2,"chemical":"mekanism:hydrogen"},
                 "right_input":{"amount":1,"chemical":"mekanism:oxygen"},
                 "output":{"amount":1,"id":"mekanism:oxygen"}}
                """),
                json(helper, "ore", ITEM_CHEMICAL_RECIPE.formatted("purifying", false))), () -> {
            var registries = helper.getLevel().registryAccess();
            FactoryStorage storage = FactoryStorage.restore(FactoryLayout.empty(5), MachineInventory.empty(),
                    ResourceInventory.empty(), ResourceInventory.empty(), registries)
                    .insertMachine(new GridPosition(1, 2), machine("electrolytic_separator"), registries).storage()
                    .insertMachine(new GridPosition(2, 2), machine("chemical_infuser"), registries).storage()
                    .insertMachine(new GridPosition(3, 2), machine("purification_chamber"), registries).storage()
                    .withConfiguration(new GridPosition(0, 2), new FactoryCell.Marker(fluid("water", 2000), GridDirection.RIGHT))
                    .withConfiguration(new GridPosition(3, 1), new FactoryCell.Marker(item(helper, "iron_ore", 1), GridDirection.DOWN))
                    .withConfiguration(new GridPosition(4, 2), new FactoryCell.Arrow());
            var compiled = catalog(helper).compile(storage.layout(), helper.getLevel());
            helper.assertTrue(compiled.successful(), "Mixed native line failed: " + compiled.diagnostic());
            helper.assertTrue(FactoryCompiler.map(compiled.factory().inputs()).equals(FactoryCompiler.map(List.of(fluid("water", 2000), item(helper, "iron_ore", 1))))
                            && FactoryCompiler.map(compiled.factory().outputs()).equals(FactoryCompiler.map(List.of(item(helper, "diamond", 2)))),
                    "Intermediate oxygen leaked into the external inventory contract");
            // Bypassing the infuser cannot silently discard the separator's hydrogen coproduct.
            var missing = catalog(helper).compile(storage.layout().withCell(new GridPosition(2, 2), new FactoryCell.Arrow()), helper.getLevel());
            helper.assertTrue(!missing.successful(), "A mixed line discarded its hydrogen coproduct");
            var fixture = MiniatureRuntimeGameTests.fixture(helper, storage, compiled.factory().energyPerBatch());
            var fluid = helper.getLevel().getCapability(Capabilities.FluidHandler.BLOCK, fixture.pluginPos(), Direction.NORTH);
            var item = helper.getLevel().getCapability(Capabilities.ItemHandler.BLOCK, fixture.pluginPos(), Direction.NORTH);
            var before = fixture.tile().factoryState().save();
            helper.assertTrue(fluid != null && item != null && fluid.fill(new FluidStack(Fluids.WATER, 4000), FluidAction.SIMULATE) == 4000
                            && item.insertItem(0, new ItemStack(Items.IRON_ORE, 2), true).isEmpty()
                            && before.equals(fixture.tile().factoryState().save()), "Mixed input simulation changed assets");
            helper.assertTrue(fluid.fill(new FluidStack(Fluids.WATER, 4000), FluidAction.EXECUTE) == 4000
                            && item.insertItem(0, new ItemStack(Items.IRON_ORE, 2), false).isEmpty(), "Mixed capability input failed");
            fixture.tick();
            observeBatch(helper, fixture, inventory(List.of(item(helper, "diamond", 4))), 3);
        });
    }

    @GameTest(template = "empty")
    public static void wrongMachinesAndConfiguredMachinesAreRejected(GameTestHelper helper) {
        if (!available(helper)) return;
        withRecipes(helper, List.of(json(helper, "crush", ITEM_RECIPE.formatted("crushing"))), () -> {
            helper.assertTrue(catalog(helper).search(key(helper, machine("enrichment_chamber")),
                    List.of(item(helper, "stone", 1).resource()), helper.getLevel()).candidates().isEmpty(), "Crusher recipe leaked into enrichment");
        });
        ItemStack configured = machine("electrolytic_separator");
        CompoundTag data = new CompoundTag();
        data.putInt("energy", 123);
        configured.set(DataComponents.CUSTOM_DATA, CustomData.of(data));
        reject(helper, () -> VanillaMachinePolicy.requireAccepted(configured));
        // Removing even a default operational component is a configured machine, not a pristine one.
        ItemStack changedDefault = machine("electrolytic_separator");
        changedDefault.remove(BuiltInRegistries.DATA_COMPONENT_TYPE.get(ResourceLocation.parse("mekanism:dump_mode")));
        reject(helper, () -> VanillaMachinePolicy.requireAccepted(changedDefault));
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "miniature_mekanism")
    public static void pristineMachinesAreAcceptedButChangedRedstoneControlIsRejected(GameTestHelper helper) {
        if (!available(helper)) return;
        var registries = helper.getLevel().registryAccess();
        // Record every machine before admission can fail on any one of them. This includes defaults
        // added after item construction by Mekanism's ModifyDefaultComponentsEvent listener.
        for (String name : MACHINES) FluxOverdrive.LOGGER.info("S08 native machine {} default components: {}", name,
                componentJson(helper, machine(name)));
        var redstone = BuiltInRegistries.DATA_COMPONENT_TYPE.get(ResourceLocation.parse("mekanism:redstone_control"));
        helper.assertTrue(redstone != null, "The native redstone component was not registered");
        for (String name : MACHINES) {
            ItemStack pristine = machine(name);
            assertNativeDefaults(helper, name, componentJson(helper, pristine));
            helper.assertTrue(FactoryStorage.empty().insertMachine(MACHINE, pristine, registries).remainder().isEmpty(),
                    "Pristine native machine was not admitted: " + name);
            ItemStack configured = ItemStack.CODEC.parse(registries.createSerializationContext(JsonOps.INSTANCE), JsonParser.parseString("""
                    {"id":"mekanism:%s","components":{"mekanism:redstone_control":"high"}}
                    """.formatted(name))).getOrThrow();
            helper.assertTrue(pristine.get(redstone) != null && !pristine.get(redstone).equals(configured.get(redstone)),
                    "The native codec did not change redstone control for " + name);
            reject(helper, () -> FactoryStorage.empty().insertMachine(MACHINE, configured, registries));
            ItemStack removed = pristine.copy();
            removed.remove(redstone);
            reject(helper, () -> FactoryStorage.empty().insertMachine(MACHINE, removed, registries));
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "miniature_mekanism")
    public static void machinesWithStoredEnergyItemsChemicalsOrFluidsAreRejected(GameTestHelper helper) {
        if (!available(helper)) return;
        var registries = helper.getLevel().registryAccess();
        for (String name : MACHINES) {
            ItemStack pristine = machine(name);
            var defaults = componentJson(helper, pristine);
            for (var fixture : Map.of("energy", "energy_containers", "items", "items",
                    "chemicals", "chemical_tanks", "fluids", "fluid_tanks").entrySet()) {
                String component = "mekanism:" + fixture.getKey();
                if (!defaults.has(component)) continue;
                JsonObject changed = defaults.getAsJsonObject(component).deepCopy();
                changed.getAsJsonArray(fixture.getValue()).set(0, switch (fixture.getKey()) {
                    case "energy" -> new JsonPrimitive(1L);
                    case "items" -> JsonParser.parseString("{\"id\":\"minecraft:diamond\",\"count\":1}");
                    case "chemicals" -> JsonParser.parseString("{\"id\":\"mekanism:oxygen\",\"amount\":1}");
                    case "fluids" -> JsonParser.parseString("{\"id\":\"minecraft:water\",\"amount\":1}");
                    default -> throw new IllegalStateException();
                });
                JsonObject components = new JsonObject();
                components.add(component, changed);
                JsonObject serialized = new JsonObject();
                serialized.addProperty("id", "mekanism:" + name);
                serialized.add("components", components);
                ItemStack filled = ItemStack.CODEC.parse(registries.createSerializationContext(JsonOps.INSTANCE), serialized).getOrThrow();
                helper.assertTrue(!ItemStack.isSameItemSameComponents(pristine, filled)
                                && componentJson(helper, filled).get(component).equals(changed),
                        "The native codec did not preserve the nonempty " + component + " fixture for " + name);
                reject(helper, () -> FactoryStorage.empty().insertMachine(MACHINE, filled, registries));
            }
        }
        helper.succeed();
    }

    private static JsonObject componentJson(GameTestHelper helper, ItemStack machine) {
        return DataComponentMap.CODEC.encodeStart(helper.getLevel().registryAccess().createSerializationContext(JsonOps.INSTANCE),
                machine.getComponents()).getOrThrow().getAsJsonObject();
    }

    private static void assertNativeDefaults(GameTestHelper helper, String name, JsonObject components) {
        Set<String> expected = new HashSet<>(Set.of("security", "redstone_control", "upgrades", "ejector", "side_config", "energy", "items"));
        int chemicalTanks = switch (name) {
            case "purification_chamber", "chemical_injection_chamber" -> 1;
            case "isotopic_centrifuge", "electrolytic_separator" -> 2;
            case "chemical_infuser" -> 3;
            default -> 0;
        };
        if (chemicalTanks > 0) expected.add("chemicals");
        if (name.equals("electrolytic_separator")) expected.addAll(Set.of("fluids", "dump_mode", "secondary_dump_mode"));
        var actual = components.keySet().stream().filter(id -> id.startsWith("mekanism:")).map(id -> id.substring("mekanism:".length()))
                .collect(java.util.stream.Collectors.toSet());
        helper.assertTrue(actual.equals(expected), "Unexpected native prototype component set for " + name + ": " + actual);
        var energy = components.getAsJsonObject("mekanism:energy").getAsJsonArray("energy_containers");
        helper.assertTrue(energy.size() == 1 && energy.get(0).getAsLong() == 0L, "Native machine did not start with one empty energy container: " + name);
        int itemSlots = Set.of("enrichment_chamber", "crusher", "isotopic_centrifuge").contains(name) ? 3 : 4;
        assertEmptyContainers(helper, name, components, "items", "items", itemSlots);
        if (chemicalTanks > 0) assertEmptyContainers(helper, name, components, "chemicals", "chemical_tanks", chemicalTanks);
        if (name.equals("electrolytic_separator")) assertEmptyContainers(helper, name, components, "fluids", "fluid_tanks", 1);
    }

    private static void assertEmptyContainers(GameTestHelper helper, String machine, JsonObject components,
                                               String component, String field, int count) {
        var containers = components.getAsJsonObject("mekanism:" + component).getAsJsonArray(field);
        helper.assertTrue(containers.size() == count, "Wrong number of default " + component + " containers for " + machine);
        for (var container : containers) helper.assertTrue(container.isJsonObject() && container.getAsJsonObject().size() == 0,
                "Default " + component + " container is not empty for " + machine);
    }

    static void observeBatch(GameTestHelper helper, MiniatureRuntimeGameTests.Fixture fixture,
                                     ResourceInventory expectedOutputs, int machines) {
        var started = fixture.tile().factoryState().storage().orElseThrow().batch();
        helper.assertTrue(started != null && started.paidTicks() == 1 && started.parallelism() == 2
                        && started.outputs().equals(expectedOutputs), "Native line did not lock the expected first batch");
        for (int elapsed = 0; elapsed <= 22; elapsed++) {
            int tick = elapsed;
            helper.runAfterDelay(tick, () -> {
                int paid = Math.min(tick + 1, 20);
                var state = fixture.tile().factoryState().storage().orElseThrow();
                helper.assertTrue(state.inputs().amounts().isEmpty() && state.outputs().equals(paid == 20 ? expectedOutputs : ResourceInventory.empty()),
                        "Native line consumed twice, exposed uncommitted resources, or lost a product");
                helper.assertTrue(fixture.battery().getStoredEnergy().equals(FactoryRules.energyPerTick(machines).multiply(BigInteger.valueOf(20 - paid))),
                        "Native recipe used its own timing/energy multiplier or charged environmental parallelism");
                helper.assertTrue(paid == 20 ? state.batch() == null : state.batch() != null
                                && state.batch().id().equals(started.id()) && state.batch().paidTicks() == paid,
                        "Native batch did not complete on the twentieth actual payment");
                var before = fixture.tile().factoryState().save();
                fixture.tick();
                helper.assertTrue(before.equals(fixture.tile().factoryState().save()), "Repeated tick duplicated native production");
                if (tick == 22) helper.succeed();
            });
        }
    }

    private static boolean available(GameTestHelper helper) {
        if (ModList.get().isLoaded("mekanism")) return true;
        helper.succeed();
        return false;
    }

    private static RecipeHolder<?> json(GameTestHelper helper, String id, String json) {
        return new RecipeHolder<>(FluxOverdrive.location("s08_" + id), Recipe.CODEC.parse(
                helper.getLevel().registryAccess().createSerializationContext(JsonOps.INSTANCE), JsonParser.parseString(json)).getOrThrow());
    }

    private static void withRecipes(GameTestHelper helper, List<RecipeHolder<?>> recipes, Runnable test) {
        var manager = helper.getLevel().getRecipeManager();
        var original = List.copyOf(manager.getRecipes());
        try {
            manager.replaceRecipes(recipes);
            catalog(helper).invalidate();
            test.run();
        } finally {
            manager.replaceRecipes(original);
            catalog(helper).invalidate();
        }
    }

    private static List<ResourceAmount> scale(List<ResourceAmount> values, long scale) {
        return values.stream().map(value -> new ResourceAmount(value.resource(), value.amount().multiply(BigInteger.valueOf(scale)))).toList();
    }

    private static ResourceInventory inventory(List<ResourceAmount> values) { return new ResourceInventory(FactoryCompiler.map(values)); }
    private static ResourceAmount item(GameTestHelper helper, String id, long count) {
        var stack = new ItemStack(BuiltInRegistries.ITEM.get(ResourceLocation.parse("minecraft:" + id)));
        return new ResourceAmount(key(helper, stack), BigInteger.valueOf(count));
    }
    private static ResourceAmount fluid(String id, long count) { return amount(ResourceKind.FLUID, "minecraft:" + id, count); }
    private static ResourceAmount chemical(String id, long count) { return amount(ResourceKind.CHEMICAL, "mekanism:" + id, count); }
    private static ResourceAmount amount(ResourceKind kind, String id, long count) {
        return new ResourceAmount(new ResourceKey(kind, id), BigInteger.valueOf(count));
    }
    private static ResourceKey key(GameTestHelper helper, ItemStack item) { return FactoryItemResources.keyOf(item, helper.getLevel().registryAccess()); }
    private static ItemStack machine(String id) { return new ItemStack(BuiltInRegistries.ITEM.get(ResourceLocation.parse("mekanism:" + id))); }
    private static MiniatureRecipeCatalog catalog(GameTestHelper helper) { return MiniatureRecipeCatalog.get(helper.getLevel().getServer()); }
    private static void reject(GameTestHelper helper, Runnable action) {
        boolean rejected = false;
        try { action.run(); } catch (IllegalArgumentException expected) { rejected = true; }
        helper.assertTrue(rejected, "Configured native machine was accepted");
    }
    private MiniatureMekanismRecipeGameTests() {}
}
