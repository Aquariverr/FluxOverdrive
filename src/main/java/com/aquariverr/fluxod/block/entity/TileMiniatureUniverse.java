package com.aquariverr.fluxod.block.entity;

import com.aquariverr.fluxod.entanglement.QuantumPluginEndpointKey;
import com.aquariverr.fluxod.miniature.model.GridPosition;
import com.aquariverr.fluxod.miniature.model.FactoryLayout;
import com.aquariverr.fluxod.miniature.compile.CompilationResult;
import com.aquariverr.fluxod.miniature.recipe.MiniatureRecipeCatalog;
import com.aquariverr.fluxod.miniature.model.ResourceKey;
import com.aquariverr.fluxod.miniature.storage.FactoryState;
import com.aquariverr.fluxod.miniature.storage.FactoryStorage;
import com.aquariverr.fluxod.miniature.model.FactoryRules;
import com.aquariverr.fluxod.miniature.runtime.FactoryRunner;
import com.aquariverr.fluxod.miniature.runtime.RuntimePreview;
import com.aquariverr.fluxod.miniature.storage.FactoryItemHandler;
import com.aquariverr.fluxod.integration.mekanism.QuantumCoolingChemicalIntegration;
import com.aquariverr.fluxod.multiblock.QuantumBatteryProcessingPlugin;
import com.aquariverr.fluxod.menu.MiniatureUniverseMenu;
import com.aquariverr.fluxod.multiblock.QuantumBatteryStructure;
import com.aquariverr.fluxod.register.FluxOdDataComponents;
import com.aquariverr.fluxod.register.RegistryBlockEntityTypes;
import com.aquariverr.fluxod.util.LoadedBlockAccess;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import java.util.UUID;

public final class TileMiniatureUniverse extends BlockEntity implements QuantumBatteryProcessingPlugin {

    public static final String STATE_TAG = "FactoryState";

    private FactoryState factoryState = FactoryState.fresh();
    private FactoryLayout compiledLayout;
    private long compiledGeneration;
    private CompilationResult compilation;
    private long lastProcessedGameTime = Long.MIN_VALUE;
    private TileQuantumBatteryController lastController;
    private FactoryRunner.Status runtimeStatus = FactoryRunner.Status.DISABLED;
    private final FactoryItemHandler itemHandler = new FactoryItemHandler(this);
    private final com.aquariverr.fluxod.miniature.storage.FactoryFluidHandler fluidHandler =
            new com.aquariverr.fluxod.miniature.storage.FactoryFluidHandler(this);
    private final Object chemicalHandler = QuantumCoolingChemicalIntegration.createMiniatureHandler(this);

    public TileMiniatureUniverse(BlockPos pos, BlockState state) {
        super(RegistryBlockEntityTypes.MINIATURE_UNIVERSE.get(), pos, state);
    }

    public FactoryState factoryState() {
        return factoryState;
    }

    public FactoryItemHandler itemHandler() { return itemHandler; }
    public com.aquariverr.fluxod.miniature.storage.FactoryFluidHandler fluidHandler() { return fluidHandler; }
    public Object chemicalHandler() { return chemicalHandler; }

    private boolean acceptsController(TileQuantumBatteryController controller) {
        return level != null && !level.isClientSide && !isRemoved() && controller != null && !controller.isRemoved()
                && controller.getLevel() == level && controller.isOperational() && controller.getStructureTier() == 3
                && LoadedBlockAccess.isCurrent(this) && LoadedBlockAccess.isCurrent(controller)
                && controller.areStructureChunksLoadedForRender()
                && controller.getEntanglementEndpoints().stream().anyMatch(endpoint -> endpoint.ringTier() == 3
                && endpoint.absolutePos().equals(worldPosition));
    }

