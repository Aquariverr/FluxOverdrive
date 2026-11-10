package com.aquariverr.fluxod.menu;

import com.aquariverr.fluxod.block.entity.QuantumFeedbackCurve;
import com.aquariverr.fluxod.block.entity.QuantumFeedbackMetric;
import com.aquariverr.fluxod.block.entity.QuantumFeedbackCurveCodec;
import com.aquariverr.fluxod.block.entity.TileQuantumEntanglementController;
import com.aquariverr.fluxod.block.entity.TileQuantumFeedbackPlugin;
import com.aquariverr.fluxod.entanglement.QuantumPluginEndpointKey;
import com.aquariverr.fluxod.network.FeedbackPluginSnapshotPayload;
import com.aquariverr.fluxod.register.RegistryBlocks;
import com.aquariverr.fluxod.register.RegistryMenuTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

import javax.annotation.Nullable;
import java.util.List;

public final class QuantumFeedbackPluginMenu extends AbstractContainerMenu {

    private static final String METRIC_TAG = "Metric";
    private static final String AVAILABLE_TAG = "Available";
    private static final String SAMPLED_VALUE_TAG = "SampledValue";
    private static final String SIGNAL_TAG = "Signal";
    private static final String NETWORK_COLOR_TAG = "NetworkColor";
    private static final String CURVE_TAG = "Curve";

    private final BlockPos pluginPos;
    private final QuantumFeedbackMetric metric;
    private final Player menuPlayer;
    private final boolean remoteAccess;
    private final BlockPos accessControllerPos;
    @Nullable
    private final TileQuantumEntanglementController accessController;
    @Nullable
    private final QuantumPluginEndpointKey endpointKey;
    @Nullable
    private final TileQuantumFeedbackPlugin plugin;
    private CompoundTag snapshot;
    @Nullable
    private CompoundTag lastSentSnapshot;

    public QuantumFeedbackPluginMenu(int containerId, Inventory inventory, RegistryFriendlyByteBuf buffer) {
        this(containerId, inventory, ClientContext.read(inventory.player, buffer));
    }

    public QuantumFeedbackPluginMenu(int containerId, Inventory inventory,
                                     TileQuantumFeedbackPlugin plugin) {
        this(containerId, inventory, plugin.getBlockPos(), plugin.getMetric(), plugin,
                false, plugin.getBlockPos(), null, null, createSnapshot(plugin));
    }

    public QuantumFeedbackPluginMenu(int containerId, Inventory inventory,
                                     TileQuantumFeedbackPlugin plugin,
                                     TileQuantumEntanglementController accessController,
                                     QuantumPluginEndpointKey endpointKey) {
        this(containerId, inventory, plugin.getBlockPos(), plugin.getMetric(), plugin,
                true, accessController.getBlockPos(), accessController, endpointKey,
                createSnapshot(plugin));
    }

    private QuantumFeedbackPluginMenu(int containerId, Inventory inventory, ClientContext context) {
        this(containerId, inventory, context.pluginPos(), context.metric(), null,
                context.remoteAccess(), context.accessControllerPos(), context.accessController(),
                context.endpointKey(), context.snapshot());
    }

    private QuantumFeedbackPluginMenu(
            int containerId,
            Inventory inventory,
            BlockPos pluginPos,
            QuantumFeedbackMetric metric,
            @Nullable TileQuantumFeedbackPlugin plugin,
            boolean remoteAccess,
            BlockPos accessControllerPos,
            @Nullable TileQuantumEntanglementController accessController,
            @Nullable QuantumPluginEndpointKey endpointKey,
            CompoundTag snapshot) {
        super(RegistryMenuTypes.QUANTUM_FEEDBACK_PLUGIN.get(), containerId);
        this.pluginPos = pluginPos.immutable();
        this.metric = metric;
        this.menuPlayer = inventory.player;
        this.plugin = plugin;
        this.remoteAccess = remoteAccess;
        this.accessControllerPos = accessControllerPos.immutable();
        this.accessController = accessController;
        this.endpointKey = endpointKey;
        this.snapshot = snapshot.copy();
    }

    public BlockPos getPluginPos() {
        return pluginPos;
    }

    public QuantumFeedbackMetric getMetric() {
        return metric;
    }

    public boolean isRemoteAccess() {
        return remoteAccess;
    }

    public boolean isSourceAvailable() {
        return snapshot.getBoolean(AVAILABLE_TAG);
    }

    public int getSampledValue() {
        return snapshot.getInt(SAMPLED_VALUE_TAG);
    }

    public int getSignal() {
        return Math.clamp(snapshot.getInt(SIGNAL_TAG), 0, 15);
    }

    public int getNetworkColorRgb() {
        int color = snapshot.getInt(NETWORK_COLOR_TAG) & 0xFFFFFF;
        return color == 0 ? 0x43D9FF : color;
    }

    public QuantumFeedbackCurve getCurve() {
        return snapshot.contains(CURVE_TAG)
                ? QuantumFeedbackCurveCodec.loadOrDefault(metric, snapshot.getCompound(CURVE_TAG))
                : metric.defaultCurve();
    }

    public void applySnapshot(CompoundTag updatedSnapshot) {
        snapshot = updatedSnapshot.copy();
    }

    public boolean updateCurve(ServerPlayer player, List<QuantumFeedbackCurve.Point> points) {
        if (plugin == null || !stillValid(player)) return false;
        try {
            plugin.setCurve(new QuantumFeedbackCurve(metric, points));
        } catch (RuntimeException ignored) {
            return false;
        }
        snapshot = createSnapshot(plugin);
        lastSentSnapshot = null;
        broadcastChanges();
        return true;
    }

