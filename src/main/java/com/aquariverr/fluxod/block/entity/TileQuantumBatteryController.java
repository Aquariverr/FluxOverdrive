package com.aquariverr.fluxod.block.entity;

import com.aquariverr.fluxod.Config;
import com.aquariverr.fluxod.FluxOverdrive;
import com.aquariverr.fluxod.entanglement.EntanglementResourceDirection;
import com.aquariverr.fluxod.entanglement.EntanglementResourceKind;
import com.aquariverr.fluxod.entanglement.QuantumPluginAdapter;
import com.aquariverr.fluxod.entanglement.QuantumPluginAdapters;
import com.aquariverr.fluxod.entanglement.QuantumPluginEndpoint;
import com.aquariverr.fluxod.entanglement.QuantumPluginEndpointKey;
import com.aquariverr.fluxod.multiblock.QuantumBatteryStructure;
import com.aquariverr.fluxod.multiblock.QuantumBatteryAssembler;
import com.aquariverr.fluxod.multiblock.QuantumBatteryPlugin;
import com.aquariverr.fluxod.multiblock.QuantumBatteryProcessingPlugin;
import com.aquariverr.fluxod.multiblock.QuantumBatteryQuenchInterceptor;
import com.aquariverr.fluxod.network.QuantumBatteryRenderStatePayload;
import com.aquariverr.fluxod.register.FluxOdDataComponents;
import com.aquariverr.fluxod.register.RegistryBlockEntityTypes;
import com.aquariverr.fluxod.register.RegistryBlocks;
import com.aquariverr.fluxod.util.BigIntegerFormatter;
import com.aquariverr.fluxod.util.LoadedBlockAccess;
import com.aquariverr.fluxod.util.QuantumEnergyDisplay;
import com.aquariverr.fluxod.menu.QuantumBatteryMenu;
import com.aquariverr.fluxod.menu.RemoteFluxMenu;
import com.aquariverr.fluxod.mixin.accessor.TileFluxDeviceAccessor;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.SectionPos;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.capabilities.BlockCapability;
import net.neoforged.neoforge.network.PacketDistributor;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.NotNull;
import sonar.fluxnetworks.api.FluxConstants;
import sonar.fluxnetworks.api.FluxDataComponents;
import sonar.fluxnetworks.api.FluxTranslate;
import sonar.fluxnetworks.api.device.FluxDeviceType;
import sonar.fluxnetworks.api.device.IFluxStorage;
import sonar.fluxnetworks.common.device.TileFluxDevice;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class TileQuantumBatteryController extends TileFluxDevice implements IFluxStorage {

    private static final Map<Level, Map<Long, Set<BlockPos>>> LOADED_CONTROLLERS =
            new ConcurrentHashMap<>();
    private static final Map<Long, Integer> CLIENT_ACTIVE_CONTROLLER_TIERS = new ConcurrentHashMap<>();
    @Nullable
    private static Level clientIndexLevel;

    private static final String ENERGY_TAG = "QuantumEnergy";
    private static final String TOTAL_IO_ENERGY_TAG = "TotalIoEnergy";
    private static final String CORE_LEVEL_TAG = "CoreLevel";
    private static final String STRUCTURE_TIER_TAG = "StructureTier";
    private static final String SUSPENDED_TAG = "StructureSuspended";
    private static final String READY_TAG = "StructureReady";
    private static final String CONSTRAINT_POWERED_TAG = "ConstraintPowered";
    private static final String STABILITY_BASIS_POINTS_TAG = "StabilityBasisPoints";
    private static final String UNPOWERED_TICKS_TAG = "StabilityUnpoweredTicks";
    private static final String POWERED_TICKS_TAG = "StabilityPoweredTicks";
    private static final String FULLNESS_TAG = "EnergyFullness";
    private static final String ENERGY_LOG_SCALE_TAG = "EnergyLogScale";
    private static final String LEAK_EVENT_SEQUENCE_TAG = "LeakEventSequence";
    private static final String INSTABILITY_PENALTIES_ENABLED_TAG = "InstabilityPenaltiesEnabled";
    private static final String RENDER_SIGNATURE_TAG = "StructureRenderSignature";
    private static final String BATTERY_ID_TAG = "BatteryId";
    private static final String TEMPERATURE_TAG = "TemperatureKelvin";
    private static final int VALIDATION_INTERVAL = 100;
    private static final int RENDER_ENERGY_SYNC_INTERVAL = 5;
    private static final double RENDER_ENERGY_INTERPOLATION_TICKS = 5.0;
    private static final int RENDER_LEAK_PULSE_TICKS = 12;
    private static final double NO_RENDER_LEAK_PULSE = -1.0;
    private static final int LEAK_EVENT_SEQUENCE_MODULUS = 1 << 15;
    private static final long FNV_OFFSET_BASIS = 1469598103934665603L;
    private static final long FNV_PRIME = 1099511628211L;

    private final QuantumEnergyLedger energy = new QuantumEnergyLedger();
    private final QuantumBatteryTransferHandler handler = new QuantumBatteryTransferHandler(this);
    private final QuantumStabilityWindow stabilityWindow = new QuantumStabilityWindow();
    private final QuantumStabilityGauge stabilityGauge = new QuantumStabilityGauge();
    private BigInteger totalIoEnergy = BigInteger.ZERO;
    private int coreLevel = QuantumBatteryState.MIN_CORE_LEVEL;
    private UUID batteryId = UUID.randomUUID();
    private double temperatureKelvin = QuantumBatteryThermalModel.ROOM_TEMPERATURE_K;
    private QuantumEnergyDisplay energyDisplay = QuantumEnergyDisplay.ZERO;
    private boolean energyDisplayDirty = true;
    private boolean remoteFluxProxy;

    private int structureTier;
    private boolean structureSuspended;
    private boolean structureReady;
    private boolean constraintPowered;
    private int entanglementCursor;
    private int leakEventSequence;
    private int clientEnergyLogBasisPoints;
    private double clientEnergyLogStartBasisPoints;
    private long clientEnergyLogUpdateGameTime = Long.MIN_VALUE;
    private boolean clientInstabilityPenaltiesEnabled = true;
    private boolean clientRenderStateReceived;
    private long clientLeakPulseStartGameTime = Long.MIN_VALUE;
    private int lastSyncedEnergyLogBasisPoints = -1;
    private int lastSyncedStabilityBasisPoints = -1;
    private int lastSyncedLeakEventSequence = -1;
    private boolean lastSyncedConstraintPowered;
    private boolean lastSyncedInstabilityPenaltiesEnabled;
    private long lastRenderEnergySyncGameTime = Long.MIN_VALUE;
    private long structureRenderSignature;
    private long nextValidationGameTime;
    private boolean structureDirty;
    private List<BlockPos> requiredChunkSamples = List.of();
    private List<List<StabilizerSlot>> stabilizerTopology = List.of();
    private List<List<CoolingSlot>> coolingTopology = List.of();
    private List<QuantumPluginEndpoint> pluginEndpoints = List.of();
    private int pluginDirectoryRevision;
    private List<TileQuantumEnergyConstraintInterface> stabilizerEntities = List.of();
    private List<QuantumBatteryCoolingSource> coolingSources = List.of();
    private boolean stabilizerEntitiesCached;
    private boolean coolingSourcesCached;
    private long[] stabilizerEnergyScratch = new long[0];
    private long effectiveCoolingTargetMilliKelvin = QuantumBatteryCoolingModel.NO_TARGET;
    private boolean coolingActive;
    private int coolingCursor;
    @Nullable
    private QuantumBatteryAssembler.Job assemblyJob;

    public TileQuantumBatteryController(@Nonnull BlockPos pos, @Nonnull BlockState state) {
        super(RegistryBlockEntityTypes.QUANTUM_BATTERY_CONTROLLER.get(), pos, state);
        energy.setCapacity(BigInteger.ZERO);
    }

    public static Collection<BlockPos> getLoadedControllersNear(Level level, BlockPos pos, int radius) {
        Map<Long, Set<BlockPos>> chunks = LOADED_CONTROLLERS.get(level);
        if (chunks == null) return List.of();

        int minChunkX = Math.floorDiv(pos.getX() - radius, 16);
        int maxChunkX = Math.floorDiv(pos.getX() + radius, 16);
        int minChunkZ = Math.floorDiv(pos.getZ() - radius, 16);
        int maxChunkZ = Math.floorDiv(pos.getZ() + radius, 16);
        List<BlockPos> result = new ArrayList<>();
        for (int chunkX = minChunkX; chunkX <= maxChunkX; chunkX++) {
            for (int chunkZ = minChunkZ; chunkZ <= maxChunkZ; chunkZ++) {
                Set<BlockPos> positions = chunks.get(chunkKey(chunkX, chunkZ));
                if (positions != null) result.addAll(positions);
            }
        }
        return result;
    }

    public static boolean isDynamicStructureBlock(BlockGetter view, BlockPos pos) {
        Collection<BlockPos> controllers = view instanceof Level level
                ? getLoadedControllersNear(level, pos, 15)
                : getClientActiveControllerPositions();
        for (BlockPos controllerPos : controllers) {
            int x = pos.getX() - controllerPos.getX();
            int y = pos.getY() - controllerPos.getY();
            int z = pos.getZ() - controllerPos.getZ();
            if (Math.max(Math.max(Math.abs(x), Math.abs(y)), Math.abs(z)) > 15) continue;
            int cellTier = QuantumBatteryStructure.tierAt(x, y, z);
            if (cellTier <= 0) continue;

            int activeTier;
            if (view instanceof Level level) {
                if (!(level.getBlockEntity(controllerPos) instanceof TileQuantumBatteryController controller)
                        || !controller.isAnimationActive()) {
                    continue;
                } else {
                    activeTier = controller.getStructureTier();
                }
            } else {
                activeTier = CLIENT_ACTIVE_CONTROLLER_TIERS.getOrDefault(controllerPos.asLong(), 0);
            }
            if (cellTier <= activeTier) return true;
        }
        return false;
    }

    public static boolean isDynamicControllerBlock(BlockGetter view, BlockPos pos) {
        if (view instanceof Level level) {
            return level.getBlockEntity(pos) instanceof TileQuantumBatteryController controller
                    && controller.isAnimationActive();
        }
        return CLIENT_ACTIVE_CONTROLLER_TIERS.getOrDefault(pos.asLong(), 0) > 0;
    }

    public static Collection<BlockPos> getClientActiveControllerPositions() {
        List<BlockPos> result = new ArrayList<>();
        for (Map.Entry<Long, Integer> entry : CLIENT_ACTIVE_CONTROLLER_TIERS.entrySet()) {
            if (entry.getValue() > 0) result.add(BlockPos.of(entry.getKey()));
        }
        return result;
    }

    public static void clearLevelControllerState(Level level) {
        LOADED_CONTROLLERS.remove(level);
        if (!level.isClientSide || clientIndexLevel != level) return;
        CLIENT_ACTIVE_CONTROLLER_TIERS.clear();
        clientIndexLevel = null;
    }

    @Override
    public void onLoad() {
        super.onLoad();
        if (level != null) {
            registerController(level, worldPosition);
            if (level.isClientSide) {
                updateClientAnimationState();
            } else {
                structureReady = false;
                constraintPowered = false;
                stabilityWindow.reset();
                handler.clearWindow();
                structureDirty = true;
                clearStabilizerCache();
            }
        }
    }

    @Override
    public void onChunkUnloaded() {
        clearStabilizerCache();
        unregisterController();
        super.onChunkUnloaded();
    }

    @Override
    public void setRemoved() {
        clearStabilizerCache();
        unregisterController();
        super.setRemoved();
    }

    @Nonnull
    @Override
    public FluxDeviceType getDeviceType() {
        return FluxDeviceType.STORAGE;
    }

    @Nonnull
    @Override
    public QuantumBatteryTransferHandler getTransferHandler() {
        return handler;
    }

    @Override
    public long getMaxTransferLimit() {
        return Long.MAX_VALUE;
    }

    @Nonnull
    @Override
    public ItemStack getDisplayStack() {
        return new ItemStack(RegistryBlocks.QUANTUM_BATTERY_CONTROLLER.get());
    }

    @Override
    protected void onServerTick() {
        super.onServerTick();
        tickAssembly();
        refreshStabilityForCurrentTick();
        applyInstabilityPenalties();
        tickProcessingPlugins();
        syncRenderStateIfNeeded(false);
    }

    public void validateStructure() {
        Level level = getLevel();
        if (level == null || level.isClientSide) return;
        structureDirty = false;
        clearStabilizerCache();

        long confirmedRenderSignature = FNV_OFFSET_BASIS;
        List<List<StabilizerSlot>> validatedTopology = new ArrayList<>(coreLevel);
        List<List<CoolingSlot>> validatedCoolingTopology = new ArrayList<>(coreLevel);
        List<List<QuantumPluginEndpoint>> validatedPluginTopology = new ArrayList<>(coreLevel);
        for (int tier = 1; tier <= coreLevel; tier++) {
            LayerValidation validation = validateLayer(level, QuantumBatteryStructure.layer(tier));
            if (validation.status() == LayerStatus.INVALID) break;
            if (validation.status() == LayerStatus.UNLOADED) {
                if (tier <= structureTier) {
                    setStructureState(structureTier, true);
                    scheduleNextValidation(level.getGameTime());
                    return;
                }
                break;
            }
            validatedTopology.add(validation.stabilizers());
            validatedCoolingTopology.add(validation.coolingSlots());
            validatedPluginTopology.add(validation.pluginEndpoints());
            if (validation.stabilizers().isEmpty()) break;
            confirmedRenderSignature ^= validation.renderSignature();
            confirmedRenderSignature *= FNV_PRIME;
        }
        int confirmedTier = QuantumBatteryState.highestFormedTier(validatedTopology);
        setStructureState(confirmedTier, false,
                confirmedTier == 0 ? 0L : confirmedRenderSignature, confirmedTier > 0);
        stabilizerTopology = List.copyOf(validatedTopology.subList(0, confirmedTier));
        coolingTopology = List.copyOf(validatedCoolingTopology.subList(0, confirmedTier));
        setPluginEndpoints(validatedPluginTopology.subList(0, confirmedTier).stream()
                .flatMap(Collection::stream)
                .sorted(QuantumPluginEndpoint.DISPLAY_ORDER)
                .toList());
        scheduleNextValidation(level.getGameTime());
    }

    public int getStructureTier() {
        return structureTier;
    }

    public int getCoreLevel() {
        return coreLevel;
    }

    public int getCoreUpgradeProgressBasisPoints() {
        return QuantumBatteryState.coreProgressBasisPoints(coreLevel, totalIoEnergy);
    }

    public boolean canUpgradeCore() {
        return QuantumBatteryState.canUpgradeCore(coreLevel, totalIoEnergy);
    }

    public boolean tryUpgradeCore(net.minecraft.world.entity.player.Player player) {
        if (level == null || level.isClientSide || !canPlayerManage(player) || !canUpgradeCore()) return false;
        coreLevel++;
        markStructureDirty();
        setChanged();
        sendBlockUpdate();
        return true;
    }

    public long getStructureRenderSignature() {
        return structureRenderSignature;
    }

    public boolean isStructureSuspended() {
        return structureSuspended;
    }

    public boolean isOperational() {
        return structureTier > 0 && structureTier <= coreLevel
                && !structureSuspended && structureReady && level != null;
    }

    public boolean isAnimationActive() {
        return isOperational();
    }

    public boolean isStructureReady() {
        return structureReady;
    }

    public boolean areStructureChunksLoadedForRender() {
        return areRequiredChunksLoaded();
    }

    public boolean isConstraintPowered() {
        return constraintPowered;
    }

    public int getStabilityBasisPoints() {
        return stabilityGauge.basisPoints();
    }

    public int getLeakEventSequence() {
        return leakEventSequence;
    }

    public double getRenderEnergyLogScale(double gameTime) {
        double basisPoints = level != null && level.isClientSide
                ? interpolatedClientEnergyLogBasisPoints(gameTime)
                : QuantumEnergyDisplay.logBasisPoints(energy.stored());
        return Math.clamp(basisPoints, 0.0, 10_000.0) / 10_000.0;
    }

    public double getRenderLeakPulseAge(double gameTime) {
        if (clientLeakPulseStartGameTime == Long.MIN_VALUE || !Double.isFinite(gameTime)) {
            return NO_RENDER_LEAK_PULSE;
        }
        double age = gameTime - clientLeakPulseStartGameTime;
        return age >= 0.0 && age < RENDER_LEAK_PULSE_TICKS ? age : NO_RENDER_LEAK_PULSE;
    }

    public boolean areRenderInstabilityPenaltiesEnabled() {
        return level != null && level.isClientSide
                ? clientInstabilityPenaltiesEnabled
                : Config.quantumInstabilityPenaltiesEnabled;
    }

    public void applyClientRenderState(int energyLogBasisPoints, int stabilityBasisPoints,
                                       int incomingLeakEventSequence, boolean powered,
                                       boolean instabilityPenaltiesEnabled) {
        if (level == null || !level.isClientSide) return;

        int normalizedSequence = Math.floorMod(incomingLeakEventSequence, LEAK_EVENT_SEQUENCE_MODULUS);
        if (clientRenderStateReceived && leakEventSequence != normalizedSequence) {
            clientLeakPulseStartGameTime = level.getGameTime();
        }
        updateClientEnergyLogTarget(energyLogBasisPoints, clientRenderStateReceived);
        leakEventSequence = normalizedSequence;
        constraintPowered = powered;
        clientInstabilityPenaltiesEnabled = instabilityPenaltiesEnabled;
        stabilityGauge.load(stabilityBasisPoints, stabilityGauge.unpoweredTickRemainder(),
                stabilityGauge.poweredTickRemainder());
        clientRenderStateReceived = true;
    }

    private void updateClientEnergyLogTarget(int energyLogBasisPoints, boolean interpolate) {
        int target = Math.clamp(energyLogBasisPoints, 0, 10_000);
        double gameTime = level == null ? 0.0 : level.getGameTime();
        double current = interpolate && target != 0
                ? interpolatedClientEnergyLogBasisPoints(gameTime)
                : target;
        clientEnergyLogStartBasisPoints = current;
        clientEnergyLogBasisPoints = target;
        clientEnergyLogUpdateGameTime = level == null ? Long.MIN_VALUE : level.getGameTime();
    }

    private double interpolatedClientEnergyLogBasisPoints(double gameTime) {
        if (clientEnergyLogUpdateGameTime == Long.MIN_VALUE || !Double.isFinite(gameTime)) {
            return clientEnergyLogBasisPoints;
        }
        double progress = Math.clamp(
                (gameTime - clientEnergyLogUpdateGameTime) / RENDER_ENERGY_INTERPOLATION_TICKS,
                0.0, 1.0);
        return clientEnergyLogStartBasisPoints
                + (clientEnergyLogBasisPoints - clientEnergyLogStartBasisPoints) * progress;
    }

    public String getStabilityPercent() {
        return formatStabilityBasisPoints(stabilityGauge.basisPoints());
    }

    public static String formatStabilityBasisPoints(int basisPoints) {
        return QuantumBatteryState.formatStability(basisPoints);
    }

    public BigInteger getStoredEnergy() {
        return energy.stored();
    }

    /**
     * Atomically charges an internal processing plugin without adding the
     * transfer to the core-upgrade network-I/O counter.
     */
    public boolean tryConsumePluginEnergy(BigInteger amount) {
        Objects.requireNonNull(amount, "amount");
        if (amount.signum() < 0 || !isOperational() || level == null || level.isClientSide
                || energy.stored().compareTo(amount) < 0) {
            return false;
        }
        if (amount.signum() == 0) return true;
        BigInteger extracted = energy.extract(amount);
        if (!extracted.equals(amount)) return false;
        energyDisplayDirty = true;
        setChanged();
        return true;
    }

    public Optional<BigInteger> getCapacity() {
        return energy.capacity();
    }

    public String getStoredEnergyCompact() {
        return BigIntegerFormatter.compact(energy.stored());
    }

    public String getCapacityCompact() {
        return energy.capacity().map(BigIntegerFormatter::compact).orElse("∞");
    }

    public QuantumEnergyDisplay getEnergyDisplay() {
        if (energyDisplayDirty) {
            energyDisplay = QuantumEnergyDisplay.from(energy.stored());
            energyDisplayDirty = false;
        }
        return energyDisplay;
    }

    public double getTemperatureKelvin() {
        return temperatureKelvin;
    }

    public int getTemperatureCentiKelvin() {
        return (int) Math.round(temperatureKelvin * 100.0);
    }

    public int getEffectiveCoolingTargetCentiKelvin() {
        if (effectiveCoolingTargetMilliKelvin == QuantumBatteryCoolingModel.NO_TARGET) return -1;
        return (int) Math.round(effectiveCoolingTargetMilliKelvin / 10.0);
    }

    public double getEffectiveCoolingTargetKelvin() {
        return effectiveCoolingTargetMilliKelvin == QuantumBatteryCoolingModel.NO_TARGET
                ? -1.0
                : QuantumBatteryCoolingModel.kelvin(effectiveCoolingTargetMilliKelvin);
    }

    public boolean isCoolingActive() {
        return coolingActive;
    }

    public List<QuantumPluginEndpoint> getEntanglementEndpoints() {
        return isOperational() ? pluginEndpoints : List.of();
    }

    public int getPluginDirectoryRevision() {
        return pluginDirectoryRevision;
    }

    @Nullable
    public QuantumPluginEndpoint findEntanglementEndpoint(QuantumPluginEndpointKey key) {
        if (!isOperational() || level == null || level.isClientSide) return null;
        for (QuantumPluginEndpoint endpoint : pluginEndpoints) {
            if (!endpoint.key().equals(key)) continue;
            BlockPos expectedPos = worldPosition.offset(key.relativePos());
            if (!endpoint.absolutePos().equals(expectedPos)) return null;
            var current = LoadedBlockAccess.read(level, expectedPos);
            if (current == null) return null;
            BlockState state = current.state();
            if (!isValidPluginState(endpoint.ringTier(), state)) return null;
            BlockEntity blockEntity = current.blockEntity();
            QuantumPluginAdapter adapter = QuantumPluginAdapters.find(state, blockEntity);
            if (!adapter.id().equals(key.adapterId())
                    || !adapter.localEndpointId(state, blockEntity).equals(key.localEndpointId())) {
                return null;
            }
            return endpoint;
        }
        return null;
    }

    @Nullable
    public <T> T resolveEntanglementCapability(QuantumPluginEndpointKey key,
                                               EntanglementResourceKind resourceKind,
                                               EntanglementResourceDirection direction,
                                               BlockCapability<T, Direction> capability) {
        QuantumPluginEndpoint endpoint = findEntanglementEndpoint(key);
        if (endpoint == null || !endpoint.supports(resourceKind, direction)
                || !(level instanceof net.minecraft.server.level.ServerLevel serverLevel)) {
            return null;
        }
        var current = LoadedBlockAccess.read(serverLevel, endpoint.absolutePos());
        // A null block entity would make the capability lookup call Level#getBlockEntity again.
        if (current == null || current.state().hasBlockEntity() && current.blockEntity() == null) return null;
        return serverLevel.getCapability(capability, endpoint.absolutePos(), current.state(), current.blockEntity(), null);
    }

    public int resolveEntanglementRedstoneSignal(QuantumPluginEndpointKey key) {
        QuantumPluginEndpoint endpoint = findEntanglementEndpoint(key);
        if (endpoint == null || level == null) return 0;
        var current = LoadedBlockAccess.read(level, endpoint.absolutePos());
        if (current == null) return 0;
        BlockState state = current.state();
        if (!state.isSignalSource()) return 0;
        var signals = LoadedBlockAccess.signals(level);
        int signal = 0;
        for (Direction direction : Direction.values()) {
            signal = Math.max(signal, state.getSignal(signals, endpoint.absolutePos(), direction));
            signal = Math.max(signal, state.getDirectSignal(signals, endpoint.absolutePos(), direction));
        }
        return Math.clamp(signal, 0, 15);
    }

    public boolean openPluginMenuFromEntanglement(ServerPlayer player,
                                                  TileQuantumEntanglementController accessController,
                                                  QuantumPluginEndpointKey key) {
        if (player.isSpectator() || !canPlayerManage(player)
                || !accessController.canProvideRemoteAccess(player, this)) {
            return false;
        }
        QuantumPluginEndpoint endpoint = findEntanglementEndpoint(key);
        if (endpoint == null || QuantumPluginAdapters.isConstraint(endpoint)
                || !endpoint.remoteMenu() || level == null) return false;
        var current = LoadedBlockAccess.read(level, endpoint.absolutePos());
        if (current == null) return false;
        BlockEntity blockEntity = current.blockEntity();
        QuantumPluginAdapter adapter = QuantumPluginAdapters.find(endpoint.key().adapterId());
        if (adapter == null || blockEntity == null
                || !adapter.matches(current.state(), blockEntity)) {
            return false;
        }
        return adapter.openRemoteMenu(player, blockEntity, accessController, key);
    }

    public double getConstraintEnergyMultiplier() {
        return QuantumBatteryThermalModel.constraintEnergyMultiplier(temperatureKelvin);
    }

    public void setTemperatureKelvin(double temperatureKelvin) {
        double clamped = QuantumBatteryThermalModel.clampTemperature(temperatureKelvin);
        if (Double.compare(this.temperatureKelvin, clamped) == 0) return;
        this.temperatureKelvin = clamped;
        setChanged();
    }

    public UUID getBatteryId() {
        return batteryId;
    }

    public int getNetworkColorRgb() {
        int color = getNetwork().isValid() ? getNetwork().getNetworkColor() : mClientColor;
        color &= 0xFFFFFF;
        return color == 0 ? 0x43D9FF : color;
    }

    public void markAsRemoteFluxProxy() {
        remoteFluxProxy = true;
    }

    public void openQuantumMenu(ServerPlayer player) {
        openQuantumMenu(player, null);
    }

    public void openQuantumMenuFromEntanglement(ServerPlayer player,
                                                 TileQuantumEntanglementController accessController) {
        openQuantumMenu(player, accessController);
    }

    public boolean openFluxMenuFromEntanglement(ServerPlayer player,
                                                 TileQuantumEntanglementController accessController) {
        if (player.isSpectator()
                || !canPlayerManage(player)
                || !accessController.canProvideRemoteAccess(player, this)
                || level == null
                || level.isClientSide) {
            return false;
        }
        if (((TileFluxDeviceAccessor) (Object) this).fluxod$getPlayerUsing() != null) {
            player.displayClientMessage(FluxTranslate.ACCESS_OCCUPY, true);
            return false;
        }

        CompoundTag updateTag = new CompoundTag();
        writeCustomTag(updateTag, FluxConstants.NBT_TILE_UPDATE);
        GlobalPos targetPos = GlobalPos.of(level.dimension(), worldPosition);
        SimpleMenuProvider provider = new SimpleMenuProvider(
                (containerId, inventory, ignored) ->
                        new RemoteFluxMenu(containerId, inventory, this, accessController),
                getDisplayName());
        player.openMenu(provider, buffer -> RemoteFluxMenu.writeOpeningData(buffer, targetPos,
                accessController.getBlockPos(), updateTag));
        return true;
    }

    private void openQuantumMenu(ServerPlayer player,
                                 @Nullable TileQuantumEntanglementController accessController) {
        if (player.isSpectator() || !canPlayerManage(player)) return;
        if (accessController != null && !accessController.canProvideRemoteAccess(player, this)) return;
        SimpleMenuProvider provider = new SimpleMenuProvider(
                (containerId, inventory, ignored) -> accessController == null
                        ? new QuantumBatteryMenu(containerId, inventory, this)
                        : new QuantumBatteryMenu(containerId, inventory, this, accessController),
                Component.translatable("screen.flux_overdrive.quantum_battery.title"));
        player.openMenu(provider, buffer -> {
            buffer.writeBlockPos(worldPosition);
            buffer.writeBoolean(accessController != null);
            if (accessController != null) buffer.writeBlockPos(accessController.getBlockPos());
        });
    }

    public QuantumBatteryAssembler.Result startAssembly(ServerPlayer player, int targetTier) {
        if (assemblyJob != null) {
            return new QuantumBatteryAssembler.Result(QuantumBatteryAssembler.Status.BUSY, 0, 0, null);
        }
        QuantumBatteryAssembler.Start start = QuantumBatteryAssembler.plan(this, player, targetTier);
        assemblyJob = start.job();
        if (assemblyJob != null) setStructureReady(false);
        return start.result();
    }

    public boolean isAssembling() {
        return assemblyJob != null;
    }

    public void markStructureDirty() {
        structureDirty = true;
        clearStabilizerCache();
        setStructureReady(false);
    }

    public boolean canPlayerManage(net.minecraft.world.entity.player.Player player) {
        if (Objects.equals(getOwnerUUID(), player.getUUID())) return true;
        return getNetwork().isValid() && canPlayerAccess(player);
    }

    QuantumEnergyLedger getEnergyLedger() {
        return energy;
    }

    boolean refreshStabilityForCurrentTick() {
        Level currentLevel = getLevel();
        if (currentLevel == null || currentLevel.isClientSide) return false;

        if (structureTier > coreLevel) {
            setStructureState(coreLevel, false, 0L, false);
            structureDirty = true;
        }
        long gameTime = currentLevel.getGameTime();
        if (!stabilityWindow.beginCheck(gameTime)) return isOperational();
        if (assemblyJob != null) {
            setStructureReady(false);
            clearCoolingStatus();
            return false;
        }
        if (structureTier > 0 && !areRequiredChunksLoaded()) {
            setStructureState(structureTier, true);
            clearCoolingStatus();
            return false;
        }
        if (structureSuspended || structureDirty || gameTime >= nextValidationGameTime) {
            validateStructure();
        }
        if (isOperational()) {
            settleThermalForCurrentTick();
        } else {
            clearCoolingStatus();
        }
        updateConstraintStability();
        return isOperational();
    }

    private void applyInstabilityPenalties() {
        Level currentLevel = getLevel();
        if (currentLevel == null || currentLevel.isClientSide || !isOperational()) return;
        if (!Config.quantumInstabilityPenaltiesEnabled) return;

        int stabilityBasisPoints = stabilityGauge.basisPoints();
        if (stabilityBasisPoints <= 0) {
            if (!tryInterceptQuench()) triggerQuench();
            return;
        }

        int leakThresholdBasisPoints = (int) Math.round(Config.quantumInstabilityLeakThreshold * 100.0);
        if (leakThresholdBasisPoints <= 0 || stabilityBasisPoints >= leakThresholdBasisPoints) return;

        if (currentLevel.random.nextDouble() >= Config.quantumInstabilityLeakChance) return;

        double severity = 1.0 - (double) stabilityBasisPoints / leakThresholdBasisPoints;
        double fraction = Config.quantumInstabilityLeakRatio
                * (0.5 + 0.5 * severity)
                * currentLevel.random.nextDouble();
        BigInteger stored = energy.stored();
        if (stored.signum() <= 0) return;
        BigInteger leak = stored.multiply(BigInteger.valueOf((long) Math.ceil(fraction * 1_000_000.0)))
                .divide(BigInteger.valueOf(1_000_000))
                .max(BigInteger.ONE);
        if (energy.extract(leak).signum() > 0) {
            leakEventSequence = (leakEventSequence + 1) % LEAK_EVENT_SEQUENCE_MODULUS;
            energyDisplayDirty = true;
            setChanged();
            syncRenderStateIfNeeded(true);
        }
    }

    private boolean tryInterceptQuench() {
        if (!(level instanceof net.minecraft.server.level.ServerLevel serverLevel)) return false;

        for (QuantumPluginEndpoint endpoint : pluginEndpoints) {
            BlockPos pluginPos = endpoint.absolutePos();
            if (!serverLevel.isLoaded(pluginPos)) continue;
            BlockState state = serverLevel.getBlockState(pluginPos);
            if (!(state.getBlock() instanceof QuantumBatteryQuenchInterceptor interceptor)
                    || !isValidPluginState(endpoint.ringTier(), state)
                    || !interceptor.tryInterceptQuench(serverLevel, pluginPos)) {
                continue;
            }

            energy.setStored(BigInteger.ZERO);
            energyDisplayDirty = true;
            stabilityGauge.reset();
            setStructureState(0, false, 0L, false);
            setPluginEndpoints(List.of());
            markStructureDirty();
            setChanged();
            sendBlockUpdate();
            return true;
        }
        return false;
    }

    private void triggerQuench() {
        Level currentLevel = getLevel();
        if (currentLevel == null || currentLevel.isClientSide) return;

        BigInteger storedBeforeQuench = energy.stored();
        energy.setStored(BigInteger.ZERO);
        energyDisplayDirty = true;
        stabilityGauge.reset();

        if (currentLevel instanceof net.minecraft.server.level.ServerLevel serverLevel) {
            double x = worldPosition.getX() + 0.5;
            double y = worldPosition.getY() + 0.5;
            double z = worldPosition.getZ() + 0.5;
            double radius = QuantumBatteryState.quenchRadius(storedBeforeQuench,
                    Config.quantumMeltdownExplosionRadius,
                    Config.quantumMeltdownRadiusPerOrder,
                    Config.quantumMeltdownMaxRadius);
            quenchExplosion(serverLevel, x, y, z, radius);
            Set<BlockPos> structureBlocks = collectQuenchStructureBlocks(serverLevel);
            // Immediately invalidate the structure so no transfer can target the melted-down battery.
            setStructureState(0, false, 0L, false);
            QuantumBatteryQuenchManager.start(serverLevel, x, y, z,
                    Math.min(radius, Config.quantumMeltdownMaxBlockDestructionRadius), structureBlocks);
        }

        markStructureDirty();
        setChanged();
        sendBlockUpdate();
    }

    /**
     * Custom quench explosion: vanilla {@code Level.explode} collects every block in the blast
     * radius into a packet sent to all clients, which becomes catastrophic at large radii.
     * Instead, play the sound and particles locally, damage entities with a distance falloff,
     * and register a bounded, tick-staggered manual destruction pass.
     */
    private void quenchExplosion(net.minecraft.server.level.ServerLevel serverLevel,
                                 double x, double y, double z, double radius) {
        float radiusF = (float) radius;
        serverLevel.playSound(null, x, y, z, SoundEvents.GENERIC_EXPLODE.value(),
                SoundSource.BLOCKS, 4.0F,
                (1.0F + (serverLevel.random.nextFloat() - serverLevel.random.nextFloat()) * 0.2F) * 0.7F);
        serverLevel.sendParticles(ParticleTypes.EXPLOSION_EMITTER, x, y, z, 1, 0.0, 0.0, 0.0, 0.0);
        int particles = (int) Math.min(128, Math.max(8, radius / 2.0));
        serverLevel.sendParticles(ParticleTypes.EXPLOSION, x, y, z, particles,
                radius, radius, radius, 0.5);
        damageEntitiesInRadius(serverLevel, x, y, z, radiusF);
    }

    private void damageEntitiesInRadius(net.minecraft.server.level.ServerLevel serverLevel,
                                        double x, double y, double z, float radius) {
        double range = radius * 2.0;
        AABB bounds = new AABB(x - range, y - range, z - range, x + range, y + range, z + range);
        Vec3 center = new Vec3(x, y, z);
        for (Entity entity : serverLevel.getEntities((Entity) null, bounds, Entity::isAlive)) {
            double distance = Math.sqrt(entity.distanceToSqr(center));
            if (distance > range) continue;
            float damage = QuantumBatteryState.explosionDamage(range, distance);
            entity.hurt(serverLevel.damageSources().explosion(null, null), damage);
            double falloff = 1.0 - distance / range;
            Vec3 knockback = entity.position().subtract(center).normalize()
                    .scale(falloff * radius * 0.25);
            entity.setDeltaMovement(entity.getDeltaMovement().add(knockback));
        }
    }

    private Set<BlockPos> collectQuenchStructureBlocks(
            net.minecraft.server.level.ServerLevel serverLevel) {
        Set<BlockPos> result = new HashSet<>();
        result.add(worldPosition.immutable());
        for (int tier = 1; tier <= structureTier; tier++) {
            for (QuantumBatteryStructure.Cell cell : QuantumBatteryStructure.layer(tier).cells()) {
                QuantumBatteryStructure.Offset offset = cell.offset();
                BlockPos target = worldPosition.offset(offset.x(), offset.y(), offset.z());
                if (QuantumBatteryAssembler.isAcceptedBlock(tier, cell, serverLevel.getBlockState(target))) {
                    result.add(target.immutable());
                }
            }
        }
        return result;
    }

    void applyNetworkTransfer(long added, long removed) {
        BigInteger extracted = energy.extract(BigInteger.valueOf(Math.max(0, removed)));
        BigInteger inserted = energy.insert(BigInteger.valueOf(Math.max(0, added)));
        if (extracted.longValueExact() != removed || inserted.longValueExact() != added) {
            FluxOverdrive.LOGGER.error("Quantum battery transfer invariant failed at {}: +{}, -{}", worldPosition, added, removed);
        }
        totalIoEnergy = QuantumBatteryState.accumulateTotalIo(totalIoEnergy, inserted, extracted);
        energyDisplayDirty = true;
        setChanged();
    }

    private void syncRenderStateIfNeeded(boolean force) {
        if (level == null || level.isClientSide || !isAnimationActive()) return;

        int currentEnergyLog = QuantumEnergyDisplay.logBasisPoints(energy.stored());
        int currentStability = stabilityGauge.basisPoints();
        boolean stateChanged = currentStability != lastSyncedStabilityBasisPoints
                || leakEventSequence != lastSyncedLeakEventSequence
                || constraintPowered != lastSyncedConstraintPowered
                || Config.quantumInstabilityPenaltiesEnabled != lastSyncedInstabilityPenaltiesEnabled;
        long gameTime = level.getGameTime();
        if (!force && !stateChanged && currentEnergyLog == lastSyncedEnergyLogBasisPoints) return;
        boolean endpointTransition = currentEnergyLog == 0 || lastSyncedEnergyLogBasisPoints == 0
                || lastSyncedEnergyLogBasisPoints < 0;
        if (!force && !stateChanged && !endpointTransition
                && lastRenderEnergySyncGameTime != Long.MIN_VALUE
                && gameTime - lastRenderEnergySyncGameTime < RENDER_ENERGY_SYNC_INTERVAL) {
            return;
        }

        lastSyncedEnergyLogBasisPoints = currentEnergyLog;
        lastSyncedStabilityBasisPoints = currentStability;
        lastSyncedLeakEventSequence = leakEventSequence;
        lastSyncedConstraintPowered = constraintPowered;
        lastSyncedInstabilityPenaltiesEnabled = Config.quantumInstabilityPenaltiesEnabled;
        lastRenderEnergySyncGameTime = gameTime;
        if (level instanceof net.minecraft.server.level.ServerLevel serverLevel) {
            PacketDistributor.sendToPlayersTrackingChunk(serverLevel, new ChunkPos(worldPosition),
                    new QuantumBatteryRenderStatePayload(worldPosition, currentEnergyLog,
                            currentStability, leakEventSequence, constraintPowered,
                            Config.quantumInstabilityPenaltiesEnabled));
        }
    }

    @Override
    protected void collectImplicitComponents(@Nonnull DataComponentMap.Builder builder) {
        super.collectImplicitComponents(builder);
        builder.set(FluxDataComponents.STORED_ENERGY, 0L);
        CompoundTag data = new CompoundTag();
        writeEnergy(data);
        writeCoreProgress(data);
        writeStability(data);
        writeTemperature(data);
        data.putUUID(BATTERY_ID_TAG, batteryId);
        builder.set(FluxOdDataComponents.QUANTUM_BATTERY_DATA, CustomData.of(data));
    }

    @Override
    protected void applyImplicitComponents(BlockEntity.@NotNull DataComponentInput input) {
        super.applyImplicitComponents(input);
        handler.clearWindow();
        CustomData data = input.get(FluxOdDataComponents.QUANTUM_BATTERY_DATA);
        if (data != null) {
            CompoundTag tag = data.copyTag();
            readEnergy(tag);
            readCoreProgress(tag, 0);
            readStability(tag);
            readTemperature(tag);
            readBatteryId(tag);
            clearStabilizerCache();
        }
    }

    @Override
    public void writeCustomTag(@Nonnull CompoundTag tag, byte type) {
        super.writeCustomTag(tag, type);
        if (type == FluxConstants.NBT_TILE_SETTINGS) return;
        tag.putInt(CORE_LEVEL_TAG, coreLevel);
        tag.putInt(STRUCTURE_TIER_TAG, structureTier);
        tag.putBoolean(SUSPENDED_TAG, structureSuspended);
        tag.putBoolean(READY_TAG, structureReady);
        tag.putBoolean(CONSTRAINT_POWERED_TAG, constraintPowered);
        tag.putInt(FULLNESS_TAG, energy.fullnessScaled(10_000));
        tag.putInt(ENERGY_LOG_SCALE_TAG, QuantumEnergyDisplay.logBasisPoints(energy.stored()));
        tag.putInt(LEAK_EVENT_SEQUENCE_TAG, leakEventSequence);
        tag.putBoolean(INSTABILITY_PENALTIES_ENABLED_TAG, Config.quantumInstabilityPenaltiesEnabled);
        tag.putLong(RENDER_SIGNATURE_TAG, structureRenderSignature);
        tag.putUUID(BATTERY_ID_TAG, batteryId);
        writeStability(tag);
        writeTemperature(tag);
        if (type == FluxConstants.NBT_SAVE_ALL || type == FluxConstants.NBT_TILE_DROP) {
            writeEnergy(tag);
            writeTotalIoEnergy(tag);
        }
    }

    @Override
    public void readCustomTag(@Nonnull CompoundTag tag, byte type) {
        int previousAnimationTier = clientAnimationTier();
        int previousLeakEventSequence = leakEventSequence;
        boolean hadClientRenderState = clientRenderStateReceived;
        super.readCustomTag(tag, type);
        // Flux settings packets are partial patches, not complete block entity state.
        if (type == FluxConstants.NBT_TILE_SETTINGS) return;
        int persistedStructureTier = Math.clamp(tag.getInt(STRUCTURE_TIER_TAG), 0, 3);
        coreLevel = QuantumBatteryState.coreLevelForLoad(tag.contains(CORE_LEVEL_TAG),
                tag.getInt(CORE_LEVEL_TAG), persistedStructureTier);
        structureTier = Math.min(persistedStructureTier, coreLevel);
        structureSuspended = tag.getBoolean(SUSPENDED_TAG);
        structureReady = tag.getBoolean(READY_TAG);
        constraintPowered = tag.getBoolean(CONSTRAINT_POWERED_TAG);
        if (tag.contains(ENERGY_LOG_SCALE_TAG)) {
            updateClientEnergyLogTarget(tag.getInt(ENERGY_LOG_SCALE_TAG), hadClientRenderState);
        }
        if (tag.contains(LEAK_EVENT_SEQUENCE_TAG)) {
            leakEventSequence = Math.floorMod(tag.getInt(LEAK_EVENT_SEQUENCE_TAG), LEAK_EVENT_SEQUENCE_MODULUS);
        }
        if (tag.contains(INSTABILITY_PENALTIES_ENABLED_TAG)) {
            clientInstabilityPenaltiesEnabled = tag.getBoolean(INSTABILITY_PENALTIES_ENABLED_TAG);
        }
        structureRenderSignature = tag.getLong(RENDER_SIGNATURE_TAG);
        readBatteryId(tag);
        readStability(tag);
        readTemperature(tag);
        if (type == FluxConstants.NBT_SAVE_ALL || type == FluxConstants.NBT_TILE_DROP) {
            readEnergy(tag);
            readTotalIoEnergy(tag);
        }
        clearStabilizerCache();
        energy.setCapacity(Config.quantumCapacityForTier(structureTier));
        rebuildRequiredChunkSamples();
        if (level != null && level.isClientSide && !remoteFluxProxy) {
            if (hadClientRenderState && previousLeakEventSequence != leakEventSequence) {
                clientLeakPulseStartGameTime = level.getGameTime();
            }
            clientRenderStateReceived = true;
            updateClientAnimationState();
            int animationTier = clientAnimationTier();
            if (previousAnimationTier != animationTier) {
                markClientStructureRenderDirty(Math.max(previousAnimationTier, animationTier));
            }
        }
    }

    private LayerValidation validateLayer(Level level, QuantumBatteryStructure.Layer layer) {
        List<StabilizerSlot> stabilizers = new ArrayList<>();
        List<CoolingSlot> coolingSlots = new ArrayList<>();
        List<QuantumPluginEndpoint> pluginEndpoints = new ArrayList<>();
        Set<Long> checkedChunks = new HashSet<>();
        long renderSignature = FNV_OFFSET_BASIS ^ layer.tier();
        for (QuantumBatteryStructure.Cell cell : layer.cells()) {
            QuantumBatteryStructure.Offset offset = cell.offset();
            BlockPos target = worldPosition.offset(offset.x(), offset.y(), offset.z());
            long chunk = net.minecraft.world.level.ChunkPos.asLong(target);
            if (checkedChunks.add(chunk) && !level.isLoaded(target)) {
                return new LayerValidation(LayerStatus.UNLOADED, List.of(), List.of(), List.of(), 0L);
            }

            BlockState state = level.getBlockState(target);
            if (!QuantumBatteryAssembler.isAcceptedBlock(layer.tier(), cell, state)) {
                return new LayerValidation(LayerStatus.INVALID, List.of(), List.of(), List.of(), 0L);
            }
            if (cell.isIntersection()) {
                renderSignature ^= Block.getId(state);
                renderSignature *= FNV_PRIME;
            }
            if (QuantumBatteryStructure.isStabilizerSlot(layer.tier(), cell)
                    && level.getBlockEntity(target) instanceof TileQuantumEnergyConstraintInterface) {
                stabilizers.add(new StabilizerSlot(target.immutable(), layer.tier()));
            }
            if (QuantumBatteryStructure.isStabilizerSlot(layer.tier(), cell)
                    && level.getBlockEntity(target) instanceof QuantumBatteryCoolingSource) {
                coolingSlots.add(new CoolingSlot(target.immutable(), layer.tier()));
            }
            if (QuantumBatteryStructure.isStabilizerSlot(layer.tier(), cell)
                    && state.getBlock() instanceof QuantumBatteryPlugin) {
                pluginEndpoints.add(QuantumPluginAdapters.describe(worldPosition, target, layer.tier(),
                        state, level.getBlockEntity(target)));
            }
        }
        return new LayerValidation(LayerStatus.VALID, List.copyOf(stabilizers),
                List.copyOf(coolingSlots), List.copyOf(pluginEndpoints), renderSignature);
    }

    private void setPluginEndpoints(List<QuantumPluginEndpoint> endpoints) {
        List<QuantumPluginEndpoint> next = List.copyOf(endpoints);
        if (pluginEndpoints.equals(next)) return;
        pluginEndpoints = next;
        pluginDirectoryRevision = pluginDirectoryRevision == Integer.MAX_VALUE
                ? 1
                : pluginDirectoryRevision + 1;
    }

    private void setStructureState(int tier, boolean suspended) {
        setStructureState(tier, suspended, structureRenderSignature, false);
    }

    private void setStructureState(int tier, boolean suspended, long renderSignature, boolean ready) {
        tier = Math.min(Math.clamp(tier, 0, 3), coreLevel);
        ready = tier > 0 && !suspended && ready;
        if (structureTier == tier && structureSuspended == suspended
                && structureRenderSignature == renderSignature && structureReady == ready) return;

        structureTier = tier;
        structureSuspended = suspended;
        structureRenderSignature = renderSignature;
        structureReady = ready;
        if (!ready) constraintPowered = false;
        clearStabilizerCache();
        energy.setCapacity(Config.quantumCapacityForTier(tier));
        rebuildRequiredChunkSamples();
        handler.clearWindow();
        setChanged();
        sendBlockUpdate();
    }

    private void writeEnergy(CompoundTag tag) {
        tag.putByteArray(ENERGY_TAG, QuantumEnergyCodec.encode(energy.stored()));
    }

    private void writeCoreProgress(CompoundTag tag) {
        tag.putInt(CORE_LEVEL_TAG, coreLevel);
        writeTotalIoEnergy(tag);
    }

    private void writeTotalIoEnergy(CompoundTag tag) {
        tag.putByteArray(TOTAL_IO_ENERGY_TAG, QuantumEnergyCodec.encode(totalIoEnergy));
    }

    private boolean areRequiredChunksLoaded() {
        Level level = getLevel();
        if (level == null) return false;
        for (BlockPos sample : requiredChunkSamples) {
            if (!level.isLoaded(sample)) return false;
        }
        return true;
    }

    private void rebuildRequiredChunkSamples() {
        if (structureTier <= 0) {
            requiredChunkSamples = List.of();
            return;
        }

        Set<Long> chunks = new HashSet<>();
        List<BlockPos> samples = new ArrayList<>();
        for (int tier = 1; tier <= structureTier; tier++) {
            for (QuantumBatteryStructure.Cell cell : QuantumBatteryStructure.layer(tier).cells()) {
                QuantumBatteryStructure.Offset offset = cell.offset();
                BlockPos target = worldPosition.offset(offset.x(), offset.y(), offset.z());
                long chunk = net.minecraft.world.level.ChunkPos.asLong(target);
                if (chunks.add(chunk)) {
                    samples.add(target);
                }
            }
        }
        requiredChunkSamples = List.copyOf(samples);
    }

    private void tickAssembly() {
        if (assemblyJob == null) return;
        QuantumBatteryAssembler.Result result = assemblyJob.tick(this);
        if (result == null) return;

        UUID playerId = assemblyJob.playerId();
        assemblyJob = null;
        if (level instanceof net.minecraft.server.level.ServerLevel serverLevel) {
            ServerPlayer player = serverLevel.getServer().getPlayerList().getPlayer(playerId);
            if (player != null) {
                player.displayClientMessage(QuantumBatteryAssembler.resultMessage(result), true);
            }
        }
    }

    private void tickProcessingPlugins() {
        if (!isOperational() || level == null || level.isClientSide || pluginEndpoints.isEmpty()) return;
        Set<BlockPos> ticked = new HashSet<>();
        for (QuantumPluginEndpoint endpoint : pluginEndpoints) {
            BlockPos pluginPos = endpoint.absolutePos();
            if (!ticked.add(pluginPos) || !level.isLoaded(pluginPos)) continue;
            BlockState state = level.getBlockState(pluginPos);
            if (!isValidPluginState(endpoint.ringTier(), state)) continue;
            if (level.getBlockEntity(pluginPos) instanceof QuantumBatteryProcessingPlugin processing) {
                processing.tickFromQuantumBattery(this);
            }
        }
    }

    private void readEnergy(CompoundTag tag) {
        byte[] encoded = tag.getByteArray(ENERGY_TAG);
        if (encoded.length > QuantumEnergyCodec.MAX_ENERGY_BYTES) {
            FluxOverdrive.LOGGER.warn("Clamping oversized quantum energy value at {} to the persistence limit",
                    worldPosition);
        }
        energy.setStored(QuantumEnergyCodec.decode(encoded));
        energyDisplayDirty = true;
    }

    private void readCoreProgress(CompoundTag tag, int persistedStructureTier) {
        coreLevel = QuantumBatteryState.coreLevelForLoad(tag.contains(CORE_LEVEL_TAG),
                tag.getInt(CORE_LEVEL_TAG), persistedStructureTier);
        readTotalIoEnergy(tag);
    }

    private void readTotalIoEnergy(CompoundTag tag) {
        byte[] encoded = tag.getByteArray(TOTAL_IO_ENERGY_TAG);
        if (encoded.length > QuantumEnergyCodec.MAX_ENERGY_BYTES) {
            FluxOverdrive.LOGGER.warn("Clamping oversized quantum battery total IO at {} to the persistence limit",
                    worldPosition);
        }
        totalIoEnergy = QuantumEnergyCodec.decode(encoded);
    }

    private void readBatteryId(CompoundTag tag) {
        if (tag.hasUUID(BATTERY_ID_TAG)) batteryId = tag.getUUID(BATTERY_ID_TAG);
    }

    private void writeStability(CompoundTag tag) {
        tag.putInt(STABILITY_BASIS_POINTS_TAG, stabilityGauge.basisPoints());
        tag.putInt(UNPOWERED_TICKS_TAG, stabilityGauge.unpoweredTickRemainder());
        tag.putInt(POWERED_TICKS_TAG, stabilityGauge.poweredTickRemainder());
    }

    private void readStability(CompoundTag tag) {
        int basisPoints = tag.contains(STABILITY_BASIS_POINTS_TAG)
                ? tag.getInt(STABILITY_BASIS_POINTS_TAG)
                : QuantumStabilityGauge.MAX_BASIS_POINTS;
        stabilityGauge.load(basisPoints, tag.getInt(UNPOWERED_TICKS_TAG), tag.getInt(POWERED_TICKS_TAG));
    }

    private void writeTemperature(CompoundTag tag) {
        tag.putDouble(TEMPERATURE_TAG, temperatureKelvin);
    }

    private void readTemperature(CompoundTag tag) {
        temperatureKelvin = tag.contains(TEMPERATURE_TAG)
                ? QuantumBatteryThermalModel.clampTemperature(tag.getDouble(TEMPERATURE_TAG))
                : QuantumBatteryThermalModel.ROOM_TEMPERATURE_K;
    }

    private void settleThermalForCurrentTick() {
        if (!cacheCoolingSources()) {
            clearCoolingStatus();
            return;
        }
        QuantumBatteryCoolingModel.Result result = QuantumBatteryCoolingModel.settle(
                QuantumBatteryCoolingModel.milliKelvin(temperatureKelvin),
                QuantumBatteryCoolingModel.milliKelvin(QuantumBatteryThermalModel.heatingPerTick(structureTier)),
                QuantumBatteryCoolingModel.milliKelvin(QuantumBatteryThermalModel.MAX_TEMPERATURE_K),
                coolingSources,
                coolingCursor);
        coolingCursor = result.nextRoundRobinCursor();
        effectiveCoolingTargetMilliKelvin = result.effectiveTargetMilliKelvin();
        coolingActive = result.active();
        setTemperatureKelvin(QuantumBatteryCoolingModel.kelvin(result.temperatureMilliKelvin()));
    }

    private void clearCoolingStatus() {
        effectiveCoolingTargetMilliKelvin = QuantumBatteryCoolingModel.NO_TARGET;
        coolingActive = false;
    }

    public int receiveConstraintEnergy(int maxReceive, boolean simulate) {
        if (maxReceive <= 0 || !isOperational() || !cacheStabilizerEntities()) return 0;

        List<TileQuantumEnergyConstraintInterface> stabilizers = stabilizerEntities;

        int accepted = 0;
        int remaining = maxReceive;
        int start = stabilizers.isEmpty() ? 0 : Math.floorMod(entanglementCursor, stabilizers.size());
        for (int offset = 0; offset < stabilizers.size(); offset++) {
            TileQuantumEnergyConstraintInterface stabilizer = stabilizers.get((start + offset) % stabilizers.size());
            int received = stabilizer.receiveStabilizationEnergy(remaining, simulate);
            accepted += received;
            remaining -= received;
            if (remaining <= 0) break;
        }
        if (!simulate && accepted > 0 && !stabilizers.isEmpty()) {
            entanglementCursor = (start + 1) % stabilizers.size();
        }
        return accepted;
    }

    private void updateConstraintStability() {
        if (!isOperational()) {
            setConstraintPowered(false, false);
            return;
        }

        if (!cacheStabilizerEntities()) return;

        List<TileQuantumEnergyConstraintInterface> stabilizers = stabilizerEntities;
        if (stabilizerEnergyScratch.length != stabilizers.size()) {
            stabilizerEnergyScratch = new long[stabilizers.size()];
        }
        BigInteger requiredEnergy = BigInteger.ZERO;
        for (int tier = 1; tier <= structureTier; tier++) {
            if (stabilizerTopology.get(tier - 1).isEmpty()) {
                invalidateStabilizerTopology();
                return;
            }
            requiredEnergy = requiredEnergy.add(BigInteger.valueOf(
                     Math.max(0, Config.quantumStabilizationCostForTier(tier))));
        }

        for (int index = 0; index < stabilizers.size(); index++) {
            stabilizerEnergyScratch[index] = stabilizers.get(index).getEnergy();
        }

        requiredEnergy = QuantumBatteryThermalModel.scaleConstraintCost(requiredEnergy, temperatureKelvin);

        Optional<long[]> plan = QuantumStabilizationPlan.create(stabilizerEnergyScratch, requiredEnergy);
        if (plan.isEmpty()) {
            setConstraintPowered(false, true);
            return;
        }
        long[] extractions = plan.orElseThrow();
        for (int index = 0; index < stabilizers.size(); index++) {
            stabilizers.get(index).extractStabilizationEnergy(extractions[index]);
        }
        setConstraintPowered(true, true);
    }

    private boolean cacheStabilizerEntities() {
        if (stabilizerEntitiesCached) return true;
        Level currentLevel = getLevel();
        if (currentLevel == null || currentLevel.isClientSide || structureTier <= 0
                || stabilizerTopology.size() < structureTier) {
            return false;
        }

        List<TileQuantumEnergyConstraintInterface> entities = new ArrayList<>();
        for (int tier = 1; tier <= structureTier; tier++) {
            List<StabilizerSlot> ring = stabilizerTopology.get(tier - 1);
            if (ring.isEmpty()) {
                invalidateStabilizerTopology();
                return false;
            }
            for (StabilizerSlot slot : ring) {
                if (!(currentLevel.getBlockEntity(slot.pos())
                        instanceof TileQuantumEnergyConstraintInterface stabilizer)
                        || !isValidStabilizerState(slot, currentLevel.getBlockState(slot.pos()))) {
                    invalidateStabilizerTopology();
                    return false;
                }
                entities.add(stabilizer);
            }
        }
        if (entities.isEmpty()) {
            invalidateStabilizerTopology();
            return false;
        }
        stabilizerEntities = List.copyOf(entities);
        stabilizerEntitiesCached = true;
        return true;
    }

    private boolean cacheCoolingSources() {
        Level currentLevel = getLevel();
        if (currentLevel == null || currentLevel.isClientSide || structureTier <= 0
                || coolingTopology.size() < structureTier) {
            return false;
        }
        if (coolingSourcesCached) return validateCachedCoolingSources(currentLevel);

        List<QuantumBatteryCoolingSource> sources = new ArrayList<>();
        for (int tier = 1; tier <= structureTier; tier++) {
            for (CoolingSlot slot : coolingTopology.get(tier - 1)) {
                BlockEntity blockEntity = currentLevel.getBlockEntity(slot.pos());
                if (!(blockEntity instanceof QuantumBatteryCoolingSource source)
                        || blockEntity.isRemoved()
                        || !isValidPluginState(slot.ringTier(), currentLevel.getBlockState(slot.pos()))) {
                    invalidateStabilizerTopology();
                    return false;
                }
                sources.add(source);
            }
        }
        coolingSources = List.copyOf(sources);
        coolingSourcesCached = true;
        return true;
    }

    private boolean validateCachedCoolingSources(Level currentLevel) {
        int sourceIndex = 0;
        for (int tier = 1; tier <= structureTier; tier++) {
            for (CoolingSlot slot : coolingTopology.get(tier - 1)) {
                BlockEntity blockEntity = currentLevel.getBlockEntity(slot.pos());
                if (sourceIndex >= coolingSources.size()
                        || !(blockEntity instanceof QuantumBatteryCoolingSource source)
                        || blockEntity.isRemoved()
                        || source != coolingSources.get(sourceIndex)
                        || !isValidPluginState(slot.ringTier(), currentLevel.getBlockState(slot.pos()))) {
                    invalidateStabilizerTopology();
                    return false;
                }
                sourceIndex++;
            }
        }
        if (sourceIndex != coolingSources.size()) {
            invalidateStabilizerTopology();
            return false;
        }
        return true;
    }

    private void clearStabilizerCache() {
        stabilizerTopology = List.of();
        coolingTopology = List.of();
        stabilizerEntities = List.of();
        coolingSources = List.of();
        stabilizerEntitiesCached = false;
        coolingSourcesCached = false;
        clearCoolingStatus();
    }

    private void invalidateStabilizerTopology() {
        clearStabilizerCache();
        structureDirty = true;
        setStructureReady(false);
    }

    private static boolean isValidStabilizerState(StabilizerSlot slot, BlockState state) {
        return isValidPluginState(slot.ringTier(), state);
    }

    private static boolean isValidPluginState(int ringTier, BlockState state) {
        return state.getBlock() instanceof QuantumBatteryPlugin plugin
                && plugin.canInstallInRing(ringTier);
    }

    private void setStructureReady(boolean ready) {
        ready = structureTier > 0 && !structureSuspended && ready;
        if (structureReady == ready) {
            if (!ready) setConstraintPowered(false, false);
            return;
        }
        structureReady = ready;
        if (!ready) {
            constraintPowered = false;
            handler.clearWindow();
        }
        setChanged();
        sendBlockUpdate();
    }

    private void setConstraintPowered(boolean powered, boolean adjustStability) {
        boolean poweredChanged = constraintPowered != powered;
        int previousBasisPoints = stabilityGauge.basisPoints();
        int previousUnpoweredTicks = stabilityGauge.unpoweredTickRemainder();
        int previousPoweredTicks = stabilityGauge.poweredTickRemainder();
        if (adjustStability) stabilityGauge.recordConstraintPower(powered);
        boolean stabilityChanged = stabilityGauge.basisPoints() != previousBasisPoints;
        boolean persistentStateChanged = stabilityChanged
                || stabilityGauge.unpoweredTickRemainder() != previousUnpoweredTicks
                || stabilityGauge.poweredTickRemainder() != previousPoweredTicks;
        constraintPowered = powered;
        if (persistentStateChanged) setChanged();
        if (poweredChanged || stabilityChanged) syncRenderStateIfNeeded(true);
    }

    private void markClientStructureRenderDirty(int maximumTier) {
        if (level == null) return;
        Set<Long> dirtySections = new HashSet<>();
        if (level.isLoaded(worldPosition)) {
            dirtySections.add(SectionPos.asLong(worldPosition));
            BlockState controllerState = level.getBlockState(worldPosition);
            level.sendBlockUpdated(worldPosition, controllerState, controllerState, Block.UPDATE_ALL);
        }
        for (int tier = 1; tier <= maximumTier; tier++) {
            for (QuantumBatteryStructure.Cell cell : QuantumBatteryStructure.layer(tier).cells()) {
                BlockPos target = worldPosition.offset(cell.offset().x(), cell.offset().y(), cell.offset().z());
                if (!level.isLoaded(target)) continue;
                if (!dirtySections.add(SectionPos.asLong(target))) continue;
                BlockState state = level.getBlockState(target);
                level.sendBlockUpdated(target, state, state, Block.UPDATE_ALL);
            }
        }
    }

    private static void registerController(Level level, BlockPos pos) {
        LOADED_CONTROLLERS.computeIfAbsent(level, ignored -> new ConcurrentHashMap<>())
                .computeIfAbsent(chunkKey(pos), ignored -> ConcurrentHashMap.newKeySet())
                .add(pos.immutable());
    }

    private void unregisterController() {
        if (level == null) return;
        int previousClientTier = 0;
        if (level.isClientSide && clientIndexLevel == level) {
            previousClientTier = CLIENT_ACTIVE_CONTROLLER_TIERS.getOrDefault(worldPosition.asLong(), 0);
            CLIENT_ACTIVE_CONTROLLER_TIERS.remove(worldPosition.asLong());
            if (previousClientTier > 0) structureReady = false;
        }
        Map<Long, Set<BlockPos>> chunks = LOADED_CONTROLLERS.get(level);
        if (chunks != null) {
            long key = chunkKey(worldPosition);
            Set<BlockPos> positions = chunks.get(key);
            if (positions != null) {
                positions.remove(worldPosition);
                if (positions.isEmpty()) chunks.remove(key);
            }
            if (chunks.isEmpty()) LOADED_CONTROLLERS.remove(level);
        }
        if (previousClientTier > 0) markClientStructureRenderDirty(previousClientTier);
    }

    private void updateClientAnimationState() {
        if (level == null || !level.isClientSide) return;
        if (clientIndexLevel != level) {
            CLIENT_ACTIVE_CONTROLLER_TIERS.clear();
            clientIndexLevel = level;
        }
        int activeTier = clientAnimationTier();
        if (activeTier > 0) {
            CLIENT_ACTIVE_CONTROLLER_TIERS.put(worldPosition.asLong(), activeTier);
        } else {
            CLIENT_ACTIVE_CONTROLLER_TIERS.remove(worldPosition.asLong());
        }
    }

    private int clientAnimationTier() {
        return QuantumBatteryState.animationTier(structureTier, structureSuspended, structureReady);
    }

    private void scheduleNextValidation(long gameTime) {
        long phase = Math.floorMod(worldPosition.asLong(), VALIDATION_INTERVAL);
        long delay = Math.floorMod(phase - Math.floorMod(gameTime, VALIDATION_INTERVAL),
                VALIDATION_INTERVAL);
        nextValidationGameTime = gameTime + (delay == 0 ? VALIDATION_INTERVAL : delay);
    }

    private static long chunkKey(BlockPos pos) {
        return chunkKey(Math.floorDiv(pos.getX(), 16), Math.floorDiv(pos.getZ(), 16));
    }

    private static long chunkKey(int chunkX, int chunkZ) {
        return (chunkX & 0xffffffffL) | ((chunkZ & 0xffffffffL) << 32);
    }

    private enum LayerStatus {
        VALID,
        INVALID,
        UNLOADED
    }

    private record LayerValidation(LayerStatus status, List<StabilizerSlot> stabilizers,
                                   List<CoolingSlot> coolingSlots,
                                   List<QuantumPluginEndpoint> pluginEndpoints,
                                   long renderSignature) {}

    private record StabilizerSlot(BlockPos pos, int ringTier) {}

    private record CoolingSlot(BlockPos pos, int ringTier) {}
}
