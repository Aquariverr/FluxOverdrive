package com.aquariverr.fluxod.miniature.storage;

import com.aquariverr.fluxod.miniature.model.*;
import com.aquariverr.fluxod.miniature.runtime.*;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

import java.math.BigInteger;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.*;

class RunningBatchCodecTest {
    @Test void decliningEnergyScheduleSurvivesEveryPaymentBoundary() {
        var base = batch(0);
        var schedule = new BatchEnergySchedule(java.util.List.of(new BatchEnergySchedule.Stage(0, 5),
                new BatchEnergySchedule.Stage(1, 3), new BatchEnergySchedule.Stage(3, 1)));
        for (int paid = 0; paid <= 80; paid++) {
            var running = new RunningBatch(base.id(), base.fingerprint(), base.environment(), base.parallelism(),
                    base.inputs(), base.outputs(), base.catalysts(), FactoryRules.energyPerTick(5), paid, 80, schedule);
            var restored = RunningBatchCodec.read(RunningBatchCodec.write(running));
            assertEquals(running, restored);
            assertEquals(FactoryRules.energyPerTick(paid == 80 ? 0 : paid < 20 ? 5 : paid < 60 ? 3 : 1), restored.currentEnergyPerTick());
            assertEquals(FactoryRules.energyPerBatch(12), restored.totalEnergy());
            if (paid < 80) assertEquals(schedule, restored.paid().energySchedule());
        }
    }

    @Test void malformedEnergySchedulesAreRejected() {
        var base = batch(0);
        var schedule = new BatchEnergySchedule(java.util.List.of(new BatchEnergySchedule.Stage(0, 2), new BatchEnergySchedule.Stage(1, 1)));
        var running = new RunningBatch(base.id(), base.fingerprint(), base.environment(), base.parallelism(),
                base.inputs(), base.outputs(), base.catalysts(), base.energyPerTick(), 0, 40, schedule);
        for (Consumer<CompoundTag> mutation : java.util.List.<Consumer<CompoundTag>>of(
                tag -> tag.putString("energySchedule", "invalid"),
                tag -> tag.put("energySchedule", new net.minecraft.nbt.ListTag()),
                tag -> tag.getList("energySchedule", 10).getCompound(0).putInt("round", 1),
                tag -> tag.getList("energySchedule", 10).getCompound(0).putInt("machines", 3),
                tag -> tag.getList("energySchedule", 10).getCompound(1).putInt("round", 0),
                tag -> tag.getList("energySchedule", 10).getCompound(1).putInt("round", 2),
                tag -> tag.getList("energySchedule", 10).getCompound(1).putInt("machines", 2),
                tag -> tag.getList("energySchedule", 10).getCompound(1).putString("round", "1"),
                tag -> tag.getList("energySchedule", 10).getCompound(1).putInt("extra", 1))) {
            var tag = RunningBatchCodec.write(running); mutation.accept(tag);
            assertThrows(IllegalArgumentException.class, () -> RunningBatchCodec.read(tag));
        }
    }

    private static final ResourceKey ITEM = new ResourceKey(ResourceKind.ITEM, "test:ore");
    private static final ResourceKey OUTPUT = new ResourceKey(ResourceKind.ITEM, "test:ingot");

    private static RunningBatch batch(int paid) {
        return new RunningBatch(new UUID(3, 7), "a".repeat(64), new FactoryRules.Environment(1, 10), 3,
                new ResourceInventory(Map.of(ITEM, BigInteger.valueOf(6))),
                new ResourceInventory(Map.of(OUTPUT, BigInteger.valueOf(9))), FactoryRules.energyPerTick(2), paid);
    }

    @Test void allPaymentBoundariesRoundTripWithoutLosingReservedResources() {
        for (int paid = 0; paid <= 20; paid++) {
            RunningBatch batch = batch(paid);
            assertEquals(batch, RunningBatchCodec.read(RunningBatchCodec.write(batch)));
        }
    }

    @Test void paidTransitionPreservesIdentityParallelismAssetsAndFee() {
        RunningBatch first = batch(0), last = first;
        for (int paid = 1; paid <= 20; paid++) {
            last = last.paid();
            assertEquals(batch(paid), last);
        }
        assertEquals(0, first.paidTicks());
        assertThrows(IllegalArgumentException.class, last::paid);
    }

