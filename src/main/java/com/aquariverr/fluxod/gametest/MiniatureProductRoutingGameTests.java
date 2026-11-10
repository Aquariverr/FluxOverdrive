package com.aquariverr.fluxod.gametest;

import com.aquariverr.fluxod.FluxOverdrive;
import com.aquariverr.fluxod.miniature.model.*;
import com.aquariverr.fluxod.miniature.recipe.*;
import com.aquariverr.fluxod.miniature.storage.*;
import com.aquariverr.fluxod.network.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.*;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.gametest.*;
import java.math.BigInteger;
import java.util.*;
import static com.aquariverr.fluxod.miniature.model.GridDirection.*;
import static com.aquariverr.fluxod.network.MiniatureFactorySnapshotPayload.Result.*;

@GameTestHolder(FluxOverdrive.MODID)
@PrefixGameTestTemplate(false)
public final class MiniatureProductRoutingGameTests {
    private static final String OXIDE = "mekanism:processing/uranium/uranium_oxide";

    @GameTest(template = "empty", batch = "miniature_mekanism")
    public static void uraniumOxidizerCyclesToActualUpstreamProductWithoutIntermediateMarker(GameTestHelper h) {
        if (!ModList.get().isLoaded("mekanism")) { h.succeed(); return; }
        var storage = FactoryStorage.empty().upgrade()
                .insertMachine(p(1, 0), item("mekanism:ultimate_enriching_factory", 64), h.getLevel().registryAccess()).storage()
                .insertMachine(p(2, 0), item("mekanism:chemical_oxidizer", 32), h.getLevel().registryAccess()).storage()
                .withConfiguration(p(0, 0), marker(key(h, "mekanism:ingot_uranium"), 180, RIGHT))
                .withConfiguration(p(0, 4), marker(key(h, "mekanism:dust_sulfur"), 9, RIGHT));
        verifyOxidizer(h, storage, p(2, 0));
        // Explicit upstream selection works too, regardless of unrelated unfinished routes.
        var enriching = catalog(h).previews(storage.layout(), h.getLevel()).get(1).reference();
        verifyOxidizer(h, storage.withRecipe(p(1, 0), enriching), p(2, 0));
        h.succeed();
    }

    @GameTest(template = "empty", batch = "miniature_mekanism")
    public static void savedFissionFactoryFindsYellowCakeFromItsRealEnrichingFactory(GameTestHelper h) throws java.io.IOException {
        String file = System.getProperty("flux_overdrive.miniatureSavedFactoryFile", "");
        if (file.isEmpty() || !ModList.get().isLoaded("mekanism") || !ModList.get().isLoaded("gtceu")) { h.succeed(); return; }
        try (var input = new java.io.DataInputStream(java.nio.file.Files.newInputStream(java.nio.file.Path.of(file)))) {
            var tag = net.minecraft.nbt.NbtIo.read(input, net.minecraft.nbt.NbtAccounter.create(FactoryStateCodec.MAX_STATE_NBT_HEAP));
            var state = FactoryState.load(tag, h.getLevel().registryAccess());
            h.assertTrue(state.storage().isPresent(), "Saved factory could not load: " + state.status());
            var storage = state.storage().orElseThrow();
            h.assertTrue(storage.layout().revision() == 431 && storage.layout().machineCount() == 576, "Unexpected saved factory fixture");
            verifyOxidizer(h, storage, p(2, 0));
            h.assertTrue(state.save().equals(tag), "Read-only diagnosis modified the saved fixture");
        }
        h.succeed();
    }

    private static void verifyOxidizer(GameTestHelper h, FactoryStorage storage, GridPosition position) {
        var machine = (FactoryCell.Machine) storage.layout().cellAt(position);
        var oldSearch = catalog(h).search(machine.stack().variant(), MiniatureRecipeCatalog.markerInputs(storage.layout()), h.getLevel());
        h.assertTrue(oldSearch.candidates().stream().noneMatch(r -> r.reference().recipeId().equals(OXIDE)), "Fixture no longer reproduces missing intermediate marker");
        var preview = catalog(h).previews(storage.layout(), h.getLevel()).get(position.y() * storage.layout().size() + position.x());
        h.assertTrue(preview.reference() != null && preview.reference().recipeId().equals(OXIDE), "Upstream yellow cake did not become the preferred preview: " + preview);
        var sulfur = oldSearch.candidates().stream().filter(r -> r.reference().recipeId().equals("mekanism:oxidizing/sulfur_dioxide")).findFirst().orElseThrow();
        var wrong = storage.withRecipe(position, sulfur.reference());
        var fixture = MiniatureResourceEditingGameTests.fixture(h, wrong);
        var response = fixture.menu().handleCommand(fixture.player(), command(fixture, 1, MiniatureFactoryCommandPayload.Action.CYCLE_RECIPE, position, -1, RIGHT));
        h.assertTrue(response.result() == SUCCESS && ((FactoryCell.Machine) fixture.tile().factoryState().storage().orElseThrow().layout().cellAt(position))
                .recipe().recipeId().equals(OXIDE), "Real cycle command could not select uranium oxide");
    }

