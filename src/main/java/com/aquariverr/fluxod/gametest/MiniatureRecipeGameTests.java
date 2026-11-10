package com.aquariverr.fluxod.gametest;

import com.aquariverr.fluxod.FluxOverdrive;
import com.aquariverr.fluxod.block.entity.TileMiniatureUniverse;
import com.aquariverr.fluxod.gametest.MiniatureResourceEditingGameTests.Fixture;
import com.aquariverr.fluxod.miniature.model.FactoryCell;
import com.aquariverr.fluxod.miniature.model.GridDirection;
import com.aquariverr.fluxod.miniature.model.GridPosition;
import com.aquariverr.fluxod.miniature.model.RecipeReference;
import com.aquariverr.fluxod.miniature.model.ResourceAmount;
import com.aquariverr.fluxod.miniature.model.ResourceKey;
import com.aquariverr.fluxod.miniature.recipe.MiniatureRecipeCatalog;
import com.aquariverr.fluxod.miniature.recipe.RecipePreview;
import com.aquariverr.fluxod.miniature.storage.FactoryItemResources;
import com.aquariverr.fluxod.miniature.storage.FactoryState;
import com.aquariverr.fluxod.miniature.storage.FactoryStateCodec;
import com.aquariverr.fluxod.miniature.storage.FactoryStorage;
import com.aquariverr.fluxod.network.MiniatureFactoryCommandPayload;
import com.aquariverr.fluxod.network.MiniatureFactoryCommandPayload.Action;
import com.aquariverr.fluxod.network.MiniatureFactorySnapshotPayload;
import com.aquariverr.fluxod.network.MiniatureFactorySnapshotPayload.Result;
import com.aquariverr.fluxod.register.RegistryBlocks;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import io.netty.buffer.Unpooled;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.SmeltingRecipe;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.CookingBookCategory;
import net.neoforged.neoforge.common.crafting.DataComponentIngredient;
import net.minecraft.world.food.FoodProperties;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.OnDatapackSyncEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.network.connection.ConnectionType;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

@GameTestHolder(FluxOverdrive.MODID)
@PrefixGameTestTemplate(false)
public final class MiniatureRecipeGameTests {
    private static final GridPosition MACHINE = new GridPosition(1, 1);
    private static final BlockPos PERSISTENCE = new BlockPos(1030, 90, 1030);

