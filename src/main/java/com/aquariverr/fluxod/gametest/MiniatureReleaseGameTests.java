package com.aquariverr.fluxod.gametest;

import com.aquariverr.fluxod.FluxOverdrive;
import com.aquariverr.fluxod.block.entity.TileMiniatureUniverse;
import com.aquariverr.fluxod.gametest.MiniatureRuntimeGameTests.Fixture;
import com.aquariverr.fluxod.miniature.model.*;
import com.aquariverr.fluxod.miniature.recipe.MiniatureRecipeCatalog;
import com.aquariverr.fluxod.miniature.runtime.*;
import com.aquariverr.fluxod.miniature.storage.*;
import com.aquariverr.fluxod.register.FluxOdDataComponents;
import com.aquariverr.fluxod.register.RegistryBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.*;
import net.minecraft.world.item.context.DirectionalPlaceContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.math.BigInteger;
import java.util.Map;
import java.util.UUID;
import java.util.function.IntConsumer;

/** S12 acceptance uses actual battery ticks, real block lifecycle and independently saved worlds. */
@GameTestHolder(FluxOverdrive.MODID)
@PrefixGameTestTemplate(false)
public final class MiniatureReleaseGameTests {
    private static final BigInteger FEE = BigInteger.valueOf(40_000_000);
    private static final net.minecraft.server.level.TicketType<net.minecraft.world.level.ChunkPos> UNLOAD_TICKET =
            net.minecraft.server.level.TicketType.create("flux_overdrive_s12_boundaries", java.util.Comparator.comparingLong(net.minecraft.world.level.ChunkPos::toLong));

    @GameTest(template = "empty", batch = "miniature_release", timeoutTicks = 80)
    public static void bothRedstoneLevelsPauseAnAlreadyPaidBatchAndResumeItsIdentity(GameTestHelper h) {
        var f = fixture(h, line(h), 20); f.tick();
        var batch = storage(f).batch();
        BlockPos signal = f.pluginPos().east();
        h.assertTrue(h.getLevel().getBlockState(signal).isAir(), "Signal must be outside the battery");
        controls(h, f, true, RedstoneMode.HIGH);
        observe(h, 39, elapsed -> {
            if (elapsed <= 15) {
                h.assertTrue(batch.equals(storage(f).batch()), "Redstone gate changed locked paid state");
                energy(h, f, 19); outputs(h, f, 5);
                if (elapsed == 5) {
                    h.getLevel().setBlock(signal, Blocks.REDSTONE_BLOCK.defaultBlockState(), 3);
                    controls(h, f, true, RedstoneMode.LOW);
                }
                if (elapsed == 10) {
                    h.getLevel().setBlock(signal, Blocks.AIR.defaultBlockState(), 3);
                    controls(h, f, true, RedstoneMode.HIGH);
                }
                if (elapsed == 15) controls(h, f, true, RedstoneMode.LOW);
            } else {
                int paid = Math.min(elapsed - 14, 20);
                energy(h, f, 20 - paid); outputs(h, f, paid == 20 ? 9 : 5);
                if (paid < 20) h.assertTrue(storage(f).batch().id().equals(batch.id()), "Redstone restart replaced batch identity");
            }
            if (elapsed == 39) h.succeed();
        });
    }

    @GameTest(template = "empty", batch = "miniature_release", timeoutTicks = 80)
    public static void quenchFuseStopsProductionAndRepairResumesOnlyTheRemainingPayments(GameTestHelper h) {
        var f = fixture(h, line(h), 20);
        BlockPos fuse = f.battery().getBlockPos().offset(-3, 0, 0);
        h.getLevel().setBlock(fuse, RegistryBlocks.QUANTUM_QUENCH_FUSE_TIER_1.get().defaultBlockState(), 3);
        f.battery().validateStructure();
        h.assertTrue(f.battery().isOperational(), "Fuse invalidated the formed tier-three battery");
        f.tick();
        CompoundTag before = f.tile().factoryState().save();
        CompoundTag battery = new CompoundTag();
        f.battery().writeCustomTag(battery, sonar.fluxnetworks.api.FluxConstants.NBT_SAVE_ALL);
        battery.putInt("StabilityBasisPoints", 0);
        battery.putInt("StabilityUnpoweredTicks", 0); battery.putInt("StabilityPoweredTicks", 0);
        f.battery().readCustomTag(battery, sonar.fluxnetworks.api.FluxConstants.NBT_SAVE_ALL);
        f.battery().validateStructure();
        h.startSequence().thenIdle(5).thenExecute(() -> {
            h.assertTrue(h.getLevel().getBlockState(fuse).isAir() && !f.battery().isOperational()
                    && before.equals(f.tile().factoryState().save()), "Protected quench did not stop the batch at its paid boundary");
            energy(h, f, 0);
        }).thenExecuteFor(10, () -> {
            f.tick(); h.assertTrue(before.equals(f.tile().factoryState().save()), "An independent plugin ticker survived safety shutdown");
        }).thenExecute(() -> {
            h.getLevel().setBlock(fuse, RegistryBlocks.QUANTUM_QUENCH_FUSE_TIER_1.get().defaultBlockState(), 3);
            MiniatureRuntimeGameTests.setEnergy(h, f.battery(), FEE.multiply(BigInteger.valueOf(19)));
        }).thenIdle(24).thenExecute(() -> { outputs(h, f, 9); energy(h, f, 0); }).thenSucceed();
    }

