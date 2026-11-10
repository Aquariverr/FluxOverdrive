package com.aquariverr.fluxod.gametest;

import com.aquariverr.fluxod.Config;
import com.aquariverr.fluxod.FluxOverdrive;
import com.aquariverr.fluxod.miniature.model.*;
import com.aquariverr.fluxod.miniature.runtime.*;
import com.aquariverr.fluxod.miniature.storage.*;
import com.aquariverr.fluxod.network.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.item.*;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction;
import net.neoforged.neoforge.gametest.*;

import java.math.BigInteger;
import java.util.Map;
import java.util.UUID;

@GameTestHolder(FluxOverdrive.MODID)
@PrefixGameTestTemplate(false)
public final class MiniatureInputCapacityGameTests {
    @GameTest(template = "empty")
    public static void menuCapacityIsLocalSynchronizedReplaySafeAndSaved(GameTestHelper h) {
        var ore = FactoryItemResources.keyOf(new ItemStack(Items.IRON_ORE), h.getLevel().registryAccess());
        var f = MiniatureResourceEditingGameTests.fixture(h, FactoryStorage.empty().withInputs(new ResourceInventory(Map.of(ore, BigInteger.ONE))));
        var peer = MiniatureResourceEditingGameTests.playerFixture(h, f.tile());
        var stale = capacityCommand(peer, 1, BigInteger.valueOf(99));
        var command = capacityCommand(f, 1, BigInteger.TEN);
        f.menu().setCarried(new ItemStack(Items.IRON_ORE, 17));
        var response = f.menu().handleCommand(f.player(), command);
        h.assertTrue(response.result() == MiniatureFactorySnapshotPayload.Result.SUCCESS
                && response.snapshot().configuredInputCapacity(ore).equals(BigInteger.TEN)
                && response.snapshot().effectiveInputCapacity(ore).equals(BigInteger.TEN.min(Config.miniatureInputCapacity))
                && f.menu().getCarried().getCount() == 17 && f.menu().revision() == 0, "Capacity command changed assets or failed to synchronize");
        var saved = f.tile().factoryState().save();
        h.assertTrue(f.menu().handleCommand(f.player(), command).result() == MiniatureFactorySnapshotPayload.Result.SUCCESS
                && saved.equals(f.tile().factoryState().save()), "Replayed capacity command changed state");
        h.assertTrue(peer.menu().handleCommand(peer.player(), stale).result() == MiniatureFactorySnapshotPayload.Result.STALE_REVISION,
                "A stale viewer overwrote the capacity");
        peer.menu().broadcastChanges();
        h.assertTrue(peer.menu().snapshot().configuredInputCapacity(ore).equals(BigInteger.TEN), "Other open menu did not receive capacity");
        var reopened = MiniatureResourceEditingGameTests.playerFixture(h, f.tile());
        h.assertTrue(reopened.menu().snapshot().configuredInputCapacity(ore).equals(BigInteger.TEN)
                && FactoryStateCodec.load(saved, h.getLevel().registryAccess()).configuredInputCapacity(ore).equals(BigInteger.TEN)
                && FactoryStorage.empty().configuredInputCapacity().equals(FactoryLimits.MAX_INVENTORY_AMOUNT),
                "Capacity was lost on save/reopen or affected another factory");
        var buffer = new net.minecraft.network.RegistryFriendlyByteBuf(io.netty.buffer.Unpooled.buffer(),
                h.getLevel().registryAccess(), net.neoforged.neoforge.network.connection.ConnectionType.NEOFORGE);
        try {
            MiniatureFactorySnapshotPayload.STREAM_CODEC.encode(buffer, response);
            h.assertTrue(response.equals(MiniatureFactorySnapshotPayload.STREAM_CODEC.decode(buffer)), "Capacity snapshot failed network round-trip");
        } finally { buffer.release(); }
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void localLimitControlsMenuItemAndFluidAdmission(GameTestHelper h) {
        var ore = FactoryItemResources.keyOf(new ItemStack(Items.IRON_ORE), h.getLevel().registryAccess());
        var water = FactoryFluidResources.keyOf(new FluidStack(Fluids.WATER, 1), h.getLevel().registryAccess());
        var f = MiniatureResourceEditingGameTests.fixture(h, FactoryStorage.empty().withInputCapacity(ore, BigInteger.TEN));
        f.menu().setCarried(new ItemStack(Items.IRON_ORE, 17));
        var deposit = new MiniatureFactoryCommandPayload(f.menu().containerId, f.menu().sessionId(), f.tile().getBlockPos(),
                f.menu().revision(), 1, MiniatureFactoryCommandPayload.Action.INSERT_INPUT, 0, 0,
                BigInteger.valueOf(17), GridDirection.RIGHT, f.menu().snapshotId(), -1);
        h.assertTrue(f.menu().handleCommand(f.player(), deposit).result() == MiniatureFactorySnapshotPayload.Result.SUCCESS
                && f.menu().getCarried().getCount() == 7, "GUI ignored the local limit");
        var tile = MiniatureFluidCapabilityGameTests.fixture(h, FactoryStorage.empty().withInputCapacity(ore, BigInteger.TEN)
                .withInputCapacity(water, BigInteger.valueOf(20))
                .withInputs(new ResourceInventory(Map.of(ore, BigInteger.valueOf(8), water, BigInteger.valueOf(19)))));
        MiniatureFluidCapabilityGameTests.allowInput(h, tile, ore);
        var before = tile.factoryState();
        h.assertTrue(tile.itemHandler().getSlotLimit(0) == 10 && tile.itemHandler().insertItem(0, new ItemStack(Items.IRON_ORE, 3), true).getCount() == 1
                && tile.factoryState() == before, "Capabilities ignored local capacity or mutated during simulation");
        h.assertTrue(tile.itemHandler().insertItem(0, new ItemStack(Items.IRON_ORE, 3), false).getCount() == 1, "Item execution differs from simulation");
        MiniatureFluidCapabilityGameTests.allowInput(h, tile, water);
        before = tile.factoryState();
        h.assertTrue(tile.fluidHandler().getTankCapacity(0) == 20 && tile.fluidHandler().fill(new FluidStack(Fluids.WATER, 3), FluidAction.SIMULATE) == 1
                && tile.factoryState() == before && tile.fluidHandler().fill(new FluidStack(Fluids.WATER, 3), FluidAction.EXECUTE) == 1,
                "Fluid execution differs from simulation or ignores local limit");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void runningBatchAcceptsCapacityChangeAndReturnsReservedCatalysts(GameTestHelper h) {
        var registries = h.getLevel().registryAccess();
        var mold = FactoryItemResources.keyOf(new ItemStack(Items.STICK), registries);
        var ingot = FactoryItemResources.keyOf(new ItemStack(Items.IRON_INGOT), registries);
        var batch = new RunningBatch(UUID.randomUUID(), "0".repeat(64), new FactoryRules.Environment(10, 1), 1,
                ResourceInventory.empty(), new ResourceInventory(Map.of(ingot, BigInteger.ONE)), new ResourceInventory(Map.of(mold, BigInteger.TWO)),
                FactoryRules.energyPerTick(1), 19);
        var storage = FactoryStorage.empty().insertMachine(new GridPosition(1, 0), new ItemStack(Items.FURNACE), registries).storage()
                .withInputs(new ResourceInventory(Map.of(mold, BigInteger.valueOf(8)))).withBatch(batch, 1);
        var f = MiniatureResourceEditingGameTests.fixture(h, storage);
        var result = f.menu().handleCommand(f.player(), capacityCommand(f, 1, BigInteger.ONE));
        h.assertTrue(result.result() == MiniatureFactorySnapshotPayload.Result.SUCCESS, "Running batch rejected safe capacity change");
        var reloaded = FactoryStateCodec.load(f.tile().factoryState().save(), registries);
        var completed = reloaded.withBatch(batch.paid(), 2).completeBatch();
        h.assertTrue(completed.inputs().amount(mold).equals(BigInteger.TEN) && completed.inputCapacity(mold).signum() == 0
                && completed.configuredInputCapacity(mold).equals(BigInteger.ONE), "Completion lost catalysts or local capacity");
        h.succeed();
    }

    private static MiniatureFactoryCommandPayload capacityCommand(MiniatureResourceEditingGameTests.Fixture f, long id, BigInteger capacity) {
        return capacityCommand(f, id, capacity, 0);
    }

    private static MiniatureFactoryCommandPayload capacityCommand(MiniatureResourceEditingGameTests.Fixture f, long id, BigInteger capacity, int index) {
        return new MiniatureFactoryCommandPayload(f.menu().containerId, f.menu().sessionId(), f.tile().getBlockPos(),
                f.menu().revision(), id, MiniatureFactoryCommandPayload.Action.SET_INPUT_CAPACITY, 0, 0,
                capacity, GridDirection.RIGHT, f.menu().snapshotId(), index);
    }

    @GameTest(template = "empty")
    public static void reorderedIngredientsRejectStaleTargetsAndUnknownIndices(GameTestHelper h) {
        var registries = h.getLevel().registryAccess();
        var ore = FactoryItemResources.keyOf(new ItemStack(Items.IRON_ORE), registries);
        var coal = FactoryItemResources.keyOf(new ItemStack(Items.COAL), registries);
        var inputs = new java.util.LinkedHashMap<ResourceKey, BigInteger>();
        inputs.put(ore, BigInteger.ONE);
        inputs.put(coal, BigInteger.ONE);
        var f = MiniatureResourceEditingGameTests.fixture(h, FactoryStorage.empty().withInputs(new ResourceInventory(inputs)));
        var stale = capacityCommand(f, 1, BigInteger.TEN);
        inputs.clear();
        inputs.put(coal, BigInteger.ONE);
        inputs.put(ore, BigInteger.ONE);
        var before = f.tile().factoryState();
        h.assertTrue(f.tile().applyPreparedState(before, FactoryState.of(before.storage().orElseThrow()
                .withInputs(new ResourceInventory(inputs)), registries)), "Could not reorder input inventory");
        h.assertTrue(f.menu().handleCommand(f.player(), stale).result() == MiniatureFactorySnapshotPayload.Result.STALE_REVISION,
                "Reordered inputs redirected a stale ingredient setting");
        h.assertTrue(f.menu().handleCommand(f.player(), capacityCommand(f, 2, BigInteger.TWO)).result() == MiniatureFactorySnapshotPayload.Result.SUCCESS,
                "Fresh ingredient selection was rejected");
        var storage = f.tile().factoryState().storage().orElseThrow();
        h.assertTrue(storage.configuredInputCapacity(coal).equals(BigInteger.TWO)
                && storage.configuredInputCapacity(ore).equals(FactoryLimits.MAX_INVENTORY_AMOUNT), "Capacity changed the wrong ingredient");
        h.assertTrue(f.menu().handleCommand(f.player(), capacityCommand(f, 3, BigInteger.TEN, 2)).result()
                == MiniatureFactorySnapshotPayload.Result.INVALID_POSITION, "Unknown input index was accepted");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void localCapacitySurvivesRealDropAndPlacement(GameTestHelper h) {
        var ore = FactoryItemResources.keyOf(new ItemStack(Items.IRON_ORE), h.getLevel().registryAccess());
        var f = MiniatureResourceEditingGameTests.fixture(h, FactoryStorage.empty().withInputCapacity(ore, BigInteger.valueOf(37)));
        var pos = f.tile().getBlockPos();
        var before = f.tile().factoryState().save();
        h.assertTrue(h.getLevel().destroyBlock(pos, true), "Could not break configured factory");
        var drops = h.getLevel().getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class, new net.minecraft.world.phys.AABB(pos).inflate(1));
        h.assertTrue(drops.size() == 1, "Expected one factory drop");
        var stack = drops.getFirst().getItem().copy();
        drops.getFirst().discard();
        h.assertTrue(before.equals(stack.get(com.aquariverr.fluxod.register.FluxOdDataComponents.MINIATURE_UNIVERSE_STATE).copyTag()), "Drop lost local capacity");
        var placed = ((BlockItem) stack.getItem()).place(new net.minecraft.world.item.context.DirectionalPlaceContext(h.getLevel(), pos,
                net.minecraft.core.Direction.DOWN, stack, net.minecraft.core.Direction.UP));
        var tile = (com.aquariverr.fluxod.block.entity.TileMiniatureUniverse) h.getLevel().getBlockEntity(pos);
        h.assertTrue(placed.consumesAction() && stack.isEmpty() && tile != null && before.equals(tile.factoryState().save()), "Placement lost capacity state");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void serverCeilingReloadAndResetRefreshOpenMenu(GameTestHelper h) {
        var previous = Config.miniatureInputCapacity;
        try {
            Config.miniatureInputCapacity = BigInteger.valueOf(1000);
            var ore = FactoryItemResources.keyOf(new ItemStack(Items.IRON_ORE), h.getLevel().registryAccess());
            var f = MiniatureResourceEditingGameTests.fixture(h, FactoryStorage.empty().withInputCapacity(ore, BigInteger.valueOf(100)));
            Config.miniatureInputCapacity = BigInteger.TEN;
            f.menu().broadcastChanges();
            h.assertTrue(f.menu().snapshot().inputCapacityLimit().equals(BigInteger.TEN)
                    && f.menu().snapshot().effectiveInputCapacity(ore).equals(BigInteger.TEN)
                    && f.tile().factoryState().storage().orElseThrow().configuredInputCapacity(ore).equals(BigInteger.valueOf(100)), "Server reload lost local setting or did not sync");
            var result = f.menu().handleCommand(f.player(), capacityCommand(f, 1, FactoryLimits.MAX_INVENTORY_AMOUNT));
            h.assertTrue(result.result() == MiniatureFactorySnapshotPayload.Result.SUCCESS
                    && result.snapshot().configuredInputCapacity(ore).equals(FactoryLimits.MAX_INVENTORY_AMOUNT)
                    && result.snapshot().effectiveInputCapacity(ore).equals(BigInteger.TEN)
                    && !f.tile().factoryState().save().contains("inputCapacities"), "Default reset bypassed server ceiling or did not clear override");
            Config.miniatureInputCapacity = previous;
            f.menu().broadcastChanges();
            h.assertTrue(f.menu().snapshot().effectiveInputCapacity(ore).equals(previous), "Default did not follow server ceiling");
        } finally { Config.miniatureInputCapacity = previous; }
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void configuredLimitClampsMenuItemsAndCapabilitySimulation(GameTestHelper h) {
        var previous = Config.miniatureInputCapacity;
        try {
            Config.miniatureInputCapacity = BigInteger.TEN;
            var ore = FactoryItemResources.keyOf(new ItemStack(Items.IRON_ORE), h.getLevel().registryAccess());
            var f = MiniatureResourceEditingGameTests.fixture(h, FactoryStorage.empty());
            f.menu().setCarried(new ItemStack(Items.IRON_ORE, 64));
            var command = new MiniatureFactoryCommandPayload(f.menu().containerId, f.menu().sessionId(), f.tile().getBlockPos(),
                    f.menu().revision(), 1, MiniatureFactoryCommandPayload.Action.INSERT_INPUT, 0, 0,
                    BigInteger.valueOf(64), GridDirection.RIGHT, f.menu().snapshotId(), -1);
            var response = f.menu().handleCommand(f.player(), command);
            h.assertTrue(response.result() == MiniatureFactorySnapshotPayload.Result.SUCCESS && f.menu().getCarried().getCount() == 54
                    && f.tile().factoryState().storage().orElseThrow().inputs().amount(ore).equals(BigInteger.TEN), "GUI did not preserve overflow on cursor");
            var source = FactoryStorage.empty().withInputs(new ResourceInventory(Map.of(ore, BigInteger.valueOf(7))));
            var tile = MiniatureFluidCapabilityGameTests.fixture(h, source);
            MiniatureFluidCapabilityGameTests.allowInput(h, tile, ore);
            var before = tile.factoryState();
            h.assertTrue(tile.itemHandler().getSlotLimit(0) == 10 && tile.itemHandler().insertItem(0, new ItemStack(Items.IRON_ORE, 8), true).getCount() == 5
                    && before == tile.factoryState(), "Item simulation ignored capacity or mutated state");
            h.assertTrue(tile.itemHandler().insertItem(0, new ItemStack(Items.IRON_ORE, 8), false).getCount() == 5
                    && tile.insertInput(new ItemStack(Items.IRON_ORE), false).getCount() == 1, "Item insertion bypassed configured capacity");
            h.assertTrue(source.insertInput(new ItemStack(Items.IRON_ORE, 8), h.getLevel().registryAccess()).remainder().getCount() == 5,
                    "Direct item insertion lost its overflow");
        } finally { Config.miniatureInputCapacity = previous; }
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void fluidCapacityAndLoweredLimitPreserveExistingContents(GameTestHelper h) {
        var previous = Config.miniatureInputCapacity;
        try {
            Config.miniatureInputCapacity = BigInteger.valueOf(1000);
            var water = FactoryFluidResources.keyOf(new FluidStack(Fluids.WATER, 1), h.getLevel().registryAccess());
            var tile = MiniatureFluidCapabilityGameTests.fixture(h, FactoryStorage.empty()
                    .withInputs(new ResourceInventory(Map.of(water, BigInteger.valueOf(997)))));
            MiniatureFluidCapabilityGameTests.allowInput(h, tile, water);
            var before = tile.factoryState();
            h.assertTrue(tile.fluidHandler().getTankCapacity(0) == 1000
                    && tile.fluidHandler().fill(new FluidStack(Fluids.WATER, 1000), FluidAction.SIMULATE) == 3
                    && before == tile.factoryState(), "Fluid simulation ignored capacity or mutated state");
            h.assertTrue(tile.fluidHandler().fill(new FluidStack(Fluids.WATER, 1000), FluidAction.EXECUTE) == 3, "Fluid fill exceeded configured capacity");
            Config.miniatureInputCapacity = BigInteger.TEN;
            var saved = tile.factoryState().save();
            var restored = FactoryStateCodec.load(saved, h.getLevel().registryAccess());
            h.assertTrue(restored.inputs().amount(water).equals(BigInteger.valueOf(1000)) && restored.inputCapacity(water).signum() == 0
                    && tile.fluidHandler().fill(new FluidStack(Fluids.WATER, 1), FluidAction.EXECUTE) == 0
                    && saved.equals(tile.factoryState().save()), "Lowering capacity deleted contents or admitted more fluid");
            Config.miniatureInputCapacity = BigInteger.valueOf(1001);
            h.assertTrue(tile.fluidHandler().fill(new FluidStack(Fluids.WATER, 2), FluidAction.EXECUTE) == 1, "Raising capacity did not restore available space");
        } finally { Config.miniatureInputCapacity = previous; }
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void reservedCatalystsCountTowardCapacityAndReturnAfterLimitReduction(GameTestHelper h) {
        var previous = Config.miniatureInputCapacity;
        try {
            Config.miniatureInputCapacity = BigInteger.TEN;
            var registries = h.getLevel().registryAccess();
            var mold = FactoryItemResources.keyOf(new ItemStack(Items.STICK), registries);
            var ingot = FactoryItemResources.keyOf(new ItemStack(Items.IRON_INGOT), registries);
            var source = FactoryStorage.empty().insertMachine(new GridPosition(1, 0), new ItemStack(Items.FURNACE), registries).storage()
                    .withInputs(new ResourceInventory(Map.of(mold, BigInteger.valueOf(7))));
            var batch = new RunningBatch(UUID.randomUUID(), "0".repeat(64), new FactoryRules.Environment(10, 1), 1,
                    ResourceInventory.empty(), new ResourceInventory(Map.of(ingot, BigInteger.ONE)),
                    new ResourceInventory(Map.of(mold, BigInteger.TWO)), FactoryRules.energyPerTick(1), 19);
            var running = source.withBatch(batch, 1).withControls(true, RedstoneMode.IGNORE);
            var inserted = running.insertInput(new ItemStack(Items.STICK, 2), registries);
            h.assertTrue(running.inputCapacity(mold).equals(BigInteger.ONE) && inserted.remainder().getCount() == 1, "Insertion occupied reserved catalyst space");
            Config.miniatureInputCapacity = BigInteger.ONE;
            running = FactoryStateCodec.load(FactoryStateCodec.save(inserted.storage(), registries), registries);
            var completed = FactoryRunner.prepare(running, null, 0, null, 2).storage();
            h.assertTrue(completed.batch() == null && completed.inputs().amount(mold).equals(BigInteger.TEN)
                    && completed.inputCapacity(mold).signum() == 0, "Lowering capacity prevented completion or destroyed reserved catalysts");
        } finally { Config.miniatureInputCapacity = previous; }
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void chemicalCapabilityUsesConfiguredInputCapacity(GameTestHelper h) {
        if (com.aquariverr.fluxod.integration.mekanism.QuantumCoolingChemicalIntegration.isAvailable())
            com.aquariverr.fluxod.integration.mekanism.MiniatureChemicalVerification.configuredCapacity(h);
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void chemicalCapabilityUsesLocalInputCapacity(GameTestHelper h) {
        if (com.aquariverr.fluxod.integration.mekanism.QuantumCoolingChemicalIntegration.isAvailable())
            com.aquariverr.fluxod.integration.mekanism.MiniatureChemicalVerification.localCapacity(h);
        h.succeed();
    }
}
