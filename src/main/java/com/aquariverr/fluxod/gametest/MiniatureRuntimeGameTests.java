package com.aquariverr.fluxod.gametest;

import com.aquariverr.fluxod.FluxOverdrive;
import com.aquariverr.fluxod.block.entity.TileMiniatureUniverse;
import com.aquariverr.fluxod.block.entity.TileQuantumBatteryController;
import com.aquariverr.fluxod.miniature.model.FactoryCell;
import com.aquariverr.fluxod.miniature.model.FactoryDiagnostic;
import com.aquariverr.fluxod.miniature.model.FactoryLimits;
import com.aquariverr.fluxod.miniature.model.FactoryRules;
import com.aquariverr.fluxod.miniature.model.GridDirection;
import com.aquariverr.fluxod.miniature.model.GridPosition;
import com.aquariverr.fluxod.miniature.model.ModelValidationException;
import com.aquariverr.fluxod.miniature.model.ResourceAmount;
import com.aquariverr.fluxod.miniature.model.ResourceKey;
import com.aquariverr.fluxod.miniature.recipe.MiniatureRecipeCatalog;
import com.aquariverr.fluxod.miniature.runtime.FactoryRunner;
import com.aquariverr.fluxod.miniature.runtime.RedstoneMode;
import com.aquariverr.fluxod.miniature.runtime.RunningBatch;
import com.aquariverr.fluxod.miniature.storage.FactoryItemResources;
import com.aquariverr.fluxod.miniature.storage.FactoryState;
import com.aquariverr.fluxod.miniature.storage.FactoryStorage;
import com.aquariverr.fluxod.miniature.storage.ResourceInventory;
import com.aquariverr.fluxod.multiblock.QuantumBatteryAssembler;
import com.aquariverr.fluxod.multiblock.QuantumBatteryProcessingPlugin;
import com.aquariverr.fluxod.multiblock.QuantumBatteryStructure;
import com.aquariverr.fluxod.register.RegistryBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import sonar.fluxnetworks.api.FluxConstants;

import java.math.BigInteger;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.IntConsumer;

@GameTestHolder(FluxOverdrive.MODID)
@PrefixGameTestTemplate(false)
public final class MiniatureRuntimeGameTests {
    // The outer ring spans +/-15. Keep its footprint inside the 40x40 empty template,
    // with the bottom above the floor (the top extends above the template's 24-block height).
    private static final BlockPos BATTERY_POS = new BlockPos(20, 16, 20);
    private static final BigInteger FEE = BigInteger.valueOf(40_000_000L); // Two real furnaces.
    private static final BigInteger FUNDS = FEE.multiply(BigInteger.valueOf(25));

    private MiniatureRuntimeGameTests() {}

    @GameTest(template = "empty", batch = "miniature_runtime", timeoutTicks = 60)
    public static void ordinaryBatteryTicksPayTwentyTimesAndCommitOutputsOnlyOnce(GameTestHelper helper) {
        Fixture fixture = fixture(helper, line(helper), FUNDS);
        fixture.tick();
        RunningBatch started = requireBatch(helper, fixture);
        helper.assertTrue(started.parallelism() == 2 && started.energyPerTick().equals(FEE)
                && started.outputs().amount(key(helper, Items.IRON_INGOT)).equals(BigInteger.valueOf(4)),
                "The real compiled furnace line did not reserve four ingots at two-way parallelism");
        long firstPayment = helper.getLevel().getGameTime();

        observe(helper, 24, elapsed -> {
            int paid = Math.min(elapsed + 1, 20);
            // Observe BEFORE another explicit call: later progress must come from the battery's ticker.
            assertBatch(helper, fixture, started, paid);
            assertEnergy(helper, fixture, FUNDS.subtract(FEE.multiply(BigInteger.valueOf(paid))));
            helper.assertTrue(storage(fixture).lastPaidGameTime() == firstPayment + paid - 1,
                    "Payment timestamp did not follow actual server game time");
            assertResources(helper, fixture, 0, elapsed < 19 ? 5 : elapsed <= 20 ? 9 : 0, 3);
            assertRepeatedTickIsIdle(helper, fixture);
            if (elapsed == 20) {
                ItemStack extracted = fixture.tile().extractOutput(key(helper, Items.IRON_INGOT), 64, false);
                helper.assertTrue(extracted.is(Items.IRON_INGOT) && extracted.getCount() == 9,
                        "Completion failed to merge four products with five previously committed ingots");
                assertRepeatedTickIsIdle(helper, fixture);
            }
            if (elapsed == 24) helper.succeed();
        });
    }

