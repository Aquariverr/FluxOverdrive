package com.aquariverr.fluxod.network;

import com.aquariverr.fluxod.FluxOverdrive;
import com.aquariverr.fluxod.miniature.model.FactoryCell;
import com.aquariverr.fluxod.miniature.model.FactoryLayout;
import com.aquariverr.fluxod.miniature.model.FactoryLimits;
import com.aquariverr.fluxod.miniature.model.GridDirection;
import com.aquariverr.fluxod.miniature.model.QuantityCodec;
import com.aquariverr.fluxod.miniature.model.ResourceAmount;
import com.aquariverr.fluxod.miniature.model.ResourceKey;
import com.aquariverr.fluxod.miniature.model.ResourceKind;
import com.aquariverr.fluxod.miniature.model.RecipeReference;
import com.aquariverr.fluxod.miniature.recipe.RecipePreview;
import com.aquariverr.fluxod.miniature.compile.CompilationPreview;
import com.aquariverr.fluxod.miniature.model.FactoryDiagnostic;
import com.aquariverr.fluxod.miniature.model.GridPosition;
import com.aquariverr.fluxod.miniature.storage.FactoryState;
import com.aquariverr.fluxod.miniature.storage.FactoryStateCodec;
import com.aquariverr.fluxod.miniature.runtime.RuntimePreview;
import com.aquariverr.fluxod.miniature.upgrade.UpgradePreview;
import com.aquariverr.fluxod.miniature.runtime.FactoryRunner;
import com.aquariverr.fluxod.miniature.runtime.RedstoneMode;
import io.netty.buffer.ByteBufOutputStream;
import io.netty.buffer.Unpooled;
import io.netty.handler.codec.DecoderException;
import io.netty.handler.codec.EncoderException;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.Optional;