    @GameTest(template = "empty", batch = "miniature_release")
    public static void activeAndPausedMenusRejectConfigurationButAllowCommittedOutput(GameTestHelper h) {
        var f = fixture(h, line(h), 1); f.tick();
        var editor = MiniatureResourceEditingGameTests.playerFixture(h, f.tile());
        f.battery().setOwnerUUID(editor.player().getUUID());
        long commandId = 0;
        for (boolean enabled : new boolean[]{true, false}) {
            controls(h, f, enabled, RedstoneMode.IGNORE);
            editor.menu().broadcastChanges();
            for (var action : com.aquariverr.fluxod.network.MiniatureFactoryCommandPayload.Action.values()) {
                if (action.isInventory() || action.isControl() || action.isContentMarker()
                        || action == com.aquariverr.fluxod.network.MiniatureFactoryCommandPayload.Action.SET_INPUT_CAPACITY
                        || action == com.aquariverr.fluxod.network.MiniatureFactoryCommandPayload.Action.SET_GHOST_MARKER
                        || action == com.aquariverr.fluxod.network.MiniatureFactoryCommandPayload.Action.SELECT_RECIPE) continue;
                var before = f.tile().factoryState();
                var request = new com.aquariverr.fluxod.network.MiniatureFactoryCommandPayload(editor.menu().containerId, editor.menu().sessionId(),
                        f.pluginPos(), editor.menu().revision(), ++commandId, action, 0, 0, BigInteger.ONE, GridDirection.RIGHT,
                        action.requiresSnapshot() ? editor.menu().snapshotId() : com.aquariverr.fluxod.network.MiniatureFactoryCommandPayload.NO_SNAPSHOT,
                        action == com.aquariverr.fluxod.network.MiniatureFactoryCommandPayload.Action.SET_PRODUCT_DIRECTION ? 0 : -1);
                h.assertTrue(editor.menu().handleCommand(editor.player(), request).result() == com.aquariverr.fluxod.network.MiniatureFactorySnapshotPayload.Result.BATCH_LOCKED
                        && before == f.tile().factoryState() && editor.menu().getCarried().isEmpty(), "Batch lock failed for " + action);
            }
        }
        var snapshot = editor.menu().snapshot();
        int row = -1;
        for (int i = 0; i < snapshot.outputs().size(); i++) if (snapshot.outputs().get(i).resource().equals(key(h, Items.IRON_INGOT))) row = i;
        var request = new com.aquariverr.fluxod.network.MiniatureFactoryCommandPayload(editor.menu().containerId, editor.menu().sessionId(),
                f.pluginPos(), editor.menu().revision(), ++commandId, com.aquariverr.fluxod.network.MiniatureFactoryCommandPayload.Action.EXTRACT_OUTPUT,
                0, 0, BigInteger.valueOf(64), GridDirection.RIGHT, editor.menu().snapshotId(), row);
        h.assertTrue(editor.menu().handleCommand(editor.player(), request).result() == com.aquariverr.fluxod.network.MiniatureFactorySnapshotPayload.Result.SUCCESS
                && editor.menu().getCarried().getCount() == 5 && storage(f).batch().paidTicks() == 1,
                "Paused output access exposed reserved products or blocked committed products");
        editor.player().closeContainer(); h.succeed();
    }