    @GameTest(template = "empty", batch = "miniature_runtime", timeoutTicks = 60)
    public static void insufficientEnergyPreservesInputsThenPausesAndResumesTheSameBatch(GameTestHelper helper) {
        BigInteger shortPayment = FEE.subtract(BigInteger.ONE);
        Fixture fixture = fixture(helper, line(helper), shortPayment);
        CompoundTag unstarted = fixture.tile().factoryState().save();
        fixture.tick();
        RunningBatch[] started = {null};
        CompoundTag[] paused = {null};

        observe(helper, 31, elapsed -> {
            int paid = elapsed <= 2 ? 0 : elapsed <= 7 ? elapsed - 2 : elapsed <= 12 ? 5 : Math.min(elapsed - 7, 20);
            if (elapsed == 3) started[0] = requireBatch(helper, fixture);
            if (paid == 0) {
                helper.assertTrue(unstarted.equals(fixture.tile().factoryState().save()),
                        "An unpaid first tick consumed inputs, reserved products, or changed the saved state");
            } else {
                assertBatch(helper, fixture, started[0], paid);
            }
            int fundedPayments = elapsed <= 2 ? 0 : elapsed <= 12 ? 5 : 20;
            assertEnergy(helper, fixture, shortPayment.add(FEE.multiply(BigInteger.valueOf(fundedPayments - paid))));
            assertResources(helper, fixture, paid == 0 ? 4 : 0, paid == 20 ? 9 : 5, 3);
            if (elapsed == 7) paused[0] = fixture.tile().factoryState().save();
            if (elapsed <= 2 || elapsed >= 8 && elapsed <= 12) {
                helper.assertTrue(fixture.tile().runtimePreview().status() == FactoryRunner.Status.NO_ENERGY,
                        "A partial payment was accepted or the no-energy pause was not reported");
                if (elapsed >= 8) helper.assertTrue(paused[0].equals(fixture.tile().factoryState().save()),
                        "An unpowered server tick changed the batch identity, reservation, or paid progress");
            }
            assertRepeatedTickIsIdle(helper, fixture);
            if (elapsed == 2 || elapsed == 12) {
                setEnergy(helper, fixture.battery(), shortPayment.add(FEE.multiply(BigInteger.valueOf(elapsed == 2 ? 5 : 15))));
                assertRepeatedTickIsIdle(helper, fixture);
            }
            if (elapsed == 31) helper.succeed();
        });
    }

    @GameTest(template = "empty", batch = "miniature_runtime", timeoutTicks = 60)
    public static void normalBlockEntityReloadResumesPaidBatchWithoutCompilationOrDuplicateOutputs(GameTestHelper helper) {
        Fixture fixture = fixture(helper, line(helper), FUNDS);
        fixture.tick();
        RunningBatch started = requireBatch(helper, fixture);
        long firstPayment = helper.getLevel().getGameTime();

        observe(helper, 24, elapsed -> {
            int paid = Math.min(elapsed + 1, 20);
            assertBatch(helper, fixture, started, paid);
            assertEnergy(helper, fixture, FUNDS.subtract(FEE.multiply(BigInteger.valueOf(paid))));
            assertResources(helper, fixture, 0, paid == 20 ? 9 : 5, 3);
            helper.assertTrue(storage(fixture).lastPaidGameTime() == firstPayment + paid - 1,
                    "Reload lost the persisted last-payment timestamp");
            if (elapsed == 6 || elapsed == 19) {
                reload(helper, fixture);
                helper.assertTrue(fixture.tile().compilationFor(fixture.tile().factoryState()) == null,
                        "Reload unexpectedly retained an executable compilation cache");
                assertBatch(helper, fixture, started, paid);
                assertResources(helper, fixture, 0, paid == 20 ? 9 : 5, 3);
            }
            assertRepeatedTickIsIdle(helper, fixture);
            if (elapsed == 24) helper.succeed();
        });
    }

