package com.aquariverr.fluxod.gametest;

import com.aquariverr.fluxod.FluxOverdrive;
import com.aquariverr.fluxod.gametest.MiniatureResourceEditingGameTests.Fixture;
import com.aquariverr.fluxod.miniature.model.*;
import com.aquariverr.fluxod.miniature.recipe.MiniatureRecipeCatalog;
import com.aquariverr.fluxod.miniature.recipe.RecipePreview;
import com.aquariverr.fluxod.miniature.runtime.RunningBatch;
import com.aquariverr.fluxod.miniature.storage.*;
import com.aquariverr.fluxod.network.MiniatureFactoryCommandPayload;
import com.aquariverr.fluxod.network.MiniatureFactoryCommandPayload.Action;
import com.aquariverr.fluxod.network.MiniatureFactorySnapshotPayload.Result;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.math.BigInteger;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Exercises the real menu transaction without loading any JEI classes on the server. */
@GameTestHolder(FluxOverdrive.MODID)
@PrefixGameTestTemplate(false)
public final class MiniatureJeiIntentGameTests {
    private static final GridPosition MACHINE = new GridPosition(1, 0);

    @GameTest(template = "empty")
    public static void ghostKindsUseServerDefaultsAndNeverCreateOrConsumeAssets(GameTestHelper helper) {
        Fixture fixture = MiniatureResourceEditingGameTests.fixture(helper, FactoryStorage.empty());
        ItemStack carried = new ItemStack(Items.DIAMOND, 17);
        carried.set(DataComponents.CUSTOM_NAME, Component.literal("Untouched cursor"));
        fixture.menu().setCarried(carried.copy());
        ItemStack named = new ItemStack(Items.IRON_ORE, 64);
        named.set(DataComponents.CUSTOM_NAME, Component.literal("Exact ghost variant"));
        CompoundTag fluidComponents = new CompoundTag();
        CompoundTag fluidData = new CompoundTag();
        fluidData.putString("s09", "exact fluid identity");
        fluidComponents.put("minecraft:custom_data", fluidData);
        var resources = new java.util.ArrayList<>(List.of(
                FactoryItemResources.keyOf(named, helper.getLevel().registryAccess()),
                new ResourceKey(ResourceKind.FLUID, "minecraft:water", fluidComponents)));
        if (ModList.get().isLoaded("mekanism")) resources.add(new ResourceKey(ResourceKind.CHEMICAL, "mekanism:hydrogen"));
        long id = 1;
        for (ResourceKey resource : resources) {
            expect(helper, fixture, intent(fixture, id++, Action.SET_GHOST_MARKER, 0, 0, resource, null), Result.SUCCESS);
            var stored = storage(fixture);
            var marker = (FactoryCell.Marker) stored.layout().cellAt(new GridPosition(0, 0));
            helper.assertTrue(marker.resource().resource().equals(resource)
                            && marker.resource().amount().equals(resource.kind() == ResourceKind.ITEM ? BigInteger.ONE : BigInteger.valueOf(1000))
                            && marker.direction() == GridDirection.RIGHT, "Ghost changed exact identity or trusted an ingredient amount");
            helper.assertTrue(ItemStack.matches(carried, fixture.menu().getCarried()) && stored.machines().entries().isEmpty()
                            && stored.inputs().amounts().isEmpty() && stored.outputs().amounts().isEmpty(),
                    "Ghost marking consumed the cursor or created withdrawable assets");
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void ghostsRejectUnknownEmptyAndNoncanonicalIdentitiesAndOccupiedCells(GameTestHelper helper) {
        Fixture fixture = MiniatureResourceEditingGameTests.fixture(helper, machine(helper)
                .withConfiguration(new GridPosition(2, 0), new FactoryCell.Arrow()));
        fixture.menu().setCarried(new ItemStack(Items.EMERALD, 5));
        CompoundTag unknown = new CompoundTag();
        unknown.putInt("flux_overdrive:unknown_component", 1);
        var invalid = new java.util.ArrayList<>(List.of(
                new ResourceKey(ResourceKind.ITEM, "flux_overdrive:missing_item"),
                new ResourceKey(ResourceKind.ITEM, "minecraft:air"),
                new ResourceKey(ResourceKind.ITEM, "minecraft:iron_ore", unknown),
                new ResourceKey(ResourceKind.FLUID, "flux_overdrive:missing_fluid"),
                new ResourceKey(ResourceKind.FLUID, "minecraft:empty"),
                new ResourceKey(ResourceKind.FLUID, "minecraft:water", unknown),
                new ResourceKey(ResourceKind.CHEMICAL, "flux_overdrive:missing_chemical"),
                new ResourceKey(ResourceKind.CHEMICAL, "mekanism:empty")));
        if (!ModList.get().isLoaded("mekanism")) invalid.add(new ResourceKey(ResourceKind.CHEMICAL, "mekanism:hydrogen"));
        long id = 1;
        for (ResourceKey resource : invalid) rejectUnchanged(helper, fixture,
                intent(fixture, id++, Action.SET_GHOST_MARKER, 0, 0, resource, null), Result.INVALID_COMPONENTS);
        for (int x : List.of(1, 2)) rejectUnchanged(helper, fixture,
                intent(fixture, id++, Action.SET_GHOST_MARKER, x, 0, key(helper, Items.IRON_ORE), null), Result.INVALID_CELL);
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void ghostAndRecipeCommandsShareSnapshotSessionReplayAndPermissionGuards(GameTestHelper helper) {
        Fixture fixture = MiniatureResourceEditingGameTests.fixture(helper, machine(helper));
        var ore = key(helper, Items.IRON_ORE);
        var recipe = smelting(ore);
        for (Action action : List.of(Action.SET_GHOST_MARKER, Action.SELECT_RECIPE)) {
            int x = action == Action.SELECT_RECIPE ? 1 : 0;
            var request = intent(fixture, action == Action.SELECT_RECIPE ? 20 : 1, action, x, 0,
                    action == Action.SET_GHOST_MARKER ? ore : null, action == Action.SELECT_RECIPE ? recipe : null);
            var wrongSession = copy(request, UUID.randomUUID(), request.expectedRevision(), request.commandId(), request.expectedSnapshotId());
            rejectUnchanged(helper, fixture, wrongSession, Result.INVALID_SESSION);
            var wrongSnapshot = copy(request, request.sessionId(), request.expectedRevision(), request.commandId(), UUID.randomUUID());
            rejectUnchanged(helper, fixture, wrongSnapshot, Result.STALE_REVISION);
            request = copy(request, request.sessionId(), fixture.menu().revision(), request.commandId() + 1, fixture.menu().snapshotId());
            expect(helper, fixture, request, Result.SUCCESS);
            var after = fixture.tile().factoryState().save();
            expect(helper, fixture, request, Result.SUCCESS);
            helper.assertTrue(after.equals(fixture.tile().factoryState().save()), "Replay applied a ghost or recipe selection twice");
            rejectUnchanged(helper, fixture, copy(request, request.sessionId(), request.expectedRevision(), request.commandId(), UUID.randomUUID()),
                    Result.COMMAND_ID_REUSED);
            var stale = copy(request, request.sessionId(), request.expectedRevision(), request.commandId() + 1, fixture.menu().snapshotId());
            rejectUnchanged(helper, fixture, stale, Result.STALE_REVISION);
            var current = copy(request, request.sessionId(), fixture.menu().revision(), request.commandId() + 2, fixture.menu().snapshotId());
            fixture.player().setGameMode(GameType.SPECTATOR);
            rejectUnchanged(helper, fixture, current, Result.INVALID_ACCESS);
            fixture.player().setGameMode(GameType.SURVIVAL);
            fixture.player().setPos(fixture.tile().getBlockPos().getX() + 20, fixture.tile().getBlockPos().getY(), fixture.tile().getBlockPos().getZ());
            rejectUnchanged(helper, fixture, current, Result.INVALID_ACCESS);
            var pos = fixture.tile().getBlockPos();
            fixture.player().setPos(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void nativeSelectionBeforeMarkersPersistsExactInputsAndCompileRequiresRealRoute(GameTestHelper helper) {
        Fixture fixture = MiniatureResourceEditingGameTests.fixture(helper, machine(helper));
        ItemStack named = new ItemStack(Items.IRON_ORE);
        named.set(DataComponents.CUSTOM_NAME, Component.literal("JEI exact input"));
        ResourceKey exact = FactoryItemResources.keyOf(named, helper.getLevel().registryAccess());
        RecipeReference reference = smelting(exact);
        fixture.menu().setCarried(new ItemStack(Items.DIAMOND, 31));
        expect(helper, fixture, intent(fixture, 1, Action.SELECT_RECIPE, 1, 0, null, reference), Result.SUCCESS);
        var preview = fixture.menu().snapshot().recipes().get(1);
        helper.assertTrue(preview.status() == RecipePreview.Status.READY && preview.reference().equals(reference)
                        && preview.inputs().equals(List.of(new ResourceAmount(exact, BigInteger.ONE)))
                        && preview.outputs().equals(List.of(new ResourceAmount(key(helper, Items.IRON_INGOT), BigInteger.ONE))),
                "Selection did not derive exact inputs and output quantities from the native recipe");
        var restored = FactoryStateCodec.load(fixture.tile().factoryState().save(), helper.getLevel().registryAccess());
        helper.assertTrue(((FactoryCell.Machine) restored.layout().cellAt(MACHINE)).recipe().equals(reference), "Selected bindings failed persistence");
        helper.assertTrue(!catalog(helper).compile(storage(fixture).layout(), helper.getLevel()).successful(), "Selection alone created an executable route");
        expect(helper, fixture, intent(fixture, 2, Action.SET_GHOST_MARKER, 0, 0, key(helper, Items.IRON_ORE), null), Result.SUCCESS);
        helper.assertTrue(!catalog(helper).compile(storage(fixture).layout(), helper.getLevel()).successful(), "Wrong component variant satisfied an exact binding");
        expect(helper, fixture, intent(fixture, 3, Action.SET_GHOST_MARKER, 0, 0, exact, null), Result.SUCCESS);
        var compiled = catalog(helper).compile(storage(fixture).layout(), helper.getLevel());
        helper.assertTrue(compiled.successful() && compiled.factory().inputs().equals(preview.inputs())
                        && compiled.factory().outputs().equals(preview.outputs()), "The matching route did not compile the authoritative native quantities");
        helper.assertTrue(fixture.menu().getCarried().is(Items.DIAMOND) && fixture.menu().getCarried().getCount() == 31
                        && storage(fixture).inputs().amounts().isEmpty() && storage(fixture).outputs().amounts().isEmpty()
                        && storage(fixture).machines().entry(MACHINE).orElseThrow().count() == 1,
                "Selection, preview, or compilation mutated real assets");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void nativeSelectionRejectsForgedRecipeAdapterSlotsInputsAndReloadedSnapshot(GameTestHelper helper) {
        Fixture fixture = MiniatureResourceEditingGameTests.fixture(helper, machine(helper));
        RecipeReference valid = smelting(key(helper, Items.IRON_ORE));
        List<RecipeReference> invalid = List.of(
                new RecipeReference(valid.adapterId(), "flux_overdrive:missing_recipe", 1, 1, valid.bindings()),
                new RecipeReference("flux_overdrive:blasting", valid.recipeId(), 1, 1, valid.bindings()),
                new RecipeReference(valid.adapterId(), valid.recipeId(), 2, 1, List.of(new RecipeReference.Binding(1, key(helper, Items.IRON_ORE)))),
                smelting(key(helper, Items.DIAMOND)),
                smelting(new ResourceKey(ResourceKind.ITEM, "flux_overdrive:missing_item")),
                smelting(new ResourceKey(ResourceKind.FLUID, "minecraft:water")));
        long id = 1;
        for (RecipeReference reference : invalid) rejectUnchanged(helper, fixture,
                intent(fixture, id++, Action.SELECT_RECIPE, 1, 0, null, reference), Result.NO_RECIPE);
        rejectUnchanged(helper, fixture, intent(fixture, id++, Action.SELECT_RECIPE, 0, 0, null, valid), Result.INVALID_CELL);
        var stale = intent(fixture, id++, Action.SELECT_RECIPE, 1, 0, null, valid);
        catalog(helper).invalidate();
        rejectUnchanged(helper, fixture, stale, Result.STALE_REVISION);
        expect(helper, fixture, intent(fixture, id, Action.SELECT_RECIPE, 1, 0, null, valid), Result.SUCCESS);
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void runningBatchLocksGhostsAndNativeSelections(GameTestHelper helper) {
        ResourceKey ore = key(helper, Items.IRON_ORE);
        var batch = new RunningBatch(UUID.randomUUID(), "0".repeat(64), FactoryRules.environment(0, 0), 1,
                new ResourceInventory(Map.of(ore, BigInteger.ONE)), new ResourceInventory(Map.of(key(helper, Items.IRON_INGOT), BigInteger.ONE)),
                FactoryRules.energyPerTick(1), 7);
        Fixture fixture = MiniatureResourceEditingGameTests.fixture(helper, machine(helper).withBatch(batch, 10));
        rejectUnchanged(helper, fixture, intent(fixture, 1, Action.SET_GHOST_MARKER, 0, 0, ore, null), Result.BATCH_LOCKED);
        rejectUnchanged(helper, fixture, intent(fixture, 2, Action.SELECT_RECIPE, 1, 0, null, smelting(ore)), Result.BATCH_LOCKED);
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void receiptsRetainOnlyDigestsAndCanonicalNbtReplayKeepsItsOriginalResult(GameTestHelper helper) throws ReflectiveOperationException {
        Fixture fixture = MiniatureResourceEditingGameTests.fixture(helper, FactoryStorage.empty());
        var firstBindings = new java.util.ArrayList<RecipeReference.Binding>();
        var reorderedBindings = new java.util.ArrayList<RecipeReference.Binding>();
        for (int slot = 0; slot < 9; slot++) {
            CompoundTag values = new CompoundTag();
            values.putByteArray("a", new byte[60_000]);
            values.putString("b", "same identity");
            CompoundTag reordered = new CompoundTag();
            reordered.putString("b", "same identity");
            reordered.putByteArray("a", new byte[60_000]);
            CompoundTag components = new CompoundTag();
            components.put("minecraft:custom_data", values);
            CompoundTag reorderedComponents = new CompoundTag();
            reorderedComponents.put("minecraft:custom_data", reordered);
            firstBindings.add(new RecipeReference.Binding(slot, new ResourceKey(ResourceKind.ITEM, "minecraft:iron_ore", components)));
            reorderedBindings.add(new RecipeReference.Binding(slot, new ResourceKey(ResourceKind.ITEM, "minecraft:iron_ore", reorderedComponents)));
        }
        var reference = new RecipeReference("flux_overdrive:crafting", "test:rejected_large_reference", 3, 3, firstBindings);
        var reordered = new RecipeReference(reference.adapterId(), reference.recipeId(), 3, 3, reorderedBindings);
        var first = intent(fixture, 1, Action.SELECT_RECIPE, 0, 0, null, reference);
        var same = intent(fixture, 1, Action.SELECT_RECIPE, 0, 0, null, reordered);
        helper.assertTrue(first.equals(same) && first.fingerprint().equals(same.fingerprint()), "NBT insertion order changed semantic request identity");
        rejectUnchanged(helper, fixture, first, Result.INVALID_CELL);
        rejectUnchanged(helper, fixture, same, Result.INVALID_CELL);
        var different = new RecipeReference(reference.adapterId(), "test:changed_reference", 3, 3, firstBindings);
        rejectUnchanged(helper, fixture, intent(fixture, 1, Action.SELECT_RECIPE, 0, 0, null, different), Result.COMMAND_ID_REUSED);
        var receiptField = fixture.menu().getClass().getDeclaredField("receipts");
        receiptField.setAccessible(true);
        Map<?, ?> receipts = (Map<?, ?>) receiptField.get(fixture.menu());
        helper.assertTrue(receipts.size() == 1, "Replay added another retained request");
        Object receipt = receipts.values().iterator().next();
        for (var field : receipt.getClass().getDeclaredFields()) {
            field.setAccessible(true);
            Object retained = field.get(receipt);
            helper.assertTrue(retained instanceof String text && text.matches("[0-9a-f]{64}")
                            || retained instanceof Result || retained instanceof Long
                            || retained instanceof com.aquariverr.fluxod.network.MiniatureFactorySnapshotPayload.ErrorContext,
                    "A receipt retained the large client command or its ingredient object graph");
        }
        for (long id = 2; id <= com.aquariverr.fluxod.menu.MiniatureUniverseMenu.RECEIPT_LIMIT + 1; id++) {
            rejectUnchanged(helper, fixture, intent(fixture, id, Action.SELECT_RECIPE, 0, 0, null, smelting(key(helper, Items.IRON_ORE))), Result.INVALID_CELL);
        }
        helper.assertTrue(receipts.size() == com.aquariverr.fluxod.menu.MiniatureUniverseMenu.RECEIPT_LIMIT, "Digest receipts lost their count bound");
        rejectUnchanged(helper, fixture, first, Result.COMMAND_EXPIRED);
        helper.succeed();
    }

    static RecipeReference smelting(ResourceKey input) {
        return new RecipeReference("flux_overdrive:smelting", "minecraft:iron_ingot_from_smelting_iron_ore", 1, 1,
                List.of(new RecipeReference.Binding(0, input)));
    }

    static MiniatureFactoryCommandPayload intent(Fixture fixture, long id, Action action, int x, int y,
                                                 ResourceKey marker, RecipeReference recipe) {
        return new MiniatureFactoryCommandPayload(fixture.menu().containerId, fixture.menu().sessionId(), fixture.tile().getBlockPos(),
                fixture.menu().revision(), id, action, x, y, BigInteger.ONE, GridDirection.RIGHT, fixture.menu().snapshotId(), -1, marker, recipe);
    }

    private static MiniatureFactoryCommandPayload copy(MiniatureFactoryCommandPayload value, UUID session, long revision, long id, UUID snapshot) {
        return new MiniatureFactoryCommandPayload(value.containerId(), session, value.targetPos(), revision, id, value.action(), value.x(), value.y(),
                value.quantity(), value.direction(), snapshot, value.resourceIndex(), value.markerResource(), value.requestedRecipe());
    }

    private static void rejectUnchanged(GameTestHelper helper, Fixture fixture, MiniatureFactoryCommandPayload command, Result result) {
        var before = fixture.tile().factoryState().save();
        ItemStack cursor = fixture.menu().getCarried().copy();
        expect(helper, fixture, command, result);
        helper.assertTrue(before.equals(fixture.tile().factoryState().save()) && ItemStack.matches(cursor, fixture.menu().getCarried()),
                "Rejected JEI intent changed state or the cursor");
    }

    private static void expect(GameTestHelper helper, Fixture fixture, MiniatureFactoryCommandPayload command, Result result) {
        var actual = fixture.menu().handleCommand(fixture.player(), command);
        helper.assertTrue(actual.result() == result, "Expected " + result + " but got " + actual.result() + " for " + command.action());
    }

    private static FactoryStorage machine(GameTestHelper helper) {
        return FactoryStorage.empty().insertMachine(MACHINE, new ItemStack(Items.FURNACE), helper.getLevel().registryAccess()).storage()
                .withConfiguration(new GridPosition(2, 0), new FactoryCell.Arrow());
    }

    private static FactoryStorage storage(Fixture fixture) { return fixture.tile().factoryState().storage().orElseThrow(); }
    private static ResourceKey key(GameTestHelper helper, Item item) { return FactoryItemResources.keyOf(new ItemStack(item), helper.getLevel().registryAccess()); }
    private static MiniatureRecipeCatalog catalog(GameTestHelper helper) { return MiniatureRecipeCatalog.get(helper.getLevel().getServer()); }
    private MiniatureJeiIntentGameTests() {}
}