    @Test void multiRoundProgressPersistsAndLegacyProgressDefaultsToTwenty() {
        var base = batch(1);
        var longBatch = new RunningBatch(base.id(), base.fingerprint(), base.environment(), base.parallelism(),
                base.inputs(), base.outputs(), base.catalysts(), base.energyPerTick(), 299, 400);
        assertEquals(longBatch, RunningBatchCodec.read(RunningBatchCodec.write(longBatch)));
        assertEquals(300, longBatch.paid().paidTicks());
        assertEquals(400, longBatch.paid().requiredTicks());
        var legacy = RunningBatchCodec.write(base); legacy.remove("requiredTicks");
        assertEquals(base, RunningBatchCodec.read(legacy));
    }

    @Test void malformedProgressFeesAndEnvironmentAreRejected() {
        for (Consumer<CompoundTag> mutation : java.util.List.<Consumer<CompoundTag>>of(
                tag -> tag.putInt("paidTicks", -1), tag -> tag.putInt("paidTicks", 21),
                tag -> tag.putInt("parallelism", 0), tag -> tag.putInt("parallelism", FactoryLimits.MAX_BATCH_PARALLELISM + 1),
                tag -> tag.putInt("requiredTicks", 21), tag -> tag.putInt("requiredTicks", 0),
                tag -> tag.putInt("n", 0), tag -> tag.putInt("m", 11),
                tag -> tag.putString("fingerprint", "invalid"), tag -> tag.putIntArray("id", new int[]{1}),
                tag -> tag.putByteArray("energyPerTick", QuantityCodec.encode(BigInteger.ZERO)),
                tag -> tag.putByteArray("energyPerTick", QuantityCodec.encode(BigInteger.ONE)),
                tag -> tag.putByteArray("energyPerTick", QuantityCodec.encode(FactoryRules.ENERGY_PER_MACHINE_TICK.multiply(BigInteger.valueOf(7745)))))) {
            CompoundTag tag = RunningBatchCodec.write(batch(1));
            mutation.accept(tag);
            assertThrows(IllegalArgumentException.class, () -> RunningBatchCodec.read(tag));
        }
    }

    @Test void wrongTypesUnknownFieldsAndMissingFieldsAreNotSilentlyDefaulted() {
        for (String field : RunningBatchCodec.write(batch(1)).getAllKeys()) {
            if (field.equals("requiredTicks")) continue; // Legacy saves default to one round.
            CompoundTag missing = RunningBatchCodec.write(batch(1));
            missing.remove(field);
            assertThrows(IllegalArgumentException.class, () -> RunningBatchCodec.read(missing));
        }
        CompoundTag tag = RunningBatchCodec.write(batch(1));
        tag.putInt("extra", 1);
        assertThrows(IllegalArgumentException.class, () -> RunningBatchCodec.read(tag));
        tag.remove("extra");
        tag.putString("paidTicks", "1");
        assertThrows(IllegalArgumentException.class, () -> RunningBatchCodec.read(tag));
    }

    @Test void batchesRequireExactScaledAmountsAndEnvironmentalLimit() {
        var first = batch(1);
        assertThrows(IllegalArgumentException.class, () -> new RunningBatch(first.id(), first.fingerprint(), first.environment(), 4,
                first.inputs(), first.outputs(), first.energyPerTick(), 1));
        assertDoesNotThrow(() -> new RunningBatch(first.id(), first.fingerprint(),
                new FactoryRules.Environment(10, 1), 3, first.inputs(), first.outputs(), first.energyPerTick(), 1));
        assertThrows(IllegalArgumentException.class, () -> new RunningBatch(first.id(), first.fingerprint(), first.environment(), 3,
                first.inputs(), ResourceInventory.empty(), first.energyPerTick(), 1));
    }

    @Test void exactComponentsSurviveReservationCodec() {
        CompoundTag components = new CompoundTag();
        components.putString("test:variant", "second");
        ResourceKey variant = new ResourceKey(ResourceKind.ITEM, "test:ingot", components);
        var first = batch(19);
        var batch = new RunningBatch(first.id(), first.fingerprint(), first.environment(), 3, first.inputs(),
                new ResourceInventory(Map.of(OUTPUT, BigInteger.valueOf(3), variant, BigInteger.valueOf(6))), first.energyPerTick(), 19);
        assertEquals(batch, RunningBatchCodec.read(RunningBatchCodec.write(batch)));
        assertEquals(2, batch.outputs().amounts().size());
    }

    @Test void redstoneModesHaveDefinedLevelsAndCycle() {
        assertTrue(RedstoneMode.IGNORE.permits(true));
        assertTrue(RedstoneMode.IGNORE.permits(false));
        assertTrue(RedstoneMode.HIGH.permits(true));
        assertFalse(RedstoneMode.HIGH.permits(false));
        assertFalse(RedstoneMode.LOW.permits(true));
        assertTrue(RedstoneMode.LOW.permits(false));
        assertEquals(RedstoneMode.IGNORE, RedstoneMode.IGNORE.next().next().next());
    }