    @GameTest(template = "empty", batch = "miniature_runtime", timeoutTicks = 60)
    public static void reservedOutputUnionRejectsOverflowAndCommitsAtTheDistinctResourceLimit(GameTestHelper helper) {
        ResourceInventory full = distinctOutputs(helper, FactoryLimits.MAX_BOUNDARY_RESOURCES);
        Fixture fixture = fixture(helper, line(helper).withOutputs(full), FUNDS);
        CompoundTag blocked = fixture.tile().factoryState().save();
        fixture.tick();
        helper.assertTrue(fixture.tile().runtimePreview().status() == FactoryRunner.Status.OUTPUT_FULL
                && blocked.equals(fixture.tile().factoryState().save()), "A new output kind started in a full inventory");
        assertEnergy(helper, fixture, FUNDS);
        ResourceKey freed = full.amounts().keySet().iterator().next();
        helper.assertTrue(fixture.tile().extractOutput(freed, 1, false).getCount() == 1, "Could not free one output kind");
        ResourceInventory remaining = storage(fixture).outputs();
        assertRepeatedTickIsIdle(helper, fixture);
        RunningBatch[] started = {null};

        observe(helper, 24, elapsed -> {
            if (elapsed == 0) return;
            int paid = Math.min(elapsed, 20);
            if (elapsed == 1) {
                started[0] = requireBatch(helper, fixture);
                FactoryState before = fixture.tile().factoryState();
                try {
                    storage(fixture).withOutputs(full);
                    helper.fail("Committed plus reserved outputs accepted 257 distinct resources");
                } catch (ModelValidationException exception) {
                    helper.assertTrue(exception.diagnostic().code() == FactoryDiagnostic.Code.RESOURCE_LIMIT_EXCEEDED,
                            "Output union overflow reported the wrong diagnostic");
                }
                helper.assertTrue(fixture.tile().factoryState() == before,
                        "A rejected output reservation mutated the live factory");
                FactoryStorage preview = storage(fixture).completionPreview();
                helper.assertTrue(preview.outputs().amounts().size() == FactoryLimits.MAX_BOUNDARY_RESOURCES
                        && preview.outputs().amount(key(helper, Items.IRON_INGOT)).equals(BigInteger.valueOf(4))
                        && storage(fixture).outputs().equals(remaining),
                        "Completion preview dropped reserved outputs or exposed them before payment");
            }
            assertBatch(helper, fixture, started[0], paid);
            assertEnergy(helper, fixture, FUNDS.subtract(FEE.multiply(BigInteger.valueOf(paid))));
            ResourceInventory expected = paid == 20
                    ? remaining.insert(new ResourceAmount(key(helper, Items.IRON_INGOT), BigInteger.valueOf(4))) : remaining;
            helper.assertTrue(storage(fixture).outputs().equals(expected),
                    "Completion did not preserve the full distinct-resource union and its quantities");
            assertRepeatedTickIsIdle(helper, fixture);
            if (elapsed == 24) helper.succeed();
        });
    }