    @GameTest(template = "empty", batch = "miniature_mekanism")
    public static void downstreamCandidatesRespectEachConfiguredProductPort(GameTestHelper h) {
        if (!ModList.get().isLoaded("mekanism")) { h.succeed(); return; }
        var water = new ResourceKey(ResourceKind.FLUID, "minecraft:water");
        var hydrogen = new ResourceKey(ResourceKind.CHEMICAL, "mekanism:hydrogen");
        var storage = FactoryStorage.empty().upgrade()
                .insertMachine(p(1, 2), item("mekanism:electrolytic_separator", 1), h.getLevel().registryAccess()).storage()
                .insertMachine(p(1, 1), item("mekanism:chemical_infuser", 1), h.getLevel().registryAccess()).storage()
                .insertMachine(p(2, 2), item("mekanism:chemical_infuser", 1), h.getLevel().registryAccess()).storage()
                .withConfiguration(p(0, 2), marker(water, 1, RIGHT))
                .withConfiguration(p(0, 1), marker(new ResourceKey(ResourceKind.CHEMICAL, "mekanism:chlorine"), 1, RIGHT))
                .withConfiguration(p(2, 3), marker(new ResourceKey(ResourceKind.CHEMICAL, "mekanism:sulfur_dioxide"), 1, UP));
        var machine = (FactoryCell.Machine) storage.layout().cellAt(p(1, 2));
        var recipe = catalog(h).search(machine.stack().variant(), List.of(water), h.getLevel()).candidates().getFirst();
        storage = storage.withProductDirection(p(1, 2), recipe, hydrogen, UP);
        var previews = catalog(h).previews(storage.layout(), h.getLevel());
        h.assertTrue(previews.get(6).reference().recipeId().equals("mekanism:chemical_infusing/hydrogen_chloride")
                && previews.get(12).reference().recipeId().equals("mekanism:chemical_infusing/sulfur_trioxide"),
                "Routed candidate discovery leaked products into the wrong port");
        h.succeed();
    }

