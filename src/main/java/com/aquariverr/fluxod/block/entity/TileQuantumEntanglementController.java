package com.aquariverr.fluxod.block.entity;

import com.aquariverr.fluxod.entanglement.EntanglementResourceDirection;
import com.aquariverr.fluxod.entanglement.EntanglementResourceKind;
import com.aquariverr.fluxod.entanglement.EntanglementRoutePolicy;
import com.aquariverr.fluxod.entanglement.QuantumPluginAdapters;
import com.aquariverr.fluxod.entanglement.QuantumPluginEndpoint;
import com.aquariverr.fluxod.entanglement.QuantumPluginEndpointKey;
import com.aquariverr.fluxod.multiblock.QuantumEntanglementAssembler;
import com.aquariverr.fluxod.multiblock.QuantumEntanglementStructure;
import com.aquariverr.fluxod.register.RegistryBlockEntityTypes;
import com.aquariverr.fluxod.menu.QuantumEntanglementMenu;
import com.aquariverr.fluxod.util.LoadedBlockAccess;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.capabilities.BlockCapability;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class TileQuantumEntanglementController extends BlockEntity {

    private static final Map<Level, Map<Long, Collection<BlockPos>>> LOADED_CONTROLLERS = new ConcurrentHashMap<>();
    private static final String OWNER_TAG = "Owner";
    private static final String FORMED_TAG = "Formed";
    private static final String TARGET_POS_TAG = "TargetPos";
    private static final String TARGET_DIMENSION_TAG = "TargetDimension";
    private static final String TARGET_ID_TAG = "TargetId";
    private static final String SELECTED_ENDPOINT_TAG = "SelectedEndpoint";
    private static final String AUTOMATIC_INPUT_TAG = "AutomaticInputEnabled";
    private static final String AUTOMATIC_OUTPUT_TAG = "AutomaticOutputEnabled";
    private static final int VALIDATION_INTERVAL = 20;

    private static final Map<Long, Collection<BlockPos>> EMPTY_INDEX = Map.of();

    @Nullable
    private UUID owner;
    @Nullable
    private GlobalPos targetBattery;
    @Nullable
    private UUID targetBatteryId;
    @Nullable
    private QuantumPluginEndpointKey selectedEndpointKey;
    private Object remotePluginAccessToken = new Object();
    private boolean automaticInputEnabled = true;
    private boolean automaticOutputEnabled = true;
    private boolean formed;
    private int redstoneSignal;
    private boolean structureDirty = true;
    private long nextValidationGameTime;

    public TileQuantumEntanglementController(@Nonnull BlockPos pos, @Nonnull BlockState state) {
        super(RegistryBlockEntityTypes.QUANTUM_BATTERY_ENTANGLEMENT_CONTROLLER.get(), pos, state);
    }

    public static Collection<BlockPos> getLoadedControllersNear(Level level, BlockPos pos, int radius) {
        Map<Long, Collection<BlockPos>> chunks = LOADED_CONTROLLERS.getOrDefault(level, EMPTY_INDEX);
        int minChunkX = Math.floorDiv(pos.getX() - radius, 16);
        int maxChunkX = Math.floorDiv(pos.getX() + radius, 16);
        int minChunkZ = Math.floorDiv(pos.getZ() - radius, 16);
        int maxChunkZ = Math.floorDiv(pos.getZ() + radius, 16);
        List<BlockPos> result = new ArrayList<>();
        for (int chunkX = minChunkX; chunkX <= maxChunkX; chunkX++) {
            for (int chunkZ = minChunkZ; chunkZ <= maxChunkZ; chunkZ++) {
                Collection<BlockPos> positions = chunks.get(chunkKey(chunkX, chunkZ));
                if (positions != null) result.addAll(positions);
            }
        }
        return result;
    }

    @Nullable
    public static TileQuantumEntanglementController findUniqueControllerForPort(Level level, BlockPos portPos) {
        TileQuantumEntanglementController found = null;
        for (BlockPos controllerPos : getLoadedControllersNear(level, portPos, 4)) {
            if (!level.isLoaded(controllerPos)) continue;
            BlockEntity current;
            if (level.isClientSide) {
                current = level.getBlockEntity(controllerPos);
            } else {
                var loaded = LoadedBlockAccess.read(level, controllerPos);
                current = loaded == null ? null : loaded.blockEntity();
            }
            if (!(current instanceof TileQuantumEntanglementController controller)
                    || !controller.isFormed() || !controller.isPortMember(portPos)) {
                continue;
            }
            if (found != null && found != controller) return null;
            found = controller;
        }
        return found;
    }

    public static void clearLevelState(Level level) {
        LOADED_CONTROLLERS.remove(level);
    }

    public void serverTick() {
        if (!(level instanceof ServerLevel serverLevel)) return;
        long gameTime = serverLevel.getGameTime();
        if (structureDirty || gameTime >= nextValidationGameTime) {
            validateStructure();
        }
        refreshRedstoneSignal();
    }

    public void validateStructure() {
        if (level == null || level.isClientSide) return;
        registerController();
        QuantumEntanglementStructure.Validation validation = QuantumEntanglementStructure.validate(
                level, worldPosition, getBlockState());
        boolean changed = formed != validation.formed();
        formed = validation.formed();
        structureDirty = false;
        nextValidationGameTime = level.getGameTime() + VALIDATION_INTERVAL;
        if (changed) {
            if (!formed) setRedstoneSignal(0);
            setChanged();
            sendBlockUpdate();
            notifyResourcePorts();
        }
    }

    public void markStructureDirty() {
        structureDirty = true;
        setRedstoneSignal(0);
        if (formed) {
            formed = false;
            setChanged();
            sendBlockUpdate();
            notifyResourcePorts();
        }
    }

    public boolean isFormed() {
        return formed && (level == null || level.isClientSide || !structureDirty);
    }

    public boolean isBound() {
        return targetBattery != null && targetBatteryId != null;
    }

    public boolean isTargetAvailable() {
        TileQuantumBatteryController battery = resolveTargetBattery();
        return battery != null && battery.isOperational();
    }

    public boolean isTargetResolved() {
        return resolveTargetBattery() != null;
    }

    public int getTargetTier() {
        TileQuantumBatteryController battery = resolveTargetBattery();
        return battery == null ? 0 : battery.getStructureTier();
    }

    public int getTargetNetworkColorRgb() {
        TileQuantumBatteryController battery = resolveTargetBattery();
        return battery == null ? 0x43D9FF : battery.getNetworkColorRgb();
    }

    public boolean canPlayerViewTarget(Player player) {
        TileQuantumBatteryController battery = resolveTargetBattery();
        return battery != null && battery.canPlayerManage(player);
    }

    public List<QuantumPluginEndpoint> getPluginEndpoints(Player player) {
        TileQuantumBatteryController battery = resolveTargetBattery();
        if (battery == null || !canProvideRemoteAccess(player, battery)) return List.of();
        validateSelectedEndpoint(battery);
        return battery.getEntanglementEndpoints();
    }

    public int getPluginDirectoryRevision(Player player) {
        TileQuantumBatteryController battery = resolveTargetBattery();
        return battery != null && canProvideRemoteAccess(player, battery)
                ? battery.getPluginDirectoryRevision()
                : 0;
    }

    @Nullable
    public QuantumPluginEndpointKey getSelectedEndpointKey() {
        return selectedEndpointKey;
    }

    /** Session identity for menus that must remain revoked after a route is restored. Never persisted or sent. */
    public Object getRemotePluginAccessToken() {
        return remotePluginAccessToken;
    }

    private void invalidateRemotePluginAccess() {
        remotePluginAccessToken = new Object();
    }

    public boolean isAutomaticInputEnabled() {
        return automaticInputEnabled;
    }

    public boolean isAutomaticOutputEnabled() {
        return automaticOutputEnabled;
    }

    public int getRedstoneSignal() {
        return isFormed() ? redstoneSignal : 0;
    }

    public boolean toggleAutomaticInput(Player player) {
        if (!canConfigureAutomation(player)) return false;
        automaticInputEnabled = !automaticInputEnabled;
        markAutomationChanged();
        return true;
    }

    public boolean toggleAutomaticOutput(Player player) {
        if (!canConfigureAutomation(player)) return false;
        automaticOutputEnabled = !automaticOutputEnabled;
        markAutomationChanged();
        return true;
    }

    public void setOwner(UUID owner) {
        if (this.owner != null || owner == null) return;
        this.owner = owner;
        invalidateRemotePluginAccess();
        setChanged();
    }

    public boolean canPlayerManage(Player player) {
        return owner == null || owner.equals(player.getUUID());
    }

    public void openMenu(Player player) {
        if (!(player instanceof ServerPlayer serverPlayer) || player.isSpectator() || !canPlayerManage(player)) return;
        if (owner == null) setOwner(player.getUUID());
        SimpleMenuProvider provider = new SimpleMenuProvider(
                (containerId, inventory, ignored) -> new QuantumEntanglementMenu(containerId, inventory, this),
                Component.translatable("screen.flux_overdrive.quantum_entanglement.title"));
        serverPlayer.openMenu(provider, buffer -> buffer.writeBlockPos(worldPosition));
    }

    public QuantumEntanglementAssembler.Result assemble(ServerPlayer player) {
        return QuantumEntanglementAssembler.assemble(this, player);
    }

    public boolean openTargetBatteryMenu(ServerPlayer player) {
        TileQuantumBatteryController battery = resolveTargetBattery();
        if (battery == null || !canProvideRemoteAccess(player, battery)) return false;
        battery.openQuantumMenuFromEntanglement(player, this);
        return true;
    }

    public boolean openSelectedPluginMenu(ServerPlayer player) {
        TileQuantumBatteryController battery = resolveTargetBattery();
        if (battery == null || selectedEndpointKey == null
                || !canProvideRemoteAccess(player, battery)) {
            return false;
        }
        QuantumPluginEndpoint endpoint = battery.findEntanglementEndpoint(selectedEndpointKey);
        if (endpoint == null) {
            clearSelectedEndpoint();
            return false;
        }
        if (QuantumPluginAdapters.isConstraint(endpoint)) return false;
        return battery.openPluginMenuFromEntanglement(player, this, selectedEndpointKey);
    }

    public boolean canProvideRemotePluginAccess(Player player, BlockEntity plugin,
                                                QuantumPluginEndpointKey endpointKey) {
        if (!endpointKey.equals(selectedEndpointKey)) return false;
        TileQuantumBatteryController battery = resolveTargetBattery();
        if (battery == null || !canProvideRemoteAccess(player, battery)
                || plugin.isRemoved() || plugin.getLevel() != battery.getLevel()) {
            return false;
        }
        QuantumPluginEndpoint endpoint = battery.findEntanglementEndpoint(endpointKey);
        return endpoint != null && !QuantumPluginAdapters.isConstraint(endpoint)
                && endpoint.remoteMenu()
                && endpoint.absolutePos().equals(plugin.getBlockPos());
    }

    public boolean selectEndpoint(Player player, QuantumPluginEndpointKey key, int expectedRevision) {
        TileQuantumBatteryController battery = resolveTargetBattery();
        if (battery == null || !canProvideRemoteAccess(player, battery)
                || expectedRevision != battery.getPluginDirectoryRevision()
                || battery.findEntanglementEndpoint(key) == null) {
            return false;
        }
        if (key.equals(selectedEndpointKey)) return true;
        selectedEndpointKey = key;
        invalidateRemotePluginAccess();
        setChanged();
        sendBlockUpdate();
        notifyResourcePorts();
        refreshRedstoneSignal();
        return true;
    }

    public boolean canProvideRemoteAccess(Player player, TileQuantumBatteryController battery) {
        return !player.isSpectator()
                && level == player.level()
                && !isRemoved()
                && isFormed()
                && player.distanceToSqr(worldPosition.getX() + 0.5, worldPosition.getY() + 0.5,
                worldPosition.getZ() + 0.5) <= 64.0
                && canPlayerManage(player)
                && battery.canPlayerManage(player)
                && resolveTargetBattery() == battery;
    }

    public boolean bindBattery(Player player, GlobalPos target) {
        if (player.isSpectator() || !canPlayerManage(player)
                || !(player.level() instanceof ServerLevel currentLevel)) return false;
        ServerLevel targetLevel = currentLevel.getServer().getLevel(target.dimension());
        if (targetLevel == null || !targetLevel.isLoaded(target.pos())) return false;
        if (!(targetLevel.getBlockEntity(target.pos()) instanceof TileQuantumBatteryController battery)
                || !battery.canPlayerManage(player)) return false;

        targetBattery = target;
        targetBatteryId = battery.getBatteryId();
        selectedEndpointKey = null;
        if (owner == null) owner = player.getUUID();
        invalidateRemotePluginAccess();
        setChanged();
        sendBlockUpdate();
        notifyResourcePorts();
        setRedstoneSignal(0);
        return true;
    }

    public void unbindBattery(Player player) {
        if (!canPlayerManage(player)) return;
        targetBattery = null;
        targetBatteryId = null;
        selectedEndpointKey = null;
        invalidateRemotePluginAccess();
        setChanged();
        sendBlockUpdate();
        notifyResourcePorts();
        setRedstoneSignal(0);
    }

    public int receiveConstraintEnergy(int maxReceive, boolean simulate) {
        if (maxReceive <= 0 || !canReceiveConstraintEnergy()) return 0;
        TileQuantumBatteryController battery = resolveTargetBattery();
        if (battery == null) return 0;
        return battery.receiveConstraintEnergy(maxReceive, simulate);
    }

    /**
     * A constraint selection is a group route: the legacy energy port feeds every
     * valid constraint interface in the bound battery. With no selection, retain
     * the pre-selection behavior for existing facilities; selecting another plugin
     * disables this route.
     */
    public boolean canReceiveConstraintEnergy() {
        if (!isFormed()) return false;
        TileQuantumBatteryController battery = resolveTargetBattery();
        if (battery == null || !battery.isOperational()) return false;
        if (EntanglementRoutePolicy.resolve(selectedEndpointKey != null,
                QuantumPluginAdapters.isConstraint(selectedEndpointKey))
                != EntanglementRoutePolicy.Mode.CONSTRAINT_GROUP) return false;
        if (selectedEndpointKey == null) return true;
        if (!validateSelectedEndpoint(battery)) return false;
        return QuantumPluginAdapters.isConstraint(selectedEndpointKey);
    }

    @Nullable
    public <T> T resolveSelectedCapability(EntanglementResourceKind resourceKind,
                                            EntanglementResourceDirection direction,
                                            BlockCapability<T, Direction> capability) {
        if (!isFormed() || selectedEndpointKey == null
                || !EntanglementRoutePolicy.automationEnabled(direction,
                automaticInputEnabled, automaticOutputEnabled)
                || EntanglementRoutePolicy.resolve(true,
                QuantumPluginAdapters.isConstraint(selectedEndpointKey))
                != EntanglementRoutePolicy.Mode.SINGLE_PLUGIN) return null;
        TileQuantumBatteryController battery = resolveTargetBattery();
        if (battery == null || !battery.isOperational()) return null;
        if (!validateSelectedEndpoint(battery)) return null;
        return battery.resolveEntanglementCapability(selectedEndpointKey, resourceKind, direction, capability);
    }

    public boolean isPortMember(BlockPos portPos) {
        if (level == null) return false;
        BlockState state;
        if (level.isClientSide) {
            state = level.getBlockState(portPos);
        } else {
            var port = LoadedBlockAccess.read(level, portPos);
            if (port == null) return false;
            state = port.state();
        }
        return (state.is(
                        com.aquariverr.fluxod.register.RegistryBlocks.QUANTUM_ENERGY_CONSTRAINT_ENTANGLEMENT_PORT.get())
                || state.is(
                        com.aquariverr.fluxod.register.RegistryBlocks.QUANTUM_ENTANGLEMENT_INPUT_PORT.get())
                || state.is(
                        com.aquariverr.fluxod.register.RegistryBlocks.QUANTUM_ENTANGLEMENT_OUTPUT_PORT.get())
                || state.is(
                        com.aquariverr.fluxod.register.RegistryBlocks.QUANTUM_ENTANGLEMENT_REDSTONE_PORT.get()))
                && QuantumEntanglementStructure.isCasingPosition(worldPosition, getBlockState(), portPos);
    }

    @Nullable
    private TileQuantumBatteryController resolveTargetBattery() {
        if (!(level instanceof ServerLevel currentLevel) || targetBattery == null || targetBatteryId == null) return null;
        ServerLevel targetLevel = currentLevel.getServer().getLevel(targetBattery.dimension());
        var target = LoadedBlockAccess.read(targetLevel, targetBattery.pos());
        if (target == null || !(target.blockEntity() instanceof TileQuantumBatteryController battery)) {
            return null;
        }
        return targetBatteryId.equals(battery.getBatteryId()) ? battery : null;
    }

    @Override
    public void onLoad() {
        super.onLoad();
        if (level != null) {
            registerController();
            if (!level.isClientSide) structureDirty = true;
        }
    }

    @Override
    public void onChunkUnloaded() {
        setRedstoneSignal(0);
        unregisterController();
        super.onChunkUnloaded();
    }

    @Override
    public void setRemoved() {
        setRedstoneSignal(0);
        unregisterController();
        super.setRemoved();
    }

    @Override
    protected void saveAdditional(@Nonnull CompoundTag tag, net.minecraft.core.HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        if (owner != null) tag.putUUID(OWNER_TAG, owner);
        tag.putBoolean(FORMED_TAG, formed);
        if (targetBattery != null) {
            tag.putLong(TARGET_POS_TAG, targetBattery.pos().asLong());
            tag.putString(TARGET_DIMENSION_TAG, targetBattery.dimension().location().toString());
        }
        if (targetBatteryId != null) tag.putUUID(TARGET_ID_TAG, targetBatteryId);
        if (selectedEndpointKey != null) tag.put(SELECTED_ENDPOINT_TAG, selectedEndpointKey.save());
        tag.putBoolean(AUTOMATIC_INPUT_TAG, automaticInputEnabled);
        tag.putBoolean(AUTOMATIC_OUTPUT_TAG, automaticOutputEnabled);
    }

    @Override
    public void loadAdditional(@Nonnull CompoundTag tag, net.minecraft.core.HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        invalidateRemotePluginAccess();
        owner = tag.hasUUID(OWNER_TAG) ? tag.getUUID(OWNER_TAG) : null;
        formed = tag.getBoolean(FORMED_TAG);
        targetBattery = readGlobalPos(tag);
        targetBatteryId = tag.hasUUID(TARGET_ID_TAG) ? tag.getUUID(TARGET_ID_TAG) : null;
        selectedEndpointKey = tag.contains(SELECTED_ENDPOINT_TAG)
                ? QuantumPluginEndpointKey.load(tag.getCompound(SELECTED_ENDPOINT_TAG))
                : null;
        automaticInputEnabled = !tag.contains(AUTOMATIC_INPUT_TAG)
                || tag.getBoolean(AUTOMATIC_INPUT_TAG);
        automaticOutputEnabled = !tag.contains(AUTOMATIC_OUTPUT_TAG)
                || tag.getBoolean(AUTOMATIC_OUTPUT_TAG);
        redstoneSignal = 0;
        structureDirty = true;
    }

    private static GlobalPos readGlobalPos(CompoundTag tag) {
        if (!tag.contains(TARGET_POS_TAG) || !tag.contains(TARGET_DIMENSION_TAG)) return null;
        long encoded = tag.getLong(TARGET_POS_TAG);
        ResourceLocation dimensionId = ResourceLocation.tryParse(tag.getString(TARGET_DIMENSION_TAG));
        if (dimensionId == null) return null;
        ResourceKey<Level> dimension = ResourceKey.create(Registries.DIMENSION, dimensionId);
        return GlobalPos.of(dimension, BlockPos.of(encoded));
    }

    private void sendBlockUpdate() {
        if (level == null) return;
        BlockState state = getBlockState();
        level.sendBlockUpdated(worldPosition, state, state, Block.UPDATE_CLIENTS);
    }

    private boolean validateSelectedEndpoint(TileQuantumBatteryController battery) {
        if (selectedEndpointKey == null) return false;
        if (battery.findEntanglementEndpoint(selectedEndpointKey) != null) return true;
        if (battery.isOperational()) clearSelectedEndpoint();
        return false;
    }

    private void clearSelectedEndpoint() {
        if (selectedEndpointKey == null) return;
        selectedEndpointKey = null;
        invalidateRemotePluginAccess();
        setChanged();
        sendBlockUpdate();
        notifyResourcePorts();
        setRedstoneSignal(0);
    }

    private boolean canConfigureAutomation(Player player) {
        return player != null && !player.isSpectator() && level != null
                && level == player.level() && !isRemoved()
                && player.distanceToSqr(worldPosition.getX() + 0.5, worldPosition.getY() + 0.5,
                worldPosition.getZ() + 0.5) <= 64.0
                && canPlayerManage(player);
    }

    private void markAutomationChanged() {
        setChanged();
        sendBlockUpdate();
        notifyResourcePorts();
    }

    private void notifyResourcePorts() {
        if (level == null) return;
        Direction facing = getBlockState().getValue(
                com.aquariverr.fluxod.block.QuantumEntanglementControllerBlock.FACING);
        for (com.aquariverr.fluxod.multiblock.QuantumEntanglementTemplate.Cell cell
                : QuantumEntanglementStructure.cells()) {
            if (cell.type() != com.aquariverr.fluxod.multiblock.QuantumEntanglementTemplate.CellType.CASING) {
                continue;
            }
            BlockPos pos = QuantumEntanglementStructure.worldPos(worldPosition, facing, cell.offset());
            if (!level.isLoaded(pos)) continue;
            BlockState state = level.getBlockState(pos);
            if (state.is(com.aquariverr.fluxod.register.RegistryBlocks.QUANTUM_ENTANGLEMENT_INPUT_PORT.get())
                    || state.is(com.aquariverr.fluxod.register.RegistryBlocks.QUANTUM_ENTANGLEMENT_OUTPUT_PORT.get())) {
                level.updateNeighborsAt(pos, state.getBlock());
            }
        }
    }

    private void refreshRedstoneSignal() {
        int nextSignal = 0;
        if (isFormed() && selectedEndpointKey != null) {
            TileQuantumBatteryController battery = resolveTargetBattery();
            if (battery != null && battery.isOperational() && validateSelectedEndpoint(battery)) {
                nextSignal = battery.resolveEntanglementRedstoneSignal(selectedEndpointKey);
            }
        }
        setRedstoneSignal(nextSignal);
    }

    private void setRedstoneSignal(int signal) {
        int clamped = Math.clamp(signal, 0, 15);
        if (redstoneSignal == clamped) return;
        redstoneSignal = clamped;
        notifyRedstonePorts();
    }

    private void notifyRedstonePorts() {
        if (level == null) return;
        Direction facing = getBlockState().getValue(
                com.aquariverr.fluxod.block.QuantumEntanglementControllerBlock.FACING);
        for (com.aquariverr.fluxod.multiblock.QuantumEntanglementTemplate.Cell cell
                : QuantumEntanglementStructure.cells()) {
            if (cell.type() != com.aquariverr.fluxod.multiblock.QuantumEntanglementTemplate.CellType.CASING) {
                continue;
            }
            BlockPos pos = QuantumEntanglementStructure.worldPos(worldPosition, facing, cell.offset());
            if (!level.isLoaded(pos)) continue;
            BlockState state = level.getBlockState(pos);
            if (state.is(com.aquariverr.fluxod.register.RegistryBlocks.QUANTUM_ENTANGLEMENT_REDSTONE_PORT.get())) {
                level.updateNeighborsAt(pos, state.getBlock());
            }
        }
    }

    private void unregisterController() {
        if (level == null) return;
        Map<Long, Collection<BlockPos>> chunks = LOADED_CONTROLLERS.get(level);
        if (chunks == null) return;
        Collection<BlockPos> positions = chunks.get(chunkKey(worldPosition));
        if (positions != null) {
            positions.remove(worldPosition);
            if (positions.isEmpty()) chunks.remove(chunkKey(worldPosition));
        }
        if (chunks.isEmpty()) LOADED_CONTROLLERS.remove(level);
    }

    private void registerController() {
        if (level == null || isRemoved()) return;
        LOADED_CONTROLLERS.computeIfAbsent(level, ignored -> new ConcurrentHashMap<>())
                .computeIfAbsent(chunkKey(worldPosition), ignored -> ConcurrentHashMap.newKeySet())
                .add(worldPosition.immutable());
    }

    private static long chunkKey(BlockPos pos) {
        return ChunkKey.of(Math.floorDiv(pos.getX(), 16), Math.floorDiv(pos.getZ(), 16));
    }

    private static long chunkKey(int chunkX, int chunkZ) {
        return ChunkKey.of(chunkX, chunkZ);
    }

    private static final class ChunkKey {
        private static long of(int chunkX, int chunkZ) {
            return (chunkX & 0xffffffffL) | ((chunkZ & 0xffffffffL) << 32);
        }
    }
}
