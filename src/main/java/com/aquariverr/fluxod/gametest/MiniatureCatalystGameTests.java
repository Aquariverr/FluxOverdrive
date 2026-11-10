package com.aquariverr.fluxod.gametest;

import com.aquariverr.fluxod.FluxOverdrive;
import com.aquariverr.fluxod.block.entity.TileMiniatureUniverse;
import com.aquariverr.fluxod.gametest.MiniatureRuntimeGameTests.Fixture;
import com.aquariverr.fluxod.integration.mekanism.QuantumCoolingChemicalIntegration;
import com.aquariverr.fluxod.miniature.compile.CompilationResult;
import com.aquariverr.fluxod.miniature.compile.CompiledFactory;
import com.aquariverr.fluxod.miniature.model.*;
import com.aquariverr.fluxod.miniature.recipe.MiniatureRecipeCatalog;
import com.aquariverr.fluxod.miniature.runtime.*;
import com.aquariverr.fluxod.miniature.storage.*;
import com.aquariverr.fluxod.network.MiniatureFactoryCommandPayload;
import com.aquariverr.fluxod.register.FluxOdDataComponents;
import com.aquariverr.fluxod.register.RegistryBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.DirectionalPlaceContext;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.math.BigInteger;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.IntConsumer;

/** Explicit compiled catalyst contracts exercise S07; native adapter support is tested separately. */
@GameTestHolder(FluxOverdrive.MODID)
@PrefixGameTestTemplate(false)
public final class MiniatureCatalystGameTests {
    private static final BigInteger FEE = FactoryRules.energyPerTick(2);
    private static final FactoryRules.Environment MAX_ENVIRONMENT = new FactoryRules.Environment(1, 10);

    @GameTest(template = "empty", batch = "miniature_catalyst")
    public static void catalystShortageLimitsParallelismAndSharesAvailabilityWithConsumedInputs(GameTestHelper h) {
        ResourceKey mold = mold(h), ore = key(h, new ItemStack(Items.IRON_ORE));
        FactoryStorage source = line(h).withInputs(new ResourceInventory(Map.of(ore, BigInteger.valueOf(4000), mold, BigInteger.valueOf(7))));
        var compiled = contract(h, source, mold, 2);
        var step = FactoryRunner.prepare(source, compiled, compiled.recipeGeneration(), MAX_ENVIRONMENT, 1);
        var batch = step.storage().batch();
        h.assertTrue(batch.parallelism() == 3 && batch.catalysts().amount(mold).equals(BigInteger.valueOf(6))
                && batch.inputs().amount(ore).equals(BigInteger.valueOf(6)) && step.charge().equals(FactoryRules.energyPerTick(1))
                && step.storage().inputs().amount(mold).equals(BigInteger.ONE), "Catalyst shortage did not cap P=2000 at three with only one active machine");
        source = source.withInputs(new ResourceInventory(Map.of(ore, BigInteger.valueOf(7))));
        compiled = contract(h, source, ore, 2);
        step = FactoryRunner.prepare(source, compiled, compiled.recipeGeneration(), MAX_ENVIRONMENT, 1);
        h.assertTrue(step.storage().batch().parallelism() == 1 && step.storage().inputs().amount(ore).equals(BigInteger.valueOf(3))
                && step.storage().batch().inputs().amount(ore).equals(BigInteger.TWO)
                && step.storage().batch().catalysts().amount(ore).equals(BigInteger.TWO), "The same ore funded both consumption and a catalyst reservation");
        var finished = finish(step.storage());
        h.assertTrue(finished.inputs().amount(ore).equals(BigInteger.valueOf(5))
                && finished.outputs().amount(key(h, new ItemStack(Items.IRON_INGOT))).equals(BigInteger.valueOf(7)),
                "Completion refunded consumed ore or sent catalysts to the product inventory");
        source = source.insertInput(new ItemStack(Items.STICK, 64), h.getLevel().registryAccess()).storage();
        compiled = contract(h, source, mold, 2);
        step = FactoryRunner.prepare(source, compiled, compiled.recipeGeneration(), MAX_ENVIRONMENT, 1);
        h.assertTrue(step.storage() == source && step.charge().signum() == 0 && step.status() == FactoryRunner.Status.MISSING_INPUT,
                "Plain sticks substituted for exact named catalysts or missing catalysts changed assets");
        h.succeed();
    }