    @Test void catalystsOfEveryKindRoundTripAtAllPaymentBoundariesWithoutLosingComponents() {
        CompoundTag components = new CompoundTag();
        components.putString("test:variant", "locked");
        var catalyst = new ResourceInventory(Map.of(
                new ResourceKey(ResourceKind.ITEM, "test:mold", components), BigInteger.valueOf(6),
                new ResourceKey(ResourceKind.FLUID, "test:medium", components), BigInteger.valueOf(3000),
                new ResourceKey(ResourceKind.CHEMICAL, "test:catalyst"), BigInteger.valueOf(Long.MAX_VALUE).multiply(BigInteger.valueOf(3))));
        for (int paid = 0; paid <= 20; paid++) {
            var base = batch(paid);
            var locked = new RunningBatch(base.id(), base.fingerprint(), base.environment(), base.parallelism(),
                    base.inputs(), base.outputs(), catalyst, base.energyPerTick(), paid);
            assertEquals(locked, RunningBatchCodec.read(RunningBatchCodec.write(locked)));
            if (paid < 20) assertEquals(catalyst, locked.paid().catalysts());
        }
    }

    @Test void historicalBatchesWithoutCatalystsStayReadableAndKeepTheirEncoding() {
        CompoundTag old = RunningBatchCodec.write(batch(19));
        assertFalse(old.contains("catalysts"));
        RunningBatch restored = RunningBatchCodec.read(old);
        assertTrue(restored.catalysts().amounts().isEmpty());
        assertEquals(old, RunningBatchCodec.write(restored));
    }

    @Test void malformedCatalystTypeQuantityAndDuplicateIdentityAreRejected() {
        CompoundTag tag = RunningBatchCodec.write(batch(1));
        tag.putString("catalysts", "invalid");
        assertThrows(IllegalArgumentException.class, () -> RunningBatchCodec.read(tag));
        var inventory = ResourceNbtCodec.writeInventory(new ResourceInventory(Map.of(ITEM, BigInteger.ONE)));
        tag.put("catalysts", inventory);
        assertThrows(IllegalArgumentException.class, () -> RunningBatchCodec.read(tag)); // Not divisible by P_run=3.
        inventory.getCompound(0).putByteArray("amount", QuantityCodec.encode(BigInteger.valueOf(3)));
        inventory.add(inventory.getCompound(0).copy());
        assertThrows(IllegalArgumentException.class, () -> RunningBatchCodec.read(tag));
        inventory.remove(1);
        inventory.getCompound(0).putByteArray("amount", QuantityCodec.encode(BigInteger.ZERO));
        assertThrows(IllegalArgumentException.class, () -> RunningBatchCodec.read(tag));
    }

    @Test void consumedAndLockedAmountsCannotExceedOneInputInventory() {
        var base = batch(1);
        assertThrows(IllegalArgumentException.class, () -> new RunningBatch(base.id(), base.fingerprint(),
                base.environment(), 1, new ResourceInventory(Map.of(ITEM, FactoryLimits.MAX_INVENTORY_AMOUNT)),
                base.outputs(), new ResourceInventory(Map.of(ITEM, BigInteger.ONE)), base.energyPerTick(), 1));
    }

    @Test void catalystsParticipateInTheCombinedBoundaryResourceLimit() {
        var amounts = new java.util.LinkedHashMap<ResourceKey, BigInteger>();
        for (int i = 0; i < FactoryLimits.MAX_BOUNDARY_RESOURCES - 1; i++) {
            amounts.put(new ResourceKey(ResourceKind.ITEM, "test:catalyst_" + i), BigInteger.valueOf(3));
        }
        var base = batch(1);
        assertThrows(IllegalArgumentException.class, () -> new RunningBatch(base.id(), base.fingerprint(),
                base.environment(), 3, base.inputs(), base.outputs(), new ResourceInventory(amounts), base.energyPerTick(), 1));
    }

    @Test void runtimeControlsRoundTripAndOlderIdleStatesDefaultToDisabled() {
        FactoryStorage storage = FactoryStorage.empty().withControls(true, RedstoneMode.LOW);
        CompoundTag saved = FactoryState.of(storage, null).save();
        var restored = FactoryStateCodec.load(saved, null);
        assertTrue(restored.enabled());
        assertEquals(RedstoneMode.LOW, restored.redstoneMode());
        saved.remove("enabled"); saved.remove("redstoneMode"); saved.remove("lastPaidGameTime");
        assertFalse(FactoryStateCodec.load(saved, null).enabled());
    }
}
