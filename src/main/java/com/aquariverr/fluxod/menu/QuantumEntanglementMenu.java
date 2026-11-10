package com.aquariverr.fluxod.menu;

import com.aquariverr.fluxod.block.entity.TileQuantumEntanglementController;
import com.aquariverr.fluxod.entanglement.QuantumPluginEndpoint;
import com.aquariverr.fluxod.entanglement.QuantumPluginEndpointKey;
import com.aquariverr.fluxod.entanglement.QuantumPluginLocation;
import com.aquariverr.fluxod.network.EntanglementPluginDirectoryPayload;
import com.aquariverr.fluxod.register.RegistryBlocks;
import com.aquariverr.fluxod.register.RegistryMenuTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

public final class QuantumEntanglementMenu extends AbstractContainerMenu {

    public static final int BUTTON_BUILD = 10;
    public static final int BUTTON_UNBIND = 11;
    public static final int BUTTON_OPEN_BATTERY = 12;
    public static final int BUTTON_OPEN_PLUGIN = 13;
    public static final int BUTTON_TOGGLE_AUTO_INPUT = 14;
    public static final int BUTTON_TOGGLE_AUTO_OUTPUT = 15;

    private static final int MAX_PLUGIN_ENDPOINTS = 18;
    private static final String REVISION_TAG = "Revision";
    private static final String SELECTED_TAG = "Selected";
    private static final String ENTRIES_TAG = "Entries";

    private static final int DATA_COUNT = 10;
    private static final int DATA_FORMED = 0;
    private static final int DATA_BOUND = 1;
    private static final int DATA_TARGET_AVAILABLE = 2;
    private static final int DATA_TARGET_TIER = 3;
    private static final int DATA_CREATIVE = 4;
    private static final int DATA_TARGET_RESOLVED = 5;
    private static final int DATA_NETWORK_COLOR_LOW = 6;
    private static final int DATA_NETWORK_COLOR_HIGH = 7;
    private static final int DATA_AUTO_INPUT = 8;
    private static final int DATA_AUTO_OUTPUT = 9;

    private final BlockPos controllerPos;
    private final Player menuPlayer;
    @Nullable
    private final TileQuantumEntanglementController controller;
    private final ContainerData data;
    private List<QuantumPluginEndpoint> pluginEndpoints = List.of();
    @Nullable
    private QuantumPluginEndpointKey selectedEndpointKey;
    private int pluginDirectoryRevision;
    @Nullable
    private CompoundTag lastSentPluginDirectory;

    public QuantumEntanglementMenu(int containerId, Inventory inventory, RegistryFriendlyByteBuf buffer) {
        this(containerId, inventory, findController(inventory.player, buffer.readBlockPos()),
                new SimpleContainerData(DATA_COUNT));
    }

    public QuantumEntanglementMenu(int containerId, Inventory inventory,
                                   TileQuantumEntanglementController controller) {
        this(containerId, inventory, controller, serverData(controller, inventory.player));
    }

    private QuantumEntanglementMenu(int containerId, Inventory inventory,
                                    @Nullable TileQuantumEntanglementController controller, ContainerData data) {
        super(RegistryMenuTypes.QUANTUM_ENTANGLEMENT.get(), containerId);
        this.controller = controller;
        this.menuPlayer = inventory.player;
        this.controllerPos = controller == null ? BlockPos.ZERO : controller.getBlockPos();
        this.data = data;
        checkContainerDataCount(data, DATA_COUNT);
        addDataSlots(data);
    }

    public boolean isFormed() {
        return data.get(DATA_FORMED) != 0;
    }

    public boolean isBound() {
        return data.get(DATA_BOUND) != 0;
    }

    public boolean isTargetAvailable() {
        return data.get(DATA_TARGET_AVAILABLE) != 0;
    }

    public int getTargetTier() {
        return data.get(DATA_TARGET_TIER);
    }

    public boolean isCreativeBuilder() {
        return data.get(DATA_CREATIVE) != 0;
    }

    public boolean isTargetResolved() {
        return data.get(DATA_TARGET_RESOLVED) != 0;
    }

    public int getNetworkColor() {
        int color = data.get(DATA_NETWORK_COLOR_LOW) & 0xFFFF
                | (data.get(DATA_NETWORK_COLOR_HIGH) & 0xFF) << 16;
        return color == 0 ? 0x43D9FF : color;
    }

    public boolean isAutomaticInputEnabled() {
        return data.get(DATA_AUTO_INPUT) != 0;
    }

    public boolean isAutomaticOutputEnabled() {
        return data.get(DATA_AUTO_OUTPUT) != 0;
    }