    @GameTest(template = "empty", batch = "miniature_catalyst")
    public static void failedFirstPaymentAndFullOutputNeverLockCatalysts(GameTestHelper h) {
        Fixture f = fixture(h, FEE.subtract(BigInteger.ONE));
        FactoryState before = f.tile().factoryState();
        f.tick();
        h.assertTrue(before == f.tile().factoryState() && f.battery().getStoredEnergy().equals(FEE.subtract(BigInteger.ONE))
                && f.tile().runtimePreview().status() == FactoryRunner.Status.NO_ENERGY, "Unpaid start removed or locked catalysts");
        var source = storage(f).withOutputs(new ResourceInventory(Map.of(key(h, new ItemStack(Items.IRON_INGOT)), FactoryLimits.MAX_INVENTORY_AMOUNT)));
        var compiled = contract(h, source, mold(h), 2);
        var step = FactoryRunner.prepare(source, compiled, compiled.recipeGeneration(), MAX_ENVIRONMENT, 1);
        h.assertTrue(step.storage() == source && step.charge().signum() == 0 && step.status() == FactoryRunner.Status.OUTPUT_FULL,
                "Failed output reservation locked catalysts");
        h.succeed();
    }

    @GameTest(template = "empty", batch = "miniature_catalyst", timeoutTicks = 80)
    public static void pausedCatalystsSurviveRealBreakAndReloadThenReturnExactlyOnce(GameTestHelper h) {
        Fixture f = fixture(h, FEE.multiply(BigInteger.valueOf(5)));
        f.tick();
        RunningBatch first = storage(f).batch();
        breakAndPlace(h, f);
        observe(h, 29, elapsed -> {
            int paid = elapsed <= 4 ? elapsed + 1 : elapsed <= 10 ? 5 : Math.min(elapsed - 5, 20);
            h.assertTrue(f.battery().getStoredEnergy().equals(FEE.multiply(BigInteger.valueOf((elapsed <= 10 ? 5 : 20) - paid))),
                    "Catalyst resume charged the wrong number of payments");
            if (paid < 20) {
                h.assertTrue(storage(f).batch().id().equals(first.id()) && storage(f).batch().paidTicks() == paid
                        && storage(f).batch().catalysts().equals(first.catalysts()), "Pause or reload changed the catalyst batch");
                h.assertTrue(f.tile().extractInput(mold(h), 64, true).isEmpty() && f.tile().extractInput(mold(h), 64, false).isEmpty()
                        && f.tile().extractOutput(mold(h), 64, true).isEmpty(), "Locked catalyst was extractable");
                var chunk = h.getLevel().getChunkAt(f.pluginPos());
                chunk.setUnsaved(false);
                FactoryState before = f.tile().factoryState();
                for (int slot = 0; slot < f.tile().itemHandler().getSlots(); slot++) {
                    h.assertTrue(!f.tile().itemHandler().getStackInSlot(slot).is(Items.STICK), "Capability exposed a locked catalyst");
                    f.tile().itemHandler().extractItem(slot, 64, true);
                }
                h.assertTrue(f.tile().itemHandler().insertItem(0, moldItem(), true).isEmpty()
                        && before == f.tile().factoryState() && !chunk.isUnsaved(), "Catalyst simulation changed state or dirtied the chunk");
                f.tile().setChanged();
                if (elapsed == 8) load(h, f.tile(), storage(f));
                if (elapsed == 10) MiniatureRuntimeGameTests.setEnergy(h, f.battery(), FEE.multiply(BigInteger.valueOf(15)));
            } else {
                h.assertTrue(storage(f).batch() == null && storage(f).outputs().amount(key(h, new ItemStack(Items.IRON_INGOT))).equals(BigInteger.valueOf(9)),
                        "Twentieth payment did not commit outputs and release catalysts");
                h.assertTrue(storage(f).inputs().amount(mold(h)).equals(BigInteger.valueOf(elapsed == 25 ? 4 : 2)), "Catalysts were lost or returned twice");
                if (elapsed == 25) h.assertTrue(f.tile().extractInput(mold(h), 2, false).getCount() == 2, "Returned catalysts could not be recovered");
            }
            assertRepeatedTick(h, f);
            if (elapsed == 29) h.succeed();
        });
    }

