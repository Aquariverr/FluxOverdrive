package com.aquariverr.fluxod.miniature.storage;

import com.aquariverr.fluxod.Config;
import com.aquariverr.fluxod.miniature.model.*;
import com.aquariverr.fluxod.miniature.runtime.RedstoneMode;
import com.aquariverr.fluxod.network.MiniatureFactoryCommandPayload;
import com.aquariverr.fluxod.network.MiniatureFactorySnapshotPayload.FactorySnapshot;
import io.netty.buffer.Unpooled;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.neoforged.neoforge.network.connection.ConnectionType;
import org.junit.jupiter.api.Test;

import java.math.BigInteger;
import java.util.Map;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

class FactoryInputCapacityTest {
    private static final HolderLookup.Provider REGISTRIES = HolderLookup.Provider.create(Stream.empty());

    @Test
    void eachExactIngredientKeepsItsOwnLimitAcrossSaveAndStorageChanges() {
        var item = new ResourceKey(ResourceKind.ITEM, "test:resource");
        var components = new net.minecraft.nbt.CompoundTag();
        components.putString("test:variant", "second");
        var variant = new ResourceKey(ResourceKind.ITEM, "test:resource", components);
        var fluid = new ResourceKey(ResourceKind.FLUID, "test:resource");
        var chemical = new ResourceKey(ResourceKind.CHEMICAL, "test:resource");
        var limits = Map.of(item, BigInteger.TEN, variant, BigInteger.valueOf(20),
                fluid, BigInteger.valueOf(30), chemical, BigInteger.valueOf(40));
        var configured = FactoryStorage.empty().withInputCapacities(limits);
        var changed = configured.withControls(true, RedstoneMode.HIGH).withBatch(null, 42)
                .withInputs(new ResourceInventory(Map.of(item, BigInteger.valueOf(8))))
                .withOutputs(ResourceInventory.empty()).withConfiguration(new GridPosition(0, 0), new FactoryCell.Arrow()).upgrade();
        var restored = FactoryStateCodec.load(FactoryStateCodec.save(changed, REGISTRIES), REGISTRIES);
        assertEquals(limits, restored.inputCapacities());
        assertEquals(BigInteger.TWO, restored.inputCapacity(item));
        assertEquals(BigInteger.valueOf(20), restored.inputCapacity(variant));
        assertEquals(BigInteger.valueOf(30), restored.inputCapacity(fluid));
        assertEquals(BigInteger.valueOf(40), restored.inputCapacity(chemical));
        assertTrue(FactoryStorage.empty().inputCapacities().isEmpty());
        assertThrows(UnsupportedOperationException.class, () -> restored.inputCapacities().clear());
    }

    @Test
    void exhaustedIngredientsRemainConfigurableAndResetOnlyRemovesSelectedOverride() {
        var ore = new ResourceKey(ResourceKind.ITEM, "test:ore");
        var water = new ResourceKey(ResourceKind.FLUID, "test:water");
        var storage = FactoryStorage.empty().withInputCapacity(BigInteger.valueOf(100))
                .withInputCapacity(ore, BigInteger.TEN).withInputCapacity(water, BigInteger.valueOf(20))
                .withInputs(new ResourceInventory(Map.of(ore, BigInteger.ONE)));
        storage = storage.withInputs(storage.inputs().extract(ore, BigInteger.ONE).inventory());
        assertEquals(List.of(ore, water), storage.inputResources());
        storage = FactoryStateCodec.load(FactoryStateCodec.save(storage, REGISTRIES), REGISTRIES)
                .withInputCapacity(ore, storage.configuredInputCapacity());
        assertEquals(List.of(water), storage.inputResources());
        assertEquals(BigInteger.valueOf(100), storage.configuredInputCapacity(ore));
        assertEquals(BigInteger.valueOf(20), storage.configuredInputCapacity(water));
    }