    @GameTest(template = "empty")
    public static void clearingConfigurationCannotEraseMachinesOrRealResourceInventories(GameTestHelper h) {
        var storage = line(h);
        var cleared = storage.withConfiguration(new GridPosition(0, 0), FactoryCell.Empty.INSTANCE)
                .withConfiguration(new GridPosition(2, 0), FactoryCell.Empty.INSTANCE);
        h.assertTrue(cleared.machines().entries().equals(storage.machines().entries()) && cleared.inputs().equals(storage.inputs())
                && cleared.outputs().equals(storage.outputs()), "Clearing marker or arrow changed real assets");
        try {
            cleared.withConfiguration(new GridPosition(1, 0), FactoryCell.Empty.INSTANCE);
            h.fail("Configuration reset erased a real machine");
        } catch (ModelValidationException expected) {
            h.assertTrue(expected.diagnostic().code() == FactoryDiagnostic.Code.INVALID_MACHINE, "Wrong machine-clear diagnostic");
        }
        h.succeed();
    }

    @GameTest(template = "empty", batch = "s12_release_reload", timeoutTicks = 1800)
    public static void actualDatapackReloadKeepsPaidTermsAndRequiresCompilationForTheNextBatch(GameTestHelper h) {
        var f = fixture(h, line(h), 1);
        f.tick();
        var original = f.tile().factoryState().save();
        BigInteger[] nextCost = {BigInteger.ZERO};
        var catalog = MiniatureRecipeCatalog.get(h.getLevel().getServer());
        long generation = catalog.generation();
        var server = h.getLevel().getServer();
        var reload = server.reloadResources(java.util.List.copyOf(server.getPackRepository().getSelectedIds()));
        h.startSequence().thenWaitUntil(() -> h.assertTrue(reload.isDone(), "Waiting for actual server datapack reload"))
                .thenExecute(() -> {
                    reload.join();
                    h.assertTrue(catalog.generation() > generation && f.tile().compilationFor(f.tile().factoryState()) == null
                            && original.equals(f.tile().factoryState().save()), "Reload changed paid terms or kept stale compilation");
                    MiniatureRuntimeGameTests.setEnergy(h, f.battery(), FEE.multiply(BigInteger.valueOf(19)));
                }).thenIdle(24).thenExecute(() -> {
                    outputs(h, f, 9); energy(h, f, 0);
                    h.assertTrue(storage(f).batch() == null, "Reload prevented the locked batch from completing");
                    f.tile().insertInput(new ItemStack(Items.IRON_ORE, 4), false);
                    MiniatureRuntimeGameTests.setEnergy(h, f.battery(), FEE.multiply(BigInteger.valueOf(20)));
                }).thenIdle(5).thenExecute(() -> {
                    outputs(h, f, 9); energy(h, f, 20);
                    h.assertTrue(storage(f).batch() == null && f.tile().runtimePreview().status() == FactoryRunner.Status.NOT_COMPILED,
                            "A new batch used the compilation invalidated by reload");
                    compile(h, f, true);
                }).thenIdle(1).thenExecute(() -> {
                    var batch = storage(f).batch();
                    h.assertTrue(batch != null && batch.parallelism() == 2, "Recompiled batch did not start");
                    int p = batch.environment().parallelism();
                    nextCost[0] = FactoryRules.energyPerBatch((4 + p - 1) / p);
                }).thenIdle(23).thenExecute(() -> {
                    outputs(h, f, 13);
                    h.assertTrue(f.battery().getStoredEnergy().equals(FEE.multiply(BigInteger.valueOf(20)).subtract(nextCost[0])),
                            "Recompiled batch did not charge its actual working machines");
                })
                .thenIdle(60).thenWaitUntil(() -> h.assertTrue(MiniatureLifecycleGameTests.chunksReadyForShutdown(h), "Waiting for save dependencies"))
                .thenSucceed();
    }