    @GameTest(template = "empty", batch = "miniature_runtime", timeoutTicks = 60)
    public static void outputQuantityCapacityLimitsParallelismAndBlocksTheFollowingBatch(GameTestHelper helper) {
        ResourceKey ingot = key(helper, Items.IRON_INGOT);
        BigInteger initial = FactoryLimits.MAX_INVENTORY_AMOUNT.subtract(BigInteger.valueOf(3));
        Fixture fixture = fixture(helper, line(helper).withOutputs(new ResourceInventory(Map.of(ingot, initial))), FUNDS);
        fixture.tick();
        RunningBatch started = requireBatch(helper, fixture);
        helper.assertTrue(started.parallelism() == 1 && started.outputs().amount(ingot).equals(BigInteger.TWO),
                "Output quantity capacity did not limit the batch to one parallel operation");
        FactoryState before = fixture.tile().factoryState();
        try {
            storage(fixture).withOutputs(new ResourceInventory(Map.of(ingot, FactoryLimits.MAX_INVENTORY_AMOUNT.subtract(BigInteger.ONE))));
            helper.fail("Committed plus reserved output quantity exceeded the inventory limit");
        } catch (ModelValidationException exception) {
            helper.assertTrue(fixture.tile().factoryState() == before, "Rejected quantity overflow changed live state");
        }
        observe(helper, 24, elapsed -> {
            int paid = Math.min(elapsed + 1, 20);
            assertBatch(helper, fixture, started, paid);
            assertEnergy(helper, fixture, FUNDS.subtract(FactoryRules.energyPerTick(1).multiply(BigInteger.valueOf(paid))));
            helper.assertTrue(storage(fixture).inputs().amount(key(helper, Items.IRON_ORE)).equals(BigInteger.TWO)
                    && storage(fixture).outputs().amount(ingot).equals(paid == 20 ? initial.add(BigInteger.TWO) : initial),
                    "Capacity-limited production consumed excess inputs, lost outputs, or overfilled inventory");
            if (elapsed >= 20) helper.assertTrue(fixture.tile().runtimePreview().status() == FactoryRunner.Status.OUTPUT_FULL,
                    "A second batch started despite insufficient output quantity capacity");
            assertRepeatedTickIsIdle(helper, fixture);
            if (elapsed == 24) helper.succeed();
        });
    }

    /** Shared with S08 tests: a real assembled tier-three battery and compiled, enabled factory. */
    static Fixture fixture(GameTestHelper helper, FactoryStorage storage, BigInteger energy) {
        helper.setBlock(BATTERY_POS, RegistryBlocks.QUANTUM_BATTERY_CONTROLLER.get().defaultBlockState());
        TileQuantumBatteryController battery = (TileQuantumBatteryController) helper.getLevel().getBlockEntity(helper.absolutePos(BATTERY_POS));
        helper.assertTrue(battery != null, "Battery controller was not placed");
        CompoundTag core = new CompoundTag();
        battery.writeCustomTag(core, FluxConstants.NBT_SAVE_ALL);
        core.putInt("CoreLevel", 3);
        battery.readCustomTag(core, FluxConstants.NBT_SAVE_ALL);
        int pluginRadius = QuantumBatteryStructure.stabilizerSlotRadius(3);
        for (int tier = 1; tier <= 3; tier++) {
            int radius = QuantumBatteryStructure.stabilizerSlotRadius(tier);
            for (QuantumBatteryStructure.Cell cell : QuantumBatteryStructure.layer(tier).cells()) {
                var offset = cell.offset();
                Block block = QuantumBatteryAssembler.expectedBlock(tier, cell.type());
                if (offset.equals(new QuantumBatteryStructure.Offset(radius, 0, 0))) {
                    block = RegistryBlocks.QUANTUM_ENERGY_CONSTRAINT_INTERFACE.get();
                } else if (tier == 3 && offset.equals(new QuantumBatteryStructure.Offset(-pluginRadius, 0, 0))) {
                    block = RegistryBlocks.MINIATURE_UNIVERSE.get();
                }
                helper.setBlock(BATTERY_POS.offset(offset.x(), offset.y(), offset.z()), block.defaultBlockState());
            }
        }
        battery.validateStructure();
        setEnergy(helper, battery, energy);
        Fixture fixture = new Fixture(battery, helper.absolutePos(BATTERY_POS.offset(-pluginRadius, 0, 0)));
        helper.assertTrue(fixture.tile() != null && battery.getEntanglementEndpoints().stream()
                .anyMatch(endpoint -> endpoint.ringTier() == 3 && endpoint.absolutePos().equals(fixture.pluginPos())),
                "Factory is missing from the real tier-three processing endpoints");
        FactoryState state = FactoryState.of(storage.withControls(true, RedstoneMode.IGNORE), helper.getLevel().registryAccess());
        helper.assertTrue(fixture.tile().applyPreparedState(fixture.tile().factoryState(), state), "Could not prepare runtime inputs");
        var catalog = MiniatureRecipeCatalog.get(helper.getLevel().getServer());
        var compiled = catalog.compile(storage.layout(), helper.getLevel());
        helper.assertTrue(compiled.successful(), "Runtime fixture compilation failed: " + compiled.diagnostic());
        helper.assertTrue(fixture.tile().applyCompilation(state, compiled, catalog.generation()), "Could not install runtime compilation");
        return fixture;
    }