    @Override
    public void broadcastChanges() {
        super.broadcastChanges();
        if (!(menuPlayer instanceof ServerPlayer player) || player.containerMenu != this
                || plugin == null || !stillValid(player)) {
            return;
        }
        CompoundTag current = createSnapshot(plugin);
        snapshot = current.copy();
        if (current.equals(lastSentSnapshot)) return;
        lastSentSnapshot = current.copy();
        PacketDistributor.sendToPlayer(player,
                new FeedbackPluginSnapshotPayload(containerId, current));
    }

    @Override
    public boolean stillValid(Player player) {
        if (remoteAccess) return remoteAccessStillValid(player);
        if (player.isSpectator() || player.distanceToSqr(pluginPos.getX() + 0.5,
                pluginPos.getY() + 0.5, pluginPos.getZ() + 0.5) > 64.0) {
            return false;
        }
        if (!player.level().isClientSide && (plugin == null || plugin.isRemoved()
                || plugin.getLevel() != player.level()
                || player.level().getBlockEntity(pluginPos) != plugin
                || !plugin.canPlayerConfigure(player))) {
            return false;
        }
        return isExpectedBlock(player);
    }

    private boolean remoteAccessStillValid(Player player) {
        if (player.isSpectator()
                || !player.level().getBlockState(accessControllerPos)
                .is(RegistryBlocks.QUANTUM_BATTERY_ENTANGLEMENT_CONTROLLER.get())
                || player.distanceToSqr(accessControllerPos.getX() + 0.5,
                accessControllerPos.getY() + 0.5, accessControllerPos.getZ() + 0.5) > 64.0) {
            return false;
        }
        if (player.level().isClientSide) return true;
        return accessController != null && endpointKey != null && plugin != null
                && accessController.canProvideRemotePluginAccess(player, plugin, endpointKey);
    }

    private boolean isExpectedBlock(Player player) {
        return metric == QuantumFeedbackMetric.STABILITY
                ? player.level().getBlockState(pluginPos)
                .is(RegistryBlocks.QUANTUM_STABILITY_FEEDBACK_PLUGIN.get())
                : player.level().getBlockState(pluginPos)
                .is(RegistryBlocks.QUANTUM_TEMPERATURE_FEEDBACK_PLUGIN.get());
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        return ItemStack.EMPTY;
    }

    public static void writeOpeningData(RegistryFriendlyByteBuf buffer,
                                        TileQuantumFeedbackPlugin plugin) {
        buffer.writeBlockPos(plugin.getBlockPos());
        buffer.writeVarInt(plugin.getMetric().id());
        buffer.writeNbt(createSnapshot(plugin));
        buffer.writeBoolean(false);
    }

    public static void writeRemoteOpeningData(RegistryFriendlyByteBuf buffer,
                                              TileQuantumFeedbackPlugin plugin,
                                              TileQuantumEntanglementController accessController,
                                              QuantumPluginEndpointKey endpointKey) {
        buffer.writeBlockPos(plugin.getBlockPos());
        buffer.writeVarInt(plugin.getMetric().id());
        buffer.writeNbt(createSnapshot(plugin));
        buffer.writeBoolean(true);
        buffer.writeBlockPos(accessController.getBlockPos());
        endpointKey.write(buffer);
    }

    private static CompoundTag createSnapshot(TileQuantumFeedbackPlugin plugin) {
        CompoundTag tag = new CompoundTag();
        tag.putInt(METRIC_TAG, plugin.getMetric().id());
        tag.putBoolean(AVAILABLE_TAG, plugin.isSourceAvailable());
        tag.putInt(SAMPLED_VALUE_TAG, plugin.getSampledValue());
        tag.putInt(SIGNAL_TAG, plugin.getRedstoneSignal());
        tag.putInt(NETWORK_COLOR_TAG, plugin.getNetworkColorRgb());
        tag.put(CURVE_TAG, QuantumFeedbackCurveCodec.save(plugin.getCurve()));
        return tag;
    }

    private record ClientContext(BlockPos pluginPos, QuantumFeedbackMetric metric,
                                 CompoundTag snapshot, boolean remoteAccess,
                                 BlockPos accessControllerPos,
                                 @Nullable TileQuantumEntanglementController accessController,
                                 @Nullable QuantumPluginEndpointKey endpointKey) {
        private static ClientContext read(Player player, RegistryFriendlyByteBuf buffer) {
            BlockPos pluginPos = buffer.readBlockPos();
            QuantumFeedbackMetric metric = QuantumFeedbackMetric.byId(buffer.readVarInt());
            CompoundTag snapshot = buffer.readNbt();
            boolean remoteAccess = buffer.readBoolean();
            BlockPos accessPos = remoteAccess ? buffer.readBlockPos() : pluginPos;
            TileQuantumEntanglementController accessController = remoteAccess
                    && player.level().getBlockEntity(accessPos)
                    instanceof TileQuantumEntanglementController found ? found : null;
            QuantumPluginEndpointKey endpointKey = remoteAccess
                    ? QuantumPluginEndpointKey.read(buffer)
                    : null;
            return new ClientContext(pluginPos, metric,
                    snapshot == null ? new CompoundTag() : snapshot,
                    remoteAccess, accessPos, accessController, endpointKey);
        }
    }
}
