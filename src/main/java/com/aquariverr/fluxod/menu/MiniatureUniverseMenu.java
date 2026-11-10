package com.aquariverr.fluxod.menu;

import com.aquariverr.fluxod.block.entity.TileMiniatureUniverse;
import com.aquariverr.fluxod.block.entity.TileQuantumEntanglementController;
import com.aquariverr.fluxod.entanglement.QuantumPluginEndpointKey;
import com.aquariverr.fluxod.miniature.model.ModelValidationException;
import com.aquariverr.fluxod.miniature.model.FactoryLimits;
import com.aquariverr.fluxod.miniature.storage.FactoryEditing;
import com.aquariverr.fluxod.miniature.storage.FactoryState;
import com.aquariverr.fluxod.miniature.recipe.MiniatureRecipeCatalog;
import com.aquariverr.fluxod.miniature.compile.CompilationResult;
import com.aquariverr.fluxod.miniature.compile.CompilationPreview;
import com.aquariverr.fluxod.miniature.recipe.RecipePreview;
import com.aquariverr.fluxod.miniature.model.FactoryCell;
import com.aquariverr.fluxod.miniature.model.GridPosition;
import com.aquariverr.fluxod.miniature.runtime.RuntimePreview;
import com.aquariverr.fluxod.miniature.upgrade.FactoryUpgrading;
import com.aquariverr.fluxod.network.MiniatureFactoryCommandPayload;
import com.aquariverr.fluxod.network.MiniatureFactorySnapshotPayload;
import com.aquariverr.fluxod.network.MiniatureFactorySnapshotPayload.FactorySnapshot;
import com.aquariverr.fluxod.network.MiniatureFactorySnapshotPayload.Result;
import com.aquariverr.fluxod.network.MiniatureFactorySnapshotPayload.ErrorContext;
import com.aquariverr.fluxod.register.RegistryBlocks;
import com.aquariverr.fluxod.register.RegistryMenuTypes;
import com.aquariverr.fluxod.util.LoadedBlockAccess;
import io.netty.buffer.Unpooled;
import io.netty.handler.codec.DecoderException;
import io.netty.handler.codec.EncoderException;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.connection.ConnectionType;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.math.BigInteger;

public final class MiniatureUniverseMenu extends AbstractContainerMenu {

    public static final int RECEIPT_LIMIT = 128;
    private final Player owner;
    private final BlockPos pluginPos;
    private final ResourceKey<Level> pluginDimension;
    private final UUID sessionId;
    private UUID snapshotId = MiniatureFactoryCommandPayload.NO_SNAPSHOT;
    private final TileMiniatureUniverse plugin;
    private final boolean remoteAccess;
    private final ResourceKey<Level> accessDimension;
    private final BlockPos accessControllerPos;
    private final TileQuantumEntanglementController accessController;
    private final Object remotePluginAccessToken;
    private final QuantumPluginEndpointKey endpointKey;
    private final Map<Long, Receipt> receipts = new LinkedHashMap<>();
    private long lastCommandId;
    private long lastAcknowledgedCommandId;
    private long clientRevision;
    private boolean closed;
    private boolean snapshotReady;
    private boolean snapshotTooLarge;
    private FactoryState observedState;
    private FactoryState sentState;
    private long observedRecipeGeneration = -1;
    private long sentRecipeGeneration = -1;
    private CompilationResult observedCompilation;
    private CompilationResult sentCompilation;
    private RuntimePreview sentRuntime;
    private long lastRuntimeSync = Long.MIN_VALUE;
    private FactorySnapshot snapshot = FactorySnapshot.from(FactoryState.fresh());
    private Result lastResult = Result.SUCCESS;
    private ErrorContext lastError = ErrorContext.NONE;

    public MiniatureUniverseMenu(int containerId, Inventory inventory, RegistryFriendlyByteBuf buffer) {
        this(containerId, inventory, ClientContext.read(inventory.player, buffer));
    }

    public MiniatureUniverseMenu(int containerId, Inventory inventory, TileMiniatureUniverse plugin) {
        this(containerId, inventory, plugin, UUID.randomUUID());
    }