    @GameTest(template = "empty", batch = "miniature_release", timeoutTicks = 900)
    public static void actualChunkUnloadPreservesEveryBoundaryAndResumesNineteenthPayment(GameTestHelper h) {
        var level = h.getLevel();
        BlockPos base = new BlockPos(4096, 90, 4096);
        var chunk = new net.minecraft.world.level.ChunkPos(base);
        Runnable acquire = () -> level.getChunkSource().addRegionTicket(UNLOAD_TICKET, chunk, 2, chunk);
        Runnable release = () -> level.getChunkSource().removeRegionTicket(UNLOAD_TICKET, chunk, 2, chunk);
        acquire.run();
        h.testInfo.addListener(new net.minecraft.gametest.framework.GameTestListener() {
            public void testStructureLoaded(net.minecraft.gametest.framework.GameTestInfo test) {}
            public void testPassed(net.minecraft.gametest.framework.GameTestInfo test, net.minecraft.gametest.framework.GameTestRunner runner) { release.run(); }
            public void testFailed(net.minecraft.gametest.framework.GameTestInfo test, net.minecraft.gametest.framework.GameTestRunner runner) { release.run(); }
            public void testAddedForRerun(net.minecraft.gametest.framework.GameTestInfo oldTest, net.minecraft.gametest.framework.GameTestInfo newTest, net.minecraft.gametest.framework.GameTestRunner runner) {}
        });
        level.getChunkAt(base);
        var f = fixture(h, line(h), 20);
        var stored = new TileMiniatureUniverse[4];
        var expected = new CompoundTag[4];
        java.util.function.IntConsumer save = index -> {
            BlockPos pos = base.offset(index, 0, 0);
            level.setBlock(pos, RegistryBlocks.MINIATURE_UNIVERSE.get().defaultBlockState(), 3);
            stored[index] = (TileMiniatureUniverse) level.getBlockEntity(pos);
            load(h, stored[index], storage(f).withControls(false, RedstoneMode.IGNORE));
            stored[index].setChanged();
            expected[index] = stored[index].factoryState().save();
        };
        save.accept(0);
        f.tick(); save.accept(1);
        h.runAfterDelay(18, () -> {
            h.assertTrue(storage(f).batch() != null && storage(f).batch().paidTicks() == 19, "Missing nineteenth-payment boundary");
            save.accept(2);
        });
        h.runAfterDelay(19, () -> { outputs(h, f, 9); save.accept(3); });
        h.startSequence().thenIdle(21).thenWaitUntil(() -> {
            var holder = level.getChunkSource().chunkMap.getVisibleChunkIfPresent(chunk.toLong());
            h.assertTrue(holder != null && holder.getGenerationRefCount() == 0 && holder.isReadyForSaving(), "Waiting for isolated source save readiness");
        }).thenExecute(release).thenWaitUntil(() -> {
            MiniatureEntanglementUnloadGameTests.allowOrdinarySourceUnloadWork(level);
            h.assertTrue(stored[0].isRemoved() && level.getChunkSource().getChunk(chunk.x, chunk.z, net.minecraft.world.level.chunk.status.ChunkStatus.FULL, false) == null,
                    "Waiting for actual source chunk unload");
        }).thenExecuteFor(20, () -> {
            for (int i = 0; i < stored.length; i++) {
                stored[i].tickFromQuantumBattery(f.battery());
                h.assertTrue(stored[i].isRemoved() && expected[i].equals(stored[i].factoryState().save()), "Unloaded reference advanced or mutated assets");
            }
            energy(h, f, 0);
        }).thenExecute(() -> {
            acquire.run(); level.getChunkAt(base);
            for (int i = 0; i < stored.length; i++) {
                var restored = (TileMiniatureUniverse) level.getBlockEntity(base.offset(i, 0, 0));
                h.assertTrue(restored != null && restored != stored[i] && expected[i].equals(restored.factoryState().save()), "Reload changed a saved payment boundary");
            }
            var remaining = ((TileMiniatureUniverse) level.getBlockEntity(base.offset(2, 0, 0))).factoryState().storage().orElseThrow();
            load(h, f.tile(), remaining.withControls(true, RedstoneMode.IGNORE));
            MiniatureRuntimeGameTests.setEnergy(h, f.battery(), FEE);
        }).thenIdle(5).thenExecute(() -> { outputs(h, f, 9); energy(h, f, 0); release.run(); }).thenSucceed();
    }

    @GameTest(template = "empty", batch = "miniature_release", timeoutTicks = 100)
    public static void threeConsecutiveBatchesStartEveryTwentyTicksWithoutAnIdleTick(GameTestHelper h) {
        var f = fixture(h, line(h), 60);
        f.tick();
        UUID[] identities = new UUID[3];
        BigInteger[] expectedEnergy = {FEE.multiply(BigInteger.valueOf(60))};
        BigInteger[] activeFee = {FEE};
        observe(h, 63, elapsed -> {
            int charges = Math.min(elapsed + 1, 60);
            int completed = charges / 20;
            outputs(h, f, 5 + completed * 4);
            if (elapsed < 60 && elapsed % 20 == 0) {
                var batch = storage(f).batch();
                h.assertTrue(batch != null && batch.paidTicks() == 1 && batch.parallelism() == 2,
                        "The next batch inserted an idle tick or changed its parallelism");
                identities[elapsed / 20] = batch.id();
                int p = batch.environment().parallelism();
                activeFee[0] = FactoryRules.energyPerTick((4 + p - 1) / p);
                h.assertTrue(batch.energyPerTick().equals(activeFee[0]), "New batch ignored its current environmental throughput");
                if (elapsed > 0) h.assertTrue(!batch.id().equals(identities[elapsed / 20 - 1]), "Reused completed batch identity");
            }
            if (elapsed < 60) expectedEnergy[0] = expectedEnergy[0].subtract(activeFee[0]);
            h.assertTrue(f.battery().getStoredEnergy().equals(expectedEnergy[0]), "Consecutive batches charged an inactive machine");
            if (elapsed == 19 || elapsed == 39) {
                h.assertTrue(f.tile().insertInput(new ItemStack(Items.IRON_ORE, 4), false).isEmpty(), "Could not feed the next batch");
            }
            unchangedRepeatedTick(h, f);
            if (elapsed == 63) h.succeed();
        });
    }