    @GameTest(template = "empty", batch = "miniature_mekanism", timeoutTicks = 100)
    public static void separatorProductsPersistAndCompileThroughIndependentPorts(GameTestHelper h) {
        if (!ModList.get().isLoaded("mekanism")) { h.succeed(); return; }
        var water = new ResourceKey(ResourceKind.FLUID, "minecraft:water");
        var hydrogen = new ResourceKey(ResourceKind.CHEMICAL, "mekanism:hydrogen");
        var oxygen = new ResourceKey(ResourceKind.CHEMICAL, "mekanism:oxygen");
        var storage = FactoryStorage.empty().insertMachine(p(1, 1), item("mekanism:electrolytic_separator", 2), h.getLevel().registryAccess()).storage()
                .withConfiguration(p(0, 1), marker(water, 999, RIGHT)).withConfiguration(p(2, 1), new FactoryCell.Arrow())
                .withConfiguration(p(1, 0), new FactoryCell.Arrow()).withConfiguration(p(2, 0), new FactoryCell.Arrow());
        var fixture = MiniatureResourceEditingGameTests.fixture(h, storage);
        var preview = fixture.menu().snapshot().recipes().get(4);
        int index = java.util.stream.IntStream.range(0, preview.outputs().size()).filter(i -> preview.outputs().get(i).resource().equals(hydrogen)).findFirst().orElseThrow();
        fixture.menu().setCarried(new ItemStack(Items.DIAMOND, 3));
        var request = command(fixture, 1, MiniatureFactoryCommandPayload.Action.SET_PRODUCT_DIRECTION, p(1, 1), index, UP);
        var response = fixture.menu().handleCommand(fixture.player(), request);
        var changed = fixture.tile().factoryState();
        var routes = Map.of(hydrogen, UP, oxygen, RIGHT);
        h.assertTrue(response.result() == SUCCESS && response.snapshot().cells().get(4).outputDirections().equals(routes)
                && changed.storage().orElseThrow().layout().revision() == storage.layout().revision() + 1
                && changed.storage().orElseThrow().machines().entries().equals(storage.machines().entries())
                && fixture.menu().getCarried().getCount() == 3, "Product direction command changed real assets or lost sibling products");
        var wire = new net.minecraft.network.RegistryFriendlyByteBuf(io.netty.buffer.Unpooled.buffer(), h.getLevel().registryAccess(),
                net.neoforged.neoforge.network.connection.ConnectionType.NEOFORGE);
        try {
            MiniatureFactorySnapshotPayload.STREAM_CODEC.encode(wire, response);
            h.assertTrue(response.equals(MiniatureFactorySnapshotPayload.STREAM_CODEC.decode(wire)), "Product routes were lost by network codec");
        } finally { wire.release(); }
        h.assertTrue(fixture.menu().handleCommand(fixture.player(), request).result() == SUCCESS && fixture.tile().factoryState() == changed, "Direction replay was applied twice");
        var invalid = command(fixture, 2, MiniatureFactoryCommandPayload.Action.SET_PRODUCT_DIRECTION, p(1, 1), FactoryLimits.MAX_RECIPE_RESOURCES - 1, DOWN);
        h.assertTrue(fixture.menu().handleCommand(fixture.player(), invalid).result() == NO_RECIPE && fixture.tile().factoryState() == changed, "Invalid output index changed the layout");
        var balanced = catalog(h).balance(changed.storage().orElseThrow().layout(), h.getLevel());
        h.assertTrue(balanced.successful() && balanced.compilation().factory().outputRows().equals(Map.of(
                0, List.of(new ResourceAmount(hydrogen, BigInteger.TWO)), 1, List.of(new ResourceAmount(oxygen, BigInteger.ONE)))),
                "Independent native products did not balance: " + balanced.compilation().diagnostic());
        var ready = changed.storage().orElseThrow().withBalancedLayout(balanced.layout());
        var saved = FactoryState.of(ready, h.getLevel().registryAccess());
        h.assertTrue(FactoryState.load(saved.save(), h.getLevel().registryAccess()).storage().orElseThrow().layout().equals(ready.layout()), "Product routing failed NBT round trip");
        var stacked = ready.insertMachine(p(1, 1), item("mekanism:electrolytic_separator", 1), h.getLevel().registryAccess()).storage()
                .extractMachine(p(1, 1), 1, h.getLevel().registryAccess()).storage();
        h.assertTrue(((FactoryCell.Machine) stacked.layout().cellAt(p(1, 1))).outputDirections().equals(routes), "Machine transfer reset product routes");
        h.assertTrue(((FactoryCell.Machine) ready.withDirection(p(1, 1), DOWN).layout().cellAt(p(1, 1))).outputDirections().equals(Map.of(hydrogen, RIGHT, oxygen, DOWN)),
                "Rotating a machine did not rotate its product ports together");
        var runtime = MiniatureRuntimeGameTests.fixture(h, ready.withInputs(new ResourceInventory(Map.of(water, BigInteger.TWO))), FactoryRules.energyPerBatch(2));
        runtime.tick();
        h.runAfterDelay(8, () -> MiniatureRuntimeGameTests.reload(h, runtime));
        h.runAfterDelay(22, () -> {
            var finalState = runtime.tile().factoryState().storage().orElseThrow();
            h.assertTrue(finalState.batch() == null && finalState.inputs().amounts().isEmpty()
                    && finalState.outputs().equals(new ResourceInventory(Map.of(hydrogen, BigInteger.TWO, oxygen, BigInteger.ONE))), "Routed products were duplicated or lost during production/reload");
            h.succeed();
        });
    }

    private static MiniatureFactoryCommandPayload command(MiniatureResourceEditingGameTests.Fixture f, long id,
            MiniatureFactoryCommandPayload.Action action, GridPosition position, int output, GridDirection direction) {
        return new MiniatureFactoryCommandPayload(f.menu().containerId, f.menu().sessionId(), f.tile().getBlockPos(), f.menu().revision(), id,
                action, position.x(), position.y(), BigInteger.ONE, direction, f.menu().snapshotId(), output);
    }
    private static GridPosition p(int x, int y) { return new GridPosition(x, y); }
    private static ItemStack item(String id, int count) { return new ItemStack(BuiltInRegistries.ITEM.get(ResourceLocation.parse(id)), count); }
    private static ResourceKey key(GameTestHelper h, String id) { return FactoryItemResources.keyOf(item(id, 1), h.getLevel().registryAccess()); }
    private static FactoryCell.Marker marker(ResourceKey key, long amount, GridDirection direction) { return new FactoryCell.Marker(new ResourceAmount(key, BigInteger.valueOf(amount)), direction); }
    private static MiniatureRecipeCatalog catalog(GameTestHelper h) { return MiniatureRecipeCatalog.get(h.getLevel().getServer()); }
}