    @GameTest(template = "empty", batch = "miniature_catalyst")
    public static void itemCapabilityAndGuiLeaveCapacityForCatalystReturn(GameTestHelper h) {
        ResourceKey mold = mold(h);
        var source = startedWithCatalyst(h, mold, 2, FactoryLimits.MAX_INVENTORY_AMOUNT.subtract(BigInteger.valueOf(3)));
        TileMiniatureUniverse tile = MiniatureFluidCapabilityGameTests.fixture(h, source);
        var before = tile.factoryState();
        ItemStack offered = moldItem().copyWithCount(5);
        h.assertTrue(tile.itemHandler().insertItem(0, offered, true).getCount() == 2 && tile.factoryState() == before,
                "Item simulation counted reserved return space as free");
        var command = new MiniatureFactoryCommandPayload(1, UUID.randomUUID(), tile.getBlockPos(), source.layout().revision(), 1,
                MiniatureFactoryCommandPayload.Action.INSERT_INPUT, 0, 0, BigInteger.valueOf(5), GridDirection.RIGHT,
                UUID.randomUUID(), -1, null, null);
        var prepared = FactoryEditing.prepare(before, offered, command, h.getLevel().registryAccess());
        h.assertTrue(prepared.carried().getCount() == 2 && prepared.state().storage().orElseThrow().inputCapacity(mold).signum() == 0
                && tile.factoryState() == before, "GUI preparation ignored the catalyst return reservation");
        h.assertTrue(tile.itemHandler().insertItem(0, offered, false).getCount() == 2
                && tile.itemHandler().insertItem(0, offered, false).getCount() == 5, "Automation overfilled the catalyst return space");
        var finished = finish(tile.factoryState().storage().orElseThrow());
        h.assertTrue(finished.inputs().amount(mold).equals(FactoryLimits.MAX_INVENTORY_AMOUNT), "Returning catalysts overflowed or lost input inventory");
        h.succeed();
    }

    @GameTest(template = "empty", batch = "miniature_catalyst")
    public static void fluidCatalystCapacityAndExtractionUseTheSameReservation(GameTestHelper h) {
        var water = new ResourceKey(ResourceKind.FLUID, "minecraft:water");
        var source = startedWithCatalyst(h, water, 1000, FactoryLimits.MAX_INVENTORY_AMOUNT.subtract(BigInteger.valueOf(7)));
        var tile = MiniatureFluidCapabilityGameTests.fixture(h, source);
        var before = tile.factoryState();
        var handler = tile.fluidHandler();
        h.assertTrue(handler.fill(new FluidStack(Fluids.WATER, 9), FluidAction.SIMULATE) == 7
                && handler.drain(new FluidStack(Fluids.WATER, 1000), FluidAction.EXECUTE).isEmpty()
                && before == tile.factoryState(), "Fluid catalyst simulation changed assets or exposed a reservation");
        h.assertTrue(handler.fill(new FluidStack(Fluids.WATER, 9), FluidAction.EXECUTE) == 7
                && handler.fill(new FluidStack(Fluids.WATER, 1), FluidAction.EXECUTE) == 0, "Fluid filling occupied return capacity");
        h.assertTrue(finish(tile.factoryState().storage().orElseThrow()).inputs().amount(water).equals(FactoryLimits.MAX_INVENTORY_AMOUNT),
                "Fluid catalysts were lost on completion");
        h.succeed();
    }

