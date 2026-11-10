package com.aquariverr.fluxod.gametest;

import com.aquariverr.fluxod.FluxOverdrive;
import com.aquariverr.fluxod.block.entity.TileMiniatureUniverse;
import com.aquariverr.fluxod.gametest.MiniatureResourceEditingGameTests.Fixture;
import com.aquariverr.fluxod.miniature.compile.*;
import com.aquariverr.fluxod.miniature.model.*;
import com.aquariverr.fluxod.miniature.recipe.MiniatureRecipeCatalog;
import com.aquariverr.fluxod.miniature.recipe.RecipePreview;
import com.aquariverr.fluxod.miniature.storage.*;
import com.aquariverr.fluxod.network.MiniatureFactoryCommandPayload;
import com.aquariverr.fluxod.network.MiniatureFactoryCommandPayload.Action;
import com.aquariverr.fluxod.network.MiniatureFactorySnapshotPayload;
import com.aquariverr.fluxod.network.MiniatureFactorySnapshotPayload.Result;
import com.aquariverr.fluxod.register.RegistryBlocks;
import io.netty.buffer.Unpooled;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.network.connection.ConnectionType;

import java.math.BigInteger;
import java.util.List;

@GameTestHolder(FluxOverdrive.MODID)
@PrefixGameTestTemplate(false)
public final class MiniatureCompilationGameTests {
    private static final BlockPos PERSISTENCE = new BlockPos(1031, 90, 1030);

