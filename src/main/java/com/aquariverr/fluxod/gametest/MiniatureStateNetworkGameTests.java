package com.aquariverr.fluxod.gametest;

import com.aquariverr.fluxod.FluxOverdrive;
import com.aquariverr.fluxod.miniature.model.GridPosition;
import com.aquariverr.fluxod.miniature.storage.FactoryItemResources;
import com.aquariverr.fluxod.miniature.storage.FactoryState;
import com.aquariverr.fluxod.miniature.storage.FactoryStateCodec;
import com.aquariverr.fluxod.miniature.storage.FactoryStorage;
import com.aquariverr.fluxod.miniature.storage.ResourceInventory;
import com.aquariverr.fluxod.register.FluxOdDataComponents;
import com.aquariverr.fluxod.register.RegistryItems;
import io.netty.buffer.Unpooled;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.network.connection.ConnectionType;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.math.BigInteger;
import java.util.List;
import java.util.Map;
import java.util.Set;

@GameTestHolder(FluxOverdrive.MODID)
@PrefixGameTestTemplate(false)
public final class MiniatureStateNetworkGameTests {

    @GameTest(template = "empty")
    public static void registeredComponentAndItemStackSyncCompleteValidState(GameTestHelper helper) {
        var registries = helper.getLevel().registryAccess();
        ItemStack machine = new ItemStack(Items.FURNACE);
        machine.set(DataComponents.MAX_STACK_SIZE, 1);
        machine.set(DataComponents.CUSTOM_NAME, Component.literal("Network fixture furnace"));
        FactoryStorage storage = FactoryStorage.empty();
        for (int count = 0; count < 2; count++) {
            storage = storage.insertMachine(new GridPosition(0, 0), machine, registries).storage();
        }
        storage = storage.insertInput(new ItemStack(Items.IRON_ORE, 47), registries).storage()
                .withOutputs(new ResourceInventory(Map.of(
                        FactoryItemResources.keyOf(new ItemStack(Items.IRON_INGOT), registries), BigInteger.valueOf(25))));
        CompoundTag expected = FactoryState.of(storage, registries).save();
        CustomData source = CustomData.of(expected);
        CustomData decoded = roundTripComponent(helper, source);
        helper.assertTrue(expected.equals(decoded.copyTag()),
                "Registered factory component stream codec did not preserve the complete valid state");
        ItemStack carrier = carrier(source);
        ItemStack decodedCarrier = roundTripItem(helper, carrier);
        helper.assertTrue(ItemStack.isSameItemSameComponents(carrier, decodedCarrier),
                "ItemStack network sync changed a valid factory item or its state component");
        FactoryStorage restored = FactoryStateCodec.load(decoded.copyTag(), registries);
        helper.assertTrue(restored.layout().equals(storage.layout())
                        && restored.machines().entries().equals(storage.machines().entries())
                        && restored.inputs().equals(storage.inputs()) && restored.outputs().equals(storage.outputs()),
                "Network round trip changed real machine or resource assets");
        helper.assertTrue(expected.equals(source.copyTag()), "Network encoding changed source CustomData");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void boundedUnknownAndInvalidStatesRemainFullySynchronized(GameTestHelper helper) {
        CompoundTag unknown = FactoryState.fresh().save();
        unknown.putString("format", "test:future_factory");
        unknown.putString("future_payload", "preserved");
        CompoundTag malformed = FactoryState.fresh().save();
        malformed.putInt("gridSize", 4);
        for (CompoundTag raw : List.of(unknown, malformed)) {
            FactoryState preserved = FactoryState.load(raw, helper.getLevel().registryAccess());
            helper.assertTrue(preserved.storage().isEmpty(), "Fixture unexpectedly exposes editable factory storage");
            CustomData source = CustomData.of(raw);
            helper.assertTrue(raw.equals(roundTripComponent(helper, source).copyTag()),
                    "A bounded unavailable state was unnecessarily omitted from network synchronization");
            helper.assertTrue(raw.equals(source.copyTag()) && raw.equals(preserved.save()),
                    "Synchronizing bounded unavailable data changed its server state");
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void unsafeRetainedStatesSyncOnlyAnOmissionMarkerWithoutChangingSource(GameTestHelper helper) {
        for (CompoundTag raw : unsafeStates()) {
            CompoundTag expected = raw.copy();
            FactoryState preserved = FactoryState.load(raw, helper.getLevel().registryAccess());
            helper.assertTrue(preserved.storage().isEmpty(), "Unsafe fixture unexpectedly exposes editable storage");
            CustomData source = CustomData.of(raw);
            assertOmissionMarker(helper, roundTripComponent(helper, source));
            ItemStack sourceItem = carrier(source);
            ItemStack decodedItem = roundTripItem(helper, sourceItem);
            helper.assertTrue(decodedItem.is(RegistryItems.MINIATURE_UNIVERSE.get()) && decodedItem.getCount() == 1,
                    "Omitting unsafe state changed the carried factory item");
            CustomData synchronizedState = decodedItem.get(FluxOdDataComponents.MINIATURE_UNIVERSE_STATE);
            helper.assertTrue(synchronizedState != null, "Factory state component disappeared during item synchronization");
            assertOmissionMarker(helper, synchronizedState);
            helper.assertTrue(expected.equals(source.copyTag()) && expected.equals(raw)
                            && expected.equals(preserved.save())
                            && expected.equals(sourceItem.get(FluxOdDataComponents.MINIATURE_UNIVERSE_STATE).copyTag()),
                    "Network fallback mutated the original retained factory state or source item");
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void persistentComponentAndItemCodecsNeverReplaceRetainedDataWithNetworkMarker(GameTestHelper helper)
            throws IOException {
        var registries = helper.getLevel().registryAccess();
        var ops = registries.createSerializationContext(NbtOps.INSTANCE);
        for (CompoundTag raw : unsafeStates()) {
            CustomData source = CustomData.of(raw);
            assertOmissionMarker(helper, roundTripComponent(helper, source));
            Tag encodedComponent = FluxOdDataComponents.MINIATURE_UNIVERSE_STATE.codecOrThrow()
                    .encodeStart(ops, source).getOrThrow();
            helper.assertTrue(raw.equals(encodedComponent),
                    "Persistent registered component codec replaced retained raw state with a network marker");
            CustomData decodedComponent = FluxOdDataComponents.MINIATURE_UNIVERSE_STATE.codecOrThrow()
                    .parse(ops, encodedComponent).getOrThrow();
            helper.assertTrue(raw.equals(decodedComponent.copyTag()), "Persistent component round trip changed retained data");
            Tag encodedCustomData = CustomData.CODEC.encodeStart(ops, source).getOrThrow();
            helper.assertTrue(raw.equals(encodedCustomData)
                            && raw.equals(CustomData.CODEC.parse(ops, encodedCustomData).getOrThrow().copyTag()),
                    "CustomData persistent codec did not retain the complete raw NBT");

            ItemStack item = carrier(source);
            CompoundTag itemTag = (CompoundTag) item.save(registries);
            CompoundTag diskTag = binaryRoundTrip(itemTag);
            ItemStack restored = ItemStack.STRICT_CODEC.parse(ops, diskTag).getOrThrow();
            CustomData restoredState = restored.get(FluxOdDataComponents.MINIATURE_UNIVERSE_STATE);
            helper.assertTrue(restoredState != null && raw.equals(restoredState.copyTag()),
                    "Strict item NBT disk round trip lost the retained raw factory state");
            helper.assertTrue(ItemStack.isSameItemSameComponents(item, restored) && raw.equals(source.copyTag()),
                    "Persistence after network fallback changed the authoritative item or its components");
        }
        helper.succeed();
    }

    private static CustomData roundTripComponent(GameTestHelper helper, CustomData source) {
        RegistryFriendlyByteBuf buffer = buffer(helper);
        try {
            var codec = FluxOdDataComponents.MINIATURE_UNIVERSE_STATE.streamCodec();
            codec.encode(buffer, source);
            CustomData decoded = codec.decode(buffer);
            helper.assertTrue(!buffer.isReadable(), "Factory component decoder left unread bytes");
            return decoded;
        } finally {
            buffer.release();
        }
    }

    private static ItemStack roundTripItem(GameTestHelper helper, ItemStack source) {
        RegistryFriendlyByteBuf buffer = buffer(helper);
        try {
            ItemStack.STREAM_CODEC.encode(buffer, source);
            ItemStack decoded = ItemStack.STREAM_CODEC.decode(buffer);
            helper.assertTrue(!buffer.isReadable(), "Factory ItemStack decoder left unread bytes");
            return decoded;
        } finally {
            buffer.release();
        }
    }

    private static RegistryFriendlyByteBuf buffer(GameTestHelper helper) {
        return new RegistryFriendlyByteBuf(Unpooled.buffer(), helper.getLevel().registryAccess(), ConnectionType.NEOFORGE);
    }

    private static ItemStack carrier(CustomData state) {
        ItemStack item = new ItemStack(RegistryItems.MINIATURE_UNIVERSE.get());
        item.set(FluxOdDataComponents.MINIATURE_UNIVERSE_STATE, state);
        return item;
    }

    private static void assertOmissionMarker(GameTestHelper helper, CustomData decoded) {
        CompoundTag tag = decoded.copyTag();
        helper.assertTrue(tag.getAllKeys().equals(Set.of("synchronizationOmitted"))
                        && tag.contains("synchronizationOmitted", Tag.TAG_BYTE) && tag.getBoolean("synchronizationOmitted"),
                "Unsafe factory state must decode to only the explicit synchronization omission marker");
    }

    private static List<CompoundTag> unsafeStates() {
        CompoundTag unknownOversized = FactoryState.fresh().save();
        unknownOversized.putString("format", "test:future_factory");
        unknownOversized.putByteArray("future_payload", new byte[FactoryStateCodec.MAX_STATE_BYTES + 1]);
        CompoundTag invalidHeap = FactoryState.fresh().save();
        ListTag rows = new ListTag();
        for (int row = 0; row < 60_000; row++) rows.add(new CompoundTag());
        invalidHeap.put("unexpected_rows", rows);
        CompoundTag invalidDepth = FactoryState.fresh().save();
        CompoundTag current = invalidDepth;
        for (int depth = 0; depth < FactoryStateCodec.MAX_STATE_DEPTH; depth++) {
            CompoundTag child = new CompoundTag();
            current.put("nested", child);
            current = child;
        }
        current.putInt("value", 7);
        return List.of(unknownOversized, invalidHeap, invalidDepth);
    }

    private static CompoundTag binaryRoundTrip(CompoundTag tag) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        NbtIo.writeUnnamedTag(tag, new DataOutputStream(bytes));
        return NbtIo.read(new DataInputStream(new ByteArrayInputStream(bytes.toByteArray())), NbtAccounter.unlimitedHeap());
    }

    private MiniatureStateNetworkGameTests() {}
}