    static void setEnergy(GameTestHelper helper, TileQuantumBatteryController battery, BigInteger energy) {
        CompoundTag data = new CompoundTag();
        battery.writeCustomTag(data, FluxConstants.NBT_SAVE_ALL);
        data.putByteArray("QuantumEnergy", energy.toByteArray());
        battery.readCustomTag(data, FluxConstants.NBT_SAVE_ALL);
        // Reading controller NBT clears its endpoint cache; rebuild it from the actual blocks.
        battery.validateStructure();
        helper.assertTrue(battery.isOperational() && battery.getStructureTier() == 3
                && battery.areStructureChunksLoadedForRender() && energy.equals(battery.getStoredEnergy()),
                "Runtime battery did not form in loaded chunks or restore its exact energy budget");
    }

    static FactoryStorage line(GameTestHelper helper) {
        var registries = helper.getLevel().registryAccess();
        return FactoryStorage.empty().insertMachine(new GridPosition(1, 0), new ItemStack(Items.FURNACE, 2), registries).storage()
                .withConfiguration(new GridPosition(0, 0), new FactoryCell.Marker(
                        new ResourceAmount(key(helper, Items.IRON_ORE), BigInteger.TWO), GridDirection.RIGHT))
                .withConfiguration(new GridPosition(2, 0), new FactoryCell.Arrow())
                .insertInput(new ItemStack(Items.IRON_ORE, 4), registries).storage()
                .withOutputs(new ResourceInventory(Map.of(key(helper, Items.IRON_INGOT), BigInteger.valueOf(5),
                        key(helper, Items.DIAMOND), BigInteger.valueOf(3))));
    }

    private static ResourceInventory distinctOutputs(GameTestHelper helper, int count) {
        var amounts = new LinkedHashMap<ResourceKey, BigInteger>();
        for (int i = 0; i < count; i++) {
            ItemStack stack = new ItemStack(Items.PAPER);
            stack.set(DataComponents.CUSTOM_NAME, Component.literal("Committed output " + i));
            amounts.put(FactoryItemResources.keyOf(stack, helper.getLevel().registryAccess()), BigInteger.ONE);
        }
        return new ResourceInventory(amounts);
    }

    static void reload(GameTestHelper helper, Fixture fixture) {
        TileMiniatureUniverse original = fixture.tile();
        CompoundTag saved = original.saveWithFullMetadata(helper.getLevel().registryAccess());
        BlockEntity restored = BlockEntity.loadStatic(original.getBlockPos(), original.getBlockState(), saved.copy(),
                helper.getLevel().registryAccess());
        helper.assertTrue(restored instanceof TileMiniatureUniverse, "Normal block entity loading rejected the saved batch");
        helper.getLevel().removeBlockEntity(original.getBlockPos());
        helper.getLevel().setBlockEntity(restored);
        helper.assertTrue(original.isRemoved() && fixture.tile() == restored
                && saved.equals(restored.saveWithFullMetadata(helper.getLevel().registryAccess())),
                "Block entity replacement did not preserve ordinary saved state exactly");
        BigInteger energy = fixture.battery().getStoredEnergy();
        original.tickFromQuantumBattery(fixture.battery());
        helper.assertTrue(energy.equals(fixture.battery().getStoredEnergy()), "Removed factory still charged the battery");
    }

