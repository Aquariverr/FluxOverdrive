package com.aquariverr.fluxod.gametest;

import com.aquariverr.fluxod.FluxOverdrive;
import com.aquariverr.fluxod.gametest.MiniatureResourceEditingGameTests.Fixture;
import com.aquariverr.fluxod.miniature.model.*;
import com.aquariverr.fluxod.miniature.recipe.MiniatureRecipeCatalog;
import com.aquariverr.fluxod.miniature.runtime.RunningBatch;
import com.aquariverr.fluxod.miniature.storage.*;
import com.aquariverr.fluxod.miniature.upgrade.*;
import com.aquariverr.fluxod.network.MiniatureFactoryCommandPayload;
import com.aquariverr.fluxod.network.MiniatureFactoryCommandPayload.Action;
import com.aquariverr.fluxod.network.MiniatureFactorySnapshotPayload;
import com.aquariverr.fluxod.network.MiniatureFactorySnapshotPayload.Result;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import io.netty.buffer.Unpooled;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.network.connection.ConnectionType;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@GameTestHolder(FluxOverdrive.MODID)
@PrefixGameTestTemplate(false)
public final class MiniatureUpgradeGameTests {
    @GameTest(template = "empty")
    public static void fourPaidUpgradesPreserveSelectedRecipesRoutesAndStorage(GameTestHelper helper) {
        withTestRecipes(helper, () -> {
            var fixture = fixture(helper, 3);
            var original = storage(fixture);
            fixture.player().getInventory().setItem(9, new ItemStack(Items.IRON_INGOT, 32));
            fixture.player().getInventory().setItem(10, new ItemStack(Items.DIAMOND, 4));
            fixture.menu().setCarried(new ItemStack(Items.IRON_INGOT, 17));
            for (int stage = 0; stage < 4; stage++) {
                int size = 5 + stage * 2;
                fixture.menu().broadcastChanges();
                var command = command(fixture, stage + 1);
                var result = fixture.menu().handleCommand(fixture.player(), command);
                helper.assertTrue(result.result() == Result.SUCCESS && result.snapshot().size() == size, "Upgrade failed: " + result.result());
                helper.assertTrue(storage(fixture).layout().cellAt(new GridPosition(1, 0)).equals(original.layout().cellAt(new GridPosition(1, 0)))
                        && storage(fixture).inputs().equals(original.inputs()) && storage(fixture).outputs().equals(original.outputs())
                        && storage(fixture).machines().entries().keySet().equals(original.machines().entries().keySet()), "Upgrade changed assets or recipe binding");
                var compiled = MiniatureRecipeCatalog.get(helper.getLevel().getServer()).compile(storage(fixture).layout(), helper.getLevel());
                helper.assertTrue(compiled.successful() && compiled.factory().outputRows().get(0).getFirst().amount().equals(BigInteger.TWO), "Output bridge failed");
                var saved = fixture.tile().factoryState().save();
                helper.assertTrue(FactoryState.load(saved, helper.getLevel().registryAccess()).save().equals(saved), "Upgraded state did not round trip");
                fixture.menu().handleCommand(fixture.player(), command);
                helper.assertTrue(fixture.player().getInventory().getItem(9).getCount() == 32 - 3 * (stage + 1)
                        && fixture.player().getInventory().getItem(10).getCount() == 3 - stage
                        && fixture.menu().getCarried().getCount() == 17, "Replay or upgrade charged the wrong owner/count");
            }
            var before = fixture.tile().factoryState();
            helper.assertTrue(fixture.menu().handleCommand(fixture.player(), command(fixture, 5)).result() == Result.UPGRADE_MAXIMUM
                    && fixture.tile().factoryState() == before, "Maximum size was modified");
        });
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void everyTierRejectsMissingOrConfiguredMaterialsWithoutPartialChanges(GameTestHelper helper) {
        withTestRecipes(helper, () -> {
            for (int size : List.of(3, 5, 7, 9)) {
                var fixture = fixture(helper, size);
                fixture.player().getInventory().setItem(9, new ItemStack(Items.IRON_INGOT, 64));
                ItemStack named = new ItemStack(Items.DIAMOND, 5);
                named.set(DataComponents.CUSTOM_NAME, Component.literal("Keep this asset"));
                fixture.player().getInventory().setItem(10, named);
                fixture.menu().setCarried(new ItemStack(Items.DIAMOND, 1));
                var before = fixture.tile().factoryState();
                var result = fixture.menu().handleCommand(fixture.player(), command(fixture, 1));
                helper.assertTrue(result.result() == Result.UPGRADE_MISSING_MATERIALS && fixture.tile().factoryState() == before
                        && fixture.player().getInventory().getItem(9).getCount() == 64
                        && ItemStack.matches(named, fixture.player().getInventory().getItem(10)) && fixture.menu().getCarried().getCount() == 1,
                        "Missing material partially consumed assets at " + size);
            }
        });
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void splitStacksPayExactMaterialsAndInvalidateCompilation(GameTestHelper helper) {
        withTestRecipes(helper, () -> {
            var fixture = fixture(helper, 3);
            var catalog = MiniatureRecipeCatalog.get(helper.getLevel().getServer());
            var before = fixture.tile().factoryState();
            fixture.tile().applyCompilation(before, catalog.compile(storage(fixture).layout(), helper.getLevel()), catalog.generation());
            fixture.menu().broadcastChanges();
            fixture.player().getInventory().setItem(0, new ItemStack(Items.IRON_INGOT, 1));
            fixture.player().getInventory().setItem(35, new ItemStack(Items.IRON_INGOT, 4));
            fixture.player().getInventory().setItem(11, new ItemStack(Items.DIAMOND, 2));
            helper.assertTrue(fixture.menu().handleCommand(fixture.player(), command(fixture, 1)).result() == Result.SUCCESS
                    && fixture.player().getInventory().getItem(0).isEmpty() && fixture.player().getInventory().getItem(35).getCount() == 2
                    && fixture.player().getInventory().getItem(11).getCount() == 1
                    && fixture.tile().compilationFor(fixture.tile().factoryState()) == null, "Split-stack payment or compilation invalidation failed");
        });
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void activeAndPausedBatchesRejectEveryUpgradeTier(GameTestHelper helper) {
        withTestRecipes(helper, () -> {
            for (int size : List.of(3, 5, 7, 9)) for (boolean enabled : List.of(false, true)) {
                var fixture = fixture(helper, size);
                var state = fixture.tile().factoryState();
                var environment = FactoryRules.environment(10000, 300);
                var batch = new RunningBatch(UUID.randomUUID(), "0".repeat(64), environment, 1,
                        storage(fixture).inputs(), storage(fixture).outputs(), FactoryRules.energyPerTick(2), 1);
                var locked = storage(fixture).withBatch(batch, helper.getLevel().getGameTime()).withControls(enabled, storage(fixture).redstoneMode());
                var wrapper = new net.minecraft.nbt.CompoundTag();
                wrapper.put(com.aquariverr.fluxod.block.entity.TileMiniatureUniverse.STATE_TAG, FactoryState.of(locked, helper.getLevel().registryAccess()).save());
                fixture.tile().loadAdditional(wrapper, helper.getLevel().registryAccess());
                helper.assertTrue(storage(fixture).batch() != null, "Saved batch fixture did not load");
                fixture.menu().broadcastChanges();
                fund(fixture);
                var before = fixture.tile().factoryState();
                helper.assertTrue(fixture.menu().handleCommand(fixture.player(), command(fixture, 1)).result() == Result.BATCH_LOCKED
                        && fixture.tile().factoryState() == before && fixture.player().getInventory().getItem(9).getCount() == 8,
                        "Locked batch upgraded at " + size);
            }
        });
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void competingPlayersAndExpiredRecipeViewsCannotPayTwice(GameTestHelper helper) {
        withTestRecipes(helper, () -> {
            var first = fixture(helper, 3);
            var second = MiniatureResourceEditingGameTests.playerFixture(helper, first.tile());
            fund(first); fund(second);
            var stale = command(second, 1);
            helper.assertTrue(first.menu().handleCommand(first.player(), command(first, 1)).result() == Result.SUCCESS, "First upgrade failed");
            helper.assertTrue(second.menu().handleCommand(second.player(), stale).result() == Result.STALE_REVISION
                    && second.player().getInventory().getItem(9).getCount() == 8, "Competing player was charged");
            second.menu().broadcastChanges();
            var oldRecipeView = command(second, 2);
            MiniatureRecipeCatalog.get(helper.getLevel().getServer()).invalidate();
            helper.assertTrue(second.menu().handleCommand(second.player(), oldRecipeView).result() == Result.STALE_REVISION
                    && second.player().getInventory().getItem(9).getCount() == 8 && storage(first).layout().size() == 5, "Reloaded view was accepted");
        });
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void missingOrAmbiguousRecipesDisableUpgradeWithoutCharging(GameTestHelper helper) {
        var manager = helper.getLevel().getRecipeManager();
        var original = List.copyOf(manager.getRecipes());
        try {
            for (int count : List.of(0, 2)) {
                var recipes = new ArrayList<RecipeHolder<?>>(original.stream().filter(holder -> holder.value().getType() != MiniatureUpgradeRecipe.TYPE).toList());
                for (int i = 0; i < count; i++) recipes.add(testRecipe(helper, 3, "duplicate_" + i));
                manager.replaceRecipes(recipes); MiniatureRecipeCatalog.get(helper.getLevel().getServer()).invalidate();
                var fixture = fixture(helper, 3); fund(fixture);
                var before = fixture.tile().factoryState();
                helper.assertTrue(fixture.menu().handleCommand(fixture.player(), command(fixture, 1)).result()
                        == (count == 0 ? Result.UPGRADE_NO_RECIPE : Result.UPGRADE_AMBIGUOUS)
                        && fixture.tile().factoryState() == before && fixture.player().getInventory().getItem(9).getCount() == 8, "Invalid recipe set charged assets");
            }
        } finally { manager.replaceRecipes(original); MiniatureRecipeCatalog.get(helper.getLevel().getServer()).invalidate(); }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void releaseRecipesAndUpgradeProtocolUseRegisteredSerializers(GameTestHelper helper) {
        var recipes = helper.getLevel().getRecipeManager().getAllRecipesFor(MiniatureUpgradeRecipe.TYPE);
        helper.assertTrue(recipes.size() == 4, "Expected four release upgrades");
        for (var holder : recipes) {
            var recipe = holder.value();
            RegistryFriendlyByteBuf buffer = buffer(helper);
            try {
                MiniatureUpgradeRecipe.STREAM_CODEC.encode(buffer, recipe);
                helper.assertTrue(MiniatureUpgradeRecipe.STREAM_CODEC.decode(buffer).equals(recipe) && !buffer.isReadable(), "Upgrade serializer mismatch");
            } finally { buffer.release(); }
        }
        var fixture = fixture(helper, 3);
        var command = command(fixture, 1);
        var response = fixture.menu().handleCommand(fixture.player(), command);
        var buffer = buffer(helper);
        try {
            MiniatureFactoryCommandPayload.STREAM_CODEC.encode(buffer, command);
            helper.assertTrue(MiniatureFactoryCommandPayload.STREAM_CODEC.decode(buffer).equals(command), "Upgrade command codec mismatch");
            buffer.clear();
            MiniatureFactorySnapshotPayload.STREAM_CODEC.encode(buffer, response);
            helper.assertTrue(MiniatureFactorySnapshotPayload.STREAM_CODEC.decode(buffer).snapshot().upgrade().equals(response.snapshot().upgrade()), "Material preview codec mismatch");
        } finally { buffer.release(); }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void exhaustedRevisionRejectsBeforeAnyMaterialCommit(GameTestHelper helper) {
        withTestRecipes(helper, () -> {
            var normal = lineStorage(helper, 3);
            var exhausted = FactoryStorage.restore(new FactoryLayout(3, normal.layout().cells(), Long.MAX_VALUE), normal.machines(),
                    normal.inputs(), normal.outputs(), helper.getLevel().registryAccess());
            var fixture = MiniatureResourceEditingGameTests.fixture(helper, exhausted); fund(fixture);
            var before = fixture.tile().factoryState();
            helper.assertTrue(fixture.menu().handleCommand(fixture.player(), command(fixture, 1)).result() == Result.REVISION_EXHAUSTED
                    && fixture.tile().factoryState() == before && fixture.player().getInventory().getItem(9).getCount() == 8
                    && fixture.player().getInventory().getItem(10).getCount() == 2, "Exhausted revision consumed materials");
        });
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void upgradedFactoryBreakAndPlacementPreserveOneCompleteItem(GameTestHelper helper) {
        var fixture = fixture(helper, 11);
        var expected = fixture.tile().factoryState().save();
        var pos = fixture.tile().getBlockPos();
        var area = new net.minecraft.world.phys.AABB(pos).inflate(1);
        helper.assertTrue(helper.getLevel().getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class, area).isEmpty(), "Unrelated drops in fixture");
        helper.assertTrue(helper.getLevel().destroyBlock(pos, true), "Destruction failed");
        var drops = helper.getLevel().getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class, area);
        helper.assertTrue(drops.size() == 1, "Upgraded factory scattered assets");
        var item = drops.getFirst().getItem().copy(); drops.getFirst().discard();
        helper.assertTrue(item.getCount() == 1 && item.is(com.aquariverr.fluxod.register.RegistryBlocks.MINIATURE_UNIVERSE.get().asItem())
                && expected.equals(item.get(com.aquariverr.fluxod.register.FluxOdDataComponents.MINIATURE_UNIVERSE_STATE).copyTag()), "Upgraded item lost state");
        var placed = ((net.minecraft.world.item.BlockItem) item.getItem()).place(new net.minecraft.world.item.context.DirectionalPlaceContext(
                helper.getLevel(), pos, net.minecraft.core.Direction.DOWN, item, net.minecraft.core.Direction.UP));
        var restored = (com.aquariverr.fluxod.block.entity.TileMiniatureUniverse) helper.getLevel().getBlockEntity(pos);
        helper.assertTrue(placed.consumesAction() && item.isEmpty() && restored != null && expected.equals(restored.factoryState().save()), "Upgraded placement lost state or duplicated item");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void actualRestartPreservesPaidElevenByElevenFactory(GameTestHelper helper) {
        var pos = new net.minecraft.core.BlockPos(2300, 90, 2300);
        var level = helper.getLevel();
        level.getChunk(pos.getX() >> 4, pos.getZ() >> 4);
        if (System.getProperty("flux_overdrive.miniaturePersistencePhase", "write").equals("write")) {
            level.setBlock(pos, com.aquariverr.fluxod.register.RegistryBlocks.MINIATURE_UNIVERSE.get().defaultBlockState(), 3);
            var tile = (com.aquariverr.fluxod.block.entity.TileMiniatureUniverse) level.getBlockEntity(pos);
            var wrapper = new net.minecraft.nbt.CompoundTag();
            wrapper.put(com.aquariverr.fluxod.block.entity.TileMiniatureUniverse.STATE_TAG, FactoryState.of(lineStorage(helper, 3), level.registryAccess()).save());
            tile.loadAdditional(wrapper, level.registryAccess());
            var f = MiniatureResourceEditingGameTests.playerFixture(helper, tile);
            withTestRecipes(helper, () -> {
                for (int stage = 0; stage < 4; stage++) {
                    fund(f); f.menu().broadcastChanges();
                    helper.assertTrue(f.menu().handleCommand(f.player(), command(f, stage + 1)).result() == Result.SUCCESS, "Persistence upgrade failed");
                }
            });
            tile.setChanged();
        }
        helper.assertTrue(level.getBlockEntity(pos) instanceof com.aquariverr.fluxod.block.entity.TileMiniatureUniverse, "Restart lost upgraded factory");
        var tile = (com.aquariverr.fluxod.block.entity.TileMiniatureUniverse) level.getBlockEntity(pos);
        helper.assertTrue(tile.factoryState().save().equals(FactoryState.of(lineStorage(helper, 11), level.registryAccess()).save()), "Restart changed paid upgrades, machines, recipe or inventories");
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "s11_upgrade_reload", timeoutTicks = 1800)
    public static void actualDatapackOverrideRefreshesCostsAndRejectsOldView(GameTestHelper helper) throws java.io.IOException {
        var server = helper.getLevel().getServer();
        var original = new ArrayList<>(server.getPackRepository().getSelectedIds());
        original.remove("file/s11-upgrade-fixture");
        var root = server.getWorldPath(net.minecraft.world.level.storage.LevelResource.DATAPACK_DIR).resolve("s11-upgrade-fixture");
        java.nio.file.Files.createDirectories(root.resolve("data/flux_overdrive/recipe/miniature_upgrade"));
        java.nio.file.Files.writeString(root.resolve("pack.mcmeta"), "{\"pack\":{\"pack_format\":48,\"description\":\"S11 isolated upgrade test costs\"}}");
        java.nio.file.Files.writeString(root.resolve("data/flux_overdrive/recipe/miniature_upgrade/3_to_5.json"), testJson(3));
        var f = fixture(helper, 3); fund(f);
        var stale = command(f, 1);
        server.getPackRepository().reload();
        var selected = new ArrayList<>(original); selected.add("file/s11-upgrade-fixture");
        var loaded = server.reloadResources(selected);
        java.util.concurrent.atomic.AtomicReference<java.util.concurrent.CompletableFuture<Void>> restored = new java.util.concurrent.atomic.AtomicReference<>();
        helper.startSequence().thenWaitUntil(() -> helper.assertTrue(loaded.isDone(), "Waiting for test datapack"))
                .thenExecute(() -> {
                    loaded.join();
                    try {
                        helper.assertTrue(f.menu().handleCommand(f.player(), stale).result() == Result.STALE_REVISION, "Old material view accepted after reload");
                        f.menu().broadcastChanges();
                        helper.assertTrue(f.menu().snapshot().upgrade().materials().size() == 2, "Override costs not synchronized");
                        helper.assertTrue(f.menu().handleCommand(f.player(), command(f, 2)).result() == Result.SUCCESS
                                && f.player().getInventory().getItem(9).getCount() == 5 && f.player().getInventory().getItem(10).getCount() == 1,
                                "Reloaded datapack did not control atomic payment");
                    } finally { restored.set(server.reloadResources(original)); }
                }).thenWaitUntil(() -> helper.assertTrue(restored.get() != null && restored.get().isDone(), "Waiting for release datapacks"))
                .thenExecute(() -> {
                    restored.get().join();
                    helper.assertTrue(FactoryUpgrading.preview(helper.getLevel(), 3).materials().size() == 3, "Release materials were not restored");
                }).thenIdle(60).thenWaitUntil(() -> helper.assertTrue(MiniatureLifecycleGameTests.chunksReadyForShutdown(helper), "Waiting for save dependencies")).thenSucceed();
    }

    private static Fixture fixture(GameTestHelper helper, int size) {
        return MiniatureResourceEditingGameTests.fixture(helper, lineStorage(helper, size));
    }
    private static FactoryStorage lineStorage(GameTestHelper helper, int size) {
        var registries = helper.getLevel().registryAccess();
        var ore = FactoryItemResources.keyOf(new ItemStack(Items.IRON_ORE), registries);
        var ingot = FactoryItemResources.keyOf(new ItemStack(Items.IRON_INGOT), registries);
        var storage = FactoryStorage.empty().insertMachine(new GridPosition(1, 0), new ItemStack(Items.FURNACE, 2), registries).storage()
                .withConfiguration(new GridPosition(0, 0), new FactoryCell.Marker(new ResourceAmount(ore, BigInteger.TWO), GridDirection.RIGHT))
                .withConfiguration(new GridPosition(2, 0), new FactoryCell.Arrow());
        var compiled = MiniatureRecipeCatalog.get(helper.getLevel().getServer()).compile(storage.layout(), helper.getLevel());
        storage = storage.withRecipe(new GridPosition(1, 0), compiled.factory().recipes().get(new GridPosition(1, 0)).reference())
                .withInputs(new ResourceInventory(Map.of(ore, BigInteger.valueOf(41))))
                .withOutputs(new ResourceInventory(Map.of(ingot, BigInteger.valueOf(7))));
        while (storage.layout().size() < size) storage = storage.upgrade();
        return storage;
    }
    private static FactoryStorage storage(Fixture f) { return f.tile().factoryState().storage().orElseThrow(); }
    private static void fund(Fixture f) { f.player().getInventory().setItem(9, new ItemStack(Items.IRON_INGOT, 8)); f.player().getInventory().setItem(10, new ItemStack(Items.DIAMOND, 2)); }
    private static MiniatureFactoryCommandPayload command(Fixture f, long id) {
        return new MiniatureFactoryCommandPayload(f.menu().containerId, f.menu().sessionId(), f.tile().getBlockPos(), f.menu().revision(), id,
                Action.UPGRADE, 0, 0, BigInteger.ONE, GridDirection.RIGHT, f.menu().snapshotId(), -1);
    }
    private static RegistryFriendlyByteBuf buffer(GameTestHelper h) { return new RegistryFriendlyByteBuf(Unpooled.buffer(), h.getLevel().registryAccess(), ConnectionType.NEOFORGE); }
    private static RecipeHolder<?> testRecipe(GameTestHelper helper, int from, String name) {
        return new RecipeHolder<>(ResourceLocation.fromNamespaceAndPath("flux_overdrive", "test/" + name),
                Recipe.CODEC.parse(helper.getLevel().registryAccess().createSerializationContext(JsonOps.INSTANCE), JsonParser.parseString(testJson(from))).getOrThrow());
    }
    private static String testJson(int from) {
        return "{\"type\":\"flux_overdrive:miniature_upgrade\",\"from_size\":" + from + ",\"to_size\":" + (from + 2)
                + ",\"materials\":[{\"item\":\"minecraft:iron_ingot\",\"count\":3},{\"item\":\"minecraft:diamond\",\"count\":1}]}";
    }
    private static void withTestRecipes(GameTestHelper helper, Runnable task) {
        var manager = helper.getLevel().getRecipeManager();
        var original = List.copyOf(manager.getRecipes());
        var test = new ArrayList<RecipeHolder<?>>(original.stream().filter(holder -> holder.value().getType() != MiniatureUpgradeRecipe.TYPE).toList());
        for (int from : List.of(3, 5, 7, 9)) test.add(testRecipe(helper, from, "upgrade_" + from));
        try { manager.replaceRecipes(test); MiniatureRecipeCatalog.get(helper.getLevel().getServer()).invalidate(); task.run(); }
        finally { manager.replaceRecipes(original); MiniatureRecipeCatalog.get(helper.getLevel().getServer()).invalidate(); }
    }
}