    @GameTest(template = "empty", batch = "miniature_catalyst")
    public static void chemicalCatalystCapacityAndExtractionUseTheNativeHandler(GameTestHelper h) {
        if (QuantumCoolingChemicalIntegration.isAvailable()) {
            com.aquariverr.fluxod.integration.mekanism.MiniatureChemicalVerification.catalystReservation(h);
        }
        h.succeed();
    }

    @GameTest(template = "empty", batch = "miniature_catalyst")
    public static void returnResourceKindsAndMalformedSavesCannotOverfillInputs(GameTestHelper h) {
        ResourceKey mold = mold(h);
        var active = startedWithCatalyst(h, mold, 2, BigInteger.valueOf(4));
        var full = new LinkedHashMap<ResourceKey, BigInteger>();
        for (int i = 0; i < FactoryLimits.MAX_BOUNDARY_RESOURCES - 1; i++) {
            ItemStack named = new ItemStack(Items.PAPER);
            named.set(DataComponents.CUSTOM_NAME, Component.literal("Unrelated input " + i));
            full.put(key(h, named), BigInteger.ONE);
        }
        active = active.withInputs(new ResourceInventory(full));
        h.assertTrue(active.inputCapacity(key(h, new ItemStack(Items.DIAMOND))).signum() == 0
                && finish(active).inputs().amounts().size() == FactoryLimits.MAX_BOUNDARY_RESOURCES, "Locked catalyst did not reserve a resource kind");
        CompoundTag malformed = FactoryState.of(active, h.getLevel().registryAccess()).save();
        full.put(key(h, new ItemStack(Items.DIAMOND)), BigInteger.ONE);
        malformed.put("inputs", ResourceNbtCodec.writeInventory(new ResourceInventory(full)));
        FactoryState rejected = FactoryState.load(malformed, h.getLevel().registryAccess());
        h.assertTrue(rejected.storage().isEmpty() && rejected.save().equals(malformed), "Overfull catalyst-return save was accepted or erased");
        malformed.put("inputs", ResourceNbtCodec.writeInventory(new ResourceInventory(Map.of(mold, FactoryLimits.MAX_INVENTORY_AMOUNT))));
        rejected = FactoryState.load(malformed, h.getLevel().registryAccess());
        h.assertTrue(rejected.storage().isEmpty() && rejected.save().equals(malformed), "Overfull catalyst amount was accepted or erased");
        h.succeed();
    }

    @GameTest(template = "empty", batch = "miniature_catalyst", timeoutTicks = 90)
    public static void catalystRestartBeforeFirstPayment(GameTestHelper h) { restart(h, 0); }

    @GameTest(template = "empty", batch = "miniature_catalyst", timeoutTicks = 90)
    public static void catalystRestartAtOnePayment(GameTestHelper h) { restart(h, 1); }
    @GameTest(template = "empty", batch = "miniature_catalyst", timeoutTicks = 90)
    public static void catalystRestartAtNineteenPayments(GameTestHelper h) { restart(h, 19); }
    @GameTest(template = "empty", batch = "miniature_catalyst", timeoutTicks = 90)
    public static void catalystRestartAfterCompletion(GameTestHelper h) { restart(h, 20); }

