package com.aquariverr.fluxod.gametest;

import com.aquariverr.fluxod.FluxOverdrive;
import com.aquariverr.fluxod.miniature.model.FactoryLimits;
import com.aquariverr.fluxod.miniature.model.GridDirection;
import com.aquariverr.fluxod.miniature.model.GridPosition;
import com.aquariverr.fluxod.miniature.model.QuantityCodec;
import com.aquariverr.fluxod.miniature.model.RecipeReference;
import com.aquariverr.fluxod.miniature.model.ResourceAmount;
import com.aquariverr.fluxod.miniature.model.ResourceKey;
import com.aquariverr.fluxod.miniature.model.ResourceKind;
import com.aquariverr.fluxod.miniature.storage.FactoryItemResources;
import com.aquariverr.fluxod.miniature.storage.FactoryState;
import com.aquariverr.fluxod.miniature.storage.FactoryStorage;
import com.aquariverr.fluxod.miniature.storage.ResourceInventory;
import com.aquariverr.fluxod.network.MiniatureFactoryCommandPayload;
import com.aquariverr.fluxod.network.MiniatureFactoryCommandPayload.Action;
import com.aquariverr.fluxod.network.MiniatureFactorySnapshotPayload;
import com.aquariverr.fluxod.network.MiniatureFactorySnapshotPayload.CellPreview;
import com.aquariverr.fluxod.network.MiniatureFactorySnapshotPayload.ErrorContext;
import com.aquariverr.fluxod.network.MiniatureFactorySnapshotPayload.FactorySnapshot;
import com.aquariverr.fluxod.network.MiniatureFactorySnapshotPayload.Result;
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
import net.minecraft.nbt.Tag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket;
import net.minecraft.network.protocol.common.ServerboundCustomPayloadPacket;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.network.connection.ConnectionType;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

@GameTestHolder(FluxOverdrive.MODID)
@PrefixGameTestTemplate(false)
public final class MiniatureProtocolGameTests {

    private static final UUID SESSION = UUID.fromString("8311ec02-4d4e-479d-92f2-7fd9088c1631");
    private static final BlockPos TARGET = new BlockPos(-19, 90, 121);