    @GameTest(template = "empty", batch = "miniature_release", timeoutTicks = 170)
    public static void hundredUnpoweredTicksAndEnvironmentChangePreserveTheLockedBatch(GameTestHelper h) {
        var f = fixture(h, line(h), 1);
        f.tick();
        RunningBatch started = storage(f).batch();
        CompoundTag paused = f.tile().factoryState().save();
        observe(h, 124, elapsed -> {
            if (elapsed <= 100) {
                h.assertTrue(paused.equals(f.tile().factoryState().save()), "Unpowered ticks changed the paid batch");
                energy(h, f, 0);
                if (elapsed == 50) f.battery().setTemperatureKelvin(8_001);
                if (elapsed == 100) MiniatureRuntimeGameTests.setEnergy(h, f.battery(), FEE.multiply(BigInteger.valueOf(19)));
            } else {
                int paid = Math.min(elapsed - 99, 20);
                energy(h, f, 20 - paid);
                outputs(h, f, paid == 20 ? 9 : 5);
                if (paid < 20) {
                    var current = storage(f).batch();
                    h.assertTrue(current != null && current.paidTicks() == paid && current.id().equals(started.id())
                            && current.environment().equals(started.environment()) && current.inputs().equals(started.inputs())
                            && current.outputs().equals(started.outputs()) && current.parallelism() == started.parallelism()
                            && current.energyPerTick().equals(started.energyPerTick()), "Resumption changed locked terms");
                }
            }
            unchangedRepeatedTick(h, f);
            if (elapsed == 124) h.succeed();
        });
    }

    @GameTest(template = "empty", batch = "miniature_release", timeoutTicks = 100)
    public static void manualAndBothRedstoneModesPauseWithoutChangingAssets(GameTestHelper h) {
        var f = fixture(h, line(h), 20);
        controls(h, f, false, RedstoneMode.IGNORE);
        CompoundTag[] paused = { f.tile().factoryState().save() };
        // The slot is at x=-11 on the inner face of the radius-13 tube. x=-10 is air;
        // west/up are still tube material and would invalidate periodic structure checks.
        BlockPos signal = f.pluginPos().east();
        h.assertTrue(h.getLevel().getBlockState(signal).isAir(), "Redstone fixture would replace a structure block");
        observe(h, 44, elapsed -> {
            if (elapsed <= 20) {
                h.assertTrue(paused[0].equals(f.tile().factoryState().save()), "A disabled or gated tick changed assets");
                energy(h, f, 20);
            }
            if (elapsed == 5) controls(h, f, true, RedstoneMode.HIGH);
            if (elapsed == 10) {
                h.getLevel().setBlock(signal, Blocks.REDSTONE_BLOCK.defaultBlockState(), 3);
                controls(h, f, true, RedstoneMode.LOW);
            }
            if (elapsed == 15) controls(h, f, false, RedstoneMode.HIGH);
            if (elapsed == 20) controls(h, f, true, RedstoneMode.HIGH);
            if (elapsed == 5 || elapsed == 10 || elapsed == 15 || elapsed == 20) paused[0] = f.tile().factoryState().save();
            if (elapsed > 20) {
                int paid = Math.min(elapsed - 20, 20);
                energy(h, f, 20 - paid);
                outputs(h, f, paid == 20 ? 9 : 5);
            }
            if (elapsed == 44) h.succeed();
        });
    }