    public BlockPos getControllerPos() {
        return controllerPos;
    }

    public boolean hasController() {
        return controller != null;
    }

    public List<QuantumPluginEndpoint> getPluginEndpoints() {
        return pluginEndpoints;
    }

    @Nullable
    public QuantumPluginEndpointKey getSelectedEndpointKey() {
        return selectedEndpointKey;
    }

    public int getPluginDirectoryRevision() {
        return pluginDirectoryRevision;
    }

    public boolean selectedEndpointHasRemoteMenu() {
        if (selectedEndpointKey == null) return false;
        return pluginEndpoints.stream().anyMatch(endpoint -> endpoint.key().equals(selectedEndpointKey)
                && endpoint.remoteMenu());
    }

    public void applyPluginDirectory(CompoundTag snapshot) {
        pluginDirectoryRevision = Math.max(0, snapshot.getInt(REVISION_TAG));
        selectedEndpointKey = snapshot.contains(SELECTED_TAG, Tag.TAG_COMPOUND)
                ? QuantumPluginEndpointKey.load(snapshot.getCompound(SELECTED_TAG))
                : null;
        ListTag entries = snapshot.getList(ENTRIES_TAG, Tag.TAG_COMPOUND);
        List<QuantumPluginEndpoint> decoded = new ArrayList<>(Math.min(entries.size(), MAX_PLUGIN_ENDPOINTS));
        for (int index = 0; index < entries.size() && decoded.size() < MAX_PLUGIN_ENDPOINTS; index++) {
            QuantumPluginEndpoint endpoint = readEndpoint(entries.getCompound(index));
            if (endpoint != null) decoded.add(endpoint);
        }
        pluginEndpoints = List.copyOf(decoded);
    }

    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (controller == null || player.level().isClientSide || player.isSpectator()
                || !(player instanceof ServerPlayer serverPlayer)) return false;
        if (!controller.canPlayerManage(player)) return false;
        if (id == BUTTON_BUILD) {
            player.displayClientMessage(
                    com.aquariverr.fluxod.multiblock.QuantumEntanglementAssembler.resultMessage(
                            controller.assemble(serverPlayer)), true);
            broadcastChanges();
            return true;
        }
        if (id == BUTTON_UNBIND) {
            controller.unbindBattery(player);
            player.displayClientMessage(
                    net.minecraft.network.chat.Component.translatable(
                            "message.flux_overdrive.quantum_battery_unbound"), true);
            broadcastChanges();
            return true;
        }
        if (id == BUTTON_OPEN_BATTERY) {
            return controller.openTargetBatteryMenu(serverPlayer);
        }
        if (id == BUTTON_OPEN_PLUGIN) {
            return controller.openSelectedPluginMenu(serverPlayer);
        }
        if (id == BUTTON_TOGGLE_AUTO_INPUT) {
            if (!stillValid(serverPlayer)) return false;
            boolean changed = controller.toggleAutomaticInput(serverPlayer);
            if (changed) broadcastChanges();
            return changed;
        }
        if (id == BUTTON_TOGGLE_AUTO_OUTPUT) {
            if (!stillValid(serverPlayer)) return false;
            boolean changed = controller.toggleAutomaticOutput(serverPlayer);
            if (changed) broadcastChanges();
            return changed;
        }
        return false;
    }

    public boolean selectEndpoint(ServerPlayer player, QuantumPluginEndpointKey key, int expectedRevision) {
        if (controller == null || !stillValid(player)) return false;
        boolean selected = controller.selectEndpoint(player, key, expectedRevision);
        if (selected) {
            lastSentPluginDirectory = null;
            broadcastChanges();
        }
        return selected;
    }

    public boolean bindBattery(ServerPlayer player, GlobalPos target) {
        if (controller == null || player.isSpectator() || !stillValid(player)
                || !controller.canPlayerManage(player)) return false;
        boolean bound = controller.bindBattery(player, target);
        if (bound) broadcastChanges();
        return bound;
    }

    @Override
    public boolean stillValid(Player player) {
        if (controller == null || controller.isRemoved()) return false;
        if (controller.getLevel() != player.level()
                || !player.level().getBlockState(controllerPos)
                .is(RegistryBlocks.QUANTUM_BATTERY_ENTANGLEMENT_CONTROLLER.get())
                || player.distanceToSqr(controllerPos.getX() + 0.5, controllerPos.getY() + 0.5,
                controllerPos.getZ() + 0.5) > 64.0) {
            return false;
        }
        return player.level().isClientSide || controller.canPlayerManage(player);
    }

    @Override
    public void broadcastChanges() {
        super.broadcastChanges();
        if (!(menuPlayer instanceof ServerPlayer player) || player.containerMenu != this
                || !stillValid(player) || controller == null) {
            return;
        }
        CompoundTag snapshot = createPluginDirectory(controller, player);
        if (snapshot.equals(lastSentPluginDirectory)) return;
        lastSentPluginDirectory = snapshot.copy();
        PacketDistributor.sendToPlayer(player,
                new EntanglementPluginDirectoryPayload(containerId, snapshot));
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        return ItemStack.EMPTY;
    }

    @Nullable
    private static TileQuantumEntanglementController findController(Player player, BlockPos pos) {
        if (player.level().getBlockEntity(pos) instanceof TileQuantumEntanglementController controller) {
            return controller;
        }
        return null;
    }

    private static ContainerData serverData(TileQuantumEntanglementController controller, Player player) {
        return new ContainerData() {
            @Override
            public int get(int index) {
                return switch (index) {
                    case DATA_FORMED -> controller.isFormed() ? 1 : 0;
                    case DATA_BOUND -> controller.isBound() ? 1 : 0;
                    case DATA_TARGET_AVAILABLE -> controller.canPlayerViewTarget(player)
                            && controller.isTargetAvailable() ? 1 : 0;
                    case DATA_TARGET_TIER -> controller.canPlayerViewTarget(player)
                            ? controller.getTargetTier() : 0;
                    case DATA_CREATIVE -> player.isCreative() ? 1 : 0;
                    case DATA_TARGET_RESOLVED -> controller.isFormed()
                            && controller.canPlayerViewTarget(player) ? 1 : 0;
                    case DATA_NETWORK_COLOR_LOW -> (controller.canPlayerViewTarget(player)
                            ? controller.getTargetNetworkColorRgb() : 0x43D9FF) & 0xFFFF;
                    case DATA_NETWORK_COLOR_HIGH -> (controller.canPlayerViewTarget(player)
                            ? controller.getTargetNetworkColorRgb() : 0x43D9FF) >>> 16 & 0xFF;
                    case DATA_AUTO_INPUT -> controller.isAutomaticInputEnabled() ? 1 : 0;
                    case DATA_AUTO_OUTPUT -> controller.isAutomaticOutputEnabled() ? 1 : 0;
                    default -> 0;
                };
            }

            @Override
            public void set(int index, int value) {}

            @Override
            public int getCount() {
                return DATA_COUNT;
            }
        };
    }

    private static CompoundTag createPluginDirectory(TileQuantumEntanglementController controller, Player player) {
        CompoundTag snapshot = new CompoundTag();
        List<QuantumPluginEndpoint> endpoints = controller.getPluginEndpoints(player);
        snapshot.putInt(REVISION_TAG, controller.getPluginDirectoryRevision(player));
        QuantumPluginEndpointKey selected = controller.getSelectedEndpointKey();
        if (selected != null) snapshot.put(SELECTED_TAG, selected.save());
        ListTag entries = new ListTag();
        for (int index = 0; index < endpoints.size() && index < MAX_PLUGIN_ENDPOINTS; index++) {
            QuantumPluginEndpoint endpoint = endpoints.get(index);
            CompoundTag entry = endpoint.key().save();
            entry.putLong("AbsolutePos", endpoint.absolutePos().asLong());
            entry.putInt("Ring", endpoint.ringTier());
            entry.putInt("Location", endpoint.location().ordinal());
            entry.putString("Icon", endpoint.iconItemId().toString());
            entry.putString("Name", endpoint.displayTranslationKey());
            entry.putInt("InputResources", endpoint.inputResources());
            entry.putInt("OutputResources", endpoint.outputResources());
            entry.putBoolean("RemoteMenu", endpoint.remoteMenu());
            entries.add(entry);
        }
        snapshot.put(ENTRIES_TAG, entries);
        return snapshot;
    }

    @Nullable
    private static QuantumPluginEndpoint readEndpoint(CompoundTag entry) {
        QuantumPluginEndpointKey key = QuantumPluginEndpointKey.load(entry);
        ResourceLocation icon = ResourceLocation.tryParse(entry.getString("Icon"));
        String name = entry.getString("Name");
        if (key == null || icon == null || name.isBlank()) return null;
        return new QuantumPluginEndpoint(key, BlockPos.of(entry.getLong("AbsolutePos")),
                Math.clamp(entry.getInt("Ring"), 1, 3),
                QuantumPluginLocation.byId(entry.getInt("Location")), icon, name,
                entry.getInt("InputResources"), entry.getInt("OutputResources"),
                entry.getBoolean("RemoteMenu"));
    }
}