    private static void restart(GameTestHelper h, int paid) {
        BlockPos pos = new BlockPos(3500 + paid * 32, 90, 3500);
        h.getLevel().getChunkAt(pos);
        Fixture f = fixture(h, FEE.multiply(BigInteger.valueOf(20)));
        if (System.getProperty("flux_overdrive.miniaturePersistencePhase", "write").equals("write")) {
            if (paid > 0) f.tick();
            h.runAfterDelay(Math.max(0, paid - 1), () -> {
                h.assertTrue((paid == 0 || paid == 20) ? storage(f).batch() == null : storage(f).batch().paidTicks() == paid,
                        "Wrong real payment boundary for catalyst persistence");
                h.getLevel().setBlock(pos, RegistryBlocks.MINIATURE_UNIVERSE.get().defaultBlockState(), 3);
                var saved = (TileMiniatureUniverse) h.getLevel().getBlockEntity(pos);
                load(h, saved, storage(f).withControls(false, RedstoneMode.IGNORE));
                saved.setChanged();
                h.succeed();
            });
        } else {
            h.assertTrue(h.getLevel().getBlockEntity(pos) instanceof TileMiniatureUniverse, "Missing catalyst fixture from independent write process");
            var saved = ((TileMiniatureUniverse) h.getLevel().getBlockEntity(pos)).factoryState().storage().orElseThrow();
            boolean active = paid > 0 && paid < 20;
            h.assertTrue(!saved.enabled() && saved.inputs().amount(mold(h)).equals(BigInteger.valueOf(active ? 0 : 4))
                    && saved.inputs().amount(key(h, new ItemStack(Items.IRON_ORE))).equals(BigInteger.valueOf(paid == 0 ? 4 : 0))
                    && saved.outputs().amount(key(h, new ItemStack(Items.IRON_INGOT))).equals(BigInteger.valueOf(paid == 20 ? 9 : 5))
                    && (active ? saved.batch() != null && saved.batch().paidTicks() == paid
                    && saved.batch().catalysts().amount(mold(h)).equals(BigInteger.valueOf(4)) : saved.batch() == null),
                    "Restart lost the catalyst lock or returned it early");
            load(h, f.tile(), saved.withControls(true, RedstoneMode.IGNORE));
            if (paid == 0) publish(h, f, contract(h, storage(f), mold(h), 2));
            int remaining = 20 - paid;
            f.tick();
            observe(h, remaining + 2, elapsed -> {
                int charges = Math.min(elapsed + 1, remaining);
                h.assertTrue(f.battery().getStoredEnergy().equals(FEE.multiply(BigInteger.valueOf(20 - charges))), "Restart charged paid catalyst progress again");
                h.assertTrue(storage(f).inputs().amount(mold(h)).equals(BigInteger.valueOf(charges == remaining ? 4 : 0)), "Restart returned catalysts an incorrect number of times");
                h.assertTrue(storage(f).outputs().amount(key(h, new ItemStack(Items.IRON_INGOT))).equals(BigInteger.valueOf(charges == remaining ? 9 : 5)),
                        "Restart lost or duplicated products alongside catalyst return");
                if (active && storage(f).batch() != null) h.assertTrue(saved.batch().id().equals(storage(f).batch().id()), "Restart changed batch identity");
                assertRepeatedTick(h, f);
                if (elapsed == remaining + 2) h.succeed();
            });
        }
    }

    public static FactoryStorage startedWithCatalyst(GameTestHelper h, ResourceKey catalyst, long required, BigInteger available) {
        var source = line(h).withInputs(new ResourceInventory(Map.of(key(h, new ItemStack(Items.IRON_ORE)), BigInteger.valueOf(4), catalyst, available)));
        var compiled = contract(h, source, catalyst, required);
        return FactoryRunner.prepare(source, compiled, compiled.recipeGeneration(), new FactoryRules.Environment(10, 1), 1).storage();
    }

    public static FactoryStorage finish(FactoryStorage storage) {
        for (long time = 2; time <= FactoryRules.BATCH_TICKS && storage.batch() != null; time++) {
            storage = FactoryRunner.prepare(storage, null, 1, null, time).storage();
        }
        if (storage.batch() != null) throw new IllegalStateException("Catalyst fixture did not complete within twenty payments");
        return storage;
    }