    @GameTest(template = "empty")
    public static void everyCommandRoundTripsWithSessionAndExactParameters(GameTestHelper helper) {
        for (Action action : Action.values()) {
            BigInteger quantity = action == Action.SET_MARKER_QUANTITY ? FactoryLimits.MAX_ENCODED_QUANTITY
                    : action.usesQuantity() ? BigInteger.valueOf(64) : BigInteger.ONE;
            GridDirection direction = action == Action.SET_DIRECTION ? GridDirection.UP : GridDirection.RIGHT;
            var expected = new MiniatureFactoryCommandPayload(5, SESSION, TARGET, Long.MAX_VALUE,
                    Long.MAX_VALUE, action, action.isInventory() || action == Action.COMPILE || action == Action.AUTO_BALANCE || action == Action.UPGRADE || action == Action.SET_INPUT_CAPACITY ? 0 : 10,
                    action.isInventory() || action == Action.COMPILE || action == Action.AUTO_BALANCE || action == Action.UPGRADE || action == Action.SET_INPUT_CAPACITY ? 0 : 10, quantity, direction,
                    action.requiresSnapshot() ? SESSION : MiniatureFactoryCommandPayload.NO_SNAPSHOT,
                    action == Action.SET_PRODUCT_DIRECTION || action == Action.SET_INPUT_CAPACITY ? 0 : action.isContentMarker() || action.isInventory() && action != Action.INSERT_INPUT ? 255 : -1,
                    action == Action.SET_GHOST_MARKER ? FactoryItemResources.keyOf(new ItemStack(Items.IRON_ORE), helper.getLevel().registryAccess()) : null,
                    action == Action.SELECT_RECIPE ? new RecipeReference("flux_overdrive:smelting", "minecraft:iron_ingot_from_smelting_iron_ore", 1, 1,
                            List.of(new RecipeReference.Binding(0, FactoryItemResources.keyOf(new ItemStack(Items.IRON_ORE), helper.getLevel().registryAccess())))) : null);
            RegistryFriendlyByteBuf buffer = buffer(helper);
            try {
                MiniatureFactoryCommandPayload.STREAM_CODEC.encode(buffer, expected);
                helper.assertTrue(buffer.readableBytes() < MiniatureFactoryCommandPayload.MAX_PAYLOAD_BYTES,
                        "Command exceeded its bounded C2S payload size");
                helper.assertTrue(expected.equals(MiniatureFactoryCommandPayload.STREAM_CODEC.decode(buffer)),
                        "Command round trip changed its identity or exact parameters");
                helper.assertTrue(!buffer.isReadable(), "Command frame was not fully consumed");
            } finally {
                buffer.release();
            }
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void registeredMinecraftPayloadWrappersRoundTripBothDirections(GameTestHelper helper) {
        var command = new MiniatureFactoryCommandPayload(1, SESSION, TARGET, 0, 1,
                Action.SET_DIRECTION, 2, 1, BigInteger.ONE, GridDirection.DOWN);
        RegistryFriendlyByteBuf serverbound = buffer(helper);
        RegistryFriendlyByteBuf clientbound = buffer(helper);
        try {
            ServerboundCustomPayloadPacket.STREAM_CODEC.encode(serverbound, new ServerboundCustomPayloadPacket(command));
            helper.assertTrue(serverbound.readableBytes() < MiniatureFactoryCommandPayload.MAX_PAYLOAD_BYTES,
                    "Registered command envelope exceeded its bound");
            helper.assertTrue(command.equals(ServerboundCustomPayloadPacket.STREAM_CODEC.decode(serverbound).payload()),
                    "Registered serverbound packet did not decode through the miniature command codec");
            var preview = snapshot(FactorySnapshot.from(FactoryState.fresh()));
            ClientboundCustomPayloadPacket.GAMEPLAY_STREAM_CODEC.encode(clientbound, new ClientboundCustomPayloadPacket(preview));
            helper.assertTrue(clientbound.readableBytes() < MiniatureFactorySnapshotPayload.MAX_PAYLOAD_BYTES,
                    "Registered snapshot envelope exceeded its bound");
            helper.assertTrue(preview.equals(ClientboundCustomPayloadPacket.GAMEPLAY_STREAM_CODEC.decode(clientbound).payload()),
                    "Registered clientbound packet did not decode through the miniature snapshot codec");
            helper.assertTrue(!serverbound.isReadable() && !clientbound.isReadable(),
                    "Registered packet wrapper left unread payload data");
        } finally {
            serverbound.release();
            clientbound.release();
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void malformedCommandIdentitiesAreRejected(GameTestHelper helper) {
        for (int variant = 0; variant < 4; variant++) {
            int index = variant;
            rejectCommand(helper, body -> commandBody(body, index == 0 ? -1 : 1, index == 1 ? -1 : 0,
                    index == 2 ? 0 : index == 3 ? -1 : 1, 0, 0, 0, new byte[]{1}, 0));
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void malformedCommandCoordinatesAreRejected(GameTestHelper helper) {
        rejectCommand(helper, body -> commandBody(body, 1, 0, 1, 0, 11, 0, new byte[]{1}, 0));
        rejectCommand(helper, body -> commandBody(body, 1, 0, 1, 0, 0, 255, new byte[]{1}, 0));
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void malformedAndOversizedCommandQuantitiesAreRejected(GameTestHelper helper) {
        for (byte[] quantity : List.of(new byte[0], new byte[]{0}, new byte[33])) {
            rejectCommand(helper, body -> commandBody(body, 1, 0, 1,
                    Action.SET_MARKER_QUANTITY.ordinal(), 0, 0, quantity, 0));
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void unusedCommandParametersMustRemainCanonical(GameTestHelper helper) {
        rejectCommand(helper, body -> commandBody(body, 1, 0, 1, Action.CREATE_ARROW.ordinal(),
                0, 0, new byte[]{2}, 0));
        rejectCommand(helper, body -> commandBody(body, 1, 0, 1, Action.INSERT_MACHINE.ordinal(),
                0, 0, new byte[]{1}, GridDirection.UP.ordinal()));
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void unknownCommandActionsAndDirectionsAreRejected(GameTestHelper helper) {
        rejectCommand(helper, body -> commandBody(body, 1, 0, 1, 255, 0, 0, new byte[]{1}, 0));
        rejectCommand(helper, body -> commandBody(body, 1, 0, 1, Action.SET_DIRECTION.ordinal(),
                0, 0, new byte[]{1}, 255));
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void inventorySelectionsRequireBoundedIndicesAndAnExplicitSnapshot(GameTestHelper helper) {
        rejectCommand(helper, body -> commandBody(body, 1, 0, 1, Action.EXTRACT_INPUT.ordinal(),
                0, 0, new byte[]{1}, 0, SESSION, -1));
        rejectCommand(helper, body -> commandBody(body, 1, 0, 1, Action.EXTRACT_OUTPUT.ordinal(),
                0, 0, new byte[]{1}, 0, SESSION, 256));
        rejectCommand(helper, body -> commandBody(body, 1, 0, 1, Action.EXTRACT_INPUT.ordinal(),
                0, 0, new byte[]{1}, 0, MiniatureFactoryCommandPayload.NO_SNAPSHOT, 0));
        rejectCommand(helper, body -> commandBody(body, 1, 0, 1, Action.INSERT_INPUT.ordinal(),
                0, 0, new byte[]{1}, 0, SESSION, 0));
        rejectCommand(helper, body -> commandBody(body, 1, 0, 1, Action.INSERT_INPUT.ordinal(),
                1, 0, new byte[]{1}, 0, SESSION, -1));
        rejectCommand(helper, body -> commandBody(body, 1, 0, 1, Action.CREATE_ARROW.ordinal(),
                0, 0, new byte[]{1}, 0, SESSION, -1));
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void contentMarkerSelectionsRequireExplicitBoundedTanksAndCanonicalFields(GameTestHelper helper) {
        for (Action action : List.of(Action.SET_FLUID_MARKER_FROM_CARRIED, Action.SET_CHEMICAL_MARKER_FROM_CARRIED)) {
            for (int tank : List.of(-1, MiniatureFactoryCommandPayload.MAX_MARKER_TANKS, Integer.MAX_VALUE)) {
                rejectCommand(helper, body -> commandBody(body, 1, 0, 1, action.ordinal(), 0, 0,
                        new byte[]{1}, 0, MiniatureFactoryCommandPayload.NO_SNAPSHOT, tank));
            }
            rejectCommand(helper, body -> commandBody(body, 1, 0, 1, action.ordinal(), 0, 0, new byte[]{1}, 0, SESSION, 0));
            rejectCommand(helper, body -> commandBody(body, 1, 0, 1, action.ordinal(), 0, 0,
                    new byte[]{2}, 0, MiniatureFactoryCommandPayload.NO_SNAPSHOT, 0));
            rejectCommand(helper, body -> commandBody(body, 1, 0, 1, action.ordinal(), 0, 0,
                    new byte[]{1}, GridDirection.UP.ordinal(), MiniatureFactoryCommandPayload.NO_SNAPSHOT, 0));
        }
        rejectCommand(helper, body -> commandBody(body, 1, 0, 1, Action.SET_MARKER_FROM_CARRIED.ordinal(), 0, 0,
                new byte[]{1}, 0, MiniatureFactoryCommandPayload.NO_SNAPSHOT, 0));
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void commandFramesRejectOversizeTruncationAndTrailingData(GameTestHelper helper) {
        rejectCommand(helper, body -> {
            commandBody(body, 1, 0, 1, 0, 0, 0, new byte[]{1}, 0);
            body.writeByte(0);
        });
        rejectCommand(helper, body -> body.writeZero(MiniatureFactoryCommandPayload.MAX_PAYLOAD_BYTES));
        rejectDeclaredFrame(helper, MiniatureFactoryCommandPayload.MAX_PAYLOAD_BYTES + 1, true);
        rejectDeclaredFrame(helper, 100, true);
        rejectDeclaredFrame(helper, -1, true);
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void validPayloadsWithExternalPaddingAreRejected(GameTestHelper helper) {
        for (boolean command : List.of(false, true)) {
            int maximum = command ? MiniatureFactoryCommandPayload.MAX_PAYLOAD_BYTES
                    : MiniatureFactorySnapshotPayload.MAX_PAYLOAD_BYTES;
            for (int padding : List.of(1, maximum)) {
                RegistryFriendlyByteBuf buffer = buffer(helper);
                try {
                    if (command) {
                        MiniatureFactoryCommandPayload.STREAM_CODEC.encode(buffer,
                                new MiniatureFactoryCommandPayload(1, SESSION, TARGET, 0, 1,
                                        Action.CREATE_ARROW, 0, 0, BigInteger.ONE, GridDirection.RIGHT));
                    } else {
                        MiniatureFactorySnapshotPayload.STREAM_CODEC.encode(buffer,
                                snapshot(FactorySnapshot.from(FactoryState.fresh())));
                    }
                    buffer.writeZero(padding);
                    assertDecodeRejected(helper, buffer, command);
                } finally {
                    buffer.release();
                }
            }
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void snapshotRoundTripPreservesExactVariantsAndSeparateAcknowledgementRevision(GameTestHelper helper) {
        var registries = helper.getLevel().registryAccess();
        ItemStack first = new ItemStack(Items.FURNACE);
        first.set(DataComponents.CUSTOM_NAME, Component.literal("Preview furnace one"));
        first.set(DataComponents.MAX_STACK_SIZE, 1);
        ItemStack second = first.copy();
        second.set(DataComponents.CUSTOM_NAME, Component.literal("Preview furnace two"));
        ResourceKey ore = FactoryItemResources.keyOf(new ItemStack(Items.IRON_ORE), registries);
        FactoryStorage storage = FactoryStorage.empty()
                .insertMachine(new GridPosition(0, 0), first, registries).storage()
                .insertMachine(new GridPosition(1, 0), second, registries).storage()
                .withInputs(new ResourceInventory(Map.of(ore, FactoryLimits.MAX_INVENTORY_AMOUNT)))
                .withOutputs(new ResourceInventory(Map.of(ore, BigInteger.TEN)));
        FactoryState state = FactoryState.of(storage, registries);
        FactorySnapshot preview = FactorySnapshot.from(state);
        var expected = new MiniatureFactorySnapshotPayload(1, SESSION, TARGET, 9, 12, 2, Result.SUCCESS, preview);
        var actual = roundTripSnapshot(helper, expected);
        helper.assertTrue(expected.equals(actual), "Snapshot lost an exact resource, quantity, session, or acknowledgement");
        helper.assertTrue(!actual.snapshot().cells().get(0).resource().equals(actual.snapshot().cells().get(1).resource()),
                "Same-ID machines with distinct components were merged in the preview");
        helper.assertTrue(actual.acknowledgedRevision() == 2 && actual.revision() == 9,
                "A replay acknowledgement replaced the current snapshot revision");
        helper.assertTrue(state.save().equals(FactoryState.of(storage, registries).save()),
                "Building the preview changed authoritative storage");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void snapshotIncludesEveryCellTypeAndExactLargeMarkerQuantities(GameTestHelper helper) {
        ResourceKey resource = new ResourceKey(ResourceKind.ITEM, "minecraft:iron_ore");
        List<CellPreview> cells = new ArrayList<>(Collections.nCopies(9, CellPreview.empty()));
        cells.set(1, CellPreview.marker(new ResourceAmount(resource, FactoryLimits.MAX_ENCODED_QUANTITY), GridDirection.UP));
        cells.set(2, CellPreview.arrow(GridDirection.LEFT));
        for (GridDirection direction : GridDirection.values()) {
            cells.set(0, CellPreview.machine(new ResourceKey(ResourceKind.ITEM, "minecraft:furnace"), 64, direction));
            var expected = snapshot(new FactorySnapshot(3, cells, List.of(), List.of(), FactoryState.Status.READY));
            helper.assertTrue(expected.equals(roundTripSnapshot(helper, expected)), "Preview did not retain machine direction and all cell forms");
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void errorContextsRoundTripOptionalPositionsAndExactUnsignedComparisons(GameTestHelper helper) {
        for (ErrorContext context : List.of(ErrorContext.NONE, new ErrorContext(10, 10, null, null),
                new ErrorContext(-1, -1, BigInteger.ZERO, FactoryLimits.MAX_ENCODED_QUANTITY),
                new ErrorContext(2, 1, BigInteger.valueOf(64), BigInteger.valueOf(65)),
                new ErrorContext(-1, -1, null, BigInteger.ZERO),
                new ErrorContext(0, 0, BigInteger.ONE, null))) {
            var source = snapshot(FactorySnapshot.from(FactoryState.fresh())).withSnapshotId(SESSION).withErrorContext(context);
            helper.assertTrue(source.equals(roundTripSnapshot(helper, source)),
                    "Error context lost an optional position or exact expected/actual comparison");
            helper.assertTrue(SESSION.equals(source.snapshotId()), "Adding error context changed the inventory snapshot identity");
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void errorContextsRejectUnknownFlagsOversizedNumbersAndInvalidCoordinates(GameTestHelper helper) {
        for (int variant = 0; variant < 3; variant++) {
            int index = variant;
            rejectSnapshot(helper, body -> {
                snapshotIdentity(body);
                body.writeByte(index == 0 ? 8 : index == 1 ? 2 : 1);
                if (index == 1) body.writeByteArray(new byte[33]);
                if (index == 2) {
                    body.writeByte(11);
                    body.writeByte(0);
                }
                body.writeByte(FactoryState.Status.READY.ordinal());
                body.writeByte(3);
                body.writeVarInt(0);
                body.writeVarInt(9);
                body.writeZero(9);
                body.writeVarInt(0);
                body.writeVarInt(0);
            });
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void unavailableSnapshotsContainNoRetainedRawFactoryState(GameTestHelper helper) {
        for (boolean unknown : List.of(false, true)) {
            CompoundTag raw = FactoryState.fresh().save();
            raw.putString("format", unknown ? "test:future_factory" : "flux_overdrive:miniature_factory");
            raw.putString("private_future_payload", "must never become a menu snapshot");
            FactoryState retained = FactoryState.load(raw, helper.getLevel().registryAccess());
            FactorySnapshot preview = roundTripSnapshot(helper, snapshot(FactorySnapshot.from(retained))).snapshot();
            helper.assertTrue(preview.size() == 0 && preview.cells().isEmpty()
                            && preview.inputs().isEmpty() && preview.outputs().isEmpty()
                            && preview.status() == retained.status(),
                    "Unavailable state exposed partial editable assets");
            helper.assertTrue(raw.equals(retained.save()), "Menu preview changed retained server NBT");
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void resourceTableDeduplicatesLargeComponentsAcrossCellsAndInventories(GameTestHelper helper) {
        CompoundTag components = new CompoundTag();
        components.putByteArray("test:payload", new byte[60_000]);
        ResourceKey resource = new ResourceKey(ResourceKind.ITEM, "minecraft:iron_ore", components);
        List<CellPreview> cells = Collections.nCopies(121,
                CellPreview.marker(new ResourceAmount(resource, BigInteger.ONE), GridDirection.RIGHT));
        var expected = snapshot(new FactorySnapshot(11, cells, List.of(new ResourceAmount(resource, BigInteger.TEN)),
                List.of(new ResourceAmount(resource, BigInteger.TWO)), FactoryState.Status.READY));
        RegistryFriendlyByteBuf buffer = buffer(helper);
        try {
            MiniatureFactorySnapshotPayload.STREAM_CODEC.encode(buffer, expected);
            helper.assertTrue(buffer.readableBytes() < FactoryLimits.MAX_COMPONENT_BYTES,
                    "Resource components were repeated for every cell or inventory");
            helper.assertTrue(expected.equals(MiniatureFactorySnapshotPayload.STREAM_CODEC.decode(buffer)),
                    "Deduplicated resource references did not restore the complete preview");
        } finally {
            buffer.release();
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void snapshotFramesRejectOversizeTruncationAndTrailingData(GameTestHelper helper) {
        rejectDeclaredFrame(helper, FactoryLimits.MAX_MESSAGE_BYTES + 1, false);
        rejectDeclaredFrame(helper, 100, false);
        rejectDeclaredFrame(helper, -1, false);
        rejectSnapshot(helper, body -> {
            emptySnapshotBody(body, FactoryState.Status.READY.ordinal(), 3, 0, 9);
            body.writeByte(0);
        });
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void snapshotRejectsInvalidEnumsAndCollectionBoundsBeforeAllocating(GameTestHelper helper) {
        rejectSnapshot(helper, body -> emptySnapshotBody(body, 255, 3, 0, 9));
        rejectSnapshot(helper, body -> emptySnapshotBody(body, 0, 3, -1, 9));
        rejectSnapshot(helper, body -> emptySnapshotBody(body, 0, 3, 634, 9));
        rejectSnapshot(helper, body -> emptySnapshotBody(body, 0, 3, 0, 122));
        rejectSnapshot(helper, body -> emptySnapshotBody(body, 0, 3, 0, 8));
        rejectSnapshot(helper, body -> emptySnapshotBody(body, 0, 4, 0, 16));
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void snapshotRejectsOversizedComponentsAndInvalidResourceReferences(GameTestHelper helper) {
        rejectSnapshot(helper, body -> {
            snapshotHeader(body, 0, 3);
            body.writeVarInt(1);
            body.writeByte(ResourceKind.ITEM.ordinal());
            body.writeUtf("minecraft:iron_ore");
            body.writeVarInt(FactoryLimits.MAX_COMPONENT_BYTES + 1);
        });
        rejectSnapshot(helper, body -> {
            snapshotHeader(body, 0, 3);
            body.writeVarInt(0);
            body.writeVarInt(9);
            body.writeByte(CellPreview.Type.MACHINE.ordinal());
            body.writeVarInt(0);
            body.writeByte(1);
        });
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void snapshotRejectsDuplicateResourceTableAndInventoryEntries(GameTestHelper helper) {
        rejectSnapshot(helper, body -> {
            snapshotHeader(body, 0, 3);
            body.writeVarInt(2);
            writeSimpleResource(body);
            writeSimpleResource(body);
            body.writeVarInt(9);
            body.writeZero(9);
            body.writeVarInt(0);
            body.writeVarInt(0);
        });
        rejectSnapshot(helper, body -> {
            snapshotHeader(body, 0, 3);
            body.writeVarInt(1);
            writeSimpleResource(body);
            body.writeVarInt(9);
            body.writeZero(9);
            body.writeVarInt(2);
            for (int entry = 0; entry < 2; entry++) {
                body.writeVarInt(0);
                body.writeByteArray(new byte[]{1});
            }
            body.writeVarInt(0);
        });
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void snapshotEncodingFailsBeforeWritingOversizedOrExcessiveHeapPreviews(GameTestHelper helper) {
        for (boolean heapFixture : List.of(false, true)) {
            List<ResourceAmount> amounts = new ArrayList<>();
            int count = heapFixture ? 3 : 18;
            for (int index = 0; index < count; index++) {
                CompoundTag components = new CompoundTag();
                if (heapFixture) {
                    ListTag entries = new ListTag();
                    for (int entry = 0; entry < 20_000; entry++) entries.add(new CompoundTag());
                    components.put("test:entries", entries);
                } else {
                    components.putByteArray("test:payload", new byte[60_000]);
                }
                amounts.add(new ResourceAmount(new ResourceKey(ResourceKind.ITEM, "test:variant_" + index, components), BigInteger.ONE));
            }
            var preview = snapshot(new FactorySnapshot(3, Collections.nCopies(9, CellPreview.empty()), amounts,
                    List.of(), FactoryState.Status.READY));
            RegistryFriendlyByteBuf buffer = buffer(helper);
            try {
                boolean rejected = false;
                try {
                    MiniatureFactorySnapshotPayload.STREAM_CODEC.encode(buffer, preview);
                } catch (EncoderException expected) {
                    rejected = true;
                }
                helper.assertTrue(rejected, "Oversized preview was encoded");
                helper.assertTrue(buffer.writerIndex() == 0, "Rejected preview partially wrote the network buffer");
                helper.assertTrue(preview.snapshot().inputs().equals(amounts), "Rejected encoding changed preview resources");
            } finally {
                buffer.release();
            }
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void snapshotDecodingSharesTheNbtHeapBudgetAcrossAllResources(GameTestHelper helper) throws IOException {
        CompoundTag components = new CompoundTag();
        ListTag entries = new ListTag();
        for (int entry = 0; entry < 20_000; entry++) entries.add(new CompoundTag());
        components.put("test:entries", entries);
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        NbtIo.writeUnnamedTag(components, new DataOutputStream(bytes));
        byte[] encoded = bytes.toByteArray();
        helper.assertTrue(encoded.length < FactoryLimits.MAX_COMPONENT_BYTES, "Heap fixture exceeded its byte bound");
        rejectSnapshot(helper, body -> {
            snapshotHeader(body, 0, 3);
            body.writeVarInt(3);
            for (int index = 0; index < 3; index++) {
                body.writeByte(ResourceKind.ITEM.ordinal());
                body.writeUtf("test:variant_" + index);
                body.writeByteArray(encoded);
            }
            body.writeVarInt(9);
            body.writeZero(9);
            body.writeVarInt(0);
            body.writeVarInt(0);
        });
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void snapshotViewsCannotMutateTheirSourceCollectionsOrComponents(GameTestHelper helper) {
        CompoundTag components = new CompoundTag();
        components.putString("test:identity", "original");
        ResourceKey resource = new ResourceKey(ResourceKind.ITEM, "minecraft:iron_ore", components);
        List<CellPreview> cells = new ArrayList<>(Collections.nCopies(9, CellPreview.empty()));
        cells.set(0, CellPreview.marker(new ResourceAmount(resource, BigInteger.ONE), GridDirection.DOWN));
        List<ResourceAmount> inputs = new ArrayList<>(List.of(new ResourceAmount(resource, BigInteger.TEN)));
        FactorySnapshot preview = new FactorySnapshot(3, cells, inputs, List.of(), FactoryState.Status.READY);
        cells.clear();
        inputs.clear();
        components.putString("test:identity", "changed");
        preview.cells().get(0).resource().components().putString("test:identity", "also changed");
        helper.assertTrue(preview.cells().size() == 9 && preview.inputs().size() == 1
                        && preview.cells().get(0).resource().components().getString("test:identity").equals("original"),
                "Preview aliases mutable caller collections or resource components");
        helper.succeed();
    }

    private static MiniatureFactorySnapshotPayload snapshot(FactorySnapshot preview) {
        return new MiniatureFactorySnapshotPayload(1, SESSION, TARGET, 0, 0, 0, Result.SUCCESS, preview);
    }

    private static MiniatureFactorySnapshotPayload roundTripSnapshot(GameTestHelper helper, MiniatureFactorySnapshotPayload source) {
        RegistryFriendlyByteBuf buffer = buffer(helper);
        try {
            MiniatureFactorySnapshotPayload.STREAM_CODEC.encode(buffer, source);
            helper.assertTrue(buffer.readableBytes() < MiniatureFactorySnapshotPayload.MAX_PAYLOAD_BYTES,
                    "Snapshot exceeded its payload budget");
            MiniatureFactorySnapshotPayload result = MiniatureFactorySnapshotPayload.STREAM_CODEC.decode(buffer);
            helper.assertTrue(!buffer.isReadable(), "Snapshot frame was not fully consumed");
            return result;
        } finally {
            buffer.release();
        }
    }

    private static void rejectCommand(GameTestHelper helper, Consumer<FriendlyByteBuf> writer) {
        rejectFrame(helper, writer, true);
    }

    private static void rejectSnapshot(GameTestHelper helper, Consumer<FriendlyByteBuf> writer) {
        rejectFrame(helper, writer, false);
    }

    private static void rejectFrame(GameTestHelper helper, Consumer<FriendlyByteBuf> writer, boolean command) {
        FriendlyByteBuf body = new FriendlyByteBuf(Unpooled.buffer());
        RegistryFriendlyByteBuf framed = buffer(helper);
        try {
            writer.accept(body);
            framed.writeVarInt(body.readableBytes());
            framed.writeBytes(body);
            assertDecodeRejected(helper, framed, command);
        } finally {
            body.release();
            framed.release();
        }
    }

    private static void rejectDeclaredFrame(GameTestHelper helper, int length, boolean command) {
        RegistryFriendlyByteBuf framed = buffer(helper);
        try {
            framed.writeVarInt(length);
            assertDecodeRejected(helper, framed, command);
        } finally {
            framed.release();
        }
    }

    private static void assertDecodeRejected(GameTestHelper helper, RegistryFriendlyByteBuf framed, boolean command) {
        boolean rejected = false;
        try {
            if (command) MiniatureFactoryCommandPayload.STREAM_CODEC.decode(framed);
            else MiniatureFactorySnapshotPayload.STREAM_CODEC.decode(framed);
        } catch (DecoderException expected) {
            rejected = true;
        }
        helper.assertTrue(rejected, "Malformed miniature payload decoded successfully");
    }

    private static void commandBody(FriendlyByteBuf body, int containerId, long revision, long commandId,
                                    int action, int x, int y, byte[] quantity, int direction) {
        commandBody(body, containerId, revision, commandId, action, x, y, quantity, direction,
                MiniatureFactoryCommandPayload.NO_SNAPSHOT, -1);
    }

    private static void commandBody(FriendlyByteBuf body, int containerId, long revision, long commandId,
                                    int action, int x, int y, byte[] quantity, int direction, UUID snapshotId, int resourceIndex) {
        body.writeVarInt(containerId);
        body.writeUUID(SESSION);
        body.writeBlockPos(TARGET);
        body.writeVarLong(revision);
        body.writeVarLong(commandId);
        body.writeByte(action);
        body.writeByte(x);
        body.writeByte(y);
        body.writeByteArray(quantity);
        body.writeByte(direction);
        body.writeUUID(snapshotId);
        body.writeVarInt(resourceIndex);
        // Protocol 5 always carries one strict intent compound, empty for the legacy commands above.
        body.writeByteArray(new byte[]{Tag.TAG_COMPOUND, Tag.TAG_END});
    }

    private static void snapshotHeader(FriendlyByteBuf body, int status, int size) {
        snapshotIdentity(body);
        body.writeByte(0);
        body.writeByte(status);
        body.writeByte(size);
    }

    private static void snapshotIdentity(FriendlyByteBuf body) {
        body.writeVarInt(1);
        body.writeUUID(SESSION);
        body.writeBlockPos(TARGET);
        body.writeUUID(MiniatureFactoryCommandPayload.NO_SNAPSHOT);
        body.writeVarLong(0);
        body.writeVarLong(0);
        body.writeVarLong(0);
        body.writeByte(Result.SUCCESS.ordinal());
    }

    private static void emptySnapshotBody(FriendlyByteBuf body, int status, int size, int resources, int cells) {
        snapshotHeader(body, status, size);
        body.writeVarInt(resources);
        body.writeVarInt(cells);
        body.writeZero(Math.max(0, cells));
        body.writeVarInt(0);
        body.writeVarInt(0);
    }

    private static void writeSimpleResource(FriendlyByteBuf body) {
        body.writeByte(ResourceKind.ITEM.ordinal());
        body.writeUtf("minecraft:iron_ore");
        body.writeByteArray(new byte[]{10, 0, 0, 0});
    }

    private static RegistryFriendlyByteBuf buffer(GameTestHelper helper) {
        return new RegistryFriendlyByteBuf(Unpooled.buffer(), helper.getLevel().registryAccess(), ConnectionType.NEOFORGE);
    }

    private MiniatureProtocolGameTests() {}
}
