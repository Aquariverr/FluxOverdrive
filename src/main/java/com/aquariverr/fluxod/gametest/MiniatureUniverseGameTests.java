package com.aquariverr.fluxod.gametest;

import com.aquariverr.fluxod.FluxOverdrive;
import com.aquariverr.fluxod.block.MiniatureUniverseBlock;
import com.aquariverr.fluxod.block.entity.TileMiniatureUniverse;
import com.aquariverr.fluxod.miniature.model.GridPosition;
import com.aquariverr.fluxod.miniature.storage.FactoryItemResources;
import com.aquariverr.fluxod.miniature.storage.FactoryState;
import com.aquariverr.fluxod.miniature.storage.FactoryStateCodec;
import com.aquariverr.fluxod.miniature.storage.FactoryStorage;
import com.aquariverr.fluxod.miniature.storage.ResourceInventory;
import com.aquariverr.fluxod.register.FluxOdDataComponents;
import com.aquariverr.fluxod.register.RegistryBlockEntityTypes;
import com.aquariverr.fluxod.register.RegistryBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.context.DirectionalPlaceContext;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.math.BigInteger;
import java.util.List;
import java.util.Map;

@GameTestHolder(FluxOverdrive.MODID)
@PrefixGameTestTemplate(false)
public final class MiniatureUniverseGameTests {

    private static final BlockPos TEST_POS = new BlockPos(2, 2, 2);
    private static final BlockPos PERSISTENCE_POS = new BlockPos(1024, 90, 1024);
    private static final GridPosition MACHINE_POS = new GridPosition(0, 0);