    @Test
    void invalidResourceLimitsPreserveOpaqueSavedStateAndBoundConfigurationCount() {
        var resource = new ResourceKey(ResourceKind.ITEM, "test:ore");
        for (var value : new BigInteger[]{BigInteger.ZERO, BigInteger.valueOf(-1), FactoryLimits.MAX_INVENTORY_AMOUNT.add(BigInteger.ONE)})
            assertThrows(IllegalArgumentException.class, () -> FactoryStorage.empty().withInputCapacity(resource, value));
        var saved = FactoryStateCodec.save(FactoryStorage.empty().withInputCapacity(resource, BigInteger.TEN), REGISTRIES);
        saved.getList("inputCapacities", net.minecraft.nbt.Tag.TAG_COMPOUND).getCompound(0)
                .putByteArray("amount", QuantityCodec.encode(BigInteger.ZERO));
        var rejected = FactoryState.load(saved, REGISTRIES);
        assertEquals(FactoryState.Status.INVALID_STATE, rejected.status());
        assertEquals(saved, rejected.save());
        var limits = new LinkedHashMap<ResourceKey, BigInteger>();
        for (int i = 0; i < FactoryLimits.MAX_BOUNDARY_RESOURCES; i++)
            limits.put(new ResourceKey(ResourceKind.ITEM, "test:item_" + i), BigInteger.TEN);
        var full = FactoryStorage.empty().withInputCapacities(limits);
        assertThrows(IllegalArgumentException.class, () -> full.withInputCapacity(resource, BigInteger.ONE));
        assertEquals(BigInteger.ONE, full.withInputCapacity(limits.keySet().iterator().next(), BigInteger.ONE)
                .configuredInputCapacity(limits.keySet().iterator().next()));
    }

    @Test
    void localLimitIsIndependentAndPreservedByStorageChangesAndOldSaves() {
        var original = FactoryStorage.empty();
        var configured = original.withInputCapacity(BigInteger.valueOf(123456));
        assertEquals(FactoryLimits.MAX_INVENTORY_AMOUNT, original.configuredInputCapacity());
        assertSame(configured, configured.withInputCapacity(BigInteger.valueOf(123456)));
        var changed = configured.withControls(true, RedstoneMode.HIGH).withBatch(null, 42)
                .withInputs(ResourceInventory.empty()).withOutputs(ResourceInventory.empty())
                .withConfiguration(new GridPosition(0, 0), new FactoryCell.Arrow()).upgrade();
        var restored = FactoryStateCodec.load(FactoryStateCodec.save(changed, REGISTRIES), REGISTRIES);
        assertEquals(BigInteger.valueOf(123456), restored.configuredInputCapacity());
        var old = FactoryStateCodec.save(changed, REGISTRIES);
        old.remove("inputCapacity");
        assertEquals(FactoryLimits.MAX_INVENTORY_AMOUNT, FactoryStateCodec.load(old, REGISTRIES).configuredInputCapacity());
    }

    @Test
    void loweredLimitsRetainResourcesAndRespectServerCeilingForEveryKind() {
        var previous = Config.miniatureInputCapacity;
        try {
            Config.miniatureInputCapacity = BigInteger.valueOf(100);
            for (var kind : ResourceKind.values()) {
                var key = new ResourceKey(kind, "test:resource");
                var storage = FactoryStorage.empty().withInputCapacity(key, BigInteger.TEN)
                        .withInputs(new ResourceInventory(Map.of(key, BigInteger.valueOf(8))));
                assertEquals(BigInteger.TWO, storage.inputCapacity(key));
                var lowered = storage.withInputCapacity(key, BigInteger.ONE);
                assertEquals(BigInteger.ZERO, lowered.inputCapacity(key));
                assertEquals(BigInteger.valueOf(8), FactoryStateCodec.load(FactoryStateCodec.save(lowered, REGISTRIES), REGISTRIES).inputs().amount(key));
                assertEquals(BigInteger.valueOf(92), storage.withInputCapacity(key, FactoryLimits.MAX_INVENTORY_AMOUNT).inputCapacity(key));
            }
        } finally { Config.miniatureInputCapacity = previous; }
    }