    @GameTest(template = "empty")
    public static void fourVanillaMachinesResolveUsingRealServerRecipeManager(GameTestHelper helper) {
        var catalog = catalog(helper);
        for (var entry : List.of(new Item[]{Items.FURNACE, Items.IRON_ORE, Items.IRON_INGOT},
                new Item[]{Items.BLAST_FURNACE, Items.IRON_ORE, Items.IRON_INGOT},
                new Item[]{Items.SMOKER, Items.PORKCHOP, Items.COOKED_PORKCHOP},
                new Item[]{Items.CRAFTING_TABLE, Items.OAK_LOG, Items.OAK_PLANKS})) {
            var result = catalog.search(key(helper, entry[0]), List.of(key(helper, entry[1])), helper.getLevel());
            helper.assertTrue(result.candidates().stream().anyMatch(recipe -> amount(recipe.outputs(), key(helper, entry[2])).signum() > 0),
                    "Native recipes missing for " + entry[0] + ": " + result.failure() + ", candidates=" + result.candidates().size()
                            + ", oak=" + helper.getLevel().getRecipeManager().byKey(ResourceLocation.parse("minecraft:oak_planks")));
            for (var candidate : result.candidates()) {
                var validated = catalog.resolve(key(helper, entry[0]), candidate.reference(), helper.getLevel());
                helper.assertTrue(validated.equals(candidate) && validated.normalization().equals(BigInteger.ONE), "Server revalidation disagrees");
                helper.assertTrue(amount(validated.inputs(), key(helper, Items.COAL)).signum() == 0, "Cooking added fuel as material");
            }
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void stableCandidatesIgnoreRegistrationAndMarkerOrderButKeepComponents(GameTestHelper helper) {
        withRecipes(helper, List.of(cooking(helper, "z", "smelting", "iron_nugget"), cooking(helper, "a", "smelting", "iron_ingot")), ignored -> {
            ItemStack named = new ItemStack(Items.IRON_ORE);
            named.set(DataComponents.CUSTOM_NAME, Component.literal("Exact alternative"));
            var inputs = new ArrayList<>(List.of(key(helper, named), key(helper, Items.IRON_ORE)));
            var first = catalog(helper).search(key(helper, Items.FURNACE), inputs, helper.getLevel()).candidates();
            helper.assertTrue(first.size() == 4 && first.getFirst().reference().recipeId().equals("flux_overdrive:s05_a"), "Wrong stable candidate count/order");
            Collections.reverse(inputs);
            var holders = new ArrayList<>(helper.getLevel().getRecipeManager().getRecipes());
            Collections.reverse(holders);
            helper.getLevel().getRecipeManager().replaceRecipes(holders);
            catalog(helper).invalidate();
            var second = catalog(helper).search(key(helper, Items.FURNACE), inputs, helper.getLevel()).candidates();
            helper.assertTrue(first.equals(second), "Candidate identity depends on registration or marker order");
            helper.assertTrue(first.stream().anyMatch(recipe -> recipe.inputs().getFirst().resource().equals(key(helper, named))), "Input components were discarded");
        });
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void recipeAndMachineMismatchUnknownIdsAndDynamicRecipesAreRejected(GameTestHelper helper) {
        withRecipes(helper, List.of(cooking(helper, "a", "smelting", "iron_ingot"), json(helper, "dynamic", """
                {"type":"minecraft:crafting_special_repairitem","category":"misc"}
                """)), ignored -> {
            var selected = catalog(helper).search(key(helper, Items.FURNACE), List.of(key(helper, Items.IRON_ORE)), helper.getLevel()).candidates().getFirst().reference();
            reject(helper, () -> catalog(helper).resolve(key(helper, Items.BLAST_FURNACE), selected, helper.getLevel()));
            reject(helper, () -> catalog(helper).resolve(key(helper, Items.FURNACE), new RecipeReference(selected.adapterId(),
                    "test:missing", 1, 1, selected.bindings()), helper.getLevel()));
            reject(helper, () -> catalog(helper).resolve(key(helper, Items.CRAFTING_TABLE), new RecipeReference("flux_overdrive:crafting",
                    "flux_overdrive:s05_dynamic", 1, 1, selected.bindings()), helper.getLevel()));
            helper.assertTrue(catalog(helper).search(key(helper, Items.CRAFTING_TABLE), List.of(key(helper, Items.IRON_ORE)),
                    helper.getLevel()).candidates().isEmpty(), "Special dynamic recipe was accepted");
        });
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void shapedRecipePreservesEmptySlotsAndRejectsSameTotalsInWrongSlots(GameTestHelper helper) {
        withRecipes(helper, List.of(json(helper, "shape", """
                {"type":"minecraft:crafting_shaped","pattern":["A "," B"],"key":{
                  "A":{"item":"minecraft:iron_ingot"},"B":{"item":"minecraft:gold_ingot"}},
                  "result":{"id":"minecraft:diamond","count":2}}
                """)), ignored -> {
            var selected = catalog(helper).search(key(helper, Items.CRAFTING_TABLE), List.of(key(helper, Items.IRON_INGOT),
                    key(helper, Items.GOLD_INGOT)), helper.getLevel()).candidates().getFirst();
            helper.assertTrue(selected.reference().width() == 2 && selected.reference().height() == 2
                    && selected.reference().bindings().stream().map(RecipeReference.Binding::slot).toList().equals(List.of(0, 3)), "Shaped slots were flattened");
            var wrong = new RecipeReference(selected.reference().adapterId(), selected.reference().recipeId(), 2, 2,
                    List.of(new RecipeReference.Binding(0, key(helper, Items.IRON_INGOT)), new RecipeReference.Binding(1, key(helper, Items.GOLD_INGOT))));
            reject(helper, () -> catalog(helper).resolve(key(helper, Items.CRAFTING_TABLE), wrong, helper.getLevel()));
            helper.assertTrue(amount(selected.outputsForMachines(3), key(helper, Items.DIAMOND)).equals(BigInteger.valueOf(6)), "Machine scaling wrong");
        });
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void nativeCakeReturnsThreeBucketsAndScalesEveryInputAndOutput(GameTestHelper helper) {
        var found = catalog(helper).search(key(helper, Items.CRAFTING_TABLE),
                List.of(key(helper, Items.MILK_BUCKET), key(helper, Items.SUGAR), key(helper, Items.EGG), key(helper, Items.WHEAT)),
                helper.getLevel());
        helper.assertTrue(found.candidates().stream().anyMatch(recipe -> recipe.reference().recipeId().equals("minecraft:cake")),
                "Cake missing: " + found.failure() + ", candidates=" + found.candidates().size() + ", native="
                        + helper.getLevel().getRecipeManager().byKey(ResourceLocation.parse("minecraft:cake")));
        var selected = found.candidates().stream().filter(recipe -> recipe.reference().recipeId().equals("minecraft:cake")).findFirst().orElseThrow();
        helper.assertTrue(amount(selected.inputsForMachines(2), key(helper, Items.MILK_BUCKET)).equals(BigInteger.valueOf(6))
                && amount(selected.outputsForMachines(2), key(helper, Items.BUCKET)).equals(BigInteger.valueOf(6))
                && amount(selected.outputsForMachines(2), key(helper, Items.CAKE)).equals(BigInteger.TWO), "Cake containers or machine scaling lost");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void shapelessReturnsContainerAndRejectsUnmatchedExtraInput(GameTestHelper helper) {
        withRecipes(helper, List.of(json(helper, "container", """
                {"type":"minecraft:crafting_shapeless","ingredients":[{"item":"minecraft:water_bucket"},{"item":"minecraft:dirt"}],
                  "result":{"id":"minecraft:clay_ball"}}
                """)), ignored -> {
            var selected = catalog(helper).search(key(helper, Items.CRAFTING_TABLE), List.of(key(helper, Items.DIRT),
                    key(helper, Items.WATER_BUCKET)), helper.getLevel()).candidates().getFirst();
            helper.assertTrue(amount(selected.outputs(), key(helper, Items.BUCKET)).equals(BigInteger.ONE), "Shapeless container lost");
            var bad = new RecipeReference(selected.reference().adapterId(), selected.reference().recipeId(), 2, 1,
                    List.of(new RecipeReference.Binding(0, key(helper, Items.WATER_BUCKET)), new RecipeReference.Binding(1, key(helper, Items.STONE))));
            reject(helper, () -> catalog(helper).resolve(key(helper, Items.CRAFTING_TABLE), bad, helper.getLevel()));
        });
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void changingDamageableContainerRequiresAnExplicitAdapter(GameTestHelper helper) {
        withRecipes(helper, List.of(json(helper, "tool", """
                {"type":"minecraft:crafting_shapeless","ingredients":[{"item":"minecraft:water_bucket"}],
                  "result":{"id":"minecraft:clay_ball"}}
                """)), ignored -> {
            ItemStack damaged = new ItemStack(Items.WATER_BUCKET);
            damaged.set(DataComponents.MAX_DAMAGE, 100);
            damaged.set(DataComponents.DAMAGE, 1);
            var result = catalog(helper).search(key(helper, Items.CRAFTING_TABLE), List.of(key(helper, damaged)), helper.getLevel());
            helper.assertTrue(result.candidates().isEmpty(), "Changing damageable remainder was accepted as linear material");
        });
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void nativeFoodEffectsRetainExactEncodedComponents(GameTestHelper helper) {
        for (Item item : List.of(Items.POISONOUS_POTATO, Items.GOLDEN_APPLE, Items.HONEY_BOTTLE, Items.PORKCHOP)) {
            ResourceKey identity = key(helper, item);
            ItemStack restored = FactoryItemResources.itemOf(identity, helper.getLevel().registryAccess());
            helper.assertTrue(identity.equals(key(helper, restored)), "Food effects or conversion container changed on round trip");
            var original = new ItemStack(item).get(DataComponents.FOOD);
            var actual = restored.get(DataComponents.FOOD);
            helper.assertTrue(original.nutrition() == actual.nutrition() && original.effects().size() == actual.effects().size(), "Food semantics lost");
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void foodConversionCannotSilentlyLoseNestedItemCount(GameTestHelper helper) {
        ItemStack food = new ItemStack(Items.APPLE);
        food.set(DataComponents.FOOD, new FoodProperties(1, 1, false, 1.6F,
                java.util.Optional.of(new ItemStack(Items.BUCKET, 2)), List.of()));
        reject(helper, () -> key(helper, food));
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void markersAreCandidateHintsIndependentOfAmountRouteAndRealInventory(GameTestHelper helper) {
        FactoryStorage storage = storage(helper, Items.FURNACE, Items.IRON_ORE);
        var first = catalog(helper).previews(storage.layout(), helper.getLevel()).get(4);
        storage = storage.withConfiguration(new GridPosition(0, 0), new FactoryCell.Marker(new ResourceAmount(key(helper, Items.IRON_ORE),
                BigInteger.valueOf(9999)), GridDirection.UP));
        helper.assertTrue(first.equals(catalog(helper).previews(storage.layout(), helper.getLevel()).get(4)), "Candidate matching incorrectly balances markers");
        helper.assertTrue(first.candidateCount() > 0 && storage.inputs().amounts().isEmpty(), "Candidate search required real inventory");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void cyclingChangesOnlyConfigurationAndReplayedCommandCannotCycleTwice(GameTestHelper helper) {
        withRecipes(helper, twoCooking(helper), ignored -> {
            Fixture fixture = MiniatureResourceEditingGameTests.fixture(helper, storage(helper, Items.FURNACE, Items.IRON_ORE));
            var before = fixture.tile().factoryState().storage().orElseThrow();
            var command = cycle(fixture, 1);
            var response = fixture.menu().handleCommand(fixture.player(), command);
            helper.assertTrue(response.result() == Result.SUCCESS && response.snapshot().recipes().get(4).selectedIndex() == 1, "Right click did not cycle from default");
            CompoundTag after = fixture.tile().factoryState().save();
            helper.assertTrue(fixture.menu().handleCommand(fixture.player(), command).result() == Result.SUCCESS
                    && after.equals(fixture.tile().factoryState().save()), "Replay cycled twice");
            helper.assertTrue(fixture.menu().getCarried().isEmpty() && before.machines() == fixture.tile().factoryState().storage().orElseThrow().machines()
                    && fixture.tile().factoryState().storage().orElseThrow().outputs().amounts().isEmpty(), "Cycling changed assets");
            helper.assertTrue(fixture.menu().handleCommand(fixture.player(), cycle(fixture, 2)).snapshot().recipes().get(4).selectedIndex() == 0,
                    "Cycle did not wrap");
        });
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void missingCandidatesAndOccupiedCursorKeepMachineAndSelection(GameTestHelper helper) {
        withRecipes(helper, twoCooking(helper), ignored -> {
            Fixture fixture = MiniatureResourceEditingGameTests.fixture(helper, storage(helper, Items.FURNACE));
            CompoundTag before = fixture.tile().factoryState().save();
            var result = fixture.menu().handleCommand(fixture.player(), cycle(fixture, 1));
            helper.assertTrue(result.result() == Result.NO_RECIPE && result.snapshot().recipes().get(4).missing().contains(key(helper, Items.IRON_ORE))
                    && before.equals(fixture.tile().factoryState().save()), "Missing inputs changed machine or lack diagnostics");
            fixture.menu().setCarried(new ItemStack(Items.DIRT, 5));
            helper.assertTrue(fixture.menu().handleCommand(fixture.player(), cycle(fixture, 2)).result() == Result.CURSOR_BLOCKED
                    && fixture.menu().getCarried().getCount() == 5 && before.equals(fixture.tile().factoryState().save()), "Occupied cursor changed recipe");
        });
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void selectedNativeBindingSurvivesQuantityChangesAndMissingMarkers(GameTestHelper helper) {
        withRecipes(helper, twoCooking(helper), ignored -> {
            FactoryStorage storage = storage(helper, Items.FURNACE, Items.IRON_ORE);
            var reference = catalog(helper).cycle(storage.layout(), MACHINE, helper.getLevel());
            storage = storage.withRecipe(MACHINE, reference);
            var changed = storage.withConfiguration(new GridPosition(0, 0), new FactoryCell.Marker(new ResourceAmount(key(helper, Items.IRON_ORE),
                    BigInteger.valueOf(700)), GridDirection.LEFT));
            helper.assertTrue(catalog(helper).previews(changed.layout(), helper.getLevel()).get(4).status() == RecipePreview.Status.READY, "Unbalanced quantity lost choice");
            changed = changed.withConfiguration(new GridPosition(0, 0), FactoryCell.Empty.INSTANCE);
            var selected = catalog(helper).previews(changed.layout(), helper.getLevel()).get(4);
            helper.assertTrue(selected.status() == RecipePreview.Status.READY && selected.reference().equals(reference)
                    && !selected.inputs().isEmpty() && !selected.outputs().isEmpty(),
                    "A still-valid native selection disappeared when an input marker was removed");
            helper.assertTrue(!catalog(helper).compile(changed.layout(), helper.getLevel()).successful(),
                    "Selecting a native recipe bypassed missing route validation");
        });
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void recipeChoicePersistsAcrossStateAndMachineCountChanges(GameTestHelper helper) {
        FactoryStorage storage = storage(helper, Items.FURNACE, Items.IRON_ORE);
        var reference = catalog(helper).cycle(storage.layout(), MACHINE, helper.getLevel());
        storage = storage.withRecipe(MACHINE, reference).insertMachine(MACHINE, new ItemStack(Items.FURNACE, 2), helper.getLevel().registryAccess()).storage();
        storage = storage.extractMachine(MACHINE, 1, helper.getLevel().registryAccess()).storage();
        CompoundTag saved = FactoryStateCodec.save(storage, helper.getLevel().registryAccess());
        var restored = FactoryStateCodec.load(saved, helper.getLevel().registryAccess());
        helper.assertTrue(((FactoryCell.Machine) restored.layout().cellAt(MACHINE)).recipe().equals(reference)
                && restored.machines().entry(MACHINE).orElseThrow().count() == 2, "Choice or machine count was lost on persistence");
        helper.assertTrue(saved.equals(FactoryStateCodec.save(restored, helper.getLevel().registryAccess())), "Recipe state round trip differs");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void reloadInvalidatesMenuSnapshotAndRetainsRemovedSelection(GameTestHelper helper) {
        withRecipes(helper, twoCooking(helper), ignored -> {
            FactoryStorage storage = storage(helper, Items.FURNACE, Items.IRON_ORE);
            storage = storage.withRecipe(MACHINE, catalog(helper).cycle(storage.layout(), MACHINE, helper.getLevel()));
            Fixture fixture = MiniatureResourceEditingGameTests.fixture(helper, storage);
            var stale = cycle(fixture, 1);
            var before = fixture.tile().factoryState().save();
            long generation = fixture.menu().snapshot().recipeGeneration();
            helper.getLevel().getRecipeManager().replaceRecipes(List.of(cooking(helper, "a", "smelting", "iron_ingot")));
            NeoForge.EVENT_BUS.post(new OnDatapackSyncEvent(helper.getLevel().getServer().getPlayerList(), null));
            var response = fixture.menu().handleCommand(fixture.player(), stale);
            helper.assertTrue(response.result() == Result.STALE_REVISION && response.snapshot().recipeGeneration() > generation
                    && !response.snapshotId().equals(stale.expectedSnapshotId()), "Reload reused a stale recipe selection snapshot");
            helper.assertTrue(response.snapshot().recipes().get(4).status() == RecipePreview.Status.INVALID_SELECTION
                    && before.equals(fixture.tile().factoryState().save()), "Reload silently replaced removed choice");
            helper.assertTrue(fixture.menu().handleCommand(fixture.player(), cycle(fixture, 2)).result() == Result.SUCCESS,
                    "Explicit cycle could not replace an invalid choice");
        });
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void loginDoesNotInvalidateCatalogAndReloadBroadcastsWithoutLayoutChange(GameTestHelper helper) {
        Fixture fixture = MiniatureResourceEditingGameTests.fixture(helper, storage(helper, Items.FURNACE, Items.IRON_ORE));
        long generation = catalog(helper).generation();
        UUID snapshot = fixture.menu().snapshotId();
        var before = fixture.tile().factoryState().save();
        MiniatureRecipeCatalog.onDatapackSync(new OnDatapackSyncEvent(helper.getLevel().getServer().getPlayerList(), fixture.player()));
        helper.assertTrue(catalog(helper).generation() == generation, "Joining player invalidated catalog");
        catalog(helper).invalidate();
        fixture.menu().broadcastChanges();
        helper.assertTrue(!snapshot.equals(fixture.menu().snapshotId()) && fixture.menu().snapshot().recipeGeneration() == generation + 1
                && before.equals(fixture.tile().factoryState().save()), "Unchanged layout did not broadcast new recipe generation");
        UUID broadcast = fixture.menu().snapshotId();
        fixture.menu().broadcastChanges();
        helper.assertTrue(broadcast.equals(fixture.menu().snapshotId()), "Unchanged recipes resent snapshot");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void concurrentRecipeEditsUseRevisionAndSnapshotGuards(GameTestHelper helper) {
        withRecipes(helper, twoCooking(helper), ignored -> {
            Fixture first = MiniatureResourceEditingGameTests.fixture(helper, storage(helper, Items.FURNACE, Items.IRON_ORE));
            Fixture second = MiniatureResourceEditingGameTests.playerFixture(helper, first.tile());
            var stale = cycle(second, 1);
            helper.assertTrue(first.menu().handleCommand(first.player(), cycle(first, 1)).result() == Result.SUCCESS, "First selection failed");
            var before = first.tile().factoryState().save();
            helper.assertTrue(second.menu().handleCommand(second.player(), stale).result() == Result.STALE_REVISION
                    && before.equals(first.tile().factoryState().save()), "Concurrent stale edit changed choice");
        });
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void recipePreviewAndReferenceRoundTripThroughBoundedNetworkCodec(GameTestHelper helper) {
        Fixture fixture = MiniatureResourceEditingGameTests.fixture(helper, storage(helper, Items.CRAFTING_TABLE,
                Items.MILK_BUCKET, Items.SUGAR, Items.EGG, Items.WHEAT));
        var payload = fixture.menu().handleCommand(fixture.player(), cycle(fixture, 1));
        RegistryFriendlyByteBuf buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), helper.getLevel().registryAccess(), ConnectionType.NEOFORGE);
        try {
            MiniatureFactorySnapshotPayload.STREAM_CODEC.encode(buffer, payload);
            helper.assertTrue(buffer.readableBytes() < MiniatureFactorySnapshotPayload.MAX_PAYLOAD_BYTES, "Recipe snapshot exceeded bound");
            helper.assertTrue(payload.equals(MiniatureFactorySnapshotPayload.STREAM_CODEC.decode(buffer)), "Recipe preview lost slots, quantities, or generation");
        } finally { buffer.release(); }
        reject(helper, () -> new MiniatureFactoryCommandPayload(1, UUID.randomUUID(), BlockPos.ZERO, 0, 1,
                Action.CYCLE_RECIPE, 0, 0, BigInteger.ONE, GridDirection.RIGHT));
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void excessiveAlternativesRejectWholeSearchWithoutPartialCandidates(GameTestHelper helper) {
        withRecipes(helper, List.of(json(helper, "many", """
                {"type":"minecraft:crafting_shaped","pattern":["AAA","AAA","AAA"],"key":{"A":{"tag":"minecraft:planks"}},
                  "result":{"id":"minecraft:diamond"}}
                """)), ignored -> {
            var search = catalog(helper).search(key(helper, Items.CRAFTING_TABLE),
                    List.of(key(helper, Items.OAK_PLANKS), key(helper, Items.BIRCH_PLANKS)), helper.getLevel());
            helper.assertTrue(search.failure() == RecipePreview.Status.SEARCH_LIMIT && search.candidates().isEmpty(), "Oversized candidate set silently truncated");
        });
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void nativeArrayIngredientsBindAllSupportedAlternatives(GameTestHelper helper) {
        withRecipes(helper, List.of(json(helper, "alternatives", """
                {"type":"minecraft:smelting","ingredient":[{"item":"minecraft:iron_ore"},{"item":"minecraft:gold_ore"}],
                 "result":{"id":"minecraft:iron_ingot"},"experience":0.0,"cookingtime":200}
                """)), ignored -> {
            var found = catalog(helper).search(key(helper, Items.FURNACE), List.of(key(helper, Items.IRON_ORE), key(helper, Items.GOLD_ORE)), helper.getLevel());
            helper.assertTrue(found.candidates().size() == 2, "Native array alternatives were treated as unsupported custom logic");
            for (var recipe : found.candidates()) catalog(helper).resolve(key(helper, Items.FURNACE), recipe.reference(), helper.getLevel());
        });
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void nativeComponentIngredientRejectsForgedInputVariant(GameTestHelper helper) {
        ItemStack named = new ItemStack(Items.IRON_ORE);
        named.set(DataComponents.CUSTOM_NAME, Component.literal("Required ore"));
        RecipeHolder<?> recipe = new RecipeHolder<>(FluxOverdrive.location("s05_components"), new SmeltingRecipe("", CookingBookCategory.MISC,
                DataComponentIngredient.of(true, named), new ItemStack(Items.IRON_INGOT), 0, 200));
        withRecipes(helper, List.of(recipe), ignored -> {
            var search = catalog(helper).search(key(helper, Items.FURNACE), List.of(key(helper, named), key(helper, Items.IRON_ORE)), helper.getLevel());
            helper.assertTrue(search.candidates().size() == 1 && search.candidates().getFirst().inputs().getFirst().resource().equals(key(helper, named)),
                    "Native component predicate accepted another variant");
            var reference = search.candidates().getFirst().reference();
            var forged = new RecipeReference(reference.adapterId(), reference.recipeId(), 1, 1,
                    List.of(new RecipeReference.Binding(0, key(helper, Items.IRON_ORE))));
            reject(helper, () -> catalog(helper).resolve(key(helper, Items.FURNACE), forged, helper.getLevel()));
        });
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void subclassedRecipesDoNotBypassTheDeterministicAdapterWhitelist(GameTestHelper helper) {
        RecipeHolder<?> custom = new RecipeHolder<>(FluxOverdrive.location("s05_subclass"), new SmeltingRecipe("", CookingBookCategory.MISC,
                Ingredient.of(Items.IRON_ORE), new ItemStack(Items.IRON_INGOT), 0, 200) {});
        withRecipes(helper, List.of(custom), ignored -> helper.assertTrue(catalog(helper).search(key(helper, Items.FURNACE),
                List.of(key(helper, Items.IRON_ORE)), helper.getLevel()).candidates().isEmpty(), "Unadapted subclass accepted as vanilla recipe"));
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void actualServerRestartPreservesSelectedRecipeAndBindings(GameTestHelper helper) {
        var level = helper.getLevel();
        level.getChunk(PERSISTENCE.getX() >> 4, PERSISTENCE.getZ() >> 4);
        var expected = new RecipeReference("flux_overdrive:smelting", "minecraft:iron_ingot_from_smelting_iron_ore", 1, 1,
                List.of(new RecipeReference.Binding(0, key(helper, Items.IRON_ORE))));
        if (System.getProperty("flux_overdrive.miniaturePersistencePhase", "write").equals("write")) {
            level.setBlock(PERSISTENCE, RegistryBlocks.MINIATURE_UNIVERSE.get().defaultBlockState(), 3);
            var tile = (TileMiniatureUniverse) level.getBlockEntity(PERSISTENCE);
            var storage = storage(helper, Items.FURNACE, Items.IRON_ORE).withRecipe(MACHINE, expected);
            helper.assertTrue(tile.applyPreparedState(tile.factoryState(), FactoryState.of(storage, level.registryAccess())), "Could not prepare persistent recipe");
            // applyPreparedState marks the block entity dirty. Normal GameTestServer shutdown saves
            // it; the independent read phase below must load the selection without rebuilding it.
        }
        var tile = (TileMiniatureUniverse) level.getBlockEntity(PERSISTENCE);
        helper.assertTrue(tile != null && ((FactoryCell.Machine) tile.factoryState().storage().orElseThrow().layout().cellAt(MACHINE)).recipe().equals(expected),
                "Selected recipe did not survive independent server restart");
        helper.assertTrue(catalog(helper).resolve(key(helper, Items.FURNACE), expected, level).outputs().getFirst().resource().equals(key(helper, Items.IRON_INGOT)),
                "Restored selection cannot resolve from native recipes");
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "s05_reload", timeoutTicks = 1200)
    public static void actualDatapackReloadRefreshesOpenMenuWithoutChangingSelection(GameTestHelper helper) {
        var storage = storage(helper, Items.FURNACE, Items.IRON_ORE)
                .withConfiguration(new GridPosition(0, 0), FactoryCell.Empty.INSTANCE)
                .withConfiguration(new GridPosition(0, 1), new FactoryCell.Marker(key(helper, Items.IRON_ORE)))
                .withConfiguration(new GridPosition(2, 1), new FactoryCell.Arrow());
        storage = storage.withRecipe(MACHINE, catalog(helper).cycle(storage.layout(), MACHINE, helper.getLevel()));
        Fixture fixture = MiniatureResourceEditingGameTests.fixture(helper, storage);
        var compiled = catalog(helper).compile(storage.layout(), helper.getLevel());
        helper.assertTrue(compiled.successful() && fixture.tile().applyCompilation(fixture.tile().factoryState(), compiled, catalog(helper).generation()),
                "Could not compile real reload fixture");
        fixture.menu().broadcastChanges();
        var before = fixture.tile().factoryState().save();
        var stale = cycle(fixture, 1);
        long generation = catalog(helper).generation();
        var server = helper.getLevel().getServer();
        var reload = server.reloadResources(server.getPackRepository().getSelectedIds());
        helper.startSequence().thenWaitUntil(() -> helper.assertTrue(reload.isDone(), "Waiting for real datapack reload"))
                .thenExecute(() -> {
                    reload.join();
                    helper.assertTrue(catalog(helper).generation() > generation, "Real reload did not invalidate catalog");
                    helper.assertTrue(fixture.tile().compilationFor(fixture.tile().factoryState()) == null, "Real reload retained compiled result");
                    var response = fixture.menu().handleCommand(fixture.player(), stale);
                    helper.assertTrue(response.result() == Result.STALE_REVISION
                            && response.snapshot().recipes().get(4).status() == RecipePreview.Status.READY
                            && response.snapshot().compilation().status() == com.aquariverr.fluxod.miniature.compile.CompilationPreview.Status.NOT_COMPILED
                            && before.equals(fixture.tile().factoryState().save()), "Real reload changed choice or accepted stale command");
                }).thenIdle(60)
                .thenWaitUntil(() -> helper.assertTrue(MiniatureLifecycleGameTests.chunksReadyForShutdown(helper),
                        "Waiting for chunk generation and save dependencies"))
                .thenSucceed();
    }

    private static MiniatureRecipeCatalog catalog(GameTestHelper helper) { return MiniatureRecipeCatalog.get(helper.getLevel().getServer()); }

    private static ResourceKey key(GameTestHelper helper, Item item) { return key(helper, new ItemStack(item)); }
    private static ResourceKey key(GameTestHelper helper, ItemStack item) { return FactoryItemResources.keyOf(item, helper.getLevel().registryAccess()); }
    private static BigInteger amount(List<ResourceAmount> resources, ResourceKey key) {
        return resources.stream().filter(amount -> amount.resource().equals(key)).map(ResourceAmount::amount).reduce(BigInteger.ZERO, BigInteger::add);
    }

    private static FactoryStorage storage(GameTestHelper helper, Item machine, Item... markers) {
        FactoryStorage storage = FactoryStorage.empty().insertMachine(MACHINE, new ItemStack(machine), helper.getLevel().registryAccess()).storage();
        for (int index = 0; index < markers.length; index++) {
            int cell = index >= 4 ? index + 1 : index;
            storage = storage.withConfiguration(new GridPosition(cell % 3, cell / 3), new FactoryCell.Marker(key(helper, markers[index])));
        }
        return storage;
    }

    private static MiniatureFactoryCommandPayload cycle(Fixture fixture, long id) {
        return new MiniatureFactoryCommandPayload(fixture.menu().containerId, fixture.menu().sessionId(), fixture.tile().getBlockPos(),
                fixture.menu().revision(), id, Action.CYCLE_RECIPE, 1, 1, BigInteger.ONE, GridDirection.RIGHT, fixture.menu().snapshotId(), -1);
    }

    private static RecipeHolder<?> json(GameTestHelper helper, String id, String json) {
        return new RecipeHolder<>(FluxOverdrive.location("s05_" + id), Recipe.CODEC.parse(
                helper.getLevel().registryAccess().createSerializationContext(JsonOps.INSTANCE), JsonParser.parseString(json)).getOrThrow());
    }

    private static RecipeHolder<?> cooking(GameTestHelper helper, String id, String type, String output) {
        return json(helper, id, """
                {"type":"minecraft:%s","ingredient":{"item":"minecraft:iron_ore"},"result":{"id":"minecraft:%s"},"experience":0.0,"cookingtime":200}
                """.formatted(type, output));
    }

    private static List<RecipeHolder<?>> twoCooking(GameTestHelper helper) {
        return List.of(cooking(helper, "a", "smelting", "iron_ingot"), cooking(helper, "z", "smelting", "iron_nugget"));
    }

    private static void withRecipes(GameTestHelper helper, List<RecipeHolder<?>> recipes, Consumer<Void> test) {
        var manager = helper.getLevel().getRecipeManager();
        var original = List.copyOf(manager.getRecipes());
        try {
            manager.replaceRecipes(recipes);
            catalog(helper).invalidate();
            test.accept(null);
        } finally {
            manager.replaceRecipes(original);
            catalog(helper).invalidate();
        }
    }

    private static void reject(GameTestHelper helper, Runnable operation) {
        boolean rejected = false;
        try { operation.run(); } catch (IllegalArgumentException expected) { rejected = true; }
        helper.assertTrue(rejected, "Unsupported or forged recipe was accepted");
    }
}