    @GameTest(template = "empty")
    public static void tierThreePluginRegistersWithoutProductionTicker(GameTestHelper helper) {
        MiniatureUniverseBlock block = RegistryBlocks.MINIATURE_UNIVERSE.get();
        helper.assertTrue(block.getPluginTier() == 3 && block.canInstallInRing(3),
                "Miniature universe must be a Tier III battery plugin");
        for (int tier : List.of(-1, 0, 1, 2, 4)) {
            helper.assertTrue(!block.canInstallInRing(tier),
                    "Miniature universe accepted an invalid ring tier: " + tier);
        }
        helper.assertTrue(block.getTicker(helper.getLevel(), block.defaultBlockState(),
                        RegistryBlockEntityTypes.MINIATURE_UNIVERSE.get()) == null,
                "S02 must not register an independent production ticker");
        helper.setBlock(TEST_POS, block.defaultBlockState());
        TileMiniatureUniverse tile = requireTile(helper, helper.absolutePos(TEST_POS));
        helper.assertTrue(tile.getType() == RegistryBlockEntityTypes.MINIATURE_UNIVERSE.get(),
                "Miniature universe did not create its registered block entity");
        helper.assertTrue(tile.factoryState().status() == FactoryState.Status.READY,
                "Fresh S02 factory does not report unavailable production");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void ordinaryBreakAndPlacementPreserveOneCompleteAsset(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos pos = helper.absolutePos(TEST_POS);
        TileMiniatureUniverse tile = placeFixture(helper, pos);
        CompoundTag expected = tile.factoryState().save();
        AABB dropArea = new AABB(pos).inflate(2.0);
        helper.assertTrue(level.getEntitiesOfClass(ItemEntity.class, dropArea).isEmpty(),
                "Break test started with unrelated item entities");
        helper.assertTrue(level.destroyBlock(pos, true), "Normal block destruction failed");
        helper.assertTrue(level.getBlockEntity(pos) == null,
                "Destroyed factory still retains a world block entity");
        List<ItemEntity> dropped = level.getEntitiesOfClass(ItemEntity.class, dropArea);
        helper.assertTrue(dropped.size() == 1,
                "Breaking the factory must drop exactly one plugin and no internal assets");
        ItemEntity entity = dropped.getFirst();
        ItemStack stack = entity.getItem().copy();
        helper.assertTrue(stack.is(RegistryBlocks.MINIATURE_UNIVERSE.get().asItem()) && stack.getCount() == 1,
                "Factory drop is not exactly one miniature universe item");
        CustomData component = stack.get(FluxOdDataComponents.MINIATURE_UNIVERSE_STATE);
        helper.assertTrue(component != null && expected.equals(component.copyTag()),
                "Factory loot lost or changed its complete state component");
        helper.assertTrue(!stack.has(DataComponents.CONTAINER),
                "Factory loot must not separately expose internal assets as a vanilla container");
        entity.discard();
        helper.assertTrue(stack.getItem() instanceof BlockItem, "Factory drop is not placeable");
        var result = ((BlockItem) stack.getItem()).place(
                new DirectionalPlaceContext(level, pos, Direction.DOWN, stack, Direction.UP));
        helper.assertTrue(result.consumesAction() && stack.isEmpty(),
                "Successful factory placement must consume its single source item");
        TileMiniatureUniverse restored = requireTile(helper, pos);
        helper.assertTrue(expected.equals(restored.factoryState().save()),
                "Ordinary BlockItem placement did not restore the complete factory state");
        assertFixture(helper, restored);
        helper.assertTrue(level.getEntitiesOfClass(ItemEntity.class, dropArea).isEmpty(),
                "Break and placement left duplicated or scattered internal assets");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void blockEntitySurvivesStrictNbtAndRegistryReload(GameTestHelper helper) throws IOException {
        TileMiniatureUniverse tile = placeFixture(helper, helper.absolutePos(TEST_POS));
        CompoundTag saved = tile.saveWithFullMetadata(helper.getLevel().registryAccess());
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        NbtIo.writeUnnamedTag(saved, new DataOutputStream(bytes));
        ByteArrayInputStream input = new ByteArrayInputStream(bytes.toByteArray());
        CompoundTag decoded = NbtIo.read(new DataInputStream(input), NbtAccounter.unlimitedHeap());
        helper.assertTrue(input.available() == 0 && saved.equals(decoded),
                "Strict block entity NBT encoding did not preserve all saved data");
        BlockEntity loaded = BlockEntity.loadStatic(tile.getBlockPos(), tile.getBlockState(), decoded,
                helper.getLevel().registryAccess());
        helper.assertTrue(loaded instanceof TileMiniatureUniverse,
                "Registry-based block entity reload did not restore the miniature universe type");
        TileMiniatureUniverse restored = (TileMiniatureUniverse) loaded;
        restored.setLevel(helper.getLevel());
        helper.assertTrue(saved.equals(restored.saveWithFullMetadata(helper.getLevel().registryAccess())),
                "Reload changed full block entity metadata or factory assets");
        assertFixture(helper, restored);
        ItemStack extracted = restored.extractMachine(MACHINE_POS, 64, true);
        helper.assertTrue(extracted.getCount() == 1
                        && ItemStack.isSameItemSameComponents(extracted, fixtureMachine()),
                "Restored machine is not extractable with its original natural stack limit and components");
        helper.assertTrue(saved.equals(restored.saveWithFullMetadata(helper.getLevel().registryAccess())),
                "Simulated interaction changed reloaded block entity data");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void unknownAndRejectedStatesKeepDataAndRejectAssetTransfers(GameTestHelper helper) {
        TileMiniatureUniverse tile = placeFixture(helper, helper.absolutePos(TEST_POS));
        CompoundTag valid = tile.factoryState().save();
        CompoundTag unknownFormat = valid.copy();
        unknownFormat.putString("format", "flux_overdrive:future_miniature_factory");
        assertUnavailable(helper, tile, unknownFormat, FactoryState.Status.UNKNOWN_FORMAT);
        CompoundTag unknownVersion = valid.copy();
        unknownVersion.putInt("schemaVersion", FactoryStateCodec.SCHEMA_VERSION + 1);
        assertUnavailable(helper, tile, unknownVersion, FactoryState.Status.UNKNOWN_FORMAT);
        CompoundTag malformed = valid.copy();
        malformed.putInt("gridSize", 4);
        assertUnavailable(helper, tile, malformed, FactoryState.Status.INVALID_STATE);
        CompoundTag legacy = new CompoundTag();
        legacy.putString("miniature_universe_data", "legacy machine and inventory payload");
        legacy.putInt("GridSize", 3);
        legacy.putInt("MachineCount", 64);
        tile.loadAdditional(legacy, helper.getLevel().registryAccess());
        helper.assertTrue(legacy.equals(tile.factoryState().save()),
                "Legacy block entity data was not preserved for diagnosis");
        assertUnavailable(helper, tile, legacy, FactoryState.Status.INVALID_STATE);
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void simulationAndRejectedTransfersDoNotMutateAssetsOrRevision(GameTestHelper helper) {
        TileMiniatureUniverse tile = placeFixture(helper, helper.absolutePos(TEST_POS));
        CompoundTag before = tile.factoryState().save();
        var registries = helper.getLevel().registryAccess();
        var ore = FactoryItemResources.keyOf(new ItemStack(Items.IRON_ORE), registries);
        var ingot = FactoryItemResources.keyOf(new ItemStack(Items.IRON_INGOT), registries);
        ItemStack offered = fixtureMachine();
        helper.assertTrue(tile.insertMachine(MACHINE_POS, offered, true).isEmpty(),
                "Simulation rejected a valid same-variant machine");
        helper.assertTrue(offered.getCount() == 1, "Simulation changed the offered machine stack");
        helper.assertTrue(tile.extractMachine(MACHINE_POS, 64, true).getCount() == 1,
                "Simulated machine extraction ignored the natural stack limit");
        helper.assertTrue(tile.insertInput(new ItemStack(Items.IRON_ORE, 5), true).isEmpty(),
                "Simulation rejected valid input items");
        helper.assertTrue(tile.extractInput(ore, 64, true).getCount() == 47,
                "Simulated input extraction returned the wrong quantity");
        helper.assertTrue(tile.extractOutput(ingot, 64, true).getCount() == 25,
                "Simulated output extraction returned the wrong quantity");
        assertSameState(helper, tile, before, "Simulation changed assets or layout revision");
        assertRejected(helper, () -> tile.insertMachine(MACHINE_POS, new ItemStack(Items.STONE), false));
        assertRejected(helper, () -> tile.insertMachine(new GridPosition(3, 0), fixtureMachine(), false));
        assertRejected(helper, () -> tile.extractMachine(MACHINE_POS, 0, false));
        assertRejected(helper, () -> tile.extractInput(ore, 0, false));
        assertRejected(helper, () -> tile.extractOutput(ingot, -1, false));
        assertSameState(helper, tile, before, "Rejected transfer changed assets or layout revision");
        ItemStack extracted = tile.extractMachine(MACHINE_POS, 64, false);
        helper.assertTrue(extracted.getCount() == 1
                        && ItemStack.isSameItemSameComponents(extracted, fixtureMachine()),
                "Committed machine extraction must return one intact naturally unstackable machine");
        helper.assertTrue(tile.factoryState().storage().orElseThrow().machines()
                        .entry(MACHINE_POS).orElseThrow().count() == 2,
                "Committed extraction cleared the remaining machines");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 60)
    public static void storageStageNeverProducesResources(GameTestHelper helper) {
        TileMiniatureUniverse tile = placeFixture(helper, helper.absolutePos(TEST_POS));
        CompoundTag before = tile.factoryState().save();
        helper.runAfterDelay(30, () -> {
            assertSameState(helper, tile, before, "S02 changed machine or resource assets over time");
            helper.assertTrue(tile.factoryState().status() == FactoryState.Status.READY,
                    "S02 factory started claiming executable production");
            helper.succeed();
        });
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void actualRestartPreservesFactory(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        String phase = System.getProperty("flux_overdrive.miniaturePersistencePhase", "write");
        helper.assertTrue(phase.equals("write") || phase.equals("read"),
                "Unknown miniature persistence test phase: " + phase);
        level.getChunk(PERSISTENCE_POS.getX() >> 4, PERSISTENCE_POS.getZ() >> 4);
        if (phase.equals("write")) {
            TileMiniatureUniverse tile = placeFixture(helper, PERSISTENCE_POS);
            assertFixture(helper, tile);
            // GameTestServer saves dirty chunks during normal shutdown. Flushing every world from
            // inside a running test can block chunk unload work; only the next JVM verifies durability.
        } else {
            // The read phase only loads the existing chunk; it must never rebuild the fixture.
            TileMiniatureUniverse tile = requireTile(helper, PERSISTENCE_POS);
            assertFixture(helper, tile);
            CompoundTag expected = FactoryStateCodec.save(fixtureStorage(helper), level.registryAccess());
            assertSameState(helper, tile, expected,
                    "The independently restarted server did not restore exact factory state");
        }
        helper.succeed();
    }

    private static TileMiniatureUniverse placeFixture(GameTestHelper helper, BlockPos pos) {
        helper.getLevel().setBlock(pos, RegistryBlocks.MINIATURE_UNIVERSE.get().defaultBlockState(), 3);
        TileMiniatureUniverse tile = requireTile(helper, pos);
        CompoundTag state = new CompoundTag();
        state.put(TileMiniatureUniverse.STATE_TAG,
                FactoryStateCodec.save(fixtureStorage(helper), helper.getLevel().registryAccess()));
        tile.loadAdditional(state, helper.getLevel().registryAccess());
        tile.setChanged();
        return tile;
    }

    private static FactoryStorage fixtureStorage(GameTestHelper helper) {
        var registries = helper.getLevel().registryAccess();
        FactoryStorage storage = FactoryStorage.empty();
        for (int count = 0; count < 3; count++) {
            FactoryStorage.Insertion inserted = storage.insertMachine(MACHINE_POS, fixtureMachine(), registries);
            helper.assertTrue(inserted.remainder().isEmpty(), "Could not insert fixture machine");
            storage = inserted.storage();
        }
        storage = storage.insertInput(new ItemStack(Items.IRON_ORE, 47), registries).storage();
        return storage.withOutputs(new ResourceInventory(Map.of(
                FactoryItemResources.keyOf(new ItemStack(Items.IRON_INGOT), registries), BigInteger.valueOf(25))));
    }

    private static ItemStack fixtureMachine() {
        ItemStack machine = new ItemStack(Items.FURNACE);
        machine.set(DataComponents.MAX_STACK_SIZE, 1);
        machine.set(DataComponents.CUSTOM_NAME, Component.literal("S02 persistent furnace"));
        return machine;
    }

    private static TileMiniatureUniverse requireTile(GameTestHelper helper, BlockPos pos) {
        BlockEntity tile = helper.getLevel().getBlockEntity(pos);
        helper.assertTrue(tile instanceof TileMiniatureUniverse,
                "Expected a miniature universe block entity at " + pos);
        return (TileMiniatureUniverse) tile;
    }

    private static void assertFixture(GameTestHelper helper, TileMiniatureUniverse tile) {
        helper.assertTrue(tile.factoryState().status() == FactoryState.Status.READY,
                "Saved fixture is not readable or is incorrectly executable");
        FactoryStorage storage = tile.factoryState().storage().orElseThrow();
        var machine = storage.machines().entry(MACHINE_POS).orElseThrow();
        helper.assertTrue(storage.layout().size() == 3 && storage.layout().revision() == 3,
                "Fixture layout size or revision changed");
        helper.assertTrue(storage.machines().entries().size() == 1 && machine.count() == 3,
                "Fixture did not preserve three machines in one cell");
        helper.assertTrue(machine.prototype().getCount() == 1 && machine.prototype().getMaxStackSize() == 1
                        && ItemStack.isSameItemSameComponents(machine.prototype(), fixtureMachine()),
                "Fixture machine identity, components or natural stack size changed");
        var registries = helper.getLevel().registryAccess();
        helper.assertTrue(storage.inputs().amounts().size() == 1 && storage.inputs().amount(
                        FactoryItemResources.keyOf(new ItemStack(Items.IRON_ORE), registries))
                        .equals(BigInteger.valueOf(47)),
                "Fixture input inventory did not preserve 47 iron ore");
        helper.assertTrue(storage.outputs().amounts().size() == 1 && storage.outputs().amount(
                        FactoryItemResources.keyOf(new ItemStack(Items.IRON_INGOT), registries))
                        .equals(BigInteger.valueOf(25)),
                "Fixture output inventory did not preserve 25 iron ingots");
    }

    private static void assertUnavailable(GameTestHelper helper, TileMiniatureUniverse tile,
                                           CompoundTag saved, FactoryState.Status status) {
        CompoundTag wrapper = new CompoundTag();
        wrapper.put(TileMiniatureUniverse.STATE_TAG, saved.copy());
        tile.loadAdditional(wrapper, helper.getLevel().registryAccess());
        helper.assertTrue(tile.factoryState().status() == status && tile.factoryState().storage().isEmpty(),
                "Unsupported state was accepted as a valid new factory");
        assertSameState(helper, tile, saved, "Rejected state data was changed or discarded");
        assertRejected(helper, () -> tile.insertMachine(MACHINE_POS, fixtureMachine(), false));
        assertRejected(helper, () -> tile.extractMachine(MACHINE_POS, 1, false));
        assertRejected(helper, () -> tile.insertInput(new ItemStack(Items.IRON_ORE), false));
        var ore = FactoryItemResources.keyOf(new ItemStack(Items.IRON_ORE), helper.getLevel().registryAccess());
        assertRejected(helper, () -> tile.extractInput(ore, 1, false));
        assertRejected(helper, () -> tile.extractOutput(ore, 1, false));
        assertSameState(helper, tile, saved, "Attempted transfers changed unreadable saved state");
        CustomData component = tile.collectComponents().get(FluxOdDataComponents.MINIATURE_UNIVERSE_STATE);
        helper.assertTrue(component != null && saved.equals(component.copyTag()),
                "Unreadable state would not survive the plugin item component path");
    }

    private static void assertSameState(GameTestHelper helper, TileMiniatureUniverse tile,
                                        CompoundTag expected, String message) {
        helper.assertTrue(expected.equals(tile.factoryState().save()), message);
    }

    private static void assertRejected(GameTestHelper helper, Runnable transfer) {
        boolean rejected = false;
        try {
            transfer.run();
        } catch (RuntimeException expected) {
            rejected = true;
        }
        helper.assertTrue(rejected, "Invalid or unavailable asset transfer was accepted");
    }

    private MiniatureUniverseGameTests() {}
}