    @Test
    void invalidCapacityIsRejectedWithoutDiscardingSavedState() {
        for (var value : new BigInteger[]{BigInteger.ZERO, BigInteger.valueOf(-1), FactoryLimits.MAX_INVENTORY_AMOUNT.add(BigInteger.ONE)})
            assertThrows(IllegalArgumentException.class, () -> FactoryStorage.empty().withInputCapacity(value));
        var saved = FactoryState.fresh().save();
        saved.putByteArray("inputCapacity", QuantityCodec.encode(BigInteger.ZERO));
        var rejected = FactoryState.load(saved, REGISTRIES);
        assertEquals(FactoryState.Status.INVALID_STATE, rejected.status());
        assertEquals(saved, rejected.save());
        saved.putString("inputCapacity", "12");
        assertEquals(FactoryState.Status.INVALID_STATE, FactoryState.load(saved, REGISTRIES).status());
    }

    @Test
    void capacityCommandRequiresSnapshotResourceIndexAndBoundedPositiveQuantity() {
        var command = command(BigInteger.TEN, UUID.randomUUID(), 0, 0);
        var buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), net.minecraft.core.RegistryAccess.EMPTY, ConnectionType.NEOFORGE);
        try {
            MiniatureFactoryCommandPayload.STREAM_CODEC.encode(buffer, command);
            assertEquals(command, MiniatureFactoryCommandPayload.STREAM_CODEC.decode(buffer));
        } finally { buffer.release(); }
        assertThrows(IllegalArgumentException.class, () -> command(BigInteger.ZERO, UUID.randomUUID(), 0, 0));
        assertThrows(IllegalArgumentException.class, () -> command(FactoryLimits.MAX_INVENTORY_AMOUNT.add(BigInteger.ONE), UUID.randomUUID(), 0, 0));
        assertThrows(IllegalArgumentException.class, () -> command(BigInteger.ONE, MiniatureFactoryCommandPayload.NO_SNAPSHOT, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> command(BigInteger.ONE, UUID.randomUUID(), 1, 0));
        assertThrows(IllegalArgumentException.class, () -> command(BigInteger.ONE, UUID.randomUUID(), 0, -1));
        assertThrows(IllegalArgumentException.class, () -> command(BigInteger.ONE, UUID.randomUUID(), 0, FactoryLimits.MAX_BOUNDARY_RESOURCES * 2));
    }

    @Test
    void snapshotCarriesLocalAndServerLimitsThroughEveryPreviewCopy() {
        var resource = new ResourceKey(ResourceKind.ITEM, "test:ore");
        var snapshot = FactorySnapshot.from(FactoryState.fresh()).withInputCapacity(BigInteger.TEN, BigInteger.valueOf(7))
                .withInputCapacities(Map.of(resource, BigInteger.valueOf(5)));
        assertEquals(snapshot, snapshot.withRuntime(snapshot.runtime()).withUpgrade(snapshot.upgrade())
                .withRecipes(snapshot.recipeGeneration(), snapshot.recipes()).withCompilation(snapshot.compilation()));
        assertEquals(BigInteger.valueOf(7), snapshot.effectiveInputCapacity());
        assertEquals(BigInteger.valueOf(5), snapshot.effectiveInputCapacity(resource));
        assertEquals(List.of(resource), snapshot.inputResources());
    }

    private static MiniatureFactoryCommandPayload command(BigInteger amount, UUID snapshot, int x, int resource) {
        return new MiniatureFactoryCommandPayload(1, UUID.randomUUID(), BlockPos.ZERO, 0, 1,
                MiniatureFactoryCommandPayload.Action.SET_INPUT_CAPACITY, x, 0, amount, GridDirection.RIGHT, snapshot, resource);
    }
}