    @Override
    public void tickFromQuantumBattery(TileQuantumBatteryController controller) {
        if (!acceptsController(controller) || !level.getServer().isSameThread() || factoryState.storage().isEmpty()) return;
        lastController = controller;
        long now = level.getGameTime();
        if (lastProcessedGameTime == now) return;
        lastProcessedGameTime = now;
        FactoryStorage storage = factoryState.storage().orElseThrow();
        if (!storage.enabled()) { runtimeStatus = FactoryRunner.Status.DISABLED; return; }
        if (!storage.redstoneMode().permits(level.hasNeighborSignal(worldPosition))) { runtimeStatus = FactoryRunner.Status.REDSTONE; return; }
        FactoryRules.Environment environment = environment(controller);
        CompilationResult compiled = compilationFor(factoryState);
        try {
            FactoryRunner.Step step = FactoryRunner.prepare(storage, compiled != null && compiled.successful() ? compiled.factory() : null,
                    MiniatureRecipeCatalog.get(level.getServer()).generation(), environment, now);
            runtimeStatus = step.status();
            if (step.storage() == storage) return;
            // Both persistent state and eventual output commit must fit before the first irreversible debit.
            FactoryState prepared = FactoryState.of(step.storage(), level.registryAccess());
            if (!QuantumBatteryPluginEnergyAccess.tryConsume(controller, step.charge(), false)) {
                runtimeStatus = FactoryRunner.Status.NO_ENERGY;
                return;
            }
            factoryState = prepared;
            setChanged();
        } catch (IllegalArgumentException exception) {
            runtimeStatus = FactoryRunner.Status.STATE_TOO_LARGE;
        }
    }

    private static FactoryRules.Environment environment(TileQuantumBatteryController controller) {
        return controller != null && Double.isFinite(controller.getTemperatureKelvin())
                ? FactoryRules.environment(controller.getStabilityBasisPoints(), controller.getTemperatureKelvin()) : null;
    }

    public RuntimePreview runtimePreview() {
        return runtimePreview(factoryState);
    }

    public RuntimePreview runtimePreview(FactoryState state) {
        if (state.storage().isEmpty()) return RuntimePreview.NONE;
        FactoryStorage storage = state.storage().orElseThrow();
        FactoryRunner.Status status = runtimeStatus;
        if (!storage.enabled()) status = FactoryRunner.Status.DISABLED;
        else if (!storage.redstoneMode().permits(level != null && LoadedBlockAccess.signals(level).hasNeighborSignal(worldPosition))) status = FactoryRunner.Status.REDSTONE;
        else if (!acceptsController(lastController)) status = FactoryRunner.Status.NO_BATTERY;
        else if (storage.batch() == null && compilationFor(state) == null) status = FactoryRunner.Status.NOT_COMPILED;
        FactoryRules.Environment environment = acceptsController(lastController) ? environment(lastController) : null;
        return RuntimePreview.from(storage, status, environment == null ? 0 : environment.parallelism());
    }

    public CompilationResult compilationFor(FactoryState state) {
        if (level == null || level.isClientSide || isRemoved() || state.storage().isEmpty()) return null;
        long generation = MiniatureRecipeCatalog.get(level.getServer()).generation();
        return compiledGeneration == generation && state.storage().orElseThrow().layout().equals(compiledLayout) ? compilation : null;
    }

    public boolean applyCompilation(FactoryState expected, CompilationResult result, long generation) {
        FactoryStorage storage = writableStorage();
        if (storage.batch() != null) return false;
        if (!level.getServer().isSameThread() || factoryState != expected
                || MiniatureRecipeCatalog.get(level.getServer()).generation() != generation) return false;
        if (result.successful() && !result.factory().matches(storage.layout(), generation)) return false;
        compiledLayout = storage.layout();
        compiledGeneration = generation;
        compilation = java.util.Objects.requireNonNull(result);
        return true;
    }

    public boolean applyBalancedState(FactoryState expected, FactoryState prepared, CompilationResult result, long generation) {
        if (level == null || level.isClientSide || !level.getServer().isSameThread() || factoryState != expected || prepared.storage().isEmpty()
                || expected.storage().isEmpty() || expected.storage().orElseThrow().batch() != null
                || MiniatureRecipeCatalog.get(level.getServer()).generation() != generation || !result.successful()
                || !result.factory().matches(prepared.storage().orElseThrow().layout(), generation)) return false;
        if (!applyPreparedState(expected, prepared)) return false;
        compiledLayout = prepared.storage().orElseThrow().layout();
        compiledGeneration = generation;
        compilation = result;
        return true;
    }