public record MiniatureFactorySnapshotPayload(int containerId, UUID sessionId, BlockPos targetPos,
                                              long revision, long acknowledgedCommandId, long acknowledgedRevision,
                                              Result result, FactorySnapshot snapshot, ErrorContext errorContext,
                                              UUID snapshotId)
        implements CustomPacketPayload {

    public static final int MAX_PAYLOAD_BYTES = FactoryLimits.MAX_MESSAGE_BYTES;
    private static final int MAX_BODY_BYTES = MAX_PAYLOAD_BYTES - 128;
    private static final int MAX_RESOURCES = FactoryLimits.MAX_GRID_SIZE * FactoryLimits.MAX_GRID_SIZE
            * (1 + 18 + FactoryLimits.MAX_RECIPE_RESOURCES * 3) + FactoryLimits.MAX_BOUNDARY_RESOURCES * 6 + 1;
    private static final int MAX_RESOURCE_ID_LENGTH = 32_767;

    public static final Type<MiniatureFactorySnapshotPayload> TYPE =
            new Type<>(FluxOverdrive.location("miniature_factory_snapshot"));
    public static final StreamCodec<RegistryFriendlyByteBuf, MiniatureFactorySnapshotPayload> STREAM_CODEC =
            new StreamCodec<>() {
                @Override
                public MiniatureFactorySnapshotPayload decode(RegistryFriendlyByteBuf buffer) {
                    if (buffer.readableBytes() > MAX_PAYLOAD_BYTES) {
                        throw new DecoderException("Oversized miniature snapshot payload");
                    }
                    int length = buffer.readVarInt();
                    if (length < 1 || length > MAX_BODY_BYTES || length != buffer.readableBytes()) {
                        throw new DecoderException("Invalid miniature snapshot frame length");
                    }
                    FriendlyByteBuf body = new FriendlyByteBuf(buffer.readSlice(length));
                    try {
                        int containerId = body.readVarInt();
                        UUID sessionId = body.readUUID();
                        BlockPos targetPos = body.readBlockPos();
                        UUID snapshotId = body.readUUID();
                        long revision = body.readVarLong();
                        long commandId = body.readVarLong();
                        long acknowledgedRevision = body.readVarLong();
                        Result result = readEnum(body, Result.values());
                        ErrorContext errorContext = readErrorContext(body);
                        FactoryState.Status status = readEnum(body, FactoryState.Status.values());
                        int size = body.readUnsignedByte();
                        int resourceCount = readCount(body, MAX_RESOURCES);
                        List<ResourceKey> resources = new ArrayList<>(resourceCount);
                        HashSet<ResourceKey> uniqueResources = new HashSet<>();
                        NbtAccounter budget = NbtAccounter.create(FactoryStateCodec.MAX_STATE_NBT_HEAP);
                        for (int index = 0; index < resourceCount; index++) {
                            ResourceKey resource = readResource(body, budget);
                            if (!uniqueResources.add(resource)) throw new DecoderException("Duplicate preview resource");
                            resources.add(resource);
                        }
                        int cellCount = readCount(body, FactoryLimits.MAX_GRID_SIZE * FactoryLimits.MAX_GRID_SIZE);
                        List<CellPreview> cells = new ArrayList<>(cellCount);
                        for (int index = 0; index < cellCount; index++) {
                            CellPreview.Type type = readEnum(body, CellPreview.Type.values());
                            cells.add(switch (type) {
                                case EMPTY -> CellPreview.empty();
                                case MACHINE -> CellPreview.machine(readReference(body, resources), body.readUnsignedByte(),
                                        readEnum(body, GridDirection.values()), readProductRoutes(body, resources));
                                case MARKER -> CellPreview.marker(new ResourceAmount(readReference(body, resources),
                                        readQuantity(body)), readEnum(body, GridDirection.values()));
                                case ARROW -> {
                                    var weights = new java.util.EnumMap<GridDirection, Integer>(GridDirection.class);
                                    for (GridDirection direction : GridDirection.values()) {
                                        int weight = body.readVarInt();
                                        if (weight != 0) weights.put(direction, weight);
                                    }
                                    yield CellPreview.arrow(weights);
                                }
                            });
                        }
                        List<ResourceAmount> inputs = readInventory(body, resources);
                        List<ResourceAmount> outputs = readInventory(body, resources);
                        long generation = body.readVarLong();
                        int recipeCount = readCount(body, FactoryLimits.MAX_GRID_SIZE * FactoryLimits.MAX_GRID_SIZE);
                        Map<Integer, RecipePreview> recipes = new LinkedHashMap<>();
                        for (int index = 0; index < recipeCount; index++) {
                            int cell = body.readVarInt();
                            if (recipes.put(cell, readRecipe(body, resources)) != null) throw new DecoderException("Duplicate recipe preview cell");
                        }
                        CompilationPreview compilation = readCompilation(body, resources);
                        RuntimePreview runtime = body.isReadable() ? new RuntimePreview(body.readBoolean(), readEnum(body, RedstoneMode.values()),
                                readEnum(body, FactoryRunner.Status.values()), body.readVarInt(), body.readVarInt(), body.readVarInt(), readNonnegativeQuantity(body), body.readVarInt(), readNonnegativeQuantity(body))
                                : RuntimePreview.NONE;
                        UpgradePreview upgrade = body.isReadable() ? new UpgradePreview(readEnum(body, UpgradePreview.Status.values()), readInventory(body, resources)) : UpgradePreview.NONE;
                        BigInteger inputCapacity = readQuantity(body);
                        BigInteger inputCapacityLimit = readQuantity(body);
                        List<ResourceAmount> inputCapacities = readInventory(body, resources);
                        if (body.isReadable()) throw new DecoderException("Trailing miniature snapshot data");
                        return new MiniatureFactorySnapshotPayload(containerId, sessionId, targetPos, revision,
                                commandId, acknowledgedRevision, result,
                                new FactorySnapshot(size, cells, inputs, outputs, status, generation, recipes, compilation, runtime, upgrade,
                                        inputCapacity, inputCapacityLimit, inputCapacities), errorContext, snapshotId);
                    } catch (IOException | RuntimeException exception) {
                        throw new DecoderException("Invalid miniature snapshot", exception);
                    }
                }

                @Override
                public void encode(RegistryFriendlyByteBuf buffer, MiniatureFactorySnapshotPayload payload) {
                    FriendlyByteBuf body = new FriendlyByteBuf(Unpooled.buffer(256, MAX_BODY_BYTES));
                    try {
                        body.writeVarInt(payload.containerId());
                        body.writeUUID(payload.sessionId());
                        body.writeBlockPos(payload.targetPos());
                        body.writeUUID(payload.snapshotId());
                        body.writeVarLong(payload.revision());
                        body.writeVarLong(payload.acknowledgedCommandId());
                        body.writeVarLong(payload.acknowledgedRevision());
                        body.writeByte(payload.result().ordinal());
                        writeErrorContext(body, payload.errorContext());
                        FactorySnapshot snapshot = payload.snapshot();
                        body.writeByte(snapshot.status().ordinal());
                        body.writeByte(snapshot.size());
                        Map<ResourceKey, Integer> resources = resourceTable(snapshot);
                        body.writeVarInt(resources.size());
                        NbtAccounter budget = NbtAccounter.create(FactoryStateCodec.MAX_STATE_NBT_HEAP);
                        for (ResourceKey resource : resources.keySet()) writeResource(body, resource, budget);
                        body.writeVarInt(snapshot.cells().size());
                        for (CellPreview cell : snapshot.cells()) {
                            body.writeByte(cell.type().ordinal());
                            switch (cell.type()) {
                                case EMPTY -> { }
                                case MACHINE -> {
                                    body.writeVarInt(resources.get(cell.resource()));
                                    body.writeByte(cell.machineCount());
                                    body.writeByte(cell.direction().ordinal());
                                    body.writeVarInt(cell.outputDirections().size());
                                    cell.outputDirections().forEach((key, direction) -> {
                                        body.writeVarInt(resources.get(key)); body.writeByte(direction.ordinal());
                                    });
                                }
                                case MARKER -> {
                                    body.writeVarInt(resources.get(cell.resource()));
                                    body.writeByteArray(QuantityCodec.encode(cell.quantity()));
                                    body.writeByte(cell.direction().ordinal());
                                }
                                case ARROW -> {
                                    for (GridDirection direction : GridDirection.values()) body.writeVarInt(cell.weights().getOrDefault(direction, 0));
                                }
                            }
                        }
                        writeInventory(body, snapshot.inputs(), resources);
                        writeInventory(body, snapshot.outputs(), resources);
                        body.writeVarLong(snapshot.recipeGeneration());
                        body.writeVarInt(snapshot.recipes().size());
                        for (var entry : snapshot.recipes().entrySet()) {
                            body.writeVarInt(entry.getKey());
                            writeRecipe(body, entry.getValue(), resources);
                        }
                        writeCompilation(body, snapshot.compilation(), resources);
                        RuntimePreview runtime = snapshot.runtime();
                        body.writeBoolean(runtime.enabled());
                        body.writeByte(runtime.redstoneMode().ordinal());
                        body.writeByte(runtime.status().ordinal());
                        body.writeVarInt(runtime.environmentParallelism());
                        body.writeVarInt(runtime.batchParallelism());
                        body.writeVarInt(runtime.paidTicks());
                        body.writeByteArray(QuantityCodec.encode(runtime.energyPerTick()));
                        body.writeVarInt(runtime.requiredTicks());
                        body.writeByteArray(QuantityCodec.encode(runtime.totalEnergy()));
                        body.writeByte(snapshot.upgrade().status().ordinal());
                        writeInventory(body, snapshot.upgrade().materials(), resources);
                        body.writeByteArray(QuantityCodec.encode(snapshot.inputCapacity()));
                        body.writeByteArray(QuantityCodec.encode(snapshot.inputCapacityLimit()));
                        writeInventory(body, snapshot.inputCapacities(), resources);
                        buffer.writeVarInt(body.readableBytes());
                        buffer.writeBytes(body);
                    } catch (IOException | RuntimeException exception) {
                        throw new EncoderException("Invalid miniature snapshot", exception);
                    } finally {
                        body.release();
                    }
                }
            };

    public MiniatureFactorySnapshotPayload {
        if (containerId < 0 || revision < 0 || acknowledgedCommandId < 0 || acknowledgedRevision < 0) {
            throw new IllegalArgumentException("Invalid miniature snapshot identity or revision");
        }
        Objects.requireNonNull(sessionId, "sessionId");
        targetPos = Objects.requireNonNull(targetPos, "targetPos").immutable();
        Objects.requireNonNull(result, "result");
        Objects.requireNonNull(snapshot, "snapshot");
        Objects.requireNonNull(errorContext, "errorContext");
        Objects.requireNonNull(snapshotId, "snapshotId");
    }

    public MiniatureFactorySnapshotPayload(int containerId, UUID sessionId, BlockPos targetPos,
                                           long revision, long acknowledgedCommandId, long acknowledgedRevision,
                                           Result result, FactorySnapshot snapshot) {
        this(containerId, sessionId, targetPos, revision, acknowledgedCommandId, acknowledgedRevision,
                result, snapshot, ErrorContext.NONE, MiniatureFactoryCommandPayload.NO_SNAPSHOT);
    }

    public MiniatureFactorySnapshotPayload(int containerId, UUID sessionId, BlockPos targetPos,
                                           long revision, long acknowledgedCommandId, long acknowledgedRevision,
                                           Result result, FactorySnapshot snapshot, ErrorContext errorContext) {
        this(containerId, sessionId, targetPos, revision, acknowledgedCommandId, acknowledgedRevision,
                result, snapshot, errorContext, MiniatureFactoryCommandPayload.NO_SNAPSHOT);
    }

    public MiniatureFactorySnapshotPayload withErrorContext(ErrorContext context) {
        return new MiniatureFactorySnapshotPayload(containerId, sessionId, targetPos, revision,
                acknowledgedCommandId, acknowledgedRevision, result, snapshot, context, snapshotId);
    }

    public MiniatureFactorySnapshotPayload withSnapshotId(UUID id) {
        return new MiniatureFactorySnapshotPayload(containerId, sessionId, targetPos, revision,
                acknowledgedCommandId, acknowledgedRevision, result, snapshot, errorContext, id);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public enum Result {
        SUCCESS,
        STALE_REVISION,
        INVALID_ACCESS,
        INVALID_SESSION,
        INVALID_COMMAND,
        COMMAND_ID_REUSED,
        COMMAND_EXPIRED,
        INVALID_POSITION,
        INVALID_QUANTITY,
        INVALID_CELL,
        INVALID_MACHINE,
        INVALID_COMPONENTS,
        CURSOR_BLOCKED,
        STATE_UNAVAILABLE,
        STATE_TOO_LARGE,
        REVISION_EXHAUSTED,
        NO_RECIPE,
        BATCH_LOCKED,
        UPGRADE_MAXIMUM,
        UPGRADE_NO_RECIPE,
        UPGRADE_AMBIGUOUS,
        UPGRADE_MISSING_MATERIALS;

        public String translationKey() {
            return "message.flux_overdrive.miniature_universe.command." + name().toLowerCase(java.util.Locale.ROOT);
        }
    }

    public record ErrorContext(int x, int y, BigInteger expected, BigInteger actual) {
        public static final ErrorContext NONE = new ErrorContext(-1, -1, null, null);

        public ErrorContext {
            if ((x == -1) != (y == -1) || x < -1 || y < -1
                    || x >= FactoryLimits.MAX_GRID_SIZE || y >= FactoryLimits.MAX_GRID_SIZE) {
                throw new IllegalArgumentException("Invalid miniature error coordinate");
            }
            if (expected != null) FactoryLimits.requireQuantity(expected);
            if (actual != null) FactoryLimits.requireQuantity(actual);
        }

        public boolean hasPosition() {
            return x >= 0;
        }
    }

    public record FactorySnapshot(int size, List<CellPreview> cells, List<ResourceAmount> inputs,
                                  List<ResourceAmount> outputs, FactoryState.Status status,
                                  long recipeGeneration, Map<Integer, RecipePreview> recipes, CompilationPreview compilation,
                                  RuntimePreview runtime, UpgradePreview upgrade,
                                  BigInteger inputCapacity, BigInteger inputCapacityLimit, List<ResourceAmount> inputCapacities) {
        public FactorySnapshot {
            inputCapacities = List.copyOf(inputCapacities);
            validateInventory(inputCapacities);
            if (size == 0 && !inputCapacities.isEmpty()) throw new IllegalArgumentException("Unavailable state has input settings");
            FactoryLimits.requireInventoryQuantity(FactoryLimits.requirePositiveQuantity(inputCapacity));
            FactoryLimits.requireInventoryQuantity(FactoryLimits.requirePositiveQuantity(inputCapacityLimit));
            Objects.requireNonNull(status, "status");
            Objects.requireNonNull(compilation, "compilation");
            Objects.requireNonNull(runtime, "runtime");
            Objects.requireNonNull(upgrade, "upgrade");
            if (size == 0 && !upgrade.equals(UpgradePreview.NONE)
                    || size == 11 && upgrade.status() == UpgradePreview.Status.AVAILABLE
                    || size != 11 && upgrade.status() == UpgradePreview.Status.MAXIMUM) throw new IllegalArgumentException("Upgrade does not match grid");
            if (size == 0 && !runtime.equals(RuntimePreview.NONE)) throw new IllegalArgumentException("Unavailable state has runtime data");
            cells = List.copyOf(cells);
            inputs = List.copyOf(inputs);
            outputs = List.copyOf(outputs);
            recipes = java.util.Collections.unmodifiableMap(new LinkedHashMap<>(recipes));
            if (recipeGeneration < 0 || recipeGeneration == 0 && !recipes.isEmpty()) throw new IllegalArgumentException("Invalid recipe generation");
            for (int index : recipes.keySet()) {
                if (index < 0 || index >= cells.size() || cells.get(index).type() != CellPreview.Type.MACHINE) {
                    throw new IllegalArgumentException("Recipe preview requires a machine cell");
                }
                Objects.requireNonNull(recipes.get(index));
            }
            if (status == FactoryState.Status.READY) {
                if (!FactoryLayout.VALID_SIZES.contains(size) || cells.size() != size * size) {
                    throw new IllegalArgumentException("Invalid miniature preview grid");
                }
            } else if (size != 0 || !cells.isEmpty() || !inputs.isEmpty() || !outputs.isEmpty()) {
                throw new IllegalArgumentException("Unavailable miniature state must not expose partial assets");
            }
            validateInventory(inputs);
            validateInventory(outputs);
            if (compilation.status() != CompilationPreview.Status.NOT_COMPILED && (size == 0 || recipeGeneration == 0)) {
                throw new IllegalArgumentException("Unavailable state cannot have a compilation");
            }
            if (compilation.status() == CompilationPreview.Status.READY && (compilation.machineCount() != cells.stream().mapToInt(CellPreview::machineCount).sum()
                    || compilation.outputRows().keySet().stream().anyMatch(row -> row >= size))) throw new IllegalArgumentException("Compilation does not match grid");
            if (compilation.diagnostic() != null) compilation.diagnostic().position().ifPresent(position -> {
                if (position.x() > size || position.y() >= size) throw new IllegalArgumentException("Compilation error is outside grid");
            });
        }

        public FactorySnapshot(int size, List<CellPreview> cells, List<ResourceAmount> inputs, List<ResourceAmount> outputs,
                               FactoryState.Status status, long recipeGeneration, Map<Integer, RecipePreview> recipes, CompilationPreview compilation,
                               RuntimePreview runtime, UpgradePreview upgrade) {
            this(size, cells, inputs, outputs, status, recipeGeneration, recipes, compilation, runtime, upgrade,
                    FactoryLimits.MAX_INVENTORY_AMOUNT, FactoryLimits.MAX_INVENTORY_AMOUNT, List.of());
        }

        public FactorySnapshot(int size, List<CellPreview> cells, List<ResourceAmount> inputs, List<ResourceAmount> outputs,
                               FactoryState.Status status, long recipeGeneration, Map<Integer, RecipePreview> recipes, CompilationPreview compilation,
                               RuntimePreview runtime) {
            this(size, cells, inputs, outputs, status, recipeGeneration, recipes, compilation, runtime, UpgradePreview.NONE);
        }

        public FactorySnapshot(int size, List<CellPreview> cells, List<ResourceAmount> inputs, List<ResourceAmount> outputs,
                               FactoryState.Status status, long recipeGeneration, Map<Integer, RecipePreview> recipes, CompilationPreview compilation) {
            this(size, cells, inputs, outputs, status, recipeGeneration, recipes, compilation, RuntimePreview.NONE);
        }

        public FactorySnapshot(int size, List<CellPreview> cells, List<ResourceAmount> inputs, List<ResourceAmount> outputs,
                               FactoryState.Status status, long recipeGeneration, Map<Integer, RecipePreview> recipes) {
            this(size, cells, inputs, outputs, status, recipeGeneration, recipes, CompilationPreview.NONE);
        }

        public FactorySnapshot(int size, List<CellPreview> cells, List<ResourceAmount> inputs,
                               List<ResourceAmount> outputs, FactoryState.Status status) {
            this(size, cells, inputs, outputs, status, 0, Map.of());
        }

        public FactorySnapshot withRecipes(long generation, Map<Integer, RecipePreview> previews) {
            return new FactorySnapshot(size, cells, inputs, outputs, status, generation, previews, compilation, runtime, upgrade, inputCapacity, inputCapacityLimit, inputCapacities);
        }

        public FactorySnapshot withCompilation(CompilationPreview preview) {
            return new FactorySnapshot(size, cells, inputs, outputs, status, recipeGeneration, recipes, preview, runtime, upgrade, inputCapacity, inputCapacityLimit, inputCapacities);
        }

        public FactorySnapshot withRuntime(RuntimePreview preview) {
            return new FactorySnapshot(size, cells, inputs, outputs, status, recipeGeneration, recipes, compilation, preview, upgrade, inputCapacity, inputCapacityLimit, inputCapacities);
        }

        public FactorySnapshot withUpgrade(UpgradePreview preview) {
            return new FactorySnapshot(size, cells, inputs, outputs, status, recipeGeneration, recipes, compilation, runtime, preview, inputCapacity, inputCapacityLimit, inputCapacities);
        }

        public FactorySnapshot withInputCapacity(BigInteger configured, BigInteger serverLimit) {
            return new FactorySnapshot(size, cells, inputs, outputs, status, recipeGeneration, recipes, compilation, runtime, upgrade, configured, serverLimit, inputCapacities);
        }

        public FactorySnapshot withInputCapacities(Map<ResourceKey, BigInteger> capacities) {
            return new FactorySnapshot(size, cells, inputs, outputs, status, recipeGeneration, recipes, compilation, runtime, upgrade,
                    inputCapacity, inputCapacityLimit, previewInventory(capacities));
        }

        public BigInteger configuredInputCapacity(ResourceKey resource) {
            return inputCapacities.stream().filter(value -> value.resource().equals(resource)).map(ResourceAmount::amount)
                    .findFirst().orElse(inputCapacity);
        }

        public BigInteger effectiveInputCapacity(ResourceKey resource) {
            return configuredInputCapacity(resource).min(inputCapacityLimit);
        }

        public List<ResourceKey> inputResources() {
            var resources = new java.util.LinkedHashSet<ResourceKey>();
            inputs.forEach(value -> resources.add(value.resource()));
            inputCapacities.forEach(value -> resources.add(value.resource()));
            return List.copyOf(resources);
        }

        public BigInteger effectiveInputCapacity() {
            return inputCapacity.min(inputCapacityLimit);
        }

        public static FactorySnapshot from(FactoryState state) {
            Objects.requireNonNull(state, "state");
            if (state.storage().isEmpty()) return unavailable(state.status());
            var storage = state.storage().orElseThrow();
            List<CellPreview> cells = storage.layout().cells().stream().map(cell -> switch (cell) {
                case FactoryCell.Empty ignored -> CellPreview.empty();
                case FactoryCell.Machine machine -> CellPreview.machine(machine.stack().variant(), machine.stack().count(), machine.direction(), machine.outputDirections());
                case FactoryCell.Marker marker -> CellPreview.marker(marker.resource(), marker.direction());
                case FactoryCell.Arrow arrow -> CellPreview.arrow(arrow.weights());
            }).toList();
            return new FactorySnapshot(storage.layout().size(), cells, previewInventory(storage.inputs().amounts()),
                    previewInventory(storage.outputs().amounts()), state.status()).withRuntime(RuntimePreview.from(storage,
                    storage.enabled() ? FactoryRunner.Status.NO_BATTERY : FactoryRunner.Status.DISABLED, 0))
                    .withInputCapacity(storage.configuredInputCapacity(), com.aquariverr.fluxod.Config.miniatureInputCapacity)
                    .withInputCapacities(storage.inputCapacities());
        }

        public static FactorySnapshot unavailable(FactoryState.Status status) {
            return new FactorySnapshot(0, List.of(), List.of(), List.of(), status);
        }
    }

    public record CellPreview(Type type, ResourceKey resource, int machineCount, BigInteger quantity,
                              GridDirection direction, Map<GridDirection, Integer> weights, Map<ResourceKey, GridDirection> outputDirections) {
        public CellPreview(Type type, ResourceKey resource, int machineCount, BigInteger quantity, GridDirection direction, Map<GridDirection, Integer> weights) {
            this(type, resource, machineCount, quantity, direction, weights, Map.of());
        }
        public CellPreview(Type type, ResourceKey resource, int machineCount, BigInteger quantity, GridDirection direction) {
            this(type, resource, machineCount, quantity, direction, type == Type.ARROW ? Map.of(direction, 1) : Map.of());
        }
        public CellPreview {
            Objects.requireNonNull(type, "type");
            Objects.requireNonNull(direction, "direction");
            outputDirections = Map.copyOf(outputDirections);
            if (outputDirections.size() > FactoryLimits.MAX_RECIPE_RESOURCES || type != Type.MACHINE && !outputDirections.isEmpty())
                throw new IllegalArgumentException("Invalid machine product routing");
            weights = type == Type.ARROW ? new FactoryCell.Arrow(weights).weights() : Map.copyOf(weights);
            if (type != Type.ARROW && !weights.isEmpty()) throw new IllegalArgumentException("Only arrows have branch weights");
            if (type == Type.ARROW) direction = new FactoryCell.Arrow(weights).direction();
            FactoryLimits.requirePositiveQuantity(quantity);
            switch (type) {
                case EMPTY, ARROW -> {
                    if (resource != null || machineCount != 0 || !quantity.equals(BigInteger.ONE)) {
                        throw new IllegalArgumentException("Configuration preview carries unexpected assets");
                    }
                }
                case MACHINE -> {
                    if (resource == null || resource.kind() != ResourceKind.ITEM || machineCount < 1
                            || machineCount > FactoryLimits.MAX_MACHINES_PER_CELL || !quantity.equals(BigInteger.ONE)) {
                        throw new IllegalArgumentException("Invalid machine preview");
                    }
                }
                case MARKER -> {
                    if (resource == null || machineCount != 0) throw new IllegalArgumentException("Invalid marker preview");
                }
            }
            if (type == Type.EMPTY && direction != GridDirection.RIGHT) {
                throw new IllegalArgumentException("Non-directional preview has a direction");
            }
        }

        public static CellPreview empty() {
            return new CellPreview(Type.EMPTY, null, 0, BigInteger.ONE, GridDirection.RIGHT);
        }

        public static CellPreview machine(ResourceKey resource, int count) {
            return machine(resource, count, GridDirection.RIGHT);
        }

        public static CellPreview machine(ResourceKey resource, int count, GridDirection direction) {
            return new CellPreview(Type.MACHINE, resource, count, BigInteger.ONE, direction);
        }

        public static CellPreview machine(ResourceKey resource, int count, GridDirection direction, Map<ResourceKey, GridDirection> routes) {
            return new CellPreview(Type.MACHINE, resource, count, BigInteger.ONE, direction, Map.of(), routes);
        }
        public GridDirection outputDirection(ResourceKey product) { return outputDirections.getOrDefault(product, direction); }

        public static CellPreview marker(ResourceAmount resource, GridDirection direction) {
            return new CellPreview(Type.MARKER, resource.resource(), 0, resource.amount(), direction);
        }

        public static CellPreview arrow(GridDirection direction) {
            return new CellPreview(Type.ARROW, null, 0, BigInteger.ONE, direction);
        }

        public static CellPreview arrow(Map<GridDirection, Integer> weights) {
            return new CellPreview(Type.ARROW, null, 0, BigInteger.ONE, GridDirection.RIGHT, weights);
        }

        public enum Type { EMPTY, MACHINE, MARKER, ARROW }
    }

    private static Map<ResourceKey, Integer> resourceTable(FactorySnapshot snapshot) {
        Map<ResourceKey, Integer> resources = new LinkedHashMap<>();
        for (CellPreview cell : snapshot.cells()) {
            if (cell.resource() != null) resources.computeIfAbsent(cell.resource(), ignored -> resources.size());
            cell.outputDirections().keySet().forEach(resource -> resources.computeIfAbsent(resource, ignored -> resources.size()));
        }
        for (ResourceAmount amount : snapshot.inputs()) resources.computeIfAbsent(amount.resource(), ignored -> resources.size());
        for (ResourceAmount amount : snapshot.inputCapacities()) resources.computeIfAbsent(amount.resource(), ignored -> resources.size());
        for (ResourceAmount amount : snapshot.outputs()) resources.computeIfAbsent(amount.resource(), ignored -> resources.size());
        for (ResourceAmount amount : snapshot.upgrade().materials()) resources.computeIfAbsent(amount.resource(), ignored -> resources.size());
        for (RecipePreview recipe : snapshot.recipes().values()) {
            if (recipe.reference() != null) for (var binding : recipe.reference().bindings()) {
                resources.computeIfAbsent(binding.resource(), ignored -> resources.size());
            }
            for (ResourceAmount amount : recipe.inputs()) resources.computeIfAbsent(amount.resource(), ignored -> resources.size());
            for (ResourceAmount amount : recipe.outputs()) resources.computeIfAbsent(amount.resource(), ignored -> resources.size());
            for (ResourceKey missing : recipe.missing()) resources.computeIfAbsent(missing, ignored -> resources.size());
        }
        CompilationPreview compilation = snapshot.compilation();
        for (List<ResourceAmount> list : List.of(compilation.inputs(), compilation.outputs(), compilation.catalysts())) {
            for (ResourceAmount amount : list) resources.computeIfAbsent(amount.resource(), ignored -> resources.size());
        }
        if (compilation.diagnostic() != null) compilation.diagnostic().resource().ifPresent(key -> resources.computeIfAbsent(key, ignored -> resources.size()));
        if (resources.size() > MAX_RESOURCES) throw new IllegalArgumentException("Too many recipe preview resources");
        return resources;
    }

    private static Map<ResourceKey, GridDirection> readProductRoutes(FriendlyByteBuf buffer, List<ResourceKey> resources) {
        int count = readCount(buffer, FactoryLimits.MAX_RECIPE_RESOURCES);
        var routes = new LinkedHashMap<ResourceKey, GridDirection>();
        for (int i = 0; i < count; i++) if (routes.put(readReference(buffer, resources), readEnum(buffer, GridDirection.values())) != null)
            throw new DecoderException("Duplicate product direction");
        return routes;
    }

    private static CompilationPreview readCompilation(FriendlyByteBuf buffer, List<ResourceKey> resources) {
        CompilationPreview.Status status = readEnum(buffer, CompilationPreview.Status.values());
        if (status == CompilationPreview.Status.NOT_COMPILED) return CompilationPreview.NONE;
        if (status == CompilationPreview.Status.FAILED) {
            FactoryDiagnostic.Code code = readEnum(buffer, FactoryDiagnostic.Code.values());
            GridPosition position = buffer.readBoolean() ? new GridPosition(buffer.readUnsignedByte(), buffer.readUnsignedByte()) : null;
            ResourceKey resource = buffer.readBoolean() ? readReference(buffer, resources) : null;
            BigInteger expected = buffer.readBoolean() ? QuantityCodec.decode(buffer.readByteArray(FactoryLimits.MAX_QUANTITY_BYTES)) : null;
            BigInteger actual = buffer.readBoolean() ? QuantityCodec.decode(buffer.readByteArray(FactoryLimits.MAX_QUANTITY_BYTES)) : null;
            return new CompilationPreview(status, List.of(), List.of(), List.of(), Map.of(), 0,
                    new FactoryDiagnostic(code, Optional.ofNullable(position), Optional.ofNullable(resource), Optional.ofNullable(expected), Optional.ofNullable(actual)));
        }
        int machines = buffer.readVarInt();
        int requiredTicks = buffer.readVarInt();
        BigInteger energyPerTick = readNonnegativeQuantity(buffer), energyPerBatch = readNonnegativeQuantity(buffer);
        List<ResourceAmount> inputs = readInventory(buffer, resources);
        List<ResourceAmount> outputs = readInventory(buffer, resources);
        List<ResourceAmount> catalysts = readInventory(buffer, resources);
        int count = readCount(buffer, FactoryLimits.MAX_GRID_SIZE);
        Map<Integer, List<ResourceAmount>> rows = new LinkedHashMap<>();
        for (int i = 0; i < count; i++) if (rows.put((int) buffer.readUnsignedByte(), readInventory(buffer, resources)) != null) {
            throw new DecoderException("Duplicate compiled output row");
        }
        return new CompilationPreview(status, inputs, outputs, catalysts, rows, machines, null, requiredTicks, energyPerTick, energyPerBatch);
    }

    private static void writeCompilation(FriendlyByteBuf buffer, CompilationPreview preview, Map<ResourceKey, Integer> resources) {
        buffer.writeByte(preview.status().ordinal());
        if (preview.status() == CompilationPreview.Status.NOT_COMPILED) return;
        if (preview.status() == CompilationPreview.Status.FAILED) {
            FactoryDiagnostic diagnostic = preview.diagnostic();
            buffer.writeByte(diagnostic.code().ordinal());
            buffer.writeBoolean(diagnostic.position().isPresent());
            diagnostic.position().ifPresent(position -> { buffer.writeByte(position.x()); buffer.writeByte(position.y()); });
            buffer.writeBoolean(diagnostic.resource().isPresent());
            diagnostic.resource().ifPresent(key -> buffer.writeVarInt(resources.get(key)));
            buffer.writeBoolean(diagnostic.expected().isPresent());
            diagnostic.expected().ifPresent(amount -> buffer.writeByteArray(QuantityCodec.encode(amount)));
            buffer.writeBoolean(diagnostic.actual().isPresent());
            diagnostic.actual().ifPresent(amount -> buffer.writeByteArray(QuantityCodec.encode(amount)));
            return;
        }
        buffer.writeVarInt(preview.machineCount());
        buffer.writeVarInt(preview.requiredTicks());
        buffer.writeByteArray(QuantityCodec.encode(preview.energyPerTick()));
        buffer.writeByteArray(QuantityCodec.encode(preview.energyPerBatch()));
        writeInventory(buffer, preview.inputs(), resources); writeInventory(buffer, preview.outputs(), resources);
        writeInventory(buffer, preview.catalysts(), resources);
        buffer.writeVarInt(preview.outputRows().size());
        preview.outputRows().forEach((row, amounts) -> { buffer.writeByte(row); writeInventory(buffer, amounts, resources); });
    }

    private static RecipePreview readRecipe(FriendlyByteBuf buffer, List<ResourceKey> resources) {
        RecipePreview.Status status = readEnum(buffer, RecipePreview.Status.values());
        int count = buffer.readVarInt();
        int selected = buffer.readVarInt();
        RecipeReference reference = null;
        if (buffer.readBoolean()) {
            String adapter = buffer.readUtf(RecipeReference.MAX_ID_LENGTH);
            String recipe = buffer.readUtf(RecipeReference.MAX_ID_LENGTH);
            int width = buffer.readUnsignedByte();
            int height = buffer.readUnsignedByte();
            int bindingsCount = readCount(buffer, 9);
            List<RecipeReference.Binding> bindings = new ArrayList<>();
            for (int index = 0; index < bindingsCount; index++) {
                bindings.add(new RecipeReference.Binding(buffer.readUnsignedByte(), readReference(buffer, resources)));
            }
            reference = new RecipeReference(adapter, recipe, width, height, bindings);
        }
        BigInteger k = readQuantity(buffer);
        List<ResourceAmount> inputs = readInventory(buffer, resources);
        List<ResourceAmount> outputs = readInventory(buffer, resources);
        int missingCount = readCount(buffer, 9);
        List<ResourceKey> missing = new ArrayList<>();
        for (int index = 0; index < missingCount; index++) missing.add(readReference(buffer, resources));
        return new RecipePreview(status, count, selected, reference, k, inputs, outputs, missing);
    }

    private static void writeRecipe(FriendlyByteBuf buffer, RecipePreview preview, Map<ResourceKey, Integer> resources) {
        buffer.writeByte(preview.status().ordinal());
        buffer.writeVarInt(preview.candidateCount());
        buffer.writeVarInt(preview.selectedIndex());
        RecipeReference reference = preview.reference();
        buffer.writeBoolean(reference != null);
        if (reference != null) {
            buffer.writeUtf(reference.adapterId(), RecipeReference.MAX_ID_LENGTH);
            buffer.writeUtf(reference.recipeId(), RecipeReference.MAX_ID_LENGTH);
            buffer.writeByte(reference.width());
            buffer.writeByte(reference.height());
            buffer.writeVarInt(reference.bindings().size());
            for (var binding : reference.bindings()) {
                buffer.writeByte(binding.slot());
                buffer.writeVarInt(resources.get(binding.resource()));
            }
        }
        buffer.writeByteArray(QuantityCodec.encode(preview.normalization()));
        writeInventory(buffer, preview.inputs(), resources);
        writeInventory(buffer, preview.outputs(), resources);
        buffer.writeVarInt(preview.missing().size());
        for (ResourceKey missing : preview.missing()) buffer.writeVarInt(resources.get(missing));
    }

    private static ErrorContext readErrorContext(FriendlyByteBuf buffer) {
        int flags = buffer.readUnsignedByte();
        if (flags > 7) throw new DecoderException("Unknown miniature error context flags");
        int x = -1;
        int y = -1;
        if ((flags & 1) != 0) {
            x = buffer.readUnsignedByte();
            y = buffer.readUnsignedByte();
        }
        BigInteger expected = (flags & 2) == 0 ? null
                : QuantityCodec.decode(buffer.readByteArray(FactoryLimits.MAX_QUANTITY_BYTES));
        BigInteger actual = (flags & 4) == 0 ? null
                : QuantityCodec.decode(buffer.readByteArray(FactoryLimits.MAX_QUANTITY_BYTES));
        return new ErrorContext(x, y, expected, actual);
    }

    private static void writeErrorContext(FriendlyByteBuf buffer, ErrorContext context) {
        int flags = (context.hasPosition() ? 1 : 0) | (context.expected() != null ? 2 : 0)
                | (context.actual() != null ? 4 : 0);
        buffer.writeByte(flags);
        if (context.hasPosition()) {
            buffer.writeByte(context.x());
            buffer.writeByte(context.y());
        }
        if (context.expected() != null) buffer.writeByteArray(QuantityCodec.encode(context.expected()));
        if (context.actual() != null) buffer.writeByteArray(QuantityCodec.encode(context.actual()));
    }

    private static ResourceKey readResource(FriendlyByteBuf buffer, NbtAccounter budget) throws IOException {
        ResourceKind kind = readEnum(buffer, ResourceKind.values());
        String registryId = buffer.readUtf(MAX_RESOURCE_ID_LENGTH);
        byte[] encoded = buffer.readByteArray(FactoryLimits.MAX_COMPONENT_BYTES);
        return new ResourceKey(kind, registryId, readComponents(encoded, budget));
    }

    private static void writeResource(FriendlyByteBuf buffer, ResourceKey resource, NbtAccounter budget) throws IOException {
        FriendlyByteBuf components = new FriendlyByteBuf(Unpooled.buffer(128, FactoryLimits.MAX_COMPONENT_BYTES));
        try {
            NbtIo.writeUnnamedTag(resource.components(), new ByteBufOutputStream(components));
            byte[] encoded = new byte[components.readableBytes()];
            components.readBytes(encoded);
            readComponents(encoded, budget);
            buffer.writeByte(resource.kind().ordinal());
            buffer.writeUtf(resource.registryId(), MAX_RESOURCE_ID_LENGTH);
            buffer.writeByteArray(encoded);
        } finally {
            components.release();
        }
    }

    private static CompoundTag readComponents(byte[] encoded, NbtAccounter budget) throws IOException {
        DataInputStream input = new DataInputStream(new ByteArrayInputStream(encoded));
        CompoundTag components = NbtIo.read(input, budget);
        if (input.available() != 0) throw new DecoderException("Trailing miniature resource component data");
        return components;
    }

    private static List<ResourceAmount> readInventory(FriendlyByteBuf buffer, List<ResourceKey> resources) {
        int count = readCount(buffer, FactoryLimits.MAX_BOUNDARY_RESOURCES);
        List<ResourceAmount> amounts = new ArrayList<>(count);
        for (int index = 0; index < count; index++) {
            amounts.add(new ResourceAmount(readReference(buffer, resources), readQuantity(buffer)));
        }
        return amounts;
    }

    private static void writeInventory(FriendlyByteBuf buffer, List<ResourceAmount> inventory,
                                       Map<ResourceKey, Integer> resources) {
        buffer.writeVarInt(inventory.size());
        for (ResourceAmount amount : inventory) {
            buffer.writeVarInt(resources.get(amount.resource()));
            buffer.writeByteArray(QuantityCodec.encode(amount.amount()));
        }
    }

    private static void validateInventory(List<ResourceAmount> inventory) {
        if (inventory.size() > FactoryLimits.MAX_BOUNDARY_RESOURCES) throw new IllegalArgumentException("Oversized inventory preview");
        HashSet<ResourceKey> unique = new HashSet<>();
        for (ResourceAmount amount : inventory) {
            FactoryLimits.requireInventoryQuantity(amount.amount());
            if (!unique.add(amount.resource())) throw new IllegalArgumentException("Duplicate inventory preview resource");
        }
    }

    private static List<ResourceAmount> previewInventory(Map<ResourceKey, BigInteger> inventory) {
        return inventory.entrySet().stream().map(entry -> new ResourceAmount(entry.getKey(), entry.getValue())).toList();
    }

    private static BigInteger readQuantity(FriendlyByteBuf buffer) {
        return FactoryLimits.requirePositiveQuantity(QuantityCodec.decode(buffer.readByteArray(FactoryLimits.MAX_QUANTITY_BYTES)));
    }

    private static BigInteger readNonnegativeQuantity(FriendlyByteBuf buffer) {
        return FactoryLimits.requireQuantity(QuantityCodec.decode(buffer.readByteArray(FactoryLimits.MAX_QUANTITY_BYTES)));
    }

    private static ResourceKey readReference(FriendlyByteBuf buffer, List<ResourceKey> resources) {
        int index = buffer.readVarInt();
        if (index < 0 || index >= resources.size()) throw new DecoderException("Invalid miniature resource reference");
        return resources.get(index);
    }

    private static int readCount(FriendlyByteBuf buffer, int maximum) {
        int count = buffer.readVarInt();
        if (count < 0 || count > maximum) throw new DecoderException("Invalid miniature preview collection size");
        return count;
    }

    private static <T> T readEnum(FriendlyByteBuf buffer, T[] values) {
        int ordinal = buffer.readUnsignedByte();
        if (ordinal >= values.length) throw new DecoderException("Unknown miniature preview enum value");
        return values[ordinal];
    }
}
