package com.aquariverr.fluxod.network;

import com.aquariverr.fluxod.FluxOverdrive;
import com.aquariverr.fluxod.miniature.model.FactoryLimits;
import com.aquariverr.fluxod.miniature.model.GridDirection;
import com.aquariverr.fluxod.miniature.model.QuantityCodec;
import com.aquariverr.fluxod.miniature.model.RecipeReference;
import com.aquariverr.fluxod.miniature.model.ResourceKey;
import com.aquariverr.fluxod.miniature.storage.FactoryStateCodec;
import com.aquariverr.fluxod.miniature.storage.RecipeReferenceCodec;
import com.aquariverr.fluxod.miniature.storage.ResourceNbtCodec;
import io.netty.buffer.ByteBufOutputStream;
import io.netty.buffer.Unpooled;
import io.netty.handler.codec.DecoderException;
import io.netty.handler.codec.EncoderException;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.Tag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import java.math.BigInteger;
import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.DataOutputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.security.DigestOutputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

public record MiniatureFactoryCommandPayload(int containerId, UUID sessionId, BlockPos targetPos,
                                             long expectedRevision, long commandId, Action action,
                                             int x, int y, BigInteger quantity, GridDirection direction,
                                             UUID expectedSnapshotId, int resourceIndex,
                                             ResourceKey markerResource, RecipeReference requestedRecipe)
        implements CustomPacketPayload {

    public static final int MAX_PAYLOAD_BYTES = FactoryLimits.MAX_MESSAGE_BYTES;
    public static final int MAX_MARKER_TANKS = 256;
    public static final UUID NO_SNAPSHOT = new UUID(0, 0);
    // Reserve space for the custom-payload ID and framing outside this codec.
    private static final int MAX_BODY_BYTES = MAX_PAYLOAD_BYTES - 128;

    public static final Type<MiniatureFactoryCommandPayload> TYPE =
            new Type<>(FluxOverdrive.location("miniature_factory_command"));
    public static final StreamCodec<RegistryFriendlyByteBuf, MiniatureFactoryCommandPayload> STREAM_CODEC =
            new StreamCodec<>() {
                @Override
                public MiniatureFactoryCommandPayload decode(RegistryFriendlyByteBuf buffer) {
                    if (buffer.readableBytes() > MAX_PAYLOAD_BYTES) {
                        throw new DecoderException("Oversized miniature command payload");
                    }
                    int length = buffer.readVarInt();
                    if (length < 1 || length > MAX_BODY_BYTES || length != buffer.readableBytes()) {
                        throw new DecoderException("Invalid miniature command frame length");
                    }
                    FriendlyByteBuf body = new FriendlyByteBuf(buffer.readSlice(length));
                    try {
                        int containerId = body.readVarInt();
                        UUID sessionId = body.readUUID();
                        BlockPos targetPos = body.readBlockPos();
                        long revision = body.readVarLong();
                        long commandId = body.readVarLong();
                        Action action = readEnum(body, Action.values());
                        int x = body.readUnsignedByte();
                        int y = body.readUnsignedByte();
                        BigInteger quantity = QuantityCodec.decode(body.readByteArray(FactoryLimits.MAX_QUANTITY_BYTES));
                        GridDirection direction = readEnum(body, GridDirection.values());
                        UUID expectedSnapshotId = body.readUUID();
                        int resourceIndex = body.readVarInt();
                        CompoundTag intent = readIntent(body.readByteArray(MAX_BODY_BYTES));
                        ResourceKey marker = null;
                        RecipeReference recipe = null;
                        if (action == Action.SET_GHOST_MARKER) {
                            ResourceNbtCodec.requireFields(intent, Set.of("marker"));
                            ResourceNbtCodec.requireType(intent, "marker", Tag.TAG_COMPOUND);
                            marker = ResourceNbtCodec.readKey(intent.getCompound("marker"));
                        } else if (action == Action.SELECT_RECIPE) {
                            ResourceNbtCodec.requireFields(intent, Set.of("recipe"));
                            ResourceNbtCodec.requireType(intent, "recipe", Tag.TAG_COMPOUND);
                            recipe = RecipeReferenceCodec.read(intent.getCompound("recipe"));
                        } else ResourceNbtCodec.requireFields(intent, Set.of());
                        MiniatureFactoryCommandPayload payload = new MiniatureFactoryCommandPayload(containerId,
                                sessionId, targetPos, revision, commandId, action, x, y, quantity, direction,
                                expectedSnapshotId, resourceIndex, marker, recipe);
                        if (body.isReadable()) throw new DecoderException("Trailing miniature command data");
                        return payload;
                    } catch (IOException | RuntimeException exception) {
                        throw new DecoderException("Invalid miniature command", exception);
                    }
                }

                @Override
                public void encode(RegistryFriendlyByteBuf buffer, MiniatureFactoryCommandPayload payload) {
                    FriendlyByteBuf body = new FriendlyByteBuf(Unpooled.buffer(64, MAX_BODY_BYTES));
                    try {
                        body.writeVarInt(payload.containerId());
                        body.writeUUID(payload.sessionId());
                        body.writeBlockPos(payload.targetPos());
                        body.writeVarLong(payload.expectedRevision());
                        body.writeVarLong(payload.commandId());
                        body.writeByte(payload.action().ordinal());
                        body.writeByte(payload.x());
                        body.writeByte(payload.y());
                        body.writeByteArray(QuantityCodec.encode(payload.quantity()));
                        body.writeByte(payload.direction().ordinal());
                        body.writeUUID(payload.expectedSnapshotId());
                        body.writeVarInt(payload.resourceIndex());
                        CompoundTag intent = new CompoundTag();
                        if (payload.markerResource() != null) intent.put("marker", ResourceNbtCodec.writeKey(payload.markerResource()));
                        if (payload.requestedRecipe() != null) intent.put("recipe", RecipeReferenceCodec.write(payload.requestedRecipe()));
                        FriendlyByteBuf encoded = new FriendlyByteBuf(Unpooled.buffer(64, MAX_BODY_BYTES));
                        try {
                            NbtIo.writeUnnamedTag(intent, new ByteBufOutputStream(encoded));
                            byte[] bytes = new byte[encoded.readableBytes()];
                            encoded.readBytes(bytes);
                            // One parse accounts for every binding/component together, including on outbound packets.
                            readIntent(bytes);
                            body.writeByteArray(bytes);
                        } finally {
                            encoded.release();
                        }
                        buffer.writeVarInt(body.readableBytes());
                        buffer.writeBytes(body);
                    } catch (IOException | RuntimeException exception) {
                        throw new EncoderException("Invalid miniature command", exception);
                    } finally {
                        body.release();
                    }
                }
            };

    public MiniatureFactoryCommandPayload {
        if (containerId < 0 || expectedRevision < 0 || commandId <= 0) {
            throw new IllegalArgumentException("Invalid miniature command identity or revision");
        }
        Objects.requireNonNull(sessionId, "sessionId");
        targetPos = Objects.requireNonNull(targetPos, "targetPos").immutable();
        Objects.requireNonNull(action, "action");
        Objects.requireNonNull(direction, "direction");
        Objects.requireNonNull(expectedSnapshotId, "expectedSnapshotId");
        if ((action == Action.SET_GHOST_MARKER) != (markerResource != null)
                || (action == Action.SELECT_RECIPE) != (requestedRecipe != null)) {
            throw new IllegalArgumentException("Command intent does not match its action");
        }
        if (x < 0 || x >= FactoryLimits.MAX_GRID_SIZE || y < 0 || y >= FactoryLimits.MAX_GRID_SIZE) {
            throw new IllegalArgumentException("Invalid miniature command coordinate");
        }
        if (action == Action.SET_ARROW_WEIGHT) {
            FactoryLimits.requireQuantity(quantity);
            if (quantity.compareTo(BigInteger.valueOf(com.aquariverr.fluxod.miniature.model.FactoryCell.Arrow.MAX_WEIGHT)) > 0)
                throw new IllegalArgumentException("Arrow weight exceeds limit");
        } else FactoryLimits.requirePositiveQuantity(quantity);
        if (action == Action.SET_INPUT_CAPACITY) FactoryLimits.requireInventoryQuantity(quantity);
        if (!action.usesQuantity() && !BigInteger.ONE.equals(quantity)) {
            throw new IllegalArgumentException("Unused command quantity must equal one");
        }
        if (action != Action.SET_DIRECTION && action != Action.SET_ARROW_WEIGHT && action != Action.SET_PRODUCT_DIRECTION && direction != GridDirection.RIGHT) {
            throw new IllegalArgumentException("Unused command direction must equal right");
        }
        if (action.isInventory()) {
            if (NO_SNAPSHOT.equals(expectedSnapshotId) || x != 0 || y != 0) {
                throw new IllegalArgumentException("Inventory commands require a snapshot identity and no grid target");
            }
            if (action == Action.INSERT_INPUT ? resourceIndex != -1
                    : resourceIndex < 0 || resourceIndex >= FactoryLimits.MAX_BOUNDARY_RESOURCES) {
                throw new IllegalArgumentException("Invalid miniature inventory resource index");
            }
        } else if (action == Action.SET_INPUT_CAPACITY) {
            if (NO_SNAPSHOT.equals(expectedSnapshotId) || x != 0 || y != 0
                    || resourceIndex < 0 || resourceIndex >= FactoryLimits.MAX_BOUNDARY_RESOURCES * 2)
                throw new IllegalArgumentException("Input capacity requires a current snapshot and an input resource index");
        } else if (action == Action.SET_PRODUCT_DIRECTION) {
            if (NO_SNAPSHOT.equals(expectedSnapshotId) || resourceIndex < 0 || resourceIndex >= FactoryLimits.MAX_RECIPE_RESOURCES)
                throw new IllegalArgumentException("Product directions require a current output selection");
        } else if (action.isContentMarker()) {
            if (!NO_SNAPSHOT.equals(expectedSnapshotId) || resourceIndex < 0 || resourceIndex >= MAX_MARKER_TANKS) {
                throw new IllegalArgumentException("Content markers require a bounded carried-container tank index");
            }
        } else if (action.isRecipeSelection() || action == Action.SET_GHOST_MARKER || action == Action.COMPILE || action == Action.AUTO_BALANCE || action == Action.UPGRADE || action.isControl()) {
            if ((!action.isControl() && NO_SNAPSHOT.equals(expectedSnapshotId)) || resourceIndex != -1) {
                throw new IllegalArgumentException("Recipe commands require the current snapshot identity");
            }
            if ((action == Action.COMPILE || action == Action.AUTO_BALANCE || action == Action.UPGRADE) && (x != 0 || y != 0)) throw new IllegalArgumentException("Factory action has no cell target");
        } else if (!NO_SNAPSHOT.equals(expectedSnapshotId) || resourceIndex != -1) {
            throw new IllegalArgumentException("Grid commands cannot carry an inventory selection");
        }
    }

    public MiniatureFactoryCommandPayload(int containerId, UUID sessionId, BlockPos targetPos,
                                          long expectedRevision, long commandId, Action action,
                                          int x, int y, BigInteger quantity, GridDirection direction,
                                          UUID expectedSnapshotId, int resourceIndex) {
        this(containerId, sessionId, targetPos, expectedRevision, commandId, action, x, y, quantity, direction,
                expectedSnapshotId, resourceIndex, null, null);
    }

    public MiniatureFactoryCommandPayload(int containerId, UUID sessionId, BlockPos targetPos,
                                          long expectedRevision, long commandId, Action action,
                                          int x, int y, BigInteger quantity, GridDirection direction) {
        this(containerId, sessionId, targetPos, expectedRevision, commandId, action, x, y, quantity, direction,
                NO_SNAPSHOT, -1);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    /** Bounded receipts keep this semantic identity instead of retaining potentially large ingredient NBT. */
    public String fingerprint() {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            DataOutputStream out = new DataOutputStream(new DigestOutputStream(OutputStream.nullOutputStream(), digest));
            out.writeInt(containerId);
            writeUuid(out, sessionId);
            out.writeLong(targetPos.asLong());
            out.writeLong(expectedRevision);
            out.writeLong(commandId);
            out.writeInt(action.ordinal());
            out.writeInt(x);
            out.writeInt(y);
            byte[] amount = QuantityCodec.encode(quantity);
            out.writeInt(amount.length);
            out.write(amount);
            out.writeInt(direction.ordinal());
            writeUuid(out, expectedSnapshotId);
            out.writeInt(resourceIndex);
            out.writeBoolean(markerResource != null);
            if (markerResource != null) writeResourceIdentity(out, markerResource);
            out.writeBoolean(requestedRecipe != null);
            if (requestedRecipe != null) {
                writeString(out, requestedRecipe.adapterId());
                writeString(out, requestedRecipe.recipeId());
                out.writeInt(requestedRecipe.width());
                out.writeInt(requestedRecipe.height());
                out.writeInt(requestedRecipe.bindings().size());
                for (var binding : requestedRecipe.bindings()) {
                    out.writeInt(binding.slot());
                    writeResourceIdentity(out, binding.resource());
                }
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (IOException | NoSuchAlgorithmException exception) {
            throw new IllegalStateException("Cannot fingerprint miniature command", exception);
        }
    }

    private static void writeResourceIdentity(DataOutputStream out, ResourceKey resource) throws IOException {
        out.writeInt(resource.kind().ordinal());
        writeString(out, resource.registryId());
        RecipeReference.writeCanonical(resource.components(), out);
    }

    private static void writeString(DataOutputStream out, String text) throws IOException {
        byte[] encoded = text.getBytes(StandardCharsets.UTF_8);
        out.writeInt(encoded.length);
        out.write(encoded);
    }

    private static void writeUuid(DataOutputStream out, UUID value) throws IOException {
        out.writeLong(value.getMostSignificantBits());
        out.writeLong(value.getLeastSignificantBits());
    }

    public enum Action {
        INSERT_MACHINE,
        EXTRACT_MACHINE,
        SET_MARKER_FROM_CARRIED,
        SET_MARKER_QUANTITY,
        SET_DIRECTION,
        CREATE_ARROW,
        ROTATE_ARROW,
        MARKER_TO_ARROW,
        CLEAR_CONFIGURATION,
        INSERT_INPUT,
        EXTRACT_INPUT,
        EXTRACT_OUTPUT,
        CYCLE_RECIPE,
        COMPILE,
        TOGGLE_RUN,
        CYCLE_REDSTONE,
        SET_FLUID_MARKER_FROM_CARRIED,
        SET_CHEMICAL_MARKER_FROM_CARRIED,
        SET_GHOST_MARKER,
        SELECT_RECIPE,
        UPGRADE,
        ROTATE_DIRECTION,
        SET_ARROW_WEIGHT,
        AUTO_BALANCE,
        SET_PRODUCT_DIRECTION,
        SET_INPUT_CAPACITY;

        public boolean isRecipeSelection() { return this == CYCLE_RECIPE || this == SELECT_RECIPE; }

        public boolean requiresSnapshot() {
            return isInventory() || isRecipeSelection() || this == SET_GHOST_MARKER || this == COMPILE || this == AUTO_BALANCE || this == UPGRADE || this == SET_PRODUCT_DIRECTION || this == SET_INPUT_CAPACITY;
        }

        public boolean isContentMarker() {
            return this == SET_FLUID_MARKER_FROM_CARRIED || this == SET_CHEMICAL_MARKER_FROM_CARRIED;
        }

        public boolean isControl() { return this == TOGGLE_RUN || this == CYCLE_REDSTONE; }

        public boolean usesQuantity() {
            return this == INSERT_MACHINE || this == EXTRACT_MACHINE || this == SET_MARKER_QUANTITY || this == SET_ARROW_WEIGHT || this == SET_INPUT_CAPACITY || isInventory();
        }

        public boolean isInventory() {
            return this == INSERT_INPUT || this == EXTRACT_INPUT || this == EXTRACT_OUTPUT;
        }
    }

    private static CompoundTag readIntent(byte[] encoded) throws IOException {
        DataInputStream input = new DataInputStream(new ByteArrayInputStream(encoded));
        CompoundTag intent = NbtIo.read(input, NbtAccounter.create(FactoryStateCodec.MAX_STATE_NBT_HEAP));
        if (input.available() != 0) throw new DecoderException("Trailing miniature command intent data");
        return intent;
    }

    private static <T> T readEnum(FriendlyByteBuf buffer, T[] values) {
        int ordinal = buffer.readUnsignedByte();
        if (ordinal >= values.length) throw new DecoderException("Unknown miniature command enum value");
        return values[ordinal];
    }
}