    public Component statusMessage() {
        return Component.translatable(factoryState.status().translationKey());
    }

    public boolean canPlayerConfigure(Player player) {
        if (player == null || player.isSpectator() || level == null || player.level() != level) return false;
        for (QuantumBatteryStructure.Cell slot : QuantumBatteryStructure.stabilizerSlots(3)) {
            var offset = slot.offset();
            BlockPos controllerPos = worldPosition.offset(-offset.x(), -offset.y(), -offset.z());
            if (level.isLoaded(controllerPos)
                    && level.getBlockEntity(controllerPos) instanceof TileQuantumBatteryController controller
                    && controller.isOperational()
                    && controller.getEntanglementEndpoints().stream().anyMatch(endpoint -> endpoint.absolutePos().equals(worldPosition))
                    && !controller.canPlayerManage(player)) return false;
        }
        return true;
    }

    public void openMenu(Player player) {
        if (!(player instanceof ServerPlayer serverPlayer) || !canPlayerConfigure(player) || isRemoved()
                || !level.hasChunkAt(worldPosition) || level.getBlockEntity(worldPosition) != this
                || player.distanceToSqr(worldPosition.getCenter()) > 64) return;
        UUID sessionId = UUID.randomUUID();
        serverPlayer.openMenu(new SimpleMenuProvider(
                (id, inventory, ignored) -> new MiniatureUniverseMenu(id, inventory, this, sessionId),
                Component.translatable("screen.flux_overdrive.miniature_universe.title")),
                buffer -> MiniatureUniverseMenu.writeOpeningData(buffer, worldPosition, sessionId));
    }

    public boolean openMenuFromEntanglement(ServerPlayer player,
                                            TileQuantumEntanglementController accessController,
                                            QuantumPluginEndpointKey endpointKey) {
        if (!player.server.isSameThread() || !player.isAlive() || player.isSpectator()
                || !(level instanceof ServerLevel sourceLevel) || isRemoved()
                || player.server.getLevel(sourceLevel.dimension()) != sourceLevel
                || !LoadedBlockAccess.isCurrent(this)
                || accessController == null || accessController.isRemoved() || accessController.getLevel() != player.level()
                || !LoadedBlockAccess.isCurrent(accessController)
                || endpointKey == null || !accessController.canProvideRemotePluginAccess(player, this, endpointKey)) return false;
        UUID sessionId = UUID.randomUUID();
        return player.openMenu(new SimpleMenuProvider((id, inventory, ignored) -> {
            MiniatureUniverseMenu menu = new MiniatureUniverseMenu(id, inventory, this, sessionId, accessController, endpointKey);
            return menu.stillValid(player) ? menu : null;
        }, Component.translatable("screen.flux_overdrive.miniature_universe.title")), buffer ->
                MiniatureUniverseMenu.writeRemoteOpeningData(buffer, this, sessionId, accessController, endpointKey)).isPresent();
    }

    public boolean applyPreparedState(FactoryState expected, FactoryState prepared) {
        writableStorage();
        if (!level.getServer().isSameThread() || factoryState != expected || prepared.storage().isEmpty()) return false;
        var current = expected.storage().orElseThrow();
        var updated = prepared.storage().orElseThrow();
        if (!java.util.Objects.equals(current.batch(), updated.batch()) || current.lastPaidGameTime() != updated.lastPaidGameTime()
                || current.batch() != null && !current.layout().equals(updated.layout())) return false;
        if (expected != prepared) {
            // FactoryState is already encoded. A changed layout revision invalidates any compiled layout reference.
            factoryState = prepared;
            setChanged();
        }
        return true;
    }

    public ItemStack insertMachine(GridPosition position, ItemStack offered, boolean simulate) {
        FactoryStorage current = writableStorage();
        FactoryStorage.Insertion transfer = current.insertMachine(position, offered, level.registryAccess());
        commit(current, transfer.storage(), simulate);
        return transfer.remainder();
    }