    @GameTest(template = "empty", batch = "miniature_release", timeoutTicks = 100)
    public static void missingStructureStopsTheRealTickerAndRestorationResumesPayment(GameTestHelper h) {
        var f = fixture(h, line(h), 20);
        f.tick();
        CompoundTag paused = f.tile().factoryState().save();
        BlockPos removed = f.battery().getBlockPos().offset(3, 0, 0);
        var block = h.getLevel().getBlockState(removed);
        h.assertTrue(!block.isAir(), "Structure fault fixture did not select a real ring cell");
        h.getLevel().setBlock(removed, Blocks.AIR.defaultBlockState(), 3);
        f.battery().validateStructure();
        h.assertTrue(!f.battery().isOperational(), "A missing ring cell did not invalidate the battery");
        observe(h, 44, elapsed -> {
            if (elapsed <= 20) {
                f.tick();
                h.assertTrue(paused.equals(f.tile().factoryState().save()), "Invalid structure advanced a batch");
                energy(h, f, 19);
                if (elapsed == 20) {
                    h.getLevel().setBlock(removed, block, 3);
                    f.battery().validateStructure();
                    h.assertTrue(f.battery().isOperational(), "Repaired structure did not reform");
                }
            } else {
                int paid = Math.min(elapsed - 19, 20);
                energy(h, f, 20 - paid);
                outputs(h, f, paid == 20 ? 9 : 5);
            }
            if (elapsed == 44) h.succeed();
        });
    }

    @GameTest(template = "empty", batch = "miniature_release")
    public static void invalidCompilationAndMissingInputNeverDebitTheBattery(GameTestHelper h) {
        var f = fixture(h, line(h).withInputs(ResourceInventory.empty()), 20);
        var before = f.tile().factoryState();
        f.tick();
        h.assertTrue(before == f.tile().factoryState() && f.tile().runtimePreview().status() == FactoryRunner.Status.MISSING_INPUT,
                "Missing input changed the factory");
        energy(h, f, 20);
        load(h, f.tile(), line(h).withConfiguration(new GridPosition(2, 0), FactoryCell.Empty.INSTANCE).withControls(true, RedstoneMode.IGNORE));
        compile(h, f, false);
        before = f.tile().factoryState();
        f.tick();
        h.assertTrue(before == f.tile().factoryState(), "Invalid compilation consumed resources");
        energy(h, f, 20);
        h.succeed();
    }

    @GameTest(template = "empty", batch = "miniature_release")
    public static void maximumEnvironmentIsLimitedToThreeByInputsOrOutputSpaceWithOneWorkingMachine(GameTestHelper h) {
        var source = line(h).withControls(true, RedstoneMode.IGNORE);
        var catalog = MiniatureRecipeCatalog.get(h.getLevel().getServer());
        var compiled = catalog.compile(source.layout(), h.getLevel()).factory();
        var max = new FactoryRules.Environment(1, 10);
        h.assertTrue(max.parallelism() == 2000, "Maximum environment fixture changed");
        for (boolean space : new boolean[]{false, true}) {
            var state = source.withInputs(new ResourceInventory(Map.of(key(h, Items.IRON_ORE), BigInteger.valueOf(space ? 4000 : 6))));
            if (space) state = state.withOutputs(new ResourceInventory(Map.of(key(h, Items.IRON_INGOT), FactoryLimits.MAX_INVENTORY_AMOUNT.subtract(BigInteger.valueOf(6)))));
            var step = FactoryRunner.prepare(state, compiled, catalog.generation(), max, h.getLevel().getGameTime());
            h.assertTrue(step.storage().batch().parallelism() == 3 && step.charge().equals(FactoryRules.energyPerTick(1))
                    && step.storage().batch().inputs().amount(key(h, Items.IRON_ORE)).equals(BigInteger.valueOf(6))
                    && step.storage().batch().outputs().amount(key(h, Items.IRON_INGOT)).equals(BigInteger.valueOf(6)),
                    "Three-way capacity did not limit a P=2000 batch with a single working-machine fee");
        }
        h.succeed();
    }

    @GameTest(template = "empty", batch = "miniature_release", timeoutTicks = 80)
    public static void realBreakAndPlacementAtEveryPaymentBoundaryKeepOneAsset(GameTestHelper h) {
        var f = fixture(h, line(h), 20);
        breakAndPlace(h, f);
        compile(h, f, true);
        f.tick();
        breakAndPlace(h, f);
        observe(h, 24, elapsed -> {
            int paid = Math.min(elapsed + 1, 20);
            energy(h, f, 20 - paid);
            outputs(h, f, paid == 20 ? 9 : 5);
            if (elapsed == 18 || elapsed == 19) breakAndPlace(h, f);
            unchangedRepeatedTick(h, f);
            if (elapsed == 24) h.succeed();
        });
    }