    private static CompiledFactory contract(GameTestHelper h, FactoryStorage source, ResourceKey catalyst, long required) {
        var nativeRecipe = MiniatureRecipeCatalog.get(h.getLevel().getServer()).compile(source.layout(), h.getLevel()).factory();
        return new CompiledFactory(source.layout(), nativeRecipe.fingerprint(), nativeRecipe.recipeGeneration(), nativeRecipe.inputs(), nativeRecipe.outputs(),
                List.of(new ResourceAmount(catalyst, BigInteger.valueOf(required))), nativeRecipe.outputRows(), nativeRecipe.recipes());
    }

    private static Fixture fixture(GameTestHelper h, BigInteger energy) {
        var f = MiniatureRuntimeGameTests.fixture(h, line(h), energy);
        publish(h, f, contract(h, storage(f), mold(h), 2));
        h.assertTrue(f.tile().itemHandler().isItemValid(1, moldItem()), "Compiled catalyst was not accepted for automation input");
        return f;
    }
    private static void publish(GameTestHelper h, Fixture f, CompiledFactory compiled) {
        h.assertTrue(f.tile().applyCompilation(f.tile().factoryState(), CompilationResult.success(compiled), compiled.recipeGeneration()), "Could not publish explicit catalyst contract");
    }
    private static FactoryStorage line(GameTestHelper h) {
        return MiniatureRuntimeGameTests.line(h).insertInput(moldItem().copyWithCount(4), h.getLevel().registryAccess()).storage().withControls(true, RedstoneMode.IGNORE);
    }
    private static ItemStack moldItem() {
        var item = new ItemStack(Items.STICK);
        item.set(DataComponents.CUSTOM_NAME, Component.literal("Reusable catalyst"));
        return item;
    }
    private static ResourceKey mold(GameTestHelper h) { return key(h, moldItem()); }
    private static ResourceKey key(GameTestHelper h, ItemStack stack) { return FactoryItemResources.keyOf(stack, h.getLevel().registryAccess()); }
    private static FactoryStorage storage(Fixture f) { return f.tile().factoryState().storage().orElseThrow(); }
    private static void load(GameTestHelper h, TileMiniatureUniverse tile, FactoryStorage storage) {
        var tag = new CompoundTag();
        tag.put(TileMiniatureUniverse.STATE_TAG, FactoryState.of(storage, h.getLevel().registryAccess()).save());
        tile.loadAdditional(tag, h.getLevel().registryAccess());
    }
    private static void breakAndPlace(GameTestHelper h, Fixture f) {
        var before = f.tile().factoryState().save();
        h.assertTrue(h.getLevel().destroyBlock(f.pluginPos(), true), "Could not break catalyst factory");
        var drops = h.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(f.pluginPos()).inflate(1));
        h.assertTrue(drops.size() == 1, "Catalyst batch scattered multiple assets");
        var stack = drops.getFirst().getItem().copy();
        drops.getFirst().discard();
        h.assertTrue(stack.getCount() == 1 && before.equals(stack.get(FluxOdDataComponents.MINIATURE_UNIVERSE_STATE).copyTag()), "Catalyst lock was absent from the unique drop");
        var result = ((BlockItem) stack.getItem()).place(new DirectionalPlaceContext(h.getLevel(), f.pluginPos(), Direction.DOWN, stack, Direction.UP));
        h.assertTrue(result.consumesAction() && stack.isEmpty() && before.equals(f.tile().factoryState().save()), "Placement lost or duplicated catalyst assets");
        f.battery().validateStructure();
    }
    private static void assertRepeatedTick(GameTestHelper h, Fixture f) {
        var state = f.tile().factoryState();
        var energy = f.battery().getStoredEnergy();
        f.tick(); f.tick();
        h.assertTrue(state == f.tile().factoryState() && energy.equals(f.battery().getStoredEnergy()), "Repeated tick paid or returned catalysts twice");
    }
    private static void observe(GameTestHelper h, int last, IntConsumer observation) {
        for (int tick = 0; tick <= last; tick++) { int elapsed = tick; h.runAfterDelay(tick, () -> observation.accept(elapsed)); }
    }
    private MiniatureCatalystGameTests() {}
}