    public MiniatureUniverseMenu(int containerId, Inventory inventory, TileMiniatureUniverse plugin, UUID sessionId) {
        this(containerId, inventory, plugin.getBlockPos(), inventory.player.level().dimension(), sessionId, plugin,
                false, plugin.getBlockPos(), null, null);
    }

    public MiniatureUniverseMenu(int containerId, Inventory inventory, TileMiniatureUniverse plugin,
                                 TileQuantumEntanglementController accessController,
                                 QuantumPluginEndpointKey endpointKey) {
        this(containerId, inventory, plugin, UUID.randomUUID(), accessController, endpointKey);
    }

    public MiniatureUniverseMenu(int containerId, Inventory inventory, TileMiniatureUniverse plugin, UUID sessionId,
                                 TileQuantumEntanglementController accessController,
                                 QuantumPluginEndpointKey endpointKey) {
        this(containerId, inventory, plugin.getBlockPos(), plugin.getLevel().dimension(), sessionId, plugin,
                true, accessController.getBlockPos(), accessController, endpointKey);
    }

    private MiniatureUniverseMenu(int containerId, Inventory inventory, ClientContext context) {
        this(containerId, inventory, context.pluginPos(), context.pluginDimension(), context.sessionId(), null,
                context.remoteAccess(), context.accessControllerPos(), null, context.endpointKey());
    }