    @GameTest(template = "empty", batch = "miniature_release", timeoutTicks = 80)
    public static void activeAndPausedSimulationDoesNotDirtyOrExposeReservations(GameTestHelper h) {
        var f = fixture(h, line(h), 1);
        f.tick();
        observe(h, 3, elapsed -> {
            var chunk = h.getLevel().getChunkAt(f.pluginPos());
            chunk.setUnsaved(false);
            var before = f.tile().factoryState();
            var handler = f.tile().itemHandler();
            for (int slot = 0; slot < handler.getSlots(); slot++) {
                handler.getStackInSlot(slot);
                handler.extractItem(slot, 64, true);
                handler.insertItem(slot, new ItemStack(Items.IRON_ORE, 64), true);
            }
            h.assertTrue(before == f.tile().factoryState() && !chunk.isUnsaved(), "Simulation changed state or dirtied the chunk");
            h.assertTrue(f.tile().extractOutput(key(h, Items.IRON_INGOT), 64, true).getCount() == 5,
                    "Unpaid products became extractable");
            energy(h, f, 0);
            // Restore normal persistence after observing the simulation contract.
            f.tile().setChanged();
            if (elapsed == 3) h.succeed();
        });
    }

    @GameTest(template = "empty", batch = "miniature_release", timeoutTicks = 90)
    public static void restartAtZeroPaidTicks(GameTestHelper h) { restart(h, 0); }
    @GameTest(template = "empty", batch = "miniature_release", timeoutTicks = 90)
    public static void restartAtOnePaidTick(GameTestHelper h) { restart(h, 1); }
    @GameTest(template = "empty", batch = "miniature_release", timeoutTicks = 90)
    public static void restartAtNineteenPaidTicks(GameTestHelper h) { restart(h, 19); }
    @GameTest(template = "empty", batch = "miniature_release", timeoutTicks = 90)
    public static void restartAfterTwentiethPayment(GameTestHelper h) { restart(h, 20); }

    private static void restart(GameTestHelper h, int paid) {
        BlockPos pos = new BlockPos(2500 + paid * 32, 90, 2500);
        h.getLevel().getChunkAt(pos);
        var f = fixture(h, line(h), 20);
        if (System.getProperty("flux_overdrive.miniaturePersistencePhase", "write").equals("write")) {
            if (paid > 0) f.tick();
            h.runAfterDelay(Math.max(0, paid - 1), () -> {
                h.assertTrue(paid == 0 ? storage(f).batch() == null : paid == 20 ? storage(f).batch() == null
                        : storage(f).batch() != null && storage(f).batch().paidTicks() == paid, "Wrong real payment boundary");
                h.getLevel().setBlock(pos, RegistryBlocks.MINIATURE_UNIVERSE.get().defaultBlockState(), 3);
                var saved = (TileMiniatureUniverse) h.getLevel().getBlockEntity(pos);
                load(h, saved, storage(f).withControls(false, RedstoneMode.IGNORE));
                saved.setChanged();
                h.succeed();
            });
        } else {
            h.assertTrue(h.getLevel().getBlockEntity(pos) instanceof TileMiniatureUniverse, "Independent write world did not contain the boundary fixture");
            var saved = ((TileMiniatureUniverse) h.getLevel().getBlockEntity(pos)).factoryState().storage().orElseThrow();
            h.assertTrue(!saved.enabled() && saved.layout().machineCount() == 2
                    && saved.inputs().amount(key(h, Items.IRON_ORE)).equals(BigInteger.valueOf(paid == 0 ? 4 : 0))
                    && saved.outputs().amount(key(h, Items.IRON_INGOT)).equals(BigInteger.valueOf(paid == 20 ? 9 : 5))
                    && (paid == 0 || paid == 20 ? saved.batch() == null : saved.batch() != null && saved.batch().paidTicks() == paid),
                    "Restart changed the boundary assets or progress");
            load(h, f.tile(), saved.withControls(true, RedstoneMode.IGNORE));
            if (paid == 0) compile(h, f, true);
            int remaining = 20 - paid;
            f.tick();
            observe(h, remaining + 3, elapsed -> {
                int charges = Math.min(elapsed + 1, remaining);
                energy(h, f, 20 - charges);
                outputs(h, f, charges == remaining ? 9 : 5);
                if (saved.batch() != null && storage(f).batch() != null) h.assertTrue(saved.batch().id().equals(storage(f).batch().id()), "Restart replaced the paid batch identity");
                unchangedRepeatedTick(h, f);
                if (elapsed == remaining + 3) h.succeed();
            });
        }
    }

