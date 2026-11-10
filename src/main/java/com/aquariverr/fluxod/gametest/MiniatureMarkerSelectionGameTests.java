package com.aquariverr.fluxod.gametest;

import com.aquariverr.fluxod.FluxOverdrive;
import com.aquariverr.fluxod.gametest.MiniatureResourceEditingGameTests.Fixture;
import com.aquariverr.fluxod.miniature.model.FactoryCell;
import com.aquariverr.fluxod.miniature.model.GridDirection;
import com.aquariverr.fluxod.miniature.model.GridPosition;
import com.aquariverr.fluxod.miniature.model.ResourceKey;
import com.aquariverr.fluxod.miniature.model.ResourceKind;
import com.aquariverr.fluxod.miniature.storage.FactoryEditing;
import com.aquariverr.fluxod.miniature.storage.FactoryItemResources;
import com.aquariverr.fluxod.miniature.storage.FactoryStorage;
import com.aquariverr.fluxod.network.MiniatureFactoryCommandPayload;
import com.aquariverr.fluxod.network.MiniatureFactoryCommandPayload.Action;
import com.aquariverr.fluxod.network.MiniatureFactorySnapshotPayload.Result;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.math.BigInteger;
import java.util.List;

@GameTestHolder(FluxOverdrive.MODID)
@PrefixGameTestTemplate(false)
public final class MiniatureMarkerSelectionGameTests {
    @GameTest(template = "empty")
    public static void fullBucketDefaultsToItemAndExplicitFluidNeverDrainsIt(GameTestHelper helper) {
        Fixture fixture = MiniatureResourceEditingGameTests.fixture(helper, FactoryStorage.empty());
        ItemStack bucket = new ItemStack(Items.WATER_BUCKET);
        bucket.set(DataComponents.CUSTOM_NAME, Component.literal("Keep this bucket and its water"));
        ItemStack original = bucket.copy();
        fixture.menu().setCarried(bucket);
        ResourceKey item = FactoryItemResources.keyOf(original, helper.getLevel().registryAccess());
        var choices = FactoryEditing.markerSources(bucket, helper.getLevel().registryAccess());
        helper.assertTrue(choices.size() == 2 && choices.getFirst().resource().equals(item)
                        && choices.get(1).tank() == 0 && choices.get(1).resource().kind() == ResourceKind.FLUID,
                "The filled bucket must offer the item first and water as an explicit tank choice");
        mark(helper, fixture, 1, Action.SET_MARKER_FROM_CARRIED, -1, item, original);
        var fluidCommand = command(fixture, 2, Action.SET_FLUID_MARKER_FROM_CARRIED, 0);
        expect(helper, fixture, fluidCommand, Result.SUCCESS);
        assertMarker(helper, fixture, new ResourceKey(ResourceKind.FLUID, "minecraft:water"), original);
        var saved = fixture.tile().factoryState().save();
        expect(helper, fixture, fluidCommand, Result.SUCCESS);
        helper.assertTrue(saved.equals(fixture.tile().factoryState().save()), "Duplicate marking changed state twice");
        expect(helper, fixture, command(fixture, 2, Action.SET_MARKER_FROM_CARRIED, -1), Result.COMMAND_ID_REUSED);
        mark(helper, fixture, 3, Action.SET_MARKER_FROM_CARRIED, -1, item, original);
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void unavailableEmptyAndInvalidTanksRejectWithoutFallbackOrAssetChanges(GameTestHelper helper) {
        Fixture fixture = MiniatureResourceEditingGameTests.fixture(helper, FactoryStorage.empty());
        long id = 1;
        for (ItemStack held : List.of(new ItemStack(Items.IRON_ORE, 17), new ItemStack(Items.BUCKET), new ItemStack(Items.WATER_BUCKET))) {
            fixture.menu().setCarried(held);
            var before = fixture.tile().factoryState().save();
            ItemStack original = held.copy();
            for (Action action : List.of(Action.SET_FLUID_MARKER_FROM_CARRIED, Action.SET_CHEMICAL_MARKER_FROM_CARRIED)) {
                for (int tank : List.of(0, 1, MiniatureFactoryCommandPayload.MAX_MARKER_TANKS - 1)) {
                    if (held.is(Items.WATER_BUCKET) && action == Action.SET_FLUID_MARKER_FROM_CARRIED && tank == 0) continue;
                    expect(helper, fixture, command(fixture, id++, action, tank), Result.INVALID_COMPONENTS);
                    helper.assertTrue(before.equals(fixture.tile().factoryState().save()) && ItemStack.matches(original, fixture.menu().getCarried()),
                            "Unavailable content selection fell back to an item or changed assets");
                }
            }
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void contentMarkersUseCurrentServerCursorAndRetainAccessAndRevisionGuards(GameTestHelper helper) {
        Fixture fixture = MiniatureResourceEditingGameTests.fixture(helper, FactoryStorage.empty());
        fixture.menu().setCarried(new ItemStack(Items.WATER_BUCKET));
        var requested = command(fixture, 1, Action.SET_FLUID_MARKER_FROM_CARRIED, 0);
        fixture.menu().setCarried(new ItemStack(Items.BUCKET));
        var before = fixture.tile().factoryState().save();
        expect(helper, fixture, requested, Result.INVALID_COMPONENTS);
        helper.assertTrue(before.equals(fixture.tile().factoryState().save()) && fixture.menu().getCarried().is(Items.BUCKET),
                "A stale client choice marked water absent from the actual cursor");
        ItemStack lava = new ItemStack(Items.LAVA_BUCKET);
        fixture.menu().setCarried(lava.copy());
        var stale = command(fixture, 3, Action.SET_FLUID_MARKER_FROM_CARRIED, 0);
        mark(helper, fixture, 2, Action.SET_FLUID_MARKER_FROM_CARRIED, 0,
                new ResourceKey(ResourceKind.FLUID, "minecraft:lava"), lava);
        expect(helper, fixture, stale, Result.STALE_REVISION);
        before = fixture.tile().factoryState().save();
        fixture.player().setGameMode(GameType.SPECTATOR);
        expect(helper, fixture, command(fixture, 4, Action.SET_FLUID_MARKER_FROM_CARRIED, 0), Result.INVALID_ACCESS);
        helper.assertTrue(before.equals(fixture.tile().factoryState().save()) && ItemStack.matches(lava, fixture.menu().getCarried()),
                "Rejected content edit changed authoritative assets");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void optionalChemicalMarkersKeepRealTankContentsAndContainerItemSelectable(GameTestHelper helper) {
        if (ModList.get().isLoaded("mekanism")) ChemicalScenario.run(helper);
        else {
            Fixture fixture = MiniatureResourceEditingGameTests.fixture(helper, FactoryStorage.empty());
            fixture.menu().setCarried(new ItemStack(Items.WATER_BUCKET));
            expect(helper, fixture, command(fixture, 1, Action.SET_CHEMICAL_MARKER_FROM_CARRIED, 0), Result.INVALID_COMPONENTS);
            helper.assertTrue(FactoryEditing.markerSources(fixture.menu().getCarried(), helper.getLevel().registryAccess())
                    .stream().noneMatch(source -> source.resource().kind() == ResourceKind.CHEMICAL), "Absent Mekanism offered chemical contents");
        }
        helper.succeed();
    }

    private static final class ChemicalScenario {
        static void run(GameTestHelper helper) {
            Fixture fixture = MiniatureResourceEditingGameTests.fixture(helper, FactoryStorage.empty());
            // Machine items only store attachments; the native tank item actually exposes this capability.
            ItemStack container = new ItemStack(net.minecraft.core.registries.BuiltInRegistries.ITEM.get(
                    net.minecraft.resources.ResourceLocation.parse("mekanism:basic_chemical_tank")));
            var capability = net.neoforged.neoforge.capabilities.ItemCapability.createVoid(
                    net.minecraft.resources.ResourceLocation.parse("mekanism:chemical_handler"), mekanism.api.chemical.IChemicalHandler.class);
            var handler = capability.getCapability(container, null);
            helper.assertTrue(handler != null && handler.getChemicalTanks() == 1, "Real chemical tank must expose its native tank");
            handler.setChemicalInTank(0, new mekanism.api.chemical.ChemicalStack(
                    mekanism.api.MekanismAPI.CHEMICAL_REGISTRY.getHolder(net.minecraft.resources.ResourceLocation.parse("mekanism:hydrogen")).orElseThrow(), 700));
            ItemStack original = container.copy();
            fixture.menu().setCarried(container);
            var choices = FactoryEditing.markerSources(container, helper.getLevel().registryAccess());
            helper.assertTrue(choices.size() == 2 && choices.get(1).tank() == 0
                            && choices.get(1).resource().registryId().equals("mekanism:hydrogen"),
                    "Native chemical contents are not an explicit source choice");
            mark(helper, fixture, 1, Action.SET_MARKER_FROM_CARRIED, -1,
                    FactoryItemResources.keyOf(original, helper.getLevel().registryAccess()), original);
            mark(helper, fixture, 2, Action.SET_CHEMICAL_MARKER_FROM_CARRIED, 0,
                    new ResourceKey(ResourceKind.CHEMICAL, "mekanism:hydrogen"), original);
            expect(helper, fixture, command(fixture, 3, Action.SET_CHEMICAL_MARKER_FROM_CARRIED, 1), Result.INVALID_COMPONENTS);
            mark(helper, fixture, 4, Action.SET_MARKER_FROM_CARRIED, -1,
                    FactoryItemResources.keyOf(original, helper.getLevel().registryAccess()), original);
            helper.assertTrue(handler.getChemicalInTank(0).getAmount() == 700,
                    "Chemical marker selection drained the real container tank");
            handler.setChemicalInTank(0, mekanism.api.chemical.ChemicalStack.EMPTY);
            var before = fixture.tile().factoryState().save();
            ItemStack emptied = container.copy();
            expect(helper, fixture, command(fixture, 5, Action.SET_CHEMICAL_MARKER_FROM_CARRIED, 0), Result.INVALID_COMPONENTS);
            helper.assertTrue(before.equals(fixture.tile().factoryState().save()) && ItemStack.matches(emptied, fixture.menu().getCarried()),
                    "Empty tank selected stale chemical contents or changed assets");
        }
    }

    private static void mark(GameTestHelper helper, Fixture fixture, long id, Action action, int tank, ResourceKey expected, ItemStack original) {
        expect(helper, fixture, command(fixture, id, action, tank), Result.SUCCESS);
        assertMarker(helper, fixture, expected, original);
    }

    private static void assertMarker(GameTestHelper helper, Fixture fixture, ResourceKey expected, ItemStack original) {
        var storage = fixture.tile().factoryState().storage().orElseThrow();
        var actual = storage.layout().cellAt(new GridPosition(0, 0));
        helper.assertTrue(actual instanceof FactoryCell.Marker, "Expected a marker but got " + actual);
        var marker = (FactoryCell.Marker) actual;
        helper.assertTrue(marker.resource().resource().equals(expected),
                "Wrong marker kind, tank identity or components: expected=" + expected + " actual=" + marker.resource().resource());
        BigInteger expectedAmount = expected.kind() == ResourceKind.ITEM ? BigInteger.ONE : BigInteger.valueOf(1000);
        helper.assertTrue(marker.resource().amount().equals(expectedAmount),
                "Wrong default marker quantity: expected=" + expectedAmount + " actual=" + marker.resource().amount());
        helper.assertTrue(marker.direction() == GridDirection.RIGHT, "New marker did not default to the right direction");
        helper.assertTrue(ItemStack.matches(original, fixture.menu().getCarried()) && storage.machines().entries().isEmpty()
                        && storage.inputs().amounts().isEmpty() && storage.outputs().amounts().isEmpty(), "Marker editing changed real assets");
    }

    private static MiniatureFactoryCommandPayload command(Fixture fixture, long id, Action action, int tank) {
        return new MiniatureFactoryCommandPayload(fixture.menu().containerId, fixture.menu().sessionId(), fixture.tile().getBlockPos(),
                fixture.menu().revision(), id, action, 0, 0, BigInteger.ONE, GridDirection.RIGHT, MiniatureFactoryCommandPayload.NO_SNAPSHOT, tank);
    }

    private static void expect(GameTestHelper helper, Fixture fixture, MiniatureFactoryCommandPayload command, Result expected) {
        var response = fixture.menu().handleCommand(fixture.player(), command);
        helper.assertTrue(response.result() == expected, "Expected " + expected + " but got " + response.result());
    }

    private MiniatureMarkerSelectionGameTests() {}
}
