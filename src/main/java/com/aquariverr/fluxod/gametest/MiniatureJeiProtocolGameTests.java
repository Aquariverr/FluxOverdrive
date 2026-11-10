package com.aquariverr.fluxod.gametest;

import com.aquariverr.fluxod.FluxOverdrive;
import com.aquariverr.fluxod.miniature.model.*;
import com.aquariverr.fluxod.miniature.storage.FactoryItemResources;
import com.aquariverr.fluxod.miniature.storage.RecipeReferenceCodec;
import com.aquariverr.fluxod.miniature.storage.ResourceNbtCodec;
import com.aquariverr.fluxod.network.MiniatureFactoryCommandPayload;
import com.aquariverr.fluxod.network.MiniatureFactoryCommandPayload.Action;
import io.netty.buffer.Unpooled;
import io.netty.handler.codec.DecoderException;
import io.netty.handler.codec.EncoderException;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.protocol.common.ServerboundCustomPayloadPacket;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.network.connection.ConnectionType;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@GameTestHolder(FluxOverdrive.MODID)
@PrefixGameTestTemplate(false)
public final class MiniatureJeiProtocolGameTests {
    private static final UUID SESSION = UUID.fromString("f39cbd38-e2e3-4390-9acb-5f8413117fda");

    @GameTest(template = "empty")
    public static void largeGhostAndNineExactBindingsRoundTripThroughRegisteredServerboundPacket(GameTestHelper helper) {
        ItemStack stack = new ItemStack(Items.IRON_ORE, 64);
        CompoundTag data = new CompoundTag();
        data.putByteArray("exact_payload", new byte[60_000]);
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(data));
        ResourceKey resource = FactoryItemResources.keyOf(stack, helper.getLevel().registryAccess());
        var bindings = new ArrayList<RecipeReference.Binding>();
        for (int slot = 0; slot < 9; slot++) bindings.add(new RecipeReference.Binding(slot, resource));
        var recipe = new RecipeReference("flux_overdrive:crafting", "minecraft:cake", 3, 3, bindings);
        for (var source : List.of(command(Action.SET_GHOST_MARKER, resource, null), command(Action.SELECT_RECIPE, null, recipe))) {
            RegistryFriendlyByteBuf buffer = buffer(helper);
            try {
                ServerboundCustomPayloadPacket.STREAM_CODEC.encode(buffer, new ServerboundCustomPayloadPacket(source));
                helper.assertTrue(buffer.readableBytes() > 32_767 && buffer.readableBytes() < FactoryLimits.MAX_MESSAGE_BYTES,
                        "Large JEI intent did not exercise the registered packet above vanilla's unknown-payload limit");
                var restored = ServerboundCustomPayloadPacket.STREAM_CODEC.decode(buffer).payload();
                helper.assertTrue(source.equals(restored) && !buffer.isReadable(), "Registered packet lost exact large component identities");
                helper.assertTrue(source.quantity().equals(BigInteger.ONE), "JEI ingredient stack size entered the command quantity");
            } finally { buffer.release(); }
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void strictIntentFieldsRejectInjectedAmountsOutputsAndMismatchedActions(GameTestHelper helper) throws IOException {
        ResourceKey resource = FactoryItemResources.keyOf(new ItemStack(Items.IRON_ORE), helper.getLevel().registryAccess());
        var reference = MiniatureJeiIntentGameTests.smelting(resource);
        for (Action action : List.of(Action.SET_GHOST_MARKER, Action.SELECT_RECIPE)) {
            var source = command(action, action == Action.SET_GHOST_MARKER ? resource : null, action == Action.SELECT_RECIPE ? reference : null);
            CompoundTag valid = intent(source);
            RegistryFriendlyByteBuf baseline = raw(helper, action, valid, BigInteger.ONE, false, false);
            try {
                helper.assertTrue(source.equals(MiniatureFactoryCommandPayload.STREAM_CODEC.decode(baseline)) && !baseline.isReadable(),
                        "Raw protocol fixture does not match the valid command schema");
            } finally { baseline.release(); }
            for (String field : List.of("amount", "quantity", "inputs", "outputs", "normalization")) {
                CompoundTag forged = valid.copy();
                forged.putInt(field, 64);
                reject(helper, raw(helper, action, forged, BigInteger.ONE, false, false));
            }
            CompoundTag extra = valid.copy();
            if (action == Action.SET_GHOST_MARKER) extra.getCompound("marker").putInt("amount", 64);
            else {
                extra.getCompound("recipe").put("outputs", new ListTag());
                reject(helper, raw(helper, action, extra, BigInteger.ONE, false, false));
                extra = valid.copy();
                extra.getCompound("recipe").getList("bindings", net.minecraft.nbt.Tag.TAG_COMPOUND).getCompound(0).putInt("amount", 64);
            }
            reject(helper, raw(helper, action, extra, BigInteger.ONE, false, false));
            reject(helper, raw(helper, action, valid, BigInteger.valueOf(64), false, false));
            reject(helper, raw(helper, action, new CompoundTag(), BigInteger.ONE, false, false));
            reject(helper, raw(helper, action == Action.SET_GHOST_MARKER ? Action.SELECT_RECIPE : Action.SET_GHOST_MARKER,
                    valid, BigInteger.ONE, false, false));
            reject(helper, raw(helper, Action.CLEAR_CONFIGURATION, valid, BigInteger.ONE, false, false));
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void recipeIntentSharesNbtHeapBudgetAcrossEveryBindingForEncodingAndDecoding(GameTestHelper helper) throws IOException {
        CompoundTag components = new CompoundTag();
        ListTag entries = new ListTag();
        for (int entry = 0; entry < 20_000; entry++) entries.add(new CompoundTag());
        components.put("minecraft:custom_data", new CompoundTag());
        components.getCompound("minecraft:custom_data").put("entries", entries);
        ResourceKey resource = new ResourceKey(ResourceKind.ITEM, "minecraft:iron_ore", components);
        // Each binding fits both per-resource byte and heap limits. Their combined heap exceeds the one request budget.
        RegistryFriendlyByteBuf single = buffer(helper);
        try {
            var one = command(Action.SET_GHOST_MARKER, resource, null);
            MiniatureFactoryCommandPayload.STREAM_CODEC.encode(single, one);
            helper.assertTrue(one.equals(MiniatureFactoryCommandPayload.STREAM_CODEC.decode(single)),
                    "One heap fixture resource must fit its own request budget");
        } finally { single.release(); }
        var bindings = new ArrayList<RecipeReference.Binding>();
        for (int slot = 0; slot < 3; slot++) bindings.add(new RecipeReference.Binding(slot, resource));
        var recipe = new RecipeReference("flux_overdrive:crafting", "minecraft:bread", 3, 1, bindings);
        var source = command(Action.SELECT_RECIPE, null, recipe);
        RegistryFriendlyByteBuf output = buffer(helper);
        try {
            boolean rejected = false;
            try { MiniatureFactoryCommandPayload.STREAM_CODEC.encode(output, source); }
            catch (EncoderException expected) { rejected = true; }
            helper.assertTrue(rejected && output.writerIndex() == 0, "Excessive aggregate NBT heap was encoded or partially wrote the packet");
        } finally { output.release(); }
        RegistryFriendlyByteBuf raw = raw(helper, Action.SELECT_RECIPE, intent(source), BigInteger.ONE, false, false);
        helper.assertTrue(raw.readableBytes() < FactoryLimits.MAX_MESSAGE_BYTES, "Heap fixture accidentally exceeded the wire byte limit");
        reject(helper, raw);
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void commandEnvelopeRejectsOversizedFramesAndTrailingNbtOrBody(GameTestHelper helper) throws IOException {
        var resource = FactoryItemResources.keyOf(new ItemStack(Items.IRON_ORE), helper.getLevel().registryAccess());
        CompoundTag valid = intent(command(Action.SET_GHOST_MARKER, resource, null));
        reject(helper, raw(helper, Action.SET_GHOST_MARKER, valid, BigInteger.ONE, true, false));
        reject(helper, raw(helper, Action.SET_GHOST_MARKER, valid, BigInteger.ONE, false, true));
        RegistryFriendlyByteBuf oversized = buffer(helper);
        oversized.writeZero(FactoryLimits.MAX_MESSAGE_BYTES + 1);
        reject(helper, oversized);
        RegistryFriendlyByteBuf beyondReservedFrame = buffer(helper);
        int length = FactoryLimits.MAX_MESSAGE_BYTES - 127;
        beyondReservedFrame.writeVarInt(length);
        beyondReservedFrame.writeZero(length);
        reject(helper, beyondReservedFrame);
        CompoundTag largeComponents = new CompoundTag();
        largeComponents.putByteArray("minecraft:custom_data", new byte[60_000]);
        var large = new ResourceKey(ResourceKind.ITEM, "test:" + "x".repeat(60_000), largeComponents);
        var bindings = new ArrayList<RecipeReference.Binding>();
        for (int slot = 0; slot < 9; slot++) bindings.add(new RecipeReference.Binding(slot, large));
        var tooLarge = command(Action.SELECT_RECIPE, null, new RecipeReference("flux_overdrive:crafting", "test:oversized", 3, 3, bindings));
        RegistryFriendlyByteBuf output = buffer(helper);
        try {
            boolean rejected = false;
            try { MiniatureFactoryCommandPayload.STREAM_CODEC.encode(output, tooLarge); }
            catch (EncoderException expected) { rejected = true; }
            helper.assertTrue(rejected && output.writerIndex() == 0, "Encoding escaped the complete command frame limit");
        } finally { output.release(); }
        helper.succeed();
    }

    private static MiniatureFactoryCommandPayload command(Action action, ResourceKey marker, RecipeReference recipe) {
        return new MiniatureFactoryCommandPayload(1, SESSION, BlockPos.ZERO, 0, 1, action, 1, 0,
                BigInteger.ONE, GridDirection.RIGHT, SESSION, -1, marker, recipe);
    }

    private static CompoundTag intent(MiniatureFactoryCommandPayload command) {
        CompoundTag result = new CompoundTag();
        if (command.markerResource() != null) result.put("marker", ResourceNbtCodec.writeKey(command.markerResource()));
        if (command.requestedRecipe() != null) result.put("recipe", RecipeReferenceCodec.write(command.requestedRecipe()));
        return result;
    }

    private static RegistryFriendlyByteBuf raw(GameTestHelper helper, Action action, CompoundTag intent, BigInteger quantity,
                                               boolean trailingNbt, boolean trailingBody) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        NbtIo.writeUnnamedTag(intent, new DataOutputStream(bytes));
        if (trailingNbt) bytes.write(0);
        FriendlyByteBuf body = new FriendlyByteBuf(Unpooled.buffer());
        RegistryFriendlyByteBuf framed = buffer(helper);
        try {
            body.writeVarInt(1);
            body.writeUUID(SESSION);
            body.writeBlockPos(BlockPos.ZERO);
            body.writeVarLong(0);
            body.writeVarLong(1);
            body.writeByte(action.ordinal());
            body.writeByte(1);
            body.writeByte(0);
            body.writeByteArray(QuantityCodec.encode(quantity));
            body.writeByte(GridDirection.RIGHT.ordinal());
            body.writeUUID(action.requiresSnapshot() ? SESSION : MiniatureFactoryCommandPayload.NO_SNAPSHOT);
            body.writeVarInt(-1);
            body.writeByteArray(bytes.toByteArray());
            if (trailingBody) body.writeByte(0);
            framed.writeVarInt(body.readableBytes());
            framed.writeBytes(body);
            return framed;
        } catch (RuntimeException exception) {
            framed.release();
            throw exception;
        } finally { body.release(); }
    }

    private static void reject(GameTestHelper helper, RegistryFriendlyByteBuf packet) {
        try {
            boolean rejected = false;
            try { MiniatureFactoryCommandPayload.STREAM_CODEC.decode(packet); }
            catch (DecoderException expected) { rejected = true; }
            helper.assertTrue(rejected, "Malformed or excessive JEI command was accepted");
        } finally { packet.release(); }
    }

    private static RegistryFriendlyByteBuf buffer(GameTestHelper helper) {
        return new RegistryFriendlyByteBuf(Unpooled.buffer(), helper.getLevel().registryAccess(), ConnectionType.NEOFORGE);
    }

    private MiniatureJeiProtocolGameTests() {}
}