    @GameTest(template = "empty")
    public static void nativeSmeltingCompilesExactBaseRecipeAndRealMachineFees(GameTestHelper helper) {
        var compiled = compile(helper, line(helper, 2));
        helper.assertTrue(compiled.inputs().equals(List.of(amount(helper, Items.IRON_ORE, 2)))
                && compiled.outputs().equals(List.of(amount(helper, Items.IRON_INGOT, 2)))
                && compiled.energyPerTick().equals(new BigInteger("40000000"))
                && compiled.energyPerBatch().equals(new BigInteger("800000000")), "Native compiled boundary or fees differ");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void automaticDownstreamRecipeUsesRoutedIntermediateAbsentFromMarkers(GameTestHelper helper) {
        FactoryStorage storage = empty(helper, 5).insertMachine(pos(1, 2), new ItemStack(Items.CRAFTING_TABLE, 2), helper.getLevel().registryAccess()).storage()
                .insertMachine(pos(3, 2), new ItemStack(Items.CRAFTING_TABLE, 4), helper.getLevel().registryAccess()).storage()
                .withConfiguration(pos(0, 2), marker(helper, Items.OAK_LOG, 2, GridDirection.RIGHT))
                .withConfiguration(pos(2, 2), new FactoryCell.Arrow()).withConfiguration(pos(4, 2), new FactoryCell.Arrow());
        var manager = helper.getLevel().getRecipeManager();
        var original = List.copyOf(manager.getRecipes());
        try {
            manager.replaceRecipes(original.stream().filter(holder -> List.of("minecraft:oak_planks", "minecraft:stick").contains(holder.id().toString())).toList());
            catalog(helper).invalidate();
            var compiled = compile(helper, storage);
            helper.assertTrue(compiled.inputs().equals(List.of(amount(helper, Items.OAK_LOG, 2)))
                        && compiled.recipes().get(pos(3, 2)).inputs().equals(List.of(amount(helper, Items.OAK_PLANKS, 2)))
                        && compiled.outputs().equals(List.of(amount(helper, Items.STICK, 16))),
                "Downstream did not match actual unmarked upstream resources: " + compiled.outputs());
        } finally { manager.replaceRecipes(original); catalog(helper).invalidate(); }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void nativeCakeKeepsShapedSlotsAndEveryReturnedBucketInOutputRow(GameTestHelper helper) {
        var compiled = compile(helper, cake(helper));
        helper.assertTrue(compiled.recipes().get(pos(4, 2)).reference().bindings().size() == 9, "Native shaped binding lost");
        helper.assertTrue(FactoryCompiler.map(compiled.outputs()).equals(FactoryCompiler.map(List.of(amount(helper, Items.CAKE, 2), amount(helper, Items.BUCKET, 6))))
                && compiled.outputRows().get(2).equals(compiled.outputs()), "Cake or returned buckets disappeared");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void compileReplayAndOtherMenusShareOneResultWithoutChangingAssets(GameTestHelper helper) {
        Fixture fixture = MiniatureResourceEditingGameTests.fixture(helper, line(helper, 2));
        var second = MiniatureResourceEditingGameTests.playerFixture(helper, fixture.tile());
        fixture.menu().setCarried(new ItemStack(Items.DIAMOND, 3));
        FactoryState before = fixture.tile().factoryState();
        var command = command(fixture, 1);
        var first = fixture.menu().handleCommand(fixture.player(), command);
        var compiled = fixture.tile().compilationFor(before);
        helper.assertTrue(first.result() == Result.SUCCESS && first.snapshot().compilation().status() == CompilationPreview.Status.READY,
                "Compile command failed: " + first.result() + " / " + first.snapshot().compilation());
        fixture.menu().handleCommand(fixture.player(), command);
        second.menu().broadcastChanges();
        helper.assertTrue(fixture.tile().compilationFor(before) == compiled && fixture.tile().factoryState() == before
                && fixture.menu().getCarried().is(Items.DIAMOND) && fixture.menu().getCarried().getCount() == 3
                && second.menu().snapshot().compilation().equals(first.snapshot().compilation()), "Replay, compilation, or second viewer changed assets/result");
        helper.assertTrue(first.snapshot().recipes().get(1).status() == RecipePreview.Status.COMPILED, "Machine preview did not show compiled binding");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void inventoryEditsRetainCompilationButLayoutEditsInvalidateEveryViewer(GameTestHelper helper) {
        Fixture fixture = MiniatureResourceEditingGameTests.fixture(helper, line(helper, 1));
        fixture.menu().handleCommand(fixture.player(), command(fixture, 1));
        var compiled = fixture.tile().compilationFor(fixture.tile().factoryState());
        fixture.tile().insertInput(new ItemStack(Items.IRON_ORE, 3), false);
        helper.assertTrue(fixture.tile().compilationFor(fixture.tile().factoryState()) == compiled, "Inventory unnecessarily invalidated compilation");
        var before = fixture.tile().factoryState();
        fixture.tile().applyPreparedState(before, FactoryState.of(before.storage().orElseThrow()
                .withConfiguration(pos(2, 0), new FactoryCell.Arrow(GridDirection.DOWN)), helper.getLevel().registryAccess()));
        fixture.menu().broadcastChanges();
        helper.assertTrue(fixture.tile().compilationFor(fixture.tile().factoryState()) == null
                && fixture.menu().snapshot().compilation().status() == CompilationPreview.Status.NOT_COMPILED, "Edited graph retained executable preview");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void failedCompilationPreservesSelectedRecipeAndReportsExactCellResourceAndAmounts(GameTestHelper helper) {
        FactoryStorage storage = line(helper, 2);
        var reference = compile(helper, storage).recipes().get(pos(1, 0)).reference();
        storage = storage.withRecipe(pos(1, 0), reference).withConfiguration(pos(0, 0), marker(helper, Items.GOLD_ORE, 1, GridDirection.RIGHT));
        Fixture fixture = MiniatureResourceEditingGameTests.fixture(helper, storage);
        FactoryState before = fixture.tile().factoryState();
        var response = fixture.menu().handleCommand(fixture.player(), command(fixture, 1));
        var diagnostic = response.snapshot().compilation().diagnostic();
        helper.assertTrue(diagnostic.code() == FactoryDiagnostic.Code.MISSING_INPUT && diagnostic.position().orElseThrow().equals(pos(1, 0))
                && diagnostic.resource().orElseThrow().equals(key(helper, Items.IRON_ORE)) && diagnostic.expected().orElseThrow().equals(BigInteger.ONE)
                && diagnostic.actual().orElseThrow().equals(BigInteger.ZERO) && fixture.tile().factoryState() == before,
                "Compile failure lost selection, changed assets, or omitted exact context");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void staleGenerationAndRemovedSelectionsCannotReuseCompilation(GameTestHelper helper) {
        FactoryStorage storage = line(helper, 1);
        storage = storage.withRecipe(pos(1, 0), compile(helper, storage).recipes().get(pos(1, 0)).reference());
        Fixture fixture = MiniatureResourceEditingGameTests.fixture(helper, storage);
        fixture.menu().handleCommand(fixture.player(), command(fixture, 1));
        var stale = command(fixture, 2);
        var manager = helper.getLevel().getRecipeManager();
        var original = List.copyOf(manager.getRecipes());
        try {
            manager.replaceRecipes(List.of()); catalog(helper).invalidate();
            helper.assertTrue(fixture.tile().compilationFor(fixture.tile().factoryState()) == null, "Reload retained compilation");
            helper.assertTrue(fixture.menu().handleCommand(fixture.player(), stale).result() == Result.STALE_REVISION, "Old directory snapshot compiled");
            var before = fixture.tile().factoryState();
            var result = fixture.menu().handleCommand(fixture.player(), command(fixture, 3));
            helper.assertTrue(result.snapshot().compilation().diagnostic().code() == FactoryDiagnostic.Code.INVALID_RECIPE
                    && fixture.tile().factoryState() == before, "Invalid saved selection was replaced");
        } finally { manager.replaceRecipes(original); catalog(helper).invalidate(); }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void compileCommandRetainsAccessSessionAndSnapshotGuards(GameTestHelper helper) {
        Fixture fixture = MiniatureResourceEditingGameTests.fixture(helper, line(helper, 1));
        var command = command(fixture, 1);
        fixture.player().setGameMode(GameType.SPECTATOR);
        helper.assertTrue(fixture.menu().handleCommand(fixture.player(), command).result() == Result.INVALID_ACCESS, "Spectator compiled factory");
        fixture.player().setGameMode(GameType.SURVIVAL);
        fixture.tile().insertInput(new ItemStack(Items.IRON_ORE), false);
        helper.assertTrue(fixture.menu().handleCommand(fixture.player(), command).result() == Result.STALE_REVISION, "Old inventory snapshot compiled");
        helper.assertTrue(fixture.tile().compilationFor(fixture.tile().factoryState()) == null, "Rejected command cached compilation");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void compiledAndFailedPreviewsRoundTripThroughBoundedNativePayload(GameTestHelper helper) {
        for (FactoryStorage storage : List.of(line(helper, 2), cake(helper), line(helper, 1).withConfiguration(pos(2, 0), FactoryCell.Empty.INSTANCE))) {
            Fixture fixture = MiniatureResourceEditingGameTests.fixture(helper, storage);
            var expected = fixture.menu().handleCommand(fixture.player(), command(fixture, 1));
            RegistryFriendlyByteBuf buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), helper.getLevel().registryAccess(), ConnectionType.NEOFORGE);
            try {
                MiniatureFactorySnapshotPayload.STREAM_CODEC.encode(buffer, expected);
                helper.assertTrue(buffer.readableBytes() < FactoryLimits.MAX_MESSAGE_BYTES, "Preview exceeded message limit");
                helper.assertTrue(expected.equals(MiniatureFactorySnapshotPayload.STREAM_CODEC.decode(buffer)) && !buffer.isReadable(),
                        "Compiled inputs, outputs, rows, or diagnostic changed on wire");
            } finally { buffer.release(); }
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void outputColumnDiagnosticAlsoSurvivesNetworkCoordinates(GameTestHelper helper) {
        var storage = line(helper, 1).withConfiguration(pos(0, 2), marker(helper, Items.DIAMOND, 1, GridDirection.RIGHT))
                .withConfiguration(pos(1, 2), new FactoryCell.Arrow()).withConfiguration(pos(2, 2), new FactoryCell.Arrow());
        var fixture = MiniatureResourceEditingGameTests.fixture(helper, storage);
        var response = fixture.menu().handleCommand(fixture.player(), command(fixture, 1));
        helper.assertTrue(response.snapshot().compilation().diagnostic().position().orElseThrow().equals(pos(3, 2)), "Bypass error lost output column coordinate");
        RegistryFriendlyByteBuf buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), helper.getLevel().registryAccess(), ConnectionType.NEOFORGE);
        try {
            MiniatureFactorySnapshotPayload.STREAM_CODEC.encode(buffer, response);
            helper.assertTrue(response.equals(MiniatureFactorySnapshotPayload.STREAM_CODEC.decode(buffer)), "Output column error failed wire roundtrip");
        } finally { buffer.release(); }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void restartRebuildsCompilationFromSavedLayoutWithoutPersistingExecutableCache(GameTestHelper helper) {
        var level = helper.getLevel();
        level.getChunk(PERSISTENCE.getX() >> 4, PERSISTENCE.getZ() >> 4);
        boolean write = System.getProperty("flux_overdrive.miniaturePersistencePhase", "write").equals("write");
        if (write) {
            level.setBlock(PERSISTENCE, RegistryBlocks.MINIATURE_UNIVERSE.get().defaultBlockState(), 3);
            var tile = (TileMiniatureUniverse) level.getBlockEntity(PERSISTENCE);
            var state = FactoryState.of(line(helper, 2), level.registryAccess());
            helper.assertTrue(tile.applyPreparedState(tile.factoryState(), state), "Failed to prepare restart fixture");
        }
        var tile = (TileMiniatureUniverse) level.getBlockEntity(PERSISTENCE);
        helper.assertTrue(tile != null && tile.compilationFor(tile.factoryState()) == null, "Executable cache survived reload or fixture is missing");
        var compiled = catalog(helper).compile(tile.factoryState().storage().orElseThrow().layout(), level);
        helper.assertTrue(compiled.successful() && compiled.factory().outputs().equals(List.of(amount(helper, Items.IRON_INGOT, 2))), "Restored graph compiled differently");
        var before = tile.factoryState().save();
        tile.applyCompilation(tile.factoryState(), compiled, catalog(helper).generation());
        helper.assertTrue(before.equals(tile.factoryState().save()), "Compilation leaked into persistent asset format");
        // The write phase is persisted by normal GameTestServer shutdown. The next JVM must rebuild
        // compilation from that saved layout; do not synchronously flush all worlds inside this test.
        helper.succeed();
    }

    private static FactoryStorage line(GameTestHelper helper, int count) {
        return FactoryStorage.empty().insertMachine(pos(1, 0), new ItemStack(Items.FURNACE, count), helper.getLevel().registryAccess()).storage()
                .withConfiguration(pos(0, 0), marker(helper, Items.IRON_ORE, count, GridDirection.RIGHT)).withConfiguration(pos(2, 0), new FactoryCell.Arrow());
    }

    private static FactoryStorage cake(GameTestHelper helper) {
        FactoryStorage storage = empty(helper, 5).insertMachine(pos(4, 2), new ItemStack(Items.CRAFTING_TABLE, 2), helper.getLevel().registryAccess()).storage()
                .withConfiguration(pos(0, 2), marker(helper, Items.MILK_BUCKET, 6, GridDirection.RIGHT));
        Item[] ingredients = {Items.SUGAR, Items.EGG, Items.WHEAT}; int[] counts = {4, 2, 6};
        for (int x = 1; x <= 3; x++) storage = storage.withConfiguration(pos(x, 2), new FactoryCell.Arrow())
                .withConfiguration(pos(x, 1), marker(helper, ingredients[x - 1], counts[x - 1], GridDirection.DOWN));
        var search = catalog(helper).search(key(helper, Items.CRAFTING_TABLE), MiniatureRecipeCatalog.markerInputs(storage.layout()), helper.getLevel());
        var reference = search.candidates().stream().filter(recipe -> recipe.reference().recipeId().equals("minecraft:cake")).findFirst().orElseThrow().reference();
        return storage.withRecipe(pos(4, 2), reference);
    }

    private static FactoryStorage empty(GameTestHelper helper, int size) {
        return FactoryStorage.restore(FactoryLayout.empty(size), MachineInventory.empty(), ResourceInventory.empty(), ResourceInventory.empty(), helper.getLevel().registryAccess());
    }
    private static CompiledFactory compile(GameTestHelper helper, FactoryStorage storage) {
        var result = catalog(helper).compile(storage.layout(), helper.getLevel());
        helper.assertTrue(result.successful(), "Compilation failed: " + result.diagnostic()); return result.factory();
    }
    private static MiniatureFactoryCommandPayload command(Fixture fixture, long id) {
        return new MiniatureFactoryCommandPayload(fixture.menu().containerId, fixture.menu().sessionId(), fixture.tile().getBlockPos(),
                fixture.menu().revision(), id, Action.COMPILE, 0, 0, BigInteger.ONE, GridDirection.RIGHT, fixture.menu().snapshotId(), -1);
    }
    private static GridPosition pos(int x, int y) { return new GridPosition(x, y); }
    private static ResourceKey key(GameTestHelper helper, Item item) { return FactoryItemResources.keyOf(new ItemStack(item), helper.getLevel().registryAccess()); }
    private static ResourceAmount amount(GameTestHelper helper, Item item, int count) { return new ResourceAmount(key(helper, item), BigInteger.valueOf(count)); }
    private static FactoryCell.Marker marker(GameTestHelper helper, Item item, int count, GridDirection direction) { return new FactoryCell.Marker(amount(helper, item, count), direction); }
    private static MiniatureRecipeCatalog catalog(GameTestHelper helper) { return MiniatureRecipeCatalog.get(helper.getLevel().getServer()); }
}