    public ItemStack extractMachine(GridPosition position, int requested, boolean simulate) {
        FactoryStorage current = writableStorage();
        FactoryStorage.Extraction transfer = current.extractMachine(position, requested, level.registryAccess());
        commit(current, transfer.storage(), simulate);
        return transfer.extracted();
    }

    public ItemStack insertInput(ItemStack offered, boolean simulate) {
        FactoryStorage current = writableStorage();
        FactoryStorage.Insertion transfer = current.insertInput(offered, level.registryAccess());
        commit(current, transfer.storage(), simulate);
        return transfer.remainder();
    }

    public ItemStack extractInput(ResourceKey resource, int requested, boolean simulate) {
        FactoryStorage current = writableStorage();
        FactoryStorage.Extraction transfer = current.extractInput(resource, requested, level.registryAccess());
        commit(current, transfer.storage(), simulate);
        return transfer.extracted();
    }

    public ItemStack extractOutput(ResourceKey resource, int requested, boolean simulate) {
        FactoryStorage current = writableStorage();
        FactoryStorage.Extraction transfer = current.extractOutput(resource, requested, level.registryAccess());
        commit(current, transfer.storage(), simulate);
        return transfer.extracted();
    }

    public void insertFluid(net.neoforged.neoforge.fluids.FluidStack offered, boolean simulate) {
        FactoryStorage current = writableStorage();
        FactoryStorage updated = current.insertFluid(offered, level.registryAccess());
        commit(current, updated, simulate);
    }

    public void extractFluid(ResourceKey resource, int requested, boolean simulate) {
        FactoryStorage current = writableStorage();
        FactoryStorage.FluidExtraction transfer = current.extractFluid(resource, requested);
        commit(current, transfer.storage(), simulate);
    }

    public void insertChemical(ResourceKey resource, long amount, boolean simulate) {
        FactoryStorage current = writableStorage();
        FactoryStorage updated = current.insertChemical(resource, java.math.BigInteger.valueOf(amount));
        commit(current, updated, simulate);
    }

    public void extractChemical(ResourceKey resource, long requested, boolean simulate) {
        FactoryStorage current = writableStorage();
        FactoryStorage.FluidExtraction transfer = current.extractChemical(resource, requested);
        commit(current, transfer.storage(), simulate);
    }

    private FactoryStorage writableStorage() {
        if (level == null || level.isClientSide || isRemoved()) {
            throw new IllegalStateException("Only a loaded server factory can transfer assets");
        }
        return factoryState.storage().orElseThrow(() -> new IllegalStateException("Factory state is not readable"));
    }

    private void commit(FactoryStorage current, FactoryStorage updated, boolean simulate) {
        if (current == updated) return;
        // Encoding is part of preparation: failure cannot leave transferred assets with unsavable state.
        FactoryState prepared = FactoryState.of(updated, level.registryAccess());
        if (!simulate) {
            factoryState = prepared;
            setChanged();
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.put(STATE_TAG, factoryState.save());
    }

    @Override
    public void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        compiledLayout = null;
        compilation = null;
        factoryState = FactoryState.rejectUnsupportedChemicals(FactoryState.load(tag.contains(STATE_TAG, Tag.TAG_COMPOUND)
                ? tag.getCompound(STATE_TAG) : tag, registries));
    }

    @Override
    protected void collectImplicitComponents(DataComponentMap.Builder builder) {
        super.collectImplicitComponents(builder);
        builder.set(FluxOdDataComponents.MINIATURE_UNIVERSE_STATE, CustomData.of(factoryState.save()));
    }

    @Override
    protected void applyImplicitComponents(DataComponentInput input) {
        super.applyImplicitComponents(input);
        CustomData saved = input.get(FluxOdDataComponents.MINIATURE_UNIVERSE_STATE);
        if (saved != null) {
            compiledLayout = null;
            compilation = null;
            factoryState = FactoryState.rejectUnsupportedChemicals(
                    FactoryState.load(saved.copyTag(), level.registryAccess()));
        }
    }

    @Override
    public void removeComponentsFromTag(CompoundTag tag) {
        super.removeComponentsFromTag(tag);
        tag.remove(STATE_TAG);
    }
}