    private static void breakAndPlace(GameTestHelper h, Fixture f) {
        var before = f.tile().factoryState().save();
        var original = f.tile();
        h.assertTrue(h.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(f.pluginPos()).inflate(1)).isEmpty(), "Unrelated drops at fixture");
        h.assertTrue(h.getLevel().destroyBlock(f.pluginPos(), true), "Could not break factory");
        var drops = h.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(f.pluginPos()).inflate(1));
        h.assertTrue(drops.size() == 1, "Breaking scattered multiple asset items");
        ItemStack item = drops.getFirst().getItem().copy();
        drops.getFirst().discard();
        h.assertTrue(item.getCount() == 1 && item.has(FluxOdDataComponents.MINIATURE_UNIVERSE_STATE)
                && before.equals(item.get(FluxOdDataComponents.MINIATURE_UNIVERSE_STATE).copyTag()), "Drop changed the complete paid state");
        BigInteger energy = f.battery().getStoredEnergy();
        original.tickFromQuantumBattery(f.battery());
        var result = ((BlockItem) item.getItem()).place(new DirectionalPlaceContext(h.getLevel(), f.pluginPos(), Direction.DOWN, item, Direction.UP));
        h.assertTrue(result.consumesAction() && item.isEmpty() && before.equals(f.tile().factoryState().save())
                && energy.equals(f.battery().getStoredEnergy()), "Placement duplicated the item, state, or payment");
        f.battery().validateStructure();
    }

    private static void compile(GameTestHelper h, Fixture f, boolean expected) {
        var catalog = MiniatureRecipeCatalog.get(h.getLevel().getServer());
        var state = f.tile().factoryState();
        var result = catalog.compile(storage(f).layout(), h.getLevel());
        h.assertTrue(result.successful() == expected && f.tile().applyCompilation(state, result, catalog.generation()), "Unexpected compilation result");
    }
    private static void load(GameTestHelper h, TileMiniatureUniverse tile, FactoryStorage storage) {
        var tag = new CompoundTag();
        tag.put(TileMiniatureUniverse.STATE_TAG, FactoryState.of(storage, h.getLevel().registryAccess()).save());
        tile.loadAdditional(tag, h.getLevel().registryAccess());
    }
    private static void controls(GameTestHelper h, Fixture f, boolean enabled, RedstoneMode mode) {
        var before = f.tile().factoryState();
        h.assertTrue(f.tile().applyPreparedState(before, FactoryState.of(storage(f).withControls(enabled, mode), h.getLevel().registryAccess())), "Control transaction failed");
    }
    private static FactoryStorage line(GameTestHelper h) { return MiniatureRuntimeGameTests.line(h); }
    private static Fixture fixture(GameTestHelper h, FactoryStorage storage, int payments) { return MiniatureRuntimeGameTests.fixture(h, storage, FEE.multiply(BigInteger.valueOf(payments))); }
    private static FactoryStorage storage(Fixture f) { return f.tile().factoryState().storage().orElseThrow(); }
    private static ResourceKey key(GameTestHelper h, Item item) { return FactoryItemResources.keyOf(new ItemStack(item), h.getLevel().registryAccess()); }
    private static void energy(GameTestHelper h, Fixture f, int payments) { h.assertTrue(f.battery().getStoredEnergy().equals(FEE.multiply(BigInteger.valueOf(payments))), "Unexpected remaining energy: " + f.battery().getStoredEnergy() + "; expected payments " + payments); }
    private static void outputs(GameTestHelper h, Fixture f, int amount) { h.assertTrue(storage(f).outputs().amount(key(h, Items.IRON_INGOT)).equals(BigInteger.valueOf(amount)), "Incorrect committed ingots: " + storage(f).outputs().amount(key(h, Items.IRON_INGOT)) + "; expected " + amount); }
    private static void unchangedRepeatedTick(GameTestHelper h, Fixture f) {
        var state = f.tile().factoryState(); var energy = f.battery().getStoredEnergy();
        for (int i = 0; i < 3; i++) f.tick();
        h.assertTrue(state == f.tile().factoryState() && energy.equals(f.battery().getStoredEnergy()), "Repeated tick changed assets");
    }
    private static void observe(GameTestHelper h, int last, IntConsumer action) {
        long started = h.getLevel().getGameTime();
        for (int i = 0; i <= last; i++) { int tick = i; h.runAfterDelay(i, () -> {
            h.assertTrue(h.getLevel().getGameTime() == started + tick, "Observation ran on the wrong game tick");
            action.accept(tick);
        }); }
    }
    private MiniatureReleaseGameTests() {}
}