    private static void observe(GameTestHelper helper, int lastTick, IntConsumer observation) {
        long started = helper.getLevel().getGameTime();
        for (int elapsed = 0; elapsed <= lastTick; elapsed++) {
            int tick = elapsed;
            helper.runAfterDelay(tick, () -> {
                helper.assertTrue(helper.getLevel().getGameTime() == started + tick, "Observation did not run on its actual server tick");
                observation.accept(tick);
            });
        }
    }

    private static void assertRepeatedTickIsIdle(GameTestHelper helper, Fixture fixture) {
        CompoundTag before = fixture.tile().factoryState().save();
        BigInteger energy = fixture.battery().getStoredEnergy();
        for (int repeat = 0; repeat < 3; repeat++) fixture.tick();
        helper.assertTrue(before.equals(fixture.tile().factoryState().save()) && energy.equals(fixture.battery().getStoredEnergy()),
                "Repeated ordinary calls in the same server tick charged twice or changed factory assets");
    }

    private static RunningBatch requireBatch(GameTestHelper helper, Fixture fixture) {
        RunningBatch batch = storage(fixture).batch();
        helper.assertTrue(batch != null, "The ordinary battery interface failed to start a paid batch");
        return batch;
    }

    private static void assertBatch(GameTestHelper helper, Fixture fixture, RunningBatch started, int paid) {
        RunningBatch batch = storage(fixture).batch();
        if (paid == FactoryRules.BATCH_TICKS) {
            helper.assertTrue(batch == null, "The twentieth payment did not clear the completed batch");
        } else {
            helper.assertTrue(batch != null && batch.paidTicks() == paid,
                    "Expected " + paid + " actual paid ticks, got " + (batch == null ? "no batch" : batch.paidTicks()));
            helper.assertTrue(batch.id().equals(started.id()) && batch.fingerprint().equals(started.fingerprint())
                    && batch.inputs().equals(started.inputs()) && batch.outputs().equals(started.outputs())
                    && batch.parallelism() == started.parallelism() && batch.environment().equals(started.environment())
                    && batch.energyPerTick().equals(started.energyPerTick()),
                    "Production changed the locked batch identity, resource reservation, environment, or fee");
        }
    }

    private static void assertResources(GameTestHelper helper, Fixture fixture, int ore, int ingots, int diamonds) {
        helper.assertTrue(storage(fixture).inputs().amount(key(helper, Items.IRON_ORE)).equals(BigInteger.valueOf(ore))
                && storage(fixture).outputs().amount(key(helper, Items.IRON_INGOT)).equals(BigInteger.valueOf(ingots))
                && storage(fixture).outputs().amount(key(helper, Items.DIAMOND)).equals(BigInteger.valueOf(diamonds)),
                "Consumed inputs, newly committed products, or unrelated existing outputs changed unexpectedly");
    }

    private static void assertEnergy(GameTestHelper helper, Fixture fixture, BigInteger expected) {
        helper.assertTrue(fixture.battery().getStoredEnergy().equals(expected),
                "Expected battery FE " + expected + ", got " + fixture.battery().getStoredEnergy());
    }

    private static FactoryStorage storage(Fixture fixture) { return fixture.tile().factoryState().storage().orElseThrow(); }
    private static ResourceKey key(GameTestHelper helper, Item item) {
        return FactoryItemResources.keyOf(new ItemStack(item), helper.getLevel().registryAccess());
    }

    record Fixture(TileQuantumBatteryController battery, BlockPos pluginPos) {
        TileMiniatureUniverse tile() { return (TileMiniatureUniverse) battery.getLevel().getBlockEntity(pluginPos); }
        void tick() { ((QuantumBatteryProcessingPlugin) tile()).tickFromQuantumBattery(battery); }
    }
}
