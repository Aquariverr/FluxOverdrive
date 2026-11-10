package com.aquariverr.fluxod.block.entity;

import com.aquariverr.fluxod.block.QuantumFeedbackPluginBlock;
import com.aquariverr.fluxod.register.RegistryBlockEntityTypes;
import com.aquariverr.fluxod.register.FluxOdDataComponents;
import com.aquariverr.fluxod.entanglement.QuantumPluginEndpointKey;
import com.aquariverr.fluxod.menu.QuantumFeedbackPluginMenu;
import com.aquariverr.fluxod.multiblock.QuantumBatteryStructure;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.block.state.BlockState;

import javax.annotation.Nonnull;
import org.jetbrains.annotations.NotNull;

public final class TileQuantumFeedbackPlugin extends BlockEntity {

    private static final String CURVE_TAG = "Curve";
    private static final int DEFAULT_NETWORK_COLOR_RGB = 0x43D9FF;

    private final QuantumFeedbackMetric metric;
    private QuantumFeedbackCurve curve;
    private boolean sourceAvailable;
    private int sampledValue;
    private int redstoneSignal;
    private int networkColorRgb = DEFAULT_NETWORK_COLOR_RGB;

    public TileQuantumFeedbackPlugin(BlockPos pos, BlockState state) {
        super(RegistryBlockEntityTypes.QUANTUM_FEEDBACK_PLUGIN.get(), pos, state);
        metric = state.getBlock() instanceof QuantumFeedbackPluginBlock block
                ? block.metric()
                : QuantumFeedbackMetric.STABILITY;
        curve = metric.defaultCurve();
    }

    public void serverTick() {
        if (level == null || level.isClientSide || isRemoved()) return;
        TileQuantumBatteryController controller = findUniqueController();
        if (controller == null) {
            applySample(false, 0);
            return;
        }
        int value = metric == QuantumFeedbackMetric.STABILITY
                ? controller.getStabilityBasisPoints()
                : controller.getTemperatureCentiKelvin();
        networkColorRgb = controller.getNetworkColorRgb();
        applySample(true, value);
    }

    public QuantumFeedbackMetric getMetric() {
        return metric;
    }

    public QuantumFeedbackCurve getCurve() {
        return curve;
    }

    public boolean isSourceAvailable() {
        return sourceAvailable;
    }

    public int getSampledValue() {
        return sampledValue;
    }

    public int getRedstoneSignal() {
        return redstoneSignal;
    }

    public int getNetworkColorRgb() {
        return networkColorRgb;
    }

    public boolean canPlayerConfigure(Player player) {
        if (player == null || player.isSpectator()) return false;
        TileQuantumBatteryController controller = findUniqueController();
        return controller == null || controller.canPlayerManage(player);
    }

    public void openMenu(Player player) {
        if (!(player instanceof ServerPlayer serverPlayer) || !canPlayerConfigure(player)) return;
        SimpleMenuProvider provider = new SimpleMenuProvider(
                (containerId, inventory, ignored) ->
                        new QuantumFeedbackPluginMenu(containerId, inventory, this),
                title());
        serverPlayer.openMenu(provider,
                buffer -> QuantumFeedbackPluginMenu.writeOpeningData(buffer, this));
    }

    public void openMenuFromEntanglement(ServerPlayer player,
                                         TileQuantumEntanglementController accessController,
                                         QuantumPluginEndpointKey endpointKey) {
        if (player.isSpectator()
                || !accessController.canProvideRemotePluginAccess(player, this, endpointKey)) {
            return;
        }
        SimpleMenuProvider provider = new SimpleMenuProvider(
                (containerId, inventory, ignored) -> new QuantumFeedbackPluginMenu(
                        containerId, inventory, this, accessController, endpointKey),
                title());
        player.openMenu(provider, buffer -> QuantumFeedbackPluginMenu.writeRemoteOpeningData(
                buffer, this, accessController, endpointKey));
    }

    public void setCurve(QuantumFeedbackCurve curve) {
        if (curve.metric() != metric) throw new IllegalArgumentException("Feedback metric mismatch");
        if (this.curve.equals(curve)) return;
        this.curve = curve;
        setChanged();
        updateSignal(sourceAvailable ? curve.evaluate(sampledValue) : 0);
    }

    private TileQuantumBatteryController findUniqueController() {
        TileQuantumBatteryController found = null;
        for (int tier = 1; tier <= 3; tier++) {
            for (QuantumBatteryStructure.Cell slot : QuantumBatteryStructure.stabilizerSlots(tier)) {
                QuantumBatteryStructure.Offset offset = slot.offset();
                BlockPos controllerPos = worldPosition.offset(-offset.x(), -offset.y(), -offset.z());
                if (!level.isLoaded(controllerPos)
                        || !(level.getBlockEntity(controllerPos) instanceof TileQuantumBatteryController controller)
                        || !controller.isOperational()) {
                    continue;
                }
                boolean member = controller.getEntanglementEndpoints().stream()
                        .anyMatch(endpoint -> endpoint.absolutePos().equals(worldPosition));
                if (!member) continue;
                if (found != null && found != controller) return null;
                found = controller;
            }
        }
        return found;
    }

    private void applySample(boolean available, int value) {
        sourceAvailable = available;
        sampledValue = available ? value : 0;
        updateSignal(available ? curve.evaluate(value) : 0);
    }

    private void updateSignal(int signal) {
        int clamped = Math.clamp(signal, QuantumFeedbackCurve.MIN_SIGNAL, QuantumFeedbackCurve.MAX_SIGNAL);
        if (redstoneSignal == clamped) return;
        redstoneSignal = clamped;
        if (level != null) level.updateNeighborsAt(worldPosition, getBlockState().getBlock());
    }

    @Override
    public void onChunkUnloaded() {
        sourceAvailable = false;
        sampledValue = 0;
        updateSignal(0);
        super.onChunkUnloaded();
    }

    @Override
    public void setRemoved() {
        sourceAvailable = false;
        sampledValue = 0;
        updateSignal(0);
        super.setRemoved();
    }

    @Override
    protected void collectImplicitComponents(DataComponentMap.@NotNull Builder builder) {
        super.collectImplicitComponents(builder);
        builder.set(FluxOdDataComponents.QUANTUM_FEEDBACK_CURVE,
                CustomData.of(QuantumFeedbackCurveCodec.save(curve)));
    }

    @Override
    protected void applyImplicitComponents(BlockEntity.@NotNull DataComponentInput input) {
        super.applyImplicitComponents(input);
        CustomData data = input.get(FluxOdDataComponents.QUANTUM_FEEDBACK_CURVE);
        if (data != null) {
            curve = QuantumFeedbackCurveCodec.loadOrDefault(metric, data.copyTag());
        }
    }

    private Component title() {
        return Component.translatable(metric == QuantumFeedbackMetric.STABILITY
                ? "screen.flux_overdrive.quantum_feedback.stability_title"
                : "screen.flux_overdrive.quantum_feedback.temperature_title");
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.put(CURVE_TAG, QuantumFeedbackCurveCodec.save(curve));
    }

    @Override
    public void loadAdditional(@Nonnull CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        curve = tag.contains(CURVE_TAG)
                ? QuantumFeedbackCurveCodec.loadOrDefault(metric, tag.getCompound(CURVE_TAG))
                : metric.defaultCurve();
        sourceAvailable = false;
        sampledValue = 0;
        redstoneSignal = 0;
        networkColorRgb = DEFAULT_NETWORK_COLOR_RGB;
    }
}