    private MiniatureUniverseMenu(int containerId, Inventory inventory, BlockPos pluginPos,
                                 ResourceKey<Level> pluginDimension, UUID sessionId, TileMiniatureUniverse plugin,
                                 boolean remoteAccess, BlockPos accessControllerPos,
                                 TileQuantumEntanglementController accessController,
                                 QuantumPluginEndpointKey endpointKey) {
        super(RegistryMenuTypes.MINIATURE_UNIVERSE.get(), containerId);
        this.owner = inventory.player;
        this.pluginPos = pluginPos.immutable();
        this.pluginDimension = pluginDimension;
        this.sessionId = sessionId;
        this.plugin = plugin;
        this.remoteAccess = remoteAccess;
        this.accessDimension = inventory.player.level().dimension();
        this.accessControllerPos = accessControllerPos.immutable();
        this.accessController = accessController;
        this.remotePluginAccessToken = accessController == null ? null : accessController.getRemotePluginAccessToken();
        this.endpointKey = endpointKey;
        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 9; column++) {
                addSlot(new Slot(inventory, column + row * 9 + 9, 8 + column * 18, 151 + row * 18));
            }
        }
        for (int column = 0; column < 9; column++) addSlot(new Slot(inventory, column, 8 + column * 18, 209));
    }

    public UUID sessionId() {
        return sessionId;
    }

    public UUID snapshotId() {
        return snapshotId;
    }

    public boolean snapshotReady() {
        return snapshotReady;
    }

    public long lastAcknowledgedCommandId() {
        return lastAcknowledgedCommandId;
    }

    public BlockPos getPluginPos() {
        return pluginPos;
    }

    public ResourceKey<Level> getPluginDimension() {
        return pluginDimension;
    }

    public boolean isRemoteAccess() {
        return remoteAccess;
    }

    public FactorySnapshot snapshot() {
        return snapshot;
    }

    public Component statusMessage() {
        return Component.translatable(snapshot.status().translationKey());
    }

    public Result lastResult() {
        return lastResult;
    }

    public ErrorContext lastError() {
        return lastError;
    }

    public MiniatureFactorySnapshotPayload handleCommand(ServerPlayer player, MiniatureFactoryCommandPayload command) {
        if (!player.server.isSameThread() || !stillValid(player) || player.containerMenu != this) {
            return response(command.commandId(), 0, Result.INVALID_ACCESS, false);
        }
        if (command.containerId() != containerId || !sessionId.equals(command.sessionId())
                || !pluginPos.equals(command.targetPos())) {
            return response(command.commandId(), revision(), Result.INVALID_SESSION, true);
        }
        Receipt previous = receipts.get(command.commandId());
        if (previous != null) {
            boolean sameCommand = previous.fingerprint().equals(command.fingerprint());
            return response(command.commandId(), previous.revision(),
                    sameCommand ? previous.result() : Result.COMMAND_ID_REUSED, true)
                    .withErrorContext(sameCommand ? previous.error() : ErrorContext.NONE);
        }
        if (command.commandId() <= lastCommandId) {
            return response(command.commandId(), revision(), Result.COMMAND_EXPIRED, true);
        }
        Result result;
        FactoryState before = plugin.factoryState();
        if (before.storage().isEmpty()) result = Result.STATE_UNAVAILABLE;
        else if (command.expectedRevision() != revision()) result = Result.STALE_REVISION;
        else if (before.storage().orElseThrow().batch() != null && !command.action().isInventory() && !command.action().isControl()
                && command.action() != MiniatureFactoryCommandPayload.Action.SET_INPUT_CAPACITY) result = Result.BATCH_LOCKED;
        else if ((command.action().requiresSnapshot() || command.action().isControl())
                && (!sameCommandView(observedState, before) || (!command.action().isControl() && !snapshotId.equals(command.expectedSnapshotId()))
                || command.action().requiresSnapshot()
                && observedRecipeGeneration != recipeCatalog().generation())) result = Result.STALE_REVISION;
        else {
            try {
                if (command.action() == MiniatureFactoryCommandPayload.Action.AUTO_BALANCE) {
                    long generation = recipeCatalog().generation();
                    var balanced = recipeCatalog().balance(before.storage().orElseThrow().layout(), pluginLevel());
                    var storage = balanced.successful() ? before.storage().orElseThrow().withBalancedLayout(balanced.layout()) : before.storage().orElseThrow();
                    FactoryState prepared = storage == before.storage().orElseThrow() ? before : FactoryState.of(storage, pluginLevel().registryAccess());
                    FactorySnapshot preview = serverSnapshot(prepared, balanced.compilation());
                    long updatedRevision = storage.layout().revision();
                    requireEncodable(new MiniatureFactorySnapshotPayload(containerId, sessionId, pluginPos,
                            updatedRevision, command.commandId(), updatedRevision, Result.SUCCESS, preview), player);
                    boolean applied = balanced.successful() ? plugin.applyBalancedState(before, prepared, balanced.compilation(), generation)
                            : plugin.applyCompilation(before, balanced.compilation(), generation);
                    result = applied ? Result.SUCCESS : Result.STALE_REVISION;
                } else if (command.action() == MiniatureFactoryCommandPayload.Action.COMPILE) {
                    long generation = recipeCatalog().generation();
                    CompilationResult compiled = recipeCatalog().compile(before.storage().orElseThrow().layout(), pluginLevel());
                    FactorySnapshot preview = serverSnapshot(before, compiled);
                    requireEncodable(new MiniatureFactorySnapshotPayload(containerId, sessionId, pluginPos,
                            revision(), command.commandId(), revision(), Result.SUCCESS, preview), player);
                    result = plugin.applyCompilation(before, compiled, generation) ? Result.SUCCESS : Result.STALE_REVISION;
                } else {
                    FactoryUpgrading.Prepared upgrade = command.action() == MiniatureFactoryCommandPayload.Action.UPGRADE
                            ? FactoryUpgrading.prepare(before, player.getInventory(), pluginLevel()) : null;
                    FactoryEditing.Prepared prepared = upgrade != null ? new FactoryEditing.Prepared(upgrade.state(), getCarried()) : command.action().isRecipeSelection()
                            ? prepareRecipe(before, command) : command.action() == MiniatureFactoryCommandPayload.Action.SET_PRODUCT_DIRECTION
                            ? prepareProductDirection(before, command) : FactoryEditing.prepare(before, getCarried(), command, pluginLevel().registryAccess());
                    FactorySnapshot preview = serverSnapshot(prepared.state());
                    // Prepare the full client response as well as persistent state before either asset owner changes.
                    long updatedRevision = prepared.state().storage().orElseThrow().layout().revision();
                    requireEncodable(new MiniatureFactorySnapshotPayload(containerId, sessionId, pluginPos,
                            updatedRevision, command.commandId(), updatedRevision, Result.SUCCESS, preview), player);
                    if (upgrade != null && !upgrade.stillMatches(player.getInventory())) result = Result.STALE_REVISION;
                    else if (!plugin.applyPreparedState(before, prepared.state())) result = Result.STALE_REVISION;
                    else {
                        if (upgrade != null) upgrade.commitMaterials(player.getInventory());
                        setCarried(prepared.carried());
                        if (prepared.state() != before) snapshotId = UUID.randomUUID();
                        observedState = prepared.state();
                        observedRecipeGeneration = recipeCatalog().generation();
                        observedCompilation = plugin.compilationFor(prepared.state());
                        snapshot = preview;
                        snapshotTooLarge = false;
                        result = Result.SUCCESS;
                    }
                }
            } catch (FactoryEditing.Rejected exception) {
                result = exception.result();
            } catch (ModelValidationException exception) {
                result = switch (exception.diagnostic().code()) {
                    case INVALID_POSITION -> Result.INVALID_POSITION;
                    case INVALID_MACHINE, INVALID_MACHINE_COUNT -> Result.INVALID_MACHINE;
                    case INVALID_QUANTITY, QUANTITY_LIMIT_EXCEEDED -> Result.INVALID_QUANTITY;
                    case INVALID_REVISION -> Result.REVISION_EXHAUSTED;
                    case INVALID_COMPONENTS, INVALID_RESOURCE_ID, COMPONENT_LIMIT_EXCEEDED -> Result.INVALID_COMPONENTS;
                    default -> Result.INVALID_COMMAND;
                };
            } catch (IllegalArgumentException | EncoderException exception) {
                result = Result.STATE_TOO_LARGE;
            }
        }
        lastCommandId = command.commandId();
        long acknowledgedRevision = revision();
        ErrorContext error = errorContext(command, result);
        receipts.put(command.commandId(), new Receipt(command.fingerprint(), result, acknowledgedRevision, error));
        if (receipts.size() > RECEIPT_LIMIT) receipts.remove(receipts.keySet().iterator().next());
        return response(command.commandId(), acknowledgedRevision, result, true).withErrorContext(error);
    }

    private ErrorContext errorContext(MiniatureFactoryCommandPayload command, Result result) {
        if (result == Result.SUCCESS || result == Result.STATE_UNAVAILABLE) return ErrorContext.NONE;
        if (command.action() == MiniatureFactoryCommandPayload.Action.SET_INPUT_CAPACITY) return ErrorContext.NONE;
        if (command.action().isInventory() || command.action() == MiniatureFactoryCommandPayload.Action.COMPILE
                || command.action() == MiniatureFactoryCommandPayload.Action.AUTO_BALANCE
                || command.action() == MiniatureFactoryCommandPayload.Action.UPGRADE || command.action().isControl()) {
            return result == Result.INVALID_QUANTITY
                    ? new ErrorContext(-1, -1, BigInteger.valueOf(Integer.MAX_VALUE), command.quantity()) : ErrorContext.NONE;
        }
        BigInteger expected = null;
        BigInteger actual = null;
        if (result == Result.STALE_REVISION) {
            expected = BigInteger.valueOf(command.expectedRevision());
            actual = BigInteger.valueOf(revision());
        } else if (result == Result.INVALID_QUANTITY) {
            expected = BigInteger.valueOf(64);
            actual = command.quantity();
        }
        return new ErrorContext(command.x(), command.y(), expected, actual);
    }

    private MiniatureFactorySnapshotPayload response(long id, long acknowledgedRevision, Result result, boolean accessible) {
        if (accessible) refreshSnapshot();
        return new MiniatureFactorySnapshotPayload(containerId, sessionId, pluginPos,
                accessible ? revision() : 0, id, acknowledgedRevision,
                accessible && id == 0 && snapshotTooLarge && result == Result.SUCCESS ? Result.STATE_TOO_LARGE : result,
                accessible ? snapshot : FactorySnapshot.unavailable(FactoryState.Status.INVALID_STATE))
                .withSnapshotId(accessible ? snapshotId : MiniatureFactoryCommandPayload.NO_SNAPSHOT);
    }

    public long revision() {
        return plugin == null ? clientRevision
                : plugin.factoryState().storage().map(storage -> storage.layout().revision()).orElse(0L);
    }

    private void refreshSnapshot() {
        FactoryState current = plugin.factoryState();
        long generation = recipeCatalog().generation();
        CompilationResult compilation = plugin.compilationFor(current);
        RuntimePreview runtime = plugin.runtimePreview();
        boolean capacityLimitChanged = !snapshot.inputCapacityLimit().equals(com.aquariverr.fluxod.Config.miniatureInputCapacity);
        if (!capacityLimitChanged && current == observedState && generation == observedRecipeGeneration && compilation == observedCompilation
                && runtime.equals(snapshot.runtime())) return;
        boolean newView = capacityLimitChanged || !sameCommandView(observedState, current) || generation != observedRecipeGeneration || compilation != observedCompilation;
        try {
            FactorySnapshot preview = newView ? serverSnapshot(current) : snapshot.withRuntime(runtime);
            requireEncodable(new MiniatureFactorySnapshotPayload(containerId, sessionId, pluginPos, revision(),
                    Long.MAX_VALUE, revision(), Result.SUCCESS, preview), (ServerPlayer) owner);
            snapshot = preview;
            snapshotTooLarge = false;
        } catch (IllegalArgumentException | EncoderException exception) {
            snapshot = FactorySnapshot.unavailable(FactoryState.Status.INVALID_STATE);
            snapshotTooLarge = true;
        }
        observedState = current;
        observedRecipeGeneration = generation;
        observedCompilation = compilation;
        if (newView) snapshotId = UUID.randomUUID();
    }

    private static boolean sameCommandView(FactoryState first, FactoryState second) {
        if (first == second) return true;
        if (first == null || second == null || first.storage().isEmpty() || second.storage().isEmpty()) return false;
        var a = first.storage().orElseThrow();
        var b = second.storage().orElseThrow();
        return a.layout().equals(b.layout()) && a.inputs().equals(b.inputs()) && a.outputs().equals(b.outputs())
                && a.configuredInputCapacity().equals(b.configuredInputCapacity())
                && a.inputCapacities().equals(b.inputCapacities())
                && a.inputResources().equals(b.inputResources())
                && a.enabled() == b.enabled() && a.redstoneMode() == b.redstoneMode()
                && java.util.Objects.equals(a.batch() == null ? null : a.batch().id(), b.batch() == null ? null : b.batch().id());
    }

    public void applySnapshot(MiniatureFactorySnapshotPayload payload) {
        if (!owner.level().isClientSide || closed || owner.containerMenu != this
                || payload.containerId() != containerId || !sessionId.equals(payload.sessionId())
                || !pluginPos.equals(payload.targetPos())) return;
        if (payload.revision() >= clientRevision) {
            snapshotReady = true;
            snapshot = payload.snapshot();
            clientRevision = payload.revision();
            snapshotId = payload.snapshotId();
        }
        if (payload.acknowledgedCommandId() > 0 && payload.acknowledgedCommandId() >= lastAcknowledgedCommandId) {
            lastAcknowledgedCommandId = payload.acknowledgedCommandId();
            lastResult = payload.result();
            lastError = payload.errorContext();
        }
    }

    @Override
    public void broadcastChanges() {
        super.broadcastChanges();
        if (!(owner instanceof ServerPlayer player) || player.containerMenu != this || !stillValid(player)) return;
        RuntimePreview runtime = plugin.runtimePreview();
        boolean capacityLimitChanged = !snapshot.inputCapacityLimit().equals(com.aquariverr.fluxod.Config.miniatureInputCapacity);
        if (!capacityLimitChanged && sentState == plugin.factoryState() && sentRecipeGeneration == recipeCatalog().generation()
                && sentCompilation == plugin.compilationFor(plugin.factoryState()) && runtime.equals(sentRuntime)) return;
        long now = pluginLevel().getGameTime();
        if (!capacityLimitChanged && sameCommandView(sentState, plugin.factoryState()) && sentRecipeGeneration == recipeCatalog().generation()
                && sentCompilation == plugin.compilationFor(plugin.factoryState()) && sentRuntime != null
                && sentRuntime.status() == runtime.status() && now >= lastRuntimeSync && now - lastRuntimeSync < 5) return;
        MiniatureFactorySnapshotPayload payload = response(0, revision(), Result.SUCCESS, true);
        PacketDistributor.sendToPlayer(player, payload);
        sentState = plugin.factoryState();
        sentRecipeGeneration = recipeCatalog().generation();
        sentCompilation = plugin.compilationFor(plugin.factoryState());
        sentRuntime = runtime;
        lastRuntimeSync = now;
    }

    private ServerLevel pluginLevel() { return (ServerLevel) plugin.getLevel(); }

    private MiniatureRecipeCatalog recipeCatalog() { return MiniatureRecipeCatalog.get(pluginLevel().getServer()); }

    private FactorySnapshot serverSnapshot(FactoryState state) {
        return serverSnapshot(state, plugin.compilationFor(state));
    }

    private FactorySnapshot serverSnapshot(FactoryState state, CompilationResult compilation) {
        FactorySnapshot base = FactorySnapshot.from(state);
        if (state.storage().isEmpty()) return base;
        var layout = state.storage().orElseThrow().layout();
        Map<Integer, RecipePreview> previews = new LinkedHashMap<>(recipeCatalog().previews(layout, pluginLevel()));
        if (compilation != null && compilation.successful()) compilation.factory().recipes().forEach((position, recipe) -> {
            BigInteger count = compilation.factory().operations().get(position);
            previews.put(position.y() * layout.size() + position.x(), new RecipePreview(RecipePreview.Status.COMPILED,
                    1, 0, recipe.reference(), recipe.normalization(), recipe.inputsForOperations(count), recipe.outputsForOperations(count), java.util.List.of()));
        });
        return base.withRecipes(recipeCatalog().generation(), previews).withCompilation(CompilationPreview.from(compilation))
                .withRuntime(plugin.runtimePreview(state)).withUpgrade(FactoryUpgrading.preview(pluginLevel(), layout.size()));
    }

    private FactoryEditing.Prepared prepareProductDirection(FactoryState before, MiniatureFactoryCommandPayload command) {
        var storage = before.storage().orElseThrow();
        var position = new GridPosition(command.x(), command.y());
        if (!storage.layout().contains(position)) throw new FactoryEditing.Rejected(Result.INVALID_POSITION);
        if (!(storage.layout().cellAt(position) instanceof FactoryCell.Machine machine)) throw new FactoryEditing.Rejected(Result.INVALID_CELL);
        var preview = snapshot.recipes().get(position.y() * storage.layout().size() + position.x());
        if (preview == null || preview.reference() == null || command.resourceIndex() >= preview.outputs().size())
            throw new FactoryEditing.Rejected(Result.NO_RECIPE);
        var recipe = recipeCatalog().resolve(machine.stack().variant(), preview.reference(), pluginLevel());
        var updated = storage.withProductDirection(position, recipe, preview.outputs().get(command.resourceIndex()).resource(), command.direction());
        return new FactoryEditing.Prepared(updated == storage ? before : FactoryState.of(updated, pluginLevel().registryAccess()), getCarried());
    }

    private FactoryEditing.Prepared prepareRecipe(FactoryState before, MiniatureFactoryCommandPayload command) {
        var storage = before.storage().orElseThrow();
        GridPosition position = new GridPosition(command.x(), command.y());
        if (!storage.layout().contains(position)) throw new FactoryEditing.Rejected(Result.INVALID_POSITION);
        if (!(storage.layout().cellAt(position) instanceof FactoryCell.Machine machine)) throw new FactoryEditing.Rejected(Result.INVALID_CELL);
        com.aquariverr.fluxod.miniature.model.RecipeReference reference;
        if (command.action() == MiniatureFactoryCommandPayload.Action.SELECT_RECIPE) {
            try {
                for (var binding : command.requestedRecipe().bindings()) FactoryEditing.requireKnownResource(binding.resource(), pluginLevel().registryAccess());
                reference = recipeCatalog().resolve(machine.stack().variant(), command.requestedRecipe(), pluginLevel()).reference();
            } catch (IllegalArgumentException exception) {
                throw new FactoryEditing.Rejected(Result.NO_RECIPE);
            }
        } else {
            if (!getCarried().isEmpty()) throw new FactoryEditing.Rejected(Result.CURSOR_BLOCKED);
            reference = recipeCatalog().cycle(storage.layout(), position, pluginLevel());
        }
        if (reference == null) throw new FactoryEditing.Rejected(Result.NO_RECIPE);
        var updated = storage.withRecipe(position, reference);
        return new FactoryEditing.Prepared(updated == storage ? before : FactoryState.of(updated, pluginLevel().registryAccess()), getCarried());
    }

    @Override
    public boolean stillValid(Player player) {
        if (closed || player != owner || player.isSpectator() || !player.isAlive()) return false;
        if (remoteAccess) return remoteAccessStillValid(player);
        if (!player.level().hasChunkAt(pluginPos)
                || player.distanceToSqr(pluginPos.getX() + 0.5, pluginPos.getY() + 0.5, pluginPos.getZ() + 0.5) > 64) return false;
        if (player.level().isClientSide) return true;
        return plugin != null && !plugin.isRemoved() && plugin.getLevel() == player.level()
                && player.level().getBlockEntity(pluginPos) == plugin
                && player.level().getBlockState(pluginPos).is(RegistryBlocks.MINIATURE_UNIVERSE.get())
                && plugin.canPlayerConfigure(player);
    }

    private boolean remoteAccessStillValid(Player player) {
        if (!accessDimension.equals(player.level().dimension())
                || !player.level().hasChunkAt(accessControllerPos)
                || player.distanceToSqr(accessControllerPos.getCenter()) > 64) return false;
        if (player.level().isClientSide) return player.level().getBlockState(accessControllerPos)
                .is(RegistryBlocks.QUANTUM_BATTERY_ENTANGLEMENT_CONTROLLER.get());
        // Keep both identities bound to the opening session. Neither a replacement block at the
        // same coordinates nor a stale entity from an unloaded level may receive commands.
        var access = LoadedBlockAccess.read(player.level(), accessControllerPos);
        if (accessController == null || accessController.isRemoved()
                || remotePluginAccessToken != accessController.getRemotePluginAccessToken()
                || accessController.getLevel() != player.level() || access == null
                || access.blockEntity() != accessController
                || !access.state().is(RegistryBlocks.QUANTUM_BATTERY_ENTANGLEMENT_CONTROLLER.get())
                || plugin == null || plugin.isRemoved() || !(plugin.getLevel() instanceof ServerLevel sourceLevel)
                || !pluginDimension.equals(sourceLevel.dimension())
                || player.getServer().getLevel(pluginDimension) != sourceLevel) return false;
        var source = LoadedBlockAccess.read(sourceLevel, pluginPos);
        return source != null && source.blockEntity() == plugin
                && source.state().is(RegistryBlocks.MINIATURE_UNIVERSE.get())
                && endpointKey != null && accessController.canProvideRemotePluginAccess(player, plugin, endpointKey);
    }

    @Override
    public void clicked(int slot, int button, ClickType type, Player player) {
        if (stillValid(player) && player.containerMenu == this) super.clicked(slot, button, type, player);
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        if (!stillValid(player) || player.containerMenu != this || index < 0 || index >= slots.size()) return ItemStack.EMPTY;
        Slot slot = slots.get(index);
        if (!slot.hasItem()) return ItemStack.EMPTY;
        ItemStack stack = slot.getItem();
        ItemStack before = stack.copy();
        if (!moveItemStackTo(stack, index < 27 ? 27 : 0, index < 27 ? 36 : 27, false)) return ItemStack.EMPTY;
        if (stack.isEmpty()) slot.setByPlayer(ItemStack.EMPTY);
        else slot.setChanged();
        slot.onTake(player, stack);
        return before;
    }

    @Override
    public void removed(Player player) {
        if (closed || player != owner) return;
        // A recipe browser can replace AbstractContainerScreen while this container remains open.
        // Native client-side close changes containerMenu first; temporary screen removal must keep
        // accepting the active session's server acknowledgements when the editor is restored.
        if (player.level().isClientSide && player.containerMenu == this) return;
        closed = true;
        receipts.clear();
        super.removed(player);
    }

    public static void writeOpeningData(RegistryFriendlyByteBuf buffer, BlockPos pos, UUID sessionId) {
        // Opening data is intentionally small; the bounded state snapshot follows in its own packet.
        buffer.writeBlockPos(pos);
        buffer.writeUUID(sessionId);
    }

    public static void writeRemoteOpeningData(RegistryFriendlyByteBuf buffer, TileMiniatureUniverse plugin,
                                              UUID sessionId, TileQuantumEntanglementController accessController,
                                              QuantumPluginEndpointKey endpointKey) {
        writeOpeningData(buffer, plugin.getBlockPos(), sessionId);
        buffer.writeBoolean(true);
        buffer.writeResourceLocation(plugin.getLevel().dimension().location());
        buffer.writeBlockPos(accessController.getBlockPos());
        endpointKey.write(buffer);
    }

    private static void requireEncodable(MiniatureFactorySnapshotPayload payload, ServerPlayer player) {
        RegistryFriendlyByteBuf buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), player.registryAccess(), ConnectionType.NEOFORGE);
        try {
            // Reserve the largest acknowledgement so any later rejection can return the same complete preview.
            MiniatureFactorySnapshotPayload.STREAM_CODEC.encode(buffer, new MiniatureFactorySnapshotPayload(
                    payload.containerId(), payload.sessionId(), payload.targetPos(), Long.MAX_VALUE,
                    Long.MAX_VALUE, Long.MAX_VALUE, payload.result(), payload.snapshot(),
                    new ErrorContext(10, 10, FactoryLimits.MAX_ENCODED_QUANTITY, FactoryLimits.MAX_ENCODED_QUANTITY),
                    payload.snapshotId()));
        } finally {
            buffer.release();
        }
    }

    private record ClientContext(BlockPos pluginPos, ResourceKey<Level> pluginDimension, UUID sessionId,
                                 boolean remoteAccess, BlockPos accessControllerPos,
                                 QuantumPluginEndpointKey endpointKey) {
        private static ClientContext read(Player player, RegistryFriendlyByteBuf buffer) {
            BlockPos pluginPos = buffer.readBlockPos();
            UUID sessionId = buffer.readUUID();
            // Preserve the existing local opening format; only remote menus append a context.
            boolean remoteAccess = buffer.isReadable();
            if (remoteAccess && !buffer.readBoolean()) throw new DecoderException("Invalid miniature menu remote context");
            ResourceKey<Level> pluginDimension = remoteAccess
                    ? ResourceKey.create(Registries.DIMENSION, buffer.readResourceLocation()) : player.level().dimension();
            BlockPos accessPos = remoteAccess ? buffer.readBlockPos() : pluginPos;
            QuantumPluginEndpointKey endpointKey = remoteAccess ? QuantumPluginEndpointKey.read(buffer) : null;
            if (buffer.isReadable()) throw new DecoderException("Unexpected miniature menu opening data");
            return new ClientContext(pluginPos, pluginDimension, sessionId, remoteAccess, accessPos, endpointKey);
        }
    }

    private record Receipt(String fingerprint, Result result, long revision, ErrorContext error) {}
}
